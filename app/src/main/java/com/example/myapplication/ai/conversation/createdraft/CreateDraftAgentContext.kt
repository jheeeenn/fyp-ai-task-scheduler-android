package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateTaskDialogState

data class CreateDraftAgentContext(
    val currentState: String,
    val expectedField: String,
    val pendingReplacementField: String,
    val hasTitle: Boolean,
    val hasSelectedDate: Boolean,
    val hasSelectedTime: Boolean,
    val allowedMoves: List<String>,
    val authorityLimitations: List<String>
) {
    fun toPromptText(): String = buildString {
        appendLine("Current state: $currentState")
        appendLine("Expected field: $expectedField")
        appendLine("Pending replacement field: $pendingReplacementField")
        appendLine("Title exists: $hasTitle")
        appendLine("Selected date exists: $hasSelectedDate")
        appendLine("Selected time exists: $hasSelectedTime")
        appendLine("Allowed moves: ${allowedMoves.joinToString()}")
        append("Authority limitations: ${authorityLimitations.joinToString("; ")}")
    }

    companion object {
        fun capture(
            state: CreateTaskDialogState,
            pendingReplacementField: CreateDraftField?,
            hasTitle: Boolean,
            hasSelectedDate: Boolean,
            hasSelectedTime: Boolean
        ): CreateDraftAgentContext {
            return CreateDraftAgentContext(
                currentState = "${state.name}: ${stateDescription(state)}",
                expectedField = expectedField(state)?.name.orEmpty(),
                pendingReplacementField = pendingReplacementField?.name.orEmpty(),
                hasTitle = hasTitle,
                hasSelectedDate = hasSelectedDate,
                hasSelectedTime = hasSelectedTime,
                allowedMoves = allowedMoves(state),
                authorityLimitations = listOf(
                    "Interpret one bounded move only",
                    "Do not validate date or time values",
                    "Do not save or modify the draft",
                    "Do not generate operational responses"
                )
            )
        }

        private fun expectedField(state: CreateTaskDialogState): CreateDraftField? = when (state) {
            CreateTaskDialogState.IDLE,
            CreateTaskDialogState.WAITING_FOR_TITLE -> CreateDraftField.TITLE
            CreateTaskDialogState.WAITING_FOR_DATE -> CreateDraftField.DATE
            CreateTaskDialogState.WAITING_FOR_TIME -> CreateDraftField.TIME
            else -> null
        }

        private fun allowedMoves(state: CreateTaskDialogState): List<String> = when (state) {
            CreateTaskDialogState.IDLE,
            CreateTaskDialogState.WAITING_FOR_TITLE -> listOf("PROVIDE_FIELD", "CHANGE_FIELD", "CANCEL", "REQUEST_HELP", "UNKNOWN")
            CreateTaskDialogState.WAITING_FOR_DATE,
            CreateTaskDialogState.WAITING_FOR_TIME -> listOf("PROVIDE_FIELD", "CHANGE_FIELD", "CANCEL", "REQUEST_HELP", "UNKNOWN")
            CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD -> listOf("CHANGE_FIELD", "CANCEL", "REQUEST_HELP", "UNKNOWN")
            CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION -> listOf(
                "CONFIRM_SAVE", "REJECT_SAVE", "CHANGE_FIELD", "APPLY_UNSPECIFIED_CORRECTION",
                "CANCEL", "REQUEST_HELP", "UNKNOWN"
            )
            CreateTaskDialogState.READY_TO_SAVE -> listOf("UNKNOWN")
        }

        private fun stateDescription(state: CreateTaskDialogState): String = when (state) {
            CreateTaskDialogState.IDLE -> "The create workflow is idle and no save is being confirmed."
            CreateTaskDialogState.WAITING_FOR_TITLE -> "The app is waiting for a task title."
            CreateTaskDialogState.WAITING_FOR_DATE -> "The app is waiting for a date phrase."
            CreateTaskDialogState.WAITING_FOR_TIME -> "The app is waiting for an exact or interpretable time phrase."
            CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD -> "The app asked which draft field should change."
            CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION ->
                "The complete draft is waiting for save confirmation or a correction."
            CreateTaskDialogState.READY_TO_SAVE ->
                "Android is already processing a confirmed save. No semantic fallback is allowed."
        }
    }
}
