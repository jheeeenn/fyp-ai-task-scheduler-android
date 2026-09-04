package com.example.myapplication

import com.example.myapplication.accessibility.AssistantAccessibilityState
import com.example.myapplication.voice.AssistantInteractionMode
import com.example.myapplication.voice.AssistantTranscriptEvent
import com.example.myapplication.voice.AssistantTranscriptRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperTestSessionTest {
    @Test
    fun activationTranscriptTtsResetAndExitRemainProcessLocalPresentationState() {
        DeveloperTestSession.deactivate()
        try {
            assertEquals(
                AssistantInteractionMode.NORMAL_VOICE,
                DeveloperTestSession.interactionMode()
            )
            assertTrue(DeveloperTestSession.shouldSpeakAudio())

            DeveloperTestSession.activate()
            assertEquals(
                AssistantInteractionMode.DEVELOPER_TEXT,
                DeveloperTestSession.interactionMode()
            )
            assertFalse(DeveloperTestSession.shouldSpeakAudio())

            val event = AssistantTranscriptEvent(
                AssistantTranscriptRole.ASSISTANT,
                "Please confirm."
            )
            DeveloperTestSession.recordTranscript(event)
            DeveloperTestSession.setTtsEnabled(true)
            DeveloperTestSession.updateAssistantState(AssistantAccessibilityState.SPEAKING)
            assertEquals(listOf(event), DeveloperTestSession.transcriptSnapshot())
            assertTrue(DeveloperTestSession.shouldSpeakAudio())

            DeveloperTestSession.reset()
            assertTrue(DeveloperTestSession.isActive)
            assertTrue(DeveloperTestSession.transcriptSnapshot().isEmpty())
            assertFalse(DeveloperTestSession.ttsEnabled)
            assertEquals(
                AssistantAccessibilityState.READY,
                DeveloperTestSession.assistantState
            )
        } finally {
            DeveloperTestSession.deactivate()
        }
        assertFalse(DeveloperTestSession.isActive)
        assertEquals(
            AssistantInteractionMode.NORMAL_VOICE,
            DeveloperTestSession.interactionMode()
        )
    }
}
