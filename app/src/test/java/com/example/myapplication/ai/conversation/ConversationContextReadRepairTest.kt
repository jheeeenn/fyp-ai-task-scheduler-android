package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.conversation.taskcontext.ContextReadRepairDisposition
import com.example.myapplication.ai.conversation.taskcontext.ContextReadRepairPolicy
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextCapture
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextItem
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextSnapshot
import com.example.myapplication.ai.conversation.taskcontext.TaskContextScope
import com.example.myapplication.ai.schema.AgentResponseSchemas
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationContextReadRepairTest {
    @Test
    fun primaryClarificationCanBeRepairedToT2TimeUsingSameCapture() = kotlinx.coroutines.runBlocking {
        val client = FakeClient(
            primaryResponse = decisionJson("ASK_CLARIFICATION", reply = "Which task?"),
            repairResponse = decisionJson(
                route = "CONTEXT_READ",
                contextRef = "T2",
                contextDetail = "TIME"
            )
        )
        val orchestrator = ConversationOrchestrator(client, ConversationDecisionParser())
        val capture = capture()

        val primary = orchestrator.process(
            "what time it is for the second",
            "AFTER_TASK_DETAILS",
            capture.promptText
        )
        assertTrue(
            ContextReadRepairPolicy.shouldAttempt(
                primary,
                capture.snapshot,
                isResultInteraction = true
            )
        )
        val repaired = orchestrator.processContextReadRepair(
            normalizedText = "what time it is for the second",
            readOnlyTaskContextSnapshot = capture.promptText,
            primaryRoute = primary.route,
            currentInteraction = "AFTER_TASK_DETAILS"
        )
        val evaluation = ContextReadRepairPolicy.evaluate(
            normalizedText = "what time it is for the second",
            repairedDecision = repaired,
            capturedSnapshot = capture.snapshot,
            currentGeneration = capture.snapshot.generation
        )

        assertEquals(ConversationRoute.CONTEXT_READ, repaired.route)
        assertEquals("T2", repaired.contextRef)
        assertEquals(ConversationContextDetail.TIME, repaired.contextDetail)
        assertEquals("conversation_agent_context_repair", repaired.source)
        assertEquals(ContextReadRepairDisposition.ACCEPTED, evaluation.disposition)
        assertEquals(capture.promptText, client.repairTaskContext)
        assertEquals(2, client.totalCalls)
    }

    @Test
    fun uniqueSuppliedTitleRepairSelectsItsRef() = kotlinx.coroutines.runBlocking {
        val client = FakeClient(
            primaryResponse = decisionJson("ASK_CLARIFICATION", reply = "Which task?"),
            repairResponse = decisionJson(
                route = "CONTEXT_READ",
                contextRef = "T3",
                contextDetail = "TIME"
            )
        )
        val orchestrator = ConversationOrchestrator(client, ConversationDecisionParser())
        val capture = capture()
        val primary = orchestrator.process(
            "what time is it for the podcast",
            "AFTER_TASK_DETAILS",
            capture.promptText
        )

        val repaired = orchestrator.processContextReadRepair(
            "what time is it for the podcast",
            capture.promptText,
            primary.route,
            "AFTER_TASK_DETAILS"
        )
        val evaluation = ContextReadRepairPolicy.evaluate(
            "what time is it for the podcast",
            repaired,
            capture.snapshot,
            capture.snapshot.generation
        )

        assertEquals("T3", repaired.contextRef)
        assertEquals(ContextReadRepairDisposition.ACCEPTED, evaluation.disposition)
        assertTrue(client.repairTaskContext.contains("\"title\":\"Podcast\""))
        assertEquals(2, client.totalCalls)
    }

    @Test
    fun ambiguousTitleRepairAbstainsAndPreservesClarification() = kotlinx.coroutines.runBlocking {
        val client = FakeClient(
            primaryResponse = decisionJson("ASK_CLARIFICATION", reply = "Which task?"),
            repairResponse = decisionJson("ASK_CLARIFICATION", reply = "Which podcast task?")
        )
        val orchestrator = ConversationOrchestrator(client, ConversationDecisionParser())
        val capture = capture(secondPodcast = true)
        val primary = orchestrator.process(
            "what time is the podcast",
            "AFTER_TASK_DETAILS",
            capture.promptText
        )
        val repaired = orchestrator.processContextReadRepair(
            "what time is the podcast",
            capture.promptText,
            primary.route,
            "AFTER_TASK_DETAILS"
        )

        val evaluation = ContextReadRepairPolicy.evaluate(
            "what time is the podcast",
            repaired,
            capture.snapshot,
            capture.snapshot.generation
        )
        assertEquals(ContextReadRepairDisposition.ABSTAINED, evaluation.disposition)
        assertEquals("Which task?", primary.reply)
        assertEquals(2, client.totalCalls)
    }

    @Test
    fun mutationWordingCannotBeConvertedToContextRead() {
        val capture = capture()
        val repaired = ConversationDecision(
            route = ConversationRoute.CONTEXT_READ,
            contextRef = "T2",
            contextDetail = ConversationContextDetail.SUMMARY,
            confidence = 0.99,
            source = "conversation_agent_context_repair"
        )

        val evaluation = ContextReadRepairPolicy.evaluate(
            "move the second task to next day",
            repaired,
            capture.snapshot,
            capture.snapshot.generation
        )

        assertEquals(ContextReadRepairDisposition.REJECTED, evaluation.disposition)
    }

    @Test
    fun titleBasedMutationCannotBeConvertedToContextRead() {
        val capture = capture()
        val repaired = ConversationDecision(
            route = ConversationRoute.CONTEXT_READ,
            contextRef = "T3",
            contextDetail = ConversationContextDetail.SUMMARY,
            confidence = 0.99,
            source = "conversation_agent_context_repair"
        )

        val evaluation = ContextReadRepairPolicy.evaluate(
            "delete the podcast",
            repaired,
            capture.snapshot,
            capture.snapshot.generation
        )

        assertEquals(ContextReadRepairDisposition.REJECTED, evaluation.disposition)
    }

    @Test
    fun staleGenerationRejectsOtherwiseValidRepair() {
        val capture = capture()
        val repaired = ConversationDecision(
            route = ConversationRoute.CONTEXT_READ,
            contextRef = "T2",
            contextDetail = ConversationContextDetail.TIME,
            confidence = 0.99,
            source = "conversation_agent_context_repair"
        )

        val evaluation = ContextReadRepairPolicy.evaluate(
            "what time is the second",
            repaired,
            capture.snapshot,
            capture.snapshot.generation + 1
        )

        assertEquals(ContextReadRepairDisposition.REJECTED, evaluation.disposition)
        assertFalse(evaluation.validation?.isValid ?: true)
    }

    @Test
    fun schemaRepairConsumesSecondCallAndPreventsSemanticThirdCall() = kotlinx.coroutines.runBlocking {
        val client = FakeClient(
            primaryResponse = "not json",
            schemaRepairResponse = decisionJson("ASK_CLARIFICATION", reply = "Which task?"),
            repairResponse = decisionJson(
                "CONTEXT_READ",
                contextRef = "T2",
                contextDetail = "TIME"
            )
        )
        val orchestrator = ConversationOrchestrator(client, ConversationDecisionParser())
        val capture = capture()

        val primary = orchestrator.process(
            "what time is the second",
            "AFTER_TASK_DETAILS",
            capture.promptText
        )

        assertEquals("conversation_agent_schema_repair", primary.source)
        assertFalse(
            ContextReadRepairPolicy.shouldAttempt(
                primary,
                capture.snapshot,
                isResultInteraction = true
            )
        )
        assertEquals(2, client.totalCalls)
    }

    @Test
    fun repairSchemaAllowsOnlyContextReadAndClarification() {
        val format = AgentResponseSchemas.contextReadRepairResponseFormat()
        val routeEnum = format
            .getJSONObject("json_schema")
            .getJSONObject("schema")
            .getJSONObject("properties")
            .getJSONObject("route")
            .getJSONArray("enum")
        val routes = (0 until routeEnum.length()).map { routeEnum.getString(it) }.toSet()

        assertEquals(setOf("CONTEXT_READ", "ASK_CLARIFICATION"), routes)
    }

    private class FakeClient(
        private val primaryResponse: String,
        private val repairResponse: String,
        private val schemaRepairResponse: String = decisionJson(
            "ASK_CLARIFICATION",
            reply = "Which task?"
        )
    ) : ConversationAgentClient(null) {
        var totalCalls: Int = 0
        var repairTaskContext: String = ""

        override suspend fun process(
            userText: String,
            memorySnapshot: String,
            appContextSummary: String
        ): String {
            totalCalls += 1
            return primaryResponse
        }

        override suspend fun processRepair(
            userText: String,
            appContextSummary: String
        ): String {
            totalCalls += 1
            return schemaRepairResponse
        }

        override suspend fun processContextReadRepair(
            userText: String,
            memorySnapshot: String,
            taskContextSnapshot: String,
            primaryRoute: ConversationRoute,
            currentInteraction: String
        ): String {
            totalCalls += 1
            repairTaskContext = taskContextSnapshot
            return repairResponse
        }
    }

    private companion object {
        fun decisionJson(
            route: String,
            reply: String = "",
            contextRef: String = "",
            contextDetail: String = "NONE"
        ): String = JSONObject()
            .put("route", route)
            .put("task_text", "")
            .put("reply", reply)
            .put("context_ref", contextRef)
            .put("context_detail", contextDetail)
            .put("confidence", 0.97)
            .put("listen_again", true)
            .toString()

        fun capture(secondPodcast: Boolean = false): ReadOnlyTaskContextCapture {
            val items = listOf(
                item("T1", "Medicine"),
                item("T2", if (secondPodcast) "Podcast notes" else "Groceries"),
                item("T3", "Podcast")
            )
            val snapshot = ReadOnlyTaskContextSnapshot(
                scope = TaskContextScope.RECENT_QUERY_RESULTS,
                generation = 6,
                items = items,
                truncated = false
            )
            val prompt = buildString {
                appendLine("Scope: RECENT_QUERY_RESULTS")
                appendLine("Generation: 6")
                appendLine("Items:")
                items.forEach { appendLine("{\"ref\":\"${it.ref}\",\"title\":\"${it.title}\"}") }
                append("Truncated: false")
            }
            return ReadOnlyTaskContextCapture(snapshot, prompt)
        }

        fun item(ref: String, title: String) = ReadOnlyTaskContextItem(
            ref = ref,
            title = title,
            dueDate = "23/07/2026",
            dueTime = "8:30 PM",
            isDone = false,
            subtaskCount = 0,
            unfinishedSubtaskCount = 0
        )
    }
}
