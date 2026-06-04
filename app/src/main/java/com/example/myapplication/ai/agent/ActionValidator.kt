package com.example.myapplication.ai.agent

class ActionValidator(
    private val minimumGemmaConfidence: Float = 0.70f
) {
    fun validate(response: AgentResponse): ValidationResult {
        val action = response.structuredAction

        if (action.confidence < minimumGemmaConfidence &&
            action.action != AgentActionType.CLARIFY &&
            action.action != AgentActionType.CANCEL
        ) {
            return ValidationResult(
                isValid = false,
                shouldFallback = true,
                reason = "Gemma confidence ${action.confidence} below $minimumGemmaConfidence"
            )
        }

        return when (action.action) {
            AgentActionType.CREATE_TASK -> validateCreate(action)
            AgentActionType.UPDATE_TASK,
            AgentActionType.RESCHEDULE_TASK,
            AgentActionType.DELETE_TASK,
            AgentActionType.MARK_DONE,
            AgentActionType.MARK_UNDONE -> validateTargetedTaskAction(action)
            AgentActionType.QUERY_TASK,
            AgentActionType.DAILY_BRIEFING,
            AgentActionType.CLARIFY,
            AgentActionType.CANCEL -> ValidationResult(isValid = true)
            AgentActionType.UNKNOWN -> ValidationResult(
                isValid = false,
                shouldFallback = true,
                reason = "Gemma returned UNKNOWN"
            )
        }
    }

    private fun validateCreate(action: StructuredAction): ValidationResult {
        if (action.needClarification) return ValidationResult(isValid = true)
        if (action.taskTitle.isNullOrBlank()) {
            return ValidationResult(
                isValid = false,
                shouldFallback = true,
                reason = "CREATE_TASK missing task title"
            )
        }
        return ValidationResult(isValid = true)
    }

    private fun validateTargetedTaskAction(action: StructuredAction): ValidationResult {
        if (action.needClarification) return ValidationResult(isValid = true)
        if (action.targetTaskTitle.isNullOrBlank() && action.taskTitle.isNullOrBlank()) {
            return ValidationResult(
                isValid = false,
                shouldFallback = true,
                reason = "${action.action} missing target task title"
            )
        }
        return ValidationResult(isValid = true)
    }
}
