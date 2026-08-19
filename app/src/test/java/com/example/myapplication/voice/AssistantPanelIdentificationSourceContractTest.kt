package com.example.myapplication.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssistantPanelIdentificationSourceContractTest {
    private val mainRoot = File("src/main/java/com/example/myapplication")
    private val session = mainRoot.resolve("voice/AssistantVoiceSession.kt").readText()
    private val bottomSheet = mainRoot.resolve("AssistantBottomSheet.kt").readText()
    private val voiceHelper = mainRoot.resolve("VoiceHelper.kt").readText()

    @Test
    fun bottomSheetIdentificationIsOwnedByTheVoiceSession() {
        assertTrue(session.contains("speakIdentification = ::speakPanelIdentification"))
        assertFalse(session.contains("speakIdentification = voiceHelper::speak"))
        assertTrue(session.contains("private fun speakPanelIdentification(text: String)"))
    }

    @Test
    fun activeRecognitionIsStoppedBeforeIdentificationTts() {
        val body = panelIdentificationBody()

        assertTrue(body.contains("recognitionActiveOrPending"))
        assertTrue(body.contains("PAUSE_RECOGNITION_AND_SPEAK"))
        assertOrdered(body, "stopListeningBeforeSpeak()", "voiceHelper.speak(identification)")
    }

    @Test
    fun identificationCallbackUsesGenerationSessionTerminalAndLifecycleGuards() {
        val body = panelIdentificationBody()
        val callback = body.substringAfter("voiceHelper.speak(identification) {")

        assertTrue(callback.contains("callbackGeneration != sessionGeneration"))
        assertTrue(callback.contains("canRestartRecognition("))
        assertTrue(callback.contains("sessionActive = assistantSessionActive"))
        assertTrue(callback.contains("forceStopping = isForceStopping"))
        assertTrue(callback.contains("terminalDeliveryActive = terminalDeliveryActive"))
        assertTrue(callback.contains("lifecycleEligible = isPanelSpeechLifecycleEligible()"))
        assertTrue(callback.contains("postRecognitionRestart(PANEL_IDENTIFICATION_RESTART_DELAY_MS)"))
    }

    @Test
    fun panelIdentificationIsUiSpeechOnlyAndCannotReplaceAuthoritativeSpeech() {
        val body = panelIdentificationBody()
        val policy = mainRoot.resolve("voice/AssistantPanelIdentificationPolicy.kt").readText()

        assertFalse(body.contains("host.onAssistantFinalText"))
        assertFalse(body.contains("logUserTranscript"))
        assertFalse(body.contains("logAssistantTranscript"))
        assertFalse(body.contains("showAssistantReply"))
        assertTrue(policy.contains("assistantState == AssistantAccessibilityState.SPEAKING"))
        assertTrue(policy.contains("assistantState == AssistantAccessibilityState.PROCESSING"))
        assertTrue(voiceHelper.contains("utteranceId != this@VoiceHelper.currentUtteranceId"))
    }

    @Test
    fun bottomSheetKeepsActionsButDynamicInformationAndPanelBackgroundAreQuiet() {
        assertTrue(bottomSheet.countOccurrences("VoiceFirstGestureBinder.bindAction") >= 3)
        assertFalse(bottomSheet.contains("VoiceFirstGestureBinder.bindInformation"))
        assertFalse(bottomSheet.contains("AssistantPanelControlSpeechRenderer::panel"))
        assertTrue(bottomSheet.contains("view = assistantRoot"))
        assertTrue(bottomSheet.contains("speechProvider = { null }"))
        assertTrue(bottomSheet.contains("activate = { onDoubleTapCancel?.invoke() }"))
        assertTrue(bottomSheet.contains("activate = { onTypedInputRequested?.invoke() }"))
        listOf("stateContainer", "tvUserSpeech", "tvAssistantReply", "tvAssistantHint")
            .forEach { informationView ->
                assertFalse(
                    Regex("VoiceFirstGestureBinder\\.bindInformation\\([\\s\\S]{0,200}$informationView")
                        .containsMatchIn(bottomSheet)
                )
            }
        val binder = mainRoot.resolve("VoiceFirstGestureBinder.kt").readText()
        assertTrue(binder.contains("view.setOnClickListener { activate() }"))
        assertTrue(binder.contains("view.performClick()"))
    }

    private fun panelIdentificationBody(): String = session
        .substringAfter("private fun speakPanelIdentification(text: String)")
        .substringBefore("private fun isPanelSpeechLifecycleEligible()")

    private fun assertOrdered(source: String, first: String, second: String) {
        val firstIndex = source.indexOf(first)
        val secondIndex = source.indexOf(second)
        assertTrue("Missing $first", firstIndex >= 0)
        assertTrue("Missing $second", secondIndex >= 0)
        assertTrue("Expected $first before $second", firstIndex < secondIndex)
    }

    private fun String.countOccurrences(value: String): Int =
        windowed(value.length).count { it == value }
}
