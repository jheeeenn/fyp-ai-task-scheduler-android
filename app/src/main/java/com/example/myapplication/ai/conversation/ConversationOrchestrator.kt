package com.example.myapplication.ai.conversation

import android.util.Log
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextItem
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextSnapshot
import com.example.myapplication.diagnostics.DebugDiagnosticLog
import kotlinx.coroutines.CancellationException

class ConversationOrchestratorException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class ConversationOrchestrator(
    private val conversationAgentClient: ConversationAgentClient,
    private val parser: ConversationDecisionParser,
    private val responseParser: ConversationResponseParser = ConversationResponseParser(),
    private val memory: ConversationSessionMemory = ConversationSessionMemory()
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

    /**
     * Retained for controlled future experiments with model-based observation verbalization.
     * HomeActivity renders authoritative production task responses deterministically for factual
     * completeness and accessibility.
     */
    suspend fun respondToObservation(
        observation: ExecutionObservation,
        appContextSummary: String
    ): ConversationResponse {
        memory.recordObservation(observation)
        return try {
            val rawContent = conversationAgentClient.respondToObservation(
                observationJson = observation.toAgentJson(),
                memorySnapshot = memory.snapshotForPrompt(),
                appContextSummary = appContextSummary
            )
            val response = responseParser.parse(rawContent)
            val expectedType = observation.outcome.toConversationResponseType()
            if (response.responseType != expectedType) {
                Log.e(
                    "CONVO_OBSERVATION",
                    "response type ${response.responseType} incompatible with authoritative outcome ${observation.outcome}; expected $expectedType"
                )
                throw ConversationSchemaException("ConversationResponse response_type does not match ExecutionObservation outcome")
            }
            memory.recordFinalSpokenResponse(response.speech)
            response
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CONVO_OBSERVATION", "response verbalization failed; using deterministic fallback", e)
            val fallback = ConversationResponse(
                speech = observation.fallbackSpeech,
                hint = observation.fallbackHint,
                responseType = observation.outcome.toConversationResponseType(),
                source = "deterministic_fallback"
            )
            memory.recordFinalSpokenResponse(fallback.speech)
            fallback
        }
    }

    fun recordDeterministicObservation(
        observation: ExecutionObservation,
        response: ConversationResponse
    ) {
        memory.recordObservation(observation)
        memory.recordFinalSpokenResponse(response.speech)
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
            parser.parse(rawContent)
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
        Log.e("CONVO_ORCH_SCHEMA", "first response invalid, retrying once", firstFailure)

        return try {
            val repairContent = conversationAgentClient.processRepair(
                userText = normalizedText,
                appContextSummary = appendTaskContext(
                    memorySnapshot = appContextSummary,
                    readOnlyTaskContextSnapshot = readOnlyTaskContextSnapshot,
                    contextFocus = contextFocus
                )
            )
            val repairedDecision = parser.parse(repairContent).copy(
                source = SOURCE_SCHEMA_REPAIR
            )
            Log.d("CONVO_ORCH_SCHEMA", "repair response accepted")
            repairedDecision
        } catch (repairFailure: Exception) {
            Log.e("CONVO_ORCH_SCHEMA", "repair response failed", repairFailure)
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
                taskText = decision.taskText.ifBlank { normalizedText }
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
        return parser.parse(rawContent).copy(source = SOURCE_CONTEXT_REPAIR)
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
        return parser.parse(rawContent).copy(source = SOURCE_CONTEXT_ACTION_REPAIR)
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
        const val SOURCE_SCHEMA_REPAIR = "conversation_agent_schema_repair"
        const val SOURCE_CONTEXT_REPAIR = "conversation_agent_context_repair"
        const val SOURCE_CONTEXT_ACTION_REPAIR = "conversation_agent_context_action_repair"
        val NO_TASK_CONTEXT = """
            Scope: NONE
            Generation: 0
            Items: None
            Truncated: false
        """.trimIndent()
    }
}
