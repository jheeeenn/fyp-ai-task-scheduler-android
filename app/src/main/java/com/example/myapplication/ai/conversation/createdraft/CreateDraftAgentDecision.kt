package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.voice.CreateDraftField

enum class CreateDraftAgentMoveType {
    CONFIRM_SAVE,
    REJECT_SAVE,
    CHANGE_FIELD,
    PROVIDE_FIELD,
    APPLY_UNSPECIFIED_CORRECTION,
    CANCEL,
    REQUEST_HELP,
    UNKNOWN
}

data class CreateDraftAgentDecision(
    val move: CreateDraftAgentMoveType,
    val field: CreateDraftField?,
    val value: String,
    val confidence: Double
)
