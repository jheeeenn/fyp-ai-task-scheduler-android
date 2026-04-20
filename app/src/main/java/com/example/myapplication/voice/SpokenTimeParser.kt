package com.example.myapplication.voice

object SpokenTimeParser {

    private val semanticTimes = mapOf(
        "morning" to Pair(9, 0),
        "this morning" to Pair(9, 0),
        "noon" to Pair(12, 0),
        "midday" to Pair(12, 0),
        "afternoon" to Pair(15, 0),
        "this afternoon" to Pair(15, 0),
        "evening" to Pair(19, 0),
        "tonight" to Pair(20, 0),
        "after lunch" to Pair(14, 0),
        "after dinner" to Pair(20, 0),
        "midnight" to Pair(0, 0)
    )

    fun clean(input: String): String {
        return input
            .lowercase()
            .replace("a.m.", "am")
            .replace("p.m.", "pm")
            .replace(Regex("\\ba\\.?\\s*m\\.?\\b"), "am")
            .replace(Regex("\\bp\\.?\\s*m\\.?\\b"), "pm")
            .replace(Regex("\\bat\\b"), " ")
            .replace(Regex("\\s*:\\s*"), ":")
            .replace(Regex("""\b(\d{1,2}):(\d{2})(am|pm)\b"""), "$1:$2 $3")
            .replace(Regex("""\b(\d{1,2})(am|pm)\b"""), "$1 $2")
            .replace(Regex("[.,!?]+$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun parseToHourMinute(rawInput: String): Pair<Int, Int>? {
        val cleaned = clean(rawInput)

        semanticTimes[cleaned]?.let { return it }

        val twelveHourPattern = Regex("""^(\d{1,2})(?::|\s)?(\d{2})?\s*(am|pm)$""")
        val twentyFourHourPattern = Regex("""^(\d{1,2})(?::|\s)(\d{2})$""")
        val oClockPattern = Regex("""^(\d{1,2})\s*o'?clock(\s*(am|pm))?$""")
        val quarterPastPattern = Regex("""^quarter\s+past\s+(\d{1,2})\s*(am|pm)?$""")
        val halfPastPattern = Regex("""^half\s+past\s+(\d{1,2})\s*(am|pm)?$""")
        val quarterToPattern = Regex("""^quarter\s+to\s+(\d{1,2})\s*(am|pm)?$""")

        twelveHourPattern.find(cleaned)?.let { match ->
            val hourRaw = match.groupValues[1].toIntOrNull() ?: return null
            val minuteRaw = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            val amPm = match.groupValues[3]
            if (hourRaw !in 1..12 || minuteRaw !in 0..59) return null

            var hour24 = hourRaw
            if (amPm == "pm" && hour24 != 12) hour24 += 12
            if (amPm == "am" && hour24 == 12) hour24 = 0
            return Pair(hour24, minuteRaw)
        }

        twentyFourHourPattern.find(cleaned)?.let { match ->
            val hour24 = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            if (hour24 !in 0..23 || minute !in 0..59) return null
            return Pair(hour24, minute)
        }

        oClockPattern.find(cleaned)?.let { match ->
            val hourRaw = match.groupValues[1].toIntOrNull() ?: return null
            val amPm = match.groupValues[3]
            if (hourRaw !in 1..12) return null
            if (amPm.isBlank()) return null
            var hour24 = hourRaw
            if (amPm == "pm" && hour24 != 12) hour24 += 12
            if (amPm == "am" && hour24 == 12) hour24 = 0
            return Pair(hour24, 0)
        }

        quarterPastPattern.find(cleaned)?.let { match ->
            val hourRaw = match.groupValues[1].toIntOrNull() ?: return null
            val amPm = match.groupValues[2]
            if (hourRaw !in 1..12 || amPm.isBlank()) return null
            var hour24 = hourRaw
            if (amPm == "pm" && hour24 != 12) hour24 += 12
            if (amPm == "am" && hour24 == 12) hour24 = 0
            return Pair(hour24, 15)
        }

        halfPastPattern.find(cleaned)?.let { match ->
            val hourRaw = match.groupValues[1].toIntOrNull() ?: return null
            val amPm = match.groupValues[2]
            if (hourRaw !in 1..12 || amPm.isBlank()) return null
            var hour24 = hourRaw
            if (amPm == "pm" && hour24 != 12) hour24 += 12
            if (amPm == "am" && hour24 == 12) hour24 = 0
            return Pair(hour24, 30)
        }

        quarterToPattern.find(cleaned)?.let { match ->
            val nextHourRaw = match.groupValues[1].toIntOrNull() ?: return null
            val amPm = match.groupValues[2]
            if (nextHourRaw !in 1..12 || amPm.isBlank()) return null
            val baseHour = if (nextHourRaw == 1) 12 else nextHourRaw - 1
            var hour24 = baseHour
            if (amPm == "pm" && hour24 != 12) hour24 += 12
            if (amPm == "am" && hour24 == 12) hour24 = 0
            return Pair(hour24, 45)
        }

        return null
    }
}
