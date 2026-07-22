package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.conversation.taskcontext.ContextReadRepairDisposition
import com.example.myapplication.ai.conversation.taskcontext.ContextReadRepairPolicy
import com.example.myapplication.ai.conversation.taskcontext.ContextFocusCarryForwardPolicy
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextReadValidator
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextResponseRenderer
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
    fun provisionalClarificationIsNotCommittedBeforeContextRepair() = kotlinx.coroutines.runBlocking {
        val client = FakeClient(
            primaryResponse = decisionJson("ASK_CLARIFICATION", reply = "Which task are you asking about?"),
            repairResponse = decisionJson("ASK_CLARIFICATION", reply = "Which task?")
        )
        val memory = ConversationSessionMemory()
        val orchestrator = ConversationOrchestrator(
            client,
            ConversationDecisionParser(),
            ConversationResponseParser(),
            memory
        )
        val capture = capture()

        val primary = orchestrator.process(
            "what time is it",
            "AFTER_TASK_DETAILS",
            capture.promptText
        )

        assertEquals(ConversationRoute.ASK_CLARIFICATION, primary.route)
        assertFalse(memory.snapshotForPrompt().contains("Which task are you asking about?"))
        assertEquals(1, memory.snapshotForPrompt().lineSequence().count { it == "User: what time is it" })
        assertEquals(null, memory.pendingAction)

        orchestrator.processContextReadRepair(
            "what time is it",
            capture.promptText,
            primary.route,
            "AFTER_TASK_DETAILS"
        )

        assertFalse(client.repairMemorySnapshot.contains("Which task are you asking about?"))
        assertEquals(
            1,
            client.repairMemorySnapshot.lineSequence().count { it == "User: what time is it" }
        )
        assertEquals(2, client.totalCalls)
    }

    @Test
    fun onlyFinalClarificationIsCommittedWhenRepairAbstains() = kotlinx.coroutines.runBlocking {
        val client = FakeClient(
            primaryResponse = decisionJson("ASK_CLARIFICATION", reply = "Which task are you asking about?"),
            repairResponse = decisionJson("ASK_CLARIFICATION", reply = "Which one?")
        )
        val memory = ConversationSessionMemory()
        val orchestrator = ConversationOrchestrator(
            client,
            ConversationDecisionParser(),
            ConversationResponseParser(),
            memory
        )
        val capture = capture()
        val primary = orchestrator.process("what time is it", "AFTER_TASK_DETAILS", capture.promptText)
        orchestrator.processContextReadRepair(
            "what time is it",
            capture.promptText,
            primary.route,
            "AFTER_TASK_DETAILS"
        )

        orchestrator.commitFinalDecision(primary)

        val prompt = memory.snapshotForPrompt()
        assertEquals(1, prompt.lineSequence().count { it == "User: what time is it" })
        assertEquals(
            1,
            prompt.lineSequence().count { it == "Assistant: Which task are you asking about?" }
        )
        assertFalse(prompt.contains("Which one?"))
    }

    @Test
    fun acceptedRepairLeavesNoPrimaryClarificationInMemory() = kotlinx.coroutines.runBlocking {
        val client = FakeClient(
            primaryResponse = decisionJson("ASK_CLARIFICATION", reply = "Which task are you asking about?"),
            repairResponse = decisionJson(
                "CONTEXT_READ",
                contextRef = "T2",
                contextDetail = "TIME"
            )
        )
        val memory = ConversationSessionMemory()
        val orchestrator = ConversationOrchestrator(
            client,
            ConversationDecisionParser(),
            ConversationResponseParser(),
            memory
        )
        val capture = capture()
        val primary = orchestrator.process("what time is it", "AFTER_TASK_DETAILS", capture.promptText)
        val repaired = orchestrator.processContextReadRepair(
            "what time is it",
            capture.promptText,
            primary.route,
            "AFTER_TASK_DETAILS"
        )
        val item = capture.snapshot.items.first { it.ref == repaired.contextRef }

        orchestrator.recordAuthoritativeContextRead(
            item,
            repaired.contextRef,
            repaired.contextDetail,
            capture.snapshot.generation,
            "Groceries is scheduled at 8:30 PM."
        )

        val prompt = memory.snapshotForPrompt()
        assertFalse(prompt.contains("Which task are you asking about?"))
        assertEquals(1, prompt.lineSequence().count { it == "User: what time is it" })
        assertEquals(1, prompt.lineSequence().count { it == "Assistant: Groceries is scheduled at 8:30 PM." })
    }

    @Test
    fun doubleAbstentionCarriesValidatedT2FocusWithoutThirdModelCall() = kotlinx.coroutines.runBlocking {
        val client = FakeClient(
            primaryResponse = decisionJson("ASK_CLARIFICATION", reply = "Which task?"),
            repairResponse = decisionJson("ASK_CLARIFICATION", reply = "Which task?")
        )
        val memory = ConversationSessionMemory()
        val orchestrator = ConversationOrchestrator(
            client,
            ConversationDecisionParser(),
            ConversationResponseParser(),
            memory
        )
        val capture = capture()
        val focusedItem = capture.snapshot.items.first { it.ref == "T2" }
        memory.recordAuthoritativeContextRead(
            focusedItem,
            "T2",
            ConversationContextDetail.TIME,
            capture.snapshot.generation,
            "Groceries is scheduled at 8:30 PM."
        )
        val focus = memory.contextFocusForGeneration(
            capture.snapshot.generation,
            capture.snapshot.items.map { it.ref }.toSet()
        )

        val primary = orchestrator.process(
            "what time is it",
            "AFTER_TASK_DETAILS",
            capture.promptText,
            focus
        )
        val repair = orchestrator.processContextReadRepair(
            "what time is it",
            capture.promptText,
            primary.route,
            "AFTER_TASK_DETAILS",
            focus
        )
        assertEquals(ConversationRoute.ASK_CLARIFICATION, repair.route)

        val fallback = requireNotNull(
            ContextFocusCarryForwardPolicy.resolve(
                "what time is it",
                focus,
                capture.snapshot,
                isResultInteraction = true
            )
        )
        val validation = ReadOnlyTaskContextReadValidator.validate(
            fallback,
            capture.snapshot,
            capture.snapshot.generation
        )

        assertEquals(ContextFocusCarryForwardPolicy.SOURCE, fallback.source)
        assertEquals("T2", fallback.contextRef)
        assertEquals(ConversationContextDetail.TIME, fallback.contextDetail)
        assertTrue(validation.isValid)
        assertEquals(
            "Groceries is scheduled at 8:30 PM.",
            ReadOnlyTaskContextResponseRenderer.render(
                requireNotNull(validation.item),
                validation.detail
            )
        )
        assertEquals(2, client.totalCalls)
        assertTrue(client.primaryMemorySnapshot.contains("Current validated task focus:"))
        assertTrue(client.primaryMemorySnapshot.contains("Ref: T2"))
        assertTrue(client.repairMemorySnapshot.contains("Current validated task focus:"))
        assertTrue(client.repairMemorySnapshot.contains("Ref: T2"))
        assertFalse(client.repairMemorySnapshot.contains("Which task?"))
    }

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
        val actionEnum = format
            .getJSONObject("json_schema")
            .getJSONObject("schema")
            .getJSONObject("properties")
            .getJSONObject("context_action")
            .getJSONArray("enum")

        assertEquals(setOf("CONTEXT_READ", "ASK_CLARIFICATION"), routes)
        assertEquals(listOf("NONE"), (0 until actionEnum.length()).map { actionEnum.getString(it) })
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
        var primaryMemorySnapshot: String = ""
        var repairTaskContext: String = ""
        var repairMemorySnapshot: String = ""

        override suspend fun process(
            userText: String,
            memorySnapshot: String,
            appContextSummary: String
        ): String {
            totalCalls += 1
            primaryMemorySnapshot = memorySnapshot
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
            repairMemorySnapshot = memorySnapshot
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
            .put("context_action", "NONE")
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
