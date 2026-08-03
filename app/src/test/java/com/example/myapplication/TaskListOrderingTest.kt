package com.example.myapplication

import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TaskListOrderingTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val now = date("03/08/2026 12:00 PM")

    @Test
    fun scheduledUsesDueTodayFutureOverdueUnscheduledCompletedGroups() {
        val tasks = listOf(
            task(90, "Completed older", "04/08/2026", "08:00 AM", isDone = true),
            task(8, "Older overdue", "02/08/2026", "09:00 AM"),
            task(3, "Future", "04/08/2026", "09:00 AM"),
            task(5, "Unscheduled", "bad-date", "09:00 AM"),
            task(2, "Later today second", "03/08/2026", "03:00 PM"),
            task(7, "Recently overdue", "03/08/2026", "11:00 AM"),
            task(1, "Later today first", "03/08/2026", "01:00 PM"),
            task(91, "Completed recent", "05/08/2026", "08:00 AM", isDone = true)
        )

        val ordered = TaskListOrdering.scheduled(tasks, now, utc)

        assertEquals(listOf(1L, 2L, 3L, 7L, 8L, 5L, 91L, 90L), ordered.map { it.id })
    }

    @Test
    fun scheduledFutureTasksAreChronologicalAndOverdueTasksAreMostRecentFirst() {
        val tasks = listOf(
            task(1, "Future later", "06/08/2026", "09:00 AM"),
            task(2, "Future sooner", "04/08/2026", "09:00 AM"),
            task(3, "Overdue older", "01/08/2026", "09:00 AM"),
            task(4, "Overdue newer", "02/08/2026", "09:00 AM")
        )

        assertEquals(
            listOf(2L, 1L, 4L, 3L),
            TaskListOrdering.scheduled(tasks, now, utc).map { it.id }
        )
    }

    @Test
    fun todayPlacesLaterTasksBeforeRecentAndOlderOverdueTasksThenCompleted() {
        val tasks = listOf(
            task(7, "Completed", "03/08/2026", "06:00 PM", isDone = true),
            task(4, "Overdue older", "03/08/2026", "08:00 AM"),
            task(2, "Due later", "03/08/2026", "05:00 PM"),
            task(5, "Unscheduled", "03/08/2026", "bad-time"),
            task(3, "Overdue recent", "03/08/2026", "11:30 AM"),
            task(1, "Due sooner", "03/08/2026", "01:00 PM")
        )

        val ordered = TaskListOrdering.today(tasks, now, utc)

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 7L), ordered.map { it.id })
    }

    @Test
    fun malformedAndMissingSchedulesFailSafelyIntoUnscheduledById() {
        val malformed = task(9, "Malformed", "31/02/2026", "09:00 AM")
        val missing = task(4, "Missing", null, null)

        assertEquals(TaskListSortGroup.UNSCHEDULED, TaskListOrdering.groupFor(malformed, now, utc))
        assertEquals(TaskListSortGroup.UNSCHEDULED, TaskListOrdering.groupFor(missing, now, utc))
        assertEquals(
            listOf(4L, 9L),
            TaskListOrdering.scheduled(listOf(malformed, missing), now, utc).map { it.id }
        )
    }

    @Test
    fun completedAlwaysMapsToCompletedAndUsesMostRecentDueFirstThenId() {
        val sameDueHigherId = task(8, "Same due higher", "05/08/2026", "09:00 AM", true)
        val sameDueLowerId = task(3, "Same due lower", "05/08/2026", "09:00 AM", true)
        val older = task(2, "Older", "04/08/2026", "09:00 AM", true)
        val malformed = task(1, "Malformed", "bad", "bad", true)

        assertEquals(
            TaskListSortGroup.COMPLETED,
            TaskListOrdering.groupFor(malformed, now, utc)
        )
        assertEquals(
            listOf(3L, 8L, 2L, 1L),
            TaskListOrdering.scheduled(
                listOf(malformed, older, sameDueHigherId, sameDueLowerId),
                now,
                utc
            ).map { it.id }
        )
    }

    private fun task(
        id: Long,
        title: String,
        date: String?,
        time: String?,
        isDone: Boolean = false
    ) = TaskEntity(id = id, title = title, dueDate = date, dueTime = time, isDone = isDone)

    private fun date(value: String): Date =
        SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.ENGLISH).apply {
            isLenient = false
            timeZone = utc
        }.parse(value)!!
}
