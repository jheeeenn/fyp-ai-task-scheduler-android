package com.example.myapplication.ai.temporal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class EditTemporalCommandPolicyTest {
    @Test
    fun exactDateTimeAndCombinedCommandsResolveBoundedly() {
        val date = resolve("change the date to tomorrow")
        val time = resolve("change the time to 3 pm")
        val preciseTime = resolve("set the time to 10:30 am")
        val both = resolve("move it to friday at 10 am")

        assertReady(date, date = "28/07/2026", minute = null)
        assertEquals(EditTemporalTarget.DATE, date.target)
        assertReady(time, date = null, minute = 15 * 60)
        assertEquals(EditTemporalTarget.TIME, time.target)
        assertReady(preciseTime, date = null, minute = 10 * 60 + 30)
        assertReady(both, date = "31/07/2026", minute = 10 * 60)
        assertEquals(EditTemporalTarget.DATE_OR_TIME, both.target)
    }

    @Test
    fun setMoveAndRescheduleGrammarUseTheSameTemporalResolver() {
        val examples = listOf(
            "set the date to next friday",
            "move the date to the day after tomorrow",
            "reschedule it to tomorrow",
            "reschedule it to tomorrow at 2 pm"
        )

        examples.forEach { command ->
            assertEquals(
                command,
                EditTemporalCommandDisposition.READY,
                resolve(command).disposition
            )
        }
    }

    @Test
    fun broadPeriodsRequestClarificationAndIncompleteRelativePhraseIsUnresolved() {
        val range = resolve("set the date to next week")
        val incomplete = resolve("reschedule it to 2 days after")

        assertEquals(
            EditTemporalCommandDisposition.NEEDS_CLARIFICATION,
            range.disposition
        )
        assertTrue(range.policy is TemporalPolicyResult.NeedsExactDate)
        assertEquals(EditTemporalCommandDisposition.UNRESOLVED, incomplete.disposition)
        assertFalse(incomplete.temporal.isExactDate)
    }

    @Test
    fun deliberatePastScheduleIsReadyForExistingTaskEdit() {
        val result = resolve("reschedule it to 26/07/2026 at 4 pm")

        assertReady(result, date = "26/07/2026", minute = 16 * 60)
        assertTrue(result.policy is TemporalPolicyResult.Ready)
    }

    @Test
    fun legacyFieldSelectionAndUnrelatedCommandsAreNotIntercepted() {
        listOf(
            "date",
            "change date",
            "time",
            "change time",
            "change title",
            "delete task"
        ).forEach { command ->
            assertEquals(
                command,
                EditTemporalCommandDisposition.NOT_APPLICABLE,
                resolve(command).disposition
            )
        }
    }

    private fun assertReady(
        result: EditTemporalCommandResolution,
        date: String?,
        minute: Int?
    ) {
        assertEquals(EditTemporalCommandDisposition.READY, result.disposition)
        assertEquals(date, result.temporal.startDateInclusive)
        assertEquals(minute, result.temporal.startMinuteInclusive)
    }

    private fun resolve(text: String) = EditTemporalCommandPolicy.resolve(
        normalizedText = text,
        baseCalendar = base()
    )

    private fun base() = Calendar.getInstance().apply {
        set(2026, Calendar.JULY, 27, 10, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
