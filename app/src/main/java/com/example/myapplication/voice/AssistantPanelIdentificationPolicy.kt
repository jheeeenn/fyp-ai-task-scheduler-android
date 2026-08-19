package com.example.myapplication.voice

import com.example.myapplication.accessibility.AssistantAccessibilityState

internal enum class AssistantPanelIdentificationDisposition {
    IGNORE,
    SPEAK,
    PAUSE_RECOGNITION_AND_SPEAK
}

internal object AssistantPanelIdentificationPolicy {
    fun decide(
        sessionActive: Boolean,
        forceStopping: Boolean,
        terminalDeliveryActive: Boolean,
        lifecycleEligible: Boolean,
        identificationSpeechActive: Boolean,
        assistantState: AssistantAccessibilityState,
        recognitionActiveOrPending: Boolean
    ): AssistantPanelIdentificationDisposition {
        if (
            !sessionActive ||
            forceStopping ||
            terminalDeliveryActive ||
            !lifecycleEligible ||
            identificationSpeechActive
        ) {
            return AssistantPanelIdentificationDisposition.IGNORE
        }

        if (
            assistantState == AssistantAccessibilityState.SPEAKING ||
            assistantState == AssistantAccessibilityState.PROCESSING
        ) {
            return AssistantPanelIdentificationDisposition.IGNORE
        }

        return if (recognitionActiveOrPending) {
            AssistantPanelIdentificationDisposition.PAUSE_RECOGNITION_AND_SPEAK
        } else {
            AssistantPanelIdentificationDisposition.SPEAK
        }
    }

    fun canRestartRecognition(
        callbackGeneration: Long,
        currentGeneration: Long,
        sessionActive: Boolean,
        forceStopping: Boolean,
        terminalDeliveryActive: Boolean,
        lifecycleEligible: Boolean,
        assistantState: AssistantAccessibilityState
    ): Boolean =
        callbackGeneration == currentGeneration &&
            sessionActive &&
            !forceStopping &&
            !terminalDeliveryActive &&
            lifecycleEligible &&
            assistantState != AssistantAccessibilityState.SPEAKING &&
            assistantState != AssistantAccessibilityState.PROCESSING
}
