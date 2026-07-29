package com.example.myapplication.reminder

data class ReminderAlarmSpec(
    val taskId: Long,
    val stage: ReminderEscalationStage,
    val expectedOriginalDueEpochMillis: Long,
    val triggerEpochMillis: Long
)

fun interface ReminderStageScheduler {
    fun schedule(spec: ReminderAlarmSpec)
}

fun interface ReminderStageCanceller {
    fun cancel(taskId: Long, stage: ReminderEscalationStage)
}

object ReminderSequenceCoordinator {
    fun scheduleCompleteSequence(
        taskId: Long,
        originalDueEpochMillis: Long,
        scheduler: ReminderStageScheduler,
        canceller: ReminderStageCanceller
    ): Boolean {
        if (!cancelAllStages(taskId, canceller)) return false

        return try {
            ReminderEscalationPolicy.orderedStages.forEach { stage ->
                scheduler.schedule(
                    ReminderAlarmSpec(
                        taskId = taskId,
                        stage = stage,
                        expectedOriginalDueEpochMillis = originalDueEpochMillis,
                        triggerEpochMillis = ReminderEscalationPolicy.triggerEpochMillis(
                            originalDueEpochMillis,
                            stage
                        )
                    )
                )
            }
            true
        } catch (_: Exception) {
            cancelAllStages(taskId, canceller)
            false
        }
    }

    fun cancelAllStages(
        taskId: Long,
        canceller: ReminderStageCanceller
    ): Boolean {
        var allCancelled = true
        ReminderEscalationPolicy.orderedStages.forEach { stage ->
            try {
                canceller.cancel(taskId, stage)
            } catch (_: Exception) {
                allCancelled = false
            }
        }
        return allCancelled
    }
}
