package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateDraftMove
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
            hasSelectedTime = false,
            localCandidate = CreateDraftMove.ProvideField(CreateDraftField.TIME, "just 9 am")
        ).toPromptText()

        assertTrue(text.contains("waiting for an exact or interpretable time phrase"))
        assertTrue(text.contains("Expected field: TIME"))
        assertTrue(text.contains("Pending replacement field: TIME"))
        assertTrue(text.contains("Title exists: true"))
        assertTrue(text.contains("Advisory local candidate move: PROVIDE_FIELD"))
        assertTrue(text.contains("Advisory local candidate field: TIME"))
        assertTrue(text.contains("Advisory local candidate has a value: true"))
        assertTrue(text.contains("Advisory local candidate recognised: true"))
        assertTrue(text.contains("local candidate is advisory and not authoritative"))
        assertFalse(text.contains("just 9 am"))
        assertFalse(text.contains("taskId"))
        assertFalse(text.contains("endpoint"))
        assertFalse(text.contains("Room"))
    }

    @Test
    fun readyToSaveContextDisallowsSemanticAgentRequest() {
        val text = CreateDraftAgentContext.capture(
            state = CreateTaskDialogState.READY_TO_SAVE,
            pendingReplacementField = null,
            hasTitle = true,
            hasSelectedDate = true,
            hasSelectedTime = true,
            localCandidate = CreateDraftMove.Unknown
        ).toPromptText()
        assertTrue(text.contains("No create-draft semantic request is allowed"))
        assertTrue(text.contains("Allowed moves: UNKNOWN"))
    }
}
