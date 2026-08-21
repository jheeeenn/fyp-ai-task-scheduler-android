package com.example.myapplication.reminder

import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderEligibilityPolicyTest {
    private val date = "30/12/2030"
    private val time = "3:00 PM"
    private val dueEpoch = requireNotNull(
        ReminderScheduleParser.parseToEpochMillis(date, time)
    )

    @Test
    fun activeMatchingRootIsEligible() {
        val task = task()

        val scheduling = ReminderEligibilityPolicy.evaluateForScheduling(
            task,
            nowEpochMillis = dueEpoch - 1
        )
        val delivery = ReminderEligibilityPolicy.evaluateForDelivery(task, dueEpoch)

        assertTrue(scheduling is ReminderSchedulingEligibility.Eligible)
        assertTrue(delivery is ReminderDeliveryEligibility.Eligible)
    }

    @Test
    fun activeRootWithPastDueTimeIsExpectedlyIneligibleForNewReminder() {
        val scheduling = ReminderEligibilityPolicy.evaluateForScheduling(
            task(),
            nowEpochMillis = dueEpoch + 1
        )

        assertEquals(
            ReminderSchedulingRejection.DUE_NOT_IN_FUTURE,
            (scheduling as ReminderSchedulingEligibility.Rejected).reason
        )
    }

    @Test
    fun completedTaskIsSuppressed() {
        assertSuppressed(
            task(isDone = true),
            ReminderSuppressionReason.TASK_COMPLETED
        )
    }

    @Test
    fun deletedTaskIsSuppressed() {
        val decision = ReminderEligibilityPolicy.evaluateForDelivery(null, dueEpoch)

        assertEquals(
            ReminderSuppressionReason.TASK_NOT_FOUND,
            (decision as ReminderDeliveryEligibility.Suppressed).reason
        )
    }

    @Test
    fun childTaskIsSuppressed() {
        assertSuppressed(
            task(parentTaskId = 99L),
            ReminderSuppressionReason.CHILD_TASK
        )
    }

    @Test
    fun changedScheduleIsSuppressedAsStale() {
        assertSuppressed(
            task(dueTime = "4:00 PM"),
            ReminderSuppressionReason.STALE_SCHEDULE
        )
    }

    @Test
    fun titleOnlyChangeUsesCurrentTitleAndRemainsEligible() {
        val currentTask = task(title = "Latest Room title")

        val decision = ReminderEligibilityPolicy.evaluateForDelivery(
            currentTask,
            dueEpoch
        )

        val eligible = decision as ReminderDeliveryEligibility.Eligible
        assertEquals("Latest Room title", eligible.task.title)
        assertEquals(dueEpoch, eligible.currentDueEpochMillis)
    }

    @Test
    fun invalidCurrentScheduleIsSuppressed() {
        assertSuppressed(
            task(dueDate = "31/02/2030"),
            ReminderSuppressionReason.INVALID_CURRENT_SCHEDULE
        )
        assertNull(ReminderScheduleParser.parseToEpochMillis(date, "not-a-time"))
    }

    @Test
    fun unknownAndMalformedStagesFailClosed() {
        assertNull(ReminderEscalationStage.fromWireValue(null))
        assertNull(ReminderEscalationStage.fromWireValue(""))
        assertNull(ReminderEscalationStage.fromWireValue("SNOOZE"))
        assertNull(ReminderEscalationStage.fromWireValue("due"))
    }

    private fun assertSuppressed(
        task: TaskEntity,
        expectedReason: ReminderSuppressionReason
    ) {
        val decision = ReminderEligibilityPolicy.evaluateForDelivery(task, dueEpoch)
        assertEquals(
            expectedReason,
            (decision as ReminderDeliveryEligibility.Suppressed).reason
        )
    }

    private fun task(
        title: String = "Task",
        dueDate: String? = date,
        dueTime: String? = time,
        isDone: Boolean = false,
        parentTaskId: Long? = null
    ) = TaskEntity(
        id = 8L,
        title = title,
        dueDate = dueDate,
        dueTime = dueTime,
        isDone = isDone,
        parentTaskId = parentTaskId
    )
}
