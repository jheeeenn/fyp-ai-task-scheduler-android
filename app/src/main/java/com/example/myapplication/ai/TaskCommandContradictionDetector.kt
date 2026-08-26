package com.example.myapplication.ai

import com.example.myapplication.ai.temporal.TemporalExpressionResolver
import java.util.Locale

/**
 * Evidence for requesting semantic repair only. Never produces a command, target, or date.
 * Anchored structures deliberately abstain on broad conversation and contextual references.
 */
internal object TaskCommandContradictionDetector {
    data class ScheduleChangeEvidence(val hasDestinationDate: Boolean, val hasDestinationTime: Boolean)

    private val temporal = TemporalExpressionResolver()
    private const val REQUEST = "(?:(?:can|could|would) you )?(?:please )?"
    private val taskQuery = Regex(
        """^(?:do i have (?:anything (?:planned|scheduled)|any tasks?)|(?:what|which) tasks do i have|how many tasks do i have|what(?:'s| is) on my schedule|(?:show|list)(?: me)? (?:my |the )?tasks)\b"""
    )
    private val completionReversals = listOf(
        Regex("""^i (?:haven't|have not) (?:finished|completed) (.+?)(?: after all| yet)?$"""),
        Regex("""^(.+?) (?:isn't|is not) (?:finished|complete|completed|done)(?: after all| yet)?$"""),
        Regex("""^${REQUEST}mark (.+?) (?:as )?(?:incomplete|not complete|not done)(?: again)?$"""),
        Regex("""^${REQUEST}reopen (.+)$""")
    )
    private val scheduleChange = Regex(
        """^${REQUEST}(?:move|reschedule|postpone) (.+?) (?:over )?(?:to|for|until) (.+)$"""
    )
    private val namedRenames = listOf(
        Regex("""^i want (?:the )?(.+?) to be called (.+?)(?: instead)?$"""),
        Regex("""^${REQUEST}rename (.+?) to (.+)$"""),
        Regex("""^${REQUEST}change the name of (.+?) to (.+)$"""),
        Regex("""^${REQUEST}change (.+?)(?:'s|s') name to (.+)$""")
    )
    private val additionalRenameOperation = Regex(
        """\b(?:and|then) (?:move|reschedule|postpone|change|delete|add|create|mark)\b"""
    )
    private val contextualOrGenericTarget = Regex(
        """^(?:it|this|that|these|those|them|something|anything|everything|nothing|myself|my day|my work|a new|new|what|how|why|when|whether|if)\b|^(?:(?:the|a|my|any|all(?: my)?|some) )?tasks?$|^(?:yet|after all|again|please)$"""
    )

    fun isExplicitTemporalTaskQuery(text: String): Boolean {
        val normalized = normalize(text)
        return taskQuery.containsMatchIn(normalized) &&
            temporal.hasExplicitDateExpressionBeyondToday(normalized)
    }

    fun isNamedCompletionReversal(text: String): Boolean {
        val normalized = normalize(text)
        return completionReversals.any { pattern ->
            pattern.matchEntire(normalized)?.groupValues?.get(1)?.let(::hasNamedEvidence) == true
        }
    }

    fun scheduleChangeEvidence(text: String): ScheduleChangeEvidence? {
        val match = scheduleChange.matchEntire(normalize(text)) ?: return null
        if (!hasNamedEvidence(match.groupValues[1])) return null
        val destination = match.groupValues[2]
        return ScheduleChangeEvidence(
            temporal.hasExplicitDateExpression(destination),
            temporal.hasExplicitTimeExpression(destination)
        ).takeIf { it.hasDestinationDate || it.hasDestinationTime }
    }

    fun isNamedTaskRename(text: String): Boolean = namedRenames.any { pattern ->
        val match = pattern.matchEntire(normalize(text)) ?: return@any false
        hasNamedEvidence(match.groupValues[1]) && hasReplacementEvidence(match.groupValues[2]) &&
            !match.groupValues[1].startsWith("to ") &&
            !additionalRenameOperation.containsMatchIn(match.value)
    }

    private fun hasNamedEvidence(candidate: String): Boolean =
        candidate.any(Char::isLetter) && !contextualOrGenericTarget.containsMatchIn(candidate)

    private fun hasReplacementEvidence(candidate: String): Boolean =
        candidate.any(Char::isLetter) && candidate !in setOf(
            "it", "this", "that", "something", "anything", "something else", "instead",
            "please", "a task", "the task", "task"
        )

    private fun normalize(text: String): String = text.lowercase(Locale.ROOT)
        .replace('’', '\'')
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd('.', '?', '!')
}
