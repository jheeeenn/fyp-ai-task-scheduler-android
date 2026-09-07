package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateDraftMove
import com.example.myapplication.voice.CreateTaskDialogState

enum class CreateDraftPreviousAssistantAct {
    NONE,
    ASKED_FOR_TITLE,
    ASKED_FOR_DATE,
    ASKED_FOR_TIME,
    ASKED_WHICH_FIELD_TO_CHANGE,
    ASKED_TO_CONFIRM_SAVE,
    SAVE_IN_PROGRESS
}

enum class CreateDraftExpectedResponseKind {
    TITLE_VALUE,
    DATE_VALUE,
    TIME_VALUE,
    FIELD_SELECTION_OR_REPLACEMENT,
    CONFIRM_REJECT_OR_CORRECT,
    NONE
}

data class CreateDraftAgentContext(
    val currentState: String,
    val previousAssistantAct: CreateDraftPreviousAssistantAct,
    val expectedResponseKind: CreateDraftExpectedResponseKind,
    val draftComplete: Boolean,
    val completedDraftWasPresented: Boolean,
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
        appendLine("Previous assistant act: ${previousAssistantAct.name}")
        appendLine("Expected response kind: ${expectedResponseKind.name}")
        appendLine("Draft complete: $draftComplete")
        appendLine("Completed draft was presented: $completedDraftWasPresented")
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

    fun toRepairPromptText(): String = buildString {
        appendLine(toPromptText())
        appendLine()
        appendLine("Semantic repair status: PRIMARY_AND_LOCAL_ABSTAINED")
        append(
            "Repair instruction: The first semantic interpretation abstained and no valid deterministic " +
                    "proposal exists. Re-evaluate the user's meaning only within the supplied interaction act " +
                    "and allowed moves. Do not invent missing values. Use UNKNOWN only if the meaning remains " +
                    "genuinely ambiguous."
        )
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
            val draftComplete = hasTitle && hasSelectedDate && hasSelectedTime
            return CreateDraftAgentContext(
                currentState = "${state.name}: ${stateDescription(state)}",
                previousAssistantAct = previousAssistantAct(state),
                expectedResponseKind = expectedResponseKind(state),
                draftComplete = draftComplete,
                completedDraftWasPresented =
                    state == CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION && draftComplete,
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
            is CreateDraftMove.ProvideSchedule -> "PROVIDE_SCHEDULE"
            is CreateDraftMove.ApplyUnspecifiedCorrection -> "APPLY_UNSPECIFIED_CORRECTION"
            is CreateDraftMove.ReadDraft -> "READ_${move.target.name}"
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
            is CreateDraftMove.ProvideSchedule ->
                move.dateText.isNotBlank() && move.timeText.isNotBlank()
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

        private fun previousAssistantAct(state: CreateTaskDialogState): CreateDraftPreviousAssistantAct =
            when (state) {
                CreateTaskDialogState.IDLE -> CreateDraftPreviousAssistantAct.NONE
                CreateTaskDialogState.WAITING_FOR_TITLE -> CreateDraftPreviousAssistantAct.ASKED_FOR_TITLE
                CreateTaskDialogState.WAITING_FOR_DATE -> CreateDraftPreviousAssistantAct.ASKED_FOR_DATE
                CreateTaskDialogState.WAITING_FOR_TIME -> CreateDraftPreviousAssistantAct.ASKED_FOR_TIME
                CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD ->
                    CreateDraftPreviousAssistantAct.ASKED_WHICH_FIELD_TO_CHANGE
                CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION ->
                    CreateDraftPreviousAssistantAct.ASKED_TO_CONFIRM_SAVE
                CreateTaskDialogState.READY_TO_SAVE -> CreateDraftPreviousAssistantAct.SAVE_IN_PROGRESS
            }

        private fun expectedResponseKind(state: CreateTaskDialogState): CreateDraftExpectedResponseKind =
            when (state) {
                CreateTaskDialogState.IDLE,
                CreateTaskDialogState.WAITING_FOR_TITLE -> CreateDraftExpectedResponseKind.TITLE_VALUE
                CreateTaskDialogState.WAITING_FOR_DATE -> CreateDraftExpectedResponseKind.DATE_VALUE
                CreateTaskDialogState.WAITING_FOR_TIME -> CreateDraftExpectedResponseKind.TIME_VALUE
                CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD ->
                    CreateDraftExpectedResponseKind.FIELD_SELECTION_OR_REPLACEMENT
                CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION ->
                    CreateDraftExpectedResponseKind.CONFIRM_REJECT_OR_CORRECT
                CreateTaskDialogState.READY_TO_SAVE -> CreateDraftExpectedResponseKind.NONE
            }

        private fun allowedMoves(state: CreateTaskDialogState): List<String> = when (state) {
            CreateTaskDialogState.IDLE,
            CreateTaskDialogState.WAITING_FOR_TITLE -> listOf(
                "PROVIDE_FIELD", "CHANGE_FIELD", "READ_TITLE", "READ_DATE", "READ_TIME",
                "READ_SCHEDULE", "READ_SUMMARY", "CANCEL", "REQUEST_HELP", "UNKNOWN"
            )
            CreateTaskDialogState.WAITING_FOR_DATE,
            CreateTaskDialogState.WAITING_FOR_TIME -> listOf(
                "PROVIDE_FIELD", "PROVIDE_SCHEDULE", "CHANGE_FIELD", "READ_TITLE", "READ_DATE",
                "READ_TIME", "READ_SCHEDULE", "READ_SUMMARY", "CANCEL", "REQUEST_HELP", "UNKNOWN"
            )
            CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD -> listOf(
                "CHANGE_FIELD", "PROVIDE_SCHEDULE", "READ_TITLE", "READ_DATE", "READ_TIME",
                "READ_SCHEDULE", "READ_SUMMARY", "CANCEL", "REQUEST_HELP", "UNKNOWN"
            )
            CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION -> listOf(
                "CONFIRM_SAVE", "REJECT_SAVE", "CHANGE_FIELD", "PROVIDE_SCHEDULE",
                "APPLY_UNSPECIFIED_CORRECTION", "READ_TITLE", "READ_DATE", "READ_TIME",
                "READ_SCHEDULE", "READ_SUMMARY", "CANCEL", "REQUEST_HELP", "UNKNOWN"
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
