package com.example.myapplication

import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TaskScreenSpeechRenderersTest {
    @Test
    fun scheduledActiveCountExcludesCompletedVisibleTasks() {
        assertEquals(
            2,
            TaskListScreenSpeechRenderer.activeCount(
                listOf(task(1), task(2, isDone = true), task(3))
            )
        )
    }

    @Test
    fun todayActiveCountExcludesCompletedVisibleTasks() {
        val visibleTodayTasks = listOf(task(1), task(2, isDone = true), task(3, isDone = true))

        assertEquals(1, TaskListScreenSpeechRenderer.activeCount(visibleTodayTasks))
        assertEquals(
            "You have 1 task today.",
            TaskListScreenSpeechRenderer.today(
                TaskListScreenSpeechRenderer.activeCount(visibleTodayTasks)
            )
        )
    }

    @Test
    fun scheduledCountWordingIsConciseForZeroSingularAndPlural() {
        assertEquals(
            "You have no active scheduled tasks.",
            TaskListScreenSpeechRenderer.scheduled(0)
        )
        assertEquals(
            "You have 1 active scheduled task.",
            TaskListScreenSpeechRenderer.scheduled(1)
        )
        assertEquals(
            "You have 8 active scheduled tasks.",
            TaskListScreenSpeechRenderer.scheduled(8)
        )
    }

    @Test
    fun todayCountWordingIsConciseForZeroSingularAndPlural() {
        assertEquals("You have no tasks today.", TaskListScreenSpeechRenderer.today(0))
        assertEquals("You have 1 task today.", TaskListScreenSpeechRenderer.today(1))
        assertEquals("You have 2 tasks today.", TaskListScreenSpeechRenderer.today(2))
    }

    @Test
    fun listEntrySpeechContainsNoTutorialOrExtraPageSentence() {
        listOf(
            TaskListScreenSpeechRenderer.scheduled(3),
            TaskListScreenSpeechRenderer.today(3)
        ).forEach { speech ->
            assertFalse(speech.contains("single tap", ignoreCase = true))
            assertFalse(speech.contains("double tap", ignoreCase = true))
            assertFalse(speech.contains("Home and Assistant"))
            assertFalse(speech.contains("updated", ignoreCase = true))
            assertFalse(speech.startsWith("Scheduled Tasks"))
            assertFalse(speech.startsWith("Today Tasks"))
        }
    }

    @Test
    fun detailEntryIsOnlyTheAuthoritativeTitleSentence() {
        assertEquals(
            "Task details for Final Year Project.",
            TaskDetailScreenSpeechRenderer.entry("Final Year Project")
        )
        assertFalse(
            TaskDetailScreenSpeechRenderer.entry("Final Year Project")
                .contains("tap", ignoreCase = true)
        )
    }

    @Test
    fun detailStateRetainsOnlyChangedEditFeedback() {
        val state = TaskDetailScreenSpeechState()

        assertEquals(
            "Task details for Final Year Project.",
            state.onAuthoritativeLoad("Final Year Project", "snapshot-one")
        )
        assertNull(state.onAuthoritativeLoad("Final Year Project", "snapshot-one"))
        assertEquals(
            "Task details updated.",
            state.onAuthoritativeLoad("Renamed task", "snapshot-two")
        )
    }

    @Test
    fun synchronizedMutationDoesNotProduceAnAdditionalDetailUpdate() {
        val state = TaskDetailScreenSpeechState()
        state.onAuthoritativeLoad("Task", "active")
        state.synchronize("completed")

        assertNull(state.onAuthoritativeLoad("Task", "completed"))
    }

    @Test
    fun buttonSingleTapDescriptionsAreOnlyShortLabels() {
        assertEquals("Home button.", TaskScreenControlSpeechRenderer.homeDescription())
        assertEquals("Assistant button.", TaskScreenControlSpeechRenderer.assistantDescription())
        assertEquals("Assistant button.", TaskScreenControlSpeechRenderer.taskAssistantDescription())
        assertEquals("Read all button.", TaskScreenControlSpeechRenderer.readAllDescription())
        assertEquals("Save button.", TaskScreenControlSpeechRenderer.saveDescription())
        assertEquals("Mark done button.", TaskScreenControlSpeechRenderer.toggleDescription(false))
        assertEquals("Undo button.", TaskScreenControlSpeechRenderer.toggleDescription(true))
        assertEquals("Edit button.", TaskScreenControlSpeechRenderer.editDescription())
        assertEquals("Delete button.", TaskScreenControlSpeechRenderer.deleteDescription())
    }

    @Test
    fun doubleTapDestinationsRemainConcise() {
        assertEquals("Returning home.", TaskScreenControlSpeechRenderer.returningHome())
        assertEquals("Opening assistant.", TaskScreenControlSpeechRenderer.openingAssistant())
        assertEquals("Opening task editor.", TaskScreenControlSpeechRenderer.openingTaskEditor())
        assertEquals(
            "Opening assistant to confirm deletion.",
            TaskScreenControlSpeechRenderer.openingDeleteConfirmation()
        )
        assertEquals(
            "Opening assistant for Final Year Project.",
            TaskScreenControlSpeechRenderer.openingTaskAssistant("Final Year Project")
        )
    }

    private fun task(id: Long, isDone: Boolean = false) = TaskEntity(
        id = id,
        title = "Task $id",
        dueDate = "05/08/2026",
        dueTime = "09:00 AM",
        isDone = isDone
    )
}
