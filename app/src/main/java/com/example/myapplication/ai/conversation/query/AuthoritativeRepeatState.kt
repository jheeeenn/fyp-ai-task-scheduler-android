package com.example.myapplication.ai.conversation.query

internal enum class RepeatableSpeechKind {
    QUERY_COUNT,
    QUERY_PAGE,
    CONTEXT_READ
}

internal data class AuthoritativeRepeatState(
    val speech: String,
    val kind: RepeatableSpeechKind,
    val contextGeneration: Long?
)
