package com.example.myapplication.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssistantAccessibilityStateTest {
    @Test
    fun requiredAssistantStatesHaveStableNonSensitiveLabels() {
        val required = mapOf(
            AssistantAccessibilityState.READY to "Ready",
            AssistantAccessibilityState.LISTENING to "Listening",
            AssistantAccessibilityState.PROCESSING to "Processing",
            AssistantAccessibilityState.SPEAKING to "Speaking",
            AssistantAccessibilityState.STOPPED to "Stopped"
        )

        required.forEach { (state, label) ->
            assertEquals(label, AssistantAccessibilitySemantics.buttonStateDescription(state))
        }
        assertEquals("Waiting for confirmation", AssistantAccessibilityState.WAITING_FOR_CONFIRMATION.label)
        assertEquals("Error", AssistantAccessibilityState.ERROR.label)
    }

    @Test
    fun accessibilityDiagnosticsCannotContainTranscriptText() {
        val line = AccessibilityAnnouncementHelper.diagnosticLine(
            screen = "EDIT_TASK",
            event = "TIME_UPDATED"
        )
        val helper = File("src/main/java/com/example/myapplication/accessibility/AccessibilityStateHelper.kt").readText()
        val transcriptLogger =
            File("src/main/java/com/example/myapplication/diagnostics/AssistantTranscriptDiagnosticLogger.kt").readText()

        assertEquals("screen=EDIT_TASK event=TIME_UPDATED", line)
        assertFalse(helper.contains("transcript", ignoreCase = true))
        assertFalse(helper.contains("ASSISTANT_TRANSCRIPT"))
        assertFalse(transcriptLogger.contains("ACCESSIBILITY_STATE"))
        assertFalse(transcriptLogger.contains("ACCESSIBILITY_ANNOUNCEMENT"))
    }
}
