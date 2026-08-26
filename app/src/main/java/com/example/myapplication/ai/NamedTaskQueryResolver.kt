package com.example.myapplication.ai

import com.example.myapplication.ai.temporal.TaskCompletionFilter
import com.example.myapplication.ai.temporal.TaskTemporalFilter
import com.example.myapplication.ai.temporal.TemporalQueryResolver
import com.example.myapplication.ai.temporal.TemporalQueryWindow
import com.example.myapplication.ai.temporal.TemporalResolutionStatus
import com.example.myapplication.data.TaskEntity

enum class NamedTaskQueryStatus { RESOLVED, NOT_FOUND, AMBIGUOUS, UNRESOLVED_TEMPORAL }

data class NamedTaskQueryResolution(
    val status: NamedTaskQueryStatus,
    val task: TaskEntity? = null,
    val score: Double = 0.0
)

/** Android-only resolution. The model supplies a title/qualifiers, never candidate tasks or facts. */
class NamedTaskQueryResolver(
    private val temporalResolver: TemporalQueryResolver = TemporalQueryResolver()
) {
    fun resolve(
        proposedTitle: String,
        roomTasks: List<TaskEntity>,
        targetDateText: String? = null,
        targetTimeText: String? = null
    ): NamedTaskQueryResolution {
        if (proposedTitle.isBlank()) return NamedTaskQueryResolution(NamedTaskQueryStatus.NOT_FOUND)
        val window = if (targetDateText.isNullOrBlank() && targetTimeText.isNullOrBlank()) {
            TemporalQueryWindow(TemporalResolutionStatus.NONE)
        } else {
            // Do not parse the original question or title as a temporal list query.
            temporalResolver.resolve(targetDateText, targetTimeText, "")
        }
        if (window.status == TemporalResolutionStatus.UNRESOLVED) {
            return NamedTaskQueryResolution(NamedTaskQueryStatus.UNRESOLVED_TEMPORAL)
        }
        val candidates = TaskTemporalFilter.filterAndSort(
            roomTasks.filter { it.parentTaskId == null },
            window,
            TaskCompletionFilter.ACTIVE_ONLY
        )
        val match = TaskMatcher.findBestTaskMatch(proposedTitle, candidates)
        return when {
            match.isAmbiguous -> NamedTaskQueryResolution(NamedTaskQueryStatus.AMBIGUOUS, score = match.bestScore)
            match.bestTask == null -> NamedTaskQueryResolution(NamedTaskQueryStatus.NOT_FOUND, score = match.bestScore)
            else -> NamedTaskQueryResolution(NamedTaskQueryStatus.RESOLVED, match.bestTask, match.bestScore)
        }
    }
}
