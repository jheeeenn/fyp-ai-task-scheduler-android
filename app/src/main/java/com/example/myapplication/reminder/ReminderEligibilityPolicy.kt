package com.example.myapplication.reminder

import com.example.myapplication.data.TaskEntity

enum class ReminderSchedulingRejection {
    MISSING_DATE,
    MISSING_TIME,
    TASK_COMPLETED,
    CHILD_TASK,
    INVALID_SCHEDULE,
    DUE_NOT_IN_FUTURE
}

sealed interface ReminderSchedulingEligibility {
    data class Eligible(val originalDueEpochMillis: Long) : ReminderSchedulingEligibility
    data class Rejected(val reason: ReminderSchedulingRejection) : ReminderSchedulingEligibility
}

enum class ReminderSuppressionReason {
    INVALID_PAYLOAD,
    TASK_NOT_FOUND,
    TASK_COMPLETED,
    CHILD_TASK,
    INVALID_CURRENT_SCHEDULE,
    STALE_SCHEDULE,
    UNKNOWN_STAGE
}

sealed interface ReminderDeliveryEligibility {
    data class Eligible(
        val task: TaskEntity,
        val currentDueEpochMillis: Long
    ) : ReminderDeliveryEligibility

    data class Suppressed(
        val reason: ReminderSuppressionReason
    ) : ReminderDeliveryEligibility
}

object ReminderEligibilityPolicy {
    fun evaluateForScheduling(
        task: TaskEntity,
        nowEpochMillis: Long
    ): ReminderSchedulingEligibility {
        if (task.dueDate.isNullOrBlank()) {
            return ReminderSchedulingEligibility.Rejected(ReminderSchedulingRejection.MISSING_DATE)
        }
        if (task.dueTime.isNullOrBlank()) {
            return ReminderSchedulingEligibility.Rejected(ReminderSchedulingRejection.MISSING_TIME)
        }
        if (task.isDone) {
            return ReminderSchedulingEligibility.Rejected(ReminderSchedulingRejection.TASK_COMPLETED)
        }
        if (task.parentTaskId != null) {
            return ReminderSchedulingEligibility.Rejected(ReminderSchedulingRejection.CHILD_TASK)
        }
        val dueEpochMillis = ReminderScheduleParser.parseToEpochMillis(
            task.dueDate,
            task.dueTime
        ) ?: return ReminderSchedulingEligibility.Rejected(
            ReminderSchedulingRejection.INVALID_SCHEDULE
        )
        if (dueEpochMillis <= nowEpochMillis) {
            return ReminderSchedulingEligibility.Rejected(
                ReminderSchedulingRejection.DUE_NOT_IN_FUTURE
            )
        }
        return ReminderSchedulingEligibility.Eligible(dueEpochMillis)
    }

    fun evaluateForDelivery(
        task: TaskEntity?,
        expectedOriginalDueEpochMillis: Long
    ): ReminderDeliveryEligibility {
        if (task == null) {
            return ReminderDeliveryEligibility.Suppressed(
                ReminderSuppressionReason.TASK_NOT_FOUND
            )
        }
        if (task.isDone) {
            return ReminderDeliveryEligibility.Suppressed(
                ReminderSuppressionReason.TASK_COMPLETED
            )
        }
        if (task.parentTaskId != null) {
            return ReminderDeliveryEligibility.Suppressed(
                ReminderSuppressionReason.CHILD_TASK
            )
        }
        val currentDueEpochMillis = ReminderScheduleParser.parseToEpochMillis(
            task.dueDate,
            task.dueTime
        ) ?: return ReminderDeliveryEligibility.Suppressed(
            ReminderSuppressionReason.INVALID_CURRENT_SCHEDULE
        )
        if (currentDueEpochMillis != expectedOriginalDueEpochMillis) {
            return ReminderDeliveryEligibility.Suppressed(
                ReminderSuppressionReason.STALE_SCHEDULE
            )
        }
        return ReminderDeliveryEligibility.Eligible(task, currentDueEpochMillis)
    }
}
