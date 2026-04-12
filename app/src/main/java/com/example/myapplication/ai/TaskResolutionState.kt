package com.example.myapplication.ai

data class TaskResolutionState(
    val action: PendingTaskAction = PendingTaskAction.NONE,
    val candidate1Id: Long? = null,
    val candidate2Id: Long? = null,
    val rescheduleDateText: String? = null,
    val rescheduleTimeText: String? = null
) {
    fun isActive(): Boolean {
        return action != PendingTaskAction.NONE &&
                candidate1Id != null &&
                candidate2Id != null
    }

    fun clear(): TaskResolutionState = TaskResolutionState()
}

enum class PendingTaskAction {
    NONE,
    EDIT,
    DELETE,
    RESCHEDULE,
    MARK_DONE,
    MARK_UNDONE
}