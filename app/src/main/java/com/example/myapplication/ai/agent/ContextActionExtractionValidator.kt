package com.example.myapplication.ai.agent

import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.conversation.ConversationContextAction

class ContextActionExtractionValidator {
    fun validate(
        response: ContextActionExtractionResponse,
        expectedAction: ConversationContextAction
    ): ContextActionChangeSet {
        val actualAction = when (response.action.trim().uppercase()) {
            AiIntent.UPDATE_TASK.name -> ConversationContextAction.UPDATE
            AiIntent.RESCHEDULE_TASK.name -> ConversationContextAction.RESCHEDULE
            else -> fail("Unsupported context-action extraction action")
        }
        if (actualAction != expectedAction || expectedAction == ConversationContextAction.NONE) {
            fail("Context-action extraction action does not match expected action")
        }
        if (!response.confidence.isFinite() ||
            response.confidence < MIN_CONFIDENCE ||
            response.confidence > 1.0
        ) {
            fail("Context-action extraction confidence is below $MIN_CONFIDENCE")
        }
        if (response.needClarification) {
            fail("Context-action extraction requested clarification")
        }

        val replacementTitle = response.replacementTitle.clean().takeIf { it.isNotEmpty() }
        if (actualAction == ConversationContextAction.RESCHEDULE && replacementTitle != null) {
            fail("RESCHEDULE context action must not return a replacement title")
        }
        return ContextActionChangeSet(
            action = actualAction,
            replacementTitle = replacementTitle,
            newDateText = response.newDate.clean().takeIf { it.isNotEmpty() },
            newTimeText = response.newTime.clean().takeIf { it.isNotEmpty() },
            confidence = response.confidence.toFloat()
        )
    }

    private fun String.clean(): String = trim().replace(Regex("\\s+"), " ")

    private fun fail(message: String): Nothing = throw TaskAgentValidationException(message)

    companion object {
        const val MIN_CONFIDENCE = 0.60
    }
}
