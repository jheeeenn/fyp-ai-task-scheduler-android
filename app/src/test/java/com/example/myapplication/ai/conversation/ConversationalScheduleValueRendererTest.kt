package com.example.myapplication.ai.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ConversationalScheduleValueRendererTest {
    @Test
    fun authoritativeDatesUseDeterministicRelativeWeekLabels() {
        val base = baseCalendar()

        assertEquals("today", renderDate("08/09/2026", base))
        assertEquals("tomorrow", renderDate("09/09/2026", base))
        assertEquals("this Thursday", renderDate("10/09/2026", base))
        assertEquals("next Thursday", renderDate("17/09/2026", base))
        assertEquals("Thursday, 24 September", renderDate("24/09/2026", base))
    }

    @Test
    fun absoluteDatesOmitOnlyTheCurrentYear() {
        val base = baseCalendar()

        val sameYear = renderDate("24/09/2026", base)
        val differentYear = renderDate("24/09/2027", base)

        assertEquals("Thursday, 24 September", sameYear)
        assertFalse(sameYear.contains("2026"))
        assertEquals("Friday, 24 September 2027", differentYear)
    }

    @Test
    fun exactTimesUseNaturalClockSpeechWithoutBecomingVague() {
        mapOf(
            "7:00 AM" to "7 AM",
            "8:00 PM" to "8 PM",
            "7:30 AM" to "7:30 AM",
            "12:00 PM" to "noon",
            "12:00 AM" to "midnight"
        ).forEach { (authoritative, expected) ->
            assertEquals(expected, ConversationalScheduleValueRenderer.time(authoritative))
        }
    }

    @Test
    fun malformedValuesFallBackWithoutChangingInputsOrBaseCalendar() {
        val base = baseCalendar()
        val originalMillis = base.timeInMillis
        val authoritativeDate = "not-a-date"
        val authoritativeTime = "not-a-time"

        assertEquals(authoritativeDate, renderDate(authoritativeDate, base))
        assertEquals(authoritativeTime, ConversationalScheduleValueRenderer.time(authoritativeTime))
        assertEquals(originalMillis, base.timeInMillis)
    }

    private fun renderDate(value: String, base: Calendar): String =
        ConversationalScheduleValueRenderer.date(value, base)

    private fun baseCalendar(): Calendar = Calendar.getInstance(
        TimeZone.getTimeZone("Asia/Kuala_Lumpur")
    ).apply {
        set(2026, Calendar.SEPTEMBER, 8, 10, 30, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
