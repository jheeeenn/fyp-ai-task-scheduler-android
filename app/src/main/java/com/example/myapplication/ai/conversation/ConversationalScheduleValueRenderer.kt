package com.example.myapplication.ai.conversation

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Presents already-authoritative schedule values naturally without resolving or changing them. */
object ConversationalScheduleValueRenderer {
    fun date(
        authoritativeDate: String?,
        baseCalendar: Calendar = Calendar.getInstance(),
        emptyValue: String = "no date"
    ): String {
        val rawValue = authoritativeDate?.trim().orEmpty()
        if (rawValue.isEmpty()) return emptyValue
        val target = parseAuthoritativeDate(rawValue, baseCalendar) ?: return rawValue
        val base = startOfDay(baseCalendar)

        return when {
            sameDay(target, base) -> "today"
            sameDay(target, addDays(base, 1)) -> "tomorrow"
            inWeek(target, startOfWeek(base)) -> "this ${weekday(target)}"
            inWeek(target, addDays(startOfWeek(base), 7)) -> "next ${weekday(target)}"
            target.get(Calendar.YEAR) == base.get(Calendar.YEAR) ->
                format(target, "EEEE, d MMMM")
            else -> format(target, "EEEE, d MMMM yyyy")
        }
    }

    fun time(authoritativeTime: String?, emptyValue: String = "no time"): String {
        val rawValue = authoritativeTime?.trim().orEmpty()
        if (rawValue.isEmpty()) return emptyValue
        val match = EXACT_TWELVE_HOUR_TIME.matchEntire(rawValue) ?: return rawValue
        val hour = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..12 }
            ?: return rawValue
        val minute = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 }
            ?: return rawValue
        val meridiem = match.groupValues[3].uppercase(Locale.UK)

        return when {
            hour == 12 && minute == 0 && meridiem == "PM" -> "noon"
            hour == 12 && minute == 0 && meridiem == "AM" -> "midnight"
            minute == 0 -> "$hour $meridiem"
            else -> "%d:%02d %s".format(Locale.UK, hour, minute, meridiem)
        }
    }

    private fun parseAuthoritativeDate(rawValue: String, base: Calendar): Calendar? {
        val parser = SimpleDateFormat("dd/MM/yyyy", Locale.UK).apply {
            isLenient = false
            timeZone = base.timeZone
        }
        val position = ParsePosition(0)
        val parsed = parser.parse(rawValue, position) ?: return null
        if (position.index != rawValue.length) return null
        return Calendar.getInstance(base.timeZone, Locale.UK).apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun startOfDay(calendar: Calendar): Calendar =
        (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun startOfWeek(calendar: Calendar): Calendar = startOfDay(calendar).apply {
        add(Calendar.DAY_OF_MONTH, -(mondayBasedDayIndex(this) - 1))
    }

    private fun mondayBasedDayIndex(calendar: Calendar): Int =
        if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            7
        } else {
            calendar.get(Calendar.DAY_OF_WEEK) - 1
        }

    private fun inWeek(target: Calendar, weekStart: Calendar): Boolean {
        val weekEnd = addDays(weekStart, 6)
        return !target.before(weekStart) && !target.after(weekEnd)
    }

    private fun sameDay(first: Calendar, second: Calendar): Boolean =
        first.get(Calendar.ERA) == second.get(Calendar.ERA) &&
            first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)

    private fun addDays(calendar: Calendar, days: Int): Calendar =
        (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, days) }

    private fun weekday(calendar: Calendar): String = format(calendar, "EEEE")

    private fun format(calendar: Calendar, pattern: String): String =
        SimpleDateFormat(pattern, Locale.UK).apply {
            timeZone = calendar.timeZone
        }.format(calendar.time)

    private val EXACT_TWELVE_HOUR_TIME = Regex(
        """^(\d{1,2}):(\d{2})\s*([ap]m)$""",
        RegexOption.IGNORE_CASE
    )
}
