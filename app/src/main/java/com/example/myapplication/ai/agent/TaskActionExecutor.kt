package com.example.myapplication.ai.agent

import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.AiParsedCommand

class TaskActionExecutor(
    private val legacyExecutor: suspend (AiParsedCommand, String, String?) -> Unit,
    private val dailyBriefingExecutor: suspend (String?) -> Unit,
    private val conversationalResponder: suspend (String, Boolean) -> Unit
) {
    suspend fun execute(
        userText: String,
        response: AgentResponse,
        validation: ValidationResult
    ): AgentExecutionResult {
        if (!validation.isValid) {
            return AgentExecutionResult(
                handled = false,
                usedFallback = validation.shouldFallback,
                reason = validation.reason
            )
        }

        val action = response.structuredAction

        if (action.needClarification || action.action == AgentActionType.CLARIFY) {
            conversationalResponder(response.naturalResponse, true)
            return AgentExecutionResult(
                handled = true,
                spokenResponse = response.naturalResponse
            )
        }

        if (action.action == AgentActionType.DELETE_TASK && action.requiresConfirmation) {
            conversationalResponder(response.naturalResponse, true)
            return AgentExecutionResult(
                handled = true,
                spokenResponse = response.naturalResponse
            )
        }

        return when (action.action) {
            AgentActionType.CREATE_TASK,
            AgentActionType.QUERY_TASK,
            AgentActionType.UPDATE_TASK,
            AgentActionType.RESCHEDULE_TASK,
            AgentActionType.DELETE_TASK,
            AgentActionType.MARK_DONE,
            AgentActionType.MARK_UNDONE -> {
                legacyExecutor(action.toAiParsedCommand(), userText, response.naturalResponse)
                AgentExecutionResult(handled = true, spokenResponse = response.naturalResponse)
            }

            AgentActionType.DAILY_BRIEFING -> {
                dailyBriefingExecutor(response.naturalResponse)
                AgentExecutionResult(handled = true, spokenResponse = response.naturalResponse)
            }

            AgentActionType.CANCEL -> {
                conversationalResponder(response.naturalResponse, false)
                AgentExecutionResult(handled = true, spokenResponse = response.naturalResponse)
            }

            AgentActionType.UNKNOWN -> AgentExecutionResult(
                handled = false,
                usedFallback = true,
                reason = "Gemma action UNKNOWN"
            )

            AgentActionType.CLARIFY -> error("CLARIFY handled earlier")
        }
    }

    private fun StructuredAction.toAiParsedCommand(): AiParsedCommand {
        return AiParsedCommand(
            intent = when (action) {
                AgentActionType.CREATE_TASK -> AiIntent.CREATE_TASK.name
                AgentActionType.QUERY_TASK -> AiIntent.QUERY_TASK.name
                AgentActionType.UPDATE_TASK -> AiIntent.UPDATE_TASK.name
                AgentActionType.RESCHEDULE_TASK -> AiIntent.RESCHEDULE_TASK.name
                AgentActionType.DELETE_TASK -> AiIntent.DELETE_TASK.name
                AgentActionType.MARK_DONE -> AiIntent.MARK_DONE.name
                AgentActionType.MARK_UNDONE -> AiIntent.MARK_UNDONE.name
                else -> AiIntent.UNKNOWN.name
            },
            taskTitle = taskTitle,
            targetTaskTitle = targetTaskTitle,
            dateText = dateText,
            timeText = timeText,
            recurrence = recurrence,
            priority = priority,
            confidence = confidence,
            source = source
        )
    }
}
