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
    ): TaskDetailEditDecisionValidation = validate(
        decision = decision,
        requestedField = requestedField,
        allowedMoves = TaskDetailEditAgentContext.allowedMoves(requestedField),
        saveConfirmation = false
    )

    fun validate(
        decision: TaskDetailEditAgentDecision,
        context: TaskDetailEditAgentContext
    ): TaskDetailEditDecisionValidation = validate(
        decision = decision,
        requestedField = context.requestedField,
        allowedMoves = context.allowedMoves,
        saveConfirmation = context.interactionState == SAVE_CONFIRMATION_STATE
    )

    private fun validate(
        decision: TaskDetailEditAgentDecision,
        requestedField: TaskDetailEditField?,
        allowedMoves: List<TaskDetailEditAgentMove>,
        saveConfirmation: Boolean
    ): TaskDetailEditDecisionValidation {
        if (decision.move !in allowedMoves) return rejected("MOVE_NOT_ALLOWED_FOR_STATE")
        if (decision.move != TaskDetailEditAgentMove.UNKNOWN && decision.confidence < MIN_CONFIDENCE) {
            return rejected("LOW_CONFIDENCE")
        }
        if (decision.move == TaskDetailEditAgentMove.CONFIRM_SAVE &&
            decision.confidence < CONFIRM_CONFIDENCE
        ) {
            return rejected("LOW_CONFIRMATION_CONFIDENCE")
        }
        val allowed = when (decision.move) {
            TaskDetailEditAgentMove.SET_TITLE ->
                saveConfirmation || requestedField == TaskDetailEditField.TITLE
            TaskDetailEditAgentMove.SET_DATE ->
                saveConfirmation || requestedField == TaskDetailEditField.DATE
            TaskDetailEditAgentMove.SET_TIME ->
                saveConfirmation || requestedField == TaskDetailEditField.TIME
            TaskDetailEditAgentMove.SET_SCHEDULE ->
                saveConfirmation || requestedField in TEMPORAL_FIELDS
            TaskDetailEditAgentMove.CONFIRM_SAVE,
            TaskDetailEditAgentMove.REJECT_SAVE,
            TaskDetailEditAgentMove.REQUEST_TITLE_CHANGE,
            TaskDetailEditAgentMove.REQUEST_DATE_CHANGE,
            TaskDetailEditAgentMove.REQUEST_TIME_CHANGE,
            TaskDetailEditAgentMove.READ_TITLE,
            TaskDetailEditAgentMove.READ_DATE,
            TaskDetailEditAgentMove.READ_TIME,
            TaskDetailEditAgentMove.READ_SCHEDULE -> saveConfirmation
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
            TaskDetailEditAgentMove.CONFIRM_SAVE -> TaskDetailEditProposal.ConfirmSave
            TaskDetailEditAgentMove.REJECT_SAVE -> TaskDetailEditProposal.RejectSave
            TaskDetailEditAgentMove.SET_TITLE -> TaskDetailEditProposal.Title(decision.title.trim())
            TaskDetailEditAgentMove.SET_DATE -> TaskDetailEditProposal.Schedule(decision.dateText.trim(), null)
            TaskDetailEditAgentMove.SET_TIME -> TaskDetailEditProposal.Schedule(null, decision.timeText.trim())
            TaskDetailEditAgentMove.SET_SCHEDULE -> TaskDetailEditProposal.Schedule(
                decision.dateText.trim().ifEmpty { null },
                decision.timeText.trim().ifEmpty { null }
            )
            TaskDetailEditAgentMove.ASK_CLARIFICATION ->
                TaskDetailEditProposal.Clarification(decision.clarification.trim())
            TaskDetailEditAgentMove.REQUEST_TITLE_CHANGE ->
                TaskDetailEditProposal.RequestField(TaskDetailEditField.TITLE)
            TaskDetailEditAgentMove.REQUEST_DATE_CHANGE ->
                TaskDetailEditProposal.RequestField(TaskDetailEditField.DATE)
            TaskDetailEditAgentMove.REQUEST_TIME_CHANGE ->
                TaskDetailEditProposal.RequestField(TaskDetailEditField.TIME)
            TaskDetailEditAgentMove.READ_TITLE ->
                TaskDetailEditProposal.ReadDraft(TaskDetailDraftReadTarget.TITLE)
            TaskDetailEditAgentMove.READ_DATE ->
                TaskDetailEditProposal.ReadDraft(TaskDetailDraftReadTarget.DATE)
            TaskDetailEditAgentMove.READ_TIME ->
                TaskDetailEditProposal.ReadDraft(TaskDetailDraftReadTarget.TIME)
            TaskDetailEditAgentMove.READ_SCHEDULE ->
                TaskDetailEditProposal.ReadDraft(TaskDetailDraftReadTarget.SCHEDULE)
            TaskDetailEditAgentMove.CANCEL -> TaskDetailEditProposal.Cancel
            TaskDetailEditAgentMove.UNKNOWN -> TaskDetailEditProposal.Unknown
        }
        return TaskDetailEditDecisionValidation(true, proposal, "ACCEPTED")
    }

    private fun rejected(reason: String) =
        TaskDetailEditDecisionValidation(false, TaskDetailEditProposal.Unknown, reason)

    companion object {
        const val MIN_CONFIDENCE = 0.80
        const val CONFIRM_CONFIDENCE = 0.90
        private const val MAX_QUESTION_LENGTH = 180
        private const val SAVE_CONFIRMATION_STATE = "WAITING_FOR_SAVE_CONFIRMATION"
        private val TEMPORAL_FIELDS = setOf(TaskDetailEditField.DATE, TaskDetailEditField.TIME)
    }
}
