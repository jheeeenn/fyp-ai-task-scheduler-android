package com.example.myapplication.ai.conversation.taskdetailedit

import com.example.myapplication.ai.conversation.ConversationAgentClient
import com.example.myapplication.ai.conversation.ConversationSchemaException
import com.example.myapplication.ai.schema.AgentResponseSchemas
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
    fun saveConfirmationMovesAreStrictAndStateScoped() {
        val context = saveContext()
        listOf(
            "CONFIRM_SAVE",
            "REJECT_SAVE",
            "REQUEST_TITLE_CHANGE",
            "REQUEST_DATE_CHANGE",
            "REQUEST_TIME_CHANGE",
            "READ_TITLE",
            "READ_DATE",
            "READ_TIME",
            "READ_SCHEDULE"
        ).forEach { move ->
            val validation = validator.validate(parse(move), context)
            assertTrue(move, validation.accepted)
        }
        assertFalse(
            validator.validate(parse("READ_TIME"), TaskDetailEditField.TIME).accepted
        )
        assertFalse(
            validator.validate(parse("CONFIRM_SAVE"), TaskDetailEditField.TITLE).accepted
        )
        assertFalse(
            validator.validate(parse("CONFIRM_SAVE", confidence = 0.89), context).accepted
        )
        listOf(
            "WAITING_FOR_HOME_CONFIRMATION",
            "WAITING_FOR_BACK_CONFIRMATION",
            "WAITING_FOR_ASSISTANT_EXIT_CONFIRMATION",
            "WAITING_FOR_DELETE_DISCARD_CONFIRMATION"
        ).forEach { state ->
            val sensitiveContext = context.copy(interactionState = state)
            assertFalse(state, validator.validate(parse("CONFIRM_SAVE"), sensitiveContext).accepted)
            assertFalse(state, validator.validate(parse("READ_TIME"), sensitiveContext).accepted)
            assertFalse(
                state,
                validator.validate(parse("SET_TIME", time = "8 PM"), sensitiveContext).accepted
            )
        }
        assertSchemaFailure(json("READ_TIME", time = "8 PM"))
        assertSchemaFailure(json("CONFIRM_SAVE", title = "leak"))
    }

    @Test
    fun responseSchemaAndPromptCoverConversationWithoutExecutionAuthority() {
        val schema = AgentResponseSchemas.taskDetailEditMoveResponseFormat()
            .getJSONObject("json_schema")
            .getJSONObject("schema")
        val moves = schema.getJSONObject("properties")
            .getJSONObject("move")
            .getJSONArray("enum")
            .let { values -> (0 until values.length()).map(values::getString).toSet() }
        assertEquals(TaskDetailEditAgentMove.entries.map { it.name }.toSet(), moves)
        assertFalse(schema.getBoolean("additionalProperties"))

        val prompt = ConversationAgentClient.TASK_DETAIL_EDIT_SYSTEM_PROMPT
        listOf(
            "same application assistant",
            "complete conversational act",
            "State constrains authority",
            "explicit correction, field-change request, or read request takes precedence",
            "What time is it set for?",
            "Change the time to 8 PM",
            "usable replacement candidate",
            "selected with no replacement candidate is REQUEST_*",
            "No make it 8:00 p.m. instead",
            "moves never mutate the draft",
            "Android owns all current draft facts",
            "do not calculate",
            "Do not invent a missing meridiem",
            "not an exhaustive phrase dictionary"
        ).forEach { expected -> assertTrue(expected, prompt.contains(expected)) }
        assertTrue(prompt.contains("No change the time instead"))
        assertTrue(prompt.contains("Move it to Sunday at 9 AM instead"))
        assertFalse(prompt.contains("update Room directly", ignoreCase = true))
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

    @Test
    fun saveContextHasNoArtificialRequestedField() {
        val prompt = saveContext().toPromptText()

        assertTrue(prompt.contains("Requested field: NONE"))
        assertTrue(prompt.contains("responding to Android's save-confirmation question"))
        assertFalse(prompt.contains("answering one question about TITLE"))
    }

    private fun saveContext() = TaskDetailEditAgentContext(
        requestedField = null,
        interactionState = "WAITING_FOR_SAVE_CONFIRMATION",
        interactionGeneration = 8,
        draftRevision = 4,
        hasTitle = true,
        currentDueDate = "10/08/2026",
        currentDueTime = "08:00 PM",
        currentLocalDate = "08/08/2026",
        currentLocalTime = "06:00 PM",
        timezone = "Asia/Kuala_Lumpur",
        currentSchedulePast = false,
        pendingClarification = "",
        allowedMoves = TaskDetailEditAgentContext.saveConfirmationMoves()
    )

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
