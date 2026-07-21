package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateTaskDialogState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateDraftAgentContextTest {
    @Test
    fun contextContainsOnlyTrustedDraftPresenceAndState() {
        val text = CreateDraftAgentContext.capture(
            state = CreateTaskDialogState.WAITING_FOR_TIME,
            pendingReplacementField = CreateDraftField.TIME,
            hasTitle = true,
            hasSelectedDate = true,
            hasSelectedTime = false
        ).toPromptText()

        assertTrue(text.contains("waiting for an exact or interpretable time phrase"))
        assertTrue(text.contains("Expected field: TIME"))
        assertTrue(text.contains("Pending replacement field: TIME"))
        assertTrue(text.contains("Title exists: true"))
        assertFalse(text.contains("taskId"))
        assertFalse(text.contains("endpoint"))
        assertFalse(text.contains("Room"))
    }

    @Test
    fun readyToSaveContextDisallowsSemanticFallback() {
        val text = CreateDraftAgentContext.capture(
            state = CreateTaskDialogState.READY_TO_SAVE,
            pendingReplacementField = null,
            hasTitle = true,
            hasSelectedDate = true,
            hasSelectedTime = true
        ).toPromptText()
        assertTrue(text.contains("No semantic fallback is allowed"))
        assertTrue(text.contains("Allowed moves: UNKNOWN"))
    }
}
