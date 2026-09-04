package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.conversation.taskcontext.ContextActionDecisionValidator
import com.example.myapplication.ai.conversation.taskcontext.ContextActionReferenceGroundingResult
import com.example.myapplication.ai.conversation.taskcontext.ContextActionReferenceGroundingValidator
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextItem
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextSnapshot
import com.example.myapplication.ai.conversation.taskcontext.TaskContextScope
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationContextActionAuthorityBindingTest {
    @Test
    fun primaryInvalidTemporaryRefIsBoundBeforeStrictAuthorityValidation() = runBlocking {
        val client = BindingClient(primary = contextActionDecision("T7"))

        val result = orchestrator(client).process(
            normalizedText = "move it to friday",
            appContextSummary = "AFTER_TASK_SUMMARY",
            readOnlyTaskContextSnapshot = prompt,
            contextFocus = focus,
            capturedTaskContextSnapshot = snapshot
        )

        assertEquals(ConversationRoute.CONTEXT_ACTION, result.route)
        assertEquals(ConversationContextAction.RESCHEDULE, result.contextAction)
        assertEquals("T1", result.contextRef)
        assertEquals(1, client.primaryCalls)
        assertEquals(0, client.repairCalls)
        assertEquals(
            ContextActionReferenceGroundingResult.VALID_CURRENT_FOCUS,
            ContextActionReferenceGroundingValidator.validate(
                normalizedText = "move it to friday",
                decision = result,
                capturedSnapshot = snapshot,
                currentFocus = focus
            ).result
        )
        assertEquals(
            true,
            ContextActionDecisionValidator.validate(
                decision = result,
                capturedSnapshot = snapshot,
                currentGeneration = snapshot.generation
            ).isValid
        )
    }

    @Test
    fun schemaRepairInvalidTemporaryRefUsesTheSameBindingBeforeValidation() = runBlocking {
        val client = BindingClient(
            primary = "not json",
            repaired = contextActionDecision("T7")
        )

        val result = orchestrator(client).process(
            normalizedText = "move it to friday",
            appContextSummary = "AFTER_TASK_SUMMARY",
            readOnlyTaskContextSnapshot = prompt,
            contextFocus = focus,
            capturedTaskContextSnapshot = snapshot
        )

        assertEquals(ConversationRoute.CONTEXT_ACTION, result.route)
        assertEquals(ConversationContextAction.RESCHEDULE, result.contextAction)
        assertEquals("T1", result.contextRef)
        assertEquals("conversation_agent_schema_repair", result.source)
        assertEquals(1, client.primaryCalls)
        assertEquals(1, client.repairCalls)
    }

    @Test
    fun alreadyAuthoritativePrimaryRefKeepsExistingSuccessfulPath() = runBlocking {
        val client = BindingClient(primary = contextActionDecision("T1"))

        val result = orchestrator(client).process(
            normalizedText = "move it to friday",
            appContextSummary = "AFTER_TASK_SUMMARY",
            readOnlyTaskContextSnapshot = prompt,
            contextFocus = focus,
            capturedTaskContextSnapshot = snapshot
        )

        assertEquals("T1", result.contextRef)
        assertEquals(1, client.primaryCalls)
        assertEquals(0, client.repairCalls)
    }

    @Test
    fun explicitUnsupportedRefIsNotSilentlyBoundAndStillFailsClosed() = runBlocking {
        val client = BindingClient(
            primary = contextActionDecision("T7"),
            repaired = contextActionDecision("T7")
        )

        val failure = runCatching {
            orchestrator(client).process(
                normalizedText = "move T2 to friday",
                appContextSummary = "AFTER_TASK_SUMMARY",
                readOnlyTaskContextSnapshot = prompt,
                contextFocus = focus,
                capturedTaskContextSnapshot = snapshot
            )
        }.exceptionOrNull()

        assertEquals(ConversationOrchestratorException::class.java, failure?.javaClass)
        val repairFailure = failure?.cause as? ConversationSchemaException
        assertEquals(
            ConversationDecisionFailureCode.INVALID_CONTEXT_REF,
            repairFailure?.decisionFailureCode
        )
        assertEquals(1, client.primaryCalls)
        assertEquals(1, client.repairCalls)
    }

    private fun orchestrator(client: BindingClient) =
        ConversationOrchestrator(client, ConversationDecisionParser())

    private class BindingClient(
        private val primary: String,
        private val repaired: String = contextActionDecision("T1")
    ) : ConversationAgentClient(null) {
        var primaryCalls = 0
        var repairCalls = 0

        override suspend fun process(
            userText: String,
            memorySnapshot: String,
            appContextSummary: String
        ): String {
            primaryCalls += 1
            return primary
        }

        override suspend fun processRepair(
            userText: String,
            appContextSummary: String,
            failureCode: String,
            failedRoute: ConversationRoute?
        ): String {
            repairCalls += 1
            return repaired
        }
    }

    private companion object {
        val snapshot = ReadOnlyTaskContextSnapshot(
            scope = TaskContextScope.RECENT_QUERY_RESULTS,
            generation = 4,
            items = listOf(
                ReadOnlyTaskContextItem(
                    ref = "T1",
                    title = "Dentist",
                    dueDate = "2026-09-05",
                    dueTime = "09:00",
                    isDone = false,
                    subtaskCount = 0,
                    unfinishedSubtaskCount = 0
                )
            ),
            truncated = false
        )
        val focus = ConversationContextFocus(
            available = true,
            ref = "T1",
            generation = snapshot.generation,
            detail = ConversationContextDetail.SUMMARY,
            title = "Dentist"
        )
        val prompt = buildString {
            appendLine("Scope: RECENT_QUERY_RESULTS")
            appendLine("Generation: 4")
            appendLine("Items:")
            append("{\"ref\":\"T1\",\"title\":\"Dentist\"}")
        }

        fun contextActionDecision(ref: String): String = JSONObject()
            .put("route", "CONTEXT_ACTION")
            .put("task_text", "")
            .put("reply", "")
            .put("context_ref", ref)
            .put("context_detail", "NONE")
            .put("context_action", "RESCHEDULE")
            .put("setting_action", "NONE")
            .put("setting_target", "NONE")
            .put("query_reading_move", "NONE")
            .put("navigation_target", "NONE")
            .put("query_presentation_hint", "NONE")
            .put("confidence", 0.97)
            .put("listen_again", true)
            .toString()
    }
}
