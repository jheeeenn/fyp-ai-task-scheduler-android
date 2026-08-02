package com.example.myapplication.accessibility

enum class AssistantAccessibilityState(val label: String) {
    READY("Ready"),
    LISTENING("Listening"),
    PROCESSING("Processing"),
    SPEAKING("Speaking"),
    WAITING_FOR_CONFIRMATION("Waiting for confirmation"),
    STOPPED("Stopped"),
    ERROR("Error")
}

object AssistantAccessibilitySemantics {
    fun buttonStateDescription(state: AssistantAccessibilityState): String = state.label

    fun diagnosticState(state: AssistantAccessibilityState): String = state.name
}
