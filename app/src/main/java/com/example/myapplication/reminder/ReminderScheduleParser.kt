package com.example.myapplication.reminder

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

object ReminderScheduleParser {
    const val PATTERN = "dd/MM/yyyy hh:mm a"

    fun parseToEpochMillis(dueDate: String?, dueTime: String?): Long? {
        if (dueDate.isNullOrBlank() || dueTime.isNullOrBlank()) return null
        val value = "${dueDate.trim()} ${dueTime.trim()}"
        val formatter = SimpleDateFormat(PATTERN, Locale.UK).apply {
            isLenient = false
        }
        val position = ParsePosition(0)
        val parsed = formatter.parse(value, position) ?: return null
        return parsed.time.takeIf {
            position.index == value.length && position.errorIndex < 0
        }
    }
}
