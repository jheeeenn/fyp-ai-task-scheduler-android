package com.example.myapplication.ai.conversation

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConversationDecisionCanonicalizationTest {
    @Test
    fun pollutedContextActionIsAcceptedWithoutRetryAndDiscardedTextNeverEntersMemory() =
        runBlocking {
            val memory = ConversationSessionMemory()
            val client = CapturingRepairClient(
                primaryResponse = decisionJson(
                    route = "CONTEXT_ACTION",
                    taskText = EXACT_UTTERANCE,
                    reply = DISCARDED_REPLY,
                    contextRef = "T2",
                    contextAction = "RESCHEDULE"
                )
            )
            val orchestrator = ConversationOrchestrator(
                conversationAgentClient = client,
                parser = ConversationDecisionParser(),
                memory = memory
            )

            val decision = orchestrator.process(
                normalizedText = EXACT_UTTERANCE,
                appContextSummary = "Interaction: AFTER_CONTEXT_SUGGESTION",
                readOnlyTaskContextSnapshot = TWO_ITEM_CONTEXT
            )
            orchestrator.commitFinalDecision(decision)

            assertEquals(1, client.primaryCalls)
            assertEquals(0, client.schemaRepairCalls)
            assertEquals(ConversationRoute.CONTEXT_ACTION, decision.route)
            assertEquals("", decision.taskText)
            assertEquals("", decision.reply)
            assertEquals("T2", decision.contextRef)
            assertEquals(ConversationContextAction.RESCHEDULE, decision.contextAction)
            assertTrue(memory.snapshotForPrompt().contains("User: $EXACT_UTTERANCE"))
            assertFalse(memory.snapshotForPrompt().contains(DISCARDED_REPLY))
            assertFalse(memory.snapshotForPrompt().contains("Assistant: $DISCARDED_REPLY"))
        }

    @Test
    fun genuineFailureRetriesOnceWithOnlyBoundedFailureCodeAndTemporaryRefs() = runBlocking {
        val invalidResponse = decisionJson(
            route = "CONTEXT_ACTION",
            taskText = EXACT_UTTERANCE,
            reply = DISCARDED_REPLY,
            contextRef = "T2",
            contextAction = "RESCHEDULE"
        ).let { valid ->
            JSONObject(valid).apply { remove("listen_again") }.toString()
        }
        val client = CapturingRepairClient(
            primaryResponse = invalidResponse,
            repairedResponse = decisionJson(
                route = "CONTEXT_ACTION",
                contextRef = "T2",
                contextAction = "RESCHEDULE"
            )
        )
        val orchestrator = ConversationOrchestrator(client, ConversationDecisionParser())

        val decision = orchestrator.process(
            normalizedText = EXACT_UTTERANCE,
            appContextSummary = "Interaction: AFTER_CONTEXT_SUGGESTION",
            readOnlyTaskContextSnapshot = TWO_ITEM_CONTEXT_WITH_PRIVATE_DATA
        )

        assertEquals(1, client.primaryCalls)
        assertEquals(1, client.schemaRepairCalls)
        assertEquals("conversation_agent_schema_repair", decision.source)
        assertTrue(client.repairContext.contains("MISSING_FIELDS"))
        assertTrue(client.repairContext.contains("Supplied temporary refs: T1,T2"))
        assertFalse(client.repairContext.contains(PRIVATE_TITLE))
        assertFalse(client.repairContext.contains(PRIVATE_ROOM_ID))
        assertFalse(client.repairContext.contains(DISCARDED_REPLY))
        assertFalse(client.repairContext.contains(invalidResponse))
    }

    @Test
    fun canonicalizationDiagnosticContainsOnlyRouteAndChangedFieldNames() {
        val source = File(
            "src/main/java/com/example/myapplication/ai/conversation/" +
                "ConversationOrchestrator.kt"
        ).readText()
        val diagnostic = source
            .substringAfter("\"CONVO_DECISION_CANONICALIZED\"")
            .substringBefore("return result.decision")

        assertTrue(diagnostic.contains("result.decision.route.name"))
        assertTrue(diagnostic.contains("canonicalizationReport.fields"))
        assertFalse(diagnostic.contains("rawContent"))
        assertFalse(diagnostic.contains("decision.reply"))
        assertFalse(diagnostic.contains("decision.taskText"))
    }

    private class CapturingRepairClient(
        private val primaryResponse: String,
        private val repairedResponse: String = decisionJson(
            route = "ASK_CLARIFICATION",
            reply = "Which task?"
        )
    ) : ConversationAgentClient(null) {
        var primaryCalls = 0
        var schemaRepairCalls = 0
        var repairContext = ""

        override suspend fun process(
            userText: String,
            memorySnapshot: String,
            appContextSummary: String
        ): String {
            primaryCalls += 1
            return primaryResponse
        }

        override suspend fun processRepair(
            userText: String,
            appContextSummary: String
        ): String {
            schemaRepairCalls += 1
            repairContext = appContextSummary
            return repairedResponse
        }
    }

    private companion object {
        const val EXACT_UTTERANCE =
            "move the second one to 1st of August 2026 at 9:00 a.m."
        const val DISCARDED_REPLY = "I already moved Groceries to a new date."
        const val PRIVATE_TITLE = "Private dentist title"
        const val PRIVATE_ROOM_ID = "918273645"
        const val TWO_ITEM_CONTEXT =
            "Scope: CONTEXT_SUGGESTION\nGeneration: 4\nItems:\n" +
                "{\"ref\":\"T1\"}\n{\"ref\":\"T2\"}"
        const val TWO_ITEM_CONTEXT_WITH_PRIVATE_DATA =
            "Scope: CONTEXT_SUGGESTION\nGeneration: 4\nItems:\n" +
                "{\"ref\":\"T1\",\"title\":\"$PRIVATE_TITLE\",\"room\":\"$PRIVATE_ROOM_ID\"}\n" +
                "{\"ref\":\"T2\",\"title\":\"Another private title\"}"

        fun decisionJson(
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
            .put("query_reading_move", "NONE")
            .put("query_presentation_hint", "NONE")
            .put("confidence", 0.97)
            .put("listen_again", true)
            .toString()
    }
}
