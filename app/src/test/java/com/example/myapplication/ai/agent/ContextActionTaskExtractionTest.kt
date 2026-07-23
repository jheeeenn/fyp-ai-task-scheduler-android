package com.example.myapplication.ai.agent

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.schema.AgentResponseSchemas
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextActionTaskExtractionTest {
    @Test
    fun dedicatedSchemaContainsExactlySixChangeFieldsAndNoTargetFields() {
        val schema = AgentResponseSchemas.contextActionExtractionResponseFormat()
            .getJSONObject("json_schema")
            .getJSONObject("schema")
        val properties = schema.getJSONObject("properties").keys().asSequence().toSet()
        val required = schema.getJSONArray("required")
        val requiredFields = (0 until required.length()).map { required.getString(it) }.toSet()
        val expected = setOf(
            "action",
            "replacement_title",
            "new_date",
            "new_time",
            "confidence",
            "need_clarification"
        )

        assertEquals(expected, properties)
        assertEquals(expected, requiredFields)
        assertFalse(schema.getBoolean("additionalProperties"))
        listOf(
            "natural_response", "task_title", "target_task_title", "date", "time",
            "target_date", "target_time", "recurrence", "priority", "missing_fields",
            "requires_confirmation", "plan", "task_id", "room_id"
        ).forEach { forbidden -> assertFalse(properties.contains(forbidden)) }
    }

    @Test
    fun parserRejectsTargetFieldAsAdditionalProperty() {
        val invalid = JSONObject(response("RESCHEDULE_TASK", newDate = "Friday"))
            .put("target_date", "Friday")
            .toString()
        assertThrows(ContextActionExtractionParseException::class.java) {
            ContextActionExtractionResponseParser().parse(invalid)
        }
    }

    @Test
    fun boundedPromptAndRequestUseOnlyDedicatedExtractionContract() {
        val prompt = LaptopAgentClient.CONTEXT_ACTION_SYSTEM_PROMPT
        assertTrue(prompt.contains("Return exactly these six JSON fields"))
        assertTrue(prompt.contains("replacement_title"))
        assertTrue(prompt.contains("Never output or request a task ID or Room ID"))
        assertTrue(prompt.contains("Do not calculate dates"))
        assertTrue(prompt.contains("move it to next Friday at 3 PM"))
        assertFalse(prompt.contains("target_date"))
        assertFalse(prompt.contains("target_task_title"))

        val source = java.io.File(
            "src/main/java/com/example/myapplication/ai/agent/LaptopAgentClient.kt"
        ).readText()
        val method = source.substringAfter("open suspend fun processContextAction(")
            .substringBefore("private fun execute(")
        assertTrue(method.contains("AgentResponseSchemas.contextActionExtractionResponseFormat()"))
        assertFalse(method.contains("taskAgentResponseFormat()"))
        assertTrue(source.contains("CONTEXT_ACTION_EXTRACTION_SCHEMA\", \"enabled"))
    }

    @Test
    fun updateAllowsEmptyFieldsAndReplacementTitleIsOnlyChange() = runBlocking {
        val emptyEdit = orchestrator(response("UPDATE_TASK"))
            .processContextAction("Edit the second one", ConversationContextAction.UPDATE)
        assertEquals(ConversationContextAction.UPDATE, emptyEdit.action)
        assertNull(emptyEdit.replacementTitle)

        val titled = orchestrator(
            response("UPDATE_TASK", replacementTitle = "Software Revision")
        ).processContextAction(
            "Change its title to Software Revision",
            ConversationContextAction.UPDATE
        )
        assertEquals("Software Revision", titled.replacementTitle)
    }

    @Test
    fun runtimeStyleRescheduleResponseSucceedsWithLiteralDestinationFields() = runBlocking {
        val result = orchestrator(
            response("RESCHEDULE_TASK", newDate = "next Friday", newTime = "3 PM")
        ).processContextAction(
            "move it to next Friday at 3 PM",
            ConversationContextAction.RESCHEDULE
        )

        assertEquals(ConversationContextAction.RESCHEDULE, result.action)
        assertEquals("next Friday", result.newDateText)
        assertEquals("3 PM", result.newTimeText)
        assertNull(result.replacementTitle)
    }

    @Test
    fun actionMismatchLowConfidenceAndClarificationFailClosed() {
        listOf(
            response("UPDATE_TASK"),
            response("RESCHEDULE_TASK", confidence = 0.59),
            response("RESCHEDULE_TASK", needClarification = true)
        ).forEach { raw ->
            assertThrows(TaskAgentProcessingException::class.java) {
                runBlocking {
                    orchestrator(raw).processContextAction(
                        "Move the second one",
                        ConversationContextAction.RESCHEDULE
                    )
                }
            }
        }
    }

    @Test
    fun rescheduleRejectsReplacementTitle() {
        assertThrows(TaskAgentProcessingException::class.java) {
            runBlocking {
                orchestrator(
                    response("RESCHEDULE_TASK", replacementTitle = "Wrong")
                ).processContextAction("Move T2", ConversationContextAction.RESCHEDULE)
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
        replacementTitle: String = "",
        newDate: String = "",
        newTime: String = "",
        confidence: Double = 0.97,
        needClarification: Boolean = false
    ): String = JSONObject()
        .put("action", action)
        .put("replacement_title", replacementTitle)
        .put("new_date", newDate)
        .put("new_time", newTime)
        .put("confidence", confidence)
        .put("need_clarification", needClarification)
        .toString()
}
