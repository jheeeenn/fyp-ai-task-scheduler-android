package com.example.myapplication.ai.conversation.suggestion

import com.example.myapplication.data.TaskEntity
import org.json.JSONArray
import org.json.JSONObject

enum class ContextSuggestionType {
    FOCUS_TASK,
    CONTINUE_SUBTASK,
    BREAK_DOWN_TASK,
    REVIEW_CLOSE_SCHEDULE,
    NO_CLOSE_SCHEDULE,
    NO_SUGGESTION
}

enum class ContextSuggestionAttentionCategory {
    OVERDUE,
    DUE_TODAY,
    UPCOMING,
    LATER,
    UNSCHEDULED
}

data class ContextSuggestionCandidate(
    val ref: String,
    val title: String,
    val dueDate: String,
    val dueTime: String,
    val attentionCategory: ContextSuggestionAttentionCategory,
    val subtaskCount: Int,
    val unfinishedSubtaskCount: Int,
    val structurallyEligibleForBreakdown: Boolean,
    internal val capturedTask: TaskEntity,
    internal val capturedSubtasks: List<TaskEntity>
) {
    internal val taskId: Long
        get() = capturedTask.id

    internal val orderedUnfinishedSubtasks: List<TaskEntity>
        get() = capturedSubtasks.filterNot(TaskEntity::isDone)

    internal fun toSemanticJson(): JSONObject = JSONObject().apply {
        put("ref", ref)
        put("title", title)
        put("due_date", dueDate)
        put("due_time", dueTime)
        put("attention_category", attentionCategory.name)
        put("subtask_count", subtaskCount)
        put("unfinished_subtask_count", unfinishedSubtaskCount)
        put("structurally_eligible_for_breakdown", structurallyEligibleForBreakdown)
    }
}

data class ContextSuggestionClosePair(
    val primaryRef: String,
    val secondaryRef: String,
    val gapMinutes: Int,
    internal val dueDate: String,
    internal val dueDateSortMillis: Long,
    internal val firstTaskMinute: Int
) {
    internal fun toSemanticJson(): JSONObject = JSONObject().apply {
        put("primary_ref", primaryRef)
        put("secondary_ref", secondaryRef)
        put("gap_minutes", gapMinutes)
    }
}

data class ContextSuggestionSnapshot(
    val candidates: List<ContextSuggestionCandidate>,
    val closePairs: List<ContextSuggestionClosePair>,
    val capturedAtMillis: Long
) {
    fun candidate(ref: String): ContextSuggestionCandidate? =
        candidates.firstOrNull { it.ref == ref }

    fun closePair(
        primaryRef: String,
        secondaryRef: String
    ): ContextSuggestionClosePair? = closePairs.firstOrNull {
        it.primaryRef == primaryRef && it.secondaryRef == secondaryRef
    }

    fun toSemanticJson(): String = JSONObject().apply {
        put(
            "candidates",
            JSONArray().apply {
                candidates.forEach { put(it.toSemanticJson()) }
            }
        )
        put(
            "close_schedule_pairs",
            JSONArray().apply {
                closePairs.forEach { put(it.toSemanticJson()) }
            }
        )
    }.toString()
}

data class ContextSuggestionDecision(
    val suggestionType: ContextSuggestionType,
    val primaryRef: String,
    val secondaryRef: String,
    val confidence: Double
)

enum class ContextSuggestionDecisionSource {
    SEMANTIC_AGENT,
    DETERMINISTIC_FALLBACK,
    ANDROID_NO_CANDIDATES
}

data class ContextSuggestionSelection(
    val decision: ContextSuggestionDecision,
    val source: ContextSuggestionDecisionSource,
    val validationResult: ContextSuggestionValidationResult
)
