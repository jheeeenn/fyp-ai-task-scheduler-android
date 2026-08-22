package com.example.myapplication.ai.temporal

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ExactTemporalSchedule(
    val date: String?,
    val time: String?
)

enum class RelativeTemporalCalculationFailure {
    CURRENT_PROPOSAL_UNAVAILABLE,
    MISSING_ORIGINAL_DATE,
    MISSING_ORIGINAL_TIME,
    INVALID_STORED_DATE,
    INVALID_STORED_TIME,
    REPLACEMENT_DATE_UNRESOLVED,
    REPLACEMENT_TIME_UNRESOLVED,
    REPLACEMENT_DATE_NEEDS_CLARIFICATION,
    REPLACEMENT_TIME_NEEDS_CLARIFICATION,
    UNMENTIONED_FIELD_CHANGED,
    NO_EFFECTIVE_CHANGE
}

sealed class RelativeTemporalCalculationResult {
    data class Success(
        val schedule: ExactTemporalSchedule,
        val crossedDateBoundary: Boolean,
        val source: RelativeTemporalBase
    ) : RelativeTemporalCalculationResult()

    data class PastSchedule(
        val schedule: ExactTemporalSchedule,
        val crossedDateBoundary: Boolean,
        val source: RelativeTemporalBase
    ) : RelativeTemporalCalculationResult()

    data class Failure(
        val reason: RelativeTemporalCalculationFailure,
        val source: RelativeTemporalBase
    ) : RelativeTemporalCalculationResult()
}

/**
 * Applies an already validated semantic proposal using strict app storage formats. The supplied
 * current clock is used only to resolve literal date expressions and reject past results; it is
 * never substituted for a missing task date or time.
 */
