package com.example.myapplication.ai.temporal

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TemporalQueryResolver {
    private val out = SimpleDateFormat("dd/MM/yyyy", Locale.UK).apply { isLenient = false }
    private val monthNames = "jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december"
    private val weekdays = mapOf(
        "sunday" to Calendar.SUNDAY, "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY, "thursday" to Calendar.THURSDAY, "friday" to Calendar.FRIDAY, "saturday" to Calendar.SATURDAY
    )

    fun resolve(agentDateText: String?, agentTimeText: String?, originalText: String, baseCalendar: Calendar = Calendar.getInstance()): TemporalQueryWindow {
        val dateSource = clean(agentDateText)
        val timeSource = clean(agentTimeText)
        val original = clean(originalText)
        val supplied = dateSource.isNotBlank() || timeSource.isNotBlank()
        val dateText = dateSource.ifBlank { extractDatePhrase(original) }
        val timeText = timeSource.ifBlank { extractTimePhrase(dateSource).ifBlank { extractTimePhrase(original) } }
        if (!supplied && dateText.isBlank() && timeText.isBlank()) return TemporalQueryWindow(TemporalResolutionStatus.NONE)
        val preferredDateText = if (dateText.isBlank()) "" else dateText
        val fallbackDateText = if (preferredDateText.isNotBlank()) extractDatePhrase(preferredDateText) else ""
        val dateWindow = if (preferredDateText.isBlank()) {
            null
        } else {
            parseDateWindow(preferredDateText, baseCalendar)
                ?: fallbackDateText.takeIf { it.isNotBlank() && it != preferredDateText }
                    ?.let { parseDateWindow(it, baseCalendar) }
        }
        val effectiveDateText = if (dateWindow != null && fallbackDateText.isNotBlank()) fallbackDateText else dateText
        val timeWindow = if (timeText.isBlank()) null else parseTimeWindow(timeText)
        if ((dateText.isNotBlank() && dateWindow == null) || (timeText.isNotBlank() && timeWindow == null)) {
            return TemporalQueryWindow(TemporalResolutionStatus.UNRESOLVED, spokenLabel = listOf(dateText, timeText).filter { it.isNotBlank() }.joinToString(" "))
        }
        if (dateWindow == null && timeWindow == null) return if (supplied) TemporalQueryWindow(TemporalResolutionStatus.UNRESOLVED) else TemporalQueryWindow(TemporalResolutionStatus.NONE)
        return merge(dateWindow, timeWindow, listOf(effectiveDateText, timeText).filter { it.isNotBlank() }.distinct().joinToString(" "))
    }

    private fun clean(s: String?) = s.orEmpty().lowercase(Locale.UK).replace(",", " ").replace(Regex("\\s+"), " ").trim()
    private fun merge(d: TemporalQueryWindow?, t: TemporalQueryWindow?, label: String) = TemporalQueryWindow(
        status = TemporalResolutionStatus.RESOLVED,
        dateScope = d?.dateScope ?: TemporalDateScope.ALL,
        startDateInclusive = d?.startDateInclusive,
        endDateInclusive = d?.endDateInclusive,
        startMinuteInclusive = t?.startMinuteInclusive,
        endMinuteInclusive = t?.endMinuteInclusive,
        wrapsMidnight = t?.wrapsMidnight ?: false,
        spokenLabel = label
    )

    private fun extractDatePhrase(text: String): String = listOf(
        Regex("\\b(overdue|upcoming)(?: tasks)?\\b"), Regex("\\bfrom (.+?) onward\\b"), Regex("\\b(?:from|between) .+? (?:to|and) .+?(?= before | after | in the | at |$)"),
        Regex("\\b(?:this|next) (?:weekend|week|month|year)\\b"), Regex("\\bnext \\d+ (?:days|weeks)\\b"),
        Regex("\\b(?:before|after) (?:$monthNames|\\d|monday|tuesday|wednesday|thursday|friday|saturday|sunday).+?(?= before | after | at |$)"),
        Regex("\\bday after tomorrow\\b"), Regex("\\b(?:today|tomorrow|tonight)\\b"), Regex("\\b(?:this |next )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b"),
        Regex("\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b"), Regex("\\b\\d{4}/\\d{1,2}/\\d{1,2}\\b"),
        Regex("\\b\\d{1,2} (?:$monthNames)(?: \\d{4})?\\b"), Regex("\\b(?:$monthNames) \\d{1,2}(?: \\d{4})?\\b")
    ).firstNotNullOfOrNull { it.find(text)?.value?.trim() }.orEmpty()

    private fun extractTimePhrase(text: String): String = listOf(
        Regex("\\bbetween .+? and .+?(?:am|pm|noon|midnight)\\b"), Regex("\\bfrom .+? to .+?(?:am|pm|noon|midnight)\\b"),
        Regex("\\b(?:before|after|at) (?:\\d{1,2}(?::\\d{2})? ?(?:am|pm)?|noon|midnight)\\b"), Regex("\\bin the (morning|afternoon|evening)\\b"),
        Regex("\\b(morning|afternoon|evening|night|tonight)\\b"), Regex("\\b(noon|midnight)\\b")
    ).firstNotNullOfOrNull { it.find(text)?.value?.removePrefix("in the ")?.trim() }.orEmpty()

    private fun parseDateWindow(text: String, base: Calendar): TemporalQueryWindow? {
        val t = text.removePrefix("tasks ").trim()
        if (t.startsWith("overdue")) return TemporalQueryWindow(TemporalResolutionStatus.RESOLVED, TemporalDateScope.OVERDUE, endDateInclusive = fmt(add(base, Calendar.DAY_OF_MONTH, -1)))
        if (t.startsWith("upcoming")) return TemporalQueryWindow(TemporalResolutionStatus.RESOLVED, TemporalDateScope.UPCOMING, startDateInclusive = fmt(base))
        Regex("from (.+) onward").matchEntire(t)?.let { val s = parseSingleDate(it.groupValues[1], base) ?: return null; return range(TemporalDateScope.UPCOMING, s, null) }
        Regex("before (.+)").matchEntire(t)?.let { val e = parseSingleDate(it.groupValues[1], base) ?: return null; e.add(Calendar.DAY_OF_MONTH, -1); return range(TemporalDateScope.DATE_RANGE, null, e) }
        Regex("after (.+)").matchEntire(t)?.let { val s = parseSingleDate(it.groupValues[1], base) ?: return null; s.add(Calendar.DAY_OF_MONTH, 1); return range(TemporalDateScope.DATE_RANGE, s, null) }
        Regex("(?:from|between) (.+) (?:to|and) (.+)").matchEntire(t)?.let {
            val s = parseSingleDate(it.groupValues[1], base) ?: return null; val e = parseSingleDate(it.groupValues[2], base) ?: return null
            while (strip(e).before(strip(s))) e.add(Calendar.DAY_OF_MONTH, 7)
            return range(TemporalDateScope.DATE_RANGE, s, e)
        }
        parseCalendarRange(t, base)?.let { return it }
        val single = parseSingleDate(t, base) ?: return null
        return range(TemporalDateScope.EXACT_DATE, single, single)
    }

    private fun parseCalendarRange(t: String, base: Calendar): TemporalQueryWindow? {
        val start = strip(base)
        return when {
            t == "this week" -> { start.add(Calendar.DAY_OF_MONTH, -(dayIndex(start) - 1)); range(TemporalDateScope.DATE_RANGE, start, add(start, Calendar.DAY_OF_MONTH, 6)) }
            t == "next week" -> { start.add(Calendar.DAY_OF_MONTH, 8 - dayIndex(start)); range(TemporalDateScope.DATE_RANGE, start, add(start, Calendar.DAY_OF_MONTH, 6)) }
            t == "this weekend" -> { start.add(Calendar.DAY_OF_MONTH, 6 - dayIndex(start)); range(TemporalDateScope.DATE_RANGE, start, add(start, Calendar.DAY_OF_MONTH, 1)) }
            t == "next weekend" -> { start.add(Calendar.DAY_OF_MONTH, 13 - dayIndex(start)); range(TemporalDateScope.DATE_RANGE, start, add(start, Calendar.DAY_OF_MONTH, 1)) }
            t == "this month" -> { start.set(Calendar.DAY_OF_MONTH, 1); val e = add(start, Calendar.MONTH, 1); e.add(Calendar.DAY_OF_MONTH, -1); range(TemporalDateScope.DATE_RANGE, start, e) }
            t == "next month" -> { start.set(Calendar.DAY_OF_MONTH, 1); start.add(Calendar.MONTH, 1); val e = add(start, Calendar.MONTH, 1); e.add(Calendar.DAY_OF_MONTH, -1); range(TemporalDateScope.DATE_RANGE, start, e) }
            t == "this year" -> { start.set(Calendar.DAY_OF_YEAR, 1); val e = add(start, Calendar.YEAR, 1); e.add(Calendar.DAY_OF_MONTH, -1); range(TemporalDateScope.DATE_RANGE, start, e) }
            t == "next year" -> { start.set(Calendar.DAY_OF_YEAR, 1); start.add(Calendar.YEAR, 1); val e = add(start, Calendar.YEAR, 1); e.add(Calendar.DAY_OF_MONTH, -1); range(TemporalDateScope.DATE_RANGE, start, e) }
            Regex("next (\\d+) days").matchEntire(t) != null -> { val n = Regex("\\d+").find(t)!!.value.toInt(); range(TemporalDateScope.DATE_RANGE, start, add(start, Calendar.DAY_OF_MONTH, n - 1)) }
            Regex("next (\\d+) weeks").matchEntire(t) != null -> { val n = Regex("\\d+").find(t)!!.value.toInt(); range(TemporalDateScope.DATE_RANGE, start, add(start, Calendar.DAY_OF_MONTH, n * 7 - 1)) }
            else -> null
        }
    }

    private fun parseSingleDate(text: String, base: Calendar): Calendar? {
        val t = text.removePrefix("on ").trim(); val c = strip(base)
        when (t) { "today", "tonight" -> return c; "tomorrow" -> return add(c, Calendar.DAY_OF_MONTH, 1); "day after tomorrow", "the day after tomorrow" -> return add(c, Calendar.DAY_OF_MONTH, 2) }
        val parts = t.split(" "); val prefix = if (parts.size == 2) parts[0] else ""; val weekday = parts.last(); weekdays[weekday]?.let { target ->
            val current = c.get(Calendar.DAY_OF_WEEK); var diff = target - current; if (diff < 0) diff += 7
            if (prefix == "this") diff = target - Calendar.MONDAY - (dayIndex(c) - 1) else if (prefix == "next") diff = (8 - dayIndex(c)) + (target - Calendar.MONDAY)
            return add(c, Calendar.DAY_OF_MONTH, diff)
        }
        val patterns = listOf("d/M/yyyy","dd/MM/yyyy","yyyy/M/d","yyyy/MM/dd","d MMM yyyy","d MMMM yyyy","MMM d yyyy","MMMM d yyyy","d MMM","d MMMM","MMM d","MMMM d")
        for (p in patterns) try { val f = SimpleDateFormat(p, Locale.UK).apply { isLenient = false }; val parsed = f.parse(t) ?: continue; val cal = Calendar.getInstance(base.timeZone); cal.time = parsed; if (!p.contains("y")) { cal.set(Calendar.YEAR, base.get(Calendar.YEAR)); if (strip(cal).before(strip(base))) cal.add(Calendar.YEAR, 1) }; return strip(cal) } catch (_: Exception) {}
        return null
    }

    private fun parseTimeWindow(text: String): TemporalQueryWindow? {
        val t = text.trim()
        when (t) { "morning" -> return time(300, 719); "afternoon" -> return time(720, 1019); "evening" -> return time(1020, 1259); "night", "tonight" -> return time(1260, 299, true); "noon" -> return time(720,720); "midnight" -> return time(0,0) }
        Regex("at (.+)").matchEntire(t)?.let { val m = parseMinute(it.groupValues[1]) ?: return null; return time(m, m) }
        Regex("before (.+)").matchEntire(t)?.let { val m = parseMinute(it.groupValues[1]) ?: return null; return time(0, (m - 1).coerceAtLeast(0)) }
        Regex("after (.+)").matchEntire(t)?.let { val m = parseMinute(it.groupValues[1]) ?: return null; return time((m + 1).coerceAtMost(1439), 1439) }
        Regex("(?:between|from) (.+) (?:and|to) (.+)").matchEntire(t)?.let { val s = parseMinute(it.groupValues[1]) ?: return null; val e = parseMinute(it.groupValues[2]) ?: return null; return time(s, e, e < s) }
        return null
    }
    private fun parseMinute(s: String): Int? { val t = s.trim(); if (t == "noon") return 720; if (t == "midnight") return 0; val m = Regex("(\\d{1,2})(?::(\\d{2}))? ?(am|pm)?").matchEntire(t) ?: return null; var h = m.groupValues[1].toInt(); val min = m.groupValues[2].ifBlank { "0" }.toInt(); val ap = m.groupValues[3]; if (min !in 0..59 || h !in 0..23) return null; if (ap == "am" && h == 12) h = 0; else if (ap == "pm" && h < 12) h += 12; return h * 60 + min }
    private fun time(s: Int, e: Int, wrap: Boolean = false) = TemporalQueryWindow(TemporalResolutionStatus.RESOLVED, startMinuteInclusive = s, endMinuteInclusive = e, wrapsMidnight = wrap)
    private fun range(scope: TemporalDateScope, s: Calendar?, e: Calendar?) = TemporalQueryWindow(TemporalResolutionStatus.RESOLVED, scope, s?.let { fmt(it) }, e?.let { fmt(it) })
    private fun fmt(c: Calendar) = out.format(c.time)
    private fun strip(c: Calendar) = (c.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
    private fun add(c: Calendar, field: Int, amount: Int) = (c.clone() as Calendar).apply { add(field, amount) }
    private fun dayIndex(c: Calendar) = if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7 else c.get(Calendar.DAY_OF_WEEK) - 1
}
