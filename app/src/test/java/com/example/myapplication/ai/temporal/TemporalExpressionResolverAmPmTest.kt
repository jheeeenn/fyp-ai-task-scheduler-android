package com.example.myapplication.ai.temporal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalExpressionResolverAmPmTest {
    private val resolver = TemporalExpressionResolver()

    @Test
    fun punctuatedAndSpacedAmPmFormsResolveAsExactTimes() {
        val cases = mapOf(
            "9:00 a.m." to 540,
            "9:00 a. m." to 540,
            "9 a m" to 540,
            "10:00 p.m." to 1320,
            "10:00 p. m." to 1320,
            "10 p m" to 1320,
            "9:00 am" to 540,
            "10:00 pm" to 1320
        )

        cases.forEach { (phrase, expectedMinute) ->
            val resolution = resolver.resolve(null, phrase, phrase)
            assertEquals("phrase=$phrase", TemporalResolutionType.EXACT_TIME, resolution.type)
            assertEquals("phrase=$phrase", expectedMinute, resolution.startMinuteInclusive)
            assertEquals("phrase=$phrase", expectedMinute, resolution.endMinuteInclusive)
        }
    }

    @Test
    fun punctuatedExactTimeStillObeysMorningConstraint() {
        val morning = resolver.resolve(null, "morning", "morning")
        val candidate = resolver.resolve(null, "9:30 p.m.", "9:30 p.m.")

        assertTrue(candidate.isExactTime)
        assertEquals(21 * 60 + 30, candidate.startMinuteInclusive)
        assertFalse(
            TemporalActionPolicy.validateClarification(
                morning,
                exactDate = null,
                exactMinute = candidate.startMinuteInclusive
            )
        )
    }
}
