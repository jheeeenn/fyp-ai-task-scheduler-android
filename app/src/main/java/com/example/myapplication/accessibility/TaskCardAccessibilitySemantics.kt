package com.example.myapplication.accessibility

import java.text.SimpleDateFormat
import java.util.Locale

data class TaskCardAccessibilityContent(
    val title: String,
    val status: String,
    val completedSubtasks: Int = 0,
    val totalSubtasks: Int = 0
)

object TaskCardAccessibilitySemantics {
    fun summary(content: TaskCardAccessibilityContent): String = buildList {
        add(content.title.ifBlank { "Untitled task" })
        add(content.status)
        if (content.totalSubtasks > 0) {
            val stepWording = if (content.totalSubtasks == 1) "step" else "steps"
            add("${content.completedSubtasks} of ${content.totalSubtasks} $stepWording done")
        }
        add("Double tap to open task details")
    }.joinToString(", ")

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
