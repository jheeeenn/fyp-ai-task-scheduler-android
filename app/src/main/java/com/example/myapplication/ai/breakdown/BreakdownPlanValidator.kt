package com.example.myapplication.ai.breakdown

import com.example.myapplication.ai.TaskMatcher
import com.example.myapplication.diagnostics.DebugDiagnosticLog
import java.util.Locale

enum class BreakdownPlanValidationReason {
    ACCEPTED,
    INVALID_COUNT,
    EMPTY_TITLE,
    PLACEHOLDER_TITLE,
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
        if (trimmed.any(::isPlaceholderTitle)) {
            return rejected(BreakdownPlanValidationReason.PLACEHOLDER_TITLE, trimmed.size)
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

    private fun isPlaceholderTitle(value: String): Boolean {
        val rawTokens = PLACEHOLDER_TOKEN.findAll(value.lowercase(Locale.ROOT))
            .map { it.value }
            .toList()
        val tokens = if (
            rawTokens.size == 3 && rawTokens[1] in OPTIONAL_NUMBER_LABELS
        ) {
            listOf(rawTokens.first(), rawTokens.last())
        } else {
            rawTokens
        }
        if (tokens.size != 2) return false
        return (tokens.first() in GENERIC_PLACEHOLDER_NOUNS && tokens.last().isOrdinalLabel()) ||
            (tokens.first().isOrdinalLabel() && tokens.last() in GENERIC_PLACEHOLDER_NOUNS)
    }

    private fun String.isOrdinalLabel(): Boolean =
        matches(NUMERIC_ORDINAL) || this in WORD_ORDINALS

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

    private val PLACEHOLDER_TOKEN = Regex("[\\p{L}\\p{N}]+")
    private val NUMERIC_ORDINAL = Regex("\\d+(?:st|nd|rd|th)?")
    private val GENERIC_PLACEHOLDER_NOUNS = setOf("step", "task", "subtask")
    private val OPTIONAL_NUMBER_LABELS = setOf("number", "no")
    private val WORD_ORDINALS = setOf(
        "one", "two", "three", "four", "five",
        "first", "second", "third", "fourth", "fifth"
    )
}
