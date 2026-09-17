package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.breakdown.BreakdownSaveResult
import com.example.myapplication.ai.breakdown.BreakdownSaveResultCategory
import com.example.myapplication.data.TaskEntity

/** Re-fetch committed data; draft titles and generated plans are never context authority. */
class BreakdownTaskContextPublisher(
    private val store: ReadOnlyTaskContextStore,
    private val getById: suspend (Long) -> TaskEntity?,
    private val getSubtasks: suspend (Long) -> List<TaskEntity>
) {
    suspend fun publish(result: BreakdownSaveResult): ReadOnlyTaskContextCapture? {
        if (result.category != BreakdownSaveResultCategory.SUCCESS &&
            result.category != BreakdownSaveResultCategory.PARTIAL_REMINDER_FAILURE
        ) return null
        val parent = result.parentTaskId?.let { getById(it) } ?: return null
        if (parent.parentTaskId != null) return null
        val children = getSubtasks(parent.id)
        store.replaceTaskDetailResult(parent, children)
        return store.capture()
    }
}
