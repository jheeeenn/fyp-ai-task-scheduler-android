package com.example.myapplication.ai.conversation.taskdetailedit

data class TaskDetailEditAgentContext(
    val requestedField: TaskDetailEditField,
    val interactionState: String,
    val interactionGeneration: Long,
    val draftRevision: Long,
    val hasTitle: Boolean,
    val currentDueDate: String,
    val currentDueTime: String,
    val currentLocalDate: String,
    val currentLocalTime: String,
    val timezone: String,
    val currentSchedulePast: Boolean,
    val pendingClarification: String,
    val allowedMoves: List<TaskDetailEditAgentMove>
) {
    fun toPromptText(): String = buildString {
        appendLine("Requested field: ${requestedField.name}")
        appendLine("Interaction state: $interactionState")
        appendLine("Interaction generation: $interactionGeneration")
        appendLine("Draft revision: $draftRevision")
        appendLine("Draft title exists: $hasTitle")
        appendLine("Current draft due date: $currentDueDate")
        appendLine("Current draft due time: $currentDueTime")
        appendLine("Current local date: $currentLocalDate")
        appendLine("Current local time: $currentLocalTime")
        appendLine("Timezone: $timezone")
        appendLine("Current draft schedule is already past: $currentSchedulePast")
        appendLine("Pending clarification: $pendingClarification")
        appendLine("Allowed moves: ${allowedMoves.joinToString { it.name }}")
        appendLine("The user is answering one question about ${requestedField.name}.")
        appendLine("A natural correction may contain both a date and a time.")
        appendLine("Changing TIME may require changing DATE; changing DATE may include a time.")
        appendLine("Relative expressions use the current draft schedule as their base.")
        append("Propose meaning only. Android performs final validation and mutation.")
    }

    fun toRepairPromptText(): String = buildString {
        appendLine(toPromptText())
        appendLine()
        appendLine("Repair attempt: one bounded retry after malformed or UNKNOWN output.")
        append("Return one valid allowed move. Do not invent missing values or authority fields.")
    }

    companion object {
        fun allowedMoves(field: TaskDetailEditField): List<TaskDetailEditAgentMove> = when (field) {
            TaskDetailEditField.TITLE -> listOf(
                TaskDetailEditAgentMove.SET_TITLE,
                TaskDetailEditAgentMove.ASK_CLARIFICATION,
                TaskDetailEditAgentMove.CANCEL,
                TaskDetailEditAgentMove.UNKNOWN
            )
            TaskDetailEditField.DATE -> listOf(
                TaskDetailEditAgentMove.SET_DATE,
                TaskDetailEditAgentMove.SET_SCHEDULE,
                TaskDetailEditAgentMove.ASK_CLARIFICATION,
                TaskDetailEditAgentMove.CANCEL,
                TaskDetailEditAgentMove.UNKNOWN
            )
            TaskDetailEditField.TIME -> listOf(
                TaskDetailEditAgentMove.SET_TIME,
                TaskDetailEditAgentMove.SET_SCHEDULE,
                TaskDetailEditAgentMove.ASK_CLARIFICATION,
                TaskDetailEditAgentMove.CANCEL,
                TaskDetailEditAgentMove.UNKNOWN
            )
        }
    }
}
