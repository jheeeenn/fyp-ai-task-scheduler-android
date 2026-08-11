package com.example.myapplication.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProcessingHapticIntegrationSourceTest {
    private val sessionSource =
        File("src/main/java/com/example/myapplication/voice/AssistantVoiceSession.kt").readText()
    private val bottomSheetSource =
        File("src/main/java/com/example/myapplication/AssistantBottomSheet.kt").readText()
    private val settingsSource =
        File("src/main/java/com/example/myapplication/SettingsActivity.kt").readText()
    private val hapticSource =
        File("src/main/java/com/example/myapplication/HapticFeedbackExtensions.kt").readText()
    private val settingsLayout = File("src/main/res/layout/activity_settings.xml").readText()

    @Test
    fun onlySubmittedVoiceOrTypedInputStartsProcessingFeedback() {
        val endOfSpeech = callbackBody("override fun onEndOfSpeech()", "override fun onError")
        val results = callbackBody("override fun onResults(results: Bundle?)", "override fun onPartialResults")
        val typed = functionBody("fun submitTypedText(", "fun onTypedInputCancelled")

        assertTrue(endOfSpeech.contains("AssistantAccessibilityState.PROCESSING"))
        assertFalse(endOfSpeech.contains("submittedCommand = true"))
        assertTrue(results.contains("submittedCommand = true"))
        assertTrue(typed.contains("submittedCommand = true"))
    }

    @Test
    fun waitingForUserIsASeparateSemanticStateAndStopsFeedback() {
        val waitingState = bottomSheetSource
            .substringAfter("fun setWaitingForConfirmationState()")
            .substringBefore("fun setStoppedState()")
        val listeningState = functionBody(
            "private fun updateListeningAccessibilityState()",
            "private fun showAssistantState("
        )
        val confirmation = functionBody("fun expectConfirmation()", "fun getBottomSheet()")

        assertTrue(waitingState.contains("AssistantAccessibilityState.WAITING_FOR_CONFIRMATION"))
        assertFalse(waitingState.contains("setProcessingState()"))
        assertTrue(listeningState.contains("AssistantAccessibilityState.WAITING_FOR_CONFIRMATION"))
        assertTrue(confirmation.contains("processingHapticFeedback.stop"))
    }

    @Test
    fun everyNonProcessingAssistantStateStopsTheSingleController() {
        val stateFunction = functionBody("private fun showAssistantState(", "private fun beginSessionGeneration")

        assertTrue(stateFunction.contains("processingHapticFeedback.start()"))
        assertTrue(stateFunction.contains("processingHapticFeedback.stop(state.name)"))
        listOf("READY", "LISTENING", "SPEAKING", "STOPPED", "ERROR").forEach { state ->
            assertTrue(stateFunction.contains("AssistantAccessibilityState.$state"))
        }
    }

    @Test
    fun lifecycleDismissalAndDestroyCancelPendingCallbacks() {
        assertTrue(sessionSource.contains("override fun onStop(owner: LifecycleOwner)"))
        assertTrue(sessionSource.contains("processingHapticFeedback.onLifecycleStopped()"))
        assertTrue(functionBody("fun dismissPanel()", "fun bindAssistantControl").contains("processingHapticFeedback.stop"))
        assertTrue(functionBody("fun destroy()", "private fun stopInternal").contains("processingHapticFeedback.destroy()"))
        assertTrue(sessionSource.contains("assistantBottomSheet?.performProcessingHapticPulse() == true"))
    }

    @Test
    fun settingUsesExistingPreferencesAndDefaultsOn() {
        assertTrue(settingsSource.contains("KEY_PROCESSING_HAPTIC_FEEDBACK"))
        assertTrue(settingsSource.contains("getBoolean(KEY_PROCESSING_HAPTIC_FEEDBACK, true)"))
        assertTrue(settingsSource.contains("putBoolean(KEY_PROCESSING_HAPTIC_FEEDBACK, processingHapticFeedback)"))
        assertTrue(settingsSource.contains("Processing haptic feedback, \${if (processingHapticFeedback) \"On\" else \"Off\"}"))
        assertTrue(settingsLayout.contains("android:id=\"@+id/cardProcessingHaptic\""))
        assertTrue(settingsLayout.contains("android:text=\"@string/processing_haptic_feedback\""))
    }

    @Test
    fun attachedPanelViewAndNativeHapticGuardAgainstInitializationCrashes() {
        val pulse = bottomSheetSource
            .substringAfter("fun performProcessingHapticPulse()")
            .substringBefore("private fun applyState")

        assertTrue(pulse.contains("!isContentReady"))
        assertTrue(pulse.contains("!assistantRoot.isAttachedToWindow"))
        assertTrue(pulse.contains("return assistantRoot.performProcessingHapticFeedback()"))
    }

    @Test
    fun processingPulseUsesProvenVirtualKeyEffectAndReturnsAndroidResult() {
        val helper = hapticSource
            .substringAfter("fun View.performProcessingHapticFeedback()")
            .substringBefore("inline fun View.setOnClickListenerWithHaptic")

        assertTrue(helper.contains(": Boolean"))
        assertTrue(helper.contains("if (!isAttachedToWindow) return false"))
        assertTrue(helper.contains("return performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)"))
        assertFalse(helper.contains("CLOCK_TICK"))
    }

    private fun callbackBody(start: String, end: String): String =
        sessionSource.substringAfter(start).substringBefore(end)

    private fun functionBody(start: String, end: String): String =
        sessionSource.substringAfter(start).substringBefore(end)
}
