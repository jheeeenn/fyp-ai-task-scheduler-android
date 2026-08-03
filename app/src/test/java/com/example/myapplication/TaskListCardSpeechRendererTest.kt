package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskListCardSpeechRendererTest {
    private val upcoming = TaskStatusPresentation(
        TaskVisualStatus.UPCOMING,
        "Due in 4 days",
        "Due in 4 days"
    )

    @Test
    fun compactSpeechContainsTitleAndRelativeStatus() {
        val output = TaskListCardSpeechRenderer.render("Medical check-up", upcoming)

        assertTrue(output.spokenSummary.contains("Medical check-up"))
        assertTrue(output.spokenSummary.contains("Due in 4 days"))
        assertTrue(output.contentDescription.contains("Double tap to open task details"))
    }

    @Test
    fun parentOutputIncludesProgressWithoutSubtaskTitles() {
        val output = TaskListCardSpeechRenderer.render(
            title = "Final year project",
            status = upcoming,
            completedSubtasks = 2,
            totalSubtasks = 4
        )

        assertTrue(output.visibleStatus.contains("Due in 4 days · 2 of 4 steps done"))
        assertTrue(output.spokenSummary.contains("2 of 4 steps done"))
        assertFalse(output.spokenSummary.contains("Write methodology"))
        assertFalse(output.contentDescription.contains("Review citations"))
    }

    @Test
    fun compactOutputNeverExposesInternalIds() {
        val output = TaskListCardSpeechRenderer.render("Project", upcoming, 1, 3)
        val allOutput = listOf(
            output.visibleStatus,
            output.spokenSummary,
            output.contentDescription
        ).joinToString(" ")

        assertFalse(allOutput.contains("task_id"))
        assertFalse(allOutput.contains("Room"))
        assertFalse(allOutput.contains("parentTaskId"))
    }
}
