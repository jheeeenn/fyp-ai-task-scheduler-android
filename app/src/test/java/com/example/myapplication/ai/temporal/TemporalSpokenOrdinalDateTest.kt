package com.example.myapplication.ai.temporal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TemporalSpokenOrdinalDateTest {
    private val resolver = TemporalExpressionResolver()
    private fun base() = Calendar.getInstance().apply {
        set(2026, Calendar.JULY, 1, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }

    @Test
    fun numericOrdinalCalendarDatesResolveExactly() {
        mapOf(
            "1st August 2026" to "01/08/2026",
            "2nd August 2026" to "02/08/2026",
            "3rd August 2026" to "03/08/2026",
            "4th August 2026" to "04/08/2026",
            "21st August 2026" to "21/08/2026",
            "22nd August 2026" to "22/08/2026",
            "23rd August 2026" to "23/08/2026",
            "31st August 2026" to "31/08/2026",
            "1st of August 2026" to "01/08/2026",
            "the 1st of August 2026" to "01/08/2026",
            "August 1st 2026" to "01/08/2026",
            "August 1st, 2026" to "01/08/2026"
        ).forEach { (phrase, expected) ->
            assertExact(phrase, expected)
        }
    }

    @Test
    fun wordOrdinalCalendarDatesFromFirstThroughThirtyFirstResolveExactly() {
        val words = listOf(
            "first", "second", "third", "fourth", "fifth", "sixth", "seventh",
            "eighth", "ninth", "tenth", "eleventh", "twelfth", "thirteenth",
            "fourteenth", "fifteenth", "sixteenth", "seventeenth", "eighteenth",
            "nineteenth", "twentieth", "twenty-first", "twenty-second",
            "twenty-third", "twenty-fourth", "twenty-fifth", "twenty-sixth",
            "twenty-seventh", "twenty-eighth", "twenty-ninth", "thirtieth",
            "thirty-first"
        )
        words.forEachIndexed { index, word ->
            assertExact(
                "$word of August 2026",
                "%02d/08/2026".format(index + 1)
            )
        }
        assertExact("the fourth of August 2026", "04/08/2026")
        assertExact("August twenty-first 2026", "21/08/2026")
    }

    @Test
    fun monthFirstOrdinalsAcceptTheOnlyInsideTheDateStructure() {
        mapOf(
            "August the first" to "01/08/2026",
            "August the first 2026" to "01/08/2026",
            "August the 1st" to "01/08/2026",
            "August the 1st 2026" to "01/08/2026",
            "August the twenty-first 2026" to "21/08/2026",
            "August first 2026" to "01/08/2026",
            "August 1st 2026" to "01/08/2026",
            "first of August 2026" to "01/08/2026",
            "the first of August 2026" to "01/08/2026"
        ).forEach { (phrase, expected) ->
            assertExact(phrase, expected)
        }
    }

    @Test
    fun invalidOrIncompleteOrdinalStructuresRemainUnresolved() {
        listOf(
            "August 2026",
            "off of August 2026",
            "31st February 2026",
            "February the thirty-first 2026",
            "February the 31st 2026",
            "August the 0th 2026",
            "August the 32nd 2026",
            "0th August 2026",
            "32nd August 2026"
        ).forEach { phrase ->
            val result = resolver.resolve(phrase, null, phrase, base())
            assertFalse("phrase=$phrase", result.isExactDate)
            assertEquals(
                "phrase=$phrase",
                TemporalResolutionType.UNRESOLVED,
                result.type
            )
        }
    }

    @Test
    fun supportedOrdinalInsideConversationalFillerNeedsNoSemanticCanonicalisation() {
        val result = resolver.resolve(
            null,
            null,
            "use the first of August 2026",
            base()
        )
        assertTrue(result.isExactDate)
        assertEquals("01/08/2026", result.startDateInclusive)
    }

    private fun assertExact(phrase: String, expected: String) {
        val result = resolver.resolve(phrase, null, phrase, base())
        assertTrue("phrase=$phrase result=$result", result.isExactDate)
        assertEquals("phrase=$phrase", expected, result.startDateInclusive)
    }
}
