// TO BE DISCARDED.................

package com.example.myapplication.voice

data class ParsedCommand(
    val intent: IntentType,
    val title: String? = null,
    val dateText: String? = null,
    val timeText: String? = null,
    val normalizedText: String = "",
    val confidence: Float = 0.0f,
    val needsConfirmation: Boolean = false
)