package com.example.myapplication.ai.agent

import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.TaskCommandContradictionDetector
import com.example.myapplication.ai.schema.AgentResponseSchemas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

class TaskActionConsistencyRepairTest {
    @Test
    fun cu05DiscardsCreateAndReextractsCompleteOriginalRequestExactlyOnce() = runBlocking {
        val text = "Move Read Book to 31 August at 8 PM."
        val client = Client(primary("CREATE_TASK", "read book").put("time", "20:00").toString())
        val command = orchestrator(client).process(text)
        assertEquals("RESCHEDULE_TASK", command.intent)
        assertEquals("read book", command.targetTaskTitle)
        assertEquals("31 August", command.newDateText)
        assertEquals("20:00", command.newTimeText)
        assertNull(command.taskTitle)
        assertNull(command.dateText)
        assertNull(command.timeText)
        assertNull(command.targetDateText)
        assertNull(command.targetTimeText)
        assertFalse(command.needsClarification)
        assertEquals(1, client.primaryCalls)
        assertEquals(1, client.repairCalls)
        assertEquals(text, client.repairText)
    }

    @Test
    fun movementVariantsUseFreshDateOrTimeExtraction() = runBlocking {
        listOf(
            Triple("Move Read Book to tomorrow", "tomorrow", ""),
            Triple("Reschedule Read Book for Friday at 8 PM", "Friday", "20:00"),
            Triple("Postpone Read Book until next week", "next week", ""),
            Triple("Move Read Book to 8 PM", "", "20:00"),
            Triple("Can you move Read Book over to tomorrow at 9 in the morning?", "tomorrow", "09:00")
        ).forEach { (text, date, time) ->
            val client = Client(primary("CREATE_TASK", "read book").toString(), repair(date, time).toString())
            val result = orchestrator(client).process(text)
            assertEquals("RESCHEDULE_TASK", result.intent)
            assertEquals(date.ifBlank { null }, result.newDateText)
            assertEquals(time.ifBlank { null }, result.newTimeText)
            assertEquals(1, client.repairCalls)
        }
    }

    @Test
    fun correctPrimaryActionsPreserveAllEvaluationBaselinesWithoutRepair() = runBlocking {
        listOf(
            Triple("Can you move Pay Rent over to 1 September at 9 in the morning?", "RESCHEDULE_TASK", "pay rent"),
            Triple("Remind me to buy milk on 27 August at 6 PM.", "CREATE_TASK", "buy milk"),
            Triple("Could you add Call Doctor for 28 August at 10 AM?", "CREATE_TASK", "call doctor"),
            Triple("I've finished Buy Milk.", "MARK_DONE", "buy milk"),
            Triple("I haven't finished Buy Milk after all.", "MARK_UNDONE", "buy milk"),
            Triple("Buy Milk is not complete yet", "MARK_UNDONE", "buy milk"),
            Triple("Get rid of Call Doctor.", "DELETE_TASK", "call doctor"),
            Triple("What's on my schedule tomorrow?", "QUERY_TASK", "")
        ).forEach { (text, action, title) ->
            val response = primary(action, title)
            if (action == "RESCHEDULE_TASK") response.put("new_date", "1 September").put("new_time", "09:00")
            val client = Client(response.toString())
            val result = orchestrator(client).process(text)
            assertEquals(action, result.intent)
            assertEquals(0, client.repairCalls)
            if (action == "CREATE_TASK") assertEquals(title, result.taskTitle)
            else if (action != "QUERY_TASK") assertEquals(title, result.targetTaskTitle)
        }
    }

    @Test
    fun creationAndUncertainMovementStructuresDoNotTriggerRescheduleRepair() = runBlocking {
        listOf(
            "Remind me to read a book tomorrow", "Create Read Book for Friday",
            "Add Read Book at 8 PM", "Schedule a new reading task for tomorrow",
            "Remind me to move Read Book to tomorrow", "Create Move Read Book for Friday",
            "Move it to tomorrow", "Move Read Book", "Move Read Book to the shelf",
            "Don't move Read Book to tomorrow", "How do I reschedule Read Book for Friday?",
            "Move a new task to tomorrow"
        ).forEach { text ->
            assertNull(text, TaskCommandContradictionDetector.scheduleChangeEvidence(text))
        }
        listOf("Remind me to read a book tomorrow", "Create Read Book for Friday",
            "Add Read Book at 8 PM", "Schedule a new reading task for tomorrow").forEach { text ->
            val client = Client(primary("CREATE_TASK", "read book").toString())
            assertEquals("CREATE_TASK", orchestrator(client).process(text).intent)
            assertEquals(0, client.repairCalls)
        }
    }

