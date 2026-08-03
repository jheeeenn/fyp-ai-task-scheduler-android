package com.example.myapplication.ai.conversation.taskdetailedit

data class TaskDetailEditDecisionValidation(
    val accepted: Boolean,
    val proposal: TaskDetailEditProposal,
    val reason: String
)

class TaskDetailEditAgentDecisionValidator {
    fun validate(
        decision: TaskDetailEditAgentDecision,
        requestedField: TaskDetailEditField
    ): TaskDetailEditDecisionValidation {
        if (decision.move != TaskDetailEditAgentMove.UNKNOWN && decision.confidence < MIN_CONFIDENCE) {
            return rejected("LOW_CONFIDENCE")
        }
        val allowed = when (decision.move) {
            TaskDetailEditAgentMove.SET_TITLE -> requestedField == TaskDetailEditField.TITLE
            TaskDetailEditAgentMove.SET_DATE -> requestedField == TaskDetailEditField.DATE
            TaskDetailEditAgentMove.SET_TIME -> requestedField == TaskDetailEditField.TIME
            TaskDetailEditAgentMove.SET_SCHEDULE -> requestedField in TEMPORAL_FIELDS
            TaskDetailEditAgentMove.ASK_CLARIFICATION,
            TaskDetailEditAgentMove.CANCEL,
            TaskDetailEditAgentMove.UNKNOWN -> true
        }
        if (!allowed) return rejected("FIELD_SCOPE_REJECTED")
        if (decision.move == TaskDetailEditAgentMove.ASK_CLARIFICATION &&
            (!decision.clarification.trim().endsWith('?') || decision.clarification.length > MAX_QUESTION_LENGTH)
        ) {
            return rejected("CLARIFICATION_NOT_DIRECT")
        }

        val proposal = when (decision.move) {
            TaskDetailEditAgentMove.SET_TITLE -> TaskDetailEditProposal.Title(decision.title.trim())
            TaskDetailEditAgentMove.SET_DATE -> TaskDetailEditProposal.Schedule(decision.dateText.trim(), null)
            TaskDetailEditAgentMove.SET_TIME -> TaskDetailEditProposal.Schedule(null, decision.timeText.trim())
            TaskDetailEditAgentMove.SET_SCHEDULE -> TaskDetailEditProposal.Schedule(
                decision.dateText.trim().ifEmpty { null },
                decision.timeText.trim().ifEmpty { null }
            )
            TaskDetailEditAgentMove.ASK_CLARIFICATION ->
                TaskDetailEditProposal.Clarification(decision.clarification.trim())
            TaskDetailEditAgentMove.CANCEL -> TaskDetailEditProposal.Cancel
            TaskDetailEditAgentMove.UNKNOWN -> TaskDetailEditProposal.Unknown
        }
        return TaskDetailEditDecisionValidation(true, proposal, "ACCEPTED")
    }

    private fun rejected(reason: String) =
        TaskDetailEditDecisionValidation(false, TaskDetailEditProposal.Unknown, reason)

    companion object {
        const val MIN_CONFIDENCE = 0.80
        private const val MAX_QUESTION_LENGTH = 180
        private val TEMPORAL_FIELDS = setOf(TaskDetailEditField.DATE, TaskDetailEditField.TIME)
    }
}
