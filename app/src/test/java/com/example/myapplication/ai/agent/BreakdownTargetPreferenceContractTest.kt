package com.example.myapplication.ai.agent

import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.AiParsedCommand
import com.example.myapplication.ai.breakdown.BreakdownTargetPreference
import com.example.myapplication.ai.schema.AgentResponseSchemas
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BreakdownTargetPreferenceContractTest {
    @Test
    fun schemaRequiresOnlyAutoOrNewRoot() {
        val schema = AgentResponseSchemas.taskAgentResponseFormat()
            .getJSONObject("json_schema")
            .getJSONObject("schema")
        val values = schema.getJSONObject("properties")
            .getJSONObject("breakdown_target_preference")
            .getJSONArray("enum")

        assertTrue(
            schema.getJSONArray("required")
                .toString()
                .contains("breakdown_target_preference")
        )
        assertEquals(
            setOf("AUTO", "NEW_ROOT"),
            (0 until values.length()).map(values::getString).toSet()
        )
    }

    @Test
    fun parserAndNormalizerCarryValidatedPreference() {
        val parsed = TaskAgentResponseParser().parse(
            completeResponseJson()
                .put("breakdown_target_preference", "NEW_ROOT")
                .toString()
        )
        val command = TaskActionNormalizer().normalize(parsed)

        assertEquals("NEW_ROOT", parsed.breakdown_target_preference)
        assertEquals(
            BreakdownTargetPreference.NEW_ROOT,
            command.breakdownTargetPreference
        )
    }

    @Test
    fun missingOrInvalidPreferenceFailsClosed() {
        val missing = completeResponseJson()
        missing.remove("breakdown_target_preference")
        try {
            TaskAgentResponseParser().parse(missing.toString())
            fail("Missing breakdown target preference must fail closed")
        } catch (_: TaskAgentParseException) {
            Unit
        }

        try {
            TaskActionNormalizer().normalize(
                TaskAgentResponse(
                    action = AiIntent.BREAKDOWN_TASK.name,
                    task_title = "Presentation",
                    breakdown_target_preference = "EXISTING_ROOT",
                    confidence = 0.95f
                )
            )
            fail("Unsupported breakdown target preference must fail closed")
        } catch (_: TaskAgentValidationException) {
            Unit
        }
    }

    @Test
    fun nonBreakdownActionsRequireAuto() {
        val invalid = AiParsedCommand(
            intent = AiIntent.CREATE_TASK.name,
            taskTitle = "Presentation",
            breakdownTargetPreference = BreakdownTargetPreference.NEW_ROOT,
            confidence = 0.95f
        )

        try {
            ActionValidator().validate(invalid)
            fail("Non-breakdown action must reject NEW_ROOT preference")
        } catch (_: TaskAgentValidationException) {
            Unit
        }

        val valid = invalid.copy(
            breakdownTargetPreference = BreakdownTargetPreference.AUTO
        )
        assertEquals(valid, ActionValidator().validate(valid))
    }

    @Test
    fun promptUsesSemanticContrastsForTargetPreference() {
        val prompt = LaptopAgentClient.SYSTEM_PROMPT

        assertTrue(
            prompt.contains(
                "\"Break down my final year project\" -> " +
                    "action=BREAKDOWN_TASK, breakdown_target_preference=AUTO"
            )
        )
        assertTrue(
            prompt.contains(
                "\"Create a new final year project plan and break it down\" -> " +
                    "action=BREAKDOWN_TASK, breakdown_target_preference=NEW_ROOT"
            )
        )
        assertTrue(
            prompt.contains(
                "\"Make a separate presentation task and split it into steps\" -> " +
                    "action=BREAKDOWN_TASK, breakdown_target_preference=NEW_ROOT"
            )
        )
        assertTrue(
            prompt.contains(
                "For every non-BREAKDOWN_TASK action, " +
                    "breakdown_target_preference must be AUTO."
            )
        )
    }

    private fun completeResponseJson() = JSONObject().apply {
        put("natural_response", "")
        put("action", "BREAKDOWN_TASK")
        put("task_title", "Presentation")
        put("target_task_title", "")
        put("date", "")
        put("time", "")
        put("target_date", "")
        put("target_time", "")
        put("new_date", "")
        put("new_time", "")
        put("recurrence", "")
        put("priority", "")
        put("query_presentation", "NONE")
        put("breakdown_target_preference", "AUTO")
        put("confidence", 0.95)
        put("need_clarification", false)
        put("missing_fields", JSONArray())
        put("requires_confirmation", false)
        put("plan", JSONArray().put("Draft slides").put("Practise delivery"))
    }
}
