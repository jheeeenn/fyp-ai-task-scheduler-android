package com.example.myapplication.ai.conversation.taskedit

enum class EditTaskInteractionState {
    READY_FOR_EDIT,
    WAITING_FOR_SAVE_CONFIRMATION,
    WAITING_FOR_DELETE_CONFIRMATION,
    WAITING_FOR_TITLE,
    WAITING_FOR_DATE,
    WAITING_FOR_TIME,
    WAITING_FOR_DATE_OR_TIME,
    WAITING_FOR_TEMPORAL_CLARIFICATION,
    WAITING_FOR_RELATIVE_TEMPORAL_CONFIRMATION,
    OPERATION_IN_FLIGHT
}

enum class EditTaskSemanticMove {
    CONFIRM_SAVE,
    REJECT_SAVE,
    CHANGE_TITLE,
    CHANGE_DATE,
    CHANGE_TIME,
    CHANGE_SCHEDULE,
    REQUEST_TITLE_CHANGE,
    REQUEST_DATE_CHANGE,
    REQUEST_TIME_CHANGE,
    DELETE,
    CANCEL,
    UNKNOWN
}

data class EditTaskSemanticDecision(
    val move: EditTaskSemanticMove,
    val title: String,
    val dateText: String,
    val timeText: String,
    val confidence: Double
)

enum class EditTaskMoveSource(val logValue: String) {
    CONVERSATION_AGENT_PRIMARY("conversation_agent"),
    CONVERSATION_AGENT_REPAIR("conversation_agent_repair"),
    LOCAL_FAST_PATH("android_local_fast_path"),
    DETERMINISTIC_UNKNOWN("android_deterministic_unknown")
}

data class EditTaskMoveResolution(
    val move: EditTaskSemanticMove,
    val title: String = "",
    val dateText: String = "",
    val timeText: String = "",
    val source: EditTaskMoveSource,
    val confidence: Double,
    val agentAttempted: Boolean,
    val reason: String
)
