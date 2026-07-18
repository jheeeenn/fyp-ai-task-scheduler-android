package com.example.myapplication.ai.conversation

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ConversationOrchestratorObservationTest {
    private class FakeClient(private val result: Result<String>) : ConversationAgentClient(null) {
        var calls = 0
        override suspend fun respondToObservation(observationJson: String, memorySnapshot: String, appContextSummary: String): String {
            calls++
            return result.getOrThrow()
        }
    }
    private val observation = ExecutionObservation(
        operation = ExecutionOperation.DELETE_TASK,
        outcome = ExecutionOutcome.NEEDS_CONFIRMATION,
        taskTitle = "purple moon",
        choices = listOf("purple moon"),
        requiredInput = RequiredInput.CONFIRMATION,
        allowedUserMoves = listOf(AllowedUserMove.CONFIRM, AllowedUserMove.REJECT),
        listenAgain = true,
        fallbackSpeech = "Are you sure?",
        fallbackHint = "Say yes or no."
    )

    @Test fun validAgentResponseReturned() = kotlinx.coroutines.runBlocking {
        val client = FakeClient(Result.success("{\"speech\":\"Should I delete purple moon?\",\"hint\":\"Say yes or no.\",\"response_type\":\"REQUEST_CONFIRMATION\"}"))
        val response = ConversationOrchestrator(client, ConversationDecisionParser()).respondToObservation(observation, "ctx")
        assertEquals("conversation_agent", response.source)
        assertEquals(1, client.calls)
    }
    @Test fun networkFailureReturnsFallbackOnce() = kotlinx.coroutines.runBlocking {
        val client = FakeClient(Result.failure(IOException("down")))
        val response = ConversationOrchestrator(client, ConversationDecisionParser()).respondToObservation(observation, "ctx")
        assertEquals("deterministic_fallback", response.source)
        assertEquals("Are you sure?", response.speech)
        assertEquals(ConversationResponseType.REQUEST_CONFIRMATION, response.responseType)
        assertEquals(1, client.calls)
    }
    @Test fun invalidSchemaAndBlankReturnFallback() = kotlinx.coroutines.runBlocking {
        val invalid = FakeClient(Result.success("{\"speech\":\"ok\",\"hint\":\"\",\"response_type\":\"SUCCESS\",\"extra\":true}"))
        assertEquals("deterministic_fallback", ConversationOrchestrator(invalid, ConversationDecisionParser()).respondToObservation(observation, "ctx").source)
        val blank = FakeClient(Result.success(""))
        assertEquals("deterministic_fallback", ConversationOrchestrator(blank, ConversationDecisionParser()).respondToObservation(observation, "ctx").source)
    }
    @Test fun authorityStateStaysAndroidOwned() {
        assertTrue(observation.listenAgain)
        assertEquals(RequiredInput.CONFIRMATION, observation.requiredInput)
        assertEquals(listOf("purple moon"), observation.choices)
        assertNotEquals(ConversationResponseType.SUCCESS, observation.outcome.toConversationResponseType())
    }
}
