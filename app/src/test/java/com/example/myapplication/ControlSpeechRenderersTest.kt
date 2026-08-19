package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ControlSpeechRenderersTest {
    @Test
    fun appControlIdentificationIsConciseAndContainsNoGestureInstruction() {
        val descriptions = listOf(
            HomeControlSpeechRenderer.todayTasks(),
            HomeControlSpeechRenderer.createTask(),
            HomeControlSpeechRenderer.scheduledTasks(),
            HomeControlSpeechRenderer.settings(),
            HomeControlSpeechRenderer.assistant(),
            TaskFormControlSpeechRenderer.pickDate(),
            TaskFormControlSpeechRenderer.pickTime(),
            TaskFormControlSpeechRenderer.saveTask(),
            TaskFormControlSpeechRenderer.deleteTask(),
            TaskFormControlSpeechRenderer.cancel(),
            TaskFormControlSpeechRenderer.home(),
            TaskFormControlSpeechRenderer.assistant(),
            SettingsControlSpeechRenderer.assistantTone("Friendly"),
            SettingsControlSpeechRenderer.replyLength("Short"),
            SettingsControlSpeechRenderer.speechSpeed("Fast"),
            SettingsControlSpeechRenderer.conversationEndpoint(),
            SettingsControlSpeechRenderer.taskEndpoint(),
            SettingsControlSpeechRenderer.saveOption(),
            SettingsControlSpeechRenderer.home(),
            SettingsControlSpeechRenderer.assistant()
        )

        descriptions.forEach { description ->
            assertFalse(description.contains("double tap", ignoreCase = true))
            assertFalse(description.contains("Opening", ignoreCase = true))
        }
    }

    @Test
    fun settingsIdentificationIncludesTheCurrentOption() {
        assertEquals(
            "Assistant Tone, Friendly",
            SettingsControlSpeechRenderer.assistantTone("Friendly")
        )
        assertEquals(
            "Reply Length, Detailed",
            SettingsControlSpeechRenderer.replyLength("Detailed")
        )
        assertEquals(
            "Speech Speed, Fast",
            SettingsControlSpeechRenderer.speechSpeed("Fast")
        )
        assertEquals(
            "Conversation Agent Endpoint",
            SettingsControlSpeechRenderer.conversationEndpoint()
        )
        assertEquals("Task Agent Endpoint", SettingsControlSpeechRenderer.taskEndpoint())
    }
}
