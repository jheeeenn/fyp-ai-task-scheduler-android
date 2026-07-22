package com.example.myapplication.ai.conversation

data class ConversationDecision(
    val route: ConversationRoute,
    val taskText: String = "",
    val reply: String = "",
    val contextRef: String = "",
    val contextDetail: ConversationContextDetail = ConversationContextDetail.NONE,
    val confidence: Double = 0.0,
    val listenAgain: Boolean = true,
    val source: String = "conversation_agent"
)
