package com.example.myapplication.ai.conversation.querypresentation

import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.TaskQueryPresentation
import com.example.myapplication.ai.TaskQueryPresentationReconciler
import com.example.myapplication.ai.TaskQueryPresentationSource
import com.example.myapplication.ai.schema.AgentResponseSchemas
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryPresentationSemanticOrchestratorTest {
    @Test
    fun naturalExistenceAndCountRequestsResolveCountOnly() = runBlocking {
        val inputs = listOf(
            "is there any task on next week",
            "do i have any tasks next week",
            "anything scheduled tomorrow",
            "how many tasks do i have tomorrow",
            "got anything planned friday",
            "have i got tasks next week",
            "are there tasks on sunday"
        )
        val client = MeaningFixtureClient(
            inputs.associateWith { response("COUNT_ONLY") }
        )
        val orchestrator = QueryPresentationSemanticOrchestrator(client)

        inputs.forEach { input ->
            val result = orchestrator.resolveForValidatedIntent(
                normalizedUserText = input,
                validatedTaskAgentIntent = AiIntent.QUERY_TASK.name
            )
            assertEquals(input, TaskQueryPresentation.COUNT_ONLY, result.presentation)
            assertEquals(input, QueryPresentationSemanticSource.CONVERSATION_AGENT, result.source)
        }
        assertEquals(inputs, client.receivedInputs)
    }

    @Test
    fun listingAndDetailedRequestsRemainDistinct() = runBlocking {
        val expected = linkedMapOf(
            "what tasks do i have next week" to "OVERVIEW",
            "what do i have tomorrow" to "OVERVIEW",
            "show me next week's tasks" to "OVERVIEW",
            "list my tasks on friday" to "OVERVIEW",
            "read my schedule tomorrow" to "OVERVIEW",
            "which tasks are on sunday" to "OVERVIEW",
            "read the full details for tomorrow" to "DETAILS",
            "read all details of next week's tasks" to "DETAILS",
            "tell me the detailed information for my friday tasks" to "DETAILS"
        )
        val client = MeaningFixtureClient(
            expected.mapValues { (_, presentation) -> response(presentation) }
        )
        val orchestrator = QueryPresentationSemanticOrchestrator(client)

        expected.forEach { (input, presentation) ->
            val result = orchestrator.resolveForValidatedIntent(
                normalizedUserText = input,
                validatedTaskAgentIntent = AiIntent.QUERY_TASK.name
            )
            assertEquals(input, TaskQueryPresentation.valueOf(presentation), result.presentation)
        }
    }

    @Test
    fun invalidUnknownLowConfidenceAndClarificationResultsUseExistingFallback() = runBlocking {
        val responses = listOf(
            "not json",
            response("UNKNOWN"),
            response("COUNT_ONLY", confidence = 0.79),
            response("COUNT_ONLY", clarification = true),
            JSONObject()
                .put("presentation", "COUNT_ONLY")
                .put("confidence", 0.98)
                .put("need_clarification", false)
                .put("count", 3)
                .toString()
        )

        responses.forEach { raw ->
            val result = QueryPresentationSemanticOrchestrator(
                QueryPresentationSemanticClient { raw }
            ).resolveForValidatedIntent(
                normalizedUserText = "do i have anything tomorrow",
                validatedTaskAgentIntent = AiIntent.QUERY_TASK.name
            )
            assertEquals(TaskQueryPresentation.NONE, result.presentation)
            assertEquals(
                QueryPresentationSemanticSource.EXISTING_PRESENTATION_FALLBACK,
                result.source
            )
            assertTrue(result.agentAttempted)
        }

        val failed = QueryPresentationSemanticOrchestrator(
            QueryPresentationSemanticClient { throw IllegalStateException("offline") }
        ).resolveForValidatedIntent(
            normalizedUserText = "do i have anything tomorrow",
            validatedTaskAgentIntent = AiIntent.QUERY_TASK.name
        )
        assertEquals(TaskQueryPresentation.NONE, failed.presentation)
        assertEquals("REQUEST_OR_SCHEMA_FAILURE", failed.reason)
    }

    @Test
    fun lowConfidenceSemanticResultLeavesConversationHintAsEffectiveFallback() = runBlocking {
        val bounded = QueryPresentationSemanticOrchestrator(
            QueryPresentationSemanticClient {
                response("COUNT_ONLY", confidence = 0.79)
            }
        ).resolveForValidatedIntent(
            normalizedUserText = "do i have anything next week",
            validatedTaskAgentIntent = AiIntent.QUERY_TASK.name
        )
        val effective = TaskQueryPresentationReconciler.reconcile(
            taskAgentIntent = AiIntent.QUERY_TASK.name,
            conversationHint = TaskQueryPresentation.OVERVIEW,
            taskAgentValue = TaskQueryPresentation.DETAILS,
            boundedSemantic = bounded.presentation
        )

        assertEquals(TaskQueryPresentation.NONE, bounded.presentation)
        assertEquals(TaskQueryPresentation.OVERVIEW, effective.effective)
        assertEquals(TaskQueryPresentationSource.CONVERSATION_AGENT_HINT, effective.source)
    }

    @Test
    fun nonQueryIntentNeverInvokesSemanticClient() = runBlocking {
        var calls = 0
        val result = QueryPresentationSemanticOrchestrator(
            QueryPresentationSemanticClient {
                calls += 1
                response("COUNT_ONLY")
            }
        ).resolveForValidatedIntent(
            normalizedUserText = "change my task",
            validatedTaskAgentIntent = AiIntent.UPDATE_TASK.name
        )

        assertEquals(0, calls)
        assertEquals(TaskQueryPresentation.NONE, result.presentation)
        assertEquals(QueryPresentationSemanticSource.NOT_APPLICABLE, result.source)
        assertFalse(result.agentAttempted)
    }

    @Test
    fun strictSchemaContainsOnlyPresentationConfidenceAndClarification() {
        val schema = AgentResponseSchemas.queryPresentationSemanticResponseFormat()
            .getJSONObject("json_schema")
            .getJSONObject("schema")
        val properties = schema.getJSONObject("properties")
        val presentationValues = properties.getJSONObject("presentation")
            .getJSONArray("enum")

        assertEquals(
            setOf("presentation", "confidence", "need_clarification"),
            properties.keys().asSequence().toSet()
        )
        assertEquals(
            setOf("COUNT_ONLY", "OVERVIEW", "DETAILS", "UNKNOWN"),
            (0 until presentationValues.length()).map(presentationValues::getString).toSet()
        )
        assertFalse(schema.getBoolean("additionalProperties"))
        assertTrue(schema.getJSONArray("required").length() == 3)
    }

    private class MeaningFixtureClient(
        private val responses: Map<String, String>
    ) : QueryPresentationSemanticClient {
        val receivedInputs = mutableListOf<String>()

        override suspend fun classifyQueryPresentation(normalizedUserText: String): String {
            receivedInputs += normalizedUserText
            return checkNotNull(responses[normalizedUserText])
        }
    }

    private companion object {
        fun response(
            presentation: String,
            confidence: Double = 0.98,
            clarification: Boolean = false
        ): String = JSONObject()
            .put("presentation", presentation)
            .put("confidence", confidence)
            .put("need_clarification", clarification)
            .toString()
    }
}
