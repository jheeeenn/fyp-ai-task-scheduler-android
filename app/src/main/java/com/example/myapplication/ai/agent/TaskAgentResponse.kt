package com.example.myapplication.ai.agent

data class TaskAgentResponse(
    val natural_response: String = "",
    val action: String = "",
    val task_title: String = "",
    val target_task_title: String = "",
    val date: String = "",
    val time: String = "",
    val recurrence: String = "",
    val priority: String = "",
    val confidence: Float = 0f,
    val need_clarification: Boolean = false,
    val missing_fields: List<String> = emptyList(),
    val requires_confirmation: Boolean = false,
    val plan: String = ""
)
