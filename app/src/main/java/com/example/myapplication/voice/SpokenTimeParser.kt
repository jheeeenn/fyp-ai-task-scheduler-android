package com.example.myapplication.voice

object SpokenTimeParser {

    private val twelveHourPattern = Regex("""\b(\d{1,2})(?::|\s)?(\d{2})?\s?(am|pm)\b""")
    private val twentyFourHourPattern = Regex("""\b(\d{1,2})(?::|\s)(\d{2})\b""")
    private val oClockPattern = Regex("""\b(\d{1,2})\s*o'?clock\s?(am|pm)\b""")
    private val quarterPastPattern = Regex("""\bquarter\s+past\s+(\d{1,2})(?::\d{2})?\s?(am|pm)\b""")
    private val halfPastPattern = Regex("""\bhalf\s+past\s+(\d{1,2})(?::\d{2})?\s?(am|pm)\b""")
    private val quarterToPattern = Regex("""\bquarter\s+to\s+(\d{1,2})(?::\d{2})?\s?(am|pm)\b""")

    private val semanticTimes = mapOf(
        "midnight" to (0 to 0),
        "midday" to (12 to 0),
        "noon" to (12 to 0),
        "morning" to (9 to 0),
        "this morning" to (9 to 0),
        "before lunch" to (11 to 0),
        "at lunch" to (12 to 0),
        "after lunch" to (14 to 0),
        "afternoon" to (15 to 0),
        "this afternoon" to (15 to 0),
        "after class" to (17 to 0),
        "evening" to (19 to 0),
        "after dinner" to (19 to 0),
        "tonight" to (20 to 0),
        "after breakfast" to (8 to 0)
    )

    fun parseToHourMinute(raw: String?): Pair<Int, Int>? {
        if (raw.isNullOrBlank()) return null

        val text = raw.lowercase()
            .replace(Regex("\\ba\\.?\\s*m\\.?\\b"), "am")
            .replace(Regex("\\bp\\.?\\s*m\\.?\\b"), "pm")
            .replace(Regex("\\bat\\b"), " ")
            .replace(Regex("[.,!?]+$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        semanticTimes.entries.firstOrNull { text.contains(it.key) }?.let { return it.value }

        quarterPastPattern.find(text)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val meridiem = match.groupValues[2]
            return to24Hour(hour, 15, meridiem)
        }

        halfPastPattern.find(text)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val meridiem = match.groupValues[2]
            return to24Hour(hour, 30, meridiem)
        }

        quarterToPattern.find(text)?.let { match ->
            var hour = match.groupValues[1].toIntOrNull() ?: return null
            val meridiem = match.groupValues[2]
            hour -= 1
            if (hour <= 0) hour = 12
            return to24Hour(hour, 45, meridiem)
        }

        oClockPattern.find(text)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val meridiem = match.groupValues[2]
            return to24Hour(hour, 0, meridiem)
        }

        twelveHourPattern.find(text)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null
            val meridiem = match.groupValues[3]
            return to24Hour(hour, minute, meridiem)
        }

        twentyFourHourPattern.find(text)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour to minute
        }

        return null
    }

    private fun to24Hour(hour12: Int, minute: Int, meridiem: String): Pair<Int, Int>? {
        if (hour12 !in 1..12 || minute !in 0..59) return null

        val hour24 = when (meridiem) {
            "am" -> if (hour12 == 12) 0 else hour12
            "pm" -> if (hour12 == 12) 12 else hour12 + 12
            else -> return null
        }

        return hour24 to minute
    }
}