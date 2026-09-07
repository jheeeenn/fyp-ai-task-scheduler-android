package com.example.myapplication.ai.conversation.createdraft

import android.util.Log
import com.example.myapplication.ai.temporal.TemporalExpressionResolver
import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateDraftMove
import com.example.myapplication.voice.CreateDraftMoveInterpreter
import com.example.myapplication.voice.CreateTaskDialogState
import kotlinx.coroutines.CancellationException

fun interface CreateDraftSemanticClient {
    suspend fun interpretCreateDraftMove(userText: String, contextSummary: String): String
}

enum class CreateDraftMoveSource(val logValue: String) {
    CONVERSATION_AGENT_PRIMARY("conversation_agent_primary"),
    CONVERSATION_AGENT_REPAIR("conversation_agent_repair"),
    LOCAL_SAFETY_REFLEX("local_safety_reflex"),
    LOCAL_FAILURE_FALLBACK("local_failure_fallback"),
    DETERMINISTIC_UNKNOWN("deterministic_unknown")
}

data class CreateDraftMoveResolution(
    val move: CreateDraftMove,
    val source: CreateDraftMoveSource,
    val confidence: Double,
    val agentAttempted: Boolean
)

class CreateDraftSemanticOrchestrator(
    private val localInterpreter: CreateDraftMoveInterpreter,
    private val semanticClient: CreateDraftSemanticClient,
    private val parser: CreateDraftAgentDecisionParser = CreateDraftAgentDecisionParser(),
    private val validator: CreateDraftAgentDecisionValidator = CreateDraftAgentDecisionValidator(),
    private val temporalResolver: TemporalExpressionResolver = TemporalExpressionResolver()
) {
    fun proposeLocal(userText: String, state: CreateTaskDialogState): CreateDraftMove =
        localInterpreter.interpret(userText, state)

    fun resolveImmediate(
        localCandidate: CreateDraftMove,
        state: CreateTaskDialogState
    ): CreateDraftMoveResolution? {
        if (state == CreateTaskDialogState.READY_TO_SAVE) {
            return deterministicUnknown(agentAttempted = false)
        }
        if (localCandidate == CreateDraftMove.Cancel) {
            return CreateDraftMoveResolution(
                move = CreateDraftMove.Cancel,
                source = CreateDraftMoveSource.LOCAL_SAFETY_REFLEX,
                confidence = 1.0,
                agentAttempted = false
            )
        }
        return null
    }

    suspend fun resolve(
        userText: String,
        state: CreateTaskDialogState,
        context: CreateDraftAgentContext,
        localCandidate: CreateDraftMove
    ): CreateDraftMoveResolution {
        resolveImmediate(localCandidate, state)?.let { return it }

        val rawContent = try {
            semanticClient.interpretCreateDraftMove(userText, context.toPromptText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CREATE_MOVE_PRIMARY", "state=$state category=REQUEST_FAILED", e)
            return localFailureFallback(userText, localCandidate, state)
        }
        val decision = try {
            parser.parse(rawContent)
        } catch (e: Exception) {
            Log.e("CREATE_MOVE_PRIMARY", "state=$state category=SCHEMA_INVALID", e)
            return attemptSemanticRepair(
                userText, state, context, localCandidate,
                CreateDraftRepairReason.PRIMARY_SCHEMA_INVALID
            )
        }

        if (decision.move == CreateDraftAgentMoveType.UNKNOWN) {
            Log.d("CREATE_MOVE_PRIMARY", "state=$state category=AGENT_ABSTAINED")
            return attemptSemanticRepair(
                userText, state, context, localCandidate,
                CreateDraftRepairReason.PRIMARY_ABSTAINED
            )
        }
        if (decision.move == CreateDraftAgentMoveType.CONFIRM_SAVE &&
            !isCompatibleWithAgentConfirmation(localCandidate)
        ) {
            Log.d("CREATE_MOVE_PRIMARY", "state=$state category=CONFIRM_CONTRADICTION_REJECTED")
            return attemptSemanticRepair(
                userText, state, context, localCandidate,
                CreateDraftRepairReason.PRIMARY_STATE_VALIDATION_REJECTED
            )
        }
        val validation = validator.validate(decision, state)
        if (!validation.accepted) {
            Log.d("CREATE_MOVE_PRIMARY", "state=$state category=VALIDATION_REJECTED")
            return attemptSemanticRepair(
                userText, state, context, localCandidate,
                CreateDraftRepairReason.PRIMARY_STATE_VALIDATION_REJECTED
            )
        }
        if (isTemporalMeaningIncomplete(userText, validation.move)) {
            Log.d("CREATE_MOVE_PRIMARY", "state=$state category=TEMPORAL_MEANING_INCOMPLETE")
            return attemptSemanticRepair(
                userText, state, context, localCandidate,
                CreateDraftRepairReason.PRIMARY_TEMPORAL_MEANING_INCOMPLETE
            )
        }
        Log.d("CREATE_MOVE_PRIMARY", "state=$state category=ACCEPTED")
        return CreateDraftMoveResolution(
            move = validation.move,
            source = CreateDraftMoveSource.CONVERSATION_AGENT_PRIMARY,
            confidence = decision.confidence,
            agentAttempted = true
        )
    }

    private suspend fun attemptSemanticRepair(
        userText: String,
        state: CreateTaskDialogState,
        context: CreateDraftAgentContext,
        localCandidate: CreateDraftMove,
        reason: CreateDraftRepairReason
    ): CreateDraftMoveResolution {
        Log.d("CREATE_MOVE_REPAIR", "state=$state category=REPAIR_ATTEMPTED reason=${reason.name}")
        return try {
            val rawContent = semanticClient.interpretCreateDraftMove(
                userText,
                context.toRepairPromptText(reason)
            )
            val decision = parser.parse(rawContent)
            if (decision.move == CreateDraftAgentMoveType.UNKNOWN) {
                Log.d("CREATE_MOVE_REPAIR", "state=$state category=REPAIR_ABSTAINED")
                return repairFailureFallback(reason, userText, localCandidate, state)
            }
            if (decision.move == CreateDraftAgentMoveType.CONFIRM_SAVE &&
                !isCompatibleWithAgentConfirmation(localCandidate)
            ) {
                Log.d(
                    "CREATE_MOVE_REPAIR",
                    "state=$state category=REPAIR_REJECTED reason=CONFIRM_CONTRADICTION"
                )
                return repairFailureFallback(reason, userText, localCandidate, state)
            }
            val validation = validator.validate(decision, state)
            if (!validation.accepted) {
                Log.d(
                    "CREATE_MOVE_REPAIR",
                    "state=$state category=REPAIR_REJECTED reason=STATE_OR_CONFIDENCE"
                )
                return repairFailureFallback(reason, userText, localCandidate, state)
            }
            if (isTemporalMeaningIncomplete(userText, validation.move)) {
                Log.d(
                    "CREATE_MOVE_REPAIR",
                    "state=$state category=REPAIR_REJECTED reason=TEMPORAL_MEANING_INCOMPLETE"
                )
                return deterministicUnknown(agentAttempted = true)
            }
            Log.d("CREATE_MOVE_REPAIR", "state=$state category=REPAIR_ACCEPTED")
            CreateDraftMoveResolution(
                move = validation.move,
                source = CreateDraftMoveSource.CONVERSATION_AGENT_REPAIR,
                confidence = decision.confidence,
                agentAttempted = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CREATE_MOVE_REPAIR", "state=$state category=REPAIR_FAILED", e)
            repairFailureFallback(reason, userText, localCandidate, state)
        }
    }

    private fun repairFailureFallback(
        reason: CreateDraftRepairReason,
        userText: String,
        localCandidate: CreateDraftMove,
        state: CreateTaskDialogState
    ): CreateDraftMoveResolution = when (reason) {
        CreateDraftRepairReason.PRIMARY_ABSTAINED,
        CreateDraftRepairReason.PRIMARY_STATE_VALIDATION_REJECTED,
        CreateDraftRepairReason.PRIMARY_SCHEMA_INVALID ->
            localFailureFallback(userText, localCandidate, state)
        CreateDraftRepairReason.PRIMARY_TEMPORAL_MEANING_INCOMPLETE ->
            deterministicUnknown(agentAttempted = true)
    }

    private fun localFailureFallback(
        userText: String,
        localCandidate: CreateDraftMove,
        state: CreateTaskDialogState
    ): CreateDraftMoveResolution =
        validatedLocalFallback(userText, localCandidate, state)
            ?: deterministicUnknown(agentAttempted = true)

    private fun validatedLocalFallback(
        userText: String,
        localCandidate: CreateDraftMove,
        state: CreateTaskDialogState
    ): CreateDraftMoveResolution? {
        if (localCandidate is CreateDraftMove.ProvideField) {
            val safeLiteral = when (localCandidate.field) {
                CreateDraftField.TITLE -> localInterpreter.isConservativeBareTitleCandidate(userText)
                CreateDraftField.DATE ->
                    temporalResolver.hasExplicitDateExpression(userText) &&
                        !temporalResolver.hasExplicitTimeExpression(userText)
                CreateDraftField.TIME ->
                    temporalResolver.hasExplicitTimeExpression(userText) &&
                        !temporalResolver.hasExplicitDateExpression(userText)
            }
            if (!safeLiteral) return null
        }
        if (isTemporalMeaningIncomplete(userText, localCandidate) &&
            localCandidate !is CreateDraftMove.ApplyUnspecifiedCorrection
        ) {
            return null
        }
        val validation = validator.validateLocalCandidate(localCandidate, state)
        return if (validation.accepted) {
            CreateDraftMoveResolution(
                move = validation.move,
                source = CreateDraftMoveSource.LOCAL_FAILURE_FALLBACK,
                confidence = 1.0,
                agentAttempted = true
            )
        } else {
            null
        }
    }

    private fun isCompatibleWithAgentConfirmation(localCandidate: CreateDraftMove): Boolean =
        localCandidate == CreateDraftMove.ConfirmSave || localCandidate == CreateDraftMove.Unknown

    private fun isTemporalMeaningIncomplete(userText: String, move: CreateDraftMove): Boolean {
        if (!temporalResolver.hasExplicitDateExpression(userText) ||
            !temporalResolver.hasExplicitTimeExpression(userText)
        ) {
            return false
        }
        return when (move) {
            is CreateDraftMove.ChangeField ->
                move.field == CreateDraftField.DATE || move.field == CreateDraftField.TIME
            is CreateDraftMove.ProvideField ->
                move.field == CreateDraftField.DATE || move.field == CreateDraftField.TIME
            is CreateDraftMove.ApplyUnspecifiedCorrection -> true
            else -> false
        }
    }

    private fun deterministicUnknown(agentAttempted: Boolean) = CreateDraftMoveResolution(
        move = CreateDraftMove.Unknown,
        source = CreateDraftMoveSource.DETERMINISTIC_UNKNOWN,
        confidence = 0.0,
        agentAttempted = agentAttempted
    )
}
