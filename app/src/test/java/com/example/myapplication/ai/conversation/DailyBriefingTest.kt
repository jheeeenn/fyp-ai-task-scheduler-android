package com.example.myapplication.ai.conversation

import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyBriefingTest {
    @Test
    fun completedRootsAreExcludedAndSubtasksAreDescriptiveOnly() {
        val active = task(1, "Active", "27/07/2026", "10:00")
        val completed = task(2, "Completed", "27/07/2026", "09:00", isDone = true)
        val subtask = task(
            id = 3,
            title = "Child",
            date = "27/07/2026",
            time = "08:00",
            parentTaskId = active.id
        )

        val snapshot = build(
            roots = listOf(active, completed, subtask),
            subtasks = mapOf(active.id to listOf(subtask))
        )

        assertEquals(1, snapshot.todayActiveCount)
        assertEquals(listOf("Active"), snapshot.highlightedTasks.map { it.title })
        assertEquals(1, snapshot.highlightedTasks.single().subtaskCount)
        assertEquals(listOf("Child"), snapshot.highlightedTasks.single().unfinishedSubtaskTitles)
    }

    @Test
    fun overdueUsesOnlyResolvedDatesBeforeToday() {
        val snapshot = build(
            roots = listOf(
                task(1, "Yesterday", "26/07/2026"),
                task(2, "Today", "27/07/2026"),
                task(3, "Tomorrow", "28/07/2026"),
                task(4, "Malformed", "2026-07-20"),
                task(5, "Missing", null),
                task(6, "Completed overdue", "25/07/2026", isDone = true)
            )
        )

        assertEquals(1, snapshot.overdueCount)
        assertEquals(1, snapshot.todayActiveCount)
    }

    @Test
    fun timedTasksSortAscendingBeforeUntimedWithDeterministicTies() {
        val snapshot = build(
            roots = listOf(
                task(7, "Untimed B", "27/07/2026"),
                task(4, "Later", "27/07/2026", "3:00 PM"),
                task(3, "Alpha", "27/07/2026", "09:00"),
                task(2, "Beta", "27/07/2026", "9 AM"),
                task(6, "Untimed A", "27/07/2026", "not-a-time"),
                task(1, "Alpha", "27/07/2026", "09:00")
            )
        )

        assertEquals(
            listOf("Alpha", "Alpha", "Beta", "Later", "Untimed A"),
            snapshot.highlightedTasks.map { it.title }
        )
        assertEquals(listOf(1L, 3L, 2L, 4L, 6L), snapshot.highlightedRoomTasks.map { it.id })
    }

    @Test
    fun highlightsAtMostFiveAndReportsAdditionalTodayCount() {
        val roots = (1L..7L).map { id ->
            task(id, "Task $id", "27/07/2026", "${id + 8}:00")
        }

        val snapshot = build(roots)

        assertEquals(7, snapshot.todayActiveCount)
        assertEquals(5, snapshot.highlightedTasks.size)
        assertEquals(2, snapshot.additionalTodayCount)
    }

    @Test
    fun speechHandlesSingularCountsTimedAndUntimedTasks() {
        val snapshot = build(
            roots = listOf(
                task(1, "Overdue", "26/07/2026"),
                task(2, "Medical check-up", "27/07/2026", "10:00"),
                task(3, "Plan revision", "27/07/2026")
            )
        )

        val speech = DailyBriefingSpeechRenderer.render(snapshot)

        assertTrue(speech.startsWith("Here is your briefing for Monday, 27 July."))
        assertTrue(speech.contains("one overdue task and two active tasks today"))
        assertTrue(speech.contains("First, Medical check-up at 10 AM."))
        assertTrue(speech.contains("Second, Plan revision, with no set time."))
    }

    @Test
    fun speechHandlesZeroTodayWithoutClaimingTheWholeScheduleIsEmpty() {
        val snapshot = build(
            roots = listOf(
                task(1, "Overdue", "26/07/2026"),
                task(2, "Future", "28/07/2026")
            )
        )

        val speech = DailyBriefingSpeechRenderer.render(snapshot)

        assertTrue(speech.contains("one overdue task and no active tasks due today"))
        assertTrue(speech.contains("review your upcoming tasks or create a new task"))
        assertFalse(speech.contains("schedule is empty", ignoreCase = true))
        assertFalse(speech.contains("no tasks at all", ignoreCase = true))
    }

    @Test
    fun speechHandlesPluralOverdueAndAdditionalTodayTasksWithoutIds() {
        val roots = listOf(
            task(98765, "Older", "25/07/2026"),
            task(2, "Yesterday", "26/07/2026")
        ) + (1L..6L).map { id ->
            task(id + 20, "Today $id", "27/07/2026", "${id + 8}:00")
        }

        val speech = DailyBriefingSpeechRenderer.render(build(roots))

        assertTrue(speech.contains("two overdue tasks and six active tasks today"))
        assertTrue(speech.contains("There is one more active task due today."))
        assertFalse(speech.contains("98765"))
        assertFalse(speech.contains("Room", ignoreCase = true))
    }

    private fun build(
        roots: List<TaskEntity>,
        subtasks: Map<Long, List<TaskEntity>> = emptyMap()
    ): DailyBriefingSnapshot = DailyBriefingSnapshotBuilder.build(
        localDate = "27/07/2026",
        rootTasks = roots,
        subtasksByParentId = subtasks
    )

    private fun task(
        id: Long,
        title: String,
        date: String?,
        time: String? = null,
        isDone: Boolean = false,
        parentTaskId: Long? = null
    ) = TaskEntity(
        id = id,
        title = title,
        dueDate = date,
        dueTime = time,
        isDone = isDone,
        parentTaskId = parentTaskId
    )
}
