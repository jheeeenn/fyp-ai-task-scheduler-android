package com.example.myapplication.ai.agent

import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.AiParsedCommand
import com.example.myapplication.ai.conversation.ConversationContextAction

class ContextActionTaskValidator {
    fun validate(
        command: AiParsedCommand,
        expectedAction: ConversationContextAction
    ): AiParsedCommand {
        val expectedIntent = when (expectedAction) {
            ConversationContextAction.UPDATE -> AiIntent.UPDATE_TASK.name
            ConversationContextAction.RESCHEDULE -> AiIntent.RESCHEDULE_TASK.name
            ConversationContextAction.NONE -> fail("Context action NONE is not executable")
        }
        if (command.intent != expectedIntent) {
            fail("Task-agent action ${command.intent} does not match $expectedIntent")
        }
        if (!command.confidence.isFinite() || command.confidence < MIN_CONFIDENCE) {
            fail("Task-agent confidence ${command.confidence} is below $MIN_CONFIDENCE")
        }
        if (!command.targetTaskTitle.isNullOrBlank() ||
            !command.targetDateText.isNullOrBlank() ||
            !command.targetTimeText.isNullOrBlank()
        ) {
            fail("Context action must not identify or select a target")
        }
        if (expectedAction == ConversationContextAction.RESCHEDULE &&
            !command.taskTitle.isNullOrBlank()
        ) {
            fail("RESCHEDULE context action must not return a replacement title")
        }
        if (!command.recurrence.isNullOrBlank() ||
            !command.priority.isNullOrBlank() ||
            command.plan.isNotEmpty() ||
            command.needsClarification
        ) {
            fail("Context action returned unsupported fields")
        }
        return command.copy(naturalResponse = null)
    }

    private fun fail(message: String): Nothing = throw TaskAgentValidationException(message)

    companion object {
        const val MIN_CONFIDENCE = 0.60f
    }
}
