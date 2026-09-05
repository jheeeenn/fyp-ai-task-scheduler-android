package com.example.myapplication.ai.conversation.taskedit

import com.example.myapplication.ai.temporal.RelativeTemporalProposalState
import com.example.myapplication.voice.BoundedConfirmationPolicy
import com.example.myapplication.voice.BoundedConfirmationResult

enum class EditTaskRelativeProposalLocalAction {
    CONFIRM,
    REJECT,
    CANCEL,
    REPEAT,
    END_SESSION
}

data class EditTaskRelativeProposalControlSignals(
    val explicitSave: Boolean,
    val explicitCancellation: Boolean,
    val explicitRepeat: Boolean,
    val explicitConversationExit: Boolean
)

enum class EditTaskRelativeProposalSemanticRoute {
    EDIT_SEMANTIC,
    RELATIVE_TEMPORAL_CORRECTION
}

/**
 * Pure routing policy for an active relative proposal. It chooses a path only; it never
 * calculates a schedule, mutates a draft, confirms persistence, or executes an operation.
 */
object EditTaskRelativeProposalRoutingPolicy {
    fun localAction(
        normalizedText: String,
        signals: EditTaskRelativeProposalControlSignals,
        foregroundState: EditTaskInteractionState =
            EditTaskInteractionState.WAITING_FOR_RELATIVE_TEMPORAL_CONFIRMATION
    ): EditTaskRelativeProposalLocalAction? {
        if (foregroundState !=
            EditTaskInteractionState.WAITING_FOR_RELATIVE_TEMPORAL_CONFIRMATION
        ) return null
        if (signals.explicitConversationExit) {
            return EditTaskRelativeProposalLocalAction.END_SESSION
        }
        if (signals.explicitCancellation) {
            return EditTaskRelativeProposalLocalAction.CANCEL
        }
        if (signals.explicitRepeat) {
            return EditTaskRelativeProposalLocalAction.REPEAT
        }
        if (signals.explicitSave) {
            return EditTaskRelativeProposalLocalAction.CONFIRM
        }
        return when (BoundedConfirmationPolicy.resolve(normalizedText).result) {
            BoundedConfirmationResult.AFFIRM -> EditTaskRelativeProposalLocalAction.CONFIRM
            BoundedConfirmationResult.REJECT -> EditTaskRelativeProposalLocalAction.REJECT
            BoundedConfirmationResult.CANCEL -> EditTaskRelativeProposalLocalAction.CANCEL
            BoundedConfirmationResult.UNKNOWN -> null
        }
    }

    fun semanticRoute(
        resolution: EditTaskMoveResolution,
        relativeProposalActive: Boolean = true
    ): EditTaskRelativeProposalSemanticRoute = when {
        !relativeProposalActive -> EditTaskRelativeProposalSemanticRoute.EDIT_SEMANTIC
        resolution.move in setOf(
            EditTaskSemanticMove.CHANGE_DATE,
            EditTaskSemanticMove.CHANGE_TIME,
            EditTaskSemanticMove.CHANGE_SCHEDULE
        ) ->
            EditTaskRelativeProposalSemanticRoute.RELATIVE_TEMPORAL_CORRECTION

        else -> EditTaskRelativeProposalSemanticRoute.EDIT_SEMANTIC
    }

    fun isCurrentProposal(
        capturedRevision: Int?,
        currentRevision: Int?,
        currentState: RelativeTemporalProposalState?
    ): Boolean = capturedRevision != null &&
        capturedRevision == currentRevision &&
        currentState == RelativeTemporalProposalState.ACTIVE
}
