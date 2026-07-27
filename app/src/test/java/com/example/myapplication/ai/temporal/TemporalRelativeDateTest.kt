package com.example.myapplication.ai.temporal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TemporalRelativeDateTest {
    private val resolver = TemporalExpressionResolver()

    @Test
    fun supportedRelativeDayExpressionsResolveFromSuppliedBaseCalendar() {
        val examples = linkedMapOf(
            "2 days after today" to "29/07/2026",
            "in 2 days" to "29/07/2026",
            "2 days from now" to "29/07/2026",
            "in 1 day" to "28/07/2026",
            "three days after today" to "30/07/2026"
        )

        examples.forEach { (text, expectedDate) ->
            val resolution = resolver.resolve(text, null, text, base())

            assertTrue(text, resolution.isExactDate)
            assertEquals(text, expectedDate, resolution.startDateInclusive)
            assertEquals(expectedDate, resolution.endDateInclusive)
        }
    }

    @Test
    fun incompleteAfterPhraseDoesNotInferTodayAsAnchor() {
        val resolution = resolver.resolve(
            agentDateText = "2 days after",
            agentTimeText = null,
            originalText = "reschedule it to 2 days after",
            baseCalendar = base()
        )

        assertEquals(TemporalResolutionType.UNRESOLVED, resolution.type)
        assertFalse(resolution.isExactDate)
        assertEquals(null, resolution.startDateInclusive)
    }

    private fun base() = Calendar.getInstance().apply {
        set(2026, Calendar.JULY, 27, 10, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
