package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.schema.AgentResponseSchemas
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationQueryReadingContractTest {
    @Test
    fun mainSchemaContainsStructuredRouteMoveAndPresentationHint() {
        val schema = AgentResponseSchemas.conversationDecisionResponseFormat()
            .getJSONObject("json_schema")
            .getJSONObject("schema")
        val properties = schema.getJSONObject("properties")

        assertEquals(
            setOf(
                "NONE",
                "START_OVERVIEW",
                "CONTINUE",
                "REPEAT_LAST",
                "REPEAT_PAGE",
                "STOP"
            ),
            properties.getJSONObject("query_reading_move").enumValues()
        )
        assertEquals(
            setOf("NONE", "COUNT_ONLY", "OVERVIEW", "DETAILS"),
            properties.getJSONObject("query_presentation_hint").enumValues()
        )
        assertTrue(
            properties.getJSONObject("route").enumValues()
                .contains("QUERY_READING_CONTROL")
        )
    }

    @Test
    fun queryReadingControlExamplesUseEmptyReplyAndNoTaskFacts() {
        val prompt = ConversationAgentClient.ROUTING_SYSTEM_PROMPT
        val examples = prompt.substringAfter("Interaction state:\nQUERY_COUNT")

        assertTrue(examples.contains("\"query_reading_move\":\"START_OVERVIEW\""))
        assertTrue(examples.contains("\"query_reading_move\":\"CONTINUE\""))
        assertTrue(examples.contains("\"query_reading_move\":\"REPEAT_LAST\""))
        assertTrue(examples.contains("\"query_reading_move\":\"REPEAT_PAGE\""))
        assertFalse(
            examples.lineSequence()
                .filter { it.contains("\"route\":\"QUERY_READING_CONTROL\"") }
                .any { !it.contains("\"reply\":\"\"") || !it.contains("\"task_text\":\"\"") }
        )
    }

    @Test
    fun repairSchemasRestrictBothNewFieldsToNone() {
        listOf(
            AgentResponseSchemas.contextReadRepairResponseFormat(),
            AgentResponseSchemas.contextActionRepairResponseFormat()
        ).forEach { format ->
            val properties = format
                .getJSONObject("json_schema")
                .getJSONObject("schema")
                .getJSONObject("properties")

            assertEquals(setOf("NONE"), properties.getJSONObject("query_reading_move").enumValues())
            assertEquals(setOf("NONE"), properties.getJSONObject("query_presentation_hint").enumValues())
        }
    }

    private fun JSONObject.enumValues(): Set<String> {
        val values = getJSONArray("enum")
        return (0 until values.length()).map { values.getString(it) }.toSet()
    }
}
