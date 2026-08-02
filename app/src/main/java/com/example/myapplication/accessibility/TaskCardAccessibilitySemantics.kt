package com.example.myapplication.accessibility

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TaskCardAccessibilityContent(
    val title: String,
    val date: String?,
    val time: String?,
    val status: String,
    val completedSubtasks: Int = 0,
    val totalSubtasks: Int = 0,
    val isSelected: Boolean = false
)

object TaskCardAccessibilitySemantics {
    fun summary(content: TaskCardAccessibilityContent): String = buildList {
        add(content.title.ifBlank { "Untitled task" })
        add(spokenDate(content.date))
        add(spokenTime(content.time))
        add(content.status)
        if (content.totalSubtasks > 0) {
            add("${content.completedSubtasks} of ${content.totalSubtasks} subtasks completed")
        }
        add(if (content.isSelected) "Selected" else "Not selected")
        add(if (content.isSelected) "Double tap to deselect" else "Double tap to select")
    }.joinToString(", ")

    fun status(
        isDone: Boolean,
        dueDate: String?,
        dueTime: String?,
        now: Date = Date(),
        locale: Locale = Locale.ENGLISH
    ): String {
        if (isDone) return "Completed"
        if (dueDate.isNullOrBlank() || dueTime.isNullOrBlank()) return "Unscheduled"

        return runCatching {
            val dateTime = SimpleDateFormat("dd/MM/yyyy hh:mm a", locale).apply {
                isLenient = false
            }.parse("$dueDate $dueTime") ?: return "Upcoming"
            val today = SimpleDateFormat("dd/MM/yyyy", locale).format(now)
            when {
                dateTime.before(now) -> "Overdue"
                dueDate == today -> "Today"
                else -> "Upcoming"
            }
        }.getOrDefault("Upcoming")
    }

    fun spokenDate(value: String?): String {
        if (value.isNullOrBlank()) return "No date set"
        return runCatching {
            val parsed = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).apply {
                isLenient = false
            }.parse(value) ?: return value
            SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH).format(parsed)
        }.getOrDefault(value)
    }

    fun spokenTime(value: String?): String {
        if (value.isNullOrBlank()) return "No time set"
        return runCatching {
            val parsed = SimpleDateFormat("hh:mm a", Locale.ENGLISH).apply {
                isLenient = false
            }.parse(value) ?: return value
            SimpleDateFormat("h:mm a", Locale.ENGLISH).format(parsed)
        }.getOrDefault(value)
    }
}
