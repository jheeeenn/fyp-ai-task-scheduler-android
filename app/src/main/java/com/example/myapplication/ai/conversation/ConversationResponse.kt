package com.example.myapplication.ai.conversation

enum class ConversationResponseType {
    ACKNOWLEDGEMENT,
    INFORMATION,
    REQUEST_CONFIRMATION,
    REQUEST_CLARIFICATION,
    SUCCESS,
    PARTIAL_SUCCESS,
    ERROR,
    SESSION_END
}

data class ConversationResponse(
    val speech: String,
    val hint: String,
    val responseType: ConversationResponseType,
    val source: String = "conversation_agent"
)
