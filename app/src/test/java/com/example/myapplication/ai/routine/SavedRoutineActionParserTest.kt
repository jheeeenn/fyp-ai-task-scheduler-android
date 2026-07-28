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
        assertTrue(prompt.contains("what routines have I saved"))
        assertTrue(prompt.contains("list my routines"))
        assertTrue(prompt.contains("read my bedtime routine"))
        assertTrue(prompt.contains("read my routine"))
        assertTrue(prompt.contains("what is in my routine"))
        assertTrue(prompt.contains("read the routine"))
        assertTrue(prompt.contains("use my routine tomorrow"))
        assertTrue(prompt.contains("delete my routine"))
        assertTrue(prompt.contains("\"routine_title\":\"routine\""))
        assertTrue(prompt.contains("semantic guidance, not an exhaustive phrase dictionary"))
        assertTrue(prompt.contains("Do not convert an explicit singular read request into LIST"))
        assertTrue(prompt.contains("delete my medicine routine"))
        assertTrue(prompt.contains("Do not output Room IDs"))
        assertTrue(prompt.contains("There is no repair request"))
        mapOf(
            "what routines have I saved" to
                """{"action":"LIST","routine_title":"","date_text":"","confidence":0.98}""",
            "list my routines" to
                """{"action":"LIST","routine_title":"","date_text":"","confidence":0.98}""",
            "read my bedtime routine" to
                """{"action":"READ_DETAILS","routine_title":"bedtime routine","date_text":"","confidence":0.98}""",
            "read my routine" to
                """{"action":"READ_DETAILS","routine_title":"routine","date_text":"","confidence":0.98}""",
            "what is in my routine" to
                """{"action":"READ_DETAILS","routine_title":"routine","date_text":"","confidence":0.98}""",
            "read the routine" to
                """{"action":"READ_DETAILS","routine_title":"routine","date_text":"","confidence":0.97}""",
            "use my routine tomorrow" to
                """{"action":"RUN","routine_title":"routine","date_text":"tomorrow","confidence":0.98}""",
            "delete my routine" to
                """{"action":"DELETE","routine_title":"routine","date_text":"","confidence":0.98}"""
        ).forEach { (userText, expectedJson) ->
            assertTrue(
                prompt.contains("User: $userText\n$expectedJson")
            )
        }
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
