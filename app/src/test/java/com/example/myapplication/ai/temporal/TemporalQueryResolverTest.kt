package com.example.myapplication.ai.temporal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TemporalQueryResolverTest {
    private val resolver = TemporalQueryResolver()
    private fun base() = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { set(2026, Calendar.JULY, 14, 9, 0, 0); set(Calendar.MILLISECOND, 0) }
    private fun base15() = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { set(2026, Calendar.JULY, 15, 9, 0, 0); set(Calendar.MILLISECOND, 0) }
    private fun r(date: String? = null, time: String? = null, original: String = "") = resolver.resolve(date, time, original, base())

    @Test fun exactDatesAndWeekdays() {
        assertRange(r("today"), "14/07/2026", "14/07/2026")
        assertRange(r("tomorrow"), "15/07/2026", "15/07/2026")
        assertRange(r("day after tomorrow"), "16/07/2026", "16/07/2026")
        assertRange(r("Monday"), "20/07/2026", "20/07/2026")
        assertRange(r("this Monday"), "13/07/2026", "13/07/2026")
        assertRange(r("next Monday"), "20/07/2026", "20/07/2026")
    }

    @Test fun calendarRanges() {
        assertRange(r("this week"), "13/07/2026", "19/07/2026")
        assertRange(r("next week"), "20/07/2026", "26/07/2026")
        assertRange(r("this weekend"), "18/07/2026", "19/07/2026")
        assertRange(r("next weekend"), "25/07/2026", "26/07/2026")
        assertRange(r("this month"), "01/07/2026", "31/07/2026")
        assertRange(r("next month"), "01/08/2026", "31/08/2026")
        assertRange(r("next 7 days"), "14/07/2026", "20/07/2026")
    }

    @Test fun explicitAndOpenRanges() {
        assertRange(r("from 20 July to 25 July"), "20/07/2026", "25/07/2026")
        assertRange(r("between Monday and Friday"), "20/07/2026", "24/07/2026")
        val overdue = r("overdue")
        assertEquals(TemporalDateScope.OVERDUE, overdue.dateScope)
        assertEquals("13/07/2026", overdue.endDateInclusive)
        val upcoming = r("upcoming")
        assertEquals(TemporalDateScope.UPCOMING, upcoming.dateScope)
        assertEquals("14/07/2026", upcoming.startDateInclusive)
    }

    @Test fun timeWindows() {
        assertTime(r(time = "morning"), 300, 719, false)
        assertTime(r(time = "afternoon"), 720, 1019, false)
        assertTime(r(time = "evening"), 1020, 1259, false)
        assertTime(r(time = "night"), 1260, 299, true)
        assertTime(r(time = "before 10 AM"), 0, 599, false)
        assertTime(r(time = "after 6 PM"), 1081, 1439, false)
        assertTime(r(time = "between 9 AM and noon"), 540, 720, false)
        listOf(
            "between 9 AM and noon",
            "from 9 AM to noon",
            "9 AM to noon",
            "9 AM until noon",
            "9 AM through noon",
            "9 AM - noon",
            "9 AM–noon"
        ).forEach { phrase ->
            assertTime(r(time = phrase), 540, 720, false)
        }
    }

    @Test fun combinedAndStatus() {
        val tomorrowMorning = r(original = "what tasks do i have tomorrow morning")
        assertRange(tomorrowMorning, "15/07/2026", "15/07/2026")
        assertTime(tomorrowMorning, 300, 719, false)
        val nextWeekMorning = r("next week in the morning", "")
        assertRange(nextWeekMorning, "20/07/2026", "26/07/2026")
        assertTime(nextWeekMorning, 300, 719, false)
        assertEquals(TemporalResolutionStatus.UNRESOLVED, r("somedayish").status)
        assertEquals(TemporalResolutionStatus.NONE, r(original = "what tasks do i have").status)
    }


    @Test fun timeOnlyOriginalTextDoesNotBecomeDateConstraint() {
        val cases = listOf(
            Triple("between 9 AM and noon", 540, 720),
            Triple("from 2 PM to 5 PM", 840, 1020),
            Triple("before 10 AM", 0, 599),
            Triple("after 6 PM", 1081, 1439),
            Triple("at 9 AM", 540, 540),
            Triple("morning", 300, 719)
        )

        cases.forEach { (phrase, start, end) ->
            val window = resolver.resolve(
                agentDateText = "",
                agentTimeText = phrase,
                originalText = "what tasks do i have $phrase",
                baseCalendar = base()
            )
            assertEquals("phrase=$phrase", TemporalResolutionStatus.RESOLVED, window.status)
            assertEquals("phrase=$phrase", TemporalDateScope.ALL, window.dateScope)
            assertEquals("phrase=$phrase", null, window.startDateInclusive)
            assertEquals("phrase=$phrase", null, window.endDateInclusive)
            assertEquals("phrase=$phrase", start, window.startMinuteInclusive)
            assertEquals("phrase=$phrase", end, window.endMinuteInclusive)
        }

        val defensiveDateFieldWindow = resolver.resolve(
            agentDateText = "between 9 AM and noon",
            agentTimeText = "",
            originalText = "what tasks do i have between 9 AM and noon",
            baseCalendar = base()
        )
        assertEquals(TemporalResolutionStatus.RESOLVED, defensiveDateFieldWindow.status)
        assertEquals(TemporalDateScope.ALL, defensiveDateFieldWindow.dateScope)
        assertEquals(540, defensiveDateFieldWindow.startMinuteInclusive)
        assertEquals(720, defensiveDateFieldWindow.endMinuteInclusive)
    }


    @Test fun reversedExplicitCalendarRangesAreUnresolvedButWeekdayRangesCanRollForward() {
        assertRange(r("Friday to Monday"), "17/07/2026", "20/07/2026")
        assertRange(r("between Monday and Friday"), "20/07/2026", "24/07/2026")
        assertEquals(TemporalResolutionStatus.UNRESOLVED, r("between 20 August and 31 July").status)
        assertEquals(TemporalResolutionStatus.UNRESOLVED, r("from 25/08/2026 to 20/08/2026").status)
        assertRange(r("from 20 August 2026 to 31 July 2027"), "20/08/2026", "31/07/2027")
    }

    @Test fun tonightKeepsStrictSameDateWrappingWindow() {
        val window = resolver.resolve("tonight", "", "what tasks do i have tonight", base15())
        assertEquals(TemporalResolutionStatus.RESOLVED, window.status)
        assertEquals(TemporalDateScope.EXACT_DATE, window.dateScope)
        assertEquals("15/07/2026", window.startDateInclusive)
        assertEquals("15/07/2026", window.endDateInclusive)
        assertEquals(1260, window.startMinuteInclusive)
        assertEquals(299, window.endMinuteInclusive)
        assertTrue(window.wrapsMidnight)
    }

    @Test fun temporalLabelFormatterKeepsReadableRangeLabels() {
        val nextWeek = r("next week")
        assertEquals("next week", TemporalQueryLabelFormatter.spokenLabel(nextWeek))
        val dateRange = r("between 31 July and 20 August")
        assertEquals("between 31 July and 20 August", TemporalQueryLabelFormatter.spokenLabel(dateRange))
        assertEquals("this morning", TemporalQueryLabelFormatter.spokenLabel(resolver.resolve("today", "morning", "", base15())))
        assertEquals("this afternoon", TemporalQueryLabelFormatter.spokenLabel(resolver.resolve("today", "afternoon", "", base15())))
        assertEquals("this evening", TemporalQueryLabelFormatter.spokenLabel(resolver.resolve("today", "evening", "", base15())))
        assertEquals("tonight", TemporalQueryLabelFormatter.spokenLabel(resolver.resolve("tonight", "", "", base15())))
        assertEquals("today after 6 PM", TemporalQueryLabelFormatter.spokenLabel(resolver.resolve("today", "after 6 PM", "", base15())))
    }

    private fun assertRange(w: TemporalQueryWindow, start: String, end: String) { assertEquals(TemporalResolutionStatus.RESOLVED, w.status); assertEquals(start, w.startDateInclusive); assertEquals(end, w.endDateInclusive) }
    private fun assertTime(w: TemporalQueryWindow, start: Int, end: Int, wraps: Boolean) { assertEquals(TemporalResolutionStatus.RESOLVED, w.status); assertEquals(start, w.startMinuteInclusive); assertEquals(end, w.endMinuteInclusive); assertEquals(wraps, w.wrapsMidnight) }
}
