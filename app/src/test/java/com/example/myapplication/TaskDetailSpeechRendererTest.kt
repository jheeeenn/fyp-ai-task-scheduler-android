package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDetailSpeechRendererTest {
    private val status = TaskStatusPresentation(
        TaskVisualStatus.UPCOMING,
        "Due in 2 days",
        "Due in 2 days"
    )

    @Test
    fun individualSurfacesUseClearLabelsAndFallbacks() {
        assertTrue(TaskDetailSpeechRenderer.title("Final year project").contains("Task title"))
        assertTrue(TaskDetailSpeechRenderer.status(status).contains("Status. Due in 2 days"))
        assertTrue(TaskDetailSpeechRenderer.date("05/08/2026").contains("5 August 2026"))
        assertTrue(TaskDetailSpeechRenderer.time("09:00 PM").contains("9:00 PM"))
        assertTrue(TaskDetailSpeechRenderer.date(null).contains("No date set"))
        assertTrue(TaskDetailSpeechRenderer.time(null).contains("No time set"))
        assertTrue(TaskDetailSpeechRenderer.subtaskProgress(0, 0).contains("No subtasks"))
    }

    @Test
    fun readAllContainsAuthoritativeVisibleFieldsAndProgress() {
        val speech = TaskDetailSpeechRenderer.readAll(
            title = "Final year project",
            status = status,
            dueDate = "05/08/2026",
            dueTime = "09:00 PM",
            completedSubtasks = 2,
            totalSubtasks = 4
        )

        assertTrue(speech.contains("Task title. Final year project"))
        assertTrue(speech.contains("Status. Due in 2 days"))
        assertTrue(speech.contains("Due date. 5 August 2026"))
        assertTrue(speech.contains("Due time. 9:00 PM"))
        assertTrue(speech.contains("Two of four steps completed"))
        assertFalse(speech.contains("task_id"))
    }
}
