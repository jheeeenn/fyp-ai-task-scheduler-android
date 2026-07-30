package com.example.myapplication.ai.conversation.suggestion

import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ContextSuggestionSpeechRendererTest {
    @Test
    fun focusSpeechUsesAuthoritativeOverdueTodayUpcomingAndUnscheduledReasons() {
        val cases = listOf(
            task(1, "Overdue", "30/07/2026", "10 AM") to
                "It is overdue since 30 July at 10 AM.",
            task(2, "Today", "30/07/2026", "5 PM") to
                "It is due today at 5 PM.",
            task(3, "Upcoming", "02/08/2026", "10 AM") to
                "It is your next upcoming task on 2 August at 10 AM.",
            task(4, "Unscheduled") to
                "It does not currently have a scheduled time."
        )

        cases.forEach { (task, reason) ->
            val snapshot = snapshot(listOf(task))
            val speech = ContextSuggestionSpeechRenderer.render(
                decision(ContextSuggestionType.FOCUS_TASK),
                snapshot,
                primaryTask = task,
                now = now()
            )
            assertTrue(speech.startsWith("A good next task is ${task.title}."))
            assertTrue(speech.contains(reason))
        }
    }

    @Test
    fun continueBreakdownReviewAndNoSuggestionHaveBoundedWording() {
        val root = task(1, "Final year project", "31/07/2026", "9 AM")
        val step = task(10, "Draft introduction", parentId = root.id)
        val continueSnapshot = snapshot(
            listOf(root),
            mapOf(root.id to listOf(step))
        )
        val continueSpeech = ContextSuggestionSpeechRenderer.render(
            decision(ContextSuggestionType.CONTINUE_SUBTASK),
            continueSnapshot,
            primaryTask = root,
            firstUnfinishedSubtask = step,
            now = now()
        )
        assertTrue(
            continueSpeech.startsWith(
                "To make progress on Final year project, continue with Draft introduction."
            )
        )

        val breakdownSnapshot = snapshot(listOf(root))
        val breakdownSpeech = ContextSuggestionSpeechRenderer.render(
            decision(ContextSuggestionType.BREAK_DOWN_TASK),
            breakdownSnapshot,
            primaryTask = root,
            now = now()
        )
        assertEquals(
            "You may want to break down Final year project into smaller steps. " +
                "Say, 'Break down Final year project,' when you are ready.",
            breakdownSpeech
        )

        val second = task(2, "Supervisor call", "31/07/2026", "9 AM")
        val pairSnapshot = snapshot(listOf(root, second))
        val pair = pairSnapshot.closePairs.single()
        val reviewSpeech = ContextSuggestionSpeechRenderer.render(
            ContextSuggestionDecision(
                ContextSuggestionType.REVIEW_CLOSE_SCHEDULE,
                pair.primaryRef,
                pair.secondaryRef,
                0.95
            ),
            pairSnapshot,
            primaryTask = pairSnapshot.candidate(pair.primaryRef)?.capturedTask,
            secondaryTask = pairSnapshot.candidate(pair.secondaryRef)?.capturedTask,
            now = now()
        )
        assertTrue(reviewSpeech.contains("at the same time on 31 July."))
        assertFalse(reviewSpeech.contains("conflict", ignoreCase = true))

        assertEquals(
            "You do not have an active task for me to suggest right now.",
            ContextSuggestionSpeechRenderer.render(
                ContextSuggestionDecision(
                    ContextSuggestionType.NO_SUGGESTION,
                    "",
                    "",
                    1.0
                ),
                snapshot(emptyList()),
                now = now()
            )
        )
    }

    @Test
    fun nonZeroCloseGapUsesMinutesApartWording() {
        val first = task(1, "First", "31/07/2026", "9 AM")
        val second = task(2, "Second", "31/07/2026", "9:20 AM")
        val snapshot = snapshot(listOf(first, second))
        val pair = snapshot.closePairs.single()

        val speech = ContextSuggestionSpeechRenderer.render(
            ContextSuggestionDecision(
                ContextSuggestionType.REVIEW_CLOSE_SCHEDULE,
                pair.primaryRef,
                pair.secondaryRef,
                0.95
            ),
            snapshot,
            primaryTask = snapshot.candidate(pair.primaryRef)?.capturedTask,
            secondaryTask = snapshot.candidate(pair.secondaryRef)?.capturedTask,
            now = now()
        )

        assertTrue(speech.contains("20 minutes apart on 31 July."))
    }

    private fun decision(type: ContextSuggestionType) =
        ContextSuggestionDecision(type, "S1", "", 0.95)

    private fun snapshot(
        roots: List<TaskEntity>,
        subtasks: Map<Long, List<TaskEntity>> = emptyMap()
    ) = ContextSuggestionSnapshotBuilder.build(now(), roots, subtasks)

    private fun now(): Calendar = Calendar.getInstance(
        TimeZone.getTimeZone("Asia/Kuala_Lumpur"),
        Locale.UK
    ).apply {
        set(2026, Calendar.JULY, 30, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun task(
        id: Long,
        title: String,
        date: String? = null,
        time: String? = null,
        parentId: Long? = null
    ) = TaskEntity(
        id = id,
        title = title,
        dueDate = date,
        dueTime = time,
        parentTaskId = parentId
    )
}
