package com.example.myapplication.ai.agent

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.schema.AgentResponseSchemas
import com.example.myapplication.ai.temporal.RelativeTemporalBase
import com.example.myapplication.ai.temporal.RelativeTemporalOperation
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
    fun dedicatedSchemaContainsExactlyBoundedOperationFieldsAndNoFinalFacts() {
        val schema = AgentResponseSchemas.contextActionExtractionResponseFormat()
            .getJSONObject("json_schema")
            .getJSONObject("schema")
        val properties = schema.getJSONObject("properties").keys().asSequence().toSet()
        val required = schema.getJSONArray("required")
        val requiredFields = (0 until required.length()).map { required.getString(it) }.toSet()
        val expected = setOf(
            "action",
            "replacement_title",
            "date_operation",
            "time_operation",
            "relative_base",
            "replacement_date_text",
            "replacement_time_text",
            "date_offset_days",
            "time_offset_minutes",
            "confidence",
            "need_clarification"
        )

        assertEquals(expected, properties)
        assertEquals(expected, requiredFields)
        assertFalse(schema.getBoolean("additionalProperties"))
        listOf(
            "natural_response", "task_title", "target_task_title", "new_date", "new_time",
            "final_date", "final_time", "task_id", "room_id", "update_completed"
        ).forEach { forbidden -> assertFalse(properties.contains(forbidden)) }
    }

    @Test
    fun parserRejectsAdditionalFinalFactOrRoomId() {
        listOf("final_date", "room_id", "update_completed").forEach { forbidden ->
            val invalid = JSONObject(setResponse()).put(forbidden, "forbidden").toString()
            assertThrows(ContextActionExtractionParseException::class.java) {
                ContextActionExtractionResponseParser().parse(invalid)
            }
        }
        assertThrows(ContextActionExtractionParseException::class.java) {
            ContextActionExtractionResponseParser().parse(
                setResponse("tomorrow", "9 AM") + " update completed"
            )
        }
    }

    @Test
    fun boundedPromptExplainsSemanticsAndRequestUsesDedicatedLimits() {
        val prompt = LaptopAgentClient.CONTEXT_ACTION_SYSTEM_PROMPT
        assertTrue(prompt.contains("semantic operation extraction"))
        assertTrue(prompt.contains("not an exhaustive phrase dictionary"))
        assertTrue(prompt.contains("Android resolves literal expressions"))
        assertTrue(prompt.contains("relative_base must be AUTHORITATIVE_TASK"))
        assertTrue(prompt.contains("Do not output markdown"))
        assertFalse(prompt.contains("target_date"))
        assertFalse(prompt.contains("target_task_title"))

        val source = java.io.File(
            "src/main/java/com/example/myapplication/ai/agent/LaptopAgentClient.kt"
        ).readText()
        val method = source.substringAfter("open suspend fun processContextAction(")
            .substringBefore("open suspend fun processRelativeTemporalCorrection(")
        assertTrue(method.contains("contextActionExtractionResponseFormat()"))
        assertTrue(method.contains("RELATIVE_TEMPORAL_MAX_TOKENS"))
        assertTrue(method.contains("boundedTemporalClient"))
        assertFalse(method.contains("taskAgentResponseFormat()"))
        assertTrue(source.contains(".callTimeout(10, TimeUnit.SECONDS)"))
    }

    @Test
    fun updateAllowsEmptyFieldsAndReplacementTitleIsOnlyChange() = runBlocking {
        val emptyEdit = orchestrator(updateResponse())
            .processContextAction("Edit the second one", ConversationContextAction.UPDATE)
        assertEquals(ConversationContextAction.UPDATE, emptyEdit.action)
        assertNull(emptyEdit.replacementTitle)
        assertNull(emptyEdit.temporalProposal)

        val titled = orchestrator(updateResponse(replacementTitle = "Software Revision"))
            .processContextAction(
                "Change its title to Software Revision",
                ConversationContextAction.UPDATE
            )
        assertEquals("Software Revision", titled.replacementTitle)
    }

    @Test
    fun existingAbsoluteContextRescheduleUsesSetOperationsAndLiteralText() = runBlocking {
        val result = orchestrator(setResponse("next Friday", "3 PM"))
            .processContextAction(
                "move it to next Friday at 3 PM",
                ConversationContextAction.RESCHEDULE
            )

        assertEquals(ConversationContextAction.RESCHEDULE, result.action)
        assertEquals("next Friday", result.newDateText)
        assertEquals("3 PM", result.newTimeText)
        assertEquals(RelativeTemporalOperation.SET, result.temporalProposal?.dateOperation)
        assertEquals(RelativeTemporalOperation.SET, result.temporalProposal?.timeOperation)
        assertEquals(RelativeTemporalBase.AUTHORITATIVE_TASK, result.temporalProposal?.relativeBase)
        assertNull(result.replacementTitle)
    }

    @Test
    fun relativeOffsetResponseCarriesOperationWithoutModelAuthoredFinalFacts() = runBlocking {
        val result = orchestrator(offsetResponse(minutes = 30)).processContextAction(
            "move it thirty minutes later",
            ConversationContextAction.RESCHEDULE
        )
        assertNull(result.newDateText)
        assertNull(result.newTimeText)
        assertEquals(30, result.temporalProposal?.timeOffsetMinutes)
    }

    @Test
    fun actionMismatchLowConfidenceClarificationAndCurrentBaseFailClosed() {
        listOf(
            updateResponse(),
            offsetResponse(minutes = 30, confidence = 0.79),
            offsetResponse(minutes = 30, needClarification = true),
            JSONObject(offsetResponse(minutes = 30))
                .put("relative_base", "CURRENT_PROPOSAL")
                .toString()
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
    fun malformedCombinationsUnknownEnumAndNonIntegerOffsetFailClosed() {
        listOf(
            JSONObject(offsetResponse(30)).put("replacement_time_text", "10 AM").toString(),
            JSONObject(offsetResponse(30)).put("time_operation", "SHIFT").toString(),
            JSONObject(offsetResponse(30)).put("time_operation", "offset").toString(),
            offsetResponse(30).replace("\"time_offset_minutes\":30", "\"time_offset_minutes\":30.5"),
            offsetResponse(0),
            offsetResponse(10_081)
        ).forEach { raw ->
            assertThrows(TaskAgentProcessingException::class.java) {
                runBlocking {
                    orchestrator(raw).processContextAction(
                        "Move it",
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
                    JSONObject(offsetResponse(30))
                        .put("replacement_title", "Wrong")
                        .toString()
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

    private fun updateResponse(
        replacementTitle: String = "",
        confidence: Double = 0.97,
        needClarification: Boolean = false
    ): String = response(
        action = "UPDATE_TASK",
        replacementTitle = replacementTitle,
        dateOperation = "KEEP",
        timeOperation = "KEEP",
        confidence = confidence,
        needClarification = needClarification
    )

    private fun setResponse(date: String = "", time: String = ""): String = response(
        action = "RESCHEDULE_TASK",
        dateOperation = if (date.isBlank()) "KEEP" else "SET",
        timeOperation = if (time.isBlank()) "KEEP" else "SET",
        replacementDateText = date,
        replacementTimeText = time
    )

    private fun offsetResponse(
        minutes: Int,
        confidence: Double = 0.97,
        needClarification: Boolean = false
    ): String = response(
        action = "RESCHEDULE_TASK",
        dateOperation = "KEEP",
        timeOperation = "OFFSET",
        timeOffsetMinutes = minutes,
        confidence = confidence,
        needClarification = needClarification
    )

    private fun response(
        action: String,
        replacementTitle: String = "",
        dateOperation: String,
        timeOperation: String,
        relativeBase: String = "AUTHORITATIVE_TASK",
        replacementDateText: String = "",
        replacementTimeText: String = "",
        dateOffsetDays: Int = 0,
        timeOffsetMinutes: Int = 0,
        confidence: Double = 0.97,
        needClarification: Boolean = false
    ): String = JSONObject()
        .put("action", action)
        .put("replacement_title", replacementTitle)
        .put("date_operation", dateOperation)
        .put("time_operation", timeOperation)
        .put("relative_base", relativeBase)
        .put("replacement_date_text", replacementDateText)
        .put("replacement_time_text", replacementTimeText)
        .put("date_offset_days", dateOffsetDays)
        .put("time_offset_minutes", timeOffsetMinutes)
        .put("confidence", confidence)
        .put("need_clarification", needClarification)
        .toString()
}
