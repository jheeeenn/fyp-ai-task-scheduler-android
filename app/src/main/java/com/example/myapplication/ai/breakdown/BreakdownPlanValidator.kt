package com.example.myapplication.ai.breakdown

import com.example.myapplication.ai.TaskMatcher
import com.example.myapplication.diagnostics.DebugDiagnosticLog
import java.util.Locale

enum class BreakdownPlanValidationReason {
    ACCEPTED,
    INVALID_COUNT,
    EMPTY_TITLE,
    DUPLICATE_TITLE,
    TITLE_TOO_LONG,
    PARENT_EQUIVALENT
}
sealed class BreakdownPlanValidationResult {
    data class Accepted(val titles: List<String>) : BreakdownPlanValidationResult()
    data class Rejected(
        val reason: BreakdownPlanValidationReason,
        val count: Int
    ) : BreakdownPlanValidationResult()
}

object BreakdownPlanValidator {
    const val MIN_SUBTASKS = 2
    const val MAX_SUBTASKS = 5
    const val MAX_TITLE_LENGTH = 120

    fun validate(
        parentTitle: String,
        proposedSubtasks: List<String>
    ): BreakdownPlanValidationResult {
        val result = validateInternal(parentTitle, proposedSubtasks)
        when (result) {
            is BreakdownPlanValidationResult.Accepted ->
                log(true, BreakdownPlanValidationReason.ACCEPTED, result.titles.size)
            is BreakdownPlanValidationResult.Rejected ->
                log(false, result.reason, result.count)
        }
        return result
    }

    private fun validateInternal(
        parentTitle: String,
        proposedSubtasks: List<String>
    ): BreakdownPlanValidationResult {
        if (proposedSubtasks.size !in MIN_SUBTASKS..MAX_SUBTASKS) {
            return rejected(
                BreakdownPlanValidationReason.INVALID_COUNT,
                proposedSubtasks.size
            )
        }

        val trimmed = proposedSubtasks.map(String::trim)
        if (trimmed.any(String::isEmpty)) {
            return rejected(BreakdownPlanValidationReason.EMPTY_TITLE, trimmed.size)
        }
        if (trimmed.any { it.length > MAX_TITLE_LENGTH }) {
            return rejected(BreakdownPlanValidationReason.TITLE_TOO_LONG, trimmed.size)
        }

        val unique = trimmed.map(::normalizeCaseInsensitive)
        if (unique.distinct().size != unique.size) {
            return rejected(BreakdownPlanValidationReason.DUPLICATE_TITLE, trimmed.size)
        }

        val normalizedParent = normalizeForParentComparison(parentTitle)
        if (normalizedParent.isNotEmpty() &&
            trimmed.any { normalizeForParentComparison(it) == normalizedParent }
        ) {
            return rejected(BreakdownPlanValidationReason.PARENT_EQUIVALENT, trimmed.size)
        }

        return BreakdownPlanValidationResult.Accepted(trimmed)
    }

    private fun normalizeCaseInsensitive(value: String): String =
        value.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), " ")

    private fun normalizeForParentComparison(value: String): String =
        TaskMatcher.normalizeForTaskMatch(value)
            .ifBlank { normalizeCaseInsensitive(value) }

    private fun rejected(
        reason: BreakdownPlanValidationReason,
        count: Int
    ) = BreakdownPlanValidationResult.Rejected(reason, count)

    private fun log(
        accepted: Boolean,
        reason: BreakdownPlanValidationReason,
        count: Int
    ) {
        DebugDiagnosticLog.event(
            "BREAKDOWN_PLAN_VALIDATION",
            "accepted=$accepted\nreason=${reason.name}\ncount=$count"
        )
    }
}
