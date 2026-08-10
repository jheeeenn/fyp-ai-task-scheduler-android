package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextFocus
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute

object ContextActionRepairPolicy {
    private const val PRIMARY_SOURCE = "conversation_agent"

    fun shouldAttempt(
        normalizedText: String,
        primaryDecision: ConversationDecision,
        capturedSnapshot: ReadOnlyTaskContextSnapshot,
        isResultInteraction: Boolean,
        contextFocus: ConversationContextFocus?
    ): Boolean {
        if (!isResultInteraction ||
            capturedSnapshot.items.isEmpty() ||
            primaryDecision.source != PRIMARY_SOURCE ||
            primaryDecision.route !in setOf(
                ConversationRoute.TASK_COMMAND,
                ConversationRoute.ASK_CLARIFICATION,
                ConversationRoute.UNKNOWN
            ) ||
            !ContextReferenceMutationGuard.containsMutationWording(normalizedText)
        ) {
            return false
        }

        val explicit = ContextReferenceMutationGuard.explicitSuppliedRefs(
            normalizedText,
            capturedSnapshot
        ).isNotEmpty()
        val uniqueTitle = ContextReferenceMutationGuard.containsUniqueSuppliedTitle(
            normalizedText,
            capturedSnapshot
        )
        val validFocus = contextFocus?.available == true &&
            contextFocus.generation == capturedSnapshot.generation &&
            capturedSnapshot.items.any { it.ref.equals(contextFocus.ref, ignoreCase = true) } &&
            (ContextReferenceMutationGuard.hasFocusReference(normalizedText) ||
                ContextFocusActionEllipsisPolicy.isBoundedActionOnly(normalizedText))
        return explicit || uniqueTitle || validFocus
    }
}
