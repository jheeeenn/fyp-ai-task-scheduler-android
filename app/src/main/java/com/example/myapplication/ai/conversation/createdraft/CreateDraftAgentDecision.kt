package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.voice.CreateDraftField

enum class CreateDraftAgentMoveType {
    CONFIRM_SAVE,
    REJECT_SAVE,
    CHANGE_FIELD,
    PROVIDE_FIELD,
    PROVIDE_SCHEDULE,
    READ_TITLE,
    READ_DATE,
    READ_TIME,
    READ_SCHEDULE,
    READ_SUMMARY,
    CANCEL,
    REQUEST_HELP,
    UNKNOWN
}

data class CreateDraftAgentDecision(
    val move: CreateDraftAgentMoveType,
    val field: CreateDraftField?,
    val value: String,
    val dateText: String,
    val timeText: String,
    val confidence: Double
)
