package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateDraftMove
import com.example.myapplication.voice.CreateDraftMoveInterpreter
import com.example.myapplication.voice.CreateTaskDialogState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class CreateDraftSemanticOrchestratorTest {
    private class FakeClient(private val response: String) : CreateDraftSemanticClient {
        var calls = 0
        override suspend fun interpretCreateDraftMove(userText: String, contextSummary: String): String {
            calls++
            return response
        }
    }

    @Test
    fun recognisedLocalMoveDoesNotCallFallback() = runBlocking {
        val client = FakeClient("unused")
        val result = orchestrator(client).resolve("yes", saveState, context(saveState))
        assertEquals(CreateDraftMove.ConfirmSave, result.move)
        assertEquals(CreateDraftMoveSource.LOCAL, result.source)
        assertFalse(result.fallbackAttempted)
        assertEquals(0, client.calls)
    }

    @Test
    fun localUnknownCallsFallbackOnceAndMapsValidMove() = runBlocking {
        val client = FakeClient(
            """{"move":"CHANGE_FIELD","field":"TIME","value":"10 AM","confidence":0.97}"""
        )
        val result = orchestrator(client).resolve("move the time to 10 AM", saveState, context(saveState))
        assertEquals(CreateDraftMove.ChangeField(CreateDraftField.TIME, "10 AM"), result.move)
        assertEquals(CreateDraftMoveSource.CONVERSATION_AGENT_FALLBACK, result.source)
        assertTrue(result.fallbackAttempted)
        assertEquals(1, client.calls)
    }

    @Test
    fun malformedFallbackReturnsDeterministicUnknown() = runBlocking {
        val client = FakeClient("not json")
        val result = orchestrator(client).resolve("unclear words", saveState, context(saveState))
        assertEquals(CreateDraftMove.Unknown, result.move)
        assertEquals(CreateDraftMoveSource.DETERMINISTIC_FALLBACK, result.source)
        assertEquals(1, client.calls)
    }

    @Test
    fun lowConfidenceFallbackReturnsUnknown() = runBlocking {
        val client = FakeClient(
            """{"move":"CHANGE_FIELD","field":"TITLE","value":"revision","confidence":0.5}"""
        )
        val result = orchestrator(client).resolve("unclear title words", saveState, context(saveState))
        assertEquals(CreateDraftMove.Unknown, result.move)
        assertEquals(CreateDraftMoveSource.DETERMINISTIC_FALLBACK, result.source)
    }

    @Test
    fun stateValidationFailureReturnsUnknown() = runBlocking {
        val state = CreateTaskDialogState.WAITING_FOR_TIME
        val client = FakeClient(
            """{"move":"PROVIDE_FIELD","field":"TITLE","value":"revision","confidence":0.96}"""
        )
        val result = orchestrator(client).resolve(
            "unclear words",
            state,
            context(state),
            CreateDraftFallbackReason.TEMPORAL_UNRESOLVED
        )
        assertEquals(CreateDraftMove.Unknown, result.move)
        assertEquals(CreateDraftMoveSource.DETERMINISTIC_FALLBACK, result.source)
        assertEquals(1, client.calls)
    }

    @Test
    fun unresolvedLocalTemporalCandidateCanUseValidatedFallback() = runBlocking {
        val state = CreateTaskDialogState.WAITING_FOR_TIME
        val client = FakeClient(
            """{"move":"PROVIDE_FIELD","field":"TIME","value":"9 AM","confidence":0.98}"""
        )
        val result = orchestrator(client).resolve(
            "just 9 am",
            state,
            context(state),
            CreateDraftFallbackReason.TEMPORAL_UNRESOLVED
        )
        assertEquals(CreateDraftMove.ProvideField(CreateDraftField.TIME, "9 AM"), result.move)
        assertEquals(CreateDraftMoveSource.CONVERSATION_AGENT_FALLBACK, result.source)
        assertEquals(1, client.calls)
    }

    @Test
    fun coroutineCancellationIsPreserved() {
        val client = CreateDraftSemanticClient { _, _ -> throw CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) {
            runBlocking {
                orchestrator(client).resolve("unclear words", saveState, context(saveState))
            }
        }
    }

    private fun orchestrator(client: CreateDraftSemanticClient) = CreateDraftSemanticOrchestrator(
        localInterpreter = CreateDraftMoveInterpreter(),
        semanticClient = client
    )

    private fun context(state: CreateTaskDialogState) = CreateDraftAgentContext.capture(
        state = state,
        pendingReplacementField = null,
        hasTitle = true,
        hasSelectedDate = true,
        hasSelectedTime = state == saveState
    )

    private val saveState = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
}
