package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.TaskQueryPresentationLevel

enum class PresentedQueryFocusResult {
    ESTABLISH,
    COUNT_ONLY,
    MULTIPLE_ITEMS,
    NO_ITEMS,
    STALE
}

data class PresentedQueryFocusEvaluation(
    val result: PresentedQueryFocusResult,
    val item: ReadOnlyTaskContextItem? = null
) {
    val shouldEstablish: Boolean
        get() = result == PresentedQueryFocusResult.ESTABLISH && item != null
}

/** Focus eligibility after Android has accepted an authoritative query-page delivery. */
object PresentedQueryFocusPolicy {
    fun evaluate(
        presentation: TaskQueryPresentationLevel,
        snapshot: ReadOnlyTaskContextSnapshot,
        deliveredGeneration: Long?,
        currentGeneration: Long
    ): PresentedQueryFocusEvaluation {
        if (presentation == TaskQueryPresentationLevel.COUNT_ONLY) {
            return PresentedQueryFocusEvaluation(PresentedQueryFocusResult.COUNT_ONLY)
        }
        if (deliveredGeneration == null ||
            deliveredGeneration != snapshot.generation ||
            currentGeneration != snapshot.generation
        ) {
            return PresentedQueryFocusEvaluation(PresentedQueryFocusResult.STALE)
        }
        return when (snapshot.items.size) {
            0 -> PresentedQueryFocusEvaluation(PresentedQueryFocusResult.NO_ITEMS)
            1 -> PresentedQueryFocusEvaluation(
                PresentedQueryFocusResult.ESTABLISH,
                snapshot.items.single()
            )
            else -> PresentedQueryFocusEvaluation(PresentedQueryFocusResult.MULTIPLE_ITEMS)
        }
    }
}
