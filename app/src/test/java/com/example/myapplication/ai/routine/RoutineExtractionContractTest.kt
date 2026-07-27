package com.example.myapplication.ai.routine

import com.example.myapplication.ai.schema.AgentResponseSchemas
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineExtractionContractTest {
    private val parser = RoutineExtractionResponseParser()

    @Test
    fun strictSchemaDocumentsOnlyFourRootFieldsAndTwoToFiveSteps() {
        val format = AgentResponseSchemas.routineExtractionResponseFormat()
        val named = format.getJSONObject("json_schema")
        val schema = named.getJSONObject("schema")
        val properties = schema.getJSONObject("properties")
        val required = schema.getJSONArray("required")
        val requiredFields =
            (0 until required.length()).map(required::getString).toSet()

        assertEquals("routine_extraction_response", named.getString("name"))
        assertTrue(named.getBoolean("strict"))
        assertFalse(schema.getBoolean("additionalProperties"))
        assertEquals(
            setOf("routine_title", "steps", "confidence", "need_clarification"),
            requiredFields
        )
        val steps = properties.getJSONObject("steps")
        assertEquals(2, steps.getInt("minItems"))
        assertEquals(5, steps.getInt("maxItems"))
        val item = steps.getJSONObject("items")
        assertFalse(item.getBoolean("additionalProperties"))
        val stepRequired = item.getJSONArray("required")
        assertEquals(
            setOf("title", "date_text", "time_text"),
            (0 until stepRequired.length()).map(stepRequired::getString).toSet()
        )
        val serialized = format.toString()
        listOf("id", "is_done", "success", "recurrence", "plan").forEach {
            assertFalse("Unexpected wire field $it", serialized.contains("\"$it\""))
        }
    }

    @Test
    fun parserAcceptsTwoThroughFiveValidOrderedSteps() {
        for (count in 2..5) {
            val parsed = parser.parse(validJson(count))
            assertEquals(count, parsed.steps.size)
            assertEquals(
                (1..count).map { "step $it" },
                parsed.steps.map(RoutineStepExtraction::title)
            )
        }
    }

    @Test
    fun parserRejectsFewerThanTwoAndMoreThanFiveSteps() {
        assertThrows(RoutineExtractionParseException::class.java) {
            parser.parse(validJson(1))
        }
        assertThrows(RoutineExtractionParseException::class.java) {
            parser.parse(validJson(6))
        }
    }

    @Test
    fun parserRejectsUnknownRootAndStepFields() {
        assertThrows(RoutineExtractionParseException::class.java) {
            parser.parse(
                validJson(2).replace(
                    "\"need_clarification\":false",
                    "\"need_clarification\":false,\"natural_response\":\"done\""
                )
            )
        }
        assertThrows(RoutineExtractionParseException::class.java) {
            parser.parse(
                validJson(2).replace(
                    "\"time_text\":\"8 AM\"",
                    "\"time_text\":\"8 AM\",\"id\":42"
                )
            )
        }
    }

    @Test
    fun modelSuccessWordingOutsideTheStrictObjectIsIgnored() {
        val parsed = parser.parse("Created successfully. ${validJson(2)}")

        assertEquals("Morning routine", parsed.routineTitle)
        assertEquals(2, parsed.steps.size)
    }

    @Test
    fun parserRejectsWrongWireTypesAndOutOfRangeConfidence() {
        assertThrows(RoutineExtractionParseException::class.java) {
            parser.parse(validJson(2).replace("\"confidence\":0.98", "\"confidence\":1.1"))
        }
        assertThrows(RoutineExtractionParseException::class.java) {
            parser.parse(validJson(2).replace("\"date_text\":\"tomorrow\"", "\"date_text\":7"))
        }
    }

    private fun validJson(count: Int): String {
        val steps = (1..count).joinToString(",") {
            JSONObject().apply {
                put("title", "step $it")
                put("date_text", "tomorrow")
                put("time_text", "8 AM")
            }.toString()
        }
        return """
            {
              "routine_title":"Morning routine",
              "steps":[$steps],
              "confidence":0.98,
              "need_clarification":false
            }
        """.trimIndent()
    }
}
