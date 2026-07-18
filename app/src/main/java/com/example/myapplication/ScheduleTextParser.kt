package com.example.myapplication

import com.example.myapplication.ai.temporal.TemporalExpressionResolver

object ScheduleTextParser {
    private val temporalResolver = TemporalExpressionResolver()

    data class ParsedSchedule(
        val dueDate: String? = null,
        val dueTime: String? = null
    ) {
        val hasDate: Boolean get() = !dueDate.isNullOrBlank()
        val hasTime: Boolean get() = !dueTime.isNullOrBlank()
        val isComplete: Boolean get() = hasDate && hasTime
    }

    fun parse(dateText: String?, timeText: String?): ParsedSchedule {
        val resolution = temporalResolver.resolve(dateText, timeText, listOfNotNull(dateText, timeText).joinToString(" "))
        return ParsedSchedule(
            dueDate = resolution.takeIf { it.isExactDate }?.startDateInclusive,
            dueTime = resolution.takeIf { it.isExactTime }?.startMinuteInclusive?.let { formatTime(it / 60, it % 60) }
        )
    }

    fun merge(existingDateText: String?, existingTimeText: String?, newText: String): ParsedSchedule {
        val existing = parse(existingDateText, existingTimeText)
        val incoming = temporalResolver.resolve(null, null, newText)
        return ParsedSchedule(
            dueDate = incoming.takeIf { it.isExactDate }?.startDateInclusive ?: existing.dueDate,
            dueTime = incoming.takeIf { it.isExactTime }?.startMinuteInclusive?.let { formatTime(it / 60, it % 60) } ?: existing.dueTime
        )
    }

    fun parseDate(raw: String?): String? = parse(raw, null).dueDate

    fun parseDateFromSentence(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return temporalResolver.resolve(null, null, raw).takeIf { it.isExactDate }?.startDateInclusive
    }

    fun parseTime(raw: String?): String? = parse(null, raw).dueTime

    fun formatTime(hour: Int, minute: Int): String {
        val ampm = if (hour < 12) "AM" else "PM"
        val formattedHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val formattedMinute = minute.toString().padStart(2, '0')
        return "$formattedHour:$formattedMinute $ampm"
    }
}
