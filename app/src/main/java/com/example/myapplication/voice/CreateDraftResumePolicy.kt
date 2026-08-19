package com.example.myapplication.voice

object CreateDraftResumePolicy {
    fun nextState(
        hasTitle: Boolean,
        hasDate: Boolean,
        hasTime: Boolean
    ): CreateTaskDialogState = when {
        !hasTitle -> CreateTaskDialogState.WAITING_FOR_TITLE
        !hasDate -> CreateTaskDialogState.WAITING_FOR_DATE
        !hasTime -> CreateTaskDialogState.WAITING_FOR_TIME
        else -> CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
    }
}
