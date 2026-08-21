package com.example.myapplication.ai.temporal

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class TemporalActionPolicyTest {
    private val resolver = TemporalExpressionResolver()
    private fun base() = Calendar.getInstance().apply { set(2026, Calendar.JULY, 16, 12, 0, 0); set(Calendar.MILLISECOND, 0) }
    private fun r(date: String? = null, time: String? = null) = resolver.resolve(date, time, "", base())

    @Test fun createExactTomorrowAt9Ready() { assertTrue(TemporalActionPolicy.evaluate(r("tomorrow", "9 am"), TemporalUseCase.CREATE, base()) is TemporalPolicyResult.Ready) }
    @Test fun createNextWeekNeedsDate() { assertTrue(TemporalActionPolicy.evaluate(r("next week", "9 am"), TemporalUseCase.CREATE, base()) is TemporalPolicyResult.NeedsExactDate) }
    @Test fun createMorningNeedsTime() { assertTrue(TemporalActionPolicy.evaluate(r("tomorrow", "morning"), TemporalUseCase.CREATE, base()) is TemporalPolicyResult.NeedsExactTime) }
    @Test fun createNextWeekMorningNeedsBoth() { assertTrue(TemporalActionPolicy.evaluate(r("next week", "morning"), TemporalUseCase.CREATE, base()) is TemporalPolicyResult.NeedsExactDateAndTime) }
    @Test fun clarificationValidatedAgainstWindow() { val w = r("next week", "morning"); assertFalse(TemporalActionPolicy.validateClarification(w, "16/07/2026", 9*60)); assertTrue(TemporalActionPolicy.validateClarification(w, "20/07/2026", 9*60)) }
    @Test fun rescheduleExactNextMondayReady() { assertTrue(TemporalActionPolicy.evaluate(r("next monday", "10 am"), TemporalUseCase.RESCHEDULE, base()) is TemporalPolicyResult.Ready) }
    @Test fun rescheduleNextWeekNeedsDate() { assertTrue(TemporalActionPolicy.evaluate(r("next week", "10 am"), TemporalUseCase.RESCHEDULE, base()) is TemporalPolicyResult.NeedsExactDate) }
    @Test fun createPartialExactSchedulesNeedMissingCounterpart() {
        assertTrue(TemporalActionPolicy.evaluate(r("tomorrow", null), TemporalUseCase.CREATE, base()) is TemporalPolicyResult.NeedsExactTime)
        assertTrue(TemporalActionPolicy.evaluate(r(null, "9 am"), TemporalUseCase.CREATE, base()) is TemporalPolicyResult.NeedsExactDate)
    }

    @Test fun reschedulePartialExactSchedulesAreReadyWithExistingCounterpart() {
        assertTrue(TemporalActionPolicy.evaluate(r("tomorrow", null), TemporalUseCase.RESCHEDULE, base()) is TemporalPolicyResult.Ready)
        assertTrue(TemporalActionPolicy.evaluate(r(null, "9 am"), TemporalUseCase.RESCHEDULE, base()) is TemporalPolicyResult.Ready)
    }

    @Test fun rescheduleRangesPreserveExistingCounterpartsWhenOnlyOneSideFlexible() {
        assertTrue(TemporalActionPolicy.evaluate(r("next week", "9 am"), TemporalUseCase.RESCHEDULE, base()) is TemporalPolicyResult.NeedsExactDate)
        assertTrue(TemporalActionPolicy.evaluate(r("tomorrow", "morning"), TemporalUseCase.RESCHEDULE, base()) is TemporalPolicyResult.NeedsExactTime)
        assertTrue(TemporalActionPolicy.evaluate(r("next week", "morning"), TemporalUseCase.RESCHEDULE, base()) is TemporalPolicyResult.NeedsExactDateAndTime)
    }

    @Test fun tonightStrictSameDateWindow() { val w = r("tonight", null); assertEquals("16/07/2026", w.startDateInclusive); assertEquals("16/07/2026", w.endDateInclusive); assertEquals(1260, w.startMinuteInclusive); assertEquals(299, w.endMinuteInclusive); assertTrue(w.wrapsMidnight) }

    @Test
    fun pastExactScheduleIsRejectedForCreationButAllowedForExistingTaskMutations() {
        val past = r("today", "9 am")

        assertTrue(
            TemporalActionPolicy.evaluate(past, TemporalUseCase.CREATE, base()) is
                TemporalPolicyResult.InvalidPastSchedule
        )
        assertTrue(
            TemporalActionPolicy.evaluate(past, TemporalUseCase.BREAKDOWN, base()) is
                TemporalPolicyResult.InvalidPastSchedule
        )
        assertTrue(
            TemporalActionPolicy.evaluate(past, TemporalUseCase.UPDATE, base()) is
                TemporalPolicyResult.Ready
        )
        assertTrue(
            TemporalActionPolicy.evaluate(past, TemporalUseCase.RESCHEDULE, base()) is
                TemporalPolicyResult.Ready
        )
    }
}
