package com.example.myapplication.ai.agent

import android.util.Log
import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.AiParsedCommand

class TaskActionNormalizer {
    fun normalize(response: TaskAgentResponse): AiParsedCommand {
        val normalizedAction = normalizeAction(response.action)
        var taskTitle = response.task_title.clean()
        var targetTaskTitle = response.target_task_title.clean()
        val recurrence = normalizeRecurrence(response.recurrence)
        val priority = normalizePriority(response.priority)
        val requiresConfirmation = response.requires_confirmation || normalizedAction == AiIntent.DELETE_TASK.name

        when (normalizedAction) {
            AiIntent.CREATE_TASK.name,
            AiIntent.BREAKDOWN_TASK.name -> {
                if (taskTitle.isBlank() && targetTaskTitle.isNotBlank()) {
                    taskTitle = targetTaskTitle
                }
                targetTaskTitle = ""
            }

            AiIntent.RESCHEDULE_TASK.name,
            AiIntent.DELETE_TASK.name,
            AiIntent.UPDATE_TASK.name,
            AiIntent.MARK_DONE.name,
            AiIntent.MARK_UNDONE.name -> {
                if (targetTaskTitle.isBlank() && taskTitle.isNotBlank()) {
                    targetTaskTitle = taskTitle
                    taskTitle = ""
                }
            }
        }

        val command = AiParsedCommand(
            intent = normalizedAction,
            taskTitle = taskTitle.takeIf { it.isNotBlank() },
            targetTaskTitle = targetTaskTitle.takeIf { it.isNotBlank() },
            dateText = response.date.clean().takeIf { it.isNotBlank() },
            timeText = response.time.clean().takeIf { it.isNotBlank() },
            recurrence = recurrence.takeIf { it.isNotBlank() },
            priority = priority.takeIf { it.isNotBlank() },
            confidence = response.confidence.coerceIn(0f, 1f),
            source = "laptop_agent",
            plan = response.plan
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(4),
            naturalResponse = response.natural_response.clean().takeIf { it.isNotBlank() }
        )

        Log.d(
            "TASK_AGENT_NORMALIZE",
            "intent=${command.intent}, title=${command.taskTitle}, target=${command.targetTaskTitle}, " +
                "recurrence=${command.recurrence}, priority=${command.priority}, requiresConfirmation=$requiresConfirmation"
        )
        return command
    }

    private fun normalizeAction(action: String): String {
        val upper = action.clean().uppercase()
        return when (upper) {
            AiIntent.CREATE_TASK.name,
            AiIntent.QUERY_TASK.name,
            AiIntent.DELETE_TASK.name,
            AiIntent.RESCHEDULE_TASK.name,
            AiIntent.UPDATE_TASK.name,
            AiIntent.MARK_DONE.name,
            AiIntent.MARK_UNDONE.name,
            AiIntent.BREAKDOWN_TASK.name -> upper

            "DAILY_BRIEFING",
            "CREATE_ROUTINE",
            "SUGGEST_TASK",
            "CLARIFY",
            "CANCEL",
            AiIntent.UNKNOWN.name -> AiIntent.UNKNOWN.name

            else -> AiIntent.UNKNOWN.name
        }
    }

    private fun normalizeRecurrence(recurrence: String): String {
        val cleaned = recurrence.clean()
        return if (cleaned.equals("once", ignoreCase = true) || cleaned.equals("none", ignoreCase = true)) {
            ""
        } else {
            cleaned
        }
    }

    private fun normalizePriority(priority: String): String {
        val upper = priority.clean().uppercase()
        return when (upper) {
            "", "LOW", "MEDIUM", "HIGH" -> upper
            else -> ""
        }
    }

    private fun String.clean(): String = trim().replace(Regex("\\s+"), " ")
}
