package com.example.myapplication

object HomeControlSpeechRenderer {
    fun todayTasks(): String = "Today Tasks. Double tap to open."
    fun createTask(): String = "Create Task. Double tap to open."
    fun scheduledTasks(): String = "Scheduled Tasks. Double tap to open."
    fun settings(): String = "Settings. Double tap to open."
    fun assistant(): String = "Talk to Assistant. Double tap to start."
}

object TaskFormControlSpeechRenderer {
    fun pickDate(): String = "Pick Date. Double tap to open the date picker."
    fun pickTime(): String = "Pick Time. Double tap to open the time picker."
    fun saveTask(): String = "Save Task. Double tap to save."
    fun deleteTask(): String = "Delete Task. Double tap to continue."
    fun cancel(): String = "Cancel. Double tap to cancel."
    fun home(): String = "Home. Double tap to return."
    fun assistant(): String = "Talk to Assistant. Double tap to start."
}

object SettingsControlSpeechRenderer {
    fun assistantTone(currentValue: String): String =
        "Assistant Tone, $currentValue. Double tap to change."

    fun replyLength(currentValue: String): String =
        "Reply Length, $currentValue. Double tap to change."

    fun conversationEndpoint(currentValue: String): String =
        "Conversation Agent Endpoint, $currentValue. Double tap to edit."

    fun taskEndpoint(currentValue: String): String =
        "Task Agent Endpoint, $currentValue. Double tap to edit."

    fun saveOption(): String = "Save option. Double tap to save."
    fun home(): String = "Home. Double tap to return."
    fun assistant(): String = "Talk to Assistant. Double tap to start."
}

object AssistantPanelControlSpeechRenderer {
    fun panel(): String = "Assistant panel. Double tap to stop the assistant."
    fun typeInput(): String = "Type to Assistant. Double tap to open typed input."
    fun stop(): String = "Stop Assistant. Double tap to stop."
    fun status(currentState: CharSequence): String = "Assistant status, $currentState."
}
