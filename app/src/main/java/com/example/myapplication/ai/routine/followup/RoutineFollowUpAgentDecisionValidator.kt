package com.example.myapplication.ai.routine.followup

import com.example.myapplication.ai.routine.PendingRoutineDraft
import com.example.myapplication.ai.routine.RoutineDraftState
import com.example.myapplication.ai.routine.RoutineFollowUpMove

data class RoutineFollowUpDecisionValidation(
    val move: RoutineFollowUpMove,
    val accepted: Boolean,
    val reason: String
)

class RoutineFollowUpAgentDecisionValidator {
    fun validate(
        decision: RoutineFollowUpAgentDecision,
        state: RoutineDraftState,
        draft: PendingRoutineDraft?,
        userText: String,
        localMove: RoutineFollowUpMove
    ): RoutineFollowUpDecisionValidation {
        if (!decision.confidence.isFinite() ||
            decision.confidence !in MIN_CONFIDENCE..1.0
        ) {
            return rejected("LOW_OR_INVALID_CONFIDENCE")
        }
        if (!hasValidShape(decision, state, draft)) {
            return rejected("INVALID_FIELD_COMBINATION")
        }
        if (state == RoutineDraftState.SAVING || state == RoutineDraftState.NONE) {
            return rejected("STATE_DISALLOWS_MOVE")
        }
        if (decision.move == RoutineFollowUpAgentMove.CONFIRM &&
            confirmationIsContradicted(userText, localMove)
        ) {
            return rejected("CONFIRMATION_CONTRADICTED")
        }
        if (!isAllowedInState(decision.move, state)) {
            return rejected("STATE_DISALLOWS_MOVE")
        }
        if (decision.move in STEP_REVISION_MOVES &&
            decision.stepIndex !in 1..(draft?.steps?.size ?: 0)
        ) {
            return rejected("STEP_INDEX_OUT_OF_RANGE")
        }
        return RoutineFollowUpDecisionValidation(
            move = decision.toRoutineMove(),
            accepted = true,
            reason = "ACCEPTED"
        )
    }

    fun validateLocal(
        move: RoutineFollowUpMove,
        state: RoutineDraftState,
        draft: PendingRoutineDraft?
    ): RoutineFollowUpDecisionValidation {
        val accepted = when (move) {
            RoutineFollowUpMove.Confirm ->
                state == RoutineDraftState.WAITING_FOR_CONFIRMATION
            RoutineFollowUpMove.Reject ->
                state == RoutineDraftState.WAITING_FOR_CONFIRMATION
            RoutineFollowUpMove.Cancel -> state != RoutineDraftState.SAVING
            RoutineFollowUpMove.Repeat ->
                state == RoutineDraftState.WAITING_FOR_CONFIRMATION
            is RoutineFollowUpMove.ChangeSharedDate ->
                state == RoutineDraftState.WAITING_FOR_CONFIRMATION &&
                    move.value.isNotBlank()
            is RoutineFollowUpMove.ChangeStepTime ->
                state == RoutineDraftState.WAITING_FOR_CONFIRMATION &&
                    move.value.isNotBlank() &&
                    move.stepIndex in draft?.steps.orEmpty().indices
            is RoutineFollowUpMove.ChangeStepTitle ->
                state == RoutineDraftState.WAITING_FOR_CONFIRMATION &&
                    move.value.isNotBlank() &&
                    move.stepIndex in draft?.steps.orEmpty().indices
            RoutineFollowUpMove.StructuralChange ->
                state == RoutineDraftState.WAITING_FOR_CONFIRMATION
            RoutineFollowUpMove.RequestHelp -> state in ACTIVE_STATES
            is RoutineFollowUpMove.ProvideSharedDate,
            is RoutineFollowUpMove.ProvideStepTime,
            RoutineFollowUpMove.Unknown -> false
        }
        return if (accepted) {
            RoutineFollowUpDecisionValidation(move, true, "ACCEPTED_LOCAL")
        } else {
            rejected("LOCAL_NOT_IMMEDIATE")
        }
    }

    private fun isAllowedInState(
        move: RoutineFollowUpAgentMove,
        state: RoutineDraftState
    ): Boolean = when (state) {
        RoutineDraftState.EXTRACTING ->
            move == RoutineFollowUpAgentMove.CANCEL ||
                move == RoutineFollowUpAgentMove.REQUEST_HELP
        RoutineDraftState.COLLECTING_SHARED_DATE ->
            move == RoutineFollowUpAgentMove.PROVIDE_SHARED_DATE ||
                move == RoutineFollowUpAgentMove.CANCEL ||
                move == RoutineFollowUpAgentMove.REQUEST_HELP
        RoutineDraftState.COLLECTING_STEP_TIME ->
            move == RoutineFollowUpAgentMove.PROVIDE_STEP_TIME ||
                move == RoutineFollowUpAgentMove.CANCEL ||
                move == RoutineFollowUpAgentMove.REQUEST_HELP
        RoutineDraftState.WAITING_FOR_CONFIRMATION ->
            move in WAITING_FOR_CONFIRMATION_MOVES
        RoutineDraftState.NONE,
        RoutineDraftState.SAVING -> false
    }

