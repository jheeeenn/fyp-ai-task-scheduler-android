package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateDraftReadTarget
import com.example.myapplication.voice.CreateTaskDialogState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CreateDraftReadResponseRendererTest {
    @Test
    fun saveConfirmationReadsAndroidValuesAndContinuesConfirmation() {
        assertEquals(
            "The current title is Buy medicine. Would you like to save this task?",
            render(CreateDraftReadTarget.TITLE, CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
        )
        assertEquals(
            "It's currently set for 9 PM. Would you like to save this task?",
            render(CreateDraftReadTarget.TIME, CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
        )
        assertEquals(
            "It's currently scheduled for next Monday at 9 PM. " +
                "Would you like to save this task?",
            render(CreateDraftReadTarget.SCHEDULE, CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION)
        )
    }

    @Test
    fun readWhileCollectingDateReturnsToThePendingQuestion() {
        val response = render(CreateDraftReadTarget.TITLE, CreateTaskDialogState.WAITING_FOR_DATE)

        assertEquals("The current title is Buy medicine. What date would you like?", response)
        assertFalse(response.contains("save", ignoreCase = true))
    }

    @Test
    fun replacementPromptAndMissingValuesRemainNatural() {
        val replacement = CreateDraftReadResponseRenderer.render(
            target = CreateDraftReadTarget.TIME,
            title = "Buy medicine",
            date = "07/09/2026",
            time = null,
            state = CreateTaskDialogState.WAITING_FOR_TIME,
            pendingReplacementField = CreateDraftField.TIME
        )
        assertEquals(
            "This task doesn't have a time set yet. What time should I use instead?",
            replacement
        )

        val summary = render(CreateDraftReadTarget.SUMMARY, CreateTaskDialogState.WAITING_FOR_DATE)
        assertTrue(summary.startsWith("The current draft is Buy medicine"))
        assertTrue(summary.endsWith("What date would you like?"))
    }

    private fun render(
        target: CreateDraftReadTarget,
        state: CreateTaskDialogState
    ): String = CreateDraftReadResponseRenderer.render(
        target = target,
        title = "Buy medicine",
        date = "07/09/2026",
        time = "9:00 PM",
        state = state,
        pendingReplacementField = null,
        baseCalendar = baseCalendar()
    )

    private fun baseCalendar(): Calendar = Calendar.getInstance(
        TimeZone.getTimeZone("Asia/Kuala_Lumpur")
    ).apply {
        set(2026, Calendar.SEPTEMBER, 1, 9, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
