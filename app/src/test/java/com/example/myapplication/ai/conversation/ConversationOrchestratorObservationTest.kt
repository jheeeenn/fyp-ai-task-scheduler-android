package com.example.myapplication.ai.conversation

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import kotlinx.coroutines.CancellationException

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
    @Test fun incompatibleResponseTypesReturnFallback() = kotlinx.coroutines.runBlocking {
        val notFound = observation.copy(outcome = ExecutionOutcome.NOT_FOUND, fallbackSpeech = "Not found.")
        val notFoundResponse = ConversationOrchestrator(
            FakeClient(Result.success("{\"speech\":\"Deleted.\",\"hint\":\"\",\"response_type\":\"SUCCESS\"}")),
            ConversationDecisionParser()
        ).respondToObservation(notFound, "ctx")
        assertEquals("deterministic_fallback", notFoundResponse.source)
        assertEquals(ConversationResponseType.INFORMATION, notFoundResponse.responseType)

        val confirmationResponse = ConversationOrchestrator(
            FakeClient(Result.success("{\"speech\":\"Done.\",\"hint\":\"\",\"response_type\":\"SUCCESS\"}")),
            ConversationDecisionParser()
        ).respondToObservation(observation, "ctx")
        assertEquals("deterministic_fallback", confirmationResponse.source)
        assertEquals(ConversationResponseType.REQUEST_CONFIRMATION, confirmationResponse.responseType)
    }

    @Test fun notFoundInformationIsAcceptedAndSuccessFallsBack() = kotlinx.coroutines.runBlocking {
        val notFound = observation.copy(
            outcome = ExecutionOutcome.NOT_FOUND,
            listenAgain = true,
            fallbackSpeech = "No matching task was found."
        )
        val accepted = ConversationOrchestrator(
            FakeClient(Result.success("{\"speech\":\"I could not find purple moon.\",\"hint\":\"\",\"response_type\":\"INFORMATION\"}")),
            ConversationDecisionParser()
        ).respondToObservation(notFound, "ctx")
        val rejected = ConversationOrchestrator(
            FakeClient(Result.success("{\"speech\":\"Deleted.\",\"hint\":\"\",\"response_type\":\"SUCCESS\"}")),
            ConversationDecisionParser()
        ).respondToObservation(notFound, "ctx")

        assertEquals("conversation_agent", accepted.source)
        assertEquals(ConversationResponseType.INFORMATION, accepted.responseType)
        assertEquals("deterministic_fallback", rejected.source)
        assertEquals(ConversationResponseType.INFORMATION, rejected.responseType)
        assertTrue(notFound.listenAgain)
    }

    @Test fun cancelledAcknowledgementIsAcceptedAndSessionEndFallsBack() = kotlinx.coroutines.runBlocking {
        val cancelled = observation.copy(
            outcome = ExecutionOutcome.CANCELLED,
            fallbackSpeech = "Okay, I will not delete it."
        )
        val accepted = ConversationOrchestrator(
            FakeClient(Result.success("{\"speech\":\"Okay, the deletion was cancelled.\",\"hint\":\"\",\"response_type\":\"ACKNOWLEDGEMENT\"}")),
            ConversationDecisionParser()
        ).respondToObservation(cancelled, "ctx")
        val rejected = ConversationOrchestrator(
            FakeClient(Result.success("{\"speech\":\"Goodbye.\",\"hint\":\"\",\"response_type\":\"SESSION_END\"}")),
            ConversationDecisionParser()
        ).respondToObservation(cancelled, "ctx")

        assertEquals("conversation_agent", accepted.source)
        assertEquals(ConversationResponseType.ACKNOWLEDGEMENT, accepted.responseType)
        assertEquals("deterministic_fallback", rejected.source)
        assertEquals(ConversationResponseType.ACKNOWLEDGEMENT, rejected.responseType)
        assertTrue(cancelled.listenAgain)
    }

    @Test fun coroutineCancellationIsRethrown() {
        val client = FakeClient(Result.failure(CancellationException("cancelled")))
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                ConversationOrchestrator(client, ConversationDecisionParser()).respondToObservation(observation, "ctx")
            }
        }
    }

    @Test fun authorityStateStaysAndroidOwned() {
        assertTrue(observation.listenAgain)
        assertEquals(RequiredInput.CONFIRMATION, observation.requiredInput)
        assertEquals(listOf("purple moon"), observation.choices)
        assertNotEquals(ConversationResponseType.SUCCESS, observation.outcome.toConversationResponseType())
    }
}
