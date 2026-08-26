package com.example.myapplication.ai.agent

import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.AiParsedCommand
import com.example.myapplication.ai.TaskCommandContradictionDetector
import com.example.myapplication.ai.schema.AgentResponseSchemas
import com.example.myapplication.isTitlePrefillChanged
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
    fun correctNamedRenameKeepsBothTitlesWithoutRepair() = runBlocking {
        val client = Client(primary("UPDATE_TASK", "rent payment").put("task_title", "Pay Rent").toString())
        assertRename(orchestrator(client).process(CU04))
        assertEquals(0, client.renameRepairCalls)
        assertEquals(0, client.repairCalls)
    }

    @Test
    fun namedRenameContradictionsReextractOnceWithoutAcceptingPrimaryFields() = runBlocking {
        val invalidPrimaries = mutableListOf(
            primary("CREATE_TASK", "Pay Rent"),
            primary("UPDATE_TASK", "rent payment"),
            primary("UPDATE_TASK", "").put("task_title", "Pay Rent"),
            primary("DELETE_TASK", "rent payment"),
            primary("UNKNOWN", "rent payment")
        )
        listOf("date", "time", "target_date", "target_time", "new_date", "new_time",
            "recurrence", "priority").forEach { field ->
            invalidPrimaries += primary("UPDATE_TASK", "rent payment").put("task_title", "Pay Rent")
                .put(field, when (field) { "recurrence" -> "DAILY"; "priority" -> "HIGH"; else -> "tomorrow" })
        }
        invalidPrimaries.forEach { primary ->
            val client = Client(primary.toString())
            assertRename(orchestrator(client).process(CU04))
            assertEquals(CU04, client.renameRepairText)
            assertEquals(1, client.primaryCalls)
            assertEquals(1, client.renameRepairCalls)
            assertEquals(0, client.repairCalls)
        }
    }

    @Test
    fun failedRenameRepairNeverFallsBackToCreateOrIncompleteUpdate() = runBlocking {
        val invalidRepairs = listOf("", "{", renameRepair().toString() + " trailing",
            renameRepair().put("confidence", 0.79).toString(),
            renameRepair().put("confidence", 1.1).toString(),
            renameRepair().put("confidence", "0.95").toString(),
            renameRepair().put("target_task_title", "").toString(),
            renameRepair().put("replacement_title", " ").toString(),
            renameRepair().put("replacement_title", 7).toString(),
            renameRepair().put("need_clarification", true).toString(),
            renameRepair().put("need_clarification", "false").toString(),
            renameRepair().put("task_id", 12).toString(),
            renameRepair().put("new_date", "tomorrow").toString(),
            renameRepair().put("action", "CREATE_TASK").toString())
        listOf(primary("CREATE_TASK", "Pay Rent"), primary("UPDATE_TASK", "rent payment"))
            .forEach { primary -> invalidRepairs.forEach { raw ->
                val client = Client(primary.toString(), renameRepaired = raw)
                assertTrue(runCatching { orchestrator(client).process(CU04) }.exceptionOrNull()
                    is TaskAgentProcessingException)
                assertEquals(1, client.renameRepairCalls)
                assertEquals(0, client.repairCalls)
            } }
        val timeout = Client(primary("CREATE_TASK", "Pay Rent").toString(), failure = IOException("timeout"))
        assertTrue(runCatching { orchestrator(timeout).process(CU04) }.exceptionOrNull() is TaskAgentProcessingException)
        assertEquals(1, timeout.renameRepairCalls)
        val cancelled = Client(primary("CREATE_TASK", "Pay Rent").toString(), failure = CancellationException())
        assertTrue(runCatching { orchestrator(cancelled).process(CU04) }.exceptionOrNull() is CancellationException)
    }

    @Test
    fun repairedRenameUsesExistingHomeMatcherAndEditTitleConfirmation() = runBlocking {
        val command = orchestrator(Client(primary("CREATE_TASK", "Pay Rent").toString())).process(CU04)
        assertRename(command)
        assertTrue(isTitlePrefillChanged(command.taskTitle, "rent payment"))
        val home = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val branch = home.substringAfter("AiIntent.UPDATE_TASK.name -> {")
            .substringBefore("// reschedule task")
        listOf("findTaskMatchResult(spokenPhrase, tasks)", "matchResult.isAmbiguous",
            "askTaskMatchClarification(", "proposedTitle = aiResult.taskTitle",
            "ExecutionOutcome.NOT_FOUND", "TaskCompletionFilter.ACTIVE_ONLY",
            "putExtra(\"task_id\", matchedTask.id)", "putExtra(\"task_title\", matchedTask.title)",
            "putExtra(\"prefill_title\", aiResult.taskTitle)", "putExtra(\"opened_by_assistant\", true)"
        ).forEach { assertTrue(it, branch.contains(it)) }
        val phrase = home.substringAfter("private fun extractSpokenTaskPhrase(")
        assertTrue(phrase.contains("return aiResult.targetTaskTitle"))
        assertTrue(phrase.indexOf("aiResult.targetTaskTitle") < phrase.indexOf("aiResult.taskTitle"))
        assertTrue(home.contains("TaskMatcher.findBestTaskMatch(spokenTitle, tasks)"))
        val edit = File("src/main/java/com/example/myapplication/EditTaskActivity.kt").readText()
        assertTrue(edit.contains("etTaskTitle.setText(prefillTitle)"))
        assertTrue(edit.contains("val hasPendingPrefillChange = titlePrefillChanged || temporalChanged"))
        assertTrue(edit.contains("hasPendingPrefillChange -> askToSaveChanges()"))
        assertTrue(edit.contains("if (dateText.isNullOrBlank() && timeText.isNullOrBlank()) return false"))
        assertTrue(edit.contains("waitingForSaveConfirmation = true"))
        listOf("ai/agent/TitleRenameRepairParser.kt", "ai/agent/AgentOrchestrator.kt").forEach { path ->
            val source = File("src/main/java/com/example/myapplication/$path").readText()
            listOf("TaskMatcher", "taskDao", "AppDatabase", "startActivity", "EditTaskActivity")
                .forEach { assertFalse(source.contains(it)) }
        }
    }

    @Test
    fun renameSchemaAndPromptSeparateExistingAndReplacementTitles() {
        val schema = AgentResponseSchemas.titleRenameRepairResponseFormat()
            .getJSONObject("json_schema").getJSONObject("schema")
        assertEquals(setOf("target_task_title", "replacement_title", "confidence", "need_clarification"),
            schema.getJSONObject("properties").keys().asSequence().toSet())
        assertEquals(4, schema.getJSONArray("required").length())
        assertFalse(schema.getBoolean("additionalProperties"))
        val prompt = LaptopAgentClient.SYSTEM_PROMPT.replace(Regex("\\s+"), " ")
        assertTrue(prompt.contains("For UPDATE_TASK, put the existing task name in target_task_title"))
        assertTrue(prompt.contains("put ONLY the new replacement title in task_title"))
        assertTrue(prompt.contains("For RESCHEDULE_TASK, DELETE_TASK, MARK_DONE, and MARK_UNDONE, put the existing task name in target_task_title and keep task_title empty"))
        assertFalse(prompt.contains("For RESCHEDULE_TASK, UPDATE_TASK, DELETE_TASK"))
        assertTrue(prompt.contains("target_task_title=\"rent payment\", task_title=\"Pay Rent\", new_date=\"\", new_time=\"\""))
        assertTrue(prompt.contains("For a pure title rename, leave date, time, target_date, target_time, new_date, new_time, recurrence, and priority empty"))
    }

    private fun assertRename(command: AiParsedCommand) {
        assertEquals("UPDATE_TASK", command.intent)
        assertEquals("rent payment", command.targetTaskTitle)
        assertEquals("Pay Rent", command.taskTitle)
        assertNull(command.newDateText)
        assertNull(command.newTimeText)
        assertNull(command.dateText)
        assertNull(command.timeText)
        assertNull(command.targetDateText)
        assertNull(command.targetTimeText)
        assertNull(command.recurrence)
        assertNull(command.priority)
        assertFalse(command.needsClarification)
    }

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
        assertEquals(0, client.renameRepairCalls)
        assertEquals(text, client.repairText)
    }

    @Test
    fun cu05PhysicalRescheduleRoleFailureReextractsCompleteRequestOnce() = runBlocking {
        val text = "Move Read Book to 31 August at 8 PM."
        val malformed = primary("RESCHEDULE_TASK", "Read Book")
            .put("target_date", "31 August").put("target_time", "20:00")
        val client = Client(malformed.toString(),
            repair().put("target_task_title", "Read Book").toString())

        val command = orchestrator(client).process(text)

        assertEquals(1, client.primaryCalls)
        assertEquals(1, client.repairCalls)
        assertEquals(0, client.renameRepairCalls)
        assertEquals(text, client.repairText)
        assertEquals("RESCHEDULE_TASK", command.intent)
        assertEquals("Read Book", command.targetTaskTitle)
        assertNull(command.taskTitle)
        assertNull(command.targetDateText)
        assertNull(command.targetTimeText)
        assertEquals("31 August", command.newDateText)
        assertEquals("20:00", command.newTimeText)
    }

    @Test
    fun missingDestinationsAndUnsupportedTargetConstraintsRequireRepair() = runBlocking {
        val valid = primary("RESCHEDULE_TASK", "Read Book")
            .put("new_date", "31 August").put("new_time", "20:00")
        listOf(
            JSONObject(valid.toString()).put("new_date", ""),
            JSONObject(valid.toString()).put("new_time", ""),
            JSONObject(valid.toString()).put("new_date", "").put("target_date", "31 August"),
            JSONObject(valid.toString()).put("new_time", "").put("target_time", "20:00"),
            JSONObject(valid.toString()).put("target_date", "31 August"),
            JSONObject(valid.toString()).put("target_time", "20:00"),
            JSONObject(valid.toString()).put("target_date", "31 August").put("target_time", "20:00"),
            // Deliberately wrong primary values prove repair does not copy/swap them.
            primary("RESCHEDULE_TASK", "Read Book").put("target_date", "1 September")
                .put("target_time", "09:00")
        ).forEach { malformed ->
            val client = Client(malformed.toString())
            val command = orchestrator(client).process("Move Read Book to 31 August at 8 PM.")
            assertEquals(1, client.primaryCalls)
            assertEquals(1, client.repairCalls)
            assertEquals(0, client.renameRepairCalls)
            assertEquals("RESCHEDULE_TASK", command.intent)
            assertNull(command.targetDateText)
            assertNull(command.targetTimeText)
            assertEquals("31 August", command.newDateText)
            assertEquals("20:00", command.newTimeText)
        }
    }

    @Test
    fun correctTemporalRolesAndOptionalCurrentConstraintsNeedNoRepair() = runBlocking {
        listOf(
            "Move Read Book to 31 August at 8 PM." to primary("RESCHEDULE_TASK", "Read Book")
                .put("new_date", "31 August").put("new_time", "20:00"),
            "Reschedule tomorrow's appointment to Friday at 10 AM" to primary("RESCHEDULE_TASK", "appointment")
                .put("target_date", "tomorrow").put("new_date", "Friday").put("new_time", "10:00"),
            "Move the 8 PM Read Book task to 9 PM" to primary("RESCHEDULE_TASK", "Read Book")
                .put("target_time", "20:00").put("new_time", "21:00"),
            // Current schedule constraints remain optional, even when present in the utterance.
            "Reschedule tomorrow's appointment to Friday at 10 AM" to primary("RESCHEDULE_TASK", "appointment")
                .put("new_date", "Friday").put("new_time", "10:00"),
            "Move Read Book to tomorrow" to primary("RESCHEDULE_TASK", "Read Book").put("new_date", "tomorrow"),
            "Move Read Book to 8 PM" to primary("RESCHEDULE_TASK", "Read Book").put("new_time", "20:00")
        ).forEach { (text, response) ->
            val client = Client(response.toString())
            val command = orchestrator(client).process(text)
            assertEquals("RESCHEDULE_TASK", command.intent)
            assertEquals(response.getString("target_task_title"), command.targetTaskTitle)
            assertEquals(response.getString("target_date").ifBlank { null }, command.targetDateText)
            assertEquals(response.getString("target_time").ifBlank { null }, command.targetTimeText)
            assertEquals(response.getString("new_date").ifBlank { null }, command.newDateText)
            assertEquals(response.getString("new_time").ifBlank { null }, command.newTimeText)
            assertEquals(1, client.primaryCalls)
            assertEquals(0, client.repairCalls)
            assertEquals(0, client.renameRepairCalls)
        }
    }

    @Test
    fun scheduleEvidenceSeparatesTargetAndDestinationSegments() {
        val destinationOnly = requireNotNull(TaskCommandContradictionDetector.scheduleChangeEvidence(
            "Move Read Book to 31 August at 8 PM."))
        assertFalse(destinationOnly.hasTargetDate)
        assertFalse(destinationOnly.hasTargetTime)
        assertTrue(destinationOnly.hasDestinationDate)
        assertTrue(destinationOnly.hasDestinationTime)
        val targetDate = requireNotNull(TaskCommandContradictionDetector.scheduleChangeEvidence(
            "Reschedule tomorrow's appointment to Friday at 10 AM"))
        assertTrue(targetDate.hasTargetDate)
        assertFalse(targetDate.hasTargetTime)
        val targetTime = requireNotNull(TaskCommandContradictionDetector.scheduleChangeEvidence(
            "Move the 8 PM Read Book task to 9 PM"))
        assertFalse(targetTime.hasTargetDate)
        assertTrue(targetTime.hasTargetTime)
        assertFalse(targetTime.hasDestinationDate)
        assertTrue(targetTime.hasDestinationTime)
    }

    @Test
    fun reschedulePromptExplicitlySeparatesCurrentAndDestinationSchedules() {
        val prompt = LaptopAgentClient.SYSTEM_PROMPT.replace(Regex("\\s+"), " ")
        assertTrue(prompt.contains("target_date / target_time identify the CURRENT existing task"))
        assertTrue(prompt.contains("new_date / new_time are the requested DESTINATION"))
        assertTrue(prompt.contains("Never put the requested destination in target_date / target_time merely because it is the only temporal phrase"))
        assertTrue(prompt.contains("target_task_title=\"Read Book\", target_date=\"\", target_time=\"\", new_date=\"31 August\", new_time=\"20:00\""))
        assertTrue(prompt.contains("target_date=\"tomorrow\", target_time=\"\", new_date=\"Friday\", new_time=\"10:00\""))
        assertTrue(prompt.contains("target_time=\"20:00\", new_date=\"\", new_time=\"21:00\""))
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
            Triple("Create a task called Pay Rent.", "CREATE_TASK", "Pay Rent"),
            Triple("Update rent payment.", "UPDATE_TASK", "rent payment"),
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
            assertEquals(0, client.renameRepairCalls)
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
    fun anyFailedRescheduleRepairFailsClosedAndNeverReturnsRejectedPrimary() = runBlocking {
        val invalidRepairs = listOf(
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
        )
        val rejectedPrimaries = listOf(
            primary("CREATE_TASK", "read book").toString(),
            primary("RESCHEDULE_TASK", "Read Book")
                .put("target_date", "31 August").put("target_time", "20:00").toString()
        )
        rejectedPrimaries.forEach { rejected ->
            invalidRepairs.forEach { raw ->
                val client = Client(rejected, raw)
                val error = runCatching {
                    orchestrator(client).process("Move Read Book to 31 August at 8 PM.")
                }.exceptionOrNull()
                assertTrue(raw, error is TaskAgentProcessingException)
                assertEquals(1, client.primaryCalls)
                assertEquals(1, client.repairCalls)
            }
            val client = Client(rejected, failure = IOException("timeout"))
            assertTrue(runCatching { orchestrator(client).process("Move Read Book to tomorrow") }
                .exceptionOrNull() is TaskAgentProcessingException)
            assertEquals(1, client.repairCalls)
            val cancelled = Client(rejected, failure = CancellationException())
            assertTrue(runCatching { orchestrator(cancelled).process("Move Read Book to tomorrow") }
                .exceptionOrNull() is CancellationException)
        }
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

    private class Client(val primary: String, val repaired: String = repair().toString(), val failure: Exception? = null,
        val renameRepaired: String = renameRepair().toString()) : LaptopAgentClient(null) {
        var primaryCalls = 0
        var repairCalls = 0
        var repairText = ""
        var renameRepairCalls = 0
        var renameRepairText = ""
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
        override suspend fun processTitleRenameRepair(normalizedText: String): String {
            renameRepairCalls++
            renameRepairText = normalizedText
            failure?.let { throw it }
            return renameRepaired
        }
    }

    companion object {
        private const val CU04 = "I want the rent payment to be called Pay Rent instead."
        private fun renameRepair() = JSONObject().put("target_task_title", "rent payment")
            .put("replacement_title", "Pay Rent").put("confidence", 0.95).put("need_clarification", false)
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
