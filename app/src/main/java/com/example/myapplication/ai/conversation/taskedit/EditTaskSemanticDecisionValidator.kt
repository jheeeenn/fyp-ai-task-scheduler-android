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
        if (decision.move in READ_MOVES &&
            (decision.title.isNotEmpty() ||
                decision.dateText.isNotEmpty() ||
                decision.timeText.isNotEmpty())
        ) {
            return rejected("READ_MOVE_HAS_VALUES")
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

        private val READ_MOVES = setOf(
            EditTaskSemanticMove.READ_TITLE,
            EditTaskSemanticMove.READ_DATE,
            EditTaskSemanticMove.READ_TIME,
            EditTaskSemanticMove.READ_SCHEDULE
        )
    }
}
