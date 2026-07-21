package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateDraftMove
import com.example.myapplication.voice.CreateTaskDialogState

data class CreateDraftAgentContext(
    val currentState: String,
    val expectedField: String,
    val pendingReplacementField: String,
    val hasTitle: Boolean,
    val hasSelectedDate: Boolean,
    val hasSelectedTime: Boolean,
    val localCandidateMove: String,
    val localCandidateField: String,
    val localCandidateValuePresent: Boolean,
    val localCandidateRecognised: Boolean,
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
        appendLine("Advisory local candidate move: $localCandidateMove")
        appendLine("Advisory local candidate field: $localCandidateField")
        appendLine("Advisory local candidate has a value: $localCandidateValuePresent")
        appendLine("Advisory local candidate recognised: $localCandidateRecognised")
        appendLine("Allowed moves: ${allowedMoves.joinToString()}")
        append("Authority limitations: ${authorityLimitations.joinToString("; ")}")
    }

    companion object {
        fun capture(
            state: CreateTaskDialogState,
            pendingReplacementField: CreateDraftField?,
            hasTitle: Boolean,
            hasSelectedDate: Boolean,
            hasSelectedTime: Boolean,
            localCandidate: CreateDraftMove
        ): CreateDraftAgentContext {
            return CreateDraftAgentContext(
                currentState = "${state.name}: ${stateDescription(state)}",
                expectedField = expectedField(state)?.name.orEmpty(),
                pendingReplacementField = pendingReplacementField?.name.orEmpty(),
                hasTitle = hasTitle,
                hasSelectedDate = hasSelectedDate,
                hasSelectedTime = hasSelectedTime,
                localCandidateMove = moveName(localCandidate),
                localCandidateField = moveField(localCandidate)?.name.orEmpty(),
                localCandidateValuePresent = moveHasValue(localCandidate),
                localCandidateRecognised = localCandidate != CreateDraftMove.Unknown,
                allowedMoves = allowedMoves(state),
                authorityLimitations = listOf(
                    "Interpret one bounded move only",
                    "The local candidate is advisory and not authoritative",
                    "Independently decide whether to agree with or correct the local candidate",
                    "Do not invent a value missing from the user utterance",
                    "Do not validate date or time values",
                    "Do not save or modify the draft",
                    "Do not generate operational responses"
                )
            )
        }

        private fun moveName(move: CreateDraftMove): String = when (move) {
            CreateDraftMove.ConfirmSave -> "CONFIRM_SAVE"
            CreateDraftMove.RejectSave -> "REJECT_SAVE"
            is CreateDraftMove.ChangeField -> "CHANGE_FIELD"
            is CreateDraftMove.ProvideField -> "PROVIDE_FIELD"
            is CreateDraftMove.ApplyUnspecifiedCorrection -> "APPLY_UNSPECIFIED_CORRECTION"
            CreateDraftMove.Cancel -> "CANCEL"
            CreateDraftMove.RequestHelp -> "REQUEST_HELP"
            CreateDraftMove.Unknown -> "UNKNOWN"
        }

        private fun moveField(move: CreateDraftMove): CreateDraftField? = when (move) {
            is CreateDraftMove.ChangeField -> move.field
            is CreateDraftMove.ProvideField -> move.field
            else -> null
        }

        private fun moveHasValue(move: CreateDraftMove): Boolean = when (move) {
            is CreateDraftMove.ChangeField -> !move.value.isNullOrBlank()
            is CreateDraftMove.ProvideField -> move.value.isNotBlank()
            is CreateDraftMove.ApplyUnspecifiedCorrection -> move.value.isNotBlank()
            else -> false
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
                "Android is already processing a confirmed save. No create-draft semantic request is allowed."
        }
    }
}
