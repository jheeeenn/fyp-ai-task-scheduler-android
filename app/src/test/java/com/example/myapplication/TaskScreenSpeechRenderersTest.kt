package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskScreenSpeechRenderersTest {
    @Test
    fun scheduledOrientationContainsTitleCountAndGestureHelp() {
        val speech = TaskListScreenSpeechRenderer.orientation("Scheduled Tasks", 2)

        assertTrue(speech.startsWith("Scheduled Tasks. Two tasks."))
        assertTrue(speech.contains("Single tap an item to hear it."))
        assertTrue(speech.contains("Double tap to open or activate it."))
        assertTrue(speech.contains("Home and Assistant are at the bottom."))
    }

    @Test
    fun todayOrientationContainsNaturalCountsAndGestureHelp() {
        assertTrue(
            TaskListScreenSpeechRenderer.orientation("Today Tasks", 1)
                .startsWith("Today Tasks. One task.")
        )
        assertTrue(
            TaskListScreenSpeechRenderer.orientation("Today Tasks", 0)
                .contains("No tasks.")
        )
        assertEquals("Two tasks.", TaskListScreenSpeechRenderer.countPhrase(2))
        assertEquals("7 tasks.", TaskListScreenSpeechRenderer.countPhrase(7))
    }

    @Test
    fun listFullOrientationOccursOnceAndRefreshOnlyFollowsChangedData() {
        val state = TaskListScreenSpeechState()
        val initial = state.onAuthoritativeLoad("Scheduled Tasks", 1, listOf("A"))
        val unchanged = state.onAuthoritativeLoad("Scheduled Tasks", 1, listOf("A"))
        val changed = state.onAuthoritativeLoad("Scheduled Tasks", 2, listOf("A", "B"))

        assertTrue(requireNotNull(initial).contains("Single tap an item"))
        assertNull(unchanged)
        assertEquals("Scheduled Tasks updated. Two tasks.", changed)
        assertFalse(requireNotNull(changed).contains("Double tap to open"))
    }

    @Test
    fun detailEntryUsesAuthoritativeTitleOnceAndChangedDataUsesShortUpdate() {
        val state = TaskDetailScreenSpeechState()
        val initial = state.onAuthoritativeLoad("Final Year Project", "snapshot-one")
        val unchanged = state.onAuthoritativeLoad("Final Year Project", "snapshot-one")
        val updated = state.onAuthoritativeLoad("Renamed task", "snapshot-two")

        assertEquals(
            "Task details for Final Year Project. " +
                "Single tap information or buttons to hear them. " +
                "Double tap a button to activate it.",
            initial
        )
        assertNull(unchanged)
        assertEquals("Task details updated.", updated)
    }

    @Test
    fun synchronizedMutationDoesNotLookLikeAnEditReturn() {
        val state = TaskDetailScreenSpeechState()
        state.onAuthoritativeLoad("Task", "active")
        state.synchronize("completed")

        assertNull(state.onAuthoritativeLoad("Task", "completed"))
    }

    @Test
    fun controlDescriptionsFollowTheVoiceFirstGrammar() {
        assertEquals(
            "Home. Double tap to return to the Home screen.",
            TaskScreenControlSpeechRenderer.homeDescription()
        )
        assertEquals(
            "Assistant. Double tap to open the voice assistant.",
            TaskScreenControlSpeechRenderer.assistantDescription()
        )
        assertTrue(TaskScreenControlSpeechRenderer.toggleDescription(false).startsWith("Mark task complete"))
        assertTrue(TaskScreenControlSpeechRenderer.toggleDescription(true).startsWith("Undo completion"))
        assertEquals(
            "Opening the assistant for Final Year Project.",
            TaskScreenControlSpeechRenderer.openingTaskAssistant("Final Year Project")
        )
    }
}
