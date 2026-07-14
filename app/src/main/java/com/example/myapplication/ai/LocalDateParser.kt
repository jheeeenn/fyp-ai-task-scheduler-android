package com.example.myapplication.ai

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class LocalDateParseResult(
    val success: Boolean,
    val normalizedDate: String? = null,
    val year: Int? = null,
    val month: Int? = null,   // Calendar month, 0-based
    val day: Int? = null,
    val matchedPhrase: String? = null,
    val confidence: Float = 0f
)

class LocalDateParser {

    fun parse(rawText: String): LocalDateParseResult {
        return parse(rawText, Calendar.getInstance())
    }

    internal fun parse(rawText: String, baseCalendar: Calendar): LocalDateParseResult {
        val text = normalize(rawText)
        val today = baseCalendar.clone() as Calendar

        parseRelativeKeyword(text, today)?.let { return it }
        parseWeekdayExpression(text, today)?.let { return it }
        parseExplicitDate(text, today)?.let { return it }

        return LocalDateParseResult(success = false)
    }

    fun extractFromSentence(rawText: String): LocalDateParseResult {
        val text = normalize(rawText)
        val today = Calendar.getInstance()

        val candidates = buildCandidatePhrases(text)

        for (candidate in candidates) {
            parse(candidate)?.let {
                if (it.success) {
                    return it.copy(matchedPhrase = candidate)
                }
            }
        }

        return LocalDateParseResult(success = false)
    }

    private fun buildCandidatePhrases(text: String): List<String> {
        val phrases = mutableListOf<String>()

        val regexCandidates = listOf(
            Regex("""\bday after tomorrow\b"""),
            Regex("""\bthe day after tomorrow\b"""),
            Regex("""\btoday\b"""),
            Regex("""\btomorrow\b"""),
            Regex("""\btonight\b"""),
            Regex("""\bnext\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b"""),
            Regex("""\bthis\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b"""),
            Regex("""\bon\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b"""),
            Regex("""\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b"""),
            Regex("""\b\d{1,2}/\d{1,2}/\d{2,4}\b"""),
            Regex("""\b\d{4}/\d{1,2}/\d{1,2}\b"""),
            Regex("""\b\d{1,2}\s+(jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december)\s+\d{4}\b"""),
            Regex("""\b\d{1,2}\s+(jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december)\b"""),
            Regex("""\b(jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december)\s+\d{1,2}(\s+\d{4})?\b""")
        )

        for (regex in regexCandidates) {
            regex.findAll(text).forEach { match ->
                phrases.add(match.value.trim())
            }
        }

        return phrases
            .map { it.removePrefix("on ").trim() }
            .distinct()
            .sortedByDescending { it.length }
    }

    private fun normalize(text: String): String {
        return text
            .lowercase(Locale.UK)
            .replace(",", " ")
            .replace("-", "/")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseRelativeKeyword(
        text: String,
        base: Calendar
    ): LocalDateParseResult? {
        val calendar = base.clone() as Calendar

        return when (text) {
            "today", "todays", "for today" -> buildResult(calendar, text, 0.98f)
            "tomorrow", "for tomorrow" -> {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                buildResult(calendar, text, 0.98f)
            }
            "tonight" -> buildResult(calendar, text, 0.92f)
            "day after tomorrow", "the day after tomorrow" -> {
                calendar.add(Calendar.DAY_OF_MONTH, 2)
                buildResult(calendar, text, 0.95f)
            }
            else -> null
        }
    }

    private fun parseWeekdayExpression(
        text: String,
        base: Calendar
    ): LocalDateParseResult? {
        val weekdayMap = mapOf(
            "monday" to Calendar.MONDAY,
            "tuesday" to Calendar.TUESDAY,
            "wednesday" to Calendar.WEDNESDAY,
            "thursday" to Calendar.THURSDAY,
            "friday" to Calendar.FRIDAY,
            "saturday" to Calendar.SATURDAY,
            "sunday" to Calendar.SUNDAY
        )

        val cleaned = text
            .removePrefix("on ")
            .removePrefix("this ")
            .removePrefix("next ")
            .trim()

        val targetDay = weekdayMap[cleaned] ?: return null
        val calendar = base.clone() as Calendar
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK)

        val isNext = text.startsWith("next ")
        val isThis = text.startsWith("this ")

        var diff = targetDay - currentDay
        if (diff < 0) diff += 7

        if (isNext) {
            val currentMondayBased = if (currentDay == Calendar.SUNDAY) 7 else currentDay - 1
            val targetMondayBased = if (targetDay == Calendar.SUNDAY) 7 else targetDay - 1
            diff = (8 - currentMondayBased) + (targetMondayBased - 1)
        } else if (isThis) {
            val currentMondayBased = if (currentDay == Calendar.SUNDAY) 7 else currentDay - 1
            val targetMondayBased = if (targetDay == Calendar.SUNDAY) 7 else targetDay - 1
            diff = targetMondayBased - currentMondayBased
        }

        calendar.add(Calendar.DAY_OF_MONTH, diff)

        return buildResult(calendar, text, if (isNext || isThis) 0.9f else 0.85f)
    }

    private fun parseExplicitDate(
        text: String,
        base: Calendar
    ): LocalDateParseResult? {
        val candidates = listOf(
            "d/M/yyyy",
            "dd/MM/yyyy",
            "d/MM/yyyy",
            "dd/M/yyyy",
            "d/M/yy",
            "dd/MM/yy",
            "yyyy/M/d",
            "yyyy/MM/dd",
            "d MMM yyyy",
            "dd MMM yyyy",
            "d MMMM yyyy",
            "dd MMMM yyyy",
            "MMM d yyyy",
            "MMMM d yyyy",
            "d MMM",
            "dd MMM",
            "d MMMM",
            "dd MMMM",
            "MMM d",
            "MMMM d"
        )

        for (pattern in candidates) {
            try {
                val formatter = SimpleDateFormat(pattern, Locale.UK)
                formatter.isLenient = false
                val parsed = formatter.parse(text) ?: continue

                val calendar = Calendar.getInstance()
                calendar.time = parsed

                val hasNoYear = !pattern.contains("y")
                if (hasNoYear) {
                    calendar.set(Calendar.YEAR, base.get(Calendar.YEAR))
                    if (calendar.before(stripTime(base))) {
                        calendar.add(Calendar.YEAR, 1)
                    }
                }

                return buildResult(calendar, text, 0.88f)
            } catch (_: Exception) {
            }
        }

        return null
    }

    private fun stripTime(calendar: Calendar): Calendar {
        return (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun buildResult(
        calendar: Calendar,
        matchedPhrase: String,
        confidence: Float
    ): LocalDateParseResult {
        val normalizedDate = "%02d/%02d/%04d".format(
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.YEAR)
        )

        return LocalDateParseResult(
            success = true,
            normalizedDate = normalizedDate,
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH),
            day = calendar.get(Calendar.DAY_OF_MONTH),
            matchedPhrase = matchedPhrase,
            confidence = confidence
        )
    }


}