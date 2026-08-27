package com.example.myapplication.ai.conversation

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class ConversationBreakdownRoutingGuardTest {
    @Test
    fun informationalBreakdownRequestAcceptsDirectReplyWithoutRepair() = runBlocking {
        val client = RoutingClient(
            primaryResponses = listOf(directReply(HELP_REPLY))
        )

        val decision = orchestrator(client).process(
            normalizedText = "how to break down a task",
            appContextSummary = "Interaction: NONE"
        )

        assertEquals(ConversationRoute.DIRECT_REPLY, decision.route)
        assertEquals(0, client.repairCalls)
    }

    @Test
    fun explicitBreakdownRejectsStaleDirectReplyAndRepairsToTaskCommand() = runBlocking {
        val utterance = "breakdown evaluation parent task"
        val client = RoutingClient(
            primaryResponses = listOf(directReply(HELP_REPLY)),
            repairedResponse = taskCommand()
        )

        val decision = orchestrator(client).process(utterance, "Interaction: NONE")

        assertEquals(ConversationRoute.TASK_COMMAND, decision.route)
        assertEquals(utterance, decision.taskText)
        assertEquals("conversation_agent_schema_repair", decision.source)
        assertEquals(1, client.repairCalls)
        assertEquals(
            ConversationDecisionFailureCode.OPERATIONAL_BREAKDOWN_MISROUTED.name,
            client.lastRepairFailureCode
        )
        assertEquals(ConversationRoute.DIRECT_REPLY, client.lastFailedRoute)
        assertEquals(utterance, client.lastRepairUserText)
    }

    @Test
    fun politeExplicitBreakdownRepairsToTaskCommand() = runBlocking {
        val utterance = "can you break down evaluation parent task"
        val client = RoutingClient(
            primaryResponses = listOf(directReply(HELP_REPLY)),
            repairedResponse = taskCommand()
        )

        val decision = orchestrator(client).process(utterance, "Interaction: NONE")

        assertEquals(ConversationRoute.TASK_COMMAND, decision.route)
        assertEquals(utterance, decision.taskText)
        assertEquals(1, client.repairCalls)
    }

    @Test
    fun priorHelpMemoryCannotKeepFreshBreakdownCommandInDirectReply() = runBlocking {
        val memory = ConversationSessionMemory()
        val client = RoutingClient(
            primaryResponses = listOf(
                directReply(HELP_REPLY),
                directReply(HELP_REPLY)
            ),
            repairedResponse = taskCommand()
        )
        val orchestrator = orchestrator(client, memory)

        val helpDecision = orchestrator.process(
            "how to break down a task",
            "Interaction: NONE"
        )
        assertEquals(ConversationRoute.DIRECT_REPLY, helpDecision.route)
        orchestrator.commitFinalDecision(helpDecision)

        val command = "break down evaluation parent task"
        val commandDecision = orchestrator.process(command, "Interaction: NONE")

        assertTrue(client.primaryMemorySnapshots[1].contains("User: how to break down a task"))
        assertTrue(client.primaryMemorySnapshots[1].contains("Assistant: $HELP_REPLY"))
        assertEquals(ConversationRoute.TASK_COMMAND, commandDecision.route)
        assertEquals(command, commandDecision.taskText)
        assertEquals(1, client.repairCalls)
    }

    @Test
    fun informationalVariantsAndUnrelatedDirectReplyRemainUnaffected() = runBlocking {
        val utterances = listOf(
            "what is task breakdown",
            "explain task breakdown",
            "tell me how task breakdown works",
            "what can this app do?"
        )

        utterances.forEach { utterance ->
            val client = RoutingClient(
                primaryResponses = listOf(directReply(HELP_REPLY))
            )
            val decision = orchestrator(client).process(utterance, "Interaction: NONE")

            assertEquals(ConversationRoute.DIRECT_REPLY, decision.route)
            assertEquals(0, client.repairCalls)
        }
    }

    @Test
    fun unresolvedDeicticBreakdownRepairsToClarificationWithoutInventedTarget() = runBlocking {
        val client = RoutingClient(
            primaryResponses = listOf(directReply(HELP_REPLY)),
            repairedResponse = clarification("Which task do you want to break down?")
        )

        val decision = orchestrator(client).process(
            normalizedText = "break it down",
            appContextSummary = "Interaction: NONE",
            readOnlyTaskContextSnapshot = "Scope: NONE\nItems: NONE",
            contextFocus = null
        )

        assertEquals(ConversationRoute.ASK_CLARIFICATION, decision.route)
        assertEquals("", decision.taskText)
        assertEquals("", decision.contextRef)
        assertFalse(decision.reply.contains("T1"))
        assertEquals(1, client.repairCalls)
    }

    @Test
    fun correctPrimaryTaskCommandIsAcceptedWithoutRepair() = runBlocking {
        val utterance = "break down evaluation parent task"
        val client = RoutingClient(
            primaryResponses = listOf(taskCommand())
        )

        val decision = orchestrator(client).process(utterance, "Interaction: NONE")

        assertEquals(ConversationRoute.TASK_COMMAND, decision.route)
        assertEquals(utterance, decision.taskText)
        assertEquals("conversation_agent", decision.source)
        assertEquals(0, client.repairCalls)
    }

    @Test
    fun boundedDetectorCoversExplicitBreakdownFormsWithoutTreatingHelpAsExecution() {
        listOf(
            "break down evaluation parent task",
            "breakdown evaluation parent task",
            "can you break down evaluation parent task",
            "please split evaluation parent task into smaller steps",
            "divide evaluation parent task into subtasks",
            "break this down",
            "break it down"
        ).forEach {
            assertTrue(it, OperationalBreakdownRoutingGuard.isExplicitOperationalRequest(it))
        }

        listOf(
            "how to break down a task",
            "how does task breakdown work",
            "what is task breakdown",
            "can you explain task breakdown",
            "tell me about the breakdown feature",
            "can you break down how the feature works?",
            "break down this explanation for me"
        ).forEach {
            assertFalse(it, OperationalBreakdownRoutingGuard.isExplicitOperationalRequest(it))
        }
    }

    @Test
    fun routingPromptMakesCurrentTurnBreakdownPrecedenceExplicit() {
        val prompt = ConversationAgentClient.ROUTING_SYSTEM_PROMPT

        assertTrue(
            prompt.contains(
                "current normalized User text is the routing decision target\n" +
                    "  and takes precedence over prior informational/help memory"
            )
        )
        assertTrue(prompt.contains("informational breakdown question"))
        assertTrue(prompt.contains("is\n  DIRECT_REPLY"))
        assertTrue(prompt.contains("fresh operational breakdown command"))
        assertTrue(prompt.contains("is TASK_COMMAND"))
        assertTrue(prompt.contains("A previous help turn does not make a later command informational"))
        assertTrue(prompt.contains("Current turn: User: \"Break down evaluation parent task.\""))
        assertTrue(prompt.contains("Expected route: TASK_COMMAND"))
    }

    @Test
    fun boundedRepairPromptUsesCurrentTextAndFailsClosedForDeicticRequest() = runBlocking {
        val client = RepairPromptCapturingClient()

        client.processRepair(
            userText = "break it down",
            appContextSummary = "Supplied temporary refs: NONE\nCurrent validated focus ref: NONE",
            failureCode =
                ConversationDecisionFailureCode.OPERATIONAL_BREAKDOWN_MISROUTED.name,
            failedRoute = ConversationRoute.DIRECT_REPLY
        )

        assertEquals("break it down", client.capturedUserText)
        assertTrue(client.capturedContext.contains("Focus on the CURRENT original normalized User text"))
        assertTrue(client.capturedContext.contains("named target"))
        assertTrue(client.capturedContext.contains("is TASK_COMMAND"))
        assertTrue(client.capturedContext.contains("remains DIRECT_REPLY"))
        assertTrue(client.capturedContext.contains("is ASK_CLARIFICATION"))
        assertTrue(client.capturedContext.contains("Never invent T1, a task name, or a Room ID"))
    }

    private fun orchestrator(
        client: ConversationAgentClient,
        memory: ConversationSessionMemory = ConversationSessionMemory()
    ) = ConversationOrchestrator(
        conversationAgentClient = client,
        parser = ConversationDecisionParser(),
        memory = memory
    )

    private class RoutingClient(
        primaryResponses: List<String>,
        private val repairedResponse: String = clarification("Which task?")
    ) : ConversationAgentClient(null) {
        private val primaryResponses = ArrayDeque(primaryResponses)
        val primaryMemorySnapshots = mutableListOf<String>()
        var repairCalls = 0
        var lastRepairFailureCode = ""
        var lastFailedRoute: ConversationRoute? = null
        var lastRepairUserText = ""

        override suspend fun process(
            userText: String,
            memorySnapshot: String,
            appContextSummary: String
        ): String {
            primaryMemorySnapshots += memorySnapshot
            return primaryResponses.removeFirst()
        }

        override suspend fun processRepair(
            userText: String,
            appContextSummary: String,
            failureCode: String,
            failedRoute: ConversationRoute?
        ): String {
            repairCalls += 1
            lastRepairFailureCode = failureCode
            lastFailedRoute = failedRoute
            lastRepairUserText = userText
            return repairedResponse
        }
    }

    private class RepairPromptCapturingClient : ConversationAgentClient(null) {
        var capturedUserText = ""
        var capturedContext = ""

        override suspend fun processRepair(
            userText: String,
            appContextSummary: String
        ): String {
            capturedUserText = userText
            capturedContext = appContextSummary
            return taskCommand()
        }
    }

    private companion object {
        const val HELP_REPLY =
            "To break down a task, ask me to create actionable subtasks for a named task."

        fun directReply(reply: String): String = decisionJson(
            route = "DIRECT_REPLY",
            reply = reply
        )

        fun taskCommand(): String = decisionJson(route = "TASK_COMMAND")

        fun clarification(reply: String): String = decisionJson(
            route = "ASK_CLARIFICATION",
            reply = reply
        )

        fun decisionJson(route: String, reply: String = ""): String = JSONObject()
            .put("route", route)
            .put("task_text", "")
            .put("reply", reply)
            .put("context_ref", "")
            .put("context_detail", "NONE")
            .put("context_action", "NONE")
            .put("setting_action", "NONE")
            .put("setting_target", "NONE")
            .put("query_reading_move", "NONE")
            .put("navigation_target", "NONE").put("query_presentation_hint", "NONE")
            .put("confidence", 0.97)
            .put("listen_again", true)
            .toString()
    }
}
