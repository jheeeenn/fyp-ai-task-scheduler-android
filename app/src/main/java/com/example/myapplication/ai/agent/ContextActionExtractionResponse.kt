package com.example.myapplication.ai.agent

data class ContextActionExtractionResponse(
    val action: String,
    val replacementTitle: String,
    val dateOperation: String,
    val timeOperation: String,
    val relativeBase: String,
    val replacementDateText: String,
    val replacementTimeText: String,
    val dateOffsetDays: Int,
    val timeOffsetMinutes: Int,
    val confidence: Double,
    val needClarification: Boolean
)
