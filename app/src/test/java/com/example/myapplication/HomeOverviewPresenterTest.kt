package com.example.myapplication

import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HomeOverviewPresenterTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val now = date("19/08/2026 12:00 PM")
    private val today = "19/08/2026"

    @Test
    fun previewUsesTodayOrderingAndSelectsExactlyTheFirstTask() {
        val dueLater = task(3, "Later", today, "05:00 PM")
        val overdueToday = task(2, "Overdue", today, "10:00 AM")
        val dueSooner = task(1, "Sooner", today, "01:00 PM")

        val presentation = HomeOverviewPresenter.present(
            rootTasks = listOf(dueLater, overdueToday, dueSooner),
            todayRootTasks = listOf(dueLater, overdueToday, dueSooner),
            todayDate = today,
            now = now,
            timeZone = utc
        )

        assertEquals(1L, presentation.preview?.task?.id)
        assertEquals(TaskVisualStatus.DUE_TODAY, presentation.preview?.visualStatus)
    }

    @Test
    fun emptyTodayResultHasNoPreviewSurfaceModel() {
        val oldTask = task(7, "Old task", "18/08/2026", "09:00 AM")

        val presentation = HomeOverviewPresenter.present(
            rootTasks = listOf(oldTask),
            todayRootTasks = emptyList(),
            todayDate = today,
            now = now,
            timeZone = utc
        )

        assertEquals(0, presentation.todayTaskCount)
        assertNull(presentation.preview)
    }

    @Test
    fun summaryKeepsActiveTodayDefinitionAndUsesStatusPresenterForOverdueCount() {
        val futureToday = task(1, "Future", today, "01:00 PM")
        val overdueToday = task(2, "Late today", today, "10:00 AM")
        val overdueEarlierDay = task(3, "Late yesterday", "18/08/2026", "10:00 AM")
        val completedToday = task(4, "Done", today, "11:00 AM", isDone = true)

        val presentation = HomeOverviewPresenter.present(
            rootTasks = listOf(futureToday, overdueToday, overdueEarlierDay, completedToday),
            todayRootTasks = listOf(futureToday, overdueToday, completedToday),
            todayDate = today,
            now = now,
            timeZone = utc
        )

        assertEquals(2, presentation.todayTaskCount)
        assertEquals(2, presentation.overdueTaskCount)
    }

    private fun task(
        id: Long,
        title: String,
        dueDate: String?,
        dueTime: String?,
        isDone: Boolean = false
    ) = TaskEntity(
        id = id,
        title = title,
        dueDate = dueDate,
        dueTime = dueTime,
        isDone = isDone
    )

    private fun date(value: String): Date =
        SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.ENGLISH).apply {
            isLenient = false
            timeZone = utc
        }.parse(value)!!
}
