package com.example.myapplication.ai.temporal

import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TaskTemporalFilterTest {
    private val resolver = TemporalQueryResolver()
    private val tasks = listOf(
        TaskEntity(1, "overdue groceries", "8:30 PM", false, "04/05/2026"),
        TaskEntity(2, "breakfast preparation", "8:00 AM", false, "15/07/2026"),
        TaskEntity(3, "medical checkup", "9:00 AM", false, "21/07/2026"),
        TaskEntity(4, "evening meeting", "7:00 PM", false, "21/07/2026"),
        TaskEntity(5, "completed next-week task", "10:00 AM", true, "22/07/2026")
    )
    private fun base() = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { set(2026, Calendar.JULY, 14, 9, 0, 0); set(Calendar.MILLISECOND, 0) }
    private fun filter(date: String? = null, time: String? = null) = TaskTemporalFilter.filterAndSort(tasks, resolver.resolve(date, time, "", base())).map { it.title }

    @Test fun filtersExpectedWindows() {
        assertEquals(listOf("medical checkup", "evening meeting"), filter("next week"))
        assertEquals(listOf("medical checkup"), filter("next week", "morning"))
        assertEquals(listOf("evening meeting"), filter("next week", "evening"))
        assertEquals(listOf("breakfast preparation"), filter("tomorrow"))
        assertEquals(listOf("overdue groceries"), filter("overdue"))
    }

    @Test fun invalidTemporalInputDoesNotBecomeAllTaskResult() {
        val window = resolver.resolve("somedayish", null, "", base())
        assertEquals(TemporalResolutionStatus.UNRESOLVED, window.status)
        assertTrue(TaskTemporalFilter.filterAndSort(tasks, window).isEmpty())
    }

    @Test fun sortsByDateThenTime() {
        assertEquals(
            listOf("overdue groceries", "breakfast preparation", "medical checkup", "evening meeting"),
            TaskTemporalFilter.filterAndSort(tasks, TemporalQueryWindow(TemporalResolutionStatus.NONE)).map { it.title }
        )
    }
}
