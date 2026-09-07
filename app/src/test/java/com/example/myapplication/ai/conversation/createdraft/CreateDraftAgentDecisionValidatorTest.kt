package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateDraftMove
import com.example.myapplication.voice.CreateDraftReadTarget
import com.example.myapplication.voice.CreateTaskDialogState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateDraftAgentDecisionValidatorTest {
    private val validator = CreateDraftAgentDecisionValidator()

    @Test
    fun confirmSaveRequiresSaveConfirmationAndHighConfidence() {
        assertAccepted(CreateDraftMove.ConfirmSave, decision(CreateDraftAgentMoveType.CONFIRM_SAVE, confidence = 0.90), saveState)
        assertRejected(decision(CreateDraftAgentMoveType.CONFIRM_SAVE, confidence = 0.89), saveState)
        assertRejected(decision(CreateDraftAgentMoveType.CONFIRM_SAVE, confidence = 0.99), CreateTaskDialogState.WAITING_FOR_TIME)
    }

    @Test
    fun provideTimeMatchesExpectedField() {
        assertAccepted(
            CreateDraftMove.ProvideField(CreateDraftField.TIME, "9 AM"),
            decision(CreateDraftAgentMoveType.PROVIDE_FIELD, CreateDraftField.TIME, "9 AM"),
            CreateTaskDialogState.WAITING_FOR_TIME
        )
        assertRejected(
            decision(CreateDraftAgentMoveType.PROVIDE_FIELD, CreateDraftField.TITLE, "revision"),
            CreateTaskDialogState.WAITING_FOR_TIME
        )
    }

    @Test
    fun changeTimeIsAcceptedAtSaveConfirmation() {
        assertAccepted(
            CreateDraftMove.ChangeField(CreateDraftField.TIME, "10 AM"),
            decision(CreateDraftAgentMoveType.CHANGE_FIELD, CreateDraftField.TIME, "10 AM"),
            saveState
        )
    }

    @Test
    fun combinedScheduleIsAcceptedOnlyInScheduleCapableStates() {
        val expected = CreateDraftMove.ProvideSchedule("Sunday", "9 PM")
        listOf(
            CreateTaskDialogState.WAITING_FOR_DATE,
            CreateTaskDialogState.WAITING_FOR_TIME,
            CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD,
            saveState
        ).forEach { state ->
            assertAccepted(
                expected,
                decision(
                    CreateDraftAgentMoveType.PROVIDE_SCHEDULE,
                    dateText = "Sunday",
                    timeText = "9 PM"
                ),
                state
            )
        }
        assertRejected(
            decision(
                CreateDraftAgentMoveType.PROVIDE_SCHEDULE,
                dateText = "Sunday",
                timeText = "9 PM"
            ),
            CreateTaskDialogState.WAITING_FOR_TITLE
        )
    }

    @Test
    fun readMovesAreObservationalAndRejectedDuringSaveExecution() {
        assertAccepted(
            CreateDraftMove.ReadDraft(CreateDraftReadTarget.TIME),
            decision(CreateDraftAgentMoveType.READ_TIME),
            saveState
        )
        assertAccepted(
            CreateDraftMove.ReadDraft(CreateDraftReadTarget.TITLE),
            decision(CreateDraftAgentMoveType.READ_TITLE),
            CreateTaskDialogState.WAITING_FOR_DATE
        )
        assertRejected(
            decision(CreateDraftAgentMoveType.READ_SCHEDULE),
            CreateTaskDialogState.READY_TO_SAVE
        )
    }

    @Test
    fun unspecifiedCorrectionIsRejectedOutsideSaveConfirmation() {
        assertRejected(
            decision(CreateDraftAgentMoveType.APPLY_UNSPECIFIED_CORRECTION, value = "tomorrow"),
            CreateTaskDialogState.WAITING_FOR_DATE
        )
    }

    @Test
    fun lowConfidenceMoveBecomesUnknown() {
        assertRejected(
            decision(
                CreateDraftAgentMoveType.CHANGE_FIELD,
                CreateDraftField.TITLE,
                "revision",
                confidence = 0.74
            ),
            saveState
        )
    }

    @Test
    fun cancelIsSafeBeforeSaveExecution() {
        assertAccepted(
            CreateDraftMove.Cancel,
            decision(CreateDraftAgentMoveType.CANCEL),
            CreateTaskDialogState.WAITING_FOR_TIME
        )
        assertRejected(decision(CreateDraftAgentMoveType.CANCEL), CreateTaskDialogState.READY_TO_SAVE)
    }

    @Test
    fun unknownIsAlwaysSafe() {
        val result = validator.validate(
            decision(CreateDraftAgentMoveType.UNKNOWN, confidence = 0.1),
            CreateTaskDialogState.READY_TO_SAVE
        )
        assertTrue(result.accepted)
        assertEquals(CreateDraftMove.Unknown, result.move)
    }

    private fun assertAccepted(
        expected: CreateDraftMove,
        decision: CreateDraftAgentDecision,
        state: CreateTaskDialogState
    ) {
        val result = validator.validate(decision, state)
        assertTrue(result.accepted)
        assertEquals(expected, result.move)
    }

    private fun assertRejected(decision: CreateDraftAgentDecision, state: CreateTaskDialogState) {
        val result = validator.validate(decision, state)
        assertFalse(result.accepted)
        assertEquals(CreateDraftMove.Unknown, result.move)
    }

    private fun decision(
        move: CreateDraftAgentMoveType,
        field: CreateDraftField? = null,
        value: String = "",
        dateText: String = "",
        timeText: String = "",
        confidence: Double = 0.95
    ) = CreateDraftAgentDecision(move, field, value, dateText, timeText, confidence)

    private val saveState = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
}
