package com.example.myapplication.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReminderEscalationPolicyTest {
    @Test
    fun policyContainsExactlyThreeOrderedStagesWithRequiredOffsets() {
        assertEquals(
            listOf(
                ReminderEscalationStage.DUE,
                ReminderEscalationStage.FOLLOW_UP,
                ReminderEscalationStage.FINAL
            ),
            ReminderEscalationPolicy.orderedStages
        )
        assertEquals(3, ReminderEscalationPolicy.orderedStages.size)
        assertEquals(
            listOf(0L, 5L, 15L),
            ReminderEscalationPolicy.orderedStages.map { it.offsetMinutes }
        )

        val originalDue = 1_000_000L
        assertEquals(
            listOf(
                originalDue,
                originalDue + TimeUnit.MINUTES.toMillis(5),
                originalDue + TimeUnit.MINUTES.toMillis(15)
            ),
            ReminderEscalationPolicy.orderedStages.map {
                ReminderEscalationPolicy.triggerEpochMillis(originalDue, it)
            }
        )
    }

    @Test
    fun alarmIdentityDiffersAcrossStagesAndTasks() {
        val dueForTaskOne = ReminderAlarmIdentity.forTaskStage(
            1L,
            ReminderEscalationStage.DUE
        )
        val followUpForTaskOne = ReminderAlarmIdentity.forTaskStage(
            1L,
            ReminderEscalationStage.FOLLOW_UP
        )
        val dueForTaskTwo = ReminderAlarmIdentity.forTaskStage(
            2L,
            ReminderEscalationStage.DUE
        )

        assertNotEquals(dueForTaskOne, followUpForTaskOne)
        assertNotEquals(dueForTaskOne, dueForTaskTwo)
        assertEquals(
            "task-reminder://task/1/stage/DUE",
            dueForTaskOne.dataUri
        )
        assertEquals(ReminderAlarmIdentity.ACTION, dueForTaskOne.action)
    }
}
