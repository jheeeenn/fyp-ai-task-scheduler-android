package com.example.myapplication.ai.temporal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RelativeTemporalChangeCalculatorTest {
    private val calculator = RelativeTemporalChangeCalculator()

    @Test fun plusThirtyMinutes() = assertSchedule("31/07/2026", "10:00 AM", offset(30), "31/07/2026", "10:30 AM")
    @Test fun minusThirtyMinutes() = assertSchedule("31/07/2026", "10:00 AM", offset(-30), "31/07/2026", "9:30 AM")
    @Test fun plusOneHour() = assertSchedule("31/07/2026", "10:00 AM", offset(60), "31/07/2026", "11:00 AM")
    @Test fun minusOneHour() = assertSchedule("31/07/2026", "10:00 AM", offset(-60), "31/07/2026", "9:00 AM")

    @Test
    fun crossesMidnightForward() {
        val result = success("31/07/2026", "11:45 PM", offset(30))
        assertEquals(ExactTemporalSchedule("01/08/2026", "12:15 AM"), result.schedule)
        assertTrue(result.crossedDateBoundary)
    }

    @Test
    fun crossesMidnightBackward() {
        val result = success("01/08/2026", "12:15 AM", offset(-30))
        assertEquals(ExactTemporalSchedule("31/07/2026", "11:45 PM"), result.schedule)
        assertTrue(result.crossedDateBoundary)
    }

    @Test fun crossesMonthBoundary() = assertSchedule("30/04/2027", "11:45 PM", offset(30), "01/05/2027", "12:15 AM")
    @Test fun crossesYearBoundary() = assertSchedule("31/12/2026", "11:45 PM", offset(30), "01/01/2027", "12:15 AM")
    @Test fun calculatesLeapDay() = assertSchedule("28/02/2028", "9:00 AM", dateOffset(1), "29/02/2028", "9:00 AM")
    @Test fun dateOffsetKeepsTime() = assertSchedule("31/07/2026", "9:00 AM", dateOffset(2), "02/08/2026", "9:00 AM")
    @Test fun exactTimeKeepsDate() = assertSchedule("31/07/2026", "8:00 AM", setTime("9 AM"), "31/07/2026", "9:00 AM")
    @Test fun exactDateKeepsTime() = assertSchedule("31/07/2026", "8:00 AM", setDate("2 August 2026"), "02/08/2026", "8:00 AM")

    @Test
    fun timeOffsetRejectsMissingOriginalDate() {
        assertFailure(null, "9:00 AM", offset(30), RelativeTemporalCalculationFailure.MISSING_ORIGINAL_DATE)
    }

    @Test
    fun timeOffsetRejectsMissingOriginalTime() {
        assertFailure("31/07/2026", null, offset(30), RelativeTemporalCalculationFailure.MISSING_ORIGINAL_TIME)
    }

    @Test
    fun dateOffsetRejectsMissingOriginalDate() {
        assertFailure(null, "9:00 AM", dateOffset(1), RelativeTemporalCalculationFailure.MISSING_ORIGINAL_DATE)
    }

    @Test
    fun calculatedPastResultIsAllowedForExistingTaskReschedule() {
        val result = calculator.calculate(
            authoritativeOriginal = ExactTemporalSchedule("31/07/2026", "1:00 PM"),
            currentProposal = null,
            proposal = offset(-120),
            now = now(hour = 12)
        )
        assertTrue(result is RelativeTemporalCalculationResult.Success)
        assertEquals(
            ExactTemporalSchedule("31/07/2026", "11:00 AM"),
            (result as RelativeTemporalCalculationResult.Success).schedule
        )
    }

    @Test
    fun zeroAndOutOfBoundsOffsetsAreRejected() {
        assertThrows(RelativeTemporalProposalValidationException::class.java) {
            calculator.calculate(
                ExactTemporalSchedule("31/07/2026", "9:00 AM"),
                null,
                offset(0),
                now()
            )
        }
        listOf(-10_081, 10_081).forEach { minutes ->
            assertThrows(RelativeTemporalProposalValidationException::class.java) {
                calculator.calculate(
                    ExactTemporalSchedule("31/07/2026", "9:00 AM"),
                    null,
                    offset(minutes),
                    now()
                )
            }
        }
    }

    @Test
    fun insteadReplacementUsesAuthoritativeBaseAndDoesNotAccumulate() {
        val result = calculator.calculate(
            authoritativeOriginal = ExactTemporalSchedule("31/07/2026", "9:00 AM"),
            currentProposal = ExactTemporalSchedule("31/07/2026", "9:30 AM"),
            proposal = offset(60, RelativeTemporalBase.AUTHORITATIVE_TASK),
            now = now()
        ) as RelativeTemporalCalculationResult.Success
        assertEquals("10:00 AM", result.schedule.time)
    }

    @Test
    fun anotherThirtyMinutesUsesCurrentProposalAndAccumulates() {
        val result = calculator.calculate(
            authoritativeOriginal = ExactTemporalSchedule("31/07/2026", "9:00 AM"),
            currentProposal = ExactTemporalSchedule("31/07/2026", "9:30 AM"),
            proposal = offset(30, RelativeTemporalBase.CURRENT_PROPOSAL),
            now = now()
        ) as RelativeTemporalCalculationResult.Success
        assertEquals("10:00 AM", result.schedule.time)
    }

    @Test
    fun dateOffsetFromAuthoritativeBasePreservesCurrentProposedTime() {
        val result = calculator.calculate(
            authoritativeOriginal = ExactTemporalSchedule("21/08/2026", "6:00 PM"),
            currentProposal = ExactTemporalSchedule("21/08/2026", "6:00 AM"),
            proposal = dateOffset(1, RelativeTemporalBase.AUTHORITATIVE_TASK),
            now = augustNow()
        ) as RelativeTemporalCalculationResult.Success

        assertEquals(ExactTemporalSchedule("22/08/2026", "6:00 AM"), result.schedule)
    }

    @Test
    fun dateSetPreservesCurrentProposedTime() {
        val result = calculator.calculate(
            authoritativeOriginal = ExactTemporalSchedule("21/08/2026", "6:00 PM"),
            currentProposal = ExactTemporalSchedule("21/08/2026", "6:00 AM"),
            proposal = setDate("tomorrow", RelativeTemporalBase.AUTHORITATIVE_TASK),
            now = augustNow()
        ) as RelativeTemporalCalculationResult.Success

        assertEquals(ExactTemporalSchedule("22/08/2026", "6:00 AM"), result.schedule)
    }

    @Test
    fun timeSetPreservesCurrentProposedDate() {
        val result = calculator.calculate(
            authoritativeOriginal = ExactTemporalSchedule("21/08/2026", "6:00 PM"),
            currentProposal = ExactTemporalSchedule("22/08/2026", "6:00 PM"),
            proposal = setTime("5 PM", RelativeTemporalBase.AUTHORITATIVE_TASK),
            now = augustNow()
        ) as RelativeTemporalCalculationResult.Success

        assertEquals(ExactTemporalSchedule("22/08/2026", "5:00 PM"), result.schedule)
    }

    @Test
    fun noEffectAndBoundaryComparisonUseTheVisibleCurrentProposal() {
        val noEffect = calculator.calculate(
            authoritativeOriginal = ExactTemporalSchedule("21/08/2026", "6:00 PM"),
            currentProposal = ExactTemporalSchedule("22/08/2026", "6:00 AM"),
            proposal = setDate("22 August 2026", RelativeTemporalBase.AUTHORITATIVE_TASK),
            now = augustNow()
        )
        assertEquals(
            RelativeTemporalCalculationFailure.NO_EFFECTIVE_CHANGE,
            (noEffect as RelativeTemporalCalculationResult.Failure).reason
        )

        val changedTime = calculator.calculate(
            authoritativeOriginal = ExactTemporalSchedule("21/08/2026", "6:00 PM"),
            currentProposal = ExactTemporalSchedule("22/08/2026", "6:00 AM"),
            proposal = setTime("7 AM", RelativeTemporalBase.AUTHORITATIVE_TASK),
            now = augustNow()
        ) as RelativeTemporalCalculationResult.Success
        assertFalse(changedTime.crossedDateBoundary)
    }

    @Test
    fun dateOnlySchedulesRemainAllowedForExactDateChanges() {
        val result = success("31/07/2026", null, setDate("2 August 2026"))
        assertEquals(ExactTemporalSchedule("02/08/2026", null), result.schedule)
        assertFalse(result.schedule.time != null)
    }

    private fun assertSchedule(
        date: String?,
        time: String?,
        proposal: RelativeTemporalProposal,
        expectedDate: String?,
        expectedTime: String?
    ) {
        assertEquals(ExactTemporalSchedule(expectedDate, expectedTime), success(date, time, proposal).schedule)
    }

    private fun success(
        date: String?,
        time: String?,
        proposal: RelativeTemporalProposal
    ): RelativeTemporalCalculationResult.Success = calculator.calculate(
        ExactTemporalSchedule(date, time),
        null,
        proposal,
        now()
    ) as RelativeTemporalCalculationResult.Success

    private fun assertFailure(
        date: String?,
        time: String?,
        proposal: RelativeTemporalProposal,
        expected: RelativeTemporalCalculationFailure
    ) {
        val result = calculator.calculate(ExactTemporalSchedule(date, time), null, proposal, now())
        assertEquals(expected, (result as RelativeTemporalCalculationResult.Failure).reason)
    }

    private fun offset(
        minutes: Int,
        base: RelativeTemporalBase = RelativeTemporalBase.AUTHORITATIVE_TASK
    ) = proposal(
        timeOperation = RelativeTemporalOperation.OFFSET,
        base = base,
        timeOffsetMinutes = minutes
    )

    private fun dateOffset(
        days: Int,
        base: RelativeTemporalBase = RelativeTemporalBase.AUTHORITATIVE_TASK
    ) = proposal(
        dateOperation = RelativeTemporalOperation.OFFSET,
        base = base,
        dateOffsetDays = days
    )

    private fun setTime(
        text: String,
        base: RelativeTemporalBase = RelativeTemporalBase.AUTHORITATIVE_TASK
    ) = proposal(
        timeOperation = RelativeTemporalOperation.SET,
        base = base,
        replacementTimeText = text
    )

    private fun setDate(
        text: String,
        base: RelativeTemporalBase = RelativeTemporalBase.AUTHORITATIVE_TASK
    ) = proposal(
        dateOperation = RelativeTemporalOperation.SET,
        base = base,
        replacementDateText = text
    )

    private fun proposal(
        dateOperation: RelativeTemporalOperation = RelativeTemporalOperation.KEEP,
        timeOperation: RelativeTemporalOperation = RelativeTemporalOperation.KEEP,
        base: RelativeTemporalBase = RelativeTemporalBase.AUTHORITATIVE_TASK,
        replacementDateText: String = "",
        replacementTimeText: String = "",
        dateOffsetDays: Int = 0,
        timeOffsetMinutes: Int = 0
    ) = RelativeTemporalProposal(
        dateOperation,
        timeOperation,
        base,
        replacementDateText,
        replacementTimeText,
        dateOffsetDays,
        timeOffsetMinutes,
        0.98,
        false
    )

    private fun now(hour: Int = 8): Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(2026, Calendar.JULY, 31, hour, 0, 0)
    }

    private fun augustNow(): Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(2026, Calendar.AUGUST, 21, 8, 0, 0)
    }
}
