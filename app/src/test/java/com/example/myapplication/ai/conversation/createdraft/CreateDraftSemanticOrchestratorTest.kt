package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateDraftMove
import com.example.myapplication.voice.CreateDraftMoveInterpreter
import com.example.myapplication.voice.CreateTaskDialogState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateDraftSemanticOrchestratorTest {
    private class FakeClient(private val response: String) : CreateDraftSemanticClient {
        var calls = 0
        var lastContext = ""

        override suspend fun interpretCreateDraftMove(userText: String, contextSummary: String): String {
            calls++
            lastContext = contextSummary
            return response
        }
    }

    @Test
    fun recognisedLocalTitleChangeStillCallsAgentAndCandidateIsAdvisoryOnly() = runBlocking {
        val client = FakeClient(
            """{"move":"CHANGE_FIELD","field":"TITLE","value":"revision","confidence":0.97}"""
        )
        val result = resolve(client, "change the title to revision", saveState)

        assertEquals(CreateDraftMove.ChangeField(CreateDraftField.TITLE, "revision"), result.move)
        assertEquals(CreateDraftMoveSource.CONVERSATION_AGENT_PRIMARY, result.source)
        assertTrue(result.agentAttempted)
        assertEquals(1, client.calls)
        assertTrue(client.lastContext.contains("Advisory local candidate move: CHANGE_FIELD"))
        assertTrue(client.lastContext.contains("Advisory local candidate field: TITLE"))
        assertTrue(client.lastContext.contains("Advisory local candidate has a value: true"))
        assertFalse(client.lastContext.contains("revision"))
    }

    @Test
    fun validAgentDecisionIsPrimaryAndMayCorrectRecognisedCandidate() = runBlocking {
        val client = FakeClient(
            """{"move":"CHANGE_FIELD","field":"TIME","value":"10 AM","confidence":0.96}"""
        )
        val result = resolve(client, "change the title to revision", saveState)

        assertEquals(CreateDraftMove.ChangeField(CreateDraftField.TIME, "10 AM"), result.move)
        assertEquals(CreateDraftMoveSource.CONVERSATION_AGENT_PRIMARY, result.source)
        assertEquals(1, client.calls)
    }

    @Test
    fun agentMayCorrectLocalUnknown() = runBlocking {
        val client = FakeClient(
            """{"move":"CHANGE_FIELD","field":"TIME","value":"10 AM","confidence":0.97}"""
        )
        val result = resolve(client, "move the time to 10 am", saveState)

        assertEquals(CreateDraftMove.ChangeField(CreateDraftField.TIME, "10 AM"), result.move)
        assertEquals(CreateDraftMoveSource.CONVERSATION_AGENT_PRIMARY, result.source)
        assertTrue(client.lastContext.contains("Advisory local candidate recognised: false"))
    }

    @Test
    fun agentUnknownFallsBackToLocalConfirmSave() = runBlocking {
        val client = FakeClient(validUnknown())
        val result = resolve(client, "yes", saveState)

        assertEquals(CreateDraftMove.ConfirmSave, result.move)
        assertEquals(CreateDraftMoveSource.LOCAL_FAILURE_FALLBACK, result.source)
        assertFalse(result.source == CreateDraftMoveSource.CONVERSATION_AGENT_PRIMARY)
    }

    @Test
    fun agentUnknownFallsBackToLocalChangeField() = runBlocking {
        val client = FakeClient(validUnknown())
        val result = resolve(client, "change the title to revision", saveState)

        assertEquals(CreateDraftMove.ChangeField(CreateDraftField.TITLE, "revision"), result.move)
        assertEquals(CreateDraftMoveSource.LOCAL_FAILURE_FALLBACK, result.source)
    }

    @Test
    fun agentUnknownWithLocalUnknownIsDeterministicUnknown() = runBlocking {
        val client = FakeClient(validUnknown())
        val result = resolve(client, "unclear words", saveState)

        assertEquals(CreateDraftMove.Unknown, result.move)
        assertEquals(CreateDraftMoveSource.DETERMINISTIC_UNKNOWN, result.source)
        assertFalse(result.source == CreateDraftMoveSource.CONVERSATION_AGENT_PRIMARY)
    }

    @Test
    fun agentConfirmCannotContradictLocalRejection() = runBlocking {
        val client = FakeClient(validConfirm())
        val result = resolve(client, "no", saveState)

        assertEquals(CreateDraftMove.RejectSave, result.move)
        assertEquals(CreateDraftMoveSource.LOCAL_FAILURE_FALLBACK, result.source)
    }

    @Test
    fun agentConfirmCannotContradictLocalFieldChange() = runBlocking {
        val client = FakeClient(validConfirm())
        val result = resolve(client, "change the time to 10 am", saveState)

        assertEquals(CreateDraftMove.ChangeField(CreateDraftField.TIME, "10 am"), result.move)
        assertEquals(CreateDraftMoveSource.LOCAL_FAILURE_FALLBACK, result.source)
    }

    @Test
    fun agentConfirmMayResolveLocalUnknown() = runBlocking {
        val client = FakeClient(validConfirm())
        val result = resolve(client, "that looks right save it", saveState)

        assertEquals(CreateDraftMove.ConfirmSave, result.move)
        assertEquals(CreateDraftMoveSource.CONVERSATION_AGENT_PRIMARY, result.source)
    }

    @Test
    fun malformedAgentOutputUsesValidLocalFallback() = runBlocking {
        val client = FakeClient("not json")
        val result = resolve(client, "change the title to revision", saveState)

        assertEquals(CreateDraftMove.ChangeField(CreateDraftField.TITLE, "revision"), result.move)
        assertEquals(CreateDraftMoveSource.LOCAL_FAILURE_FALLBACK, result.source)
        assertTrue(result.agentAttempted)
    }

    @Test
    fun lowConfidenceAgentOutputUsesValidLocalFallback() = runBlocking {
        val client = FakeClient(
            """{"move":"CHANGE_FIELD","field":"TIME","value":"10 AM","confidence":0.50}"""
        )
        val result = resolve(client, "change the title to revision", saveState)

        assertEquals(CreateDraftMove.ChangeField(CreateDraftField.TITLE, "revision"), result.move)
        assertEquals(CreateDraftMoveSource.LOCAL_FAILURE_FALLBACK, result.source)
    }

    @Test
    fun malformedAgentOutputWithoutValidLocalCandidateBecomesUnknown() = runBlocking {
        val client = FakeClient("not json")
        val result = resolve(client, "unclear words", saveState)

        assertEquals(CreateDraftMove.Unknown, result.move)
        assertEquals(CreateDraftMoveSource.DETERMINISTIC_UNKNOWN, result.source)
    }

    @Test
    fun stateValidationRejectsAgentDecisionWhenLocalCandidateIsAlsoInvalid() = runBlocking {
        val state = CreateTaskDialogState.WAITING_FOR_TIME
        val client = FakeClient(
            """{"move":"PROVIDE_FIELD","field":"TITLE","value":"revision","confidence":0.96}"""
        )
        val result = resolve(client, "", state)

        assertEquals(CreateDraftMove.Unknown, result.move)
        assertEquals(CreateDraftMoveSource.DETERMINISTIC_UNKNOWN, result.source)
        assertEquals(1, client.calls)
    }

    @Test
    fun exactCancelUsesImmediateSafetyReflexWithoutAgent() = runBlocking {
        val client = FakeClient("unused")
        val result = resolve(client, "cancel", saveState)

        assertEquals(CreateDraftMove.Cancel, result.move)
        assertEquals(CreateDraftMoveSource.LOCAL_SAFETY_REFLEX, result.source)
        assertFalse(result.agentAttempted)
        assertEquals(0, client.calls)
    }

    @Test
    fun readyToSaveDoesNotCallAgent() = runBlocking {
        val client = FakeClient("unused")
        val result = resolve(client, "yes", CreateTaskDialogState.READY_TO_SAVE)

        assertEquals(CreateDraftMove.Unknown, result.move)
        assertEquals(CreateDraftMoveSource.DETERMINISTIC_UNKNOWN, result.source)
        assertEquals(0, client.calls)
    }

    @Test
    fun coroutineCancellationIsPreserved() {
        val client = CreateDraftSemanticClient { _, _ -> throw CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) {
            runBlocking {
                resolve(client, "unclear words", saveState)
            }
        }
    }

    private suspend fun resolve(
        client: CreateDraftSemanticClient,
        text: String,
        state: CreateTaskDialogState
    ): CreateDraftMoveResolution {
        val orchestrator = CreateDraftSemanticOrchestrator(
            localInterpreter = CreateDraftMoveInterpreter(),
            semanticClient = client
        )
        val candidate = orchestrator.proposeLocal(text, state)
        val context = context(state, candidate)
        return orchestrator.resolve(text, state, context, candidate)
    }

    private fun context(
        state: CreateTaskDialogState,
        candidate: CreateDraftMove
    ) = CreateDraftAgentContext.capture(
        state = state,
        pendingReplacementField = null,
        hasTitle = true,
        hasSelectedDate = true,
        hasSelectedTime = state == saveState,
        localCandidate = candidate
    )

    private fun validUnknown() =
        """{"move":"UNKNOWN","field":"","value":"","confidence":0.95}"""

    private fun validConfirm() =
        """{"move":"CONFIRM_SAVE","field":"","value":"","confidence":0.95}"""

    private val saveState = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
}
