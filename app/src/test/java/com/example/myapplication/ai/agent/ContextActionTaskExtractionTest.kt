package com.example.myapplication.ai.agent

import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.conversation.ConversationContextAction
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextActionTaskExtractionTest {
    @Test
    fun boundedPromptSelectsNoTargetAndReceivesNoRoomId() {
        val prompt = LaptopAgentClient.CONTEXT_ACTION_SYSTEM_PROMPT
        assertTrue(prompt.contains("Android has already selected and validated"))
        assertTrue(prompt.contains("target_task_title, target_date, and target_time must remain empty"))
        assertTrue(prompt.contains("Never output a Room ID"))
        assertTrue(prompt.contains("Do not calculate dates"))
        assertFalse(prompt.contains("task_id"))
    }

    @Test
    fun updateAllowsEmptyFieldsAndReplacementTitleIsOnlyPrefill() = runBlocking {
        val orchestrator = orchestrator(response("UPDATE_TASK"))
        val emptyEdit = orchestrator.processContextAction("Edit the first one", ConversationContextAction.UPDATE)
        assertEquals(AiIntent.UPDATE_TASK.name, emptyEdit.intent)
        assertNull(emptyEdit.targetTaskTitle)

        val titled = orchestrator(response("UPDATE_TASK", taskTitle = "Software Revision"))
            .processContextAction("Change its title to Software Revision", ConversationContextAction.UPDATE)
        assertEquals("Software Revision", titled.taskTitle)
        assertNull(titled.targetTaskTitle)
    }

    @Test
    fun reschedulePreservesLiteralDateAndTime() = runBlocking {
        val result = orchestrator(
            response("RESCHEDULE_TASK", newDate = "next Wednesday", newTime = "around 4 PM")
        ).processContextAction("Move T2 to next Wednesday around 4 PM", ConversationContextAction.RESCHEDULE)

        assertEquals("next Wednesday", result.newDateText)
        assertEquals("around 4 PM", result.newTimeText)
        assertNull(result.targetTaskTitle)
    }

    @Test
    fun actionMismatchAndLowConfidenceAreRejected() {
        assertThrows(TaskAgentProcessingException::class.java) {
            runBlocking {
                orchestrator(response("UPDATE_TASK"))
                    .processContextAction("Move T2", ConversationContextAction.RESCHEDULE)
            }
        }
        assertThrows(TaskAgentProcessingException::class.java) {
            runBlocking {
                orchestrator(response("RESCHEDULE_TASK", confidence = 0.59))
                    .processContextAction("Move T2", ConversationContextAction.RESCHEDULE)
            }
        }
    }

    private fun orchestrator(raw: String) = AgentOrchestrator(
        laptopAgentClient = object : LaptopAgentClient(null) {
            override suspend fun processContextAction(
                normalizedText: String,
                expectedAction: ConversationContextAction
            ): String = raw
        },
        taskAgentResponseParser = TaskAgentResponseParser(),
        taskActionNormalizer = TaskActionNormalizer(),
        actionValidator = ActionValidator()
    )

    private fun response(
        action: String,
        taskTitle: String = "",
        newDate: String = "",
        newTime: String = "",
        confidence: Double = 0.97
    ): String = JSONObject()
        .put("natural_response", "")
        .put("action", action)
        .put("task_title", taskTitle)
        .put("target_task_title", "")
        .put("date", "")
        .put("time", "")
        .put("target_date", "")
        .put("target_time", "")
        .put("new_date", newDate)
        .put("new_time", newTime)
        .put("recurrence", "")
        .put("priority", "")
        .put("confidence", confidence)
        .put("need_clarification", false)
        .put("missing_fields", JSONArray())
        .put("requires_confirmation", false)
        .put("plan", JSONArray())
        .toString()
}
