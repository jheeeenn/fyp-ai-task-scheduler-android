package com.example.myapplication.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssistantVoiceSessionTerminalDeliverySourceTest {
    private val voiceSource =
        File("src/main/java/com/example/myapplication/voice/AssistantVoiceSession.kt")
            .readText()
    private val homeSource =
        File("src/main/java/com/example/myapplication/HomeActivity.kt")
            .readText()

    @Test
    fun terminalSpeechDismissesOnlyFromTheFinalTtsCallbackAndStopsOnce() {
        val body = voiceSource
            .substringAfter("fun speakThenStop(text: String, dismissPanel: Boolean = true)")
            .substringBefore("fun endConversation(reply: String)")
        val beforeTtsCallback = body.substringBefore("voiceHelper.speak(text) {")
        val finalTtsCallback = body.substringAfter("voiceHelper.speak(text) {")

        assertTrue(beforeTtsCallback.contains("assistantSessionActive = false"))
        assertTrue(beforeTtsCallback.contains("isForceStopping = true"))
        assertFalse(beforeTtsCallback.contains("assistantBottomSheet?.dismiss()"))
        assertTrue(finalTtsCallback.contains("assistantBottomSheet?.dismiss()"))
        assertTrue(finalTtsCallback.contains("host.onAssistantSessionStopped()"))
        assertTrue(
            finalTtsCallback.indexOf("assistantBottomSheet?.dismiss()") <
                finalTtsCallback.indexOf("host.onAssistantSessionStopped()")
        )
        assertEquals(1, body.split("host.onAssistantSessionStopped()").size - 1)
        assertFalse(body.contains("startVoiceRecognition()"))
        assertFalse(body.contains("responseManager.stopListening()"))
        assertFalse(body.contains("host.onAssistantCancelled()"))
    }

    @Test
    fun successfulBreakdownUsesTerminalSpeechWithoutRecognizerRestartOrExtraStopMessage() {
        val save = homeSource
            .substringAfter("private fun savePendingBreakdown")
            .substringBefore("private fun handleBreakdownDraftFailure")
        val delivery = homeSource
            .substringAfter("private fun deliverObservationResponse")
            .substringBefore("private suspend fun speakObservation")

        assertTrue(save.contains("ExecutionOutcome.SUCCESS"))
        assertTrue(save.contains("listenAgain = false"))
        assertTrue(delivery.contains("assistantSession.speakThenStop("))
        assertTrue(delivery.contains("response.speech"))
        assertFalse(delivery.contains("startVoiceRecognition"))
        assertFalse(delivery.contains("responseManager.stopListening()"))
        assertFalse(delivery.contains("USER_CANCELLED"))
    }

    @Test
    fun routineSaveResultUsesTerminalSpeechWithoutUserCancellation() {
        val save = homeSource
            .substringAfter("private fun savePendingRoutine")
            .substringBefore("private fun speakRoutineTerminalResponse")
        val saveResult = save.substringAfter("if (assistantSession.assistantSessionActive)")
        val terminal = homeSource
            .substringAfter("private fun speakRoutineTerminalResponse")
            .substringBefore("private fun speakRoutineResponse")

        assertTrue(saveResult.contains("RoutineResponseKind.SAVE_RESULT"))
        assertTrue(saveResult.contains("speakRoutineTerminalResponse("))
        assertFalse(saveResult.contains("listenAgain = false"))
        assertTrue(terminal.contains("\"ROUTINE_RESPONSE_DEBUG\""))
        assertTrue(terminal.contains("assistantSession.speakThenStop(text, dismissPanel = true)"))
        assertFalse(terminal.contains("assistantSession.speak("))
        assertFalse(terminal.contains("startVoiceRecognition"))
        assertFalse(terminal.contains("USER_CANCELLED"))
        assertFalse(terminal.contains("responseManager.stopListening()"))
    }

    @Test
    fun terminalStopCallbackRetainsHostRequestAndContextCleanup() {
        val stopped = homeSource
            .substringAfter("override fun onAssistantSessionStopped()")
            .substringBefore("override fun onResume()")

        assertTrue(
            stopped.contains(
                "invalidateAssistantRequest(AssistantRequestInvalidationReason.SESSION_STOPPED)"
            )
        )
        assertTrue(stopped.contains("clearConversationSessionContext()"))
        assertTrue(stopped.contains("clearPendingTaskMatchState()"))
        assertTrue(stopped.contains("clearPendingDeleteState()"))
        assertTrue(stopped.contains("clearPendingBreakdownState()"))
    }

    @Test
    fun continuingAndNavigationDeliveryPathsRemainUnchanged() {
        val delivery = homeSource
            .substringAfter("private fun deliverObservationResponse")
            .substringBefore("private suspend fun speakObservation")

        assertTrue(
            delivery.contains(
                "assistantSession.speakThenListenAgain(response.speech)"
            )
        )
        assertTrue(
            delivery.contains(
                "assistantSession.speakThenRun(response.speech) { afterSpeech() }"
            )
        )
    }
}
