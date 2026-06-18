package com.example.myapplication

import com.example.myapplication.ai.LocalDateParser
import com.example.myapplication.voice.SpokenTimeParser

object ScheduleTextParser {
    private val localDateParser = LocalDateParser()

    data class ParsedSchedule(
        val dueDate: String? = null,
        val dueTime: String? = null
    ) {
        val hasDate: Boolean get() = !dueDate.isNullOrBlank()
        val hasTime: Boolean get() = !dueTime.isNullOrBlank()
        val isComplete: Boolean get() = hasDate && hasTime
    }

    fun parse(dateText: String?, timeText: String?): ParsedSchedule {
        val date = parseDate(dateText)
        val time = parseTime(timeText)
        return ParsedSchedule(dueDate = date, dueTime = time)
    }

    fun merge(existingDateText: String?, existingTimeText: String?, newText: String): ParsedSchedule {
        val existing = parse(existingDateText, existingTimeText)
        val extractedDate = parseDateFromSentence(newText) ?: existing.dueDate
        val extractedTime = parseTime(newText) ?: existing.dueTime
        return ParsedSchedule(dueDate = extractedDate, dueTime = extractedTime)
    }

    fun parseDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val direct = localDateParser.parse(raw)
        if (direct.success && !direct.normalizedDate.isNullOrBlank()) {
            return direct.normalizedDate
        }
        return parseDateFromSentence(raw)
    }

    fun parseDateFromSentence(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val extracted = localDateParser.extractFromSentence(raw)
        return if (extracted.success && !extracted.normalizedDate.isNullOrBlank()) {
            extracted.normalizedDate
        } else {
            null
        }
    }

    fun parseTime(raw: String?): String? {
        val parsed = SpokenTimeParser.parseToHourMinute(raw) ?: return null
        return formatTime(parsed.first, parsed.second)
    }

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
