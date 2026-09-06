package com.example.myapplication.ai.conversation.taskdetailedit

enum class TaskDetailEditField { TITLE, DATE, TIME }

enum class TaskDetailEditAgentMove {
    CONFIRM_SAVE,
    REJECT_SAVE,
    SET_TITLE,
    SET_DATE,
    SET_TIME,
    SET_SCHEDULE,
    REQUEST_TITLE_CHANGE,
    REQUEST_DATE_CHANGE,
    REQUEST_TIME_CHANGE,
    READ_TITLE,
    READ_DATE,
    READ_TIME,
    READ_SCHEDULE,
    ASK_CLARIFICATION,
    CANCEL,
    UNKNOWN
}

enum class TaskDetailDraftReadTarget { TITLE, DATE, TIME, SCHEDULE }

data class TaskDetailEditAgentDecision(
    val move: TaskDetailEditAgentMove,
    val title: String,
    val dateText: String,
    val timeText: String,
    val clarification: String,
    val confidence: Double
)

sealed interface TaskDetailEditProposal {
    data object ConfirmSave : TaskDetailEditProposal
    data object RejectSave : TaskDetailEditProposal
    data class Title(val value: String) : TaskDetailEditProposal
    data class Schedule(
        val dateText: String?,
        val timeText: String?
    ) : TaskDetailEditProposal
    data class Clarification(val question: String) : TaskDetailEditProposal
    data class RequestField(val field: TaskDetailEditField) : TaskDetailEditProposal
    data class ReadDraft(val target: TaskDetailDraftReadTarget) : TaskDetailEditProposal
    data object Cancel : TaskDetailEditProposal
    data object Unknown : TaskDetailEditProposal
}

sealed interface TaskDetailEditLocalCandidate {
    data class Title(val value: String) : TaskDetailEditLocalCandidate
    data class Schedule(
        val dueDate: String?,
        val dueTime: String?
    ) : TaskDetailEditLocalCandidate
    data class Clarification(val question: String) : TaskDetailEditLocalCandidate
    data object Invalid : TaskDetailEditLocalCandidate
}

enum class TaskDetailEditMoveSource(val logValue: String) {
    CONVERSATION_AGENT_PRIMARY("conversation_agent_primary"),
    CONVERSATION_AGENT_REPAIR("conversation_agent_repair"),
    LOCAL_CONFIRMATION_FAST_PATH("local_confirmation_fast_path"),
    LOCAL_SAFETY_REFLEX("local_safety_reflex"),
    LOCAL_FAILURE_FALLBACK("local_failure_fallback"),
    DETERMINISTIC_UNKNOWN("deterministic_unknown")
}

data class TaskDetailEditMoveResolution(
    val proposal: TaskDetailEditProposal,
    val move: TaskDetailEditAgentMove,
    val source: TaskDetailEditMoveSource,
    val confidence: Double,
    val agentAttempted: Boolean,
    val reason: String
)
