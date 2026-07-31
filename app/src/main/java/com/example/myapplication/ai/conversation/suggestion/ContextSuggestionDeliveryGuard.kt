package com.example.myapplication.ai.conversation.suggestion

import com.example.myapplication.ai.conversation.AssistantRequestToken
import com.example.myapplication.ai.conversation.AssistantRequestTokenPolicy
import com.example.myapplication.data.TaskEntity
import java.util.Calendar

enum class ContextSuggestionDeliveryResult {
    CURRENT,
    STALE_REQUEST,
    SELECTED_TASK_MISSING,
    SELECTED_TASK_INACTIVE,
    TITLE_CHANGED,
    SCHEDULE_CHANGED,
    SUBTASKS_CHANGED,
    BREAKDOWN_NO_LONGER_ELIGIBLE,
    CLOSE_PAIR_CHANGED,
    CANDIDATES_CHANGED
}

object ContextSuggestionDeliveryGuard {
    fun requestResult(
        token: AssistantRequestToken,
        currentRequestGeneration: Long,
        requestActive: Boolean
    ): ContextSuggestionDeliveryResult =
        if (
            AssistantRequestTokenPolicy.isCurrent(
                token,
                currentRequestGeneration,
                requestActive
            )
        ) {
            ContextSuggestionDeliveryResult.CURRENT
        } else {
            ContextSuggestionDeliveryResult.STALE_REQUEST
        }

    fun validateFreshSelection(
        snapshot: ContextSuggestionSnapshot,
        decision: ContextSuggestionDecision,
        freshTasksById: Map<Long, TaskEntity>,
        freshSubtasksByParentId: Map<Long, List<TaskEntity>>,
        now: Calendar
    ): ContextSuggestionDeliveryResult {
        if (
            decision.suggestionType == ContextSuggestionType.NO_SUGGESTION ||
            decision.suggestionType == ContextSuggestionType.NO_CLOSE_SCHEDULE
        ) {
            return ContextSuggestionDeliveryResult.CURRENT
        }
        val primaryCandidate = snapshot.candidate(decision.primaryRef)
            ?: return ContextSuggestionDeliveryResult.SELECTED_TASK_MISSING
        val primary = freshTasksById[primaryCandidate.taskId]
            ?: return ContextSuggestionDeliveryResult.SELECTED_TASK_MISSING
        if (primary.isDone || primary.parentTaskId != null) {
            return ContextSuggestionDeliveryResult.SELECTED_TASK_INACTIVE
        }
        if (
            ContextSuggestionSnapshotBuilder.sanitizeTitle(primary.title) !=
            primaryCandidate.title
        ) {
            return ContextSuggestionDeliveryResult.TITLE_CHANGED
        }
        val primarySubtasks = freshSubtasksByParentId[primary.id]
            .orEmpty()
            .sortedWith(compareBy<TaskEntity> { it.subtaskOrder }.thenBy { it.id })

        return when (decision.suggestionType) {
            ContextSuggestionType.FOCUS_TASK -> {
                if (!sameSchedule(primaryCandidate.capturedTask, primary)) {
                    ContextSuggestionDeliveryResult.SCHEDULE_CHANGED
                } else {
                    ContextSuggestionDeliveryResult.CURRENT
                }
            }
            ContextSuggestionType.CONTINUE_SUBTASK -> {
                when {
                    !sameSchedule(primaryCandidate.capturedTask, primary) ->
                        ContextSuggestionDeliveryResult.SCHEDULE_CHANGED
                    primarySubtasks != primaryCandidate.capturedSubtasks ->
                        ContextSuggestionDeliveryResult.SUBTASKS_CHANGED
                    primarySubtasks.none { !it.isDone } ->
                        ContextSuggestionDeliveryResult.SUBTASKS_CHANGED
                    else -> ContextSuggestionDeliveryResult.CURRENT
                }
            }
            ContextSuggestionType.BREAK_DOWN_TASK -> {
                if (primarySubtasks.isNotEmpty()) {
                    ContextSuggestionDeliveryResult.BREAKDOWN_NO_LONGER_ELIGIBLE
                } else {
                    ContextSuggestionDeliveryResult.CURRENT
                }
            }
            ContextSuggestionType.REVIEW_CLOSE_SCHEDULE -> {
                val secondaryCandidate = snapshot.candidate(decision.secondaryRef)
                    ?: return ContextSuggestionDeliveryResult.SELECTED_TASK_MISSING
                val secondary = freshTasksById[secondaryCandidate.taskId]
                    ?: return ContextSuggestionDeliveryResult.SELECTED_TASK_MISSING
                if (secondary.isDone || secondary.parentTaskId != null) {
                    return ContextSuggestionDeliveryResult.SELECTED_TASK_INACTIVE
                }
                if (
                    ContextSuggestionSnapshotBuilder.sanitizeTitle(secondary.title) !=
                    secondaryCandidate.title
                ) {
                    return ContextSuggestionDeliveryResult.TITLE_CHANGED
                }
                if (
                    !sameSchedule(primaryCandidate.capturedTask, primary) ||
                    !sameSchedule(secondaryCandidate.capturedTask, secondary)
                ) {
                    return ContextSuggestionDeliveryResult.SCHEDULE_CHANGED
                }
                val expectedPair = snapshot.closePair(
                    decision.primaryRef,
                    decision.secondaryRef
                ) ?: return ContextSuggestionDeliveryResult.CLOSE_PAIR_CHANGED
                val freshGap = ContextSuggestionSnapshotBuilder.exactGapMinutes(
                    primary,
                    secondary,
                    now
                )
                if (freshGap == expectedPair.gapMinutes && freshGap <= 30) {
                    ContextSuggestionDeliveryResult.CURRENT
                } else {
                    ContextSuggestionDeliveryResult.CLOSE_PAIR_CHANGED
                }
            }
            ContextSuggestionType.NO_CLOSE_SCHEDULE ->
                ContextSuggestionDeliveryResult.CURRENT
            ContextSuggestionType.NO_SUGGESTION ->
                ContextSuggestionDeliveryResult.CURRENT
        }
    }

    private fun sameSchedule(captured: TaskEntity, fresh: TaskEntity): Boolean =
        captured.dueDate == fresh.dueDate && captured.dueTime == fresh.dueTime

    fun validateFreshNoSuggestion(
        freshSnapshot: ContextSuggestionSnapshot
    ): ContextSuggestionDeliveryResult =
        if (freshSnapshot.candidates.isEmpty()) {
            ContextSuggestionDeliveryResult.CURRENT
        } else {
            ContextSuggestionDeliveryResult.CANDIDATES_CHANGED
        }

    fun validateFreshNoCloseSchedule(
        freshSnapshot: ContextSuggestionSnapshot
    ): ContextSuggestionDeliveryResult =
        if (freshSnapshot.closePairs.isEmpty()) {
            ContextSuggestionDeliveryResult.CURRENT
        } else {
            ContextSuggestionDeliveryResult.CLOSE_PAIR_CHANGED
        }
}
