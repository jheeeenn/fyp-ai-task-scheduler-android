package com.example.myapplication.ai.routine

import com.example.myapplication.ai.conversation.ConversationAgentClient
import com.example.myapplication.ai.routine.saved.SavedRoutineAction
import com.example.myapplication.ai.routine.saved.SavedRoutineActionParser
import com.example.myapplication.ai.routine.saved.SavedRoutineActionSchemaException
import com.example.myapplication.ai.schema.AgentResponseSchemas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedRoutineActionParserTest {
    private val parser = SavedRoutineActionParser()

    @Test
    fun acceptsStrictValidActionsAndPreservesLiteralDate() {
        assertEquals(
            SavedRoutineAction.LIST,
            parser.parse(json("LIST")).action
        )
        assertEquals(
            SavedRoutineAction.READ_DETAILS,
            parser.parse(json("READ_DETAILS", "study routine")).action
        )
        assertEquals(
            SavedRoutineAction.DELETE,
            parser.parse(json("DELETE", "medicine routine")).action
        )
        val run = parser.parse(json("RUN", "morning routine", "next Tuesday"))
        assertEquals(SavedRoutineAction.RUN, run.action)
        assertEquals("morning routine", run.routineTitle)
        assertEquals("next Tuesday", run.dateText)
    }

    @Test
    fun rejectsMissingExtraUnknownInvalidAndLowConfidenceOutput() {
        listOf(
            """{"action":"LIST","routine_title":"","date_text":""}""",
            """{"action":"LIST","routine_title":"","date_text":"","confidence":0.9,"id":4}""",
            json("CREATE", "morning routine"),
            json("RUN", "", "tomorrow"),
            json("LIST", "morning routine"),
            json("READ_DETAILS", "study routine", "tomorrow"),
            json("DELETE", ""),
            json("RUN", "study routine", confidence = 0.79)
        ).forEach { invalid ->
            assertThrows(SavedRoutineActionSchemaException::class.java) {
                parser.parse(invalid)
            }
        }
    }

    @Test
    fun schemaClientConfigurationAndNaturalExamplesRemainBounded() {
        val schema = AgentResponseSchemas.savedRoutineActionResponseFormat().toString()
        val prompt = ConversationAgentClient.SAVED_ROUTINE_ACTION_SYSTEM_PROMPT

        assertEquals(0.0, ConversationAgentClient.SAVED_ROUTINE_ACTION_TEMPERATURE, 0.0)
        assertTrue(schema.contains("\"additionalProperties\":false"))
        assertTrue(schema.contains("\"action\""))
        assertTrue(schema.contains("\"routine_title\""))
        assertTrue(schema.contains("\"date_text\""))
        assertTrue(schema.contains("\"confidence\""))
        assertTrue(prompt.contains("use my morning routine tomorrow"))
        assertTrue(prompt.contains("start the study routine next Tuesday"))
        assertTrue(prompt.contains("what routines do I have"))
        assertTrue(prompt.contains("read my bedtime routine"))
        assertTrue(prompt.contains("delete my medicine routine"))
        assertTrue(prompt.contains("Do not output Room IDs"))
        assertTrue(prompt.contains("There is no repair request"))
    }

    private fun json(
        action: String,
        title: String = "",
        date: String = "",
        confidence: Double = 0.95
    ): String = """
        {
          "action":"$action",
          "routine_title":"$title",
          "date_text":"$date",
          "confidence":$confidence
        }
    """.trimIndent()
}
