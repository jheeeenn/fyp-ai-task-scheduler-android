package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlSpeechRenderersTest {
    @Test
    fun homeIdentificationDescribesFutureActivationWithoutClaimingNavigationStarted() {
        val descriptions = listOf(
            HomeControlSpeechRenderer.todayTasks(),
            HomeControlSpeechRenderer.createTask(),
            HomeControlSpeechRenderer.scheduledTasks(),
            HomeControlSpeechRenderer.settings(),
            HomeControlSpeechRenderer.assistant()
        )

        descriptions.forEach { description ->
            assertTrue(description.contains("Double tap"))
            assertFalse(description.contains("Opening", ignoreCase = true))
        }
    }

    @Test
    fun settingsIdentificationIncludesTheCurrentOption() {
        assertEquals(
            "Assistant Tone, Friendly. Double tap to change.",
            SettingsControlSpeechRenderer.assistantTone("Friendly")
        )
        assertEquals(
            "Reply Length, Detailed. Double tap to change.",
            SettingsControlSpeechRenderer.replyLength("Detailed")
        )
    }
}
