package com.example.myapplication.ai.conversation.taskcontext

enum class TaskContextScope {
    NONE,
    RECENT_QUERY_RESULTS,
    DAILY_BRIEFING,
    CONTEXT_SUGGESTION,
    TASK_MATCH_CHOICES
}

data class ReadOnlyTaskContextItem(
    val ref: String,
    val title: String,
    val dueDate: String,
    val dueTime: String,
    val isDone: Boolean,
    val subtaskCount: Int,
    val unfinishedSubtaskCount: Int
)

data class ReadOnlyTaskContextSnapshot(
    val scope: TaskContextScope,
    val generation: Long,
    val items: List<ReadOnlyTaskContextItem>,
    val truncated: Boolean
)

data class ReadOnlyTaskContextCapture(
    val snapshot: ReadOnlyTaskContextSnapshot,
    val promptText: String
)
