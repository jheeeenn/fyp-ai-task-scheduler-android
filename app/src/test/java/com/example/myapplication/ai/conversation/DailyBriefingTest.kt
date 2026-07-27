package com.example.myapplication.ai.conversation

import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyBriefingTest {
    @Test
    fun upcomingWindowIncludesTomorrowAndDaySevenButNotTodayOrDayEight() {
        val snapshot = build(
            listOf(
                task(1, "Today", "27/07/2026"),
                task(2, "Tomorrow", "28/07/2026"),
                task(3, "Day seven", "03/08/2026"),
                task(4, "Day eight", "04/08/2026")
            )
        )

        assertEquals(1, snapshot.todayActiveCount)
        assertEquals(2, snapshot.upcomingActiveCount)
        assertEquals(
            listOf("Today", "Tomorrow", "Day seven"),
            snapshot.spokenItems.map { it.task.title }
        )
    }

    @Test
    fun malformedMissingCompletedAndSubtaskDatesAreNotUpcoming() {
        val activeRoot = task(1, "Active root", "28/07/2026")
        val completed = task(2, "Completed", "29/07/2026", isDone = true)
        val child = task(
            3,
            "Child",
            "30/07/2026",
            parentTaskId = activeRoot.id
        )
        val snapshot = build(
            roots = listOf(
                activeRoot,
                completed,
                child,
                task(4, "Malformed", "2026-07-31"),
                task(5, "Missing", null)
            ),
            subtasks = mapOf(activeRoot.id to listOf(child))
        )

        assertEquals(1, snapshot.upcomingActiveCount)
        assertEquals(listOf("Active root"), snapshot.spokenItems.map { it.task.title })
        assertEquals(1, snapshot.spokenItems.single().task.subtaskCount)
        assertEquals(
            listOf("Child"),
            snapshot.spokenItems.single().task.unfinishedSubtaskTitles
        )
    }

    @Test
    fun upcomingCountIsAuthoritativeBeyondFiveSpokenItems() {
        val snapshot = build(
            (1L..8L).map { id ->
                task(id, "Upcoming $id", "28/07/2026", "${id + 8}:00")
            }
        )

        assertEquals(8, snapshot.upcomingActiveCount)
        assertEquals(5, snapshot.spokenItems.size)
        assertEquals(3, snapshot.additionalUpcomingCount)
    }

    @Test
    fun upcomingSortsByDateThenTimedBeforeUntimedAndTime() {
        val snapshot = build(
            listOf(
                task(1, "Later date", "30/07/2026", "08:00"),
                task(2, "Untimed", "28/07/2026"),
                task(3, "Afternoon", "28/07/2026", "3 PM"),
                task(4, "Morning", "28/07/2026", "09:00"),
                task(5, "Malformed time", "28/07/2026", "soon")
            )
        )

        assertEquals(
            listOf("Morning", "Afternoon", "Malformed time", "Untimed", "Later date"),
            snapshot.spokenItems.map { it.task.title }
        )
    }

    @Test
    fun matchingDateAndTimeSortByTitleThenPrivateId() {
        val snapshot = build(
            listOf(
                task(3, "Alpha", "28/07/2026", "09:00"),
                task(2, "Beta", "28/07/2026", "09:00"),
                task(1, "Alpha", "28/07/2026", "09:00")
            )
        )

        assertEquals(listOf("Alpha", "Alpha", "Beta"), snapshot.spokenItems.map { it.task.title })
        assertEquals(listOf(1L, 3L, 2L), snapshot.spokenRoomTasks.map { it.id })
        val speech = DailyBriefingSpeechRenderer.render(snapshot)
        assertFalse(speech.contains("T1"))
        assertFalse(speech.contains("Room", ignoreCase = true))
    }

    @Test
    fun mostRecentlyOverdueTaskIsFocusWithDeterministicScheduleOrdering() {
        val snapshot = build(
            listOf(
                task(8, "Older", "24/07/2026", "08:00"),
                task(7, "Untimed close", "26/07/2026"),
                task(6, "Later close", "26/07/2026", "11:00"),
                task(5, "Earlier close", "26/07/2026", "09:00")
            )
        )

        assertEquals(4, snapshot.overdueCount)
        assertEquals("Earlier close", snapshot.suggestedFocus?.title)
        assertEquals(
            DailyBriefingFocusReason.RECENTLY_OVERDUE,
            snapshot.suggestedFocusReason
        )
        assertEquals(DailyBriefingItemCategory.OVERDUE, snapshot.spokenItems.single().category)
        assertEquals(listOf(5L), snapshot.spokenRoomTasks.map { it.id })
    }

    @Test
    fun earliestTimedTodayTaskIsFocusWhenThereIsNoOverdueTask() {
        val snapshot = build(
            listOf(
                task(1, "Untimed", "27/07/2026"),
                task(2, "Later", "27/07/2026", "14:00"),
                task(3, "Earlier", "27/07/2026", "09:00")
            )
        )

        assertEquals("Earlier", snapshot.suggestedFocus?.title)
        assertEquals(DailyBriefingFocusReason.EARLIEST_TODAY, snapshot.suggestedFocusReason)
    }

    @Test
    fun untimedTodayTaskCanBeFocusWhenNoTimedTodayTaskExists() {
        val snapshot = build(
            listOf(
                task(2, "Zulu", "27/07/2026"),
                task(1, "Alpha", "27/07/2026")
            )
        )

        assertEquals("Alpha", snapshot.suggestedFocus?.title)
    }

    @Test
    fun earliestUpcomingTaskIsFocusWhenNothingIsOverdueOrToday() {
        val snapshot = build(
            listOf(
                task(1, "Later", "30/07/2026", "08:00"),
                task(2, "Tomorrow afternoon", "28/07/2026", "15:00"),
                task(3, "Tomorrow morning", "28/07/2026", "09:00")
            )
        )

        assertEquals("Tomorrow morning", snapshot.suggestedFocus?.title)
        assertEquals(DailyBriefingFocusReason.NEXT_UPCOMING, snapshot.suggestedFocusReason)
    }

    @Test
    fun noFocusExistsWhenThereAreNoRelevantTasks() {
        val snapshot = build(
            listOf(
                task(1, "Too far away", "04/08/2026"),
                task(2, "Malformed", "2026-07-27")
            )
        )

        assertNull(snapshot.suggestedFocus)
        assertNull(snapshot.suggestedFocusReason)
        assertTrue(snapshot.spokenItems.isEmpty())
    }

    @Test
    fun focusIsSpokenFirstOnceThenTodayAndUpcomingInAuthoritativeOrder() {
        val snapshot = build(
            listOf(
                task(10, "Overdue focus", "26/07/2026", "10:00"),
                task(1, "Today later", "27/07/2026", "11:00"),
                task(2, "Today earlier", "27/07/2026", "09:00"),
                task(3, "Upcoming later", "29/07/2026"),
                task(4, "Upcoming earlier", "28/07/2026")
            )
        )

        assertEquals(
            listOf(
                "Overdue focus",
                "Today earlier",
                "Today later",
                "Upcoming earlier",
                "Upcoming later"
            ),
            snapshot.spokenItems.map { it.task.title }
        )
        assertTrue(snapshot.spokenItems.first().isSuggestedFocus)
        assertEquals(1, snapshot.spokenItems.count { it.task.title == "Overdue focus" })
        assertEquals(snapshot.spokenItems.map { it.task.title }, snapshot.spokenRoomTasks.map { it.title })
    }

    @Test
    fun todayFocusIsNotDuplicated() {
        val snapshot = build(
            listOf(
                task(1, "First today", "27/07/2026", "09:00"),
                task(2, "Second today", "27/07/2026", "10:00"),
                task(3, "Upcoming", "28/07/2026")
            )
        )

        assertEquals(
            listOf("First today", "Second today", "Upcoming"),
            snapshot.spokenItems.map { it.task.title }
        )
        assertEquals(1, snapshot.spokenItems.count { it.isSuggestedFocus })
    }

    @Test
    fun atMostFiveItemsAndAdditionalTodayCountAreCorrect() {
        val snapshot = build(
            (1L..7L).map { id ->
                task(id, "Today $id", "27/07/2026", "${id + 8}:00")
            } + listOf(task(20, "Upcoming", "28/07/2026"))
        )

        assertEquals(5, snapshot.spokenItems.size)
        assertEquals(2, snapshot.additionalTodayCount)
        assertEquals(1, snapshot.additionalUpcomingCount)
    }

    @Test
    fun additionalUpcomingCountAccountsOnlyForUnspokenUpcomingTasks() {
        val snapshot = build(
            listOf(
                task(1, "Today 1", "27/07/2026", "09:00"),
                task(2, "Today 2", "27/07/2026", "10:00"),
                task(3, "Today 3", "27/07/2026", "11:00")
            ) + (1L..4L).map { id ->
                task(id + 10, "Upcoming $id", "28/07/2026", "${id + 8}:00")
            }
        )

        assertEquals(0, snapshot.additionalTodayCount)
        assertEquals(2, snapshot.additionalUpcomingCount)
    }

    @Test
    fun speechHandlesZeroCountsWithoutClaimingEntireScheduleIsEmpty() {
        val speech = DailyBriefingSpeechRenderer.render(build(emptyList()))

        assertTrue(speech.contains("no overdue tasks"))
        assertTrue(speech.contains("no active tasks due today"))
        assertTrue(speech.contains("no upcoming tasks within the next seven days"))
        assertTrue(speech.contains("request the full task list"))
        assertFalse(speech.contains("schedule is empty", ignoreCase = true))
        assertFalse(speech.contains("no tasks at all", ignoreCase = true))
    }

    @Test
    fun speechHandlesSingularCountsAndFocusFromToday() {
        val speech = DailyBriefingSpeechRenderer.render(
            build(
                listOf(
                    task(1, "Yesterday", "26/07/2026"),
                    task(2, "Today task", "27/07/2026", "10:00"),
                    task(3, "Tomorrow", "28/07/2026")
                )
            )
        )

        assertTrue(speech.startsWith("Here is your briefing for Monday, 27 July."))
        assertTrue(
            speech.contains(
                "one overdue task, one active task due today, and one upcoming task"
            )
        )
        assertTrue(
            speech.contains(
                "Suggested focus, first, Yesterday, overdue since Sunday, 26 July, with no set time."
            )
        )
    }

    @Test
    fun speechHandlesPluralCountsAndRemainingCounts() {
        val roots = listOf(
            task(98765, "Overdue focus", "26/07/2026"),
            task(98766, "Older overdue", "25/07/2026")
        ) + (1L..6L).map { id ->
            task(id + 20, "Today $id", "27/07/2026", "${id + 8}:00")
        } + (1L..3L).map { id ->
            task(id + 30, "Upcoming $id", "28/07/2026", "${id + 8}:00")
        }

        val speech = DailyBriefingSpeechRenderer.render(build(roots))

        assertTrue(
            speech.contains(
                "two overdue tasks, six active tasks due today, and three upcoming tasks"
            )
        )
        assertTrue(speech.contains("There are two more active tasks due today."))
        assertTrue(speech.contains("There are three more upcoming tasks within the next seven days."))
        assertFalse(speech.contains("98765"))
        assertFalse(speech.contains("Room", ignoreCase = true))
        assertFalse(speech.contains("T1"))
    }

    @Test
    fun speechStatesTodayUpcomingAndOverdueDatesWithAvailableTimes() {
        val speech = DailyBriefingSpeechRenderer.render(
            build(
                listOf(
                    task(1, "Overdue item", "26/07/2026", "10:00"),
                    task(2, "Today item", "27/07/2026", "11:00"),
                    task(3, "Upcoming item", "30/07/2026", "15:00")
                )
            )
        )

        assertTrue(speech.contains("overdue since Sunday, 26 July at 10 AM"))
        assertTrue(speech.contains("due today at 11 AM"))
        assertTrue(speech.contains("upcoming on Thursday, 30 July at 3 PM"))
        assertTrue(speech.contains("ask about one of these tasks"))
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