    private fun hasValidShape(
        decision: RoutineFollowUpAgentDecision,
        state: RoutineDraftState,
        draft: PendingRoutineDraft?
    ): Boolean =
        when (decision.move) {
            RoutineFollowUpAgentMove.CHANGE_STEP_TIME,
            RoutineFollowUpAgentMove.CHANGE_STEP_TITLE ->
                decision.stepIndex in 1..5 && decision.value.isNotBlank()
            RoutineFollowUpAgentMove.PROVIDE_SHARED_DATE,
            RoutineFollowUpAgentMove.CHANGE_SHARED_DATE ->
                decision.stepIndex == 0 && decision.value.isNotBlank()
            RoutineFollowUpAgentMove.PROVIDE_STEP_TIME ->
                decision.value.isNotBlank() &&
                    (decision.stepIndex == 0 ||
                        (state == RoutineDraftState.COLLECTING_STEP_TIME &&
                            decision.stepIndex == draft.firstUnresolvedTimeStepIndex()))
            else -> decision.stepIndex == 0 && decision.value.isEmpty()
        }

    private fun PendingRoutineDraft?.firstUnresolvedTimeStepIndex(): Int {
        val zeroBasedIndex = this?.steps?.indexOfFirst { it.resolvedTime == null } ?: -1
        return if (zeroBasedIndex >= 0) zeroBasedIndex + 1 else 0
    }

    private fun confirmationIsContradicted(
        userText: String,
        localMove: RoutineFollowUpMove
    ): Boolean {
        if (localMove is RoutineFollowUpMove.Reject ||
            localMove is RoutineFollowUpMove.Cancel ||
            localMove is RoutineFollowUpMove.ChangeSharedDate ||
            localMove is RoutineFollowUpMove.ChangeStepTime ||
            localMove is RoutineFollowUpMove.ChangeStepTitle ||
            localMove is RoutineFollowUpMove.StructuralChange
        ) {
            return true
        }
        val tokens = TOKEN.findAll(userText.lowercase()).map { it.value }.toSet()
        val mixedControl = tokens.any(AGREEMENT_WORDS::contains) &&
            tokens.any(REJECTION_WORDS::contains)
        val correction = tokens.any(CORRECTION_WORDS::contains)
        return mixedControl || correction
    }

    private fun RoutineFollowUpAgentDecision.toRoutineMove(): RoutineFollowUpMove =
        when (move) {
            RoutineFollowUpAgentMove.CONFIRM -> RoutineFollowUpMove.Confirm
            RoutineFollowUpAgentMove.REJECT -> RoutineFollowUpMove.Reject
            RoutineFollowUpAgentMove.CANCEL -> RoutineFollowUpMove.Cancel
            RoutineFollowUpAgentMove.REPEAT -> RoutineFollowUpMove.Repeat
            RoutineFollowUpAgentMove.PROVIDE_SHARED_DATE ->
                RoutineFollowUpMove.ProvideSharedDate(value)
            RoutineFollowUpAgentMove.PROVIDE_STEP_TIME ->
                RoutineFollowUpMove.ProvideStepTime(value)
            RoutineFollowUpAgentMove.CHANGE_SHARED_DATE ->
                RoutineFollowUpMove.ChangeSharedDate(value)
            RoutineFollowUpAgentMove.CHANGE_STEP_TIME ->
                RoutineFollowUpMove.ChangeStepTime(stepIndex - 1, value)
            RoutineFollowUpAgentMove.CHANGE_STEP_TITLE ->
                RoutineFollowUpMove.ChangeStepTitle(stepIndex - 1, value)
            RoutineFollowUpAgentMove.STRUCTURAL_CHANGE ->
                RoutineFollowUpMove.StructuralChange
            RoutineFollowUpAgentMove.REQUEST_HELP -> RoutineFollowUpMove.RequestHelp
            RoutineFollowUpAgentMove.UNKNOWN -> RoutineFollowUpMove.Unknown
        }

    private fun rejected(reason: String) = RoutineFollowUpDecisionValidation(
        move = RoutineFollowUpMove.Unknown,
        accepted = false,
        reason = reason
    )

    private companion object {
        const val MIN_CONFIDENCE = 0.80
        val ACTIVE_STATES = setOf(
            RoutineDraftState.EXTRACTING,
            RoutineDraftState.COLLECTING_SHARED_DATE,
            RoutineDraftState.COLLECTING_STEP_TIME,
            RoutineDraftState.WAITING_FOR_CONFIRMATION
        )
        val STEP_REVISION_MOVES = setOf(
            RoutineFollowUpAgentMove.CHANGE_STEP_TIME,
            RoutineFollowUpAgentMove.CHANGE_STEP_TITLE
        )
        val WAITING_FOR_CONFIRMATION_MOVES = setOf(
            RoutineFollowUpAgentMove.CONFIRM,
            RoutineFollowUpAgentMove.REJECT,
            RoutineFollowUpAgentMove.CANCEL,
            RoutineFollowUpAgentMove.REPEAT,
            RoutineFollowUpAgentMove.CHANGE_SHARED_DATE,
            RoutineFollowUpAgentMove.CHANGE_STEP_TIME,
            RoutineFollowUpAgentMove.CHANGE_STEP_TITLE,
            RoutineFollowUpAgentMove.STRUCTURAL_CHANGE,
            RoutineFollowUpAgentMove.REQUEST_HELP
        )
        val TOKEN = Regex("""[a-z]+""")
        val AGREEMENT_WORDS = setOf("yes", "yeah", "yep", "confirm", "right", "ahead")
        val REJECTION_WORDS = setOf("no", "not", "reject", "cancel", "stop")
        val CORRECTION_WORDS = setOf(
            "change", "make", "call", "rename", "instead", "actually", "but", "correction"
        )
    }
}
