package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.TaskCommandContradictionDetector
import com.example.myapplication.ai.TaskQueryPresentation
import com.example.myapplication.ai.TaskQueryDetail
import com.example.myapplication.ai.schema.AgentResponseSchemas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConversationTaskRouteConsistencyTest {
    @Test
    fun freshNamedScheduleReadsUseOneCompactRepairWithOnlyTheCompleteOriginalText() = runBlocking {
        listOf("When is Read Book?", "What time is Read Book?", "What date is Read Book?").forEach { text ->
            listOf("" to "TIME", "T1" to "TIME", "Read Book" to "TIME", "T1" to "NONE").forEach { (invalidRef, detail) ->
                val client = Client(ConversationRoute.CONTEXT_READ,
                    contextRef = invalidRef, contextDetail = detail)
                val result = orchestrator(client).process(text, "private app fact 987654",
                    "Scope: NONE\nItems: []",
                    ConversationContextFocus(available = false, ref = "", generation = 4,
                        detail = ConversationContextDetail.NONE, title = "private old title"))
                assertTaskCommand(text, result)
                assertEquals(1, client.primaryCalls)
                assertEquals(1, client.compactCalls)
                assertEquals(0, client.generalCalls)
                assertEquals(text, client.repairText)
                assertEquals("NAMED_TASK_QUERY_MISROUTED", client.failure)
                assertEquals(ConversationRoute.CONTEXT_READ, client.failedRoute)
                val repairInputs = listOf(client.repairText, client.failure, client.failedRoute?.name).joinToString()
                listOf("987654", "private", "T1", "T2", "Scope", "Items").forEach {
                    assertFalse(repairInputs.contains(it))
                }
            }
        }
    }

    @Test
    fun scheduleReadEvidenceAbstainsOnPronounsOrdinalsGenericAndCompoundRequests() {
        assertEquals(TaskQueryDetail.DATE_TIME,
            TaskCommandContradictionDetector.namedScheduleReadEvidence("when is read book")?.expectedDetail)
        assertEquals(TaskQueryDetail.TIME,
            TaskCommandContradictionDetector.namedScheduleReadEvidence("What time is Read Book?")?.expectedDetail)
        assertEquals(TaskQueryDetail.DATE,
            TaskCommandContradictionDetector.namedScheduleReadEvidence("What date is Read Book?")?.expectedDetail)
        val qualified = requireNotNull(TaskCommandContradictionDetector.namedScheduleReadEvidence(
            "What time is Read Book on 31 August at 9 PM?"
        ))
        assertEquals(TaskQueryDetail.TIME, qualified.expectedDetail)
        assertTrue(qualified.hasTargetDate)
        assertTrue(qualified.hasTargetTime)
        listOf("When is it?", "What time is that?", "What date is the task?",
            "What time is the second one?", "When is the first task?", "When is T2?",
            "When is the next one?", "When is my day?", "When is anything?",
            "When is the task tomorrow?", "When is she?", "What time is today?",
            "When is Read Book and delete it", "Move Read Book to tomorrow",
            "Can I ask when Read Book is scheduled?"
        ).forEach { assertNull(it, TaskCommandContradictionDetector.namedScheduleReadEvidence(it)) }
    }

    @Test
    fun contextlessPronounNeverInventsANamedTargetOrUsesNamedRepair() = runBlocking {
        val client = Client(ConversationRoute.CONTEXT_READ, contextDetail = "DATE_TIME",
            generalRepair = clarification())
        val result = orchestrator(client).process("When is it?", "")
        assertEquals(ConversationRoute.ASK_CLARIFICATION, result.route)
        assertEquals("", result.taskText)
        assertEquals("", result.contextRef)
        assertEquals(0, client.compactCalls)
        assertEquals(1, client.generalCalls)
    }

    @Test
    fun suppliedOrdinalContextReadsAndCorrectNamedTaskCommandsDoNotRepair() = runBlocking {
        listOf(Triple("What time is the second one?", "T2", "TIME"),
            Triple("When is the first one?", "T1", "DATE_TIME")).forEach { (text, ref, detail) ->
            val client = Client(ConversationRoute.CONTEXT_READ, contextRef = ref, contextDetail = detail)
            val items = if (ref == "T1") "{\"ref\":\"T1\"}" else "{\"ref\":\"T1\"}\n{\"ref\":\"T2\"}"
            val result = orchestrator(client).process(text, "Interaction: QUERY_PAGE",
                "Scope: RECENT_QUERY_RESULTS\nGeneration: 4\nItems:\n$items")
            assertEquals(ConversationRoute.CONTEXT_READ, result.route)
            assertEquals(ref, result.contextRef)
            assertEquals(ConversationContextDetail.valueOf(detail), result.contextDetail)
            assertEquals(0, client.compactCalls)
            assertEquals(0, client.generalCalls)
        }
        val client = Client(ConversationRoute.TASK_COMMAND)
        val result = orchestrator(client).process("When is Read Book?", "")
        assertEquals(ConversationRoute.TASK_COMMAND, result.route)
        assertEquals("When is Read Book?", result.taskText)
        assertEquals(0, client.compactCalls)
        assertEquals(0, client.generalCalls)
    }

    @Test
    fun namedGuardDoesNotOverrideSuppliedRefsOrAvailableValidatedFocus() = runBlocking {
        val withRefs = Client(ConversationRoute.CONTEXT_READ, contextRef = "T9", contextDetail = "TIME",
            generalRepair = clarification())
        orchestrator(withRefs).process("What time is Read Book?", "", "Items: [{\"ref\":\"T1\"}]")
        assertEquals(0, withRefs.compactCalls)
        assertEquals(1, withRefs.generalCalls)
        val withFocus = Client(ConversationRoute.CONTEXT_READ, contextDetail = "TIME",
            generalRepair = clarification())
        orchestrator(withFocus).process("What time is Read Book?", "",
            contextFocus = ConversationContextFocus(available = true, ref = "T1", generation = 4,
                detail = ConversationContextDetail.SUMMARY, title = "Read Book"))
        assertEquals(0, withFocus.compactCalls)
        assertEquals(1, withFocus.generalCalls)
    }

    @Test
    fun namedReadRepairMayClarifyButNeverRetriesASecondTimeOrAcceptsContextAuthority() = runBlocking {
        val clarify = Client(ConversationRoute.CONTEXT_READ, compact("ASK_CLARIFICATION", "Which task?"),
            contextDetail = "TIME")
        val result = orchestrator(clarify).process("What time is Read Book?", "")
        assertEquals(ConversationRoute.ASK_CLARIFICATION, result.route)
        assertEquals("", result.taskText)
        assertEquals("", result.contextRef)
        assertEquals(1, clarify.compactCalls)
        listOf(compact("CONTEXT_READ"), JSONObject(compact()).put("context_ref", "T1").toString(),
            compact("TASK_COMMAND", "Read Book is at 7 PM"), "{"
        ).forEach { repair ->
            val client = Client(ConversationRoute.CONTEXT_READ, repair, contextDetail = "TIME")
            assertTrue(runCatching { orchestrator(client).process("What time is Read Book?", "") }
                .exceptionOrNull() is ConversationOrchestratorException)
            assertEquals(1, client.compactCalls)
            assertEquals(0, client.generalCalls)
        }
    }

    @Test
    fun promptsContrastNamedScheduleReadsWithSuppliedContextGrounding() {
        val prompt = ConversationAgentClient.ROUTING_SYSTEM_PROMPT
        listOf("When is Read Book?", "What time is Read Book?", "What date is Read Book?").forEach {
            assertTrue(prompt.contains("\"$it\" -> TASK_COMMAND"))
            assertTrue(ConversationAgentClient.TASK_COMMAND_ROUTE_REPAIR_SYSTEM_PROMPT.contains("\"$it\" -> TASK_COMMAND"))
        }
        assertTrue(prompt.contains("The distinction is target grounding, not the word \"when\""))
        assertTrue(prompt.contains("\"When is the first task?\" -> CONTEXT_READ DATE_TIME"))
        assertTrue(prompt.contains("\"What time is the second one?\" -> CONTEXT_READ TIME"))
        assertTrue(prompt.contains("\"When is it?\" with no task context needs ASK_CLARIFICATION"))
    }

    @Test
    fun namedRenamesRepairContradictoryRoutesWithCompleteOriginalText() = runBlocking {
        listOf(
            "I want the rent payment to be called Pay Rent instead.",
            "Rename Rent Payment to Pay Rent.",
            "Change the name of Rent Payment to Pay Rent.",
            "Change Rent Payment's name to Pay Rent."
        ).forEach { text ->
            listOf(ConversationRoute.DIRECT_REPLY, ConversationRoute.ASK_CLARIFICATION,
                ConversationRoute.CONTEXT_AWARE_SUGGESTION, ConversationRoute.DAILY_BRIEFING
            ).forEach { route ->
                val client = Client(route)
                assertTaskCommand(text, orchestrator(client).process(text, ""))
                assertEquals("NAMED_RENAME_MUTATION_MISROUTED", client.failure)
                assertEquals(route, client.failedRoute)
                assertEquals(text, client.repairText)
                assertEquals(1, client.compactCalls)
                assertEquals(0, client.generalCalls)
            }
        }
    }

    @Test
    fun renameQuestionsCreationAndDeicticRequestsNeverInventNamedTargets() = runBlocking {
        listOf("Can I rename tasks?", "How do I rename a task?", "What does rename do?",
            "I want to create a task called Pay Rent.", "Create a task named Pay Rent.",
            "Call Doctor", "Rename it to Pay Rent.", "Rename Rent Payment to",
            "Rename the task to Pay Rent", "Rename Rent Payment to it",
            "I want to create a task to be called Pay Rent instead",
            "I want Rent Payment to be called instead",
            "Rename Rent Payment to Pay Rent and move it to tomorrow"
        ).forEach { text ->
            assertFalse(text, TaskCommandContradictionDetector.isNamedTaskRename(text))
            val route = if (text.contains("create", true)) ConversationRoute.TASK_COMMAND
                else ConversationRoute.DIRECT_REPLY
            val client = Client(route)
            val decision = orchestrator(client).process(text, "")
            assertEquals(route, decision.route)
            assertEquals(0, client.compactCalls)
            assertEquals("", decision.contextRef)
        }
        val text = "Rename Rent Payment to Pay Rent."
        val correct = Client(ConversationRoute.TASK_COMMAND)
        assertEquals(text, orchestrator(correct).process(text, "").taskText)
        assertEquals(0, correct.compactCalls)
    }

    @Test
    fun renamePromptContrastsAndBoundedContractAreExplicit() {
        val prompt = ConversationAgentClient.ROUTING_SYSTEM_PROMPT
        assertTrue(prompt.contains("\"I want the rent payment to be called Pay Rent instead.\" -> TASK_COMMAND"))
        assertTrue(prompt.contains("\"Can I rename tasks?\" -> DIRECT_REPLY"))
        assertTrue(prompt.contains("\"How do I rename a task?\" -> DIRECT_REPLY"))
        assertTrue(prompt.contains("TASK_COMMAND for creation, not rename"))
        assertTrue(prompt.contains("Current user command semantics take precedence over prior conversational memory"))
        val repair = ConversationAgentClient.TASK_COMMAND_ROUTE_REPAIR_SYSTEM_PROMPT
        assertTrue(repair.contains("existing target and replacement title"))
        assertTrue(repair.contains("\"Rename Rent Payment to Pay Rent\" -> TASK_COMMAND"))
        assertTrue(repair.contains("Return only move, reply, confidence"))
    }

    @Test
    fun differentDateTaskQueriesUseOneCompactRepair() = runBlocking {
        listOf(
            "Do I have anything planned for 28 August?",
            "What tasks do I have on 30 August?",
            "How many tasks do I have tomorrow?",
            "Do I have any tasks tomorrow?",
            "Show me my tasks for Friday",
            "List my tasks next week"
        ).forEach { text ->
            val client = Client(ConversationRoute.DAILY_BRIEFING)
            val decision = orchestrator(client).process(text, "private app data",
                "Items: [{\"ref\":\"T1\",\"title\":\"private task\"}]")
            assertTaskCommand(text, decision)
            assertEquals(1, client.primaryCalls)
            assertEquals(1, client.compactCalls)
            assertEquals(0, client.generalCalls)
            assertEquals(text, client.repairText)
            assertEquals("TEMPORAL_TASK_QUERY_MISROUTED", client.failure)
            assertEquals(ConversationRoute.DAILY_BRIEFING, client.failedRoute)
        }
    }

    @Test
    fun namedCompletionCorrectionsRepairAllThreeSpecialRoutes() = runBlocking {
        listOf(
            "I haven't finished Buy Milk after all",
            "I haven’t finished Buy Milk after all",
            "Buy Milk isn't finished after all",
            "Buy Milk is not complete yet",
            "Mark Buy Milk incomplete again",
            "Reopen Buy Milk"
        ).forEach { text ->
            listOf(ConversationRoute.CONTEXT_AWARE_SUGGESTION,
                ConversationRoute.DAILY_BRIEFING, ConversationRoute.DIRECT_REPLY).forEach { route ->
                val client = Client(route)
                assertTaskCommand(text, orchestrator(client).process(text, ""))
                assertEquals(1, client.compactCalls)
                assertEquals(0, client.generalCalls)
                assertEquals("NAMED_COMPLETION_MUTATION_MISROUTED", client.failure)
                assertEquals(route, client.failedRoute)
            }
        }
    }

    @Test
    fun legitimateRoutesAndAlreadyCorrectCommandsDoNotRepair() = runBlocking {
        listOf(
            "Give me my daily briefing" to ConversationRoute.DAILY_BRIEFING,
            "Brief me for the day" to ConversationRoute.DAILY_BRIEFING,
            "Help me review my day" to ConversationRoute.DAILY_BRIEFING,
            "What's on my schedule tomorrow?" to ConversationRoute.TASK_COMMAND,
            "Do I have anything planned for 28 August?" to ConversationRoute.TASK_COMMAND,
            "I haven't finished Buy Milk after all" to ConversationRoute.TASK_COMMAND,
            "What should I do next?" to ConversationRoute.CONTEXT_AWARE_SUGGESTION,
            "What should I focus on?" to ConversationRoute.CONTEXT_AWARE_SUGGESTION,
            "How should I make progress today?" to ConversationRoute.CONTEXT_AWARE_SUGGESTION,
            "Are any tasks scheduled too close together?" to ConversationRoute.CONTEXT_AWARE_SUGGESTION,
            "I don't feel productive today" to ConversationRoute.DIRECT_REPLY,
            "It's not finished after all" to ConversationRoute.ASK_CLARIFICATION
        ).forEach { (text, route) ->
            val client = Client(route)
            val result = orchestrator(client).process(text, "")
            assertEquals(route, result.route)
            assertEquals(0, client.compactCalls)
            assertEquals(0, client.generalCalls)
            if (route == ConversationRoute.TASK_COMMAND) assertEquals(text, result.taskText)
            else assertEquals("", result.contextRef)
        }
    }

    @Test
    fun deicticAndNegativeConversationDoNotSupplyNamedEvidence() {
        listOf("It's not finished after all", "It is not complete yet", "Reopen it",
            "Mark that task incomplete again", "I haven't finished my work yet",
            "I haven't finished anything yet", "I am not happy with Buy Milk",
            "I haven't finished yet", "I haven't finished after all",
            "I haven't finished all my tasks yet", "Reopen please",
            "What if Buy Milk is not complete yet", "Don't reopen Buy Milk"
        ).forEach { assertFalse(it, TaskCommandContradictionDetector.isNamedCompletionReversal(it)) }
    }

    @Test
    fun compactClarificationIsReconstructedWithoutTaskAuthority() = runBlocking {
        val client = Client(ConversationRoute.DIRECT_REPLY, compact("ASK_CLARIFICATION", "Which task?"))
        val decision = orchestrator(client).process("Reopen Buy Milk", "")
        assertEquals(ConversationRoute.ASK_CLARIFICATION, decision.route)
        assertEquals("Which task?", decision.reply)
        assertEquals("", decision.taskText)
        assertEquals("", decision.contextRef)
        assertEquals(ConversationContextAction.NONE, decision.contextAction)
        assertEquals(1, client.compactCalls)
    }

    @Test
    fun invalidOrFailedRepairNeverFallsBackOrRetriesAgain() = runBlocking {
        listOf("", "{", compact("DAILY_BRIEFING"),
            compact("TASK_COMMAND", "Task reopened"),
            JSONObject(compact()).put("context_ref", "T1").toString(),
            JSONObject(compact()).put("confidence", 0.1).toString()
        ).forEach { raw ->
            val client = Client(ConversationRoute.DAILY_BRIEFING, raw)
            val failure = runCatching {
                orchestrator(client).process("Do I have anything planned for 28 August?", "")
            }.exceptionOrNull()
            assertTrue(failure is ConversationOrchestratorException)
            assertEquals(1, client.compactCalls)
            assertEquals(0, client.generalCalls)
        }
        val client = Client(ConversationRoute.DIRECT_REPLY, error = java.io.IOException("timeout"))
        assertTrue(runCatching { orchestrator(client).process("Reopen Buy Milk", "") }
            .exceptionOrNull() is ConversationOrchestratorException)
        assertEquals(1, client.compactCalls)
        val cancelled = Client(ConversationRoute.DIRECT_REPLY, error = CancellationException())
        assertTrue(runCatching { orchestrator(cancelled).process("Reopen Buy Milk", "") }
            .exceptionOrNull() is CancellationException)
    }

    @Test
    fun compactSchemaAllowsOnlyRoutingMoveReplyAndConfidence() {
        val schema = AgentResponseSchemas.taskCommandRouteRepairResponseFormat()
            .getJSONObject("json_schema").getJSONObject("schema")
        assertFalse(schema.getBoolean("additionalProperties"))
        val properties = schema.getJSONObject("properties")
        assertEquals(setOf("move", "reply", "confidence"), properties.keys().asSequence().toSet())
        val moves = properties.getJSONObject("move").getJSONArray("enum")
        assertEquals(listOf("TASK_COMMAND", "ASK_CLARIFICATION"),
            (0 until moves.length()).map(moves::getString))
    }

    private fun assertTaskCommand(text: String, result: ConversationDecision) {
        assertEquals(ConversationRoute.TASK_COMMAND, result.route)
        assertEquals(text, result.taskText)
        assertEquals("", result.reply)
        assertEquals("", result.contextRef)
        assertEquals(ConversationContextDetail.NONE, result.contextDetail)
        assertEquals(ConversationContextAction.NONE, result.contextAction)
        assertEquals(ConversationSettingAction.NONE, result.settingAction)
        assertEquals(ConversationSettingTarget.NONE, result.settingTarget)
        assertEquals(ConversationQueryReadingMove.NONE, result.queryReadingMove)
        assertEquals(TaskQueryPresentation.NONE, result.queryPresentationHint)
        assertTrue(result.listenAgain)
        assertEquals("conversation_agent_schema_repair", result.source)
    }

    private fun orchestrator(client: Client) = ConversationOrchestrator(client, ConversationDecisionParser())

    private class Client(
        val route: ConversationRoute,
        val repair: String = compact(),
        val error: Exception? = null,
        val contextRef: String = "",
        val contextDetail: String = "NONE",
        val generalRepair: String? = null
    ) : ConversationAgentClient(null) {
        var primaryCalls = 0
        var compactCalls = 0
        var generalCalls = 0
        var repairText = ""
        var failure = ""
        var failedRoute: ConversationRoute? = null

        override suspend fun process(userText: String, memorySnapshot: String, appContextSummary: String): String {
            primaryCalls++
            return JSONObject().put("route", route.name).put("task_text", "discarded")
                .put("reply", "Which task?").put("context_ref", contextRef)
                .put("context_detail", contextDetail).put("context_action", "NONE")
                .put("setting_action", "NONE").put("setting_target", "NONE")
                .put("query_reading_move", "NONE").put("query_presentation_hint", "NONE")
                .put("confidence", 0.97).put("listen_again", true).toString()
        }

        override suspend fun processTaskCommandRouteRepair(
            normalizedText: String, failureCode: String, failedRoute: ConversationRoute
        ): String {
            compactCalls++
            repairText = normalizedText
            failure = failureCode
            this.failedRoute = failedRoute
            error?.let { throw it }
            return repair
        }

        override suspend fun processRepair(userText: String, appContextSummary: String): String {
            generalCalls++
            return generalRepair ?: error("Unrestricted repair must not run")
        }
    }

    companion object {
        private fun clarification() = JSONObject()
            .put("route", "ASK_CLARIFICATION").put("task_text", "").put("reply", "Which task do you mean?")
            .put("context_ref", "").put("context_detail", "NONE").put("context_action", "NONE")
            .put("setting_action", "NONE").put("setting_target", "NONE")
            .put("query_reading_move", "NONE").put("query_presentation_hint", "NONE")
            .put("confidence", 0.97).put("listen_again", true).toString()

        private fun compact(move: String = "TASK_COMMAND", reply: String = "") = JSONObject()
            .put("move", move).put("reply", reply).put("confidence", 0.97).toString()
    }
}
