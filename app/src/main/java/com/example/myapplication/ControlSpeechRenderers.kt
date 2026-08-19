package com.example.myapplication

import java.text.SimpleDateFormat
import java.util.Locale

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
    fun date(currentValue: String?): String =
        "Date, ${TaskFormScheduleValueRenderer.date(currentValue, emptyValue = "no date selected")}"

    fun time(currentValue: String?): String =
        "Time, ${TaskFormScheduleValueRenderer.time(currentValue, emptyValue = "no time selected")}"

    fun saveTask(): String = "Save Task"
    fun saveChanges(): String = "Save Changes"
    fun deleteTask(): String = "Delete Task"
    fun cancel(): String = "Cancel"
    fun home(): String = "Home"
    fun assistant(): String = "Talk to Assistant"
}

object TaskFormScheduleValueRenderer {
    fun date(currentValue: String?, emptyValue: String = "No date selected"): String {
        val rawValue = currentValue?.trim().orEmpty()
        if (rawValue.isEmpty()) return emptyValue

        return try {
            val input = SimpleDateFormat("dd/MM/yyyy", Locale.UK).apply { isLenient = false }
            val output = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.UK)
            input.parse(rawValue)?.let(output::format) ?: rawValue
        } catch (_: Exception) {
            rawValue
        }
    }

    fun time(currentValue: String?, emptyValue: String = "No time selected"): String =
        currentValue?.trim()?.takeIf(String::isNotEmpty) ?: emptyValue
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
