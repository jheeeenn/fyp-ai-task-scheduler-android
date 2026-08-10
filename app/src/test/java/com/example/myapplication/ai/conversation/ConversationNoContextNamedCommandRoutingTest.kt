package com.example.myapplication.ai.conversation

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConversationNoContextNamedCommandRoutingTest {
    @Test
    fun namedOperationsRepairImpossibleNoContextActionsToTaskCommands() = runBlocking {
        listOf(
            Triple("Delete buy groceries", "DELETE", ""),
            Triple("Reschedule medical checkup", "RESCHEDULE", "T1"),
            Triple("Update buy groceries", "UPDATE", "")
        ).forEach { (utterance, action, invalidRef) ->
            val client = NoContextClient(
                primary = decision(
                    route = "CONTEXT_ACTION",
                    contextRef = invalidRef,
                    contextAction = action
                ),
                repaired = decision(route = "TASK_COMMAND")
            )

            val result = ConversationOrchestrator(client, ConversationDecisionParser())
                .process(utterance, "Interaction: NONE")

            assertEquals(ConversationRoute.TASK_COMMAND, result.route)
            assertEquals(utterance, result.taskText)
            assertEquals("", result.contextRef)
            assertEquals(ConversationContextAction.NONE, result.contextAction)
            assertEquals(1, client.repairCalls)
            assertTrue(client.repairContext.contains("INVALID_CONTEXT_REF"))
            assertTrue(client.repairContext.contains("Supplied temporary refs: NONE"))
            assertTrue(client.repairContext.contains("Current validated focus ref: NONE"))
            assertTrue(client.repairContext.contains("structurally impossible"))
            assertTrue(client.repairContext.contains("Never invent T1 or T2"))
        }
    }

    @Test
    fun unresolvedDeleteThisClarifiesWithoutInventingARef() = runBlocking {
        val client = NoContextClient(
            primary = decision(
                route = "CONTEXT_ACTION",
                contextAction = "DELETE"
            ),
            repaired = decision(
                route = "ASK_CLARIFICATION",
                reply = "Which task do you want to delete?"
            )
        )

        val result = ConversationOrchestrator(client, ConversationDecisionParser())
            .process("delete this", "Interaction: NONE")

        assertEquals(ConversationRoute.ASK_CLARIFICATION, result.route)
        assertEquals("", result.contextRef)
        assertEquals(ConversationContextAction.NONE, result.contextAction)
        assertFalse(result.reply.contains("T1", ignoreCase = true))
        assertEquals(1, client.repairCalls)
    }

    @Test
    fun groundedFocusAndOrdinalContextActionsRemainUnchanged() = runBlocking {
        val focusedContext = context("T1")
        val focus = ConversationContextFocus(
            available = true,
            ref = "T1",
            generation = 4,
            detail = ConversationContextDetail.SUMMARY,
            title = "private title"
        )
        val focusedClient = NoContextClient(
            primary = decision(
                route = "CONTEXT_ACTION",
                contextRef = "T1",
                contextAction = "DELETE"
            )
        )
        val focused = ConversationOrchestrator(focusedClient, ConversationDecisionParser())
            .process(
                normalizedText = "delete this task",
                appContextSummary = "Interaction: AFTER_TASK_DETAILS",
                readOnlyTaskContextSnapshot = focusedContext,
                contextFocus = focus
            )

        assertEquals(ConversationRoute.CONTEXT_ACTION, focused.route)
        assertEquals("T1", focused.contextRef)
        assertEquals(ConversationContextAction.DELETE, focused.contextAction)
        assertEquals(0, focusedClient.repairCalls)

        val ordinalClient = NoContextClient(
            primary = decision(
                route = "CONTEXT_ACTION",
                contextRef = "T2",
                contextAction = "DELETE"
            )
        )
        val ordinal = ConversationOrchestrator(ordinalClient, ConversationDecisionParser())
            .process(
                normalizedText = "delete the second one",
                appContextSummary = "Interaction: QUERY_PAGE",
                readOnlyTaskContextSnapshot = context("T1", "T2")
            )

        assertEquals(ConversationRoute.CONTEXT_ACTION, ordinal.route)
        assertEquals("T2", ordinal.contextRef)
        assertEquals(ConversationContextAction.DELETE, ordinal.contextAction)
        assertEquals(0, ordinalClient.repairCalls)
    }

    @Test
    fun promptsMakeNoContextPrecedenceAndRepairExplicit() {
        val routingPrompt = ConversationAgentClient.ROUTING_SYSTEM_PROMPT
        assertTrue(routingPrompt.contains("No-context named-operation precedence:"))
        assertTrue(routingPrompt.contains("CONTEXT_ACTION is structurally impossible"))
        assertTrue(routingPrompt.contains("\"Delete buy groceries\""))
        assertTrue(routingPrompt.contains("\"Reschedule medical checkup\""))
        assertTrue(routingPrompt.contains("\"Update buy groceries\""))
        assertTrue(routingPrompt.contains("Use ASK_CLARIFICATION with an empty context_ref"))
        assertTrue(routingPrompt.contains("never invent T1 or T2"))

        val source = File(
            "src/main/java/com/example/myapplication/ai/conversation/ConversationOrchestrator.kt"
        ).readText()
        val authorityCheck = source
            .substringAfter("private fun validateContextActionAuthority(")
            .substringBefore("private fun boundedRepairContext(")
        assertTrue(authorityCheck.contains("suppliedContextRefs"))
        assertTrue(authorityCheck.contains("contextFocus"))
        assertFalse(authorityCheck.contains("TaskMatcher"))
        assertFalse(authorityCheck.contains("taskTitle"))
        assertFalse(authorityCheck.contains("normalizedText"))
    }

    private class NoContextClient(
        private val primary: String,
        private val repaired: String = decision(
            route = "ASK_CLARIFICATION",
            reply = "Which task?"
        )
    ) : ConversationAgentClient(null) {
        var repairCalls = 0
        var repairContext = ""

        override suspend fun process(
            userText: String,
            memorySnapshot: String,
            appContextSummary: String
        ): String = primary

        override suspend fun processRepair(
            userText: String,
            appContextSummary: String
        ): String {
            repairCalls += 1
            repairContext = appContextSummary
            return repaired
        }
    }

    private companion object {
        fun context(vararg refs: String): String = buildString {
            appendLine("Scope: RECENT_QUERY_RESULTS")
            appendLine("Generation: 4")
            appendLine("Items:")
            refs.forEach { appendLine("{\"ref\":\"$it\"}") }
        }.trim()

        fun decision(
            route: String,
            reply: String = "",
            contextRef: String = "",
            contextAction: String = "NONE"
        ): String = JSONObject()
            .put("route", route)
            .put("task_text", "")
            .put("reply", reply)
            .put("context_ref", contextRef)
            .put("context_detail", "NONE")
            .put("context_action", contextAction)
            .put("query_reading_move", "NONE")
            .put("query_presentation_hint", "NONE")
            .put("confidence", 0.97)
            .put("listen_again", true)
            .toString()
    }
}
