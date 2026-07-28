package com.example.myapplication.ai

import com.example.myapplication.ai.breakdown.BreakdownTargetPreference

data class AiParsedCommand(
    val intent: String,
    val taskTitle: String? = null,          // for create / breakdown / new title if needed
    val targetTaskTitle: String? = null,    // task user wants to edit/delete/reschedule
    val dateText: String? = null,
    val timeText: String? = null,
    val targetDateText: String? = null,
    val targetTimeText: String? = null,
    val newDateText: String? = null,
    val newTimeText: String? = null,
    val recurrence: String? = null,
    val priority: String? = null,
    val queryPresentation: TaskQueryPresentation = TaskQueryPresentation.NONE,
    val breakdownTargetPreference: BreakdownTargetPreference = BreakdownTargetPreference.AUTO,
    val confidence: Float = 0f,
    val source: String = "local",
    val needsClarification: Boolean = false,
    val missingFields: List<String> = emptyList(),

    // Used by AI planning actions such as BREAKDOWN_TASK.
    val plan: List<String> = emptyList(),

    // Optional model-generated wording. Android may use this for planning conversation,
    // but Android should still control execution and safety.
    val naturalResponse: String? = null
)