class RelativeTemporalChangeCalculator(
    private val resolver: TemporalExpressionResolver = TemporalExpressionResolver(),
    private val validator: RelativeTemporalProposalValidator = RelativeTemporalProposalValidator()
) {
    fun calculate(
        authoritativeOriginal: ExactTemporalSchedule,
        currentProposal: ExactTemporalSchedule?,
        proposal: RelativeTemporalProposal,
        now: Calendar = Calendar.getInstance()
    ): RelativeTemporalCalculationResult {
        val validated = validator.validate(proposal)
        val base = when (validated.relativeBase) {
            RelativeTemporalBase.AUTHORITATIVE_TASK -> authoritativeOriginal
            RelativeTemporalBase.CURRENT_PROPOSAL -> currentProposal
                ?: return failure(
                    RelativeTemporalCalculationFailure.CURRENT_PROPOSAL_UNAVAILABLE,
                    validated.relativeBase
                )
        }

        // The selected relative base controls arithmetic for changed fields. During correction,
        // KEEP remains Android-owned and preserves the currently visible unsaved proposal.
        val visibleProposal = currentProposal ?: base
        val timeOffsetCanMoveDate =
            validated.timeOperation == RelativeTemporalOperation.OFFSET
        var proposedDate = if (
            currentProposal != null &&
            validated.dateOperation == RelativeTemporalOperation.KEEP &&
            !timeOffsetCanMoveDate
        ) {
            currentProposal.date.cleanStoredValue()
        } else {
            base.date.cleanStoredValue()
        }
        var proposedTime = if (
            currentProposal != null &&
            validated.timeOperation == RelativeTemporalOperation.KEEP
        ) {
            currentProposal.time.cleanStoredValue()
        } else {
            base.time.cleanStoredValue()
        }

        when (validated.dateOperation) {
            RelativeTemporalOperation.KEEP -> Unit
            RelativeTemporalOperation.SET -> {
                val resolution = resolver.resolve(
                    agentDateText = validated.replacementDateText,
                    agentTimeText = null,
                    originalText = validated.replacementDateText,
                    baseCalendar = clone(now)
                )
                if (resolution.type == TemporalResolutionType.UNRESOLVED) {
                    return failure(
                        RelativeTemporalCalculationFailure.REPLACEMENT_DATE_UNRESOLVED,
                        validated.relativeBase
                    )
                }
                if (!resolution.isExactDate || resolution.startDateInclusive.isNullOrBlank()) {
                    return failure(
                        RelativeTemporalCalculationFailure.REPLACEMENT_DATE_NEEDS_CLARIFICATION,
                        validated.relativeBase
                    )
                }
                val policy = TemporalActionPolicy.evaluate(
                    resolution,
                    TemporalUseCase.RESCHEDULE,
                    clone(now)
                )
                if (policy is TemporalPolicyResult.NeedsExactDate ||
                    policy is TemporalPolicyResult.NeedsExactDateAndTime
                ) {
                    return failure(
                        RelativeTemporalCalculationFailure.REPLACEMENT_DATE_NEEDS_CLARIFICATION,
                        validated.relativeBase
                    )
                }
                proposedDate = resolution.startDateInclusive
            }
            RelativeTemporalOperation.OFFSET -> {
                val date = proposedDate ?: return failure(
                    RelativeTemporalCalculationFailure.MISSING_ORIGINAL_DATE,
                    validated.relativeBase
                )
                val calendar = parseDate(date, now) ?: return failure(
                    RelativeTemporalCalculationFailure.INVALID_STORED_DATE,
                    validated.relativeBase
                )
                calendar.add(Calendar.DAY_OF_MONTH, validated.dateOffsetDays)
                proposedDate = formatDate(calendar)
            }
        }

        when (validated.timeOperation) {
            RelativeTemporalOperation.KEEP -> Unit
            RelativeTemporalOperation.SET -> {
                val resolution = resolver.resolve(
                    agentDateText = null,
                    agentTimeText = validated.replacementTimeText,
                    originalText = validated.replacementTimeText,
                    baseCalendar = clone(now)
                )
                if (resolution.type == TemporalResolutionType.UNRESOLVED) {
                    return failure(
                        RelativeTemporalCalculationFailure.REPLACEMENT_TIME_UNRESOLVED,
                        validated.relativeBase
                    )
                }
                if (!resolution.isExactTime || resolution.startMinuteInclusive == null) {
                    return failure(
                        RelativeTemporalCalculationFailure.REPLACEMENT_TIME_NEEDS_CLARIFICATION,
                        validated.relativeBase
                    )
                }
                val policy = TemporalActionPolicy.evaluate(
                    resolution,
                    TemporalUseCase.RESCHEDULE,
                    clone(now)
                )
                if (policy is TemporalPolicyResult.NeedsExactTime ||
                    policy is TemporalPolicyResult.NeedsExactDateAndTime
                ) {
                    return failure(
                        RelativeTemporalCalculationFailure.REPLACEMENT_TIME_NEEDS_CLARIFICATION,
                        validated.relativeBase
                    )
                }
                proposedTime = formatTime(resolution.startMinuteInclusive)
            }
            RelativeTemporalOperation.OFFSET -> {
                val date = proposedDate ?: return failure(
                    RelativeTemporalCalculationFailure.MISSING_ORIGINAL_DATE,
                    validated.relativeBase
                )
                val time = proposedTime ?: return failure(
                    RelativeTemporalCalculationFailure.MISSING_ORIGINAL_TIME,
                    validated.relativeBase
                )
                val calendar = parseDate(date, now) ?: return failure(
                    RelativeTemporalCalculationFailure.INVALID_STORED_DATE,
                    validated.relativeBase
                )
                val minute = parseTime(time) ?: return failure(
                    RelativeTemporalCalculationFailure.INVALID_STORED_TIME,
                    validated.relativeBase
                )
                calendar.set(Calendar.HOUR_OF_DAY, minute / 60)
                calendar.set(Calendar.MINUTE, minute % 60)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.add(Calendar.MINUTE, validated.timeOffsetMinutes)
                proposedDate = formatDate(calendar)
                proposedTime = formatTime(
                    calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
                )
            }
        }

        val calculated = ExactTemporalSchedule(proposedDate, proposedTime)
        if (currentProposal != null &&
            validated.timeOperation == RelativeTemporalOperation.KEEP &&
            calculated.time.cleanStoredValue() != currentProposal.time.cleanStoredValue()
        ) {
            return failure(
                RelativeTemporalCalculationFailure.UNMENTIONED_FIELD_CHANGED,
                validated.relativeBase
            )
        }
        if (currentProposal != null &&
            validated.dateOperation == RelativeTemporalOperation.KEEP &&
            !timeOffsetCanMoveDate &&
            calculated.date.cleanStoredValue() != currentProposal.date.cleanStoredValue()
        ) {
            return failure(
                RelativeTemporalCalculationFailure.UNMENTIONED_FIELD_CHANGED,
                validated.relativeBase
            )
        }
        if (calculated == visibleProposal) {
            return failure(
                RelativeTemporalCalculationFailure.NO_EFFECTIVE_CHANGE,
                validated.relativeBase
            )
        }
        val crossedDateBoundary =
            visibleProposal.date.cleanStoredValue() != calculated.date.cleanStoredValue()
        val combinedResolution = resolver.resolve(
            agentDateText = calculated.date,
            agentTimeText = calculated.time,
            originalText = listOfNotNull(calculated.date, calculated.time).joinToString(" "),
            baseCalendar = clone(now)
        )
        return if (
            TemporalActionPolicy.evaluate(
                combinedResolution,
                TemporalUseCase.RESCHEDULE,
                clone(now)
            ) is TemporalPolicyResult.InvalidPastSchedule
        ) {
            RelativeTemporalCalculationResult.PastSchedule(
                calculated,
                crossedDateBoundary,
                validated.relativeBase
            )
        } else {
            RelativeTemporalCalculationResult.Success(
                calculated,
                crossedDateBoundary,
                validated.relativeBase
            )
        }
    }

    private fun failure(
        reason: RelativeTemporalCalculationFailure,
        source: RelativeTemporalBase
    ) = RelativeTemporalCalculationResult.Failure(reason, source)

    private fun parseDate(value: String, base: Calendar): Calendar? {
        val formatter = SimpleDateFormat(DATE_PATTERN, Locale.UK).apply {
            isLenient = false
            timeZone = base.timeZone
        }
        val position = ParsePosition(0)
        val parsed = formatter.parse(value, position) ?: return null
        if (position.index != value.length) return null
        return Calendar.getInstance(base.timeZone).apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun parseTime(value: String): Int? {
        val match = TIME_PATTERN.matchEntire(value.trim().uppercase(Locale.UK)) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        val meridiem = match.groupValues[3]
        if (hour !in 1..12 || minute !in 0..59) return null
        if (hour == 12) hour = 0
        if (meridiem == "PM") hour += 12
        return hour * 60 + minute
    }

    private fun formatDate(calendar: Calendar): String =
        SimpleDateFormat(DATE_PATTERN, Locale.UK).apply {
            isLenient = false
            timeZone = calendar.timeZone
        }.format(calendar.time)

    private fun formatTime(minuteOfDay: Int): String {
        val hour24 = minuteOfDay / 60
        val minute = minuteOfDay % 60
        val suffix = if (hour24 < 12) "AM" else "PM"
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return "$hour12:${minute.toString().padStart(2, '0')} $suffix"
    }

    private fun String?.cleanStoredValue(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    @Suppress("UNCHECKED_CAST")
    private fun clone(calendar: Calendar): Calendar = calendar.clone() as Calendar

    private companion object {
        const val DATE_PATTERN = "dd/MM/yyyy"
        val TIME_PATTERN = Regex("""^(\d{1,2}):(\d{2})\s*(AM|PM)$""")
    }
}
