package com.example.myapplication.reminder

import java.util.ArrayDeque

data class ReminderSpeechRequest(
    val taskTitle: String,
    val stage: ReminderEscalationStage
) {
    val spokenText: String
        get() = ReminderNotificationSpeechRenderer.render(taskTitle, stage).spokenText

    companion object {
        fun fromPayload(
            taskTitle: String?,
            stageWireValue: String?
        ): ReminderSpeechRequest? {
            val validatedTitle = taskTitle?.takeIf { it.isNotBlank() } ?: return null
            val stage = ReminderEscalationStage.fromWireValue(stageWireValue) ?: return null
            return ReminderSpeechRequest(validatedTitle, stage)
        }
    }
}

class ReminderSpeechQueue(
    private val speaker: (ReminderSpeechRequest, () -> Unit) -> Unit,
    private val onQueueEmpty: () -> Unit
) {
    private val pending = ArrayDeque<ReminderSpeechRequest>()
    private var current: ReminderSpeechRequest? = null

    val isIdle: Boolean
        get() = current == null && pending.isEmpty()

    val pendingCount: Int
        get() = pending.size

    fun enqueuePayload(taskTitle: String?, stageWireValue: String?): Boolean {
        val request = ReminderSpeechRequest.fromPayload(
            taskTitle,
            stageWireValue
        ) ?: return false
        pending.addLast(request)
        startNextIfIdle()
        return true
    }

    private fun startNextIfIdle() {
        if (current != null) return
        val next = pending.pollFirst()
        if (next == null) {
            onQueueEmpty()
            return
        }
        current = next
        var completionAccepted = false
        speaker(next) {
            if (completionAccepted || current !== next) return@speaker
            completionAccepted = true
            current = null
            startNextIfIdle()
        }
    }
}
