package com.example.myapplication.ai.routine

data class RoutineExtractionResponse(
    val routineTitle: String,
    val steps: List<RoutineStepExtraction>,
    val confidence: Double,
    val needClarification: Boolean
)

data class RoutineStepExtraction(
    val title: String,
    val dateText: String,
    val timeText: String
)
