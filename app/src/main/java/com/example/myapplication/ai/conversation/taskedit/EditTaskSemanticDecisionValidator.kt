package com.example.myapplication.ai.conversation.taskedit

data class EditTaskSemanticValidation(
    val accepted: Boolean,
    val reason: String
)

class EditTaskSemanticDecisionValidator {
    fun validate(
        decision: EditTaskSemanticDecision,
        context: EditTaskAgentContext
    ): EditTaskSemanticValidation {
        if (decision.move !in context.allowedMoves) return rejected("MOVE_NOT_ALLOWED_FOR_STATE")
        if (decision.move != EditTaskSemanticMove.UNKNOWN &&
            decision.confidence < MIN_CONFIDENCE
        ) {
            return rejected("LOW_CONFIDENCE")
        }
        if (decision.move == EditTaskSemanticMove.CONFIRM_SAVE &&
            decision.confidence < CONFIRM_CONFIDENCE
        ) {
            return rejected("LOW_CONFIRMATION_CONFIDENCE")
        }
        if (context.saveInFlight || context.deleteInFlight ||
            context.interactionState == EditTaskInteractionState.OPERATION_IN_FLIGHT
        ) {
            return rejected("OPERATION_IN_FLIGHT")
        }
        return EditTaskSemanticValidation(accepted = true, reason = "ACCEPTED")
    }

    private fun rejected(reason: String) =
        EditTaskSemanticValidation(accepted = false, reason = reason)

    companion object {
        const val MIN_CONFIDENCE = 0.80
        const val CONFIRM_CONFIDENCE = 0.90
    }
}
