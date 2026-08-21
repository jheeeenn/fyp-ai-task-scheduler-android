package com.example.myapplication.ai.temporal

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class TemporalUseCase { QUERY, CREATE, UPDATE, RESCHEDULE, BREAKDOWN, TASK_MATCH }

sealed class TemporalPolicyResult {
    data class Ready(val resolution: TemporalResolution) : TemporalPolicyResult()
    data class NeedsExactDate(val resolution: TemporalResolution) : TemporalPolicyResult()
    data class NeedsExactTime(val resolution: TemporalResolution) : TemporalPolicyResult()
    data class NeedsExactDateAndTime(val resolution: TemporalResolution) : TemporalPolicyResult()
    data class Unresolved(val resolution: TemporalResolution) : TemporalPolicyResult()
    data class InvalidPastSchedule(val resolution: TemporalResolution) : TemporalPolicyResult()
}

object TemporalActionPolicy {
    fun evaluate(
        resolution: TemporalResolution,
        useCase: TemporalUseCase,
        baseCalendar: Calendar = Calendar.getInstance()
    ): TemporalPolicyResult {
        if (resolution.type == TemporalResolutionType.UNRESOLVED) return TemporalPolicyResult.Unresolved(resolution)
        if (useCase == TemporalUseCase.QUERY || useCase == TemporalUseCase.TASK_MATCH) return TemporalPolicyResult.Ready(resolution)
        val hasExactDate = resolution.isExactDate
        val hasExactTime = resolution.isExactTime
        val hasDate = resolution.hasDateConstraint
        val hasTime = resolution.hasTimeConstraint
        if (useCase in FUTURE_ONLY_USE_CASES && isWhollyPast(resolution, baseCalendar)) {
            return TemporalPolicyResult.InvalidPastSchedule(resolution)
        }
        return when (useCase) {
            TemporalUseCase.CREATE, TemporalUseCase.BREAKDOWN -> when {
                hasExactDate && hasExactTime -> TemporalPolicyResult.Ready(resolution)
                hasExactDate && !hasTime -> TemporalPolicyResult.NeedsExactTime(resolution)
                hasExactTime && !hasDate -> TemporalPolicyResult.NeedsExactDate(resolution)
                hasDate && hasExactTime -> TemporalPolicyResult.NeedsExactDate(resolution)
                hasExactDate && hasTime -> TemporalPolicyResult.NeedsExactTime(resolution)
                hasDate && hasTime -> TemporalPolicyResult.NeedsExactDateAndTime(resolution)
                hasDate || hasTime -> TemporalPolicyResult.NeedsExactDateAndTime(resolution)
                else -> TemporalPolicyResult.NeedsExactDateAndTime(resolution)
            }
            TemporalUseCase.UPDATE, TemporalUseCase.RESCHEDULE -> when {
                hasExactDate && hasExactTime -> TemporalPolicyResult.Ready(resolution)
                hasExactDate && !hasTime -> TemporalPolicyResult.Ready(resolution)
                hasExactTime && !hasDate -> TemporalPolicyResult.Ready(resolution)
                hasDate && hasExactTime -> TemporalPolicyResult.NeedsExactDate(resolution)
                hasExactDate && hasTime -> TemporalPolicyResult.NeedsExactTime(resolution)
                hasDate && hasTime -> TemporalPolicyResult.NeedsExactDateAndTime(resolution)
                hasDate -> TemporalPolicyResult.NeedsExactDate(resolution)
                hasTime -> TemporalPolicyResult.NeedsExactTime(resolution)
                else -> TemporalPolicyResult.Ready(resolution)
            }
            else -> TemporalPolicyResult.Ready(resolution)
        }
    }

    fun validateClarification(original: TemporalResolution, exactDate: String?, exactMinute: Int?): Boolean {
        if (exactDate != null && original.hasDateConstraint) {
            val d = parseDate(exactDate) ?: return false
            parseDate(original.startDateInclusive)?.let { if (d < it) return false }
            parseDate(original.endDateInclusive)?.let { if (d > it) return false }
        }
        if (exactMinute != null && original.hasTimeConstraint) {
            val s = original.startMinuteInclusive ?: 0
            val e = original.endMinuteInclusive ?: 1439
            if (if (original.wrapsMidnight) exactMinute < s && exactMinute > e else exactMinute !in s..e) return false
        }
        return true
    }

    internal fun isWhollyPast(r: TemporalResolution, base: Calendar): Boolean {
        val endDate = parseDate(
            r.endDateInclusive ?: r.startDateInclusive,
            base.timeZone
        ) ?: return false
        val today = (base.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.time.time
        if (endDate < today) return true
        if (endDate == today && r.hasTimeConstraint && !r.wrapsMidnight) return (r.endMinuteInclusive ?: 1439) < base.get(Calendar.HOUR_OF_DAY) * 60 + base.get(Calendar.MINUTE)
        return false
    }

    private fun parseDate(
        value: String?,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long? = try {
        if (value.isNullOrBlank()) {
            null
        } else {
            SimpleDateFormat("dd/MM/yyyy", Locale.UK).apply {
                isLenient = false
                this.timeZone = timeZone
            }.parse(value)?.time
        }
    } catch (_: Exception) {
        null
    }

    private val FUTURE_ONLY_USE_CASES = setOf(
        TemporalUseCase.CREATE,
        TemporalUseCase.BREAKDOWN
    )
}
