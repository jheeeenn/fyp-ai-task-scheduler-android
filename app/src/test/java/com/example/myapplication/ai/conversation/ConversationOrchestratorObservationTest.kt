package com.example.myapplication.ai.conversation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ConversationOrchestratorObservationTest {
    private class FakeClient(private val result: Result<String>) : ConversationAgentClient(null) {
        var calls = 0
        var capturedPlanJson = ""
        var capturedMemory = "not-called"
        var capturedAppContext = "not-called"

        override suspend fun respondToObservation(
            observationJson: String,
            memorySnapshot: String,
            appContextSummary: String
        ): String {
            calls += 1
            capturedPlanJson = observationJson
            capturedMemory = memorySnapshot
            capturedAppContext = appContextSummary
            return result.getOrThrow()
        }
    }

    private val confirmation = ExecutionObservation(
        operation = ExecutionOperation.DELETE_TASK,
        outcome = ExecutionOutcome.NEEDS_CONFIRMATION,
        taskTitle = "buy groceries",
        choices = listOf("buy groceries"),
        requiredInput = RequiredInput.CONFIRMATION,
        allowedUserMoves = listOf(
            AllowedUserMove.CONFIRM,
            AllowedUserMove.REJECT,
            AllowedUserMove.CANCEL
        ),
        listenAgain = true,
        fallbackSpeech = "Delete buy groceries? Please say yes or no.",
        fallbackHint = "Say yes or no."
    )

    @Test
    fun successfulDeleteUsesNaturalTemplateAndAndroidProtectedTitle() = runBlocking {
        val success = confirmation.copy(
            outcome = ExecutionOutcome.SUCCESS,
            requiredInput = RequiredInput.NONE,
            allowedUserMoves = emptyList(),
            listenAgain = false,
            fallbackSpeech = "buy groceries was deleted.",
            fallbackHint = ""
        )
        val client = FakeClient(
            Result.success(
                verbalization("All set — I've {authoritative_action} {task_title}.")
            )
        )

        val response = orchestrator(client).respondToObservation(
            success,
            tone = ResponseVerbalizationTone.FRIENDLY
        )

        assertEquals("All set — I've deleted buy groceries.", response.speech)
        assertEquals("conversation_agent_verbalization", response.source)
        assertEquals(ConversationResponseType.SUCCESS, response.responseType)
        assertEquals("buy groceries", success.taskTitle)
        assertEquals(ExecutionOutcome.SUCCESS, success.outcome)
        assertFalse(client.capturedPlanJson.contains("buy groceries"))
        assertFalse(client.capturedPlanJson.contains("was deleted"))
        assertFalse(client.capturedPlanJson.contains("id", ignoreCase = true))
        val safePlan = JSONObject(client.capturedPlanJson)
        assertEquals("TASK_ACTION_RESULT", safePlan.getString("verbalization_contract"))
        assertTrue(safePlan.getJSONArray("required_placeholders").toString().contains("task_title"))
        assertTrue(
            safePlan.getJSONArray("required_placeholders").toString()
                .contains("authoritative_action")
        )
        assertEquals("", client.capturedMemory)
        assertEquals("", client.capturedAppContext)
        assertEquals(1, client.calls)
    }

    @Test
    fun confirmationStateAndListenAgainStayAndroidOwned() = runBlocking {
        val response = orchestrator(
            FakeClient(Result.success(verbalization("Sure — {authoritative_message}")))
        ).respondToObservation(confirmation)

        assertEquals(
            "Sure — Delete buy groceries? Please say yes or no.",
            response.speech
        )
        assertEquals(ConversationResponseType.REQUEST_CONFIRMATION, response.responseType)
        assertEquals(RequiredInput.CONFIRMATION, confirmation.requiredInput)
        assertEquals(
            listOf(AllowedUserMove.CONFIRM, AllowedUserMove.REJECT, AllowedUserMove.CANCEL),
            confirmation.allowedUserMoves
        )
        assertTrue(confirmation.listenAgain)
        assertNotEquals(ExecutionOutcome.SUCCESS, confirmation.outcome)
    }

    @Test
    fun markDoneOutcomeCannotBeChangedByTemplate() = runBlocking {
        val observation = confirmation.copy(
            operation = ExecutionOperation.MARK_DONE,
            outcome = ExecutionOutcome.SUCCESS,
            requiredInput = RequiredInput.NONE,
            allowedUserMoves = emptyList(),
            listenAgain = false,
            fallbackSpeech = "buy groceries was marked as complete."
        )
        val response = orchestrator(
            FakeClient(
                Result.success(
                    verbalization("Done — {task_title} is now {authoritative_action}.")
                )
            )
        ).respondToObservation(observation)

        assertEquals(ConversationResponseType.SUCCESS, response.responseType)
        assertEquals(ExecutionOutcome.SUCCESS, observation.outcome)
        assertEquals("Done — buy groceries is now marked as complete.", response.speech)
    }

    @Test
    fun noResultsCannotInventMatchingTasks() = runBlocking {
        val noResults = confirmation.copy(
            operation = ExecutionOperation.QUERY_TASK,
            outcome = ExecutionOutcome.NO_RESULTS,
            taskTitle = "",
            taskCount = 0,
            tasks = emptyList(),
            requiredInput = RequiredInput.NONE,
            allowedUserMoves = listOf(AllowedUserMove.CANCEL),
            fallbackSpeech = "You have no tasks this month. Would you like to create a task?"
        )
        val response = orchestrator(
            FakeClient(
                Result.success(
                    verbalization("I found a matching task. {authoritative_message}")
                )
            )
        ).respondToObservation(noResults)

        assertEquals("android_deterministic", response.source)
        assertEquals(noResults.fallbackSpeech, response.speech)
        assertEquals(0, noResults.taskCount)
        assertTrue(noResults.tasks.isEmpty())
    }

    @Test
    fun malformedLowConfidenceUnknownAndMissingPlaceholderFallBack() = runBlocking {
        val cases = listOf(
            "not json",
            verbalization("Sure — {authoritative_message}", confidence = 0.40),
            verbalization("Sure — {task_title}"),
            verbalization("Sure.")
        )

        cases.forEach { raw ->
            val response = orchestrator(FakeClient(Result.success(raw)))
                .respondToObservation(confirmation)
            assertEquals("android_deterministic", response.source)
            assertEquals(confirmation.fallbackSpeech, response.speech)
            assertEquals(ConversationResponseType.REQUEST_CONFIRMATION, response.responseType)
        }
    }

    @Test
    fun timeoutAndUnavailableModelUseDeterministicResponseOnce() = runBlocking {
        listOf(
            IOException("LM Studio request timed out"),
            IOException("LM Studio unavailable")
        ).forEach { failure ->
            val client = FakeClient(Result.failure(failure))
            val response = orchestrator(client).respondToObservation(confirmation)

            assertEquals("android_deterministic", response.source)
            assertEquals(confirmation.fallbackSpeech, response.speech)
            assertEquals(1, client.calls)
        }
    }

    @Test
    fun queryPagesRemainOnExistingSafeStylePathWithoutGeneralCall() {
        val queryPage = confirmation.copy(
            operation = ExecutionOperation.QUERY_TASK,
            outcome = ExecutionOutcome.INFORMATION,
            taskTitle = "",
            taskCount = 2,
            tasks = listOf(ObservedTask("Alpha"), ObservedTask("Beta")),
            queryPage = TaskQueryPageObservation(
                totalTaskCount = 2,
                pageStartPosition = 1,
                pageEndPosition = 2,
                pageNumber = 1,
                pageCount = 1,
                pageSize = 5,
                hasNextPage = false,
                presentation = TaskQueryPresentationLevel.OVERVIEW,
                detailLevel = TaskQuerySpeechDetail.BRIEF,
                tone = TaskQuerySpeechTone.FRIENDLY,
                includeTaskDates = false,
                temporalLabel = ""
            ),
            fallbackSpeech = ""
        )

        assertEquals(
            null,
            ResponseVerbalizationPlanner.createOrNull(
                queryPage,
                ResponseVerbalizationTone.FRIENDLY,
                ResponseVerbalizationVerbosity.NORMAL
            )
        )
        val deterministic = AndroidObservationResponseRenderer.render(queryPage).speech
        assertTrue(deterministic.indexOf("Alpha") < deterministic.indexOf("Beta"))
        assertEquals(1, deterministic.windowed("Alpha".length).count { it == "Alpha" })
        assertEquals(1, deterministic.windowed("Beta".length).count { it == "Beta" })
    }

    @Test
    fun cancellationIsRethrown() {
        val client = FakeClient(Result.failure(CancellationException("cancelled")))
        assertThrows(CancellationException::class.java) {
            runBlocking { orchestrator(client).respondToObservation(confirmation) }
        }
    }

    @Test
    fun candidateIsNotRecordedBeforeDeliveryGuardAcceptsIt() = runBlocking {
        val memory = ConversationSessionMemory()
        val client = FakeClient(
            Result.success(verbalization("Sure — {authoritative_message}"))
        )
        val orchestrator = ConversationOrchestrator(
            client,
            ConversationDecisionParser(),
            ConversationResponseParser(),
            memory
        )

        val response = orchestrator.respondToObservation(confirmation)

        assertFalse(memory.snapshotForPrompt().contains(response.speech))
        orchestrator.recordDeliveredObservationResponse(confirmation, response)
        assertEquals(response.speech, memory.finalSpokenResponse)
        assertFalse(memory.snapshotForPrompt().contains(response.speech))
        assertEquals(ExecutionOperation.DELETE_TASK, memory.latestExecutionOperation)
        assertEquals(ExecutionOutcome.NEEDS_CONFIRMATION, memory.latestExecutionOutcome)
    }

    @Test
    fun safePlanContainsControlClassificationsButNoProtectedFacts() {
        val plan = requireNotNull(
            ResponseVerbalizationPlanner.createOrNull(
                confirmation,
                ResponseVerbalizationTone.PROFESSIONAL,
                ResponseVerbalizationVerbosity.SHORT
            )
        )
        val json = JSONObject(plan.toSafeAgentJson())

        assertEquals("DELETE_TASK", json.getString("operation"))
        assertEquals("NEEDS_CONFIRMATION", json.getString("outcome"))
        assertEquals("PROFESSIONAL", json.getString("tone"))
        assertEquals("SHORT", json.getString("verbosity"))
        assertEquals("CONFIRMATION", json.getString("required_input"))
        assertEquals(
            "AUTHORITATIVE_MESSAGE",
            json.getString("verbalization_contract")
        )
        assertTrue(json.getJSONArray("allowed_user_moves").toString().contains("CONFIRM"))
        assertFalse(json.toString().contains("buy groceries"))
        assertFalse(json.toString().contains("yes or no"))
        assertFalse(json.has("task_id"))
    }

    private fun orchestrator(client: ConversationAgentClient) =
        ConversationOrchestrator(client, ConversationDecisionParser())

    private fun verbalization(template: String, confidence: Double = 0.96) =
        """{"use_verbalization":true,"speech_template":"$template","confidence":$confidence}"""
}
