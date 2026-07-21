package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateDraftMove
import com.example.myapplication.voice.CreateTaskDialogState

data class CreateDraftDecisionValidation(
    val move: CreateDraftMove,
    val accepted: Boolean
)

class CreateDraftAgentDecisionValidator {
    fun validate(
        decision: CreateDraftAgentDecision,
        state: CreateTaskDialogState
    ): CreateDraftDecisionValidation {
        if (decision.move != CreateDraftAgentMoveType.UNKNOWN && decision.confidence < MIN_CONFIDENCE) {
            return rejected()
        }
        if (decision.move == CreateDraftAgentMoveType.CONFIRM_SAVE && decision.confidence < CONFIRM_CONFIDENCE) {
            return rejected()
        }

        val allowed = when (decision.move) {
            CreateDraftAgentMoveType.CONFIRM_SAVE -> state == CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
            CreateDraftAgentMoveType.REJECT_SAVE -> state == CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
            CreateDraftAgentMoveType.PROVIDE_FIELD -> decision.field == expectedField(state)
            CreateDraftAgentMoveType.CHANGE_FIELD -> isChangeAllowed(decision.field, state)
            CreateDraftAgentMoveType.APPLY_UNSPECIFIED_CORRECTION ->
                state == CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION && decision.value.isNotBlank()
            CreateDraftAgentMoveType.CANCEL -> state != CreateTaskDialogState.READY_TO_SAVE
            CreateDraftAgentMoveType.REQUEST_HELP -> state in ACTIVE_STATES
            CreateDraftAgentMoveType.UNKNOWN -> true
        }
        if (!allowed) return rejected()

        val move = when (decision.move) {
            CreateDraftAgentMoveType.CONFIRM_SAVE -> CreateDraftMove.ConfirmSave
            CreateDraftAgentMoveType.REJECT_SAVE -> CreateDraftMove.RejectSave
            CreateDraftAgentMoveType.CHANGE_FIELD ->
                CreateDraftMove.ChangeField(decision.field!!, decision.value.ifBlank { null })
            CreateDraftAgentMoveType.PROVIDE_FIELD ->
                CreateDraftMove.ProvideField(decision.field!!, decision.value)
            CreateDraftAgentMoveType.APPLY_UNSPECIFIED_CORRECTION ->
                CreateDraftMove.ApplyUnspecifiedCorrection(decision.value)
            CreateDraftAgentMoveType.CANCEL -> CreateDraftMove.Cancel
            CreateDraftAgentMoveType.REQUEST_HELP -> CreateDraftMove.RequestHelp
            CreateDraftAgentMoveType.UNKNOWN -> CreateDraftMove.Unknown
        }
        return CreateDraftDecisionValidation(move, accepted = true)
    }

    private fun isChangeAllowed(field: CreateDraftField?, state: CreateTaskDialogState): Boolean {
        if (field == null) return false
        return state == CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION ||
                state == CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD ||
                expectedField(state) == field
    }

    private fun expectedField(state: CreateTaskDialogState): CreateDraftField? = when (state) {
        CreateTaskDialogState.IDLE,
        CreateTaskDialogState.WAITING_FOR_TITLE -> CreateDraftField.TITLE
        CreateTaskDialogState.WAITING_FOR_DATE -> CreateDraftField.DATE
        CreateTaskDialogState.WAITING_FOR_TIME -> CreateDraftField.TIME
        else -> null
    }

    private fun rejected() = CreateDraftDecisionValidation(CreateDraftMove.Unknown, accepted = false)

    private companion object {
        const val MIN_CONFIDENCE = 0.75
        const val CONFIRM_CONFIDENCE = 0.90
        val ACTIVE_STATES = setOf(
            CreateTaskDialogState.IDLE,
            CreateTaskDialogState.WAITING_FOR_TITLE,
            CreateTaskDialogState.WAITING_FOR_DATE,
            CreateTaskDialogState.WAITING_FOR_TIME,
            CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD,
            CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
        )
    }
}
