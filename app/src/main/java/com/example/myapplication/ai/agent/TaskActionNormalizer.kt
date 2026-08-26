package com.example.myapplication.ai.agent

import android.util.Log
import com.example.myapplication.diagnostics.DebugDiagnosticLog
import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.AiParsedCommand
import com.example.myapplication.ai.TaskQueryPresentation
import com.example.myapplication.ai.TaskQueryDetail
import com.example.myapplication.ai.breakdown.BreakdownTargetPreference

class TaskActionNormalizer {
    fun normalize(response: TaskAgentResponse): AiParsedCommand {
        val normalizedAction = normalizeAction(response.action)
        var taskTitle = response.task_title.clean()
        var targetTaskTitle = response.target_task_title.clean()
        val recurrence = normalizeRecurrence(response.recurrence)
        val priority = normalizePriority(response.priority)
        val queryDetail = TaskQueryDetail.fromWireValue(response.query_detail)
            ?: throw TaskAgentValidationException(
                "Unsupported query_detail '${response.query_detail}'"
            )
        val parsedQueryPresentation = TaskQueryPresentation.fromWireValue(response.query_presentation)
            ?: throw TaskAgentValidationException(
                "Unsupported query_presentation '${response.query_presentation}'"
            )
        val breakdownTargetPreference =
            BreakdownTargetPreference.fromWireValue(response.breakdown_target_preference)
                ?: throw TaskAgentValidationException(
                    "Unsupported breakdown_target_preference " +
                        "'${response.breakdown_target_preference}'"
                )
        val queryPresentation = if (
            normalizedAction == AiIntent.QUERY_TASK.name &&
            parsedQueryPresentation == TaskQueryPresentation.NONE
        ) {
            // Compatibility policy: a structurally valid QUERY_TASK with NONE is safely
            // normalized to the documented default instead of hiding query results.
            TaskQueryPresentation.OVERVIEW
        } else {
            parsedQueryPresentation
        }
        val legacyDate = response.date.clean()
        val legacyTime = response.time.clean()
        val targetDate = response.target_date.clean()
        val targetTime = response.target_time.clean()
        val newDate = response.new_date.clean()
        val newTime = response.new_time.clean()
        val effectiveTargetDate = targetDate.ifBlank { if (normalizedAction == AiIntent.QUERY_TASK.name) legacyDate else "" }
        val effectiveTargetTime = targetTime.ifBlank { if (normalizedAction == AiIntent.QUERY_TASK.name) legacyTime else "" }
        val effectiveNewDate = newDate.ifBlank { if (normalizedAction in setOf(AiIntent.CREATE_TASK.name, AiIntent.BREAKDOWN_TASK.name, AiIntent.UPDATE_TASK.name, AiIntent.RESCHEDULE_TASK.name)) legacyDate else "" }
        val effectiveNewTime = newTime.ifBlank { if (normalizedAction in setOf(AiIntent.CREATE_TASK.name, AiIntent.BREAKDOWN_TASK.name, AiIntent.UPDATE_TASK.name, AiIntent.RESCHEDULE_TASK.name)) legacyTime else "" }
        val legacyMatchDate = if (normalizedAction in setOf(AiIntent.DELETE_TASK.name, AiIntent.MARK_DONE.name, AiIntent.MARK_UNDONE.name)) legacyDate else ""
        val legacyMatchTime = if (normalizedAction in setOf(AiIntent.DELETE_TASK.name, AiIntent.MARK_DONE.name, AiIntent.MARK_UNDONE.name)) legacyTime else ""
        val requiresConfirmation = response.requires_confirmation || normalizedAction == AiIntent.DELETE_TASK.name

        when (normalizedAction) {
            AiIntent.CREATE_TASK.name,
            AiIntent.BREAKDOWN_TASK.name -> {
                if (taskTitle.isBlank() && targetTaskTitle.isNotBlank()) {
                    taskTitle = targetTaskTitle
                }
                targetTaskTitle = ""
            }

            AiIntent.QUERY_TASK.name -> {
                // Canonicalize only an existing named schedule-detail query's title role.
                // List/count queries and conflicting title fields remain for strict validation.
                if (queryDetail != TaskQueryDetail.NONE && targetTaskTitle.isBlank() && taskTitle.isNotBlank()) {
                    targetTaskTitle = taskTitle
                    taskTitle = ""
                }
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

        val missingFields = response.missing_fields
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val command = AiParsedCommand(
            intent = normalizedAction,
            taskTitle = taskTitle.takeIf { it.isNotBlank() },
            targetTaskTitle = targetTaskTitle.takeIf { it.isNotBlank() },
            dateText = legacyDate.takeIf { it.isNotBlank() },
            timeText = legacyTime.takeIf { it.isNotBlank() },
            targetDateText = effectiveTargetDate.ifBlank { legacyMatchDate }.takeIf { it.isNotBlank() },
            targetTimeText = effectiveTargetTime.ifBlank { legacyMatchTime }.takeIf { it.isNotBlank() },
            newDateText = effectiveNewDate.takeIf { it.isNotBlank() },
            newTimeText = effectiveNewTime.takeIf { it.isNotBlank() },
            recurrence = recurrence.takeIf { it.isNotBlank() },
            priority = priority.takeIf { it.isNotBlank() },
            queryPresentation = queryPresentation,
            queryDetail = queryDetail,
            breakdownTargetPreference = breakdownTargetPreference,
            confidence = response.confidence.coerceIn(0f, 1f),
            source = "laptop_agent",
            needsClarification = response.need_clarification,
            missingFields = missingFields,
            // BREAKDOWN_TASK plans remain an untrusted model proposal here.
            // The bounded Android breakdown controller performs final trimming and validation.
            plan = response.plan,
            naturalResponse = response.natural_response.clean().takeIf { it.isNotBlank() }
        )

        DebugDiagnosticLog.event(
            "TASK_AGENT_NORMALIZE",
            "intent=${command.intent}, title=${command.taskTitle}, target=${command.targetTaskTitle}, " +
                "recurrence=${command.recurrence}, priority=${command.priority}, " +
                "queryPresentation=${command.queryPresentation}, " +
                "queryDetail=${command.queryDetail}, " +
                "breakdownTargetPreference=${command.breakdownTargetPreference}, " +
                "needsClarification=${command.needsClarification}, missingFields=${command.missingFields}, " +
                "requiresConfirmation=$requiresConfirmation"
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
