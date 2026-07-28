package com.example.myapplication.ai.agent

import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.AiParsedCommand
import com.example.myapplication.ai.TaskQueryPresentation
import com.example.myapplication.ai.schema.AgentResponseSchemas
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TaskQueryPresentationContractTest {
    @Test
    fun schemaRequiresQueryPresentationWithExactlyTheAllowedValues() {
        val schema = AgentResponseSchemas.taskAgentResponseFormat()
            .getJSONObject("json_schema")
            .getJSONObject("schema")
        val property = schema.getJSONObject("properties")
            .getJSONObject("query_presentation")
        val values = property.getJSONArray("enum")

        assertTrue(schema.getJSONArray("required").toString().contains("query_presentation"))
        assertEquals(
            setOf("NONE", "COUNT_ONLY", "OVERVIEW", "DETAILS"),
            (0 until values.length()).map(values::getString).toSet()
        )
    }

    @Test
    fun parserRequiresAndReadsQueryPresentation() {
        val json = completeResponseJson().put("query_presentation", "COUNT_ONLY")

        assertEquals(
            "COUNT_ONLY",
            TaskAgentResponseParser().parse(json.toString()).query_presentation
        )
        json.remove("query_presentation")
        try {
            TaskAgentResponseParser().parse(json.toString())
            fail("Missing query_presentation must fail closed")
        } catch (_: TaskAgentParseException) {
            Unit
        }
    }

    @Test
    fun queryNoneUsesDocumentedSafeOverviewCompatibilityPolicy() {
        val command = TaskActionNormalizer().normalize(
            TaskAgentResponse(
                action = AiIntent.QUERY_TASK.name,
                query_presentation = "NONE",
                confidence = 0.95f
            )
        )

        assertEquals(TaskQueryPresentation.OVERVIEW, command.queryPresentation)
        assertEquals(command, ActionValidator().validate(command))
    }

    @Test
    fun nonQueryActionsRequireNone() {
        val invalid = AiParsedCommand(
            intent = AiIntent.CREATE_TASK.name,
            taskTitle = "medicine",
            queryPresentation = TaskQueryPresentation.OVERVIEW,
            confidence = 0.95f
        )

        try {
            ActionValidator().validate(invalid)
            fail("Non-query presentation must be rejected")
        } catch (_: TaskAgentValidationException) {
            Unit
        }
    }

    @Test
    fun invalidWireValueFailsAndroidNormalization() {
        try {
            TaskActionNormalizer().normalize(
                TaskAgentResponse(
                    action = AiIntent.QUERY_TASK.name,
                    query_presentation = "EVERYTHING",
                    confidence = 0.95f
                )
            )
            fail("Unsupported query presentation must fail closed")
        } catch (_: TaskAgentValidationException) {
            Unit
        }
    }

    @Test
    fun promptExamplesDistinguishOverviewCountAndDetails() {
        val prompt = LaptopAgentClient.SYSTEM_PROMPT

        assertTrue(prompt.contains("\"What task do I have tomorrow?\" -> action=QUERY_TASK, query_presentation=OVERVIEW"))
        assertTrue(prompt.contains("\"Do I have any tasks tomorrow?\" -> action=QUERY_TASK, query_presentation=COUNT_ONLY"))
        assertTrue(prompt.contains("\"Do I have any tomorrow?\" -> action=QUERY_TASK, query_presentation=COUNT_ONLY"))
        assertTrue(prompt.contains("\"Anything tomorrow?\" -> action=QUERY_TASK, query_presentation=COUNT_ONLY"))
        assertTrue(prompt.contains("\"How many this week?\" -> action=QUERY_TASK, query_presentation=COUNT_ONLY"))
        assertTrue(prompt.contains("\"What do I have tomorrow?\" -> action=QUERY_TASK, query_presentation=OVERVIEW"))
        assertTrue(prompt.contains("\"Read all task details tomorrow.\" -> action=QUERY_TASK, query_presentation=DETAILS"))
        assertTrue(prompt.contains("For every non-QUERY_TASK action, query_presentation must be NONE."))
    }

    private fun completeResponseJson() = JSONObject().apply {
        put("natural_response", "")
        put("action", "QUERY_TASK")
        put("task_title", "")
        put("target_task_title", "")
        put("date", "tomorrow")
        put("time", "")
        put("target_date", "")
        put("target_time", "")
        put("new_date", "")
        put("new_time", "")
        put("recurrence", "")
        put("priority", "")
        put("query_presentation", "OVERVIEW")
        put("breakdown_target_preference", "AUTO")
        put("confidence", 0.95)
        put("need_clarification", false)
        put("missing_fields", org.json.JSONArray())
        put("requires_confirmation", false)
        put("plan", org.json.JSONArray())
    }
}
