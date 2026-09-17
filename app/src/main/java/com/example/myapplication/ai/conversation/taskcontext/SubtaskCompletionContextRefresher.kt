package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.data.TaskEntity

data class RefreshedSubtaskCompletionContext(
    val capture: ReadOnlyTaskContextCapture,
    val affectedItem: ReadOnlyTaskContextItem
)

/** Refreshes the full sibling list without allowing a stale request to replace newer context. */
class SubtaskCompletionContextRefresher(
    private val store: ReadOnlyTaskContextStore,
    private val getSubtasks: suspend (Long) -> List<TaskEntity>,
    private val isCurrent: () -> Boolean
) {
    suspend fun refresh(
        parentTaskId: Long,
        affectedTaskId: Long,
        expectedGeneration: Long
    ): RefreshedSubtaskCompletionContext? {
        if (!isCurrent() || store.currentGeneration() != expectedGeneration) return null
        val siblings = getSubtasks(parentTaskId)
        if (!isCurrent() || store.currentGeneration() != expectedGeneration) return null
        if (siblings.any { it.parentTaskId != parentTaskId }) return null
        val affectedIndex = siblings.indexOfFirst { it.id == affectedTaskId }
        if (affectedIndex < 0) return null

        store.replaceSubtaskList(siblings)
        val capture = store.capture()
        val affectedItem = capture.snapshot.items.getOrNull(affectedIndex) ?: return null
        if (store.resolveRef(affectedItem.ref, capture.snapshot.generation) != affectedTaskId) {
            return null
        }
        return RefreshedSubtaskCompletionContext(capture, affectedItem)
    }
}
