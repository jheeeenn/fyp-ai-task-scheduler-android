package com.example.myapplication.ai.conversation.query

import com.example.myapplication.ai.TaskQueryPresentation
import com.example.myapplication.ai.temporal.TemporalQueryWindow
import com.example.myapplication.data.TaskEntity

/**
 * Android-only authoritative state for one task-query reading interaction.
 *
 * This object is never serialized, logged, or placed in conversation memory.
 */
internal data class AccessibleTaskQuerySession(
    val orderedTasks: List<TaskEntity>,
    val subtasksByParentId: Map<Long, List<TaskEntity>>,
    val queryWindow: TemporalQueryWindow,
    val presentation: TaskQueryPresentation,
    val currentPageIndex: Int = 0
) {
    val pageSize: Int
        get() = VOICE_PAGE_SIZE

    val pageCount: Int
        get() = if (orderedTasks.isEmpty()) 0 else (orderedTasks.size + pageSize - 1) / pageSize

    val currentPageTasks: List<TaskEntity>
        get() {
            if (orderedTasks.isEmpty()) return emptyList()
            val start = currentPageIndex * pageSize
            return orderedTasks.subList(start, minOf(start + pageSize, orderedTasks.size))
        }

    val currentPageStartPosition: Int
        get() = if (orderedTasks.isEmpty()) 0 else currentPageIndex * pageSize + 1

    val currentPageEndPosition: Int
        get() = if (orderedTasks.isEmpty()) 0 else currentPageStartPosition + currentPageTasks.size - 1

    val hasNextPage: Boolean
        get() = currentPageIndex + 1 < pageCount

    fun advanceOnePage(): AccessibleTaskQuerySession? =
        if (hasNextPage) copy(currentPageIndex = currentPageIndex + 1) else null

    fun beginOverview(): AccessibleTaskQuerySession = copy(
        presentation = TaskQueryPresentation.OVERVIEW,
        currentPageIndex = 0
    )

    companion object {
        const val VOICE_PAGE_SIZE = 5
    }
}
