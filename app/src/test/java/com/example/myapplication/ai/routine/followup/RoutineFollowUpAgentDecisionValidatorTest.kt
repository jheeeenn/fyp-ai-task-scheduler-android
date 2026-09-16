package com.example.myapplication.ai.routine.followup

import com.example.myapplication.ai.routine.PendingRoutineDraft
import com.example.myapplication.ai.routine.PendingRoutineStep
import com.example.myapplication.ai.routine.RoutineDraftState
import com.example.myapplication.ai.routine.RoutineFollowUpMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineFollowUpAgentDecisionValidatorTest {
    private val validator = RoutineFollowUpAgentDecisionValidator()
    private val draft = PendingRoutineDraft(
        title = "Morning routine",
        steps = List(3) { index ->
            PendingRoutineStep(
                title = "Step ${index + 1}",
                originalDateText = "4 August 2026",
                originalTimeText = "8 AM",
                resolvedDate = "04/08/2026",
                resolvedTime = "08:00"
            )
        },
        revision = 7L
    )

    @Test
    fun confirmIsAcceptedOnlyWhileWaitingAndCorrectionsCannotBecomeConfirm() {
        val confirm = decision(RoutineFollowUpAgentMove.CONFIRM)
        assertTrue(validate(confirm, RoutineDraftState.WAITING_FOR_CONFIRMATION).accepted)
        listOf(
            RoutineDraftState.EXTRACTING,
            RoutineDraftState.COLLECTING_SHARED_DATE,
            RoutineDraftState.COLLECTING_STEP_TIME,
            RoutineDraftState.SAVING
        ).forEach { state ->
            assertFalse(validate(confirm, state).accepted)
        }
        assertFalse(
            validator.validate(
                decision = confirm,
                state = RoutineDraftState.WAITING_FOR_CONFIRMATION,
                draft = draft,
                userText = "no, make the second one 8:30 PM",
                localMove = RoutineFollowUpMove.Unknown
            ).accepted
        )
        assertFalse(
            validator.validate(
                decision = confirm,
                state = RoutineDraftState.WAITING_FOR_CONFIRMATION,
                draft = draft,
                userText = "yes no",
                localMove = RoutineFollowUpMove.Unknown
            ).accepted
        )
    }

    @Test
    fun providedValuesAreAcceptedOnlyInTheirCollectionState() {
        val date = decision(
            RoutineFollowUpAgentMove.PROVIDE_SHARED_DATE,
            value = "first of August 2026"
        )
        val time = decision(
            RoutineFollowUpAgentMove.PROVIDE_STEP_TIME,
            value = "8:15 AM"
        )
        assertTrue(validate(date, RoutineDraftState.COLLECTING_SHARED_DATE).accepted)
        assertFalse(validate(date, RoutineDraftState.COLLECTING_STEP_TIME).accepted)
        assertTrue(validate(time, RoutineDraftState.COLLECTING_STEP_TIME).accepted)
        assertFalse(validate(time, RoutineDraftState.COLLECTING_SHARED_DATE).accepted)
    }

    @Test
    fun missingTimeCollectionAcceptsZeroOrMatchingAuthoritativeStepOnly() {
        val draftWithThirdStepUnresolved = draft.copy(
            steps = draft.steps.mapIndexed { index, step ->
                if (index == 2) step.copy(resolvedTime = null) else step
            }
        )
        val matchingSelection = decision(
            move = RoutineFollowUpAgentMove.PROVIDE_STEP_TIME,
            stepIndex = 3,
            value = "9 AM"
        )
        val mismatchedSelection = decision(
            move = RoutineFollowUpAgentMove.PROVIDE_STEP_TIME,
            stepIndex = 2,
            value = "8:15 AM"
        )
        val zeroSelection = decision(
            move = RoutineFollowUpAgentMove.PROVIDE_STEP_TIME,
            stepIndex = 0,
            value = "9 AM"
        )

        val matching = validate(
            matchingSelection,
            RoutineDraftState.COLLECTING_STEP_TIME,
            draftWithThirdStepUnresolved
        )
        assertTrue(matching.accepted)
        assertEquals(RoutineFollowUpMove.ProvideStepTime("9 AM"), matching.move)
        assertFalse(
            validate(
                mismatchedSelection,
                RoutineDraftState.COLLECTING_STEP_TIME,
                draftWithThirdStepUnresolved
            ).accepted
        )
        assertTrue(
            validate(
                zeroSelection,
                RoutineDraftState.COLLECTING_STEP_TIME,
                draftWithThirdStepUnresolved
            ).accepted
        )
    }

    @Test
    fun revisionIndexMustExistInCurrentDraft() {
        val valid = decision(
            RoutineFollowUpAgentMove.CHANGE_STEP_TIME,
            stepIndex = 3,
            value = "8:30 PM"
        )
        val invalid = valid.copy(stepIndex = 4)
        assertTrue(validate(valid, RoutineDraftState.WAITING_FOR_CONFIRMATION).accepted)
        assertFalse(validate(invalid, RoutineDraftState.WAITING_FOR_CONFIRMATION).accepted)
        assertEquals(
            RoutineFollowUpMove.ChangeStepTime(2, "8:30 PM"),
            validate(valid, RoutineDraftState.WAITING_FOR_CONFIRMATION).move
        )
    }

    @Test
    fun lowInvalidConfidenceAndSavingBecomeUnknown() {
        listOf(0.79, Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1).forEach {
            val validation = validate(
                decision(RoutineFollowUpAgentMove.CONFIRM, confidence = it),
                RoutineDraftState.WAITING_FOR_CONFIRMATION
            )
            assertFalse(validation.accepted)
            assertEquals(RoutineFollowUpMove.Unknown, validation.move)
        }
        val saving = validate(
            decision(
                RoutineFollowUpAgentMove.CHANGE_STEP_TITLE,
                stepIndex = 1,
                value = "New title"
            ),
            RoutineDraftState.SAVING
        )
        assertFalse(saving.accepted)
        assertEquals(RoutineFollowUpMove.Unknown, saving.move)
    }

    private fun validate(
        decision: RoutineFollowUpAgentDecision,
        state: RoutineDraftState,
        validationDraft: PendingRoutineDraft = draft
    ) = validator.validate(
        decision = decision,
        state = state,
        draft = validationDraft,
        userText = "candidate",
        localMove = RoutineFollowUpMove.Unknown
    )

    private fun decision(
        move: RoutineFollowUpAgentMove,
        stepIndex: Int = 0,
        value: String = "",
        confidence: Double = 0.95
    ) = RoutineFollowUpAgentDecision(move, stepIndex, value, confidence)
}
