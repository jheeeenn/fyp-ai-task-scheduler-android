package com.example.myapplication.ai.agent

import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.AiParsedCommand
import com.example.myapplication.ai.TaskQueryPresentation
import com.example.myapplication.ai.conversation.ConversationAgentClient
import com.example.myapplication.ai.conversation.ExecutionObservation
import com.example.myapplication.ai.conversation.ExecutionOperation
import com.example.myapplication.ai.conversation.ExecutionOutcome
import com.example.myapplication.ai.conversation.ResponseVerbalizationContract
import com.example.myapplication.ai.conversation.ResponseVerbalizationPlan
import com.example.myapplication.ai.conversation.ResponseVerbalizationPlanner
import com.example.myapplication.ai.conversation.ResponseVerbalizationTone
import com.example.myapplication.ai.conversation.ResponseVerbalizationVerbosity
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GenericCreateTaskDraftHandoffTest {
    private val validator = ActionValidator()

    @Test
    fun createTaskWithOrWithoutTitleIsAccepted() {
        val specific = AiParsedCommand(
            intent = AiIntent.CREATE_TASK.name,
            taskTitle = "buy milk",
            dateText = "tomorrow",
            newDateText = "tomorrow",
            confidence = 0.97f
        )
        val generic = AiParsedCommand(
            intent = AiIntent.CREATE_TASK.name,
            confidence = 1.0f
        )

        assertEquals(specific, validator.validate(specific))
        assertEquals(generic, validator.validate(generic))
    }

    @Test
    fun genericStructuredResultPassesTheTaskAgentPipeline() = runBlocking {
        val agent = AgentOrchestrator(
            laptopAgentClient = object : LaptopAgentClient(null) {
                override suspend fun process(normalizedText: String): String =
                    createResponse(taskTitle = "")
            },
            taskAgentResponseParser = TaskAgentResponseParser(),
            taskActionNormalizer = TaskActionNormalizer(),
            actionValidator = ActionValidator()
        )

        val command = agent.process("create a task for me")

        assertEquals(AiIntent.CREATE_TASK.name, command.intent)
        assertNull(command.taskTitle)
        assertNull(command.targetTaskTitle)
        assertNull(command.newDateText)
        assertNull(command.newTimeText)
        assertFalse(command.needsClarification)
        assertTrue(command.missingFields.isEmpty())
    }

    @Test
    fun operationsThatRequireAuthoritativeTargetsStillRejectBlankTargets() {
        assertThrows(TaskAgentValidationException::class.java) {
            validator.validate(
                AiParsedCommand(
                    intent = AiIntent.BREAKDOWN_TASK.name,
                    confidence = 0.97f
                )
            )
        }
        listOf(
            AiIntent.DELETE_TASK,
            AiIntent.UPDATE_TASK,
            AiIntent.RESCHEDULE_TASK,
            AiIntent.MARK_DONE,
            AiIntent.MARK_UNDONE
        ).forEach { intent ->
            assertThrows(TaskAgentValidationException::class.java) {
                validator.validate(
                    AiParsedCommand(
                        intent = intent.name,
                        confidence = 0.97f
                    )
                )
            }
        }
    }

    @Test
    fun homeCreateBranchRemainsDraftOnlyWithOptionalPrefills() {
        val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val createBranch = source
            .substringAfter("AiIntent.CREATE_TASK.name -> {")
            .substringBefore("// query on task")

        assertTrue(createBranch.contains("speakObservationThenRun("))
        assertTrue(createBranch.contains("ExecutionOperation.CREATE_TASK"))
        assertTrue(createBranch.contains("startActivity(openCreateIntent)"))
        assertTrue(createBranch.contains("putExtra(\"prefill_title\", aiResult.taskTitle)"))
        assertTrue(
            createBranch.contains(
                "putExtra(\"prefill_date_text\", aiResult.newDateText ?: aiResult.dateText)"
            )
        )
        assertTrue(
            createBranch.contains(
                "putExtra(\"prefill_time_text\", aiResult.newTimeText ?: aiResult.timeText)"
            )
        )
        listOf(
            "AppDatabase",
            "taskDao()",
            "dao.",
            "insert",
            "ReminderHelper",
            "scheduleReminder",
            "saveTask"
        ).forEach { mutation -> assertFalse(createBranch.contains(mutation)) }
    }

    @Test
    fun blankCreateStillUsesExistingProtectedTaskTransition() {
        val plan = requireNotNull(
            ResponseVerbalizationPlanner.createOrNull(
                observation = ExecutionObservation(
                    operation = ExecutionOperation.CREATE_TASK,
                    outcome = ExecutionOutcome.INFORMATION,
                    taskTitle = "",
                    dateText = "",
                    timeText = "",
                    listenAgain = false,
                    fallbackSpeech = "Opening task creation."
                ),
                tone = ResponseVerbalizationTone.FRIENDLY,
                verbosity = ResponseVerbalizationVerbosity.NORMAL
            )
        )

        assertEquals(ResponseVerbalizationContract.TASK_TRANSITION, plan.contract)
        assertEquals(
            setOf(ResponseVerbalizationPlan.TRANSITION_TARGET),
            plan.requiredPlaceholders
        )
        assertEquals(
            "task creation",
            plan.protectedValues.getValue(ResponseVerbalizationPlan.TRANSITION_TARGET)
        )
    }

    @Test
    fun taskPromptAllowsGenericCreationWithoutInventingFields() {
        val taskPrompt = LaptopAgentClient.SYSTEM_PROMPT
        val normalizedTaskPrompt = taskPrompt.replace(Regex("\\s+"), " ")
        assertTrue(normalizedTaskPrompt.contains("\"Create a task for me.\""))
        assertTrue(normalizedTaskPrompt.contains("\"Can you create a task for me?\""))
        assertTrue(normalizedTaskPrompt.contains("task_title=\"\""))
        assertTrue(normalizedTaskPrompt.contains("Do not invent a task title"))
        assertTrue(
            normalizedTaskPrompt.contains(
                "Do not return UNKNOWN merely because the title is missing"
            )
        )
        assertTrue(normalizedTaskPrompt.contains("Android will open the create-task draft"))
        assertTrue(normalizedTaskPrompt.contains("need_clarification=false"))
        assertTrue(normalizedTaskPrompt.contains("missing_fields=[]"))
        assertTrue(
            normalizedTaskPrompt.contains(
                "Blank task_title is allowed only when the user supplied no usable task or reminder content"
            )
        )
        assertTrue(
            normalizedTaskPrompt.contains(
                "task_title=\"take medicine\", new_date=\"tomorrow\", new_time=\"21:00\""
            )
        )
        assertTrue(
            normalizedTaskPrompt.contains(
                "task_title=\"take breakfast\", new_date=\"tomorrow\", new_time=\"morning\""
            )
        )
        assertTrue(
            normalizedTaskPrompt.contains(
                "\"Create revision next Friday\" -> action=CREATE_TASK, task_title=\"revision\""
            )
        )
        assertTrue(
            normalizedTaskPrompt.contains(
                "\"Remind me\" -> action=CREATE_TASK, task_title=\"\""
            )
        )

        val routingPrompt = ConversationAgentClient.ROUTING_SYSTEM_PROMPT
        assertTrue(routingPrompt.contains("\"How do I create a task?\" is DIRECT_REPLY"))
        assertTrue(routingPrompt.contains("\"Create a task called revision\" is TASK_COMMAND"))
    }

    private fun createResponse(taskTitle: String): String = JSONObject()
        .put("natural_response", "")
        .put("action", AiIntent.CREATE_TASK.name)
        .put("task_title", taskTitle)
        .put("target_task_title", "")
        .put("date", "")
        .put("time", "")
        .put("target_date", "")
        .put("target_time", "")
        .put("new_date", "")
        .put("new_time", "")
        .put("recurrence", "")
        .put("priority", "")
        .put("query_presentation", TaskQueryPresentation.NONE.name)
        .put("breakdown_target_preference", "AUTO")
        .put("confidence", 1.0)
        .put("need_clarification", false)
        .put("missing_fields", JSONArray())
        .put("requires_confirmation", false)
        .put("plan", JSONArray())
        .toString()
}
