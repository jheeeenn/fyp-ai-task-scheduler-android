package com.example.myapplication.ai.schema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateDraftMoveSchemaTest {
    private val format = AgentResponseSchemas.createDraftMoveResponseFormat()
    private val jsonSchema = format.getJSONObject("json_schema")
    private val schema = jsonSchema.getJSONObject("schema")
    private val properties = schema.getJSONObject("properties")

    @Test
    fun schemaNameIsDistinct() {
        assertEquals("create_draft_move", jsonSchema.getString("name"))
        assertFalse(format.toString().contains("conversation_decision\""))
        assertFalse(format.toString().contains("task_agent_response\""))
    }

    @Test
    fun allFieldsAreRequiredAndAdditionalPropertiesAreDisabled() {
        val required = schema.getJSONArray("required")
        val fields = (0 until required.length()).map(required::getString).toSet()
        assertEquals(
            setOf("move", "field", "value", "date_text", "time_text", "confidence"),
            fields
        )
        assertFalse(schema.getBoolean("additionalProperties"))
        assertTrue(jsonSchema.getBoolean("strict"))
    }

    @Test
    fun moveAndFieldEnumsAreComplete() {
        assertEquals(
            setOf(
                "CONFIRM_SAVE", "REJECT_SAVE", "CHANGE_FIELD", "PROVIDE_FIELD",
                "PROVIDE_SCHEDULE", "READ_TITLE",
                "READ_DATE", "READ_TIME", "READ_SCHEDULE", "READ_SUMMARY", "CANCEL",
                "REQUEST_HELP", "UNKNOWN"
            ),
            enumValues("move")
        )
        assertEquals(setOf("", "TITLE", "DATE", "TIME"), enumValues("field"))
        assertTrue(properties.has("date_text"))
        assertTrue(properties.has("time_text"))
    }

    @Test
    fun confidenceRangeIsBounded() {
        val confidence = properties.getJSONObject("confidence")
        assertEquals(0.0, confidence.getDouble("minimum"), 0.0)
        assertEquals(1.0, confidence.getDouble("maximum"), 0.0)
    }

    private fun enumValues(name: String): Set<String> {
        val values = properties.getJSONObject(name).getJSONArray("enum")
        return (0 until values.length()).map(values::getString).toSet()
    }
}
