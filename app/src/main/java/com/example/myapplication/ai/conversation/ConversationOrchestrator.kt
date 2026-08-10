package com.example.myapplication.ai.conversation

import android.util.Log
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextItem
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextSnapshot
import com.example.myapplication.diagnostics.DebugDiagnosticLog
import kotlinx.coroutines.CancellationException
import java.util.Locale

class ConversationOrchestratorException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class ConversationOrchestrator(
    private val conversationAgentClient: ConversationAgentClient,
    private val parser: ConversationDecisionParser,
    private val responseParser: ConversationResponseParser = ConversationResponseParser(),
    private val memory: ConversationSessionMemory = ConversationSessionMemory(),
    private val responseVerbalizationParser: ResponseVerbalizationParser =
        ResponseVerbalizationParser()
) {
    suspend fun styleTaskQuerySpeech(
        plan: TaskQuerySpeechPlan,
        budgetAvailable: Boolean
    ): ConversationResponse {
        if (!budgetAvailable) {
            Log.d("SAFE_OBSERVATION_STYLE_FALLBACK", "reason=CALL_BUDGET")
            return deterministicTaskQueryResponse(plan)
        }
        return try {
            val raw = conversationAgentClient.requestSafeObservationStyle(
                plan.styleContext.toSafeJson()
            )
            val envelope = SafeObservationStyleParser().parse(raw)
            val validation = SafeObservationStyleValidator.evaluate(envelope)
            Log.d(
                "SAFE_OBSERVATION_STYLE_RESULT",
                "accepted=${validation.accepted} reason=${validation.reason}"
            )
            if (!validation.accepted) {
                deterministicTaskQueryResponse(plan)
            } else {
                val speech = SafeTaskQuerySpeechComposer.compose(plan, envelope)
                Log.d(
                    "SAFE_OBSERVATION_COMPOSE",
                    "source=android_hybrid_safe coreLength=${plan.authoritativeCore.length} " +
                        "controlLength=${plan.authoritativeControl.length}"
                )
                ConversationResponse(
                    speech = speech,
                    hint = "",
                    responseType = ConversationResponseType.INFORMATION,
                    source = "android_hybrid_safe"
                )
            }
        } catch (e: ConversationSchemaException) {
            Log.d(
                "SAFE_OBSERVATION_STYLE_RESULT",
                "accepted=false reason=${SafeStyleValidationReason.INVALID_FORMAT}"
            )
            Log.d("SAFE_OBSERVATION_STYLE_FALLBACK", "reason=INVALID_SCHEMA")
            deterministicTaskQueryResponse(plan)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = if (
                e.message.orEmpty().contains("timed out", ignoreCase = true) ||
                e.message.orEmpty().contains("timeout", ignoreCase = true)
            ) {
                "TIMEOUT"
            } else {
                "REQUEST_OR_SCHEMA_FAILURE"
            }
            Log.d("SAFE_OBSERVATION_STYLE_FALLBACK", "reason=$reason")
            deterministicTaskQueryResponse(plan)
        }
    }

    private fun deterministicTaskQueryResponse(plan: TaskQuerySpeechPlan) =
        ConversationResponse(
            speech = plan.deterministicSpeech,
            hint = "",
            responseType = ConversationResponseType.INFORMATION,
            source = "android_deterministic"
        )

    /** Returns presentation-only speech; the caller records it only after stale-delivery checks. */
    suspend fun respondToObservation(
        observation: ExecutionObservation,
        appContextSummary: String = "",
        tone: ResponseVerbalizationTone = ResponseVerbalizationTone.NEUTRAL,
        verbosity: ResponseVerbalizationVerbosity = ResponseVerbalizationVerbosity.NORMAL
    ): ConversationResponse {
        val plan = ResponseVerbalizationPlanner.createOrNull(
            observation = observation,
            tone = tone,
            verbosity = verbosity
        ) ?: return AndroidObservationResponseRenderer.render(observation)
        val startedAt = System.currentTimeMillis()
        Log.d(
            "RESPONSE_VERBALIZATION_REQUEST",
            "operation=${observation.operation} outcome=${observation.outcome} " +
                "responseType=${plan.responseType} tone=$tone verbosity=$verbosity"
        )
        return try {
            val rawContent = conversationAgentClient.respondToObservation(
                observationJson = plan.toSafeAgentJson(),
                memorySnapshot = "",
                appContextSummary = ""
            )
            val envelope = responseVerbalizationParser.parse(rawContent)
            val validation = ResponseVerbalizationValidator.evaluate(plan, envelope)
            val latencyMs = System.currentTimeMillis() - startedAt
            Log.d(
                "RESPONSE_VERBALIZATION_RESULT",
                "operation=${observation.operation} outcome=${observation.outcome} " +
                    "accepted=${validation.accepted} source=conversation_agent " +
                    "validation=${validation.reason} latencyMs=$latencyMs"
            )
            if (!validation.accepted) {
                verbalizationFallback(plan, validation.reason.name, latencyMs)
            } else {
                val response = plan.deterministicResponse.copy(
                    speech = ResponseVerbalizationComposer.compose(plan, envelope),
                    source = "conversation_agent_verbalization"
                )
                Log.d(
                    "RESPONSE_VERBALIZATION_ACCEPTED",
                    "operation=${observation.operation} outcome=${observation.outcome} " +
                        "source=${response.source} latencyMs=$latencyMs"
                )
                response
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ConversationSchemaException) {
            val latencyMs = System.currentTimeMillis() - startedAt
            Log.d(
                "RESPONSE_VERBALIZATION_RESULT",
                "operation=${observation.operation} outcome=${observation.outcome} " +
                    "accepted=false source=conversation_agent " +
                    "validation=${ResponseVerbalizationValidationReason.INVALID_FORMAT} " +
                    "latencyMs=$latencyMs"
            )
            verbalizationFallback(
                plan,
                ResponseVerbalizationValidationReason.INVALID_FORMAT.name,
                latencyMs
            )
        } catch (e: Exception) {
            val latencyMs = System.currentTimeMillis() - startedAt
            val reason = if (
                e.message.orEmpty().contains("timed out", ignoreCase = true) ||
                e.message.orEmpty().contains("timeout", ignoreCase = true)
            ) {
                "TIMEOUT"
            } else {
                "REQUEST_FAILURE"
            }
            Log.e(
                "RESPONSE_VERBALIZATION_RESULT",
                "operation=${observation.operation} outcome=${observation.outcome} " +
                    "accepted=false source=conversation_agent validation=$reason " +
                    "latencyMs=$latencyMs",
                e
            )
            verbalizationFallback(plan, reason, latencyMs)
        }
    }

    private fun verbalizationFallback(
        plan: ResponseVerbalizationPlan,
        reason: String,
        latencyMs: Long
    ): ConversationResponse {
        Log.d(
            "RESPONSE_VERBALIZATION_FALLBACK",
            "operation=${plan.operation} outcome=${plan.outcome} reason=$reason " +
                "source=android_deterministic latencyMs=$latencyMs"
        )
        return plan.deterministicResponse
    }

    fun recordDeliveredObservationResponse(
        observation: ExecutionObservation,
        response: ConversationResponse
    ) {
        memory.recordObservation(observation)
        memory.recordPresentationSink(response.speech)
    }

    suspend fun process(
        normalizedText: String,
        appContextSummary: String,
        readOnlyTaskContextSnapshot: String = NO_TASK_CONTEXT,
        contextFocus: ConversationContextFocus? = null
    ): ConversationDecision {
        memory.recordUser(normalizedText)
        val routingMemory = appendTaskContext(
            memorySnapshot = memory.snapshotForPrompt(),
            readOnlyTaskContextSnapshot = readOnlyTaskContextSnapshot,
            contextFocus = contextFocus
        )

        val parsed = try {
            val rawContent = conversationAgentClient.process(
                userText = normalizedText,
                memorySnapshot = routingMemory,
                appContextSummary = appContextSummary
            )
            parseCanonicalDecision(rawContent).also {
                validateContextActionAuthority(
                    decision = it,
                    readOnlyTaskContextSnapshot = readOnlyTaskContextSnapshot,
                    contextFocus = contextFocus
                )
            }
        } catch (e: ConversationSchemaException) {
            retryWithRepair(normalizedText, appContextSummary, readOnlyTaskContextSnapshot, contextFocus, e)
        } catch (e: ConversationAgentResponseException) {
            retryWithRepair(normalizedText, appContextSummary, readOnlyTaskContextSnapshot, contextFocus, e)
        }

        val decision = normalizeDecision(parsed, normalizedText)
        DebugDiagnosticLog.event(
            "CONVERSATION_DECISION_DEBUG",
            "route=${decision.route.name}\n" +
                "task_text=${decision.taskText}\n" +
                "reply=${decision.reply}\n" +
                "context_ref=${decision.contextRef}\n" +
                "context_detail=${decision.contextDetail.name}\n" +
                "context_action=${decision.contextAction.name}\n" +
                "query_reading_move=${decision.queryReadingMove.name}\n" +
                "query_presentation_hint=${decision.queryPresentationHint.name}\n" +
                "confidence=${decision.confidence}\n" +
                "listen_again=${decision.listenAgain}\n" +
                "source=${decision.source}"
        )
        return decision
    }

    private suspend fun retryWithRepair(
        normalizedText: String,
        appContextSummary: String,
        readOnlyTaskContextSnapshot: String,
        contextFocus: ConversationContextFocus?,
        firstFailure: Exception
    ): ConversationDecision {
        val failureCode = repairFailureCode(firstFailure)
        Log.e(
            "CONVO_ORCH_SCHEMA",
            "first response invalid; failureCode=$failureCode; retrying once"
        )

        return try {
            val repairContent = conversationAgentClient.processRepair(
                userText = normalizedText,
                appContextSummary = boundedRepairContext(
                    appContextSummary = appContextSummary,
                    readOnlyTaskContextSnapshot = readOnlyTaskContextSnapshot,
                    contextFocus = contextFocus
                ),
                failureCode = failureCode
            )
            val repairedDecision = parseCanonicalDecision(
                rawContent = repairContent,
                source = SOURCE_SCHEMA_REPAIR
            )
            validateContextActionAuthority(
                decision = repairedDecision,
                readOnlyTaskContextSnapshot = readOnlyTaskContextSnapshot,
                contextFocus = contextFocus
            )
            Log.d("CONVO_ORCH_SCHEMA", "repair response accepted")
            repairedDecision
        } catch (repairFailure: Exception) {
            Log.e(
                "CONVO_ORCH_SCHEMA",
                "repair response failed; failureCode=${repairFailureCode(repairFailure)}"
            )
            throw ConversationOrchestratorException(
                "Conversation Agent failed after schema retry",
                repairFailure
            )
        }
    }

    private fun normalizeDecision(
        decision: ConversationDecision,
        normalizedText: String
    ): ConversationDecision {
        return when (decision.route) {
            ConversationRoute.TASK_COMMAND -> decision.copy(
                taskText = normalizedText
            )
            ConversationRoute.SMART_ROUTINE_BUILDER -> decision.copy(
                taskText = normalizedText,
                reply = ""
            )
            ConversationRoute.SAVED_ROUTINE_ACTION -> decision.copy(
                taskText = normalizedText,
                reply = ""
            )
            ConversationRoute.DAILY_BRIEFING -> decision
            ConversationRoute.CONTEXT_AWARE_SUGGESTION -> decision.copy(
                taskText = normalizedText,
                reply = ""
            )
            ConversationRoute.CONTEXT_READ -> decision
            ConversationRoute.CONTEXT_ACTION -> decision
            ConversationRoute.QUERY_READING_CONTROL -> decision
            ConversationRoute.DIRECT_REPLY -> decision.copy(
                reply = decision.reply.ifBlank { "Hi. I can help you create, check, reschedule, delete, complete, or break down tasks." }
            )
            ConversationRoute.ASK_CLARIFICATION -> decision.copy(
                reply = decision.reply.ifBlank { "Could you say that another way, or tell me which task you mean?" }
            )
            ConversationRoute.UNKNOWN -> decision.copy(
                reply = decision.reply.ifBlank { "I can help with task scheduling. Try asking me to create, check, reschedule, delete, complete, or break down a task." }
            )
            ConversationRoute.END_SESSION -> decision.copy(
                reply = decision.reply.ifBlank { "Okay, stopping the assistant." },
                listenAgain = false
            )
        }
    }

    fun clearSessionMemory() {
        memory.clear()
    }

    fun contextFocusForSnapshot(snapshot: ReadOnlyTaskContextSnapshot): ConversationContextFocus? =
        memory.contextFocusForGeneration(
            currentGeneration = snapshot.generation,
            suppliedRefs = snapshot.items.map { it.ref }.toSet()
        )

    fun clearInvalidContextFocus(snapshot: ReadOnlyTaskContextSnapshot): Boolean =
        memory.clearInvalidContextFocus(
            currentGeneration = snapshot.generation,
            suppliedRefs = snapshot.items.map { it.ref }.toSet()
        )

    fun setAuthoritativeContextFocus(
        item: ReadOnlyTaskContextItem,
        selectedRef: String,
        capturedGeneration: Long
    ) {
        memory.setAuthoritativeContextFocus(
            item = item,
            selectedRef = selectedRef,
            capturedGeneration = capturedGeneration
        )
    }

    fun commitFinalDecision(decision: ConversationDecision) {
        memory.commitFinalDecision(decision)
    }

    suspend fun processContextReadRepair(
        normalizedText: String,
        readOnlyTaskContextSnapshot: String,
        primaryRoute: ConversationRoute,
        currentInteraction: String,
        contextFocus: ConversationContextFocus? = null
    ): ConversationDecision {
        val rawContent = conversationAgentClient.processContextReadRepair(
            userText = normalizedText,
            memorySnapshot = appendContextFocus(memory.snapshotForPrompt(), contextFocus),
            taskContextSnapshot = readOnlyTaskContextSnapshot,
            primaryRoute = primaryRoute,
            currentInteraction = currentInteraction
        )
        return parseCanonicalDecision(
            rawContent = rawContent,
            source = SOURCE_CONTEXT_REPAIR
        )
    }

    suspend fun processContextActionRepair(
        normalizedText: String,
        readOnlyTaskContextSnapshot: String,
        primaryRoute: ConversationRoute,
        currentInteraction: String,
        contextFocus: ConversationContextFocus? = null
    ): ConversationDecision {
        val rawContent = conversationAgentClient.processContextActionRepair(
            userText = normalizedText,
            memorySnapshot = appendContextFocus(memory.snapshotForPrompt(), contextFocus),
            taskContextSnapshot = readOnlyTaskContextSnapshot,
            primaryRoute = primaryRoute,
            currentInteraction = currentInteraction
        )
        return parseCanonicalDecision(
            rawContent = rawContent,
            source = SOURCE_CONTEXT_ACTION_REPAIR
        )
    }

    private fun parseCanonicalDecision(
        rawContent: String,
        source: String = SOURCE_CONVERSATION_AGENT
    ): ConversationDecision {
        val result = parser.parseWithReport(rawContent)
        if (result.canonicalizationReport.wasCanonicalized) {
            Log.d(
                "CONVO_DECISION_CANONICALIZED",
                "route=${result.decision.route.name} " +
                    "fields=${result.canonicalizationReport.fields.joinToString(",")}"
            )
        }
        return result.decision.copy(source = source)
    }

    private fun repairFailureCode(failure: Exception): String = when (failure) {
        is ConversationSchemaException ->
            failure.decisionFailureCode?.name ?: FAILURE_CODE_SCHEMA_UNKNOWN
        is ConversationAgentResponseException -> FAILURE_CODE_AGENT_RESPONSE
        else -> FAILURE_CODE_UNEXPECTED
    }

    /** CONTEXT_ACTION may select only authority already supplied by Android. */
    private fun validateContextActionAuthority(
        decision: ConversationDecision,
        readOnlyTaskContextSnapshot: String,
        contextFocus: ConversationContextFocus?
    ) {
        if (decision.route != ConversationRoute.CONTEXT_ACTION) return
        val suppliedRefs = suppliedContextRefs(readOnlyTaskContextSnapshot)
        val focusRef = contextFocus
            ?.takeIf { it.available }
            ?.ref
            ?.uppercase(Locale.ROOT)
        val selectedRef = decision.contextRef.uppercase(Locale.ROOT)
        if (selectedRef !in suppliedRefs && selectedRef != focusRef) {
            throw ConversationSchemaException(
                message = "CONTEXT_ACTION selected a ref not supplied by Android",
                decisionFailureCode = ConversationDecisionFailureCode.INVALID_CONTEXT_REF
            )
        }
    }

    private fun suppliedContextRefs(readOnlyTaskContextSnapshot: String): List<String> =
        SUPPLIED_CONTEXT_REF.findAll(readOnlyTaskContextSnapshot)
            .map { it.groupValues[1].uppercase(Locale.ROOT) }
            .distinct()
            .toList()

    private fun boundedRepairContext(
        appContextSummary: String,
        readOnlyTaskContextSnapshot: String,
        contextFocus: ConversationContextFocus?
    ): String = buildString {
        append(appContextSummary.trim())
        appendLine()
        appendLine()
        appendLine("Bounded decision-repair context:")
        val refs = suppliedContextRefs(readOnlyTaskContextSnapshot)
        val focusRef = contextFocus?.takeIf { it.available }?.ref
        appendLine(
            "Supplied temporary refs: " +
                refs.takeIf { it.isNotEmpty() }?.joinToString(",").orEmpty()
                    .ifBlank { "NONE" }
        )
        appendLine(
            "Current validated focus ref: " +
                focusRef.orEmpty().ifBlank { "NONE" }
        )
        append(
            "Context action authority available: " +
                (refs.isNotEmpty() || !focusRef.isNullOrBlank())
        )
    }

    fun recordAuthoritativeContextRead(
        item: ReadOnlyTaskContextItem,
        selectedRef: String,
        selectedDetail: ConversationContextDetail,
        capturedGeneration: Long,
        finalSpeech: String
    ) {
        memory.recordAuthoritativeContextRead(
            item = item,
            selectedRef = selectedRef,
            selectedDetail = selectedDetail,
            capturedGeneration = capturedGeneration,
            finalSpeech = finalSpeech
        )
    }

    private fun appendTaskContext(
        memorySnapshot: String,
        readOnlyTaskContextSnapshot: String,
        contextFocus: ConversationContextFocus?
    ): String = buildString {
        append(memorySnapshot.trim())
        appendLine()
        appendLine()
        appendLine("Read-only task context:")
        append(
            readOnlyTaskContextSnapshot.takeIf { it.isNotBlank() }
                ?: NO_TASK_CONTEXT
        )
        appendLine()
        appendLine()
        appendLine("Current validated task focus:")
        append(contextFocus?.toPromptText() ?: ConversationContextFocus.UNAVAILABLE_PROMPT)
    }

    private fun appendContextFocus(
        memorySnapshot: String,
        contextFocus: ConversationContextFocus?
    ): String = buildString {
        append(memorySnapshot.trim())
        appendLine()
        appendLine()
        appendLine("Current validated task focus:")
        append(contextFocus?.toPromptText() ?: ConversationContextFocus.UNAVAILABLE_PROMPT)
    }

    private companion object {
        const val SOURCE_CONVERSATION_AGENT = "conversation_agent"
        const val SOURCE_SCHEMA_REPAIR = "conversation_agent_schema_repair"
        const val SOURCE_CONTEXT_REPAIR = "conversation_agent_context_repair"
        const val SOURCE_CONTEXT_ACTION_REPAIR = "conversation_agent_context_action_repair"
        const val FAILURE_CODE_AGENT_RESPONSE = "AGENT_RESPONSE_FAILURE"
        const val FAILURE_CODE_SCHEMA_UNKNOWN = "SCHEMA_FAILURE"
        const val FAILURE_CODE_UNEXPECTED = "UNEXPECTED_FAILURE"
        val SUPPLIED_CONTEXT_REF = Regex("\\\"ref\\\":\\\"(T[1-9][0-9]*)\\\"")
        val NO_TASK_CONTEXT = """
            Scope: NONE
            Generation: 0
            Items: None
            Truncated: false
        """.trimIndent()
    }
}
