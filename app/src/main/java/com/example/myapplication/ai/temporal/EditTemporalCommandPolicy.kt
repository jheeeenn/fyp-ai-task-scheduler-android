package com.example.myapplication.ai.temporal

import java.util.Calendar

enum class EditTemporalCommandDisposition {
    NOT_APPLICABLE,
    READY,
    NEEDS_CLARIFICATION,
    UNRESOLVED
}

enum class EditTemporalTarget {
    DATE,
    TIME,
    DATE_OR_TIME
}

data class EditTemporalCommandResolution(
    val disposition: EditTemporalCommandDisposition,
    val target: EditTemporalTarget = EditTemporalTarget.DATE_OR_TIME,
    val temporal: TemporalResolution = TemporalResolution(TemporalResolutionType.NONE),
    val policy: TemporalPolicyResult = TemporalPolicyResult.Unresolved(
        TemporalResolution(TemporalResolutionType.NONE)
    )
)

/**
 * Recognizes only bounded edit/reschedule grammar with an explicit replacement clause.
 * Temporal meaning remains authoritative in [TemporalExpressionResolver] and
 * [TemporalActionPolicy].
 */
object EditTemporalCommandPolicy {
    fun resolve(
        normalizedText: String,
        baseCalendar: Calendar = Calendar.getInstance(),
        resolver: TemporalExpressionResolver = TemporalExpressionResolver()
    ): EditTemporalCommandResolution {
        val text = normalizedText.trim()
        val match = DATE_FIELD.matchEntire(text)?.let {
            CommandMatch(
                target = EditTemporalTarget.DATE,
                replacement = it.groupValues[1]
            )
        } ?: TIME_FIELD.matchEntire(text)?.let {
            CommandMatch(
                target = EditTemporalTarget.TIME,
                replacement = it.groupValues[1]
            )
        } ?: GENERAL_RESCHEDULE.matchEntire(text)?.let {
            CommandMatch(
                target = EditTemporalTarget.DATE_OR_TIME,
                replacement = it.groupValues[1]
            )
        } ?: return EditTemporalCommandResolution(
            EditTemporalCommandDisposition.NOT_APPLICABLE
        )

        val temporal = when (match.target) {
            EditTemporalTarget.DATE ->
                resolver.resolve(match.replacement, null, match.replacement, baseCalendar)
            EditTemporalTarget.TIME ->
                resolver.resolve(null, match.replacement, match.replacement, baseCalendar)
            EditTemporalTarget.DATE_OR_TIME ->
                resolver.resolve(null, null, match.replacement, baseCalendar)
        }
        val policy = TemporalActionPolicy.evaluate(
            resolution = temporal,
            useCase = TemporalUseCase.RESCHEDULE,
            baseCalendar = baseCalendar
        )
        val disposition = if (
            temporal.type == TemporalResolutionType.NONE ||
            temporal.type == TemporalResolutionType.UNRESOLVED
        ) {
            EditTemporalCommandDisposition.UNRESOLVED
        } else when (policy) {
            is TemporalPolicyResult.Ready -> EditTemporalCommandDisposition.READY
            is TemporalPolicyResult.NeedsExactDate,
            is TemporalPolicyResult.NeedsExactTime,
            is TemporalPolicyResult.NeedsExactDateAndTime ->
                EditTemporalCommandDisposition.NEEDS_CLARIFICATION
            is TemporalPolicyResult.Unresolved,
            is TemporalPolicyResult.InvalidPastSchedule ->
                EditTemporalCommandDisposition.UNRESOLVED
        }
        return EditTemporalCommandResolution(
            disposition = disposition,
            target = match.target,
            temporal = temporal,
            policy = policy
        )
    }

    private data class CommandMatch(
        val target: EditTemporalTarget,
        val replacement: String
    )

    private val DATE_FIELD = Regex(
        """^(?:change|set|move)\s+(?:the\s+)?date\s+to\s+(.+)$"""
    )
    private val TIME_FIELD = Regex(
        """^(?:change|set|move)\s+(?:the\s+)?time\s+to\s+(.+)$"""
    )
    private val GENERAL_RESCHEDULE = Regex(
        """^(?:move|reschedule)\s+(?:(?:it|this task|the task)\s+)?to\s+(.+)$"""
    )
}
