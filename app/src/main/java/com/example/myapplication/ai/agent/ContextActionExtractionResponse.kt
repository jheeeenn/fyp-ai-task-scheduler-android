package com.example.myapplication.ai.agent

data class ContextActionExtractionResponse(
    val action: String,
    val replacementTitle: String,
    val newDate: String,
    val newTime: String,
    val confidence: Double,
    val needClarification: Boolean
)
