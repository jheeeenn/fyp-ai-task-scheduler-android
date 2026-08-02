package com.example.myapplication.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class TaskCardAccessibilitySemanticsTest {
    @Test
    fun summaryConsolidatesTitleScheduleStatusProgressAndAction() {
        val summary = TaskCardAccessibilitySemantics.summary(
            TaskCardAccessibilityContent(
                title = "Prepare breakfast",
                date = "03/08/2026",
                time = "09:00 AM",
                status = "Upcoming",
                completedSubtasks = 2,
                totalSubtasks = 3,
                isSelected = false
            )
        )

        assertTrue(summary.contains("Prepare breakfast"))
        assertTrue(summary.contains("3 August 2026"))
        assertTrue(summary.contains("9:00 AM"))
        assertTrue(summary.contains("Upcoming"))
        assertTrue(summary.contains("2 of 3 subtasks completed"))
        assertTrue(summary.contains("Not selected"))
        assertTrue(summary.endsWith("Double tap to select"))
    }

    @Test
    fun selectedStateChangesWithoutChangingTaskIdentity() {
        val base = TaskCardAccessibilityContent(
            title = "Read messages",
            date = null,
            time = null,
            status = "Unscheduled"
        )

        val notSelected = TaskCardAccessibilitySemantics.summary(base)
        val selected = TaskCardAccessibilitySemantics.summary(base.copy(isSelected = true))

        assertTrue(notSelected.contains("Not selected"))
        assertTrue(selected.contains("Selected"))
        assertTrue(selected.endsWith("Double tap to deselect"))
    }

    @Test
    fun completedStateDoesNotRelyOnVisualCheckMark() {
        val summary = TaskCardAccessibilitySemantics.summary(
            TaskCardAccessibilityContent(
                title = "Take medication",
                date = "03/08/2026",
                time = "08:00 AM",
                status = "Completed"
            )
        )

        assertTrue(summary.contains("Completed"))
        assertFalse(summary.contains("✓"))
    }

    @Test
    fun missingScheduleHasExplicitFallbacksAndNoInternalId() {
        val summary = TaskCardAccessibilitySemantics.summary(
            TaskCardAccessibilityContent(
                title = "Call family",
                date = null,
                time = null,
                status = "Unscheduled"
            )
        )

        assertTrue(summary.contains("No date set"))
        assertTrue(summary.contains("No time set"))
        assertFalse(summary.contains("task_id"))
        assertFalse(summary.contains("Room"))
    }

    @Test
    fun identicalTitlesRemainTwoSeparateAccessibleItems() {
        val first = TaskCardAccessibilityContent("Medication", null, null, "Unscheduled")
        val second = TaskCardAccessibilityContent("Medication", null, null, "Unscheduled")
        val accessibleItems = listOf(
            TaskCardAccessibilitySemantics.summary(first),
            TaskCardAccessibilitySemantics.summary(second)
        )

        assertEquals(2, accessibleItems.size)
        assertEquals(accessibleItems[0], accessibleItems[1])
    }

    @Test
    fun statusCoversCompletedOverdueTodayUpcomingAndUnscheduled() {
        val locale = Locale.US
        val now = SimpleDateFormat("dd/MM/yyyy hh:mm a", locale).parse("03/08/2026 12:00 PM")!!

        assertEquals("Completed", TaskCardAccessibilitySemantics.status(true, null, null, now, locale))
        assertEquals("Unscheduled", TaskCardAccessibilitySemantics.status(false, null, null, now, locale))
        assertEquals("Overdue", TaskCardAccessibilitySemantics.status(false, "02/08/2026", "09:00 AM", now, locale))
        assertEquals("Today", TaskCardAccessibilitySemantics.status(false, "03/08/2026", "01:00 PM", now, locale))
        assertEquals("Upcoming", TaskCardAccessibilitySemantics.status(false, "04/08/2026", "09:00 AM", now, locale))
    }
}
