package com.example.myapplication.ai.conversation.taskedit

import com.example.myapplication.ai.temporal.RelativeTemporalRevisionResult

enum class EditFieldTarget {
    NONE, TITLE, DATE, TIME, DATE_OR_TIME
}

/** Foreground collection and confirmation precedence; owns no draft or proposal state. */
object EditTaskInteractionPolicy {
    fun foregroundState(
        operationInFlight: Boolean,
        deleteConfirmationPending: Boolean,
        temporalClarificationPending: Boolean,
        pendingFieldTarget: EditFieldTarget,
        relativeProposalActive: Boolean,
        saveConfirmationPending: Boolean
    ): EditTaskInteractionState = when {
        operationInFlight -> EditTaskInteractionState.OPERATION_IN_FLIGHT
        deleteConfirmationPending -> EditTaskInteractionState.WAITING_FOR_DELETE_CONFIRMATION
        temporalClarificationPending ->
            EditTaskInteractionState.WAITING_FOR_TEMPORAL_CLARIFICATION
        pendingFieldTarget == EditFieldTarget.TITLE -> EditTaskInteractionState.WAITING_FOR_TITLE
        pendingFieldTarget == EditFieldTarget.DATE -> EditTaskInteractionState.WAITING_FOR_DATE
        pendingFieldTarget == EditFieldTarget.TIME -> EditTaskInteractionState.WAITING_FOR_TIME
        pendingFieldTarget == EditFieldTarget.DATE_OR_TIME ->
            EditTaskInteractionState.WAITING_FOR_DATE_OR_TIME
        relativeProposalActive -> EditTaskInteractionState.WAITING_FOR_RELATIVE_TEMPORAL_CONFIRMATION
        saveConfirmationPending -> EditTaskInteractionState.WAITING_FOR_SAVE_CONFIRMATION
        else -> EditTaskInteractionState.READY_FOR_EDIT
    }

    fun isCollecting(state: EditTaskInteractionState): Boolean = state in setOf(
        EditTaskInteractionState.WAITING_FOR_TITLE,
        EditTaskInteractionState.WAITING_FOR_DATE,
        EditTaskInteractionState.WAITING_FOR_TIME,
        EditTaskInteractionState.WAITING_FOR_DATE_OR_TIME,
        EditTaskInteractionState.WAITING_FOR_TEMPORAL_CLARIFICATION
    )

    fun completesTemporalCollection(
        state: EditTaskInteractionState,
        result: RelativeTemporalRevisionResult
    ): Boolean = result == RelativeTemporalRevisionResult.APPLIED && state in setOf(
        EditTaskInteractionState.WAITING_FOR_DATE,
        EditTaskInteractionState.WAITING_FOR_TIME,
        EditTaskInteractionState.WAITING_FOR_DATE_OR_TIME,
        EditTaskInteractionState.WAITING_FOR_TEMPORAL_CLARIFICATION
    )
}
