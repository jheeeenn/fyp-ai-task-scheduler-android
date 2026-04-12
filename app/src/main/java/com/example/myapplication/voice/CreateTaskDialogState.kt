package com.example.myapplication.voice

enum class CreateTaskDialogState {
    IDLE,
    WAITING_FOR_TITLE,
    WAITING_FOR_DATE,
    WAITING_FOR_TIME,
    WAITING_FOR_CHANGE_FIELD,
    READY_TO_SAVE,
    WAITING_FOR_SAVE_CONFIRMATION
}