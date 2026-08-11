package com.example.myapplication.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssistantVoiceSessionEndConversationSourceTest {
    private val sessionSource =
        File("src/main/java/com/example/myapplication/voice/AssistantVoiceSession.kt").readText()
    private val homeSource =
        File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()

    @Test
    fun endSessionHasOneClosingDeliveryAndDismissesAfterTts() {
        val body = functionBody("fun endConversation(reply: String)", "fun speakThenListenAgain")
        val beforeTts = body.substringBefore("voiceHelper.speak(closingReply) {")
        val afterTts = body.substringAfter("voiceHelper.speak(closingReply) {")

        assertTrue(beforeTts.contains("invalidateSessionCallbacks()"))
        assertTrue(beforeTts.contains("cancelRecognitionIfActive()"))
        assertTrue(beforeTts.contains("waitingForConfirmation = false"))
        assertTrue(beforeTts.contains("assistantSessionActive = false"))
        assertTrue(beforeTts.contains("terminalDeliveryActive = true"))
        assertFalse(beforeTts.contains("assistantBottomSheet?.dismiss()"))
        assertTrue(afterTts.contains("showAssistantState(AssistantAccessibilityState.STOPPED)"))
        assertTrue(afterTts.contains("assistantBottomSheet?.dismiss()"))
        assertTrue(afterTts.contains("host.onAssistantSessionStopped()"))
        assertEquals(1, body.split("voiceHelper.speak(closingReply)").size - 1)
        assertFalse(body.contains("host.onAssistantCancelled()"))
        assertFalse(body.contains("responseManager.stopListening()"))
        assertFalse(body.contains("startVoiceRecognition()"))
    }

    @Test
    fun blankEndSessionReplyUsesOneSafeClosingReplyEverywhere() {
        val body = functionBody("fun endConversation(reply: String)", "fun speakThenListenAgain")

        assertTrue(
            body.contains(
                "val closingReply = reply.trim().ifBlank { \"Okay, stopping the assistant.\" }"
            )
        )
        assertTrue(body.contains("showAssistantReply(closingReply)"))
        assertTrue(body.contains("logAssistantTranscript(closingReply, listenAgain = false)"))
        assertTrue(body.contains("voiceHelper.speak(closingReply)"))
        assertFalse(body.contains("voiceHelper.speak(reply)"))
    }

    @Test
    fun homeEndRouteUsesTerminalApiInsteadOfOrdinarySpeech() {
        val body = homeSource
            .substringAfter("ConversationRoute.END_SESSION -> {")
            .substringBefore("ConversationRoute.TASK_COMMAND ->")

        assertTrue(body.contains("AssistantRequestInvalidationReason.CONVERSATION_ENDED"))
        assertTrue(body.contains("assistantSession.endConversation(conversationDecision.reply)"))
        assertFalse(body.contains("assistantSession.speak("))
        assertFalse(body.contains("endAssistantConversation()"))
    }

    @Test
    fun cancelLifecycleAndConversationalEndRemainSeparate() {
        val end = functionBody("fun endConversation(reply: String)", "fun speakThenListenAgain")
        val cancel = functionBody("fun forceStop()", "fun dismissPanel()")
        val lifecycle = functionBody("fun stopForLifecycle()", "fun startSession")

        assertTrue(cancel.contains("host.onAssistantCancelled()"))
        assertTrue(cancel.contains("responseManager.stopListening()"))
        assertFalse(end.contains("host.onAssistantCancelled()"))
        assertFalse(lifecycle.contains("host.onAssistantCancelled()"))
        assertFalse(lifecycle.contains("host.onAssistantSessionStopped()"))
        assertFalse(lifecycle.contains("voiceHelper.speak"))
    }

    @Test
    fun delayedRestartsAreGenerationGuardedAndCleanupIsIdempotent() {
        val restart = functionBody("private fun postRecognitionRestart(", "private fun cancelRecognitionIfActive")
        val invalidate = functionBody("private fun invalidateSessionCallbacks()", "private fun postRecognitionRestart")
        val destroy = functionBody("fun destroy()", "private fun stopInternal")

        assertTrue(restart.contains("callbackGeneration != sessionGeneration"))
        assertTrue(restart.contains("!assistantSessionActive"))
        assertTrue(restart.contains("isForceStopping"))
        assertTrue(restart.contains("recognitionRequestActive"))
        assertTrue(invalidate.contains("removeCallbacks"))
        assertTrue(invalidate.contains("sessionGeneration += 1L"))
        assertTrue(destroy.contains("cancelRecognitionIfActive()"))
        assertTrue(destroy.contains("speechRecognizer = null"))
        assertTrue(sessionSource.contains("if (!recognitionRequestActive && !isListening) return"))
    }

    private fun functionBody(start: String, end: String): String =
        sessionSource.substringAfter(start).substringBefore(end)
}
