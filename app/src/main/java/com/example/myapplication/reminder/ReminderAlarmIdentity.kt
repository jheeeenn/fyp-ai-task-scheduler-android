package com.example.myapplication.reminder

data class ReminderAlarmIdentity(
    val action: String,
    val dataUri: String
) {
    companion object {
        const val ACTION = "com.example.myapplication.action.TASK_REMINDER_ESCALATION"
        const val EXTRA_TASK_ID = "reminder_task_id"
        const val EXTRA_STAGE = "reminder_escalation_stage"
        const val EXTRA_EXPECTED_DUE_EPOCH = "reminder_expected_due_epoch"
        const val EXTRA_VALIDATED_TASK_TITLE = "validated_reminder_task_title"

        fun forTaskStage(
            taskId: Long,
            stage: ReminderEscalationStage
        ): ReminderAlarmIdentity = ReminderAlarmIdentity(
            action = ACTION,
            dataUri = "task-reminder://task/$taskId/stage/${stage.name}"
        )

        fun notificationId(taskId: Long): Int =
            (taskId xor (taskId ushr Int.SIZE_BITS)).toInt()
    }
}
