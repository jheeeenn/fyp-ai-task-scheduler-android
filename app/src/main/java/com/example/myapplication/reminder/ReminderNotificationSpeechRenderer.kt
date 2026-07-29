package com.example.myapplication.reminder

data class ReminderDeliveryWording(
    val notificationTitle: String,
    val notificationMessage: String,
    val spokenText: String
)

object ReminderNotificationSpeechRenderer {
    fun render(
        taskTitle: String,
        stage: ReminderEscalationStage
    ): ReminderDeliveryWording = when (stage) {
        ReminderEscalationStage.DUE -> ReminderDeliveryWording(
            notificationTitle = "Reminder: $taskTitle",
            notificationMessage = "Your task is due now.",
            spokenText = "Task reminder. $taskTitle is due now."
        )

        ReminderEscalationStage.FOLLOW_UP -> ReminderDeliveryWording(
            notificationTitle = "Follow-up reminder: $taskTitle",
            notificationMessage = "This task is still incomplete.",
            spokenText = "Follow-up reminder. $taskTitle is still incomplete."
        )

        ReminderEscalationStage.FINAL -> ReminderDeliveryWording(
            notificationTitle = "Final reminder: $taskTitle",
            notificationMessage = "This task is still incomplete. This is the final reminder.",
            spokenText = "Final reminder. $taskTitle is still incomplete."
        )
    }
}
