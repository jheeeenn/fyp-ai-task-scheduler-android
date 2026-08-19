package com.example.myapplication.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssistantAccessibilityFollowUpSourceTest {
    private val mainRoot = File("src/main/java/com/example/myapplication")
    private val session = mainRoot.resolve("voice/AssistantVoiceSession.kt").readText()
    private val dialog = mainRoot.resolve("accessibility/AccessibleAssistantInputDialog.kt").readText()
    private val focusHelper = mainRoot.resolve("accessibility/AccessibilityStateHelper.kt").readText()

    @Test
    fun dialogCancellationAndDismissShareOneShotRecovery() {
        assertTrue(dialog.contains("setNegativeButton(\"Cancel\") { _, _ -> cancellation.cancel() }"))
        assertTrue(dialog.contains("setOnCancelListener { cancellation.cancel() }"))
        assertTrue(dialog.contains("setOnDismissListener { cancellation.cancel() }"))
        assertTrue(dialog.contains("cancellation.markSubmitted()"))
        assertTrue(dialog.contains("if (submitted || delivered) return"))
    }

    @Test
    fun externalTypedCancellationRecoveryKeepsOneGuardedVoiceRestartPath() {
        val recovery = session
            .substringAfter("fun onTypedInputCancelled()")
            .substringBefore("fun startVoiceFlow()")

        assertFalse(session.contains("typedInputCancellationRecovery.onPanelTypedInputRequested"))
        assertTrue(session.contains("typedInputCancellationRecovery.onTypedInputSubmitted()"))
        assertTrue(recovery.contains("claimRecovery"))
        assertTrue(recovery.contains("postRecognitionRestart"))
        assertTrue(recovery.contains("useVoiceFlow = true"))
        val guardedRestart = session
            .substringAfter("private fun postRecognitionRestart(")
            .substringBefore("private fun cancelRecognitionIfActive")
        assertTrue(guardedRestart.contains("!assistantSessionActive"))
        assertTrue(guardedRestart.contains("isForceStopping"))
        assertTrue(guardedRestart.contains("isListening"))
        assertEquals(1, Regex("startVoiceFlow\\(\\)").findAll(guardedRestart).count())
    }

    @Test
    fun everyTypedDialogForwardsCancellationToTheExistingSession() {
        listOf("HomeActivity.kt", "CreateTaskActivity.kt", "EditTaskActivity.kt").forEach { file ->
            val source = mainRoot.resolve(file).readText()
            assertTrue(source.contains("onCancel = assistantSession::onTypedInputCancelled"))
        }
        val recovery = session
            .substringAfter("fun onTypedInputCancelled()")
            .substringBefore("fun startVoiceFlow()")
        assertFalse(recovery.contains("SpeechRecognizer.createSpeechRecognizer"))
    }

    @Test
    fun focusReturnChecksEligibilityAndUsesAccessibilityFocusAction() {
        assertTrue(focusHelper.contains("view.isAttachedToWindow"))
        assertTrue(focusHelper.contains("view.visibility == View.VISIBLE"))
        assertTrue(focusHelper.contains("view.isEnabled"))
        assertTrue(focusHelper.contains("isScreenReaderActive(target)"))
        assertTrue(focusHelper.contains("ViewCompat.performAccessibilityAction"))
        assertTrue(focusHelper.contains("AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS"))
        assertTrue(focusHelper.contains("if (!focused) target.requestFocus()"))
    }

    @Test
    fun accessibilityDiagnosticTagsNeverReceiveAnnouncementOrTranscriptContent() {
        val helper = mainRoot.resolve("accessibility/AccessibilityStateHelper.kt").readText()
        val transcriptLogger = mainRoot.resolve("diagnostics/AssistantTranscriptDiagnosticLogger.kt").readText()

        val announcementLog = helper.substringAfter("fun announce").substringBefore("fun logState")
        assertFalse(announcementLog.substringBefore("if (view.isAttachedToWindow)").contains("message}"))
        assertFalse(helper.contains("ASSISTANT_TRANSCRIPT"))
        assertFalse(transcriptLogger.contains("ACCESSIBILITY_STATE"))
        assertFalse(transcriptLogger.contains("ACCESSIBILITY_ANNOUNCEMENT"))
    }
}
