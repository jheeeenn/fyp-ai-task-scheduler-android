package com.example.myapplication.ai.temporal

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TemporalExpressionResolver {
    private val out = SimpleDateFormat("dd/MM/yyyy", Locale.UK).apply { isLenient = false }
    private val monthNames = "jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december"
    private val weekdays = mapOf(
        "sunday" to Calendar.SUNDAY, "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY, "thursday" to Calendar.THURSDAY, "friday" to Calendar.FRIDAY, "saturday" to Calendar.SATURDAY
    )

    fun resolve(agentDateText: String?, agentTimeText: String?, originalText: String, baseCalendar: Calendar = Calendar.getInstance()): TemporalResolution {
        val dateSource = clean(agentDateText)
        val timeSource = clean(agentTimeText)
        val original = clean(originalText)
        val supplied = dateSource.isNotBlank() || timeSource.isNotBlank()
        val dateSourceIsTimeOnly = dateSource.isNotBlank() && dateSource != "tonight" && parseTimeWindow(dateSource) != null
        val dateText = if (dateSourceIsTimeOnly) "" else dateSource.ifBlank { extractDatePhrase(original) }
        val timeText = timeSource.ifBlank {
            if (dateSourceIsTimeOnly) dateSource else extractTimePhrase(dateSource).ifBlank { extractTimePhrase(original) }
        }
        if (!supplied && dateText.isBlank() && timeText.isBlank()) return TemporalResolution(TemporalResolutionType.NONE)
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
            return TemporalResolution(TemporalResolutionType.UNRESOLVED, spokenLabel = listOf(dateText, timeText).filter { it.isNotBlank() }.joinToString(" "))
        }
        if (dateWindow == null && timeWindow == null) return if (supplied) TemporalResolution(TemporalResolutionType.UNRESOLVED) else TemporalResolution(TemporalResolutionType.NONE)
        return merge(
            dateWindow?.copy(originalDatePhrase = effectiveDateText),
            timeWindow?.copy(originalTimePhrase = timeText),
            listOf(effectiveDateText, timeText).filter { it.isNotBlank() }.distinct().joinToString(" ")
        )
    }

    private fun clean(s: String?) = s.orEmpty().lowercase(Locale.UK).replace(",", " ").replace(Regex("\\s+"), " ").trim()
    private fun merge(d: TemporalResolution?, t: TemporalResolution?, label: String) = TemporalResolution(
        type = when { d != null && t != null && d.isExactDate && t.isExactTime -> TemporalResolutionType.EXACT_DATE_TIME
            d != null && t != null -> TemporalResolutionType.DATE_TIME_WINDOW
            d != null -> d.type
            t != null -> t.type
            else -> TemporalResolutionType.NONE
        },
        dateScope = d?.dateScope ?: TemporalDateScope.ALL,
        startDateInclusive = d?.startDateInclusive,
        endDateInclusive = d?.endDateInclusive,
        startMinuteInclusive = t?.startMinuteInclusive,
        endMinuteInclusive = t?.endMinuteInclusive,
        wrapsMidnight = t?.wrapsMidnight ?: false,
        spokenLabel = label,
        originalDatePhrase = d?.originalDatePhrase.orEmpty(),
        originalTimePhrase = t?.originalTimePhrase.orEmpty()
    )

    private fun extractDatePhrase(text: String): String {
        Regex("""\b(overdue|upcoming)(?: tasks)?\b""").find(text)?.let { return it.value.trim() }
        Regex("""\bfrom (.+?) onward\b""").find(text)?.let { match ->
            if (!looksLikeClockExpression(match.groupValues[1])) return match.value.trim()
        }
        Regex("""\b(?:from|between) (.+?) (?:to|and) (.+?)(?= before | after | in the | at |$)""").findAll(text).forEach { match ->
            val start = match.groupValues[1].trim()
            val end = match.groupValues[2].trim()
            if (!looksLikeClockExpression(start) || !looksLikeClockExpression(end)) {
                return match.value.trim()
            }
        }
        Regex("""\b(?:this |next )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday) (?:to|and) (?:this |next )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""").find(text)?.let { return it.value.trim() }
        Regex("""\b(?:this|next) (?:weekend|week|month|year)\b""").find(text)?.let { return it.value.trim() }
        Regex("""\bnext \d+ (?:days|weeks)\b""").find(text)?.let { return it.value.trim() }
        Regex("""\b(?:before|after) (.+?)(?= before | after | in the | at |$)""").findAll(text).forEach { match ->
            val target = match.groupValues[1].trim()
            if (!looksLikeClockExpression(target) && looksLikeDateExpression(target)) {
                return match.value.trim()
            }
        }
        return listOf(
            Regex("""\bday after tomorrow\b"""),
            Regex("""\b(?:today|tomorrow|tonight)\b"""),
            Regex("""\b(?:this |next )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b"""),
            Regex("""\b\d{1,2}/\d{1,2}/\d{2,4}\b"""),
            Regex("""\b\d{4}/\d{1,2}/\d{1,2}\b"""),
            Regex("""\b\d{1,2} (?:$monthNames)(?: \d{4})?\b"""),
            Regex("""\b(?:$monthNames) \d{1,2}(?: \d{4})?\b""")
        ).firstNotNullOfOrNull { it.find(text)?.value?.trim() }.orEmpty()
    }

    private fun looksLikeDateExpression(text: String): Boolean {
        val t = text.trim()
        if (t in setOf("today", "tomorrow", "tonight", "day after tomorrow", "the day after tomorrow")) return true
        if (Regex("""^(?:this |next )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)$""").matches(t)) return true
        if (Regex("""^\d{1,2}/\d{1,2}/\d{2,4}$""").matches(t) || Regex("""^\d{4}/\d{1,2}/\d{1,2}$""").matches(t)) return true
        if (Regex("""^\d{1,2} (?:$monthNames)(?: \d{4})?$""").matches(t)) return true
        if (Regex("""^(?:$monthNames) \d{1,2}(?: \d{4})?$""").matches(t)) return true
        return false
    }

    private fun looksLikeClockExpression(text: String): Boolean {
        val t = text.trim().removePrefix("at ").removePrefix("before ").removePrefix("after ")
        if (t in setOf("morning", "afternoon", "evening", "night", "tonight", "noon", "midnight")) return true
        return parseMinute(t) != null
    }

    private fun extractTimePhrase(text: String): String = listOf(
        Regex("\\bbetween .+? and .+?(?:am|pm|noon|midnight)\\b"), Regex("\\bfrom .+? to .+?(?:am|pm|noon|midnight)\\b"),
        Regex("\\b(?:before|after|at) (?:\\d{1,2}(?::\\d{2})? ?(?:am|pm)?|noon|midnight)\\b"), Regex("\\bin the (morning|afternoon|evening)\\b"),
        Regex("\\b(morning|afternoon|evening|night|tonight)\\b"), Regex("\\b(noon|midnight)\\b")
    ).firstNotNullOfOrNull { it.find(text)?.value?.removePrefix("in the ")?.trim() }.orEmpty()

    private fun parseDateWindow(text: String, base: Calendar): TemporalResolution? {
        val t = text.removePrefix("tasks ").trim()
        if (t.startsWith("overdue")) return TemporalResolution(TemporalResolutionType.OVERDUE, TemporalDateScope.OVERDUE, endDateInclusive = fmt(add(base, Calendar.DAY_OF_MONTH, -1)))
        if (t.startsWith("upcoming")) return TemporalResolution(TemporalResolutionType.UPCOMING, TemporalDateScope.UPCOMING, startDateInclusive = fmt(base))
        Regex("from (.+) onward").matchEntire(t)?.let { val s = parseSingleDate(it.groupValues[1], base) ?: return null; return range(TemporalDateScope.UPCOMING, s, null) }
        Regex("before (.+)").matchEntire(t)?.let { val e = parseSingleDate(it.groupValues[1], base) ?: return null; e.add(Calendar.DAY_OF_MONTH, -1); return range(TemporalDateScope.DATE_RANGE, null, e) }
        Regex("after (.+)").matchEntire(t)?.let { val s = parseSingleDate(it.groupValues[1], base) ?: return null; s.add(Calendar.DAY_OF_MONTH, 1); return range(TemporalDateScope.DATE_RANGE, s, null) }
        Regex("(?:(?:from|between) )?(.+) (?:to|and) (.+)").matchEntire(t)?.let {
            val startText = it.groupValues[1].trim()
            val endText = it.groupValues[2].trim()
            val s = parseSingleDate(startText, base) ?: return null
            val e = parseSingleDate(endText, base) ?: return null
            if (strip(e).before(strip(s))) {
                if (isWeekdayExpression(startText) && isWeekdayExpression(endText)) {
                    do {
                        e.add(Calendar.DAY_OF_MONTH, 7)
                    } while (strip(e).before(strip(s)))
                } else {
                    return null
                }
            }
            return range(TemporalDateScope.DATE_RANGE, s, e)
        }
        parseCalendarRange(t, base)?.let { return it }
        val single = parseSingleDate(t, base) ?: return null
        return range(TemporalDateScope.EXACT_DATE, single, single)
    }

    private fun parseCalendarRange(t: String, base: Calendar): TemporalResolution? {
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

    private fun parseTimeWindow(text: String): TemporalResolution? {
        val t = text.trim()
        when (t) { "morning" -> return time(300, 719); "afternoon" -> return time(720, 1019); "evening" -> return time(1020, 1259); "night", "tonight" -> return time(1260, 299, true); "noon" -> return time(720,720); "midnight" -> return time(0,0) }
        Regex("at (.+)").matchEntire(t)?.let { val m = parseMinute(it.groupValues[1]) ?: return null; return time(m, m) }
        Regex("before (.+)").matchEntire(t)?.let { val m = parseMinute(it.groupValues[1]) ?: return null; return time(0, (m - 1).coerceAtLeast(0)) }
        Regex("after (.+)").matchEntire(t)?.let { val m = parseMinute(it.groupValues[1]) ?: return null; return time((m + 1).coerceAtMost(1439), 1439) }
        val rangeText = t
            .removePrefix("between ")
            .removePrefix("from ")
            .replace('–', '-')
        Regex("""(.+?)\s*(?:and|to|until|through|-)\s*(.+)""").matchEntire(rangeText)?.let {
            val s = parseMinute(it.groupValues[1]) ?: return null
            val e = parseMinute(it.groupValues[2]) ?: return null
            return time(s, e, e < s)
        }
        Regex("""(.+?)-(.*)""").matchEntire(rangeText)?.let {
            val s = parseMinute(it.groupValues[1]) ?: return null
            val e = parseMinute(it.groupValues[2]) ?: return null
            return time(s, e, e < s)
        }
        parseMinute(t)?.let { minute ->
            return time(minute, minute)
        }
        return null
    }
    private fun isWeekdayExpression(text: String): Boolean {
        val t = text.trim().removePrefix("on ").removePrefix("this ").removePrefix("next ")
        return weekdays.containsKey(t)
    }
    private fun parseMinute(s: String): Int? { val t = s.trim(); if (t == "noon") return 720; if (t == "midnight") return 0; val m = Regex("(\\d{1,2})(?::(\\d{2}))? ?(am|pm)?").matchEntire(t) ?: return null; var h = m.groupValues[1].toInt(); val min = m.groupValues[2].ifBlank { "0" }.toInt(); val ap = m.groupValues[3]; if (min !in 0..59) return null; if (ap.isNotBlank() && h !in 1..12) return null; if (ap.isBlank() && h !in 0..23) return null; if (ap == "am" && h == 12) h = 0; else if (ap == "pm" && h < 12) h += 12; return h * 60 + min }
    private fun time(s: Int, e: Int, wrap: Boolean = false) = TemporalResolution(if (s == e) TemporalResolutionType.EXACT_TIME else TemporalResolutionType.TIME_RANGE, startMinuteInclusive = s, endMinuteInclusive = e, wrapsMidnight = wrap)
    private fun range(scope: TemporalDateScope, s: Calendar?, e: Calendar?) = TemporalResolution(if (scope == TemporalDateScope.EXACT_DATE) TemporalResolutionType.EXACT_DATE else TemporalResolutionType.DATE_RANGE, scope, s?.let { fmt(it) }, e?.let { fmt(it) })
    private fun fmt(c: Calendar) = out.format(c.time)
    private fun strip(c: Calendar) = (c.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
    private fun add(c: Calendar, field: Int, amount: Int) = (c.clone() as Calendar).apply { add(field, amount) }
    private fun dayIndex(c: Calendar) = if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7 else c.get(Calendar.DAY_OF_WEEK) - 1
}
