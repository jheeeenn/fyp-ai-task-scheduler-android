package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.conversation.taskcontext.ContextActionReferenceGroundingResult
import com.example.myapplication.ai.conversation.taskcontext.ContextActionReferenceGroundingValidator
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextItem
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextReadValidator
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextSnapshot
import com.example.myapplication.ai.conversation.taskcontext.TaskContextScope
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSingleResultContextReadTest {
    @Test
    fun soleSuppliedResultRepairsDeicticDateTimeAndWhenReads() = runBlocking {
        listOf(
            "what date is that" to ConversationContextDetail.DATE,
            "what is the time for that" to ConversationContextDetail.TIME,
            "when is that" to ConversationContextDetail.DATE_TIME
        ).forEach { (utterance, detail) ->
            val client = RouteAwareRepairClient(
                primary = decision(
                    route = "CONTEXT_READ",
                    contextDetail = detail.name
                ),
                repaired = decision(
                    route = "CONTEXT_READ",
                    contextRef = "T1",
                    contextDetail = detail.name
                )
            )
            val capture = capture("T1")

            val result = ConversationOrchestrator(client, ConversationDecisionParser()).process(
                normalizedText = utterance,
                appContextSummary = "Interaction: QUERY_PAGE",
                readOnlyTaskContextSnapshot = capture.promptText
            )

            assertEquals(ConversationRoute.CONTEXT_READ, result.route)
            assertEquals("T1", result.contextRef)
            assertEquals(detail, result.contextDetail)
            assertEquals("conversation_agent_schema_repair", result.source)
            assertEquals(1, client.repairCalls)
            assertTrue(client.repairContext.contains("Canonical route that failed contract validation:\nCONTEXT_READ"))
            assertTrue(client.repairContext.contains("not CONTEXT_ACTION"))
            assertTrue(client.repairContext.contains("Supplied temporary ref count: 1"))
            assertFalse(client.repairContext.contains("previous CONTEXT_ACTION was structurally impossible"))
        }
    }

    @Test
    fun repairedReadStillPassesAuthoritativeReadValidationAndEstablishesFocusAfterRecording() = runBlocking {
        val capture = capture("T1")
        val client = RouteAwareRepairClient(
            primary = decision(route = "CONTEXT_READ", contextDetail = "DATE"),
            repaired = decision(
                route = "CONTEXT_READ",
                contextRef = "T1",
                contextDetail = "DATE"
            )
        )
        val orchestrator = ConversationOrchestrator(client, ConversationDecisionParser())
        val repaired = orchestrator.process(
            "what date is that",
            "Interaction: QUERY_PAGE",
            capture.promptText
        )

        val validation = ReadOnlyTaskContextReadValidator.validate(
            decision = repaired,
            capturedSnapshot = capture.snapshot,
            currentGeneration = capture.snapshot.generation,
            normalizedText = "what date is that"
        )

        assertTrue(validation.isValid)
        assertEquals(ConversationContextDetail.DATE, validation.detail)
        assertEquals("T1", validation.item?.ref)
        assertEquals(null, orchestrator.contextFocusForSnapshot(capture.snapshot))

        orchestrator.recordAuthoritativeContextRead(
            item = requireNotNull(validation.item),
            selectedRef = repaired.contextRef,
            selectedDetail = repaired.contextDetail,
            capturedGeneration = capture.snapshot.generation,
            finalSpeech = "Dinner is scheduled for 11 August 2026."
        )

        val focus = orchestrator.contextFocusForSnapshot(capture.snapshot)
        assertNotNull(focus)
        assertEquals("T1", focus?.ref)
        assertEquals(capture.snapshot.generation, focus?.generation)
    }

    @Test
    fun multipleResultsWithoutFocusClarifyButExplicitSecondStillSelectsT2() = runBlocking {
        val capture = capture("T1", "T2")
        val ambiguousClient = RouteAwareRepairClient(
            primary = decision(route = "CONTEXT_READ", contextDetail = "DATE"),
            repaired = decision(
                route = "ASK_CLARIFICATION",
                reply = "Which task do you mean?"
            )
        )
        val ambiguous = ConversationOrchestrator(ambiguousClient, ConversationDecisionParser()).process(
            "what date is that",
            "Interaction: QUERY_PAGE",
            capture.promptText
        )

        assertEquals(ConversationRoute.ASK_CLARIFICATION, ambiguous.route)
        assertEquals("", ambiguous.contextRef)
        assertTrue(ambiguousClient.repairContext.contains("Supplied temporary ref count: 2"))
        assertTrue(ambiguousClient.repairContext.contains("never default to T1"))

        val explicitClient = RouteAwareRepairClient(
            primary = decision(
                route = "CONTEXT_READ",
                contextRef = "T2",
                contextDetail = "DATE"
            )
        )
        val explicit = ConversationOrchestrator(explicitClient, ConversationDecisionParser()).process(
            "what date is the second one",
            "Interaction: QUERY_PAGE",
            capture.promptText
        )

        assertEquals(ConversationRoute.CONTEXT_READ, explicit.route)
        assertEquals("T2", explicit.contextRef)
        assertEquals(0, explicitClient.repairCalls)
    }

    @Test
    fun soleUnpresentedQueryResultDoesNotGrantDeicticMutationAuthority() {
        val capture = capture("T1")
        val generatedAction = ConversationDecision(
            route = ConversationRoute.CONTEXT_ACTION,
            contextRef = "T1",
            contextAction = ConversationContextAction.DELETE,
            confidence = 0.97
        )

        val grounding = ContextActionReferenceGroundingValidator.validate(
            normalizedText = "delete that",
            decision = generatedAction,
            capturedSnapshot = capture.snapshot,
            currentFocus = null
        )

        assertFalse(grounding.isValid)
        assertEquals(ContextActionReferenceGroundingResult.MISSING_CURRENT_FOCUS, grounding.result)
    }

    @Test
    fun contextReadRequiresRefWhileContextActionMayAwaitAndroidGrounding() {
        val parser = ConversationDecisionParser()

        val readFailure = runCatching {
            parser.parse(decision(route = "CONTEXT_READ", contextDetail = "DATE"))
        }.exceptionOrNull() as ConversationSchemaException
        val action = parser.parse(decision(route = "CONTEXT_ACTION", contextAction = "DELETE"))

        assertEquals(ConversationDecisionFailureCode.INVALID_CONTEXT_REF, readFailure.decisionFailureCode)
        assertEquals(ConversationRoute.CONTEXT_READ, readFailure.failedRoute)
        assertEquals(ConversationRoute.CONTEXT_ACTION, action.route)
        assertEquals("", action.contextRef)
    }

    @Test
    fun promptsDescribeBoundedReadResolutionWithoutWeakeningMutationFocus() {
        val routing = ConversationAgentClient.ROUTING_SYSTEM_PROMPT
        val repair = ConversationAgentClient.CONTEXT_READ_REPAIR_SYSTEM_PROMPT

        assertTrue(routing.contains("bounded single-item selection"))
        assertTrue(routing.contains("only T1 is supplied"))
        assertTrue(routing.contains("User: What date is that?"))
        assertTrue(routing.contains("User: What is the time for that?"))
        assertTrue(routing.contains("User: When is that?"))
        assertTrue(routing.contains("T1 and T2 are supplied"))
        assertTrue(routing.contains("The single-item deictic exception is read-only"))
        assertTrue(repair.contains("bounded single-item read resolution"))
        assertTrue(repair.contains("This exception never applies to mutations"))
    }

    private class RouteAwareRepairClient(
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

    private data class Capture(
        val snapshot: ReadOnlyTaskContextSnapshot,
        val promptText: String
    )

    private companion object {
        fun capture(vararg refs: String): Capture {
            val items = refs.mapIndexed { index, ref ->
                ReadOnlyTaskContextItem(
                    ref = ref,
                    title = if (index == 0) "Dinner" else "Dentist",
                    dueDate = "11/08/2026",
                    dueTime = "6:00 PM",
                    isDone = false,
                    subtaskCount = 0,
                    unfinishedSubtaskCount = 0
                )
            }
            val snapshot = ReadOnlyTaskContextSnapshot(
                scope = TaskContextScope.RECENT_QUERY_RESULTS,
                generation = 4,
                items = items,
                truncated = false
            )
            val prompt = buildString {
                appendLine("Scope: RECENT_QUERY_RESULTS")
                appendLine("Generation: 4")
                appendLine("Items:")
                items.forEach { appendLine("{\"ref\":\"${it.ref}\"}") }
                append("Truncated: false")
            }
            return Capture(snapshot, prompt)
        }

        fun decision(
            route: String,
            reply: String = "",
            contextRef: String = "",
            contextDetail: String = "NONE",
            contextAction: String = "NONE"
        ): String = JSONObject()
            .put("route", route)
            .put("task_text", "")
            .put("reply", reply)
            .put("context_ref", contextRef)
            .put("context_detail", contextDetail)
            .put("context_action", contextAction)
            .put("setting_action", "NONE")
            .put("setting_target", "NONE")
            .put("query_reading_move", "NONE")
            .put("navigation_target", "NONE").put("query_presentation_hint", "NONE")
            .put("confidence", 0.97)
            .put("listen_again", true)
            .toString()
    }
}
