package com.example.myapplication.voice

import com.example.myapplication.accessibility.AssistantAccessibilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantPanelIdentificationPolicyTest {
    @Test
    fun activeRecognitionMustPauseBeforeIdentificationSpeech() {
        assertEquals(
            AssistantPanelIdentificationDisposition.PAUSE_RECOGNITION_AND_SPEAK,
            decide(
                assistantState = AssistantAccessibilityState.LISTENING,
                recognitionActiveOrPending = true
            )
        )
    }

    @Test
    fun readyActiveSessionMaySpeakWithoutStartingARecognitionCycle() {
        assertEquals(
            AssistantPanelIdentificationDisposition.SPEAK,
            decide(assistantState = AssistantAccessibilityState.READY)
        )
    }

    @Test
    fun realAssistantSpeechAndProcessingRemainAuthoritative() {
        listOf(
            AssistantAccessibilityState.SPEAKING,
            AssistantAccessibilityState.PROCESSING
        ).forEach { state ->
            assertEquals(
                AssistantPanelIdentificationDisposition.IGNORE,
                decide(assistantState = state)
            )
        }
    }

    @Test
    fun stoppedTerminalForceStoppingAndLifecycleInvalidSessionsIgnoreIdentification() {
        assertEquals(
            AssistantPanelIdentificationDisposition.IGNORE,
            decide(sessionActive = false)
        )
        assertEquals(
            AssistantPanelIdentificationDisposition.IGNORE,
            decide(forceStopping = true)
        )
        assertEquals(
            AssistantPanelIdentificationDisposition.IGNORE,
            decide(terminalDeliveryActive = true)
        )
        assertEquals(
            AssistantPanelIdentificationDisposition.IGNORE,
            decide(lifecycleEligible = false)
        )
        assertEquals(
            AssistantPanelIdentificationDisposition.IGNORE,
            decide(identificationSpeechActive = true)
        )
    }

    @Test
    fun restartRequiresSameEligibleActiveSessionAndNoAuthoritativeSpeech() {
        assertTrue(canRestart())
        assertFalse(canRestart(callbackGeneration = 6L))
        assertFalse(canRestart(sessionActive = false))
        assertFalse(canRestart(forceStopping = true))
        assertFalse(canRestart(terminalDeliveryActive = true))
        assertFalse(canRestart(lifecycleEligible = false))
        assertFalse(canRestart(assistantState = AssistantAccessibilityState.SPEAKING))
        assertFalse(canRestart(assistantState = AssistantAccessibilityState.PROCESSING))
    }

    private fun decide(
        sessionActive: Boolean = true,
        forceStopping: Boolean = false,
        terminalDeliveryActive: Boolean = false,
        lifecycleEligible: Boolean = true,
        identificationSpeechActive: Boolean = false,
        assistantState: AssistantAccessibilityState = AssistantAccessibilityState.READY,
        recognitionActiveOrPending: Boolean = false
    ): AssistantPanelIdentificationDisposition =
        AssistantPanelIdentificationPolicy.decide(
            sessionActive = sessionActive,
            forceStopping = forceStopping,
            terminalDeliveryActive = terminalDeliveryActive,
            lifecycleEligible = lifecycleEligible,
            identificationSpeechActive = identificationSpeechActive,
            assistantState = assistantState,
            recognitionActiveOrPending = recognitionActiveOrPending
        )

    private fun canRestart(
        callbackGeneration: Long = 7L,
        currentGeneration: Long = 7L,
        sessionActive: Boolean = true,
        forceStopping: Boolean = false,
        terminalDeliveryActive: Boolean = false,
        lifecycleEligible: Boolean = true,
        assistantState: AssistantAccessibilityState = AssistantAccessibilityState.LISTENING
    ): Boolean = AssistantPanelIdentificationPolicy.canRestartRecognition(
        callbackGeneration = callbackGeneration,
        currentGeneration = currentGeneration,
        sessionActive = sessionActive,
        forceStopping = forceStopping,
        terminalDeliveryActive = terminalDeliveryActive,
        lifecycleEligible = lifecycleEligible,
        assistantState = assistantState
    )
}
