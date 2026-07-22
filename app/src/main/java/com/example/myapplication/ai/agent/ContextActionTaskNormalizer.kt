package com.example.myapplication.ai.agent

import com.example.myapplication.ai.AiParsedCommand

class ContextActionTaskNormalizer {
    fun normalize(response: TaskAgentResponse): AiParsedCommand = AiParsedCommand(
        intent = response.action.trim().uppercase(),
        taskTitle = response.task_title.clean().takeIf { it.isNotEmpty() },
        targetTaskTitle = response.target_task_title.clean().takeIf { it.isNotEmpty() },
        dateText = response.date.clean().takeIf { it.isNotEmpty() },
        timeText = response.time.clean().takeIf { it.isNotEmpty() },
        targetDateText = response.target_date.clean().takeIf { it.isNotEmpty() },
        targetTimeText = response.target_time.clean().takeIf { it.isNotEmpty() },
        newDateText = response.new_date.clean().ifBlank { response.date.clean() }
            .takeIf { it.isNotEmpty() },
        newTimeText = response.new_time.clean().ifBlank { response.time.clean() }
            .takeIf { it.isNotEmpty() },
        recurrence = response.recurrence.clean().takeIf { it.isNotEmpty() },
        priority = response.priority.clean().takeIf { it.isNotEmpty() },
        confidence = response.confidence.coerceIn(0f, 1f),
        source = "laptop_agent_context_action",
        needsClarification = response.need_clarification,
        missingFields = response.missing_fields.map { it.clean() }.filter { it.isNotEmpty() },
        plan = response.plan.map { it.clean() }.filter { it.isNotEmpty() },
        naturalResponse = null
    )

    private fun String.clean(): String = trim().replace(Regex("\\s+"), " ")
}
