package com.example.myapplication.ai.agent

import com.example.myapplication.data.TaskEntity

enum class AgentActionType {
    CREATE_TASK,
    QUERY_TASK,
    UPDATE_TASK,
    RESCHEDULE_TASK,
    DELETE_TASK,
    MARK_DONE,
    MARK_UNDONE,
    DAILY_BRIEFING,
    CLARIFY,
    CANCEL,
    UNKNOWN
}

data class StructuredAction(
    val action: AgentActionType,
    val taskTitle: String? = null,
    val targetTaskTitle: String? = null,
    val dateText: String? = null,
    val timeText: String? = null,
    val recurrence: String? = null,
    val priority: String? = null,
    val confidence: Float = 0f,
    val needClarification: Boolean = false,
    val missingFields: List<String> = emptyList(),
    val requiresConfirmation: Boolean = false,
    val source: String = "gemma"
)

data class AgentResponse(
    val structuredAction: StructuredAction,
    val naturalResponse: String,
    val rawResponse: String = ""
)

data class ConversationTurn(
    val userText: String,
    val action: AgentActionType,
    val assistantResponse: String
)

data class ConversationState(
    val pendingAction: StructuredAction? = null,
    val lastAssistantQuestion: String? = null,
    val recentTurns: List<ConversationTurn> = emptyList()
)

data class TaskSnapshot(
    val activeTasks: List<TaskEntity>,
    val allTasks: List<TaskEntity>
)

data class ValidationResult(
    val isValid: Boolean,
    val shouldFallback: Boolean = false,
    val reason: String? = null
)

data class AgentExecutionResult(
    val handled: Boolean,
    val usedFallback: Boolean = false,
    val spokenResponse: String? = null,
    val reason: String? = null
)
