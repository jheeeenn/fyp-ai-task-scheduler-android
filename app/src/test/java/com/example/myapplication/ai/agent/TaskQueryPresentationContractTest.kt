package com.example.myapplication.ai.agent

import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.AiParsedCommand
import com.example.myapplication.ai.TaskQueryPresentation
import com.example.myapplication.ai.TaskQueryDetail
import com.example.myapplication.ai.schema.AgentResponseSchemas
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TaskQueryPresentationContractTest {
    @Test
    fun strictSchemaRequiresExactlyTheScheduleDetailEnum() {
        val format = AgentResponseSchemas.taskAgentResponseFormat().getJSONObject("json_schema")
        val schema = format.getJSONObject("schema")
        val values = schema.getJSONObject("properties").getJSONObject("query_detail").getJSONArray("enum")
        assertEquals(setOf("NONE", "DATE", "TIME", "DATE_TIME"),
            (0 until values.length()).map(values::getString).toSet())
        assertTrue(schema.getJSONArray("required").toString().contains("query_detail"))
        assertFalse(schema.getBoolean("additionalProperties"))
        assertTrue(format.getBoolean("strict"))
    }

    @Test
    fun namedScheduleDetailsSurviveParserNormalizerAndValidatorWithoutInventedFilters() {
        listOf(TaskQueryDetail.DATE, TaskQueryDetail.TIME, TaskQueryDetail.DATE_TIME).forEach { detail ->
            val json = completeResponseJson()
                .put("target_task_title", "Read Book")
                .put("query_detail", detail.name)
                .put("query_presentation", "DETAILS")
                .put("date", "")
            val command = ActionValidator().validate(TaskActionNormalizer().normalize(
                TaskAgentResponseParser().parse(json.toString())
            ))
            assertEquals(AiIntent.QUERY_TASK.name, command.intent)
            assertEquals("Read Book", command.targetTaskTitle)
            assertEquals(detail, command.queryDetail)
            assertEquals(TaskQueryPresentation.DETAILS, command.queryPresentation)
            assertEquals(null, command.taskTitle)
            assertEquals(null, command.targetDateText)
            assertEquals(null, command.targetTimeText)
            assertEquals(null, command.newDateText)
            assertEquals(null, command.newTimeText)
            assertEquals(null, command.naturalResponse)
        }
    }

    @Test
    fun ordinaryListAndCountQueriesKeepNoneDetailAndPresentationCompatibility() {
        listOf("OVERVIEW", "COUNT_ONLY", "DETAILS", "NONE").forEach { presentation ->
            val json = completeResponseJson().put("query_presentation", presentation)
            val command = ActionValidator().validate(TaskActionNormalizer().normalize(
                TaskAgentResponseParser().parse(json.toString())
            ))
            assertEquals(TaskQueryDetail.NONE, command.queryDetail)
            assertEquals(null, command.targetTaskTitle)
            assertEquals("tomorrow", command.targetDateText)
            assertEquals(if (presentation == "NONE") TaskQueryPresentation.OVERVIEW
                else TaskQueryPresentation.valueOf(presentation), command.queryPresentation)
        }
    }

    @Test
    fun invalidDetailAndTargetCombinationsFailClosed() {
        val query = AiParsedCommand(intent = AiIntent.QUERY_TASK.name,
            queryPresentation = TaskQueryPresentation.DETAILS, confidence = 0.95f)
        val malformed = listOf(
            query.copy(targetTaskTitle = "Read Book", queryDetail = TaskQueryDetail.NONE),
            query.copy(queryDetail = TaskQueryDetail.TIME),
            query.copy(targetTaskTitle = "   ", queryDetail = TaskQueryDetail.DATE),
            query.copy(taskTitle = "Read Book")
        ) + TaskQueryDetail.entries.filter { it != TaskQueryDetail.NONE }.map {
            AiParsedCommand(intent = AiIntent.CREATE_TASK.name, taskTitle = "Read Book",
                queryDetail = it, confidence = 0.95f)
        }
        malformed.forEach { command ->
            assertTrue(command.toString(), runCatching { ActionValidator().validate(command) }
                .exceptionOrNull() is TaskAgentValidationException)
        }
    }

    @Test
    fun parserRequiresDetailAndRejectsUnsupportedWireValuesAndTypes() {
        val missing = completeResponseJson().apply { remove("query_detail") }
        assertTrue(runCatching { TaskAgentResponseParser().parse(missing.toString()) }
            .exceptionOrNull() is TaskAgentParseException)
        listOf("SUMMARY", "ALL", "", "time", " TIME ", 7, true, JSONObject.NULL).forEach { invalid ->
            val json = completeResponseJson().put("query_detail", invalid)
            assertTrue(invalid.toString(), runCatching { TaskAgentResponseParser().parse(json.toString()) }
                .exceptionOrNull() is TaskAgentParseException)
        }
        assertTrue(runCatching { TaskActionNormalizer().normalize(TaskAgentResponse(
            action = "QUERY_TASK", query_detail = "SUMMARY", confidence = 0.95f
        )) }.exceptionOrNull() is TaskAgentValidationException)
    }

    @Test
    fun promptMakesNamedScheduleComponentsIndependentOfFiltersAndStoredFacts() {
        val prompt = LaptopAgentClient.SYSTEM_PROMPT
        listOf("When" to "DATE_TIME", "What time" to "TIME", "What date" to "DATE").forEach { (word, detail) ->
            assertTrue(prompt.contains("\"$word is Read Book?\" -> action=QUERY_TASK, target_task_title=\"Read Book\", query_detail=$detail"))
        }
        assertTrue(prompt.contains("For every non-QUERY_TASK action, query_detail must be NONE."))
        assertTrue(prompt.contains("query_detail is the requested schedule component, not a temporal query filter or a stored fact."))
        assertTrue(prompt.contains("never answer a task's actual date/time"))
        assertTrue(prompt.contains("All remaining list/count examples below use target_task_title=\"\" and query_detail=NONE."))
    }

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
        put("query_detail", "NONE")
        put("breakdown_target_preference", "AUTO")
        put("confidence", 0.95)
        put("need_clarification", false)
        put("missing_fields", org.json.JSONArray())
        put("requires_confirmation", false)
        put("plan", org.json.JSONArray())
    }
}
