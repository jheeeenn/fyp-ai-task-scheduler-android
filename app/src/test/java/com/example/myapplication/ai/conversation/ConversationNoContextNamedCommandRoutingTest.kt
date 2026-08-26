package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.TaskQueryPresentation
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConversationNoContextNamedCommandRoutingTest {
    @Test
    fun blankNoContextNamedMutationsRepairToTaskCommandsWithOriginalText() = runBlocking {
        listOf(
            Triple(
                "reschedule the evaluation task to tomorrow at 3pm",
                "RESCHEDULE",
                "evaluation task"
            ),
            Triple("Update take medicine", "UPDATE", "take medicine"),
            Triple("Delete take medicine", "DELETE", "take medicine"),
            Triple("Mark take medicine complete", "MARK_DONE", "take medicine"),
            Triple("Reopen take medicine", "MARK_UNDONE", "take medicine"),
            Triple("Buy Milk is not complete yet", "MARK_UNDONE", "Buy Milk"),
            Triple("I haven't finished Buy Milk after all", "MARK_UNDONE", "Buy Milk")
        ).forEach { (utterance, action, expectedNamedTarget) ->
            val client = NoContextClient(
                primary = decision(
                    route = "CONTEXT_ACTION",
                    contextAction = action
                ),
                repaired = compactRepair(move = "TASK_COMMAND")
            )

            val result = ConversationOrchestrator(client, ConversationDecisionParser())
                .process(utterance, "Interaction: NONE")

            assertEquals(ConversationRoute.TASK_COMMAND, result.route)
            assertEquals(utterance, result.taskText)
            assertTrue(result.taskText.contains(expectedNamedTarget))
            assertEquals("", result.contextRef)
            assertEquals(ConversationContextDetail.NONE, result.contextDetail)
            assertEquals(ConversationContextAction.NONE, result.contextAction)
            assertEquals(ConversationSettingAction.NONE, result.settingAction)
            assertEquals(ConversationSettingTarget.NONE, result.settingTarget)
            assertEquals(ConversationQueryReadingMove.NONE, result.queryReadingMove)
            assertEquals(TaskQueryPresentation.NONE, result.queryPresentationHint)
            assertEquals(0.97, result.confidence, 0.0)
            assertTrue(result.listenAgain)
            assertEquals("conversation_agent_schema_repair", result.source)
            assertEquals(1, client.repairCalls)
            assertTrue(client.repairContext.contains("INVALID_CONTEXT_REF"))
            assertTrue(client.repairContext.contains("Supplied temporary refs: NONE"))
            assertTrue(client.repairContext.contains("Current validated focus ref: NONE"))
            assertTrue(
                client.repairContext.contains(
                    "Conversation repair request mode: NO_CONTEXT_MUTATION_REPAIR"
                )
            )
            assertTrue(client.repairContext.contains("$action"))
        }
    }

    @Test
    fun blankNoContextDeicticMutationsRepairToClarification() = runBlocking {
        listOf(
            "move it" to "RESCHEDULE",
            "delete this" to "DELETE",
            "It's not finished after all" to "MARK_UNDONE"
        ).forEach { (utterance, action) ->
            val client = NoContextClient(
                primary = decision(
                    route = "CONTEXT_ACTION",
                    contextAction = action
                ),
                repaired = compactRepair(
                    move = "ASK_CLARIFICATION",
                    reply = "Which task do you mean?"
                )
            )

            val result = ConversationOrchestrator(client, ConversationDecisionParser())
                .process(utterance, "Interaction: NONE")

            assertEquals(ConversationRoute.ASK_CLARIFICATION, result.route)
            assertEquals("", result.contextRef)
            assertEquals("", result.taskText)
            assertEquals("Which task do you mean?", result.reply)
            assertEquals(ConversationContextDetail.NONE, result.contextDetail)
            assertEquals(ConversationContextAction.NONE, result.contextAction)
            assertEquals(ConversationSettingAction.NONE, result.settingAction)
            assertEquals(ConversationSettingTarget.NONE, result.settingTarget)
            assertEquals(ConversationQueryReadingMove.NONE, result.queryReadingMove)
            assertEquals(TaskQueryPresentation.NONE, result.queryPresentationHint)
            assertTrue(result.listenAgain)
            assertEquals("conversation_agent_schema_repair", result.source)
            assertEquals(1, client.repairCalls)
            assertTrue(
                client.repairContext.contains(
                    "Conversation repair request mode: NO_CONTEXT_MUTATION_REPAIR"
                )
            )
            assertTrue(client.repairContext.contains("For an unresolved deictic request"))
            assertTrue(client.repairContext.contains("Never invent T1 or T2"))
        }
    }

    @Test
    fun nonblankUncorroboratedNoContextActionsRepairToTaskCommands() = runBlocking {
        listOf(
            Triple("Delete buy groceries", "DELETE", "T1"),
            Triple("Reschedule medical checkup", "RESCHEDULE", "T1"),
            Triple("Update buy groceries", "UPDATE", "T1")
        ).forEach { (utterance, action, invalidRef) ->
            val client = NoContextClient(
                primary = decision(
                    route = "CONTEXT_ACTION",
                    contextRef = invalidRef,
                    contextAction = action
                ),
                repaired = compactRepair(move = "TASK_COMMAND")
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
    fun blankContextActionRefRemainsAllowedWithContextualAuthority() = runBlocking {
        val focus = ConversationContextFocus(
            available = true,
            ref = "T1",
            generation = 4,
            detail = ConversationContextDetail.SUMMARY,
            title = "private title"
        )
        val recentFocusClient = NoContextClient(
            primary = decision(
                route = "CONTEXT_ACTION",
                contextAction = "RESCHEDULE"
            )
        )
        val recentFocused = ConversationOrchestrator(
            recentFocusClient,
            ConversationDecisionParser()
        ).process(
            normalizedText = "reschedule it to today 4 p.m.",
            appContextSummary = "Interaction: AFTER_TASK_DETAILS",
            readOnlyTaskContextSnapshot = context("T1"),
            contextFocus = focus
        )
        assertEquals(ConversationRoute.CONTEXT_ACTION, recentFocused.route)
        assertEquals("", recentFocused.contextRef)
        assertEquals(0, recentFocusClient.repairCalls)

        val taskDetailClient = NoContextClient(
            primary = decision(
                route = "CONTEXT_ACTION",
                contextAction = "UPDATE"
            )
        )
        val taskDetail = ConversationOrchestrator(taskDetailClient, ConversationDecisionParser())
            .process(
                normalizedText = "change it to tomorrow",
                appContextSummary = "Interaction: AFTER_TASK_DETAILS",
                readOnlyTaskContextSnapshot = contextWithScope("TASK_DETAIL", "T1"),
                contextFocus = focus
            )
        assertEquals(ConversationRoute.CONTEXT_ACTION, taskDetail.route)
        assertEquals("", taskDetail.contextRef)
        assertEquals(0, taskDetailClient.repairCalls)

        val suppliedItemsClient = NoContextClient(
            primary = decision(
                route = "CONTEXT_ACTION",
                contextAction = "DELETE"
            )
        )
        val suppliedItems = ConversationOrchestrator(
            suppliedItemsClient,
            ConversationDecisionParser()
        ).process(
            normalizedText = "delete the second task",
            appContextSummary = "Interaction: QUERY_PAGE",
            readOnlyTaskContextSnapshot = context("T1", "T2")
        )
        assertEquals(ConversationRoute.CONTEXT_ACTION, suppliedItems.route)
        assertEquals("", suppliedItems.contextRef)
        assertEquals(0, suppliedItemsClient.repairCalls)
    }

    @Test
    fun nonblankRefConflictingWithSuppliedAndFocusedAuthorityStillRepairs() = runBlocking {
        val focus = ConversationContextFocus(
            available = true,
            ref = "T1",
            generation = 4,
            detail = ConversationContextDetail.SUMMARY,
            title = "private title"
        )
        val client = NoContextClient(
            primary = decision(
                route = "CONTEXT_ACTION",
                contextRef = "T2",
                contextAction = "UPDATE"
            ),
            repaired = decision(route = "TASK_COMMAND")
        )

        val result = ConversationOrchestrator(client, ConversationDecisionParser()).process(
            normalizedText = "change the title to leaving home",
            appContextSummary = "Interaction: QUERY_PAGE",
            readOnlyTaskContextSnapshot = context("T1"),
            contextFocus = focus
        )

        assertEquals(ConversationRoute.TASK_COMMAND, result.route)
        assertEquals(1, client.repairCalls)
        assertTrue(client.repairContext.contains("INVALID_CONTEXT_REF"))
    }

    @Test
    fun schemaRepairMayAlsoDeferBlankContextActionRefToAndroid() = runBlocking {
        val client = NoContextClient(
            primary = decision(
                route = "CONTEXT_ACTION",
                contextRef = "T3",
                contextAction = "DELETE"
            ),
            repaired = decision(
                route = "CONTEXT_ACTION",
                contextAction = "DELETE"
            )
        )

        val result = ConversationOrchestrator(client, ConversationDecisionParser()).process(
            normalizedText = "delete the second one",
            appContextSummary = "Interaction: QUERY_PAGE",
            readOnlyTaskContextSnapshot = context("T1", "T2")
        )

        assertEquals(ConversationRoute.CONTEXT_ACTION, result.route)
        assertEquals("", result.contextRef)
        assertEquals(ConversationContextAction.DELETE, result.contextAction)
        assertEquals("conversation_agent_schema_repair", result.source)
        assertEquals(1, client.repairCalls)
        assertFalse(
            client.repairContext.contains(
                "Conversation repair request mode: NO_CONTEXT_MUTATION_REPAIR"
            )
        )
    }

    @Test
    fun restrictedRepairFailsClosedIfClientViolatesAllowedRoutes() {
        val client = NoContextClient(
            primary = decision(
                route = "CONTEXT_ACTION",
                contextAction = "DELETE"
            ),
            repaired = JSONObject()
                .put("move", "TASK_COMMAND")
                .put("reply", "")
                .put("confidence", 0.97)
                .put("context_ref", "T1")
                .toString()
        )

        val failure = runCatching {
            runBlocking {
                ConversationOrchestrator(client, ConversationDecisionParser())
                    .process("delete this", "Interaction: NONE")
            }
        }.exceptionOrNull()

        assertTrue(failure is ConversationOrchestratorException)
        assertTrue(failure?.cause is ConversationSchemaException)
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
        assertTrue(authorityCheck.contains("DEFERRED_TO_ANDROID_GROUNDING"))
        assertTrue(authorityCheck.contains("suppliedRefs.isEmpty() && focusRef == null"))
        listOf("UPDATE", "RESCHEDULE", "DELETE", "MARK_DONE", "MARK_UNDONE").forEach {
            assertTrue(clientRepairRule().contains(it))
        }
        assertFalse(authorityCheck.contains("TaskMatcher"))
        assertFalse(authorityCheck.contains("taskTitle"))
        assertFalse(authorityCheck.contains("normalizedText"))
    }

    private fun clientRepairRule(): String = File(
        "src/main/java/com/example/myapplication/ai/conversation/ConversationAgentClient.kt"
    ).readText()
        .substringAfter("INVALID_CONTEXT_REF CONTEXT_ACTION repair rule:")
        .substringBefore("For an unresolved deictic request")

    private class NoContextClient(
        private val primary: String,
        private val repaired: String = compactRepair(
            move = "ASK_CLARIFICATION",
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
        fun context(vararg refs: String): String =
            contextWithScope("RECENT_QUERY_RESULTS", *refs)

        fun contextWithScope(scope: String, vararg refs: String): String = buildString {
            appendLine("Scope: $scope")
            appendLine("Generation: 4")
            appendLine("Items:")
            refs.forEach { appendLine("{\"ref\":\"$it\"}") }
        }.trim()

        fun decision(
            route: String,
            taskText: String = "",
            reply: String = "",
            contextRef: String = "",
            contextAction: String = "NONE"
        ): String = JSONObject()
            .put("route", route)
            .put("task_text", taskText)
            .put("reply", reply)
            .put("context_ref", contextRef)
            .put("context_detail", "NONE")
            .put("context_action", contextAction)
            .put("setting_action", "NONE")
            .put("setting_target", "NONE")
            .put("query_reading_move", "NONE")
            .put("query_presentation_hint", "NONE")
            .put("confidence", 0.97)
            .put("listen_again", true)
            .toString()

        fun compactRepair(
            move: String,
            reply: String = "",
            confidence: Double = 0.97
        ): String = JSONObject()
            .put("move", move)
            .put("reply", reply)
            .put("confidence", confidence)
            .toString()
    }
}
