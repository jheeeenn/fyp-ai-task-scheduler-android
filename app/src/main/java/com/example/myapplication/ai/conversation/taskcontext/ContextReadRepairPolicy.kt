package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute

enum class ContextReadRepairDisposition {
    ACCEPTED,
    ABSTAINED,
    REJECTED
}

data class ContextReadRepairEvaluation(
    val disposition: ContextReadRepairDisposition,
    val validation: ValidatedContextRead? = null
)

object ContextReadRepairPolicy {
    private const val PRIMARY_SOURCE = "conversation_agent"

    fun shouldAttempt(
        primaryDecision: ConversationDecision,
        capturedSnapshot: ReadOnlyTaskContextSnapshot,
        isResultInteraction: Boolean,
        normalizedText: String = ""
    ): Boolean = primaryDecision.source == PRIMARY_SOURCE &&
        (primaryDecision.route == ConversationRoute.ASK_CLARIFICATION ||
            primaryDecision.route == ConversationRoute.UNKNOWN) &&
        capturedSnapshot.items.isNotEmpty() &&
        isResultInteraction &&
        (normalizedText.isBlank() || !ContextReferenceMutationGuard.containsMutationWording(normalizedText))

    fun evaluate(
        normalizedText: String,
        repairedDecision: ConversationDecision,
        capturedSnapshot: ReadOnlyTaskContextSnapshot,
        currentGeneration: Long
    ): ContextReadRepairEvaluation {
        if (repairedDecision.route == ConversationRoute.ASK_CLARIFICATION) {
            return ContextReadRepairEvaluation(ContextReadRepairDisposition.ABSTAINED)
        }
        if (repairedDecision.route != ConversationRoute.CONTEXT_READ) {
            return ContextReadRepairEvaluation(ContextReadRepairDisposition.REJECTED)
        }
        if (ContextReferenceMutationGuard.containsMutationWording(normalizedText)) {
            return ContextReadRepairEvaluation(ContextReadRepairDisposition.REJECTED)
        }

        val validation = ReadOnlyTaskContextReadValidator.validate(
            decision = repairedDecision,
            capturedSnapshot = capturedSnapshot,
            currentGeneration = currentGeneration
        )
        return ContextReadRepairEvaluation(
            disposition = if (validation.isValid) {
                ContextReadRepairDisposition.ACCEPTED
            } else {
                ContextReadRepairDisposition.REJECTED
            },
            validation = validation
        )
    }
}
