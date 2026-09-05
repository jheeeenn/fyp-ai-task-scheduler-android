package com.example.myapplication.ai.conversation.taskedit

import android.util.Log
import com.example.myapplication.voice.BoundedConfirmationPolicy
import com.example.myapplication.voice.BoundedConfirmationResult
import com.example.myapplication.voice.TextNormalizer
import kotlinx.coroutines.CancellationException

fun interface EditTaskSemanticClient {
    suspend fun interpretEditTaskMove(userText: String, contextSummary: String): String
}

class EditTaskSemanticOrchestrator(
    private val semanticClient: EditTaskSemanticClient,
    private val parser: EditTaskSemanticDecisionParser = EditTaskSemanticDecisionParser(),
    private val validator: EditTaskSemanticDecisionValidator =
        EditTaskSemanticDecisionValidator()
) {
    fun resolveImmediate(
        userText: String,
        context: EditTaskAgentContext
    ): EditTaskMoveResolution? {
        val normalized = TextNormalizer.normalize(userText)
        val confirmation = BoundedConfirmationPolicy.resolve(normalized).result
        if (EditTaskInteractionPolicy.isCollecting(context.interactionState) &&
            confirmation in setOf(BoundedConfirmationResult.AFFIRM, BoundedConfirmationResult.REJECT)
        ) {
            return unknown(agentAttempted = false, reason = "FIELD_VALUE_REQUIRED")
        }
        if (context.interactionState in SAVE_CONFIRMATION_STATES) {
            when (confirmation) {
                BoundedConfirmationResult.AFFIRM ->
                    return local(EditTaskSemanticMove.CONFIRM_SAVE, "BOUNDED_CONFIRMATION")
                BoundedConfirmationResult.REJECT ->
                    return local(EditTaskSemanticMove.REJECT_SAVE, "BOUNDED_REJECTION")
                BoundedConfirmationResult.CANCEL ->
                    return local(EditTaskSemanticMove.CANCEL, "BOUNDED_CANCELLATION")
                BoundedConfirmationResult.UNKNOWN -> Unit
            }
        } else if (confirmation == BoundedConfirmationResult.CANCEL) {
            return local(EditTaskSemanticMove.CANCEL, "BOUNDED_CANCELLATION")
        }

        return when (normalized) {
            "title", "the title" ->
                local(EditTaskSemanticMove.REQUEST_TITLE_CHANGE, "EXACT_FIELD_SELECTION")
            "date", "the date" ->
                local(EditTaskSemanticMove.REQUEST_DATE_CHANGE, "EXACT_FIELD_SELECTION")
            "time", "the time" ->
                local(EditTaskSemanticMove.REQUEST_TIME_CHANGE, "EXACT_FIELD_SELECTION")
            "delete", "delete task", "delete this", "delete this task" ->
                local(EditTaskSemanticMove.DELETE, "EXACT_DELETE")
            else -> null
        }
    }

    suspend fun resolve(
        userText: String,
        context: EditTaskAgentContext
    ): EditTaskMoveResolution {
        return try {
            val primary = semanticClient.interpretEditTaskMove(userText, context.toPromptText())
            val decision = parser.parse(primary)
            if (decision.move == EditTaskSemanticMove.UNKNOWN) {
                attemptRepair(userText, context)
            } else {
                validatedResolution(decision, context, EditTaskMoveSource.CONVERSATION_AGENT_PRIMARY)
                    ?: unknown(agentAttempted = true, reason = "PRIMARY_VALIDATION_REJECTED")
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            attemptRepair(userText, context)
        }
    }

    private suspend fun attemptRepair(
        userText: String,
        context: EditTaskAgentContext
    ): EditTaskMoveResolution = try {
        val repaired = semanticClient.interpretEditTaskMove(userText, context.toRepairPromptText())
        val decision = parser.parse(repaired)
        if (decision.move == EditTaskSemanticMove.UNKNOWN) {
            unknown(agentAttempted = true, reason = "REPAIR_ABSTAINED")
        } else {
            validatedResolution(decision, context, EditTaskMoveSource.CONVERSATION_AGENT_REPAIR)
                ?: unknown(agentAttempted = true, reason = "REPAIR_VALIDATION_REJECTED")
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        unknown(agentAttempted = true, reason = "REPAIR_FAILED")
    }

    private fun validatedResolution(
        decision: EditTaskSemanticDecision,
        context: EditTaskAgentContext,
        source: EditTaskMoveSource
    ): EditTaskMoveResolution? {
        val validation = validator.validate(decision, context)
        if (!validation.accepted) {
            Log.d(
                "EDIT_SEMANTIC_REJECTED",
                "state=${context.interactionState} reason=${validation.reason}"
            )
            return null
        }
        return EditTaskMoveResolution(
            move = decision.move,
            title = decision.title.trim(),
            dateText = decision.dateText.trim(),
            timeText = decision.timeText.trim(),
            source = source,
            confidence = decision.confidence,
            agentAttempted = true,
            reason = validation.reason
        )
    }

    private fun local(move: EditTaskSemanticMove, reason: String) =
        EditTaskMoveResolution(
            move = move,
            source = EditTaskMoveSource.LOCAL_FAST_PATH,
            confidence = 1.0,
            agentAttempted = false,
            reason = reason
        )

    private fun unknown(agentAttempted: Boolean, reason: String) =
        EditTaskMoveResolution(
            move = EditTaskSemanticMove.UNKNOWN,
            source = EditTaskMoveSource.DETERMINISTIC_UNKNOWN,
            confidence = 0.0,
            agentAttempted = agentAttempted,
            reason = reason
        )

    private companion object {
        val SAVE_CONFIRMATION_STATES = setOf(
            EditTaskInteractionState.WAITING_FOR_SAVE_CONFIRMATION,
            EditTaskInteractionState.WAITING_FOR_RELATIVE_TEMPORAL_CONFIRMATION
        )
    }
}
