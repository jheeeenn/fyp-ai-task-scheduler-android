package com.example.myapplication.ai.conversation.suggestion

import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextItem
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextSnapshot
import com.example.myapplication.ai.conversation.taskcontext.TaskContextScope

object ContextSuggestionFocusPolicy {
    fun authoritativeItemOrNull(
        suggestionType: ContextSuggestionType,
        publishedSnapshot: ReadOnlyTaskContextSnapshot,
        capturedGeneration: Long,
        currentGeneration: Long
    ): ReadOnlyTaskContextItem? {
        if (suggestionType !in SINGLE_TASK_SUGGESTION_TYPES) return null
        if (publishedSnapshot.scope != TaskContextScope.CONTEXT_SUGGESTION) return null
        if (
            publishedSnapshot.generation != capturedGeneration ||
            currentGeneration != capturedGeneration
        ) {
            return null
        }
        val item = publishedSnapshot.items.singleOrNull() ?: return null
        return item.takeIf { it.ref.equals(SINGLE_TASK_REF, ignoreCase = true) }
    }

    private val SINGLE_TASK_SUGGESTION_TYPES = setOf(
        ContextSuggestionType.FOCUS_TASK,
        ContextSuggestionType.CONTINUE_SUBTASK,
        ContextSuggestionType.BREAK_DOWN_TASK
    )
    private const val SINGLE_TASK_REF = "T1"
}
