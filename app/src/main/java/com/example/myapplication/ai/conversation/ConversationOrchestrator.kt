package com.example.myapplication.ai.conversation

import android.util.Log
import com.example.myapplication.ai.TaskQueryPresentation
import com.example.myapplication.ai.TaskCommandContradictionDetector
import com.example.myapplication.ai.conversation.taskcontext.ContextActionAuthorityBindingPolicy
import com.example.myapplication.ai.conversation.taskcontext.ContextActionAuthorityBindingResult
import com.example.myapplication.ai.conversation.taskcontext.ContextReadTitleGroundingPolicy
import com.example.myapplication.ai.conversation.taskcontext.ContextReadTitleGroundingResult
import com.example.myapplication.ai.conversation.taskcontext.PendingContextActionTargetDecision
import com.example.myapplication.ai.conversation.taskcontext.PendingContextActionTargetParser
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextItem
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextSnapshot
import com.example.myapplication.ai.conversation.query.QueryCountFollowUpDecision
import com.example.myapplication.ai.conversation.query.QueryCountFollowUpParser
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
        ResponseVerbalizationParser(),
    private val noContextMutationRepairParser: NoContextMutationRepairParser =
        NoContextMutationRepairParser()
) {
    private val responseVerbalizationEngine = ResponseVerbalizationEngine(
        conversationAgentClient = conversationAgentClient,
        parser = responseVerbalizationParser
    )

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
        return responseVerbalizationEngine.verbalize(plan)
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
        contextFocus: ConversationContextFocus? = null,
        voiceSettingRoutingContext: VoiceSettingRoutingContext =
            VoiceSettingRoutingContext.UNAVAILABLE,
        capturedTaskContextSnapshot: ReadOnlyTaskContextSnapshot? = null
    ): ConversationDecision {
        val topLevelRouteContext = ConversationTopLevelRouteConsistencyContext(
            recentSavedRoutineAction =
                memory.pendingAction == ConversationRoute.SAVED_ROUTINE_ACTION.name
        )
        memory.recordUser(normalizedText)
        val routingMemory = appendTaskContext(
            memorySnapshot = memory.snapshotForPrompt(),
            readOnlyTaskContextSnapshot = readOnlyTaskContextSnapshot,
            contextFocus = contextFocus,
            voiceSettingRoutingContext = voiceSettingRoutingContext
        )

        val parsed = try {
            val rawContent = conversationAgentClient.process(
                userText = normalizedText,
                memorySnapshot = routingMemory,
                appContextSummary = appContextSummary
            )
            val canonicalDecision = parseCanonicalDecision(
                rawContent = rawContent,
                normalizedTextForTopLevelValidation = normalizedText,
                topLevelRouteContext = topLevelRouteContext,
                capturedTaskContextSnapshot = capturedTaskContextSnapshot
            )
            validateTaskRouteConsistency(normalizedText, canonicalDecision)
            validateOperationalBreakdownRouting(normalizedText, canonicalDecision)
            validateContextReadAuthority(
                decision = canonicalDecision,
                readOnlyTaskContextSnapshot = readOnlyTaskContextSnapshot
            )
            val authorityReconciledDecision = reconcileContextActionAuthority(
                normalizedText = normalizedText,
                decision = canonicalDecision,
                capturedTaskContextSnapshot = capturedTaskContextSnapshot,
                contextFocus = contextFocus
            )
            validateContextActionAuthority(
                decision = authorityReconciledDecision,
                readOnlyTaskContextSnapshot = readOnlyTaskContextSnapshot,
                contextFocus = contextFocus
            )
            authorityReconciledDecision
        } catch (e: ConversationSchemaException) {
            retryWithRepair(
                normalizedText,
                appContextSummary,
                readOnlyTaskContextSnapshot,
                contextFocus,
                voiceSettingRoutingContext,
                topLevelRouteContext,
                capturedTaskContextSnapshot,
                e
            )
        } catch (e: ConversationAgentResponseException) {
            retryWithRepair(
                normalizedText,
                appContextSummary,
                readOnlyTaskContextSnapshot,
                contextFocus,
                voiceSettingRoutingContext,
                topLevelRouteContext,
                capturedTaskContextSnapshot,
                e
            )
        }

        val decision = normalizeDecision(parsed, normalizedText)
        DebugDiagnosticLog.event(
            "CONVERSATION_DECISION_DEBUG",
            "route=${decision.route.name}\n" +
                "navigation_target=${decision.navigationTarget.name}\n" +
                "task_text=${decision.taskText}\n" +
                "reply=${decision.reply}\n" +
                "context_ref=${decision.contextRef}\n" +
                "context_detail=${decision.contextDetail.name}\n" +
                "context_action=${decision.contextAction.name}\n" +
                "setting_action=${decision.settingAction.name}\n" +
                "setting_target=${decision.settingTarget.name}\n" +
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
        voiceSettingRoutingContext: VoiceSettingRoutingContext,
        topLevelRouteContext: ConversationTopLevelRouteConsistencyContext,
        capturedTaskContextSnapshot: ReadOnlyTaskContextSnapshot?,
        firstFailure: Exception
    ): ConversationDecision {
        val failureCode = repairFailureCode(firstFailure)
        val failedRoute = (firstFailure as? ConversationSchemaException)?.failedRoute
        // A decoded CONTEXT_READ cannot have authority here, including when the strict parser
        // rejects its ref or detail before the authority check. Never relax either validator.
        if (
            failedRoute == ConversationRoute.CONTEXT_READ &&
            suppliedContextRefs(readOnlyTaskContextSnapshot).isEmpty() &&
            contextFocus?.takeIf { it.available }?.ref.isNullOrBlank()
        ) {
            val evidence = TaskCommandContradictionDetector.namedScheduleReadEvidence(normalizedText)
            if (evidence != null) {
                Log.d(
                    "CONVERSATION_NAMED_QUERY_GUARD",
                    "detail=${evidence.expectedDetail} primaryRoute=$failedRoute contextAuthority=false " +
                        "result=REPAIR_REQUIRED"
                )
                return repairTaskCommandRoute(
                    normalizedText,
                    ConversationDecisionFailureCode.NAMED_TASK_QUERY_MISROUTED.name,
                    failedRoute
                )
            }
        }
        if (failureCode in TASK_ROUTE_CONTRADICTIONS) {
            return repairTaskCommandRoute(normalizedText, failureCode, requireNotNull(failedRoute))
        }
        val repairProfile = ConversationRepairProfileSelector.select(
            failureCode = failureCode,
            failedRoute = failedRoute,
            suppliedTemporaryRefCount = suppliedContextRefs(readOnlyTaskContextSnapshot).size,
            validatedFocusAvailable = contextFocus
                ?.takeIf { it.available }
                ?.ref
                ?.isNotBlank() == true
        )
        Log.e(
            "CONVO_ORCH_SCHEMA",
            "first response invalid; failureCode=$failureCode; retrying once"
        )
        if (repairProfile == ConversationRepairProfile.NO_CONTEXT_MUTATION_REPAIR) {
            Log.d(
                "NO_CONTEXT_MUTATION_REPAIR",
                "state=SELECTED allowedRoutes=TASK_COMMAND,ASK_CLARIFICATION"
            )
        }

        return try {
            val repairContent = conversationAgentClient.processRepair(
                userText = normalizedText,
                appContextSummary = boundedRepairContext(
                    appContextSummary = appContextSummary,
                    readOnlyTaskContextSnapshot = readOnlyTaskContextSnapshot,
                    contextFocus = contextFocus,
                    voiceSettingRoutingContext = voiceSettingRoutingContext,
                    repairProfile = repairProfile,
                    topLevelRouteContext = topLevelRouteContext
                ),
                failureCode = failureCode,
                failedRoute = failedRoute
            )
            val repairedDecision = when (repairProfile) {
                ConversationRepairProfile.GENERAL -> parseCanonicalDecision(
                    rawContent = repairContent,
                    source = SOURCE_SCHEMA_REPAIR,
                    normalizedTextForTopLevelValidation = normalizedText,
                    topLevelRouteContext = topLevelRouteContext,
                    capturedTaskContextSnapshot = capturedTaskContextSnapshot
                )
                ConversationRepairProfile.NO_CONTEXT_MUTATION_REPAIR ->
                    parseCompactTaskCommandRepair(
                        rawContent = repairContent,
                        normalizedText = normalizedText
                    )
            }
            validateOperationalBreakdownRouting(normalizedText, repairedDecision)
            validateContextReadAuthority(
                decision = repairedDecision,
                readOnlyTaskContextSnapshot = readOnlyTaskContextSnapshot
            )
            val authorityReconciledDecision = reconcileContextActionAuthority(
                normalizedText = normalizedText,
                decision = repairedDecision,
                capturedTaskContextSnapshot = capturedTaskContextSnapshot,
                contextFocus = contextFocus
            )
            validateContextActionAuthority(
                decision = authorityReconciledDecision,
                readOnlyTaskContextSnapshot = readOnlyTaskContextSnapshot,
                contextFocus = contextFocus
            )
            if (failureCode == ConversationDecisionFailureCode.OPERATIONAL_BREAKDOWN_MISROUTED.name) {
                Log.d(
                    "BREAKDOWN_ROUTING_GUARD",
                    "result=REPAIRED route=${repairedDecision.route.name}"
                )
            }
            if (repairProfile == ConversationRepairProfile.NO_CONTEXT_MUTATION_REPAIR) {
                Log.d(
                    "NO_CONTEXT_MUTATION_REPAIR",
                    "result=${authorityReconciledDecision.route.name}"
                )
            }
            Log.d("CONVO_ORCH_SCHEMA", "repair response accepted")
            authorityReconciledDecision
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
            ConversationRoute.APP_NAVIGATION -> decision
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
            ConversationRoute.SETTINGS_ACTION -> decision
            ConversationRoute.SETTINGS_READ -> decision
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

    fun clearPendingDialogueAction() {
        memory.clearPendingDialogueAction()
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

    suspend fun processQueryCountFollowUp(normalizedText: String): QueryCountFollowUpDecision =
        QueryCountFollowUpParser.parse(
            conversationAgentClient.processQueryCountFollowUp(normalizedText)
        )

    suspend fun processPendingContextActionTarget(
        normalizedText: String,
        readOnlyTaskContextSnapshot: String,
        pendingAction: ConversationContextAction
    ): PendingContextActionTargetDecision = PendingContextActionTargetParser.parse(
        conversationAgentClient.processPendingContextActionTarget(
            userText = normalizedText,
            taskContextSnapshot = readOnlyTaskContextSnapshot,
            pendingAction = pendingAction
        )
    )

    private fun parseCanonicalDecision(
        rawContent: String,
        source: String = SOURCE_CONVERSATION_AGENT,
        normalizedTextForTopLevelValidation: String? = null,
        topLevelRouteContext: ConversationTopLevelRouteConsistencyContext =
            ConversationTopLevelRouteConsistencyContext(),
        capturedTaskContextSnapshot: ReadOnlyTaskContextSnapshot? = null
    ): ConversationDecision {
        val result = if (normalizedTextForTopLevelValidation == null) {
            parser.parseWithReport(rawContent)
        } else {
            parser.parseCanonicalizedWithReport(rawContent)
        }
        if (result.canonicalizationReport.wasCanonicalized) {
            Log.d(
                "CONVO_DECISION_CANONICALIZED",
                "route=${result.decision.route.name} " +
                    "fields=${result.canonicalizationReport.fields.joinToString(",")}"
            )
        }
        val decision = result.decision.copy(source = source)
        var groundedDecision = decision
        if (normalizedTextForTopLevelValidation != null &&
            capturedTaskContextSnapshot != null
        ) {
            val titleGrounding = ContextReadTitleGroundingPolicy.reconcile(
                normalizedText = normalizedTextForTopLevelValidation,
                decision = groundedDecision,
                capturedSnapshot = capturedTaskContextSnapshot,
                currentGeneration = capturedTaskContextSnapshot.generation
            )
            groundedDecision = when (titleGrounding.result) {
                ContextReadTitleGroundingResult.MATCHED_MODEL_REF,
                ContextReadTitleGroundingResult.GROUNDED_BLANK_MODEL_REF -> {
                    Log.d(
                        "CONTEXT_READ_TITLE_GROUNDING",
                        "result=${titleGrounding.result} ref=${titleGrounding.expectedRef}"
                    )
                    titleGrounding.decision
                }
                ContextReadTitleGroundingResult.AMBIGUOUS_TITLE ->
                    ConversationDecision(
                        route = ConversationRoute.ASK_CLARIFICATION,
                        reply = "More than one supplied task matches that title. Please say its position in the list.",
                        confidence = groundedDecision.confidence,
                        listenAgain = true,
                        source = source
                    )
                ContextReadTitleGroundingResult.MODEL_REF_MISMATCH,
                ContextReadTitleGroundingResult.STALE_GENERATION ->
                    throw ConversationSchemaException(
                        message = "CONTEXT_READ title evidence does not match the selected ref",
                        decisionFailureCode = ConversationDecisionFailureCode.INVALID_CONTEXT_REF,
                        failedRoute = ConversationRoute.CONTEXT_READ
                    )
                ContextReadTitleGroundingResult.NOT_APPLICABLE -> groundedDecision
            }
        }
        if (normalizedTextForTopLevelValidation != null) {
            validateTopLevelRouteConsistency(
                normalizedText = normalizedTextForTopLevelValidation,
                decision = groundedDecision,
                context = topLevelRouteContext
            )
            ConversationDecisionContractValidator.validate(groundedDecision)
        }
        return groundedDecision
    }

    private fun validateOperationalBreakdownRouting(
        normalizedText: String,
        decision: ConversationDecision
    ) {
        if (!OperationalBreakdownRoutingGuard.requiresRepair(normalizedText, decision)) return
        Log.d(
            "BREAKDOWN_ROUTING_GUARD",
            "primaryRoute=${ConversationRoute.DIRECT_REPLY.name} result=REPAIR_REQUIRED"
        )
        throw ConversationSchemaException(
            message = "Explicit operational breakdown request was routed as DIRECT_REPLY",
            decisionFailureCode =
                ConversationDecisionFailureCode.OPERATIONAL_BREAKDOWN_MISROUTED,
            failedRoute = ConversationRoute.DIRECT_REPLY
        )
    }

    private fun validateTopLevelRouteConsistency(
        normalizedText: String,
        decision: ConversationDecision,
        context: ConversationTopLevelRouteConsistencyContext
    ) {
        try {
            ConversationTopLevelRouteConsistencyGuard.validate(
                normalizedText = normalizedText,
                decision = decision,
                context = context
            )
        } catch (failure: ConversationSchemaException) {
            Log.d(
                "CONVERSATION_TOP_LEVEL_ROUTE_GUARD",
                "failure=${failure.decisionFailureCode} primaryRoute=${decision.route} " +
                    "result=REPAIR_REQUIRED"
            )
            throw failure
        }
    }

    private fun repairFailureCode(failure: Exception): String = when (failure) {
        is ConversationSchemaException ->
            failure.decisionFailureCode?.name ?: FAILURE_CODE_SCHEMA_UNKNOWN
        is ConversationAgentResponseException -> FAILURE_CODE_AGENT_RESPONSE
        else -> FAILURE_CODE_UNEXPECTED
    }

    private fun validateTaskRouteConsistency(text: String, decision: ConversationDecision) {
        val failure = when {
            decision.route == ConversationRoute.DAILY_BRIEFING &&
                TaskCommandContradictionDetector.isExplicitTemporalTaskQuery(text) ->
                ConversationDecisionFailureCode.TEMPORAL_TASK_QUERY_MISROUTED
            decision.route in setOf(
                ConversationRoute.CONTEXT_AWARE_SUGGESTION,
                ConversationRoute.DAILY_BRIEFING,
                ConversationRoute.DIRECT_REPLY
            ) && TaskCommandContradictionDetector.isNamedCompletionMutation(text) ->
                ConversationDecisionFailureCode.NAMED_COMPLETION_MUTATION_MISROUTED
            decision.route in setOf(
                ConversationRoute.DIRECT_REPLY,
                ConversationRoute.CONTEXT_AWARE_SUGGESTION,
                ConversationRoute.DAILY_BRIEFING,
                ConversationRoute.ASK_CLARIFICATION
            ) && TaskCommandContradictionDetector.isNamedTaskRename(text) ->
                ConversationDecisionFailureCode.NAMED_RENAME_MUTATION_MISROUTED
            else -> return
        }
        Log.d(
            "CONVERSATION_TASK_ROUTE_GUARD",
            "failure=$failure primaryRoute=${decision.route} result=REPAIR_REQUIRED"
        )
        throw ConversationSchemaException(
            "Task command contradicts special route",
            decisionFailureCode = failure,
            failedRoute = decision.route
        )
    }

    private suspend fun repairTaskCommandRoute(
        normalizedText: String,
        failureCode: String,
        failedRoute: ConversationRoute
    ): ConversationDecision = try {
        // Deliberately no app context, memory, refs, or task snapshot in this request.
        val raw = conversationAgentClient.processTaskCommandRouteRepair(
            normalizedText, failureCode, failedRoute
        )
        parseCompactTaskCommandRepair(raw, normalizedText).also {
            Log.d("CONVERSATION_TASK_ROUTE_REPAIR", "result=${it.route}")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.d("CONVERSATION_TASK_ROUTE_REPAIR", "result=REJECTED")
        throw ConversationOrchestratorException("Task route repair failed closed", e)
    }

    // The two repairs share the same strict three-field wire contract and Android reconstruction.
    private fun parseCompactTaskCommandRepair(
        rawContent: String,
        normalizedText: String
    ): ConversationDecision {
        val compactDecision = noContextMutationRepairParser.parse(rawContent)
        val route = when (compactDecision.move) {
            NoContextMutationRepairMove.TASK_COMMAND -> ConversationRoute.TASK_COMMAND
            NoContextMutationRepairMove.ASK_CLARIFICATION ->
                ConversationRoute.ASK_CLARIFICATION
        }
        return ConversationDecision(
            route = route,
            taskText = if (route == ConversationRoute.TASK_COMMAND) normalizedText else "",
            reply = if (route == ConversationRoute.ASK_CLARIFICATION) {
                compactDecision.reply
            } else {
                ""
            },
            contextRef = "",
            contextDetail = ConversationContextDetail.NONE,
            contextAction = ConversationContextAction.NONE,
            settingAction = ConversationSettingAction.NONE,
            settingTarget = ConversationSettingTarget.NONE,
            queryReadingMove = ConversationQueryReadingMove.NONE,
            queryPresentationHint = TaskQueryPresentation.NONE,
            confidence = compactDecision.confidence,
            listenAgain = true,
            source = SOURCE_SCHEMA_REPAIR
        )
    }

    /** CONTEXT_READ may select only a temporary ref in Android's captured snapshot. */
    private fun validateContextReadAuthority(
        decision: ConversationDecision,
        readOnlyTaskContextSnapshot: String
    ) {
        if (decision.route != ConversationRoute.CONTEXT_READ) return
        val suppliedRefs = suppliedContextRefs(readOnlyTaskContextSnapshot)
        val selectedRef = decision.contextRef.uppercase(Locale.ROOT)
        if (selectedRef !in suppliedRefs) {
            throw ConversationSchemaException(
                message = "CONTEXT_READ selected a ref not supplied by Android",
                decisionFailureCode = ConversationDecisionFailureCode.INVALID_CONTEXT_REF,
                failedRoute = ConversationRoute.CONTEXT_READ
            )
        }
    }

    private fun reconcileContextActionAuthority(
        normalizedText: String,
        decision: ConversationDecision,
        capturedTaskContextSnapshot: ReadOnlyTaskContextSnapshot?,
        contextFocus: ConversationContextFocus?
    ): ConversationDecision {
        val snapshot = capturedTaskContextSnapshot ?: return decision
        val binding = ContextActionAuthorityBindingPolicy.reconcile(
            normalizedText = normalizedText,
            decision = decision,
            capturedSnapshot = snapshot,
            contextFocus = contextFocus
        )
        if (binding.result == ContextActionAuthorityBindingResult.BOUND_TO_CURRENT_FOCUS) {
            Log.d(
                "CONTEXT_ACTION_AUTHORITY_BINDING",
                "modelRef=${binding.modelRef} authoritativeRef=${binding.authoritativeRef} " +
                    "result=BOUND_TO_CURRENT_FOCUS reason=SINGLE_RESULT_DEICTIC_FOCUS"
            )
        }
        return binding.decision
    }

    /** CONTEXT_ACTION requires either supplied task context or Android-validated focus authority. */
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
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
        val selectedRef = decision.contextRef.trim().uppercase(Locale.ROOT)
        if (selectedRef.isBlank()) {
            if (suppliedRefs.isEmpty() && focusRef == null) {
                throw ConversationSchemaException(
                    message = "CONTEXT_ACTION has no Android grounding authority",
                    decisionFailureCode = ConversationDecisionFailureCode.INVALID_CONTEXT_REF,
                    failedRoute = ConversationRoute.CONTEXT_ACTION
                )
            }
            Log.d(
                "CONTEXT_ACTION_AUTHORITY",
                "modelRef= result=DEFERRED_TO_ANDROID_GROUNDING"
            )
            return
        }
        if (selectedRef !in suppliedRefs && selectedRef != focusRef) {
            throw ConversationSchemaException(
                message = "CONTEXT_ACTION selected a ref not supplied by Android",
                decisionFailureCode = ConversationDecisionFailureCode.INVALID_CONTEXT_REF,
                failedRoute = ConversationRoute.CONTEXT_ACTION
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
        contextFocus: ConversationContextFocus?,
        voiceSettingRoutingContext: VoiceSettingRoutingContext,
        repairProfile: ConversationRepairProfile,
        topLevelRouteContext: ConversationTopLevelRouteConsistencyContext
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
        appendLine("Supplied temporary ref count: ${refs.size}")
        appendLine(
            "Current validated focus ref: " +
                focusRef.orEmpty().ifBlank { "NONE" }
        )
        append(
            "Context action authority available: " +
                (refs.isNotEmpty() || !focusRef.isNullOrBlank())
        )
        appendLine()
        appendLine()
        appendLine(voiceSettingRoutingContext.toPromptText())
        appendLine()
        append(ConversationRepairProfileSelector.marker(repairProfile))
        appendLine()
        appendLine()
        append(
            "Recent saved-routine routing context available: " +
                topLevelRouteContext.recentSavedRoutineAction
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
        contextFocus: ConversationContextFocus?,
        voiceSettingRoutingContext: VoiceSettingRoutingContext
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
        appendLine()
        appendLine()
        append(voiceSettingRoutingContext.toPromptText())
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
        val TASK_ROUTE_CONTRADICTIONS = setOf(
            ConversationDecisionFailureCode.TEMPORAL_TASK_QUERY_MISROUTED.name,
            ConversationDecisionFailureCode.NAMED_COMPLETION_MUTATION_MISROUTED.name,
            ConversationDecisionFailureCode.NAMED_RENAME_MUTATION_MISROUTED.name
        )
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
