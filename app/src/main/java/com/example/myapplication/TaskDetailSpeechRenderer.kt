package com.example.myapplication

import com.example.myapplication.accessibility.TaskCardAccessibilitySemantics

object TaskDetailSpeechRenderer {
    fun title(title: String): String = "Task title. ${title.ifBlank { "Untitled task" }}."

    fun date(dueDate: String?): String = if (dueDate.isNullOrBlank()) {
        "No date set."
    } else {
        "Due date. ${TaskCardAccessibilitySemantics.spokenDate(dueDate)}."
    }

    fun time(dueTime: String?): String = if (dueTime.isNullOrBlank()) {
        "No time set."
    } else {
        "Due time. ${TaskCardAccessibilitySemantics.spokenTime(dueTime)}."
    }

    fun status(status: TaskStatusPresentation): String = "Status. ${status.spokenText}."

    fun subtaskProgress(completed: Int, total: Int): String {
        if (total <= 0) return "No subtasks."
        val safeCompleted = completed.coerceIn(0, total)
        val stepWording = if (total == 1) "step" else "steps"
        return "${spokenNumber(safeCompleted)} of ${spokenNumber(total).lowercase()} $stepWording completed."
    }

    fun readAll(
        title: String,
        status: TaskStatusPresentation,
        dueDate: String?,
        dueTime: String?,
        completedSubtasks: Int,
        totalSubtasks: Int
    ): String = listOf(
        title(title),
        status(status),
        date(dueDate),
        time(dueTime),
        subtaskProgress(completedSubtasks, totalSubtasks)
    ).joinToString(" ")

    private fun spokenNumber(number: Int): String = when (number) {
        0 -> "Zero"
        1 -> "One"
        2 -> "Two"
        3 -> "Three"
        4 -> "Four"
        5 -> "Five"
        6 -> "Six"
        7 -> "Seven"
        8 -> "Eight"
        9 -> "Nine"
        10 -> "Ten"
        else -> number.toString()
    }
}
