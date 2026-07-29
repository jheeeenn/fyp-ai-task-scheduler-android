package com.example.myapplication.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderNotificationSpeechRendererTest {
    @Test
    fun wordingIsExactForEveryStage() {
        assertEquals(
            ReminderDeliveryWording(
                notificationTitle = "Reminder: Submit report",
                notificationMessage = "Your task is due now.",
                spokenText = "Task reminder. Submit report is due now."
            ),
            ReminderNotificationSpeechRenderer.render(
                "Submit report",
                ReminderEscalationStage.DUE
            )
        )
        assertEquals(
            ReminderDeliveryWording(
                notificationTitle = "Follow-up reminder: Submit report",
                notificationMessage = "This task is still incomplete.",
                spokenText = "Follow-up reminder. Submit report is still incomplete."
            ),
            ReminderNotificationSpeechRenderer.render(
                "Submit report",
                ReminderEscalationStage.FOLLOW_UP
            )
        )
        assertEquals(
            ReminderDeliveryWording(
                notificationTitle = "Final reminder: Submit report",
                notificationMessage =
                    "This task is still incomplete. This is the final reminder.",
                spokenText = "Final reminder. Submit report is still incomplete."
            ),
            ReminderNotificationSpeechRenderer.render(
                "Submit report",
                ReminderEscalationStage.FINAL
            )
        )
    }
}
