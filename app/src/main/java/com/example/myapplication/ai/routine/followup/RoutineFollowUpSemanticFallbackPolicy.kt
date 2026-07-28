package com.example.myapplication.ai.routine.followup

import com.example.myapplication.ai.routine.RoutineDraftIssue
import com.example.myapplication.ai.routine.RoutineDraftState
import com.example.myapplication.ai.routine.RoutineDraftUpdate

object RoutineFollowUpSemanticFallbackPolicy {
    fun afterRawSharedDate(
        update: RoutineDraftUpdate,
        currentState: RoutineDraftState
    ): Boolean = update is RoutineDraftUpdate.Rejected &&
        update.reason == RoutineDraftIssue.INVALID_DATE &&
        currentState == RoutineDraftState.COLLECTING_SHARED_DATE

    fun afterRawStepTime(
        update: RoutineDraftUpdate,
        currentState: RoutineDraftState
    ): Boolean = update is RoutineDraftUpdate.Rejected &&
        update.reason == RoutineDraftIssue.INVALID_TIME &&
        currentState == RoutineDraftState.COLLECTING_STEP_TIME
}
