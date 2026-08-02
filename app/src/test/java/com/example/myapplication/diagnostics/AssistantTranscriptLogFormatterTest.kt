package com.example.myapplication.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantTranscriptLogFormatterTest {
    private val privateText = "move my private appointment"

    @Test
    fun debugFormattingRetainsUserAndAssistantText() {
        val visibility = AssistantTranscriptLogPolicy.visibility(debugBuild = true)
        val user = AssistantTranscriptLogFormatter.user(privateText, "VOICE", visibility)
        val assistant = AssistantTranscriptLogFormatter.assistant(privateText, true, visibility)

        assertTrue(user.contains("text=$privateText"))
        assertTrue(assistant.contains("text=$privateText"))
        assertFalse(user.contains("content=REDACTED"))
    }

    @Test
    fun releaseFormattingRedactsUserAndAssistantText() {
        val visibility = AssistantTranscriptLogPolicy.visibility(debugBuild = false)
        val user = AssistantTranscriptLogFormatter.user(privateText, "TYPED", visibility)
        val assistant = AssistantTranscriptLogFormatter.assistant(privateText, false, visibility)

        listOf(user, assistant).forEach { diagnostic ->
            assertTrue(diagnostic.contains("content=REDACTED"))
            assertTrue(diagnostic.contains("characterCount=${privateText.length}"))
            assertFalse(diagnostic.contains(privateText))
        }
    }
}
