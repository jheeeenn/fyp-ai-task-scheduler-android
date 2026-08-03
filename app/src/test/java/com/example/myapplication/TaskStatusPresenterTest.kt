package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TaskStatusPresenterTest {
    private val timeZone = TimeZone.getTimeZone("UTC")

    @Test
    fun completedStatusWinsEvenWithoutSchedule() {
        assertStatus(TaskVisualStatus.COMPLETED, "Completed", true, null, null)
    }

    @Test
    fun missingScheduleIsUnscheduled() {
        assertStatus(TaskVisualStatus.UNSCHEDULED, "Unscheduled", false, null, "09:00 AM")
    }

    @Test
    fun previousDateOneDayAgoUsesSingularWording() {
        assertStatus(
            TaskVisualStatus.OVERDUE,
            "Overdue by 1 day",
            false,
            "02/08/2026",
            "09:00 AM"
        )
    }

    @Test
    fun previousDateMultipleDaysAgoUsesPluralWording() {
        assertStatus(
            TaskVisualStatus.OVERDUE,
            "Overdue by 3 days",
            false,
            "31/07/2026",
            "09:00 AM"
        )
    }

    @Test
    fun earlierTodayUsesLessThanOneHourAndWholeHourWording() {
        assertStatus(
            TaskVisualStatus.OVERDUE,
            "Overdue by less than 1 hour",
            false,
            "03/08/2026",
            "12:01 PM"
        )
        assertStatus(
            TaskVisualStatus.OVERDUE,
            "Overdue by 1 hour",
            false,
            "03/08/2026",
            "11:00 AM"
        )
        assertStatus(
            TaskVisualStatus.OVERDUE,
            "Overdue by 2 hours",
            false,
            "03/08/2026",
            "10:00 AM"
        )
    }

    @Test
    fun laterTodayIncludesDisplayTime() {
        assertStatus(
            TaskVisualStatus.DUE_TODAY,
            "Due today at 9:00 PM",
            false,
            "03/08/2026",
            "09:00 PM"
        )
    }

    @Test
    fun tomorrowIncludesDisplayTime() {
        assertStatus(
            TaskVisualStatus.UPCOMING,
            "Due tomorrow at 8:30 AM",
            false,
            "04/08/2026",
            "08:30 AM"
        )
    }

    @Test
    fun futureDateUsesCleanDayCount() {
        assertStatus(
            TaskVisualStatus.UPCOMING,
            "Due in 33 days",
            false,
            "05/09/2026",
            "08:30 AM"
        )
    }

    @Test
    fun invalidDateOrTimeFailsSafely() {
        assertStatus(
            TaskVisualStatus.UNSCHEDULED,
            "Unscheduled",
            false,
            "31/02/2026",
            "25:90 PM"
        )
        assertStatus(
            TaskVisualStatus.UNSCHEDULED,
            "Unscheduled",
            false,
            "03/08/2026 trailing",
            "09:00 PM"
        )
    }

    private fun assertStatus(
        expectedVisualStatus: TaskVisualStatus,
        expectedText: String,
        isDone: Boolean,
        dueDate: String?,
        dueTime: String?
    ) {
        val presentation = TaskStatusPresenter.present(
            isDone = isDone,
            dueDate = dueDate,
            dueTime = dueTime,
            now = date("03/08/2026 12:30 PM"),
            timeZone = timeZone
        )
        assertEquals(expectedVisualStatus, presentation.visualStatus)
        assertEquals(expectedText, presentation.visibleText)
        assertEquals(expectedText, presentation.spokenText)
    }

    private fun date(value: String): Date =
        SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.ENGLISH).apply {
            isLenient = false
            timeZone = this@TaskStatusPresenterTest.timeZone
        }.parse(value)!!
}
