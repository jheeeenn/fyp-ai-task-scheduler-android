package com.example.myapplication.ai.breakdown

import com.example.myapplication.ai.TaskMatcher
import com.example.myapplication.data.TaskEntity

sealed class BreakdownTargetResolution {
    abstract val matchCount: Int

    data class ExistingRoot(val task: TaskEntity) : BreakdownTargetResolution() {
        override val matchCount: Int = 1
    }

    data class Ambiguous(val tasks: List<TaskEntity>) : BreakdownTargetResolution() {
        override val matchCount: Int = tasks.size
    }

    data object NewRoot : BreakdownTargetResolution() {
        override val matchCount: Int = 0
    }
}
object BreakdownTargetResolver {
    fun resolve(
        preference: BreakdownTargetPreference,
        proposedParentTitle: String,
        storedTasks: List<TaskEntity>
    ): BreakdownTargetResolution {
        if (preference == BreakdownTargetPreference.NEW_ROOT) {
            return BreakdownTargetResolution.NewRoot
        }
        val eligibleRoots = storedTasks.filter {
            it.parentTaskId == null && !it.isDone
        }
        val match = TaskMatcher.findBestTaskMatch(proposedParentTitle, eligibleRoots)
        return when {
            match.isAmbiguous && match.bestTask != null && match.secondTask != null ->
                BreakdownTargetResolution.Ambiguous(
                    listOf(match.bestTask, match.secondTask)
                )
            match.bestTask != null -> BreakdownTargetResolution.ExistingRoot(match.bestTask)
            else -> BreakdownTargetResolution.NewRoot
        }
    }

    fun selectCandidate(
        userText: String,
        candidates: List<TaskEntity>
    ): TaskEntity? {
        val eligible = candidates.filter { it.parentTaskId == null && !it.isDone }
        ordinalIndex(userText)?.let { index ->
            return eligible.getOrNull(index)
        }

        val normalizedChoice = TaskMatcher.normalizeForTaskMatch(userText)
        val exactMatches = eligible.filter {
            TaskMatcher.normalizeForTaskMatch(it.title) == normalizedChoice
        }
        if (exactMatches.size == 1) return exactMatches.single()
        if (exactMatches.size > 1) return null

        val match = TaskMatcher.findBestTaskMatch(userText, eligible)
        return match.bestTask.takeUnless { match.isAmbiguous }
    }

    private fun ordinalIndex(value: String): Int? = when (value.trim().lowercase()) {
        "first", "the first", "first one", "the first one", "1", "1st" -> 0
        "second", "the second", "second one", "the second one", "2", "2nd" -> 1
        else -> null
    }
}
