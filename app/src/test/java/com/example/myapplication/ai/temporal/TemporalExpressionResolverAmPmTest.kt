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
    fun approximateModifiersResolveOnlyWhenFollowedByACompleteClock() {
        val cases = mapOf(
            "around 8:00 am" to 480,
            "about 9 am" to 540,
            "roughly 10:30 pm" to 1350,
            "approximately 20:00" to 1200
        )

        cases.forEach { (phrase, expectedMinute) ->
            val resolution = resolver.resolve(null, phrase, phrase)
            assertEquals("phrase=$phrase", TemporalResolutionType.EXACT_TIME, resolution.type)
            assertEquals("phrase=$phrase", expectedMinute, resolution.startMinuteInclusive)
            assertEquals("phrase=$phrase", expectedMinute, resolution.endMinuteInclusive)
        }

        val unresolved = resolver.resolve(null, "around later", "around later")
        assertEquals(TemporalResolutionType.UNRESOLVED, unresolved.type)
    }

    @Test
    fun approximateExactTimesStillObeyMorningConstraint() {
        val morning = resolver.resolve(null, "morning", "morning")
        val morningCandidate = resolver.resolve(null, "around 8 AM", "around 8 AM")
        val eveningCandidate = resolver.resolve(null, "around 9:30 PM", "around 9:30 PM")

        assertTrue(morningCandidate.isExactTime)
        assertEquals(8 * 60, morningCandidate.startMinuteInclusive)
        assertTrue(
            TemporalActionPolicy.validateClarification(
                morning,
                exactDate = null,
                exactMinute = morningCandidate.startMinuteInclusive
            )
        )

        assertTrue(eveningCandidate.isExactTime)
        assertEquals(21 * 60 + 30, eveningCandidate.startMinuteInclusive)
        assertFalse(
            TemporalActionPolicy.validateClarification(
                morning,
                exactDate = null,
                exactMinute = eveningCandidate.startMinuteInclusive
            )
        )
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
