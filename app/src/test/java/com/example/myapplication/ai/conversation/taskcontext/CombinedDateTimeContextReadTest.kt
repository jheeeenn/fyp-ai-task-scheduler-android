package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationAgentClient
import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationDecisionParser
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute
import com.example.myapplication.ai.schema.AgentResponseSchemas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CombinedDateTimeContextReadTest {
    @Test
    fun strictContractsAndPromptsSupportDateTime() {
        val parsed = ConversationDecisionParser().parse(decisionJson("DATE_TIME"))
        val routingSchema = AgentResponseSchemas.conversationDecisionResponseFormat().toString()
        val repairSchema = AgentResponseSchemas.contextReadRepairResponseFormat().toString()

        assertEquals(ConversationContextDetail.DATE_TIME, parsed.contextDetail)
        assertTrue(routingSchema.contains("DATE_TIME"))
        assertTrue(repairSchema.contains("DATE_TIME"))
        assertTrue(ConversationAgentClient.ROUTING_SYSTEM_PROMPT.contains("uses DATE_TIME"))
        assertTrue(ConversationAgentClient.ROUTING_SYSTEM_PROMPT.contains("context_detail\":\"DATE_TIME"))
        assertTrue(ConversationAgentClient.CONTEXT_READ_REPAIR_SYSTEM_PROMPT.contains("requests DATE_TIME"))
    }

    @Test
    fun semanticDetailContractKeepsDateTimeDateTimeAndSummaryDistinct() {
        val expected = mapOf(
            "what is the date and time" to ConversationContextDetail.DATE_TIME,
            "what is the date" to ConversationContextDetail.DATE,
            "what is the time" to ConversationContextDetail.TIME,
            "when is this task" to ConversationContextDetail.DATE_TIME
        )

        expected.forEach { (text, detail) ->
            assertEquals(detail, ContextReadDetailCompatibilityPolicy.requiredDetail(text))
        }

        assertTrue(ConversationAgentClient.ROUTING_SYSTEM_PROMPT.contains("Use SUMMARY for broad requests"))
        assertTrue(ConversationAgentClient.ROUTING_SYSTEM_PROMPT.contains("read the task"))
    }

    @Test
    fun androidRejectsAValidButIncompleteTimeRouteForACombinedRequest() {
        val captured = snapshot()
        val timeOnly = ConversationDecision(
            route = ConversationRoute.CONTEXT_READ,
            contextRef = "T1",
            contextDetail = ConversationContextDetail.TIME,
            confidence = 0.95,
            listenAgain = true
        )
        val combined = "what is the date and time"

        assertEquals(
            ConversationContextDetail.DATE_TIME,
            ContextReadDetailCompatibilityPolicy.requiredDetail(combined)
        )
        assertFalse(
            ContextReadDetailCompatibilityPolicy.isCompatible(
                combined,
                ConversationContextDetail.TIME
            )
        )
        assertEquals(
            ContextReadValidationResult.INCOMPATIBLE_DETAIL,
            ReadOnlyTaskContextReadValidator.validate(
                timeOnly,
                captured,
                captured.generation,
                combined
            ).result
        )
        assertTrue(
            ContextReadDetailCompatibilityPolicy.isCompatible(
                combined,
                ConversationContextDetail.DATE_TIME
            )
        )
        assertEquals(
            ContextReadRepairDisposition.REJECTED,
            ContextReadRepairPolicy.evaluate(
                normalizedText = combined,
                repairedDecision = timeOnly,
                capturedSnapshot = captured,
                currentGeneration = captured.generation
            ).disposition
        )
        assertEquals(
            ConversationContextDetail.DATE_TIME,
            ContextReadDetailCompatibilityPolicy.requiredDetail("when is this task")
        )
    }

    @Test
    fun dateTimeRendersOnlyAuthoritativeCapturedScheduleWithAllMissingFallbacks() {
        val complete = item(date = "04/08/2026", time = "11:45 AM")
        assertEquals(
            "Breakfast is scheduled for 4 August at 11:45 AM.",
            render(complete)
        )
        assertEquals(
            "Breakfast has no date set. Its time is 11:45 AM.",
            render(complete.copy(dueDate = ""))
        )
        assertEquals(
            "Breakfast is scheduled for 4 August, with no time set.",
            render(complete.copy(dueTime = ""))
        )
        assertEquals(
            "Breakfast does not have a date or time set.",
            render(complete.copy(dueDate = "", dueTime = ""))
        )
    }

    @Test
    fun dateTimeRenderingNeverExposesDatabaseIdentifiers() {
        val speech = render(item(date = "04/08/2026", time = "11:45 AM"))
        assertFalse(speech.contains("918273645"))
        assertFalse(speech.contains("Room", ignoreCase = true))
        assertFalse(speech.contains("T1"))
    }

    private fun render(item: ReadOnlyTaskContextItem): String =
        ReadOnlyTaskContextResponseRenderer.render(item, ConversationContextDetail.DATE_TIME)

    private fun snapshot() = ReadOnlyTaskContextSnapshot(
        generation = 7L,
        scope = TaskContextScope.TASK_DETAIL,
        items = listOf(item(date = "04/08/2026", time = "11:45 AM")),
        truncated = false
    )

    private fun item(date: String, time: String) = ReadOnlyTaskContextItem(
        ref = "T1",
        title = "Breakfast",
        dueDate = date,
        dueTime = time,
        isDone = false,
        subtaskCount = 0,
        unfinishedSubtaskCount = 0
    )

    private fun decisionJson(detail: String) = """
        {
          "route":"CONTEXT_READ",
          "task_text":"",
          "reply":"",
          "context_ref":"T1",
          "context_detail":"$detail",
          "context_action":"NONE",
          "setting_target":"NONE","setting_action":"NONE",
          "query_reading_move":"NONE",
          "query_presentation_hint":"NONE",
          "confidence":0.95,
          "listen_again":true
        }
    """.trimIndent()
}
