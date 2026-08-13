package com.example.myapplication.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssistantSessionEndHapticSourceTest {
    private val session =
        File("src/main/java/com/example/myapplication/voice/AssistantVoiceSession.kt").readText()
    private val feedback =
        File("src/main/java/com/example/myapplication/voice/AssistantSessionEndHapticFeedback.kt")
            .readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun fourTerminalTtsPathsDeliverOnceAfterStoppedState() {
        val terminalBodies = listOf(
            functionBody("fun speakThenStop(", "fun endConversation("),
            functionBody("fun endConversation(", "fun speakThenListenAgain("),
            functionBody("fun handleListenFailure(", "fun forceStop()"),
            functionBody("fun forceStop()", "fun dismissPanel()")
        )

        terminalBodies.forEach { body ->
            val callback = body.substringAfter("activity.runOnUiThread {")
            assertEquals(1, callback.split("sessionEndHapticFeedback.deliverOnce").size - 1)
            assertTrue(
                callback.indexOf("showAssistantState(AssistantAccessibilityState.STOPPED)") <
                    callback.indexOf("sessionEndHapticFeedback.deliverOnce(callbackGeneration)")
            )
            assertTrue(
                callback.indexOf("sessionEndHapticFeedback.deliverOnce(callbackGeneration)") <
                    callback.indexOf("assistantBottomSheet?.dismiss()")
            )
        }
    }

    @Test
    fun ordinarySpeechNavigationAndLifecycleNeverDeliverSessionEndHaptic() {
        val ordinarySpeak = functionBody("fun speak(text: String", "fun speakThenStop(")
        val navigation = functionBody("fun speakThenRun(", "private fun stopListeningBeforeSpeak")
        val lifecycle = functionBody("fun stopForLifecycle()", "fun startSession(")
        val destroy = functionBody("fun destroy()", "private fun stopInternal")

        listOf(ordinarySpeak, navigation, lifecycle, destroy).forEach { body ->
            assertFalse(body.contains("sessionEndHapticFeedback.deliverOnce"))
        }
    }

    @Test
    fun inactiveForceStopCleanupDoesNotDeliverTerminalHaptic() {
        val inactiveBranch = functionBody("fun forceStop()", "fun dismissPanel()")
            .substringAfter("if (!assistantSessionActive && !isListening) {")
            .substringBefore("assistantSessionActive = false")

        assertFalse(inactiveBranch.contains("sessionEndHapticFeedback.deliverOnce"))
    }

    @Test
    fun lifecycleOnlyCancelsAndDestroyOnlyDestroysTerminalFeedback() {
        val observer = session
            .substringAfter("private val lifecycleObserver")
            .substringBefore("init {")

        assertTrue(observer.contains("sessionEndHapticFeedback.cancelActive()"))
        assertTrue(observer.contains("sessionEndHapticFeedback.destroy()"))
        assertFalse(observer.contains("sessionEndHapticFeedback.deliverOnce"))
        assertTrue(session.contains("activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)"))
        assertTrue(session.contains("!activity.isFinishing"))
        assertTrue(session.contains("!activity.isDestroyed"))
    }

    @Test
    fun platformVibrationUsesAccessibilityAttributesAndCompatibleFallback() {
        assertTrue(feedback.contains("const val DURATION_MS = 1_000L"))
        assertTrue(feedback.contains("VibrationEffect.createOneShot"))
        assertTrue(feedback.contains("VibrationAttributes.USAGE_ACCESSIBILITY"))
        assertTrue(feedback.contains("Build.VERSION_CODES.TIRAMISU"))
        assertTrue(feedback.contains("Build.VERSION_CODES.O"))
        assertTrue(feedback.contains("target.vibrate(durationMs)"))
        assertFalse(feedback.contains("FLAG_BYPASS_INTERRUPTION_POLICY"))
        assertFalse(feedback.contains("FLAG_IGNORE_GLOBAL_SETTING"))
    }

    @Test
    fun manifestDeclaresNormalVibratePermission() {
        assertTrue(manifest.contains("android.permission.VIBRATE"))
    }

    private fun functionBody(start: String, end: String): String =
        session.substringAfter(start).substringBefore(end)
}
