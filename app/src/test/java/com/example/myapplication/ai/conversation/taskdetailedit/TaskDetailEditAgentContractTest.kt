package com.example.myapplication.ai.conversation.taskdetailedit

import com.example.myapplication.ai.conversation.ConversationSchemaException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TaskDetailEditAgentContractTest {
    private val parser = TaskDetailEditAgentDecisionParser()
    private val validator = TaskDetailEditAgentDecisionValidator()

    @Test
    fun strictSetTitleIsAcceptedOnlyForTitle() {
        val decision = parse("SET_TITLE", title = "breakfast preparation")
        assertTrue(validator.validate(decision, TaskDetailEditField.TITLE).accepted)
        assertFalse(validator.validate(decision, TaskDetailEditField.DATE).accepted)
        assertFalse(validator.validate(decision, TaskDetailEditField.TIME).accepted)
    }

    @Test
    fun setDateAndSetTimeAreBoundToTheirRequestedFields() {
        val date = parse("SET_DATE", date = "next Friday")
        val time = parse("SET_TIME", time = "11:45 AM")
        assertTrue(validator.validate(date, TaskDetailEditField.DATE).accepted)
        assertFalse(validator.validate(date, TaskDetailEditField.TIME).accepted)
        assertTrue(validator.validate(time, TaskDetailEditField.TIME).accepted)
        assertFalse(validator.validate(time, TaskDetailEditField.DATE).accepted)
    }

    @Test
    fun setScheduleCanSupplyBothFieldsFromDateOrTimeFocus() {
        val decision = parse("SET_SCHEDULE", date = "tomorrow", time = "11:45 AM")
        listOf(TaskDetailEditField.DATE, TaskDetailEditField.TIME).forEach { field ->
            val validation = validator.validate(decision, field)
            assertTrue(validation.accepted)
            assertEquals(
                TaskDetailEditProposal.Schedule("tomorrow", "11:45 AM"),
                validation.proposal
            )
        }
        assertFalse(validator.validate(decision, TaskDetailEditField.TITLE).accepted)
    }

    @Test
    fun malformedExtraAuthorityFieldsAndTextOutsideJsonAreRejected() {
        assertSchemaFailure(
            """{"move":"SET_TIME","title":"","date_text":"","time_text":"9 PM","clarification":"","confidence":0.9,"room_id":42}"""
        )
        assertSchemaFailure(
            "result: " + json("SET_TIME", time = "9 PM")
        )
        assertSchemaFailure(
            """{"move":"SET_TIME","title":"leak","date_text":"","time_text":"9 PM","clarification":"","confidence":0.9}"""
        )
    }

    @Test
    fun lowConfidenceAndIndirectClarificationsAreRejected() {
        assertFalse(
            validator.validate(
                parse("SET_TIME", time = "9 PM", confidence = 0.79),
                TaskDetailEditField.TIME
            ).accepted
        )
        assertFalse(
            validator.validate(
                parse("ASK_CLARIFICATION", clarification = "Please try again."),
                TaskDetailEditField.TIME
            ).accepted
        )
        assertTrue(
            validator.validate(
                parse("ASK_CLARIFICATION", clarification = "Did you mean 8:30 AM or 8:30 PM?"),
                TaskDetailEditField.TIME
            ).accepted
        )
    }

    @Test
    fun contextContainsBoundedScheduleFactsButNoRoomOrTaskIdentifier() {
        val prompt = TaskDetailEditAgentContext(
            requestedField = TaskDetailEditField.TIME,
            interactionState = "WAITING_FOR_TIME",
            interactionGeneration = 7,
            draftRevision = 3,
            hasTitle = true,
            currentDueDate = "03/08/2026",
            currentDueTime = "11:00 PM",
            currentLocalDate = "03/08/2026",
            currentLocalTime = "11:26 PM",
            timezone = "Asia/Kuala_Lumpur",
            currentSchedulePast = true,
            pendingClarification = "",
            allowedMoves = TaskDetailEditAgentContext.allowedMoves(TaskDetailEditField.TIME)
        ).toPromptText()

        assertTrue(prompt.contains("Changing TIME may require changing DATE"))
        assertTrue(prompt.contains("Relative expressions use the current draft schedule as their base"))
        assertFalse(prompt.contains("Room ID", ignoreCase = true))
        assertFalse(prompt.contains("task ID", ignoreCase = true))
        assertFalse(prompt.contains("breakfast preparation"))
    }

    private fun parse(
        move: String,
        title: String = "",
        date: String = "",
        time: String = "",
        clarification: String = "",
        confidence: Double = 0.95
    ) = parser.parse(json(move, title, date, time, clarification, confidence))

    private fun json(
        move: String,
        title: String = "",
        date: String = "",
        time: String = "",
        clarification: String = "",
        confidence: Double = 0.95
    ): String = """{"move":"$move","title":"$title","date_text":"$date","time_text":"$time","clarification":"$clarification","confidence":$confidence}"""

    private fun assertSchemaFailure(raw: String) {
        try {
            parser.parse(raw)
            fail("Expected strict schema rejection")
        } catch (_: ConversationSchemaException) {
        }
    }
}
