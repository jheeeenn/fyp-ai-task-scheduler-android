package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextFocus
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute

object ContextActionRepairPolicy {
    private const val PRIMARY_SOURCE = "conversation_agent"
    private const val SCHEMA_REPAIR_SOURCE = "conversation_agent_schema_repair"

    fun shouldAttempt(
        normalizedText: String,
        primaryDecision: ConversationDecision,
        capturedSnapshot: ReadOnlyTaskContextSnapshot,
        isResultInteraction: Boolean,
        contextFocus: ConversationContextFocus?,
        currentGeneration: Long = capturedSnapshot.generation
    ): Boolean {
        if (!isResultInteraction ||
            capturedSnapshot.items.isEmpty() ||
            capturedSnapshot.truncated ||
            capturedSnapshot.generation != currentGeneration ||
            primaryDecision.source !in setOf(PRIMARY_SOURCE, SCHEMA_REPAIR_SOURCE) ||
            primaryDecision.route !in setOf(
                ConversationRoute.TASK_COMMAND,
                ConversationRoute.ASK_CLARIFICATION,
                ConversationRoute.UNKNOWN
            ) ||
            !ContextReferenceMutationGuard.containsMutationWording(normalizedText)
        ) {
            return false
        }

        val explicitTargetGrounding = ContextActionReferenceGroundingValidator.validate(
            normalizedText = normalizedText,
            decision = primaryDecision.copy(
                route = ConversationRoute.CONTEXT_ACTION,
                contextRef = ""
            ),
            capturedSnapshot = capturedSnapshot,
            currentFocus = contextFocus
        )
        val hasStrongExplicitTarget = explicitTargetGrounding.result in setOf(
            ContextActionReferenceGroundingResult.VALID_EXPLICIT_REF,
            ContextActionReferenceGroundingResult.VALID_ORDINAL,
            ContextActionReferenceGroundingResult.VALID_UNIQUE_TITLE
        )
        val hasMultipleExplicitSelectors =
            ContextReferenceMutationGuard.explicitContextSelectorCount(normalizedText) > 1
        if (primaryDecision.source == SCHEMA_REPAIR_SOURCE &&
            (!hasStrongExplicitTarget || hasMultipleExplicitSelectors)
        ) {
            return false
        }
        val validFocus = contextFocus?.available == true &&
            contextFocus.generation == capturedSnapshot.generation &&
            capturedSnapshot.items.count {
                it.ref.equals(contextFocus.ref, ignoreCase = true)
            } == 1 &&
            (ContextReferenceMutationGuard.hasFocusReference(normalizedText) ||
                ContextFocusActionEllipsisPolicy.isBoundedActionOnly(normalizedText))
        val validTaskDetailImplicitFocus = strictTaskDetailImplicitFocusRef(
            capturedSnapshot,
            contextFocus
        ) != null
        val validSingleResultImplicitFocus = strictSingleResultImplicitFocusRef(
            capturedSnapshot,
            contextFocus
        ) != null
        return hasStrongExplicitTarget || validFocus || validTaskDetailImplicitFocus ||
            validSingleResultImplicitFocus
    }
}
