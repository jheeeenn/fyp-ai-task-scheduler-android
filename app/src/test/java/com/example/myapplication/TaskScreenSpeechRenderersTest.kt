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
    fun buttonSingleTapDescriptionsAreConciseLabels() {
        val descriptions = listOf(
            TaskScreenControlSpeechRenderer.homeDescription() to "Home",
            TaskScreenControlSpeechRenderer.assistantDescription() to "Talk to Assistant",
            TaskScreenControlSpeechRenderer.taskAssistantDescription() to "Talk to Assistant",
            TaskScreenControlSpeechRenderer.readAllDescription() to "Read All",
            TaskScreenControlSpeechRenderer.saveDescription() to "Save",
            TaskScreenControlSpeechRenderer.toggleDescription(false) to "Mark Done",
            TaskScreenControlSpeechRenderer.toggleDescription(true) to "Undo",
            TaskScreenControlSpeechRenderer.editDescription() to "Edit",
            TaskScreenControlSpeechRenderer.deleteDescription() to "Delete"
        )

        descriptions.forEach { (actual, expected) ->
            assertEquals(expected, actual)
            assertFalse(actual.contains("double tap", ignoreCase = true))
        }
    }

    @Test
    fun doubleTapDestinationsRemainConcise() {
        assertEquals("Returning home.", TaskScreenControlSpeechRenderer.returningHome())
        assertEquals("Going back.", TaskScreenControlSpeechRenderer.goingBack())
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

    @Test
    fun taskDetailRetryQuestionsNameTheExpectedAnswer() {
        assertEquals(
            "I couldn't use that title. What title would you like to use?",
            TaskDetailEditSpeechRenderer.retryQuestion(TaskDetailEditInteraction.WAITING_FOR_TITLE)
        )
        assertEquals(
            "I couldn't understand that date. What date would you like to use?",
            TaskDetailEditSpeechRenderer.retryQuestion(TaskDetailEditInteraction.WAITING_FOR_DATE)
        )
        assertEquals(
            "I couldn't understand that time. What time would you like to use?",
            TaskDetailEditSpeechRenderer.retryQuestion(TaskDetailEditInteraction.WAITING_FOR_TIME)
        )
        assertEquals(
            "That date and time would be in the past. What date would you like to use instead?",
            TaskDetailEditSpeechRenderer.pastScheduleRetry(TaskDetailEditInteraction.WAITING_FOR_DATE)
        )
        assertEquals(
            "That time would be in the past. What time would you like to use instead?",
            TaskDetailEditSpeechRenderer.pastScheduleRetry(TaskDetailEditInteraction.WAITING_FOR_TIME)
        )
    }

    @Test
    fun unclearConfirmationsRepeatEachPendingQuestion() {
        val expectedQuestionFragments = mapOf(
            TaskDetailEditInteraction.WAITING_FOR_SAVE_CONFIRMATION to
                "Would you like to save the changes?",
            TaskDetailEditInteraction.WAITING_FOR_HOME_CONFIRMATION to
                "Would you like to save the changes before returning home?",
            TaskDetailEditInteraction.WAITING_FOR_BACK_CONFIRMATION to
                "Would you like to save the changes before going back?",
            TaskDetailEditInteraction.WAITING_FOR_ASSISTANT_EXIT_CONFIRMATION to
                "Would you like to save the changes before opening the assistant?",
            TaskDetailEditInteraction.WAITING_FOR_DELETE_DISCARD_CONFIRMATION to
                "Continue and discard the unsaved changes?"
        )

        expectedQuestionFragments.forEach { (interaction, question) ->
            val retry = TaskDetailEditSpeechRenderer.retryQuestion(interaction)
            assertEquals(true, retry.contains(question))
            assertEquals(true, retry.endsWith("Please say yes, no, or cancel."))
        }
    }

    private fun task(id: Long, isDone: Boolean = false) = TaskEntity(
        id = id,
        title = "Task $id",
        dueDate = "05/08/2026",
        dueTime = "09:00 AM",
        isDone = isDone
    )
}
