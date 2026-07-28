package com.example.myapplication.ai.routine.followup

enum class RoutineFollowUpAgentMove {
    CONFIRM,
    REJECT,
    CANCEL,
    REPEAT,
    PROVIDE_SHARED_DATE,
    PROVIDE_STEP_TIME,
    CHANGE_SHARED_DATE,
    CHANGE_STEP_TIME,
    CHANGE_STEP_TITLE,
    STRUCTURAL_CHANGE,
    REQUEST_HELP,
    UNKNOWN
}

data class RoutineFollowUpAgentDecision(
    val move: RoutineFollowUpAgentMove,
    val stepIndex: Int,
    val value: String,
    val confidence: Double
)
