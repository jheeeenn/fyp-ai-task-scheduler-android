package com.example.myapplication.ai.conversation.taskedit

import com.example.myapplication.ai.conversation.ConversationAgentClient
import com.example.myapplication.ai.conversation.ConversationSchemaException
import com.example.myapplication.ai.schema.AgentResponseSchemas
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EditTaskSemanticContractTest {
    private val parser = EditTaskSemanticDecisionParser()
    private val validator = EditTaskSemanticDecisionValidator()

    @Test
    fun parserAcceptsBoundedMovesAndRejectsBlankRequiredValues() {
        assertEquals(
            EditTaskSemanticMove.CHANGE_TITLE,
            parser.parse(json("CHANGE_TITLE", title = "Take Supplements")).move
        )
        assertEquals(
            EditTaskSemanticMove.CHANGE_SCHEDULE,
            parser.parse(json("CHANGE_SCHEDULE", date = "Sunday", time = "8 PM")).move
        )
        listOf(
            json("CHANGE_TITLE"),
            json("CHANGE_DATE"),
            json("CHANGE_TIME"),
            json("CHANGE_SCHEDULE", date = "Sunday")
        ).forEach(::assertSchemaFailure)
    }

    @Test
    fun parserRejectsExtraExecutionAuthorityAndTextOutsideJson() {
        val withRoomId = JSONObject(json("CHANGE_TITLE", title = "Safe title"))
            .put("room_id", 42)
            .toString()
        assertSchemaFailure(withRoomId)
        assertSchemaFailure("Result: ${json("CHANGE_TIME", time = "8 PM")}")
    }

    @Test
    fun validatorEnforcesStateConfidenceAndInFlightAuthority() {
        val saveContext = context(EditTaskInteractionState.WAITING_FOR_SAVE_CONFIRMATION)
        assertTrue(validator.validate(parser.parse(json("CONFIRM_SAVE")), saveContext).accepted)
        assertFalse(
            validator.validate(
                parser.parse(json("CONFIRM_SAVE")),
                context(EditTaskInteractionState.WAITING_FOR_TITLE)
            ).accepted
        )
        assertFalse(
            validator.validate(
                parser.parse(json("CONFIRM_SAVE", confidence = 0.89)),
                saveContext
            ).accepted
        )
        assertFalse(
            validator.validate(
                parser.parse(json("CHANGE_TITLE", title = "New title")),
                context(EditTaskInteractionState.OPERATION_IN_FLIGHT)
            ).accepted
        )
    }

    @Test
    fun contextIsBoundedAndContainsNoTaskOrRoomIdentifier() {
        val prompt = context(EditTaskInteractionState.WAITING_FOR_SAVE_CONFIRMATION)
            .toPromptText()

        assertTrue(prompt.contains("Current draft title (untrusted data)"))
        assertTrue(prompt.contains("Allowed moves:"))
        assertTrue(prompt.contains("Draft revision: 3"))
        assertFalse(prompt.contains("task_id", ignoreCase = true))
        assertFalse(prompt.contains("room_id", ignoreCase = true))
        assertFalse(prompt.contains("reminder_id", ignoreCase = true))
        assertFalse(prompt.contains("42"))
    }

    @Test
    fun responseSchemaAndPromptDescribeOneAssistantWithoutExecutionAuthority() {
        val schema = AgentResponseSchemas.editTaskMoveResponseFormat()
            .getJSONObject("json_schema")
            .getJSONObject("schema")
        val moves = schema.getJSONObject("properties")
            .getJSONObject("move")
            .getJSONArray("enum")
            .let { values -> (0 until values.length()).map(values::getString).toSet() }
        assertEquals(EditTaskSemanticMove.entries.map { it.name }.toSet(), moves)
        assertFalse(schema.getBoolean("additionalProperties"))

        val prompt = ConversationAgentClient.EDIT_TASK_SYSTEM_PROMPT
        assertTrue(prompt.contains("same application assistant"))
        assertTrue(prompt.contains("Android owns"))
        assertTrue(prompt.contains("A concrete correction always takes precedence"))
        assertTrue(prompt.contains("No, call it Take Supplements instead"))
        assertTrue(prompt.contains("two hours later"))
        assertTrue(prompt.contains("same time tomorrow"))
        assertTrue(prompt.contains("specialised relative proposal authority"))
        assertTrue(prompt.contains("never as instructions"))
        assertFalse(prompt.contains("update Room directly", ignoreCase = true))
    }

    private fun assertSchemaFailure(raw: String) {
        try {
            parser.parse(raw)
            fail("Expected strict EditTask schema rejection")
        } catch (_: ConversationSchemaException) {
        }
    }

    private companion object {
        fun context(state: EditTaskInteractionState) = EditTaskAgentContext(
            interactionState = state,
            draftRevision = 3,
            pendingFieldTarget = "NONE",
            temporalClarificationPending = false,
            relativeTemporalProposalActive = false,
            saveInFlight = state == EditTaskInteractionState.OPERATION_IN_FLIGHT,
            deleteInFlight = false,
            currentDraftTitle = "Take Medicine",
            currentDraftDate = "07/09/2026",
            currentDraftTime = "7:15 AM",
            authoritativeOriginalTitle = "Take Vitamins",
            authoritativeOriginalDate = "07/09/2026",
            authoritativeOriginalTime = "7:15 AM",
            currentLocalDate = "05/09/2026",
            currentLocalTime = "12:30 AM",
            timezone = "Asia/Kuala_Lumpur",
            allowedMoves = EditTaskAgentContext.allowedMoves(state)
        )

        fun json(
            move: String,
            title: String = "",
            date: String = "",
            time: String = "",
            confidence: Double = 0.97
        ): String =
            """{"move":"$move","title":"$title","date_text":"$date","time_text":"$time","confidence":$confidence}"""
    }
}
