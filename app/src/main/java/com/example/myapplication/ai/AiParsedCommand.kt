package com.example.myapplication.ai

data class AiParsedCommand(
    val intent: String,
    val taskTitle: String? = null,          // for create or new title if needed
    val targetTaskTitle: String? = null,    // task user wants to edit/delete/reschedule
    val dateText: String? = null,
    val timeText: String? = null,
    val recurrence: String? = null,
    val priority: String? = null,
    val confidence: Float = 0f,
    val source: String = "local"
)