package com.example.myapplication.ai.conversation.query

internal enum class RepeatableSpeechKind {
    QUERY_COUNT,
    QUERY_PAGE,
    DAILY_BRIEFING,
    CONTEXT_SUGGESTION,
    CONTEXT_READ
}

internal data class AuthoritativeRepeatState(
    val speech: String,
    val kind: RepeatableSpeechKind,
    val contextGeneration: Long?
)
