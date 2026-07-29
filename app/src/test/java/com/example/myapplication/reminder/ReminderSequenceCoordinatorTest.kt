package com.example.myapplication.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSequenceCoordinatorTest {
    @Test
    fun completeSequenceSchedulesAllThreeStages() {
        val scheduled = mutableListOf<ReminderAlarmSpec>()
        val cancelled = mutableListOf<ReminderEscalationStage>()

        val result = ReminderSequenceCoordinator.scheduleCompleteSequence(
            taskId = 42L,
            originalDueEpochMillis = 10_000L,
            scheduler = ReminderStageScheduler { scheduled += it },
            canceller = ReminderStageCanceller { _, stage -> cancelled += stage }
        )

        assertTrue(result)
        assertEquals(ReminderEscalationPolicy.orderedStages, scheduled.map { it.stage })
        assertEquals(ReminderEscalationPolicy.orderedStages, cancelled)
        assertTrue(scheduled.all { it.taskId == 42L })
        assertTrue(scheduled.all { it.expectedOriginalDueEpochMillis == 10_000L })
    }

    @Test
    fun schedulingFailureCleansUpEveryStageAndLeavesNoPartialSuccess() {
        val scheduled = mutableListOf<ReminderEscalationStage>()
        val cancelled = mutableListOf<ReminderEscalationStage>()

        val result = ReminderSequenceCoordinator.scheduleCompleteSequence(
            taskId = 42L,
            originalDueEpochMillis = 10_000L,
            scheduler = ReminderStageScheduler {
                scheduled += it.stage
                if (it.stage == ReminderEscalationStage.FOLLOW_UP) {
                    throw SecurityException("exact alarm denied")
                }
            },
            canceller = ReminderStageCanceller { _, stage -> cancelled += stage }
        )

        assertFalse(result)
        assertEquals(
            listOf(
                ReminderEscalationStage.DUE,
                ReminderEscalationStage.FOLLOW_UP
            ),
            scheduled
        )
        assertEquals(
            ReminderEscalationPolicy.orderedStages +
                ReminderEscalationPolicy.orderedStages,
            cancelled
        )
    }

    @Test
    fun cancellationVisitsAllThreeStagesEvenWhenOneFails() {
        val cancellationAttempts = mutableListOf<ReminderEscalationStage>()

        val result = ReminderSequenceCoordinator.cancelAllStages(
            taskId = Long.MAX_VALUE,
            canceller = ReminderStageCanceller { _, stage ->
                cancellationAttempts += stage
                if (stage == ReminderEscalationStage.FOLLOW_UP) {
                    throw IllegalStateException("test failure")
                }
            }
        )

        assertFalse(result)
        assertEquals(ReminderEscalationPolicy.orderedStages, cancellationAttempts)
    }
}
