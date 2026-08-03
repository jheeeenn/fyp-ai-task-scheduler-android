package com.example.myapplication

import com.example.myapplication.accessibility.TaskCardAccessibilityContent
import com.example.myapplication.accessibility.TaskCardAccessibilitySemantics

data class TaskListCardPresentation(
    val visibleStatus: String,
    val spokenSummary: String,
    val contentDescription: String
)

object TaskListCardSpeechRenderer {
    fun render(
        title: String,
        status: TaskStatusPresentation,
        completedSubtasks: Int = 0,
        totalSubtasks: Int = 0
    ): TaskListCardPresentation {
        val safeTitle = title.ifBlank { "Untitled task" }
        val safeTotal = totalSubtasks.coerceAtLeast(0)
        val safeCompleted = completedSubtasks.coerceIn(0, safeTotal)
        val progress = if (safeTotal > 0) {
            "$safeCompleted of $safeTotal ${if (safeTotal == 1) "step" else "steps"} done"
        } else {
            null
        }
        val visibleStatus = listOfNotNull(status.visibleText, progress).joinToString(" · ")
        val spokenSummary = listOfNotNull(safeTitle, status.spokenText, progress)
            .joinToString(". ", postfix = ".")
        val contentDescription = TaskCardAccessibilitySemantics.summary(
            TaskCardAccessibilityContent(
                title = safeTitle,
                status = status.visibleText,
                completedSubtasks = safeCompleted,
                totalSubtasks = safeTotal
            )
        )
        return TaskListCardPresentation(visibleStatus, spokenSummary, contentDescription)
    }
}
