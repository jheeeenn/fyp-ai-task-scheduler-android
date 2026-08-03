package com.example.myapplication.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCardAccessibilitySemanticsTest {
    @Test
    fun summaryContainsOnlyCompactInformationAndOpenAction() {
        val summary = TaskCardAccessibilitySemantics.summary(
            TaskCardAccessibilityContent(
                title = "Prepare breakfast",
                status = "Due today at 9:00 AM",
                completedSubtasks = 2,
                totalSubtasks = 3
            )
        )

        assertTrue(summary.contains("Prepare breakfast"))
        assertTrue(summary.contains("Due today at 9:00 AM"))
        assertTrue(summary.contains("2 of 3 steps done"))
        assertTrue(summary.endsWith("Double tap to open task details"))
        assertFalse(summary.contains("03/08/2026"))
        assertFalse(summary.contains("selected", ignoreCase = true))
    }

    @Test
    fun completedStateDoesNotRelyOnVisualCheckMark() {
        val summary = TaskCardAccessibilitySemantics.summary(
            TaskCardAccessibilityContent(
                title = "Take medication",
                status = "Completed"
            )
        )

        assertTrue(summary.contains("Completed"))
        assertFalse(summary.contains("✓"))
    }

    @Test
    fun summaryDoesNotExposeInternalIdentifiers() {
        val summary = TaskCardAccessibilitySemantics.summary(
            TaskCardAccessibilityContent("Call family", "Unscheduled")
        )

        assertFalse(summary.contains("task_id"))
        assertFalse(summary.contains("Room"))
        assertFalse(summary.contains("42"))
    }

    @Test
    fun identicalTitlesRemainTwoSeparateAccessibleItems() {
        val first = TaskCardAccessibilityContent("Medication", "Unscheduled")
        val second = TaskCardAccessibilityContent("Medication", "Unscheduled")
        val accessibleItems = listOf(
            TaskCardAccessibilitySemantics.summary(first),
            TaskCardAccessibilitySemantics.summary(second)
        )

        assertEquals(2, accessibleItems.size)
        assertEquals(accessibleItems[0], accessibleItems[1])
    }
}
