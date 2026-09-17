package com.example.myapplication.ai.conversation.taskcontext

/** Establishes post-save focus only while the exact published task-detail generation is current. */
object BreakdownPostSaveContextFocusPolicy {
    fun authoritativeItemOrNull(
        publishedSnapshot: ReadOnlyTaskContextSnapshot,
        currentGeneration: Long
    ): ReadOnlyTaskContextItem? {
        if (publishedSnapshot.scope != TaskContextScope.TASK_DETAIL ||
            publishedSnapshot.generation != currentGeneration
        ) {
            return null
        }
        return publishedSnapshot.items.singleOrNull()
            ?.takeIf { it.ref.equals("T1", ignoreCase = true) }
    }
}
