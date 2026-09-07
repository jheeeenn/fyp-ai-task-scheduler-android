package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.ai.conversation.ConversationSchemaException
import com.example.myapplication.voice.CreateDraftField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CreateDraftAgentDecisionParserTest {
    private val parser = CreateDraftAgentDecisionParser()

    @Test
    fun exactValidResponseIsAccepted() {
        val decision = parser.parse(
            json("CHANGE_FIELD", field = "TIME", value = "10 AM", confidence = 0.96)
        )
        assertEquals(CreateDraftAgentMoveType.CHANGE_FIELD, decision.move)
        assertEquals(CreateDraftField.TIME, decision.field)
        assertEquals("10 AM", decision.value)
        assertEquals(0.96, decision.confidence, 0.0)
    }

    @Test
    fun combinedScheduleAndReadMovesUseStrictShapes() {
        val schedule = parser.parse(
            json("PROVIDE_SCHEDULE", date = "Sunday", time = "9 PM")
        )
        assertEquals(CreateDraftAgentMoveType.PROVIDE_SCHEDULE, schedule.move)
        assertEquals("Sunday", schedule.dateText)
        assertEquals("9 PM", schedule.timeText)

        val read = parser.parse(json("READ_TIME"))
        assertEquals(CreateDraftAgentMoveType.READ_TIME, read.move)
        assertEquals(null, read.field)
        assertEquals("", read.value)
        assertEquals("", read.dateText)
        assertEquals("", read.timeText)
    }

    @Test
    fun missingFieldIsRejected() {
        assertRejected("""{"move":"CANCEL","field":"","value":""}""")
    }

    @Test
    fun additionalFieldIsRejected() {
        assertRejected(validUnknown().dropLast(1) + ",\"extra\":true}")
        assertRejected("Explanation: ${validUnknown()}")
    }

    @Test
    fun invalidMoveIsRejected() {
        assertRejected(json("SAVE_TASK"))
    }

    @Test
    fun invalidFieldIsRejected() {
        assertRejected(json("CHANGE_FIELD", field = "PRIORITY", value = "high"))
    }

    @Test
    fun confidenceOutsideRangeIsRejected() {
        assertRejected(json("UNKNOWN", confidence = 1.1))
        assertRejected(json("UNKNOWN", confidence = -0.1))
    }

    @Test
    fun taskAgentFieldsAreRejected() {
        assertRejected(validUnknown().dropLast(1) + ",\"task_title\":\"revision\"}")
    }

    @Test
    fun moveSpecificFieldAndValueRequirementsAreEnforced() {
        assertRejected(json("CHANGE_FIELD", value = "revision"))
        assertRejected(json("PROVIDE_FIELD", field = "TITLE"))
        assertRejected(json("PROVIDE_FIELD", field = "DATE", value = "Sunday", time = "9 PM"))
        assertRejected(json("PROVIDE_SCHEDULE", date = "Sunday"))
        assertRejected(json("PROVIDE_SCHEDULE", field = "DATE", date = "Sunday", time = "9 PM"))
        assertRejected(json("APPLY_UNSPECIFIED_CORRECTION"))
        assertRejected(json("CONFIRM_SAVE", field = "TIME"))
        assertRejected(json("READ_TITLE", value = "Buy medicine"))
        assertRejected(json("READ_TIME", time = "9 PM"))
    }

    @Test
    fun matchingReadFieldsAreCanonicalizedAndContradictionsAreRejected() {
        listOf(
            Triple("READ_TITLE", "TITLE", CreateDraftAgentMoveType.READ_TITLE),
            Triple("READ_DATE", "DATE", CreateDraftAgentMoveType.READ_DATE),
            Triple("READ_TIME", "TIME", CreateDraftAgentMoveType.READ_TIME)
        ).forEach { (move, field, expectedMove) ->
            assertEquals(null, parser.parse(json(move)).field)
            val redundant = parser.parse(json(move, field = field))
            assertEquals(expectedMove, redundant.move)
            assertEquals(null, redundant.field)
        }

        listOf(
            "READ_TITLE" to listOf("DATE", "TIME"),
            "READ_DATE" to listOf("TITLE", "TIME"),
            "READ_TIME" to listOf("TITLE", "DATE")
        ).forEach { (move, contradictoryFields) ->
            contradictoryFields.forEach { field -> assertRejected(json(move, field = field)) }
        }
    }

    @Test
    fun readCanonicalizationNeverAcceptsDraftFacts() {
        assertRejected(json("READ_TITLE", field = "TITLE", value = "Buy medicine"))
        assertRejected(json("READ_DATE", field = "DATE", date = "Sunday"))
        assertRejected(json("READ_TIME", field = "TIME", time = "9 PM"))
        assertRejected(json("READ_SCHEDULE", field = "DATE"))
        assertRejected(json("READ_SUMMARY", field = "TITLE"))
    }

    @Test
    fun controlMovesRejectEveryNonEmptyValue() {
        listOf(
            "CONFIRM_SAVE",
            "REJECT_SAVE",
            "READ_TITLE",
            "READ_DATE",
            "READ_TIME",
            "READ_SCHEDULE",
            "READ_SUMMARY",
            "CANCEL",
            "REQUEST_HELP",
            "UNKNOWN"
        ).forEach { move ->
            assertRejected(json(move, value = "unexpected"))
            assertRejected(json(move, value = " "))
        }
    }

    private fun validUnknown() = json("UNKNOWN")

    private fun json(
        move: String,
        field: String = "",
        value: String = "",
        date: String = "",
        time: String = "",
        confidence: Double = 0.9
    ): String = """{"move":"$move","field":"$field","value":"$value","date_text":"$date","time_text":"$time","confidence":$confidence}"""

    private fun assertRejected(json: String) {
        assertThrows(ConversationSchemaException::class.java) { parser.parse(json) }
    }
}
