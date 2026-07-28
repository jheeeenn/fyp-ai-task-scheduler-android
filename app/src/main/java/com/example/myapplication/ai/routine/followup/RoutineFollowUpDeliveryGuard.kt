package com.example.myapplication.ai.routine.followup

import com.example.myapplication.ai.routine.RoutineDraftState

object RoutineFollowUpDeliveryGuard {
    fun shouldDeliver(
        capturedState: RoutineDraftState,
        currentState: RoutineDraftState,
        capturedRevision: Long,
        currentRevision: Long,
        requestCurrent: Boolean
    ): Boolean = requestCurrent &&
        currentState == capturedState &&
        currentRevision == capturedRevision
}
