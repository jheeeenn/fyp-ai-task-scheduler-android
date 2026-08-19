package com.example.myapplication

object HomeControlSpeechRenderer {
    fun todayTasks(): String = "Today Tasks"
    fun createTask(): String = "Create Task"
    fun scheduledTasks(): String = "Scheduled Tasks"
    fun settings(): String = "Settings"
    fun assistant(): String = "Talk to Assistant"
}

object TaskFormControlSpeechRenderer {
    fun pickDate(): String = "Pick Date"
    fun pickTime(): String = "Pick Time"
    fun saveTask(): String = "Save Task"
    fun deleteTask(): String = "Delete Task"
    fun cancel(): String = "Cancel"
    fun home(): String = "Home"
    fun assistant(): String = "Talk to Assistant"
}

object SettingsControlSpeechRenderer {
    fun assistantTone(currentValue: String): String =
        "Assistant Tone, $currentValue"

    fun replyLength(currentValue: String): String =
        "Reply Length, $currentValue"

    fun speechSpeed(currentValue: String): String =
        "Speech Speed, $currentValue"

    fun conversationEndpoint(): String = "Conversation Agent Endpoint"

    fun taskEndpoint(): String = "Task Agent Endpoint"

    fun saveOption(): String = "Save Option"
    fun home(): String = "Home"
    fun assistant(): String = "Talk to Assistant"
}

object AssistantPanelControlSpeechRenderer {
    fun typeInput(): String = "Type to Assistant"
    fun stop(): String = "Stop Assistant"
}
