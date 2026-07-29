package com.example.myapplication.reminder

import java.util.concurrent.TimeUnit

object ReminderEscalationPolicy {
    val orderedStages: List<ReminderEscalationStage> = listOf(
        ReminderEscalationStage.DUE,
        ReminderEscalationStage.FOLLOW_UP,
        ReminderEscalationStage.FINAL
    )

    fun triggerEpochMillis(
        originalDueEpochMillis: Long,
        stage: ReminderEscalationStage
    ): Long = Math.addExact(
        originalDueEpochMillis,
        TimeUnit.MINUTES.toMillis(stage.offsetMinutes)
    )
}
