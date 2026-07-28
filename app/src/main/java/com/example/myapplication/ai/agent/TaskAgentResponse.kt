package com.example.myapplication.ai.agent

data class TaskAgentResponse(
    val natural_response: String = "",
    val action: String = "",
    val task_title: String = "",
    val target_task_title: String = "",
    val date: String = "",
    val time: String = "",
    val target_date: String = "",
    val target_time: String = "",
    val new_date: String = "",
    val new_time: String = "",
    val recurrence: String = "",
    val priority: String = "",
    val query_presentation: String = "NONE",
    val breakdown_target_preference: String = "AUTO",
    val confidence: Float = 0f,
    val need_clarification: Boolean = false,
    val missing_fields: List<String> = emptyList(),
    val requires_confirmation: Boolean = false,
    val plan: List<String> = emptyList()
)
