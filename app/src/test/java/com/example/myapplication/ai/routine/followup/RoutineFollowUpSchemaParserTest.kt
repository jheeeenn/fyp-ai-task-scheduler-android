package com.example.myapplication.ai.routine.followup

import com.example.myapplication.ai.conversation.ConversationSchemaException
import com.example.myapplication.ai.schema.AgentResponseSchemas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineFollowUpSchemaParserTest {
    private val parser = RoutineFollowUpAgentDecisionParser()

    @Test
    fun strictSchemaHasOnlyFourRequiredRootFields() {
        val format = AgentResponseSchemas.routineFollowUpMoveResponseFormat()
        val jsonSchema = format.getJSONObject("json_schema")
        val schema = jsonSchema.getJSONObject("schema")
        val properties = schema.getJSONObject("properties")
        val required = schema.getJSONArray("required")
        val fields = (0 until required.length()).map(required::getString).toSet()

        assertEquals("routine_follow_up_move", jsonSchema.getString("name"))
        assertEquals(setOf("move", "step_index", "value", "confidence"), fields)
        assertEquals(fields, properties.keys().asSequence().toSet())
        assertFalse(schema.getBoolean("additionalProperties"))
        assertTrue(jsonSchema.getBoolean("strict"))
    }

    @Test
    fun parserAcceptsEveryAllowedMoveWithItsRequiredShape() {
        val cases = mapOf(
            "CONFIRM" to Pair(0, ""),
            "REJECT" to Pair(0, ""),
            "CANCEL" to Pair(0, ""),
            "REPEAT" to Pair(0, ""),
            "PROVIDE_SHARED_DATE" to Pair(0, "next Tuesday"),
            "PROVIDE_STEP_TIME" to Pair(0, "8:15 AM"),
            "CHANGE_SHARED_DATE" to Pair(0, "4 August 2026"),
            "CHANGE_STEP_TIME" to Pair(2, "8:30 PM"),
            "CHANGE_STEP_TITLE" to Pair(3, "charge my phone"),
            "STRUCTURAL_CHANGE" to Pair(0, ""),
            "REQUEST_HELP" to Pair(0, ""),
            "UNKNOWN" to Pair(0, "")
        )

        cases.forEach { (move, shape) ->
            assertEquals(
                RoutineFollowUpAgentMove.valueOf(move),
                parser.parse(json(move, shape.first, shape.second)).move
            )
        }
    }

    @Test
    fun additionalFieldsInvalidMovesWrongTypesAndIndexesAreRejected() {
        val invalid = listOf(
            """{"move":"CONFIRM","step_index":0,"value":"","confidence":0.9,"reply":"done"}""",
            json("SAVE", 0, ""),
            """{"move":"CONFIRM","step_index":"0","value":"","confidence":0.9}""",
            """{"move":"CONFIRM","step_index":0,"value":false,"confidence":0.9}""",
            """{"move":"CONFIRM","step_index":0.5,"value":"","confidence":0.9}""",
            json("CHANGE_STEP_TIME", 0, "8 PM"),
            json("PROVIDE_STEP_TIME", 2, "8 PM"),
            json("CONFIRM", 6, "")
        )
        invalid.forEach { raw ->
            assertThrows(ConversationSchemaException::class.java) { parser.parse(raw) }
        }
    }

    @Test
    fun nonFiniteAndOutOfRangeConfidenceAreRejected() {
        listOf(
            json("CONFIRM", 0, "", -0.01),
            json("CONFIRM", 0, "", 1.01),
            """{"move":"CONFIRM","step_index":0,"value":"","confidence":NaN}""",
            """{"move":"CONFIRM","step_index":0,"value":"","confidence":"0.9"}"""
        ).forEach { raw ->
            assertThrows(ConversationSchemaException::class.java) { parser.parse(raw) }
        }
    }

    private fun json(
        move: String,
        stepIndex: Int,
        value: String,
        confidence: Double = 0.9
    ): String = """
        {"move":"$move","step_index":$stepIndex,"value":"$value","confidence":$confidence}
    """.trimIndent()
}