    @Test
    fun anyFailedRescheduleRepairFailsClosedAndNeverReturnsCreate() = runBlocking {
        listOf(
            "", "{", repair().toString() + " trailing",
            primary("CREATE_TASK", "read book").toString(),
            repair().put("action", "CREATE_TASK").toString(),
            repair().put("task_id", 42).toString(),
            repair().put("target_task_title", "").toString(),
            repair().put("target_task_title", 7).toString(),
            repair().put("confidence", 0.59).toString(),
            repair().put("confidence", 1.5).toString(),
            repair().put("confidence", "0.9").toString(),
            repair().put("need_clarification", true).toString(),
            repair().put("need_clarification", "false").toString(),
            repair(date = "").toString(), repair(time = "").toString()
        ).forEach { raw ->
            val client = Client(primary("CREATE_TASK", "read book").toString(), raw)
            val error = runCatching { orchestrator(client).process("Move Read Book to 31 August at 8 PM.") }
                .exceptionOrNull()
            assertTrue(raw, error is TaskAgentProcessingException)
            assertEquals(1, client.primaryCalls)
            assertEquals(1, client.repairCalls)
        }
        val client = Client(primary("CREATE_TASK", "read book").toString(), failure = IOException("timeout"))
        assertTrue(runCatching { orchestrator(client).process("Move Read Book to tomorrow") }
            .exceptionOrNull() is TaskAgentProcessingException)
        assertEquals(1, client.repairCalls)
        val cancelled = Client(primary("CREATE_TASK", "read book").toString(), failure = CancellationException())
        assertTrue(runCatching { orchestrator(cancelled).process("Move Read Book to tomorrow") }
            .exceptionOrNull() is CancellationException)
    }

    @Test
    fun repairedRescheduleStillUsesHomeMatcherAmbiguityAndProposalFlow() = runBlocking {
        val command = orchestrator(Client(primary("CREATE_TASK", "read book").toString()))
            .process("Move Read Book to 31 August at 8 PM.")
        assertEquals(AiIntent.RESCHEDULE_TASK.name, command.intent)
        val home = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val branch = home.substringAfter("AiIntent.${command.intent}.name -> {")
            .substringBefore("// to mark a task done")
        listOf("dao.getRootActiveTasks()", "TaskCompletionFilter.ACTIVE_ONLY",
            "extractSpokenTaskPhrase(aiResult, normalized)", "findTaskMatchResult(spokenPhrase, tasks)",
            "matchResult.isAmbiguous", "askTaskMatchClarification(", "PendingTaskAction.RESCHEDULE",
            "matchResult.bestTask != null", "ExecutionOutcome.NOT_FOUND",
            "putExtra(\"task_id\", matchedTask.id)", "putExtra(\"assistant_mode\", \"reschedule\")",
            "putExtra(\"prefill_new_date_text\", aiResult.newDateText ?: aiResult.dateText)",
            "putExtra(\"prefill_new_time_text\", aiResult.newTimeText ?: aiResult.timeText)"
        ).forEach { assertTrue(it, branch.contains(it)) }
        assertTrue(branch.indexOf("findTaskMatchResult(") < branch.indexOf("startActivity("))
        assertTrue(home.contains("TaskMatcher.findBestTaskMatch(spokenTitle, tasks)"))
        assertTrue(home.contains("if (normalized.contains(\"first\")) return firstTask"))
        assertTrue(home.contains("if (normalized.contains(\"second\")) return secondTask"))
        val undone = home.substringAfter("AiIntent.MARK_UNDONE.name -> {")
            .substringBefore("// for breakdown tasks")
        assertTrue(undone.contains("TaskCompletionFilter.COMPLETED_ONLY"))
        assertTrue(undone.contains("executeDeterministicTaskCompletion("))
        listOf("ai/agent/AgentOrchestrator.kt", "ai/agent/RescheduleRepairParser.kt",
            "ai/TaskCommandContradictionDetector.kt").forEach { path ->
            val source = File("src/main/java/com/example/myapplication/$path").readText()
            listOf("TaskMatcher", "AppDatabase", "taskDao", "startActivity", "EditTaskActivity")
                .forEach { assertFalse("$path: $it", source.contains(it)) }
        }
    }

    @Test
    fun repairWireSchemaHasNoActionChoiceOrUnrelatedAuthorityFields() {
        val schema = AgentResponseSchemas.rescheduleRepairResponseFormat()
            .getJSONObject("json_schema").getJSONObject("schema")
        assertFalse(schema.getBoolean("additionalProperties"))
        assertEquals(setOf("target_task_title", "new_date", "new_time", "confidence", "need_clarification"),
            schema.getJSONObject("properties").keys().asSequence().toSet())
        assertEquals(5, schema.getJSONArray("required").length())
    }

    private fun orchestrator(client: Client) = AgentOrchestrator(
        client, TaskAgentResponseParser(), TaskActionNormalizer(), ActionValidator()
    )

    private class Client(val primary: String, val repaired: String = repair().toString(), val failure: Exception? = null) : LaptopAgentClient(null) {
        var primaryCalls = 0
        var repairCalls = 0
        var repairText = ""
        override suspend fun process(normalizedText: String): String {
            primaryCalls++
            return primary
        }
        override suspend fun processRescheduleRepair(normalizedText: String): String {
            repairCalls++
            repairText = normalizedText
            failure?.let { throw it }
            return repaired
        }
    }

    companion object {
        private fun repair(date: String = "31 August", time: String = "20:00") = JSONObject()
            .put("target_task_title", "read book").put("new_date", date).put("new_time", time)
            .put("confidence", 0.95).put("need_clarification", false)

        private fun primary(action: String, title: String) = JSONObject().apply {
            put("action", action)
            listOf("natural_response", "date", "time", "target_date", "target_time", "new_date",
                "new_time", "recurrence", "priority").forEach { put(it, "") }
            put("task_title", if (action == "CREATE_TASK") title else "")
            put("target_task_title", if (action == "CREATE_TASK") "" else title)
            put("query_presentation", "NONE")
            put("breakdown_target_preference", "AUTO")
            put("confidence", 0.9)
            put("need_clarification", false)
            put("requires_confirmation", action == "DELETE_TASK")
            put("missing_fields", JSONArray())
            put("plan", JSONArray())
        }
    }
}
