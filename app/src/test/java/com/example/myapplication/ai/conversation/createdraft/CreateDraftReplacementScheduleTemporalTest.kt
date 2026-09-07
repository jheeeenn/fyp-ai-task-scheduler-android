package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.ai.temporal.PendingTemporalClarification
import com.example.myapplication.ai.temporal.TemporalActionPolicy
import com.example.myapplication.ai.temporal.TemporalExpressionResolver
import com.example.myapplication.ai.temporal.TemporalPolicyResult
import com.example.myapplication.ai.temporal.TemporalUseCase
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateDraftReplacementScheduleTemporalTest {
    private val resolver = TemporalExpressionResolver()

    @Test
    fun exactDateAndBroadTimeRetainOnlyTheExactDateCandidate() {
        val resolution = resolver.resolve("Sunday", "evening", "Sunday evening", base())
        val policy = TemporalActionPolicy.evaluate(resolution, TemporalUseCase.CREATE, base())

        assertTrue(policy is TemporalPolicyResult.NeedsExactTime)
        val pending = PendingTemporalClarification(
            original = resolution,
            exactDate = resolution.startDateInclusive,
            needsExactDate = false,
            needsExactTime = true,
            replacingOriginalConstraint = true
        )
        assertTrue(pending.exactDate?.isNotBlank() == true)
        assertNull(pending.exactMinute)
        assertFalse(pending.isComplete)
        assertTrue(TemporalActionPolicy.validateClarification(resolution, null, 20 * 60))
        assertTrue(TemporalActionPolicy.validateClarification(resolution, null, 21 * 60))
        assertFalse(TemporalActionPolicy.validateClarification(resolution, null, 16 * 60))
    }

    @Test
    fun observedFridayEveningCorrectionRequiresExactTimeWithoutReusingOldTime() {
        val resolution = resolver.resolve("Friday", "evening", "Friday evening", base())
        val policy = TemporalActionPolicy.evaluate(resolution, TemporalUseCase.CREATE, base())

        assertTrue(policy is TemporalPolicyResult.NeedsExactTime)
        assertTrue(resolution.isExactDate)
        assertFalse(resolution.isExactTime)
        assertEquals("friday", resolution.originalDatePhrase)
        assertEquals("evening", resolution.originalTimePhrase)
        assertTrue(TemporalActionPolicy.validateClarification(resolution, null, 21 * 60))
    }

    @Test
    fun broadDateAndExactTimeRetainOnlyTheExactTimeCandidate() {
        val resolution = resolver.resolve("next week", "9 PM", "next week at 9 PM", base())
        val policy = TemporalActionPolicy.evaluate(resolution, TemporalUseCase.CREATE, base())

        assertTrue(policy is TemporalPolicyResult.NeedsExactDate)
        val pending = PendingTemporalClarification(
            original = resolution,
            exactMinute = resolution.startMinuteInclusive,
            needsExactDate = true,
            needsExactTime = false,
            replacingOriginalConstraint = true
        )
        assertEquals(21 * 60, pending.exactMinute)
        assertNull(pending.exactDate)
        assertFalse(pending.isComplete)
        assertTrue(TemporalActionPolicy.validateClarification(resolution, "16/09/2026", null))
        assertFalse(TemporalActionPolicy.validateClarification(resolution, "21/09/2026", null))
    }

    @Test
    fun completedReplacementBecomesAnExactAndroidValidatedSchedule() {
        val original = resolver.resolve("next week", "9 PM", "next week at 9 PM", base())
        val completed = PendingTemporalClarification(
            original = original,
            exactDate = "16/09/2026",
            exactMinute = 21 * 60,
            needsExactDate = true,
            needsExactTime = false,
            replacingOriginalConstraint = true
        )
        assertTrue(completed.isComplete)
        assertTrue(
            TemporalActionPolicy.validateClarification(
                completed.original,
                completed.exactDate,
                completed.exactMinute
            )
        )

        val finalResolution = resolver.resolve(
            completed.exactDate,
            "9 PM",
            "${completed.exactDate} 9 PM",
            base()
        )
        assertTrue(
            TemporalActionPolicy.evaluate(finalResolution, TemporalUseCase.CREATE, base()) is
                TemporalPolicyResult.Ready
        )
    }

    private fun base(): Calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur")).apply {
        set(2026, Calendar.SEPTEMBER, 7, 10, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
