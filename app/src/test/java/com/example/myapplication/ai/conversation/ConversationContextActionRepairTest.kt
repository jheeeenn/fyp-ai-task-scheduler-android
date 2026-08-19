package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.conversation.taskcontext.ContextActionRepairPolicy
import com.example.myapplication.ai.conversation.taskcontext.ContextActionReferenceGroundingValidator
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextItem
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextSnapshot
import com.example.myapplication.ai.conversation.taskcontext.TaskContextScope
import com.example.myapplication.ai.schema.AgentResponseSchemas
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationContextActionRepairTest {
    @Test
    fun primaryTaskCommandCanBeRepairedToContextActionWithTwoCalls() = runBlocking {
        val client = RepairClient(
            primary = decision("TASK_COMMAND", taskText = "move the second one"),
            repaired = decision(
                "CONTEXT_ACTION",
                contextRef = "T2",
                contextAction = "RESCHEDULE"
            )
        )
        val orchestrator = ConversationOrchestrator(client, ConversationDecisionParser())
        val primary = orchestrator.process("move the second one", "AFTER_TASK_SUMMARY", prompt)
        val repaired = orchestrator.processContextActionRepair(
            "move the second one",
            prompt,
            primary.route,
            "AFTER_TASK_SUMMARY"
        )

        assertEquals(ConversationRoute.CONTEXT_ACTION, repaired.route)
        assertEquals(ConversationContextAction.RESCHEDULE, repaired.contextAction)
        assertEquals("conversation_agent_context_action_repair", repaired.source)
        assertEquals(2, client.calls)
    }

    @Test
    fun validTaskDetailFocusRepairsDeleteThisTaskToContextualDelete() = runBlocking {
        val client = RepairClient(
            primary = decision("ASK_CLARIFICATION", reply = "Which task?"),
            repaired = decision(
                "CONTEXT_ACTION",
                contextRef = "T1",
                contextAction = "DELETE"
            )
        )
        val orchestrator = ConversationOrchestrator(client, ConversationDecisionParser())
        val primary = orchestrator.process("delete this task", "AFTER_TASK_DETAILS", prompt)
        val repaired = orchestrator.processContextActionRepair(
            "delete this task",
            prompt,
            primary.route,
            "AFTER_TASK_DETAILS"
        )

        assertEquals(ConversationRoute.CONTEXT_ACTION, repaired.route)
        assertEquals(ConversationContextAction.DELETE, repaired.contextAction)
        assertEquals(2, client.calls)
    }

    @Test
    fun repairEligibilityRequiresMutationAndAValidContextSelector() {
        val primary = ConversationDecision(
            route = ConversationRoute.TASK_COMMAND,
            taskText = "change it",
            source = "conversation_agent"
        )
        assertTrue(
            ContextActionRepairPolicy.shouldAttempt(
                "move the second one to Friday",
                primary,
                snapshot,
                true,
                null
            )
        )
        assertFalse(
            ContextActionRepairPolicy.shouldAttempt(
                "what time is the second one",
                primary,
                snapshot,
                true,
                null
            )
        )
        assertFalse(
            ContextActionRepairPolicy.shouldAttempt(
                "move it to Friday",
                primary,
                snapshot,
                true,
                null
            )
        )
        val focus = ConversationContextFocus(
            available = true,
            ref = "T2",
            generation = 4,
            detail = ConversationContextDetail.SUMMARY,
            title = "Software"
        )
        assertTrue(
            ContextActionRepairPolicy.shouldAttempt(
                "change its title to Revision",
                primary,
                snapshot,
                true,
                focus
            )
        )
        assertTrue(
            ContextActionRepairPolicy.shouldAttempt(
                "delete this task",
                primary,
                snapshot,
                true,
                focus
            )
        )
        assertTrue(
            ContextActionRepairPolicy.shouldAttempt(
                "remove it",
                primary,
                snapshot,
                true,
                focus
            )
        )
        listOf("mark it done", "reopen it", "this task is not done").forEach { utterance ->
            assertTrue(
                utterance,
                ContextActionRepairPolicy.shouldAttempt(
                    utterance,
                    primary,
                    snapshot,
                    true,
                    focus
                )
            )
        }
        assertFalse(
            ContextActionRepairPolicy.shouldAttempt(
                "delete this task",
                primary,
                snapshot,
                true,
                null
            )
        )
    }

    @Test
    fun noFocusDeleteCanClarifyAndDeleteSchemaRemainsBounded() {
        val repairedDelete = ConversationDecisionParser().parse(
            decision(
                route = "ASK_CLARIFICATION",
                reply = "Please say the task name you want to delete."
            )
        )
        assertEquals(ConversationRoute.ASK_CLARIFICATION, repairedDelete.route)
        assertEquals(ConversationContextAction.NONE, repairedDelete.contextAction)
        val acceptedDelete = ConversationDecisionParser().parse(
            decision(
                route = "CONTEXT_ACTION",
                contextRef = "T1",
                contextAction = "DELETE"
            )
        )
        assertEquals(ConversationContextAction.DELETE, acceptedDelete.contextAction)

        val schema = AgentResponseSchemas.contextActionRepairResponseFormat()
            .getJSONObject("json_schema")
            .getJSONObject("schema")
        val routes = schema.getJSONObject("properties")
            .getJSONObject("route")
            .getJSONArray("enum")
            .toStrings()
        val moveValues = schema.getJSONObject("properties")
            .getJSONObject("query_reading_move")
            .getJSONArray("enum")
            .toStrings()
        val hintValues = schema.getJSONObject("properties")
            .getJSONObject("query_presentation_hint")
            .getJSONArray("enum")
            .toStrings()
        val actionValues = schema.getJSONObject("properties")
            .getJSONObject("context_action")
            .getJSONArray("enum")
            .toStrings()
        assertEquals(setOf("CONTEXT_ACTION", "ASK_CLARIFICATION"), routes)
        assertEquals(setOf("NONE"), moveValues)
        assertEquals(setOf("NONE"), hintValues)
        assertEquals(
            setOf("NONE", "UPDATE", "RESCHEDULE", "DELETE", "MARK_DONE", "MARK_UNDONE"),
            actionValues
        )
        val routingActions = AgentResponseSchemas.conversationDecisionResponseFormat()
            .getJSONObject("json_schema")
            .getJSONObject("schema")
            .getJSONObject("properties")
            .getJSONObject("context_action")
            .getJSONArray("enum")
            .toStrings()
        assertEquals(
            setOf("NONE", "UPDATE", "RESCHEDULE", "DELETE", "MARK_DONE", "MARK_UNDONE"),
            routingActions
        )
        assertEquals(false, schema.getBoolean("additionalProperties"))
    }

    @Test
    fun taskDetailFocusGroundsMoveItToItsOnlyTemporaryRef() {
        val detailSnapshot = snapshot.copy(
            scope = TaskContextScope.TASK_DETAIL,
            items = listOf(item("T1", "Dentist"))
        )
        val detailFocus = ConversationContextFocus(
            available = true,
            ref = "T1",
            generation = detailSnapshot.generation,
            detail = ConversationContextDetail.SUMMARY,
            title = "Dentist"
        )
        val primary = ConversationDecision(
            route = ConversationRoute.TASK_COMMAND,
            taskText = "move it one hour later",
            source = "conversation_agent"
        )

        assertTrue(
            ContextActionRepairPolicy.shouldAttempt(
                "move it one hour later",
                primary,
                detailSnapshot,
                isResultInteraction = true,
                contextFocus = detailFocus
            )
        )
        assertTrue(
            ContextActionReferenceGroundingValidator.validate(
                normalizedText = "move it one hour later",
                decision = primary.copy(
                    route = ConversationRoute.CONTEXT_ACTION,
                    taskText = "",
                    contextRef = "T1",
                    contextAction = ConversationContextAction.RESCHEDULE,
                    confidence = 0.97
                ),
                capturedSnapshot = detailSnapshot,
                currentFocus = detailFocus
            ).isValid
        )
    }

    private class RepairClient(
        private val primary: String,
        private val repaired: String
    ) : ConversationAgentClient(null) {
        var calls = 0

        override suspend fun process(
            userText: String,
            memorySnapshot: String,
            appContextSummary: String
        ): String {
            calls += 1
            return primary
        }

        override suspend fun processContextActionRepair(
            userText: String,
            memorySnapshot: String,
            taskContextSnapshot: String,
            primaryRoute: ConversationRoute,
            currentInteraction: String
        ): String {
            calls += 1
            return repaired
        }
    }

    private companion object {
        val snapshot = ReadOnlyTaskContextSnapshot(
            scope = TaskContextScope.RECENT_QUERY_RESULTS,
            generation = 4,
            items = listOf(
                item("T1", "Medicine"),
                item("T2", "Software")
            ),
            truncated = false
        )
        val prompt = "Generation: 4\nItems: T1, T2"

        fun item(ref: String, title: String) = ReadOnlyTaskContextItem(
            ref = ref,
            title = title,
            dueDate = "",
            dueTime = "",
            isDone = false,
            subtaskCount = 0,
            unfinishedSubtaskCount = 0
        )

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

        fun JSONArray.toStrings(): Set<String> =
            (0 until length()).map { getString(it) }.toSet()
    }
}
