package com.example.myapplication.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssistantBottomSheetInteractionSourceContractTest {
    private val mainRoot = File("src/main/java/com/example/myapplication")
    private val session = mainRoot.resolve("voice/AssistantVoiceSession.kt").readText()
    private val bottomSheet = mainRoot.resolve("AssistantBottomSheet.kt").readText()

    @Test
    fun visualStopAndTypeButtonsUseNativeSingleClickCallbacks() {
        val stopButton = bottomSheet
            .substringAfter("btnStopAssistant).setOnClickListener")
            .substringBefore("btnTypeAssistantInput).setOnClickListener")
        val typeButton = bottomSheet
            .substringAfter("btnTypeAssistantInput).setOnClickListener")
            .substringBefore("assistantRoot.contentDescription")

        assertTrue(stopButton.contains("onDoubleTapCancel?.invoke()"))
        assertTrue(typeButton.contains("onTypedInputRequested?.invoke()"))
        assertFalse(stopButton.contains("VoiceFirstGestureBinder.bindAction"))
        assertFalse(typeButton.contains("VoiceFirstGestureBinder.bindAction"))
    }

    @Test
    fun panelRootKeepsTheSharedQuietDoubleTapStopGesture() {
        val rootBinding = bottomSheet
            .substringAfter("assistantRoot.contentDescription")
            .substringBefore("setOnDismissListener")

        assertEquals(1, bottomSheet.countOccurrences("VoiceFirstGestureBinder.bindAction"))
        assertTrue(rootBinding.contains("VoiceFirstGestureBinder.bindAction"))
        assertTrue(rootBinding.contains("view = assistantRoot"))
        assertTrue(rootBinding.contains("speechProvider = { null }"))
        assertTrue(rootBinding.contains("speak = { _ -> }"))
        assertTrue(rootBinding.contains("activate = { onDoubleTapCancel?.invoke() }"))
    }

    @Test
    fun dynamicInformationAndEveryPanelTouchPathRemainSilent() {
        assertFalse(bottomSheet.contains("VoiceFirstGestureBinder.bindInformation"))
        assertFalse(bottomSheet.contains("speakIdentification"))
        assertFalse(bottomSheet.contains("VoiceHelper"))
        listOf("stateContainer", "tvUserSpeech", "tvAssistantReply", "tvAssistantHint")
            .forEach { informationView ->
                assertFalse(
                    Regex("VoiceFirstGestureBinder\\.bindInformation\\([\\s\\S]{0,200}$informationView")
                        .containsMatchIn(bottomSheet)
                )
            }
    }

    @Test
    fun obsoletePanelIdentificationSubsystemIsRemovedWithoutNormalVoiceSafeguards() {
        assertFalse(mainRoot.resolve("voice/AssistantPanelIdentificationPolicy.kt").exists())
        assertFalse(session.contains("speakPanelIdentification"))
        assertFalse(session.contains("panelIdentificationSpeechActive"))
        assertFalse(session.contains("PANEL_IDENTIFICATION_RESTART_DELAY_MS"))
        assertFalse(session.contains("AssistantPanelIdentification"))
        assertTrue(session.contains("private fun stopListeningBeforeSpeak()"))
        assertTrue(session.contains("private fun postRecognitionRestart("))
        assertTrue(session.contains("callbackGeneration != sessionGeneration"))
        assertTrue(session.contains("isForceStopping"))
        assertTrue(session.contains("terminalDeliveryActive"))
    }

    private fun String.countOccurrences(value: String): Int =
        windowed(value.length).count { it == value }
}
