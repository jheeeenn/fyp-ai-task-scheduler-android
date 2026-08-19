package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute

enum class ContextActionValidationResult {
    VALID,
    LOW_CONFIDENCE,
    EMPTY_CONTEXT,
    STALE_GENERATION,
    UNKNOWN_REF,
    INVALID_ACTION,
    INVALID_ROUTE_FIELDS
}

data class ValidatedContextAction(
    val result: ContextActionValidationResult,
    val ref: String = "",
    val action: ConversationContextAction = ConversationContextAction.NONE
) {
    val isValid: Boolean
        get() = result == ContextActionValidationResult.VALID
}

object ContextActionDecisionValidator {
    const val MIN_CONFIDENCE = 0.90

    fun validate(
        decision: ConversationDecision,
        capturedSnapshot: ReadOnlyTaskContextSnapshot,
        currentGeneration: Long
    ): ValidatedContextAction {
        if (decision.route != ConversationRoute.CONTEXT_ACTION ||
            decision.taskText.isNotEmpty() ||
            decision.reply.isNotEmpty() ||
            decision.contextDetail != ConversationContextDetail.NONE
        ) {
            return ValidatedContextAction(ContextActionValidationResult.INVALID_ROUTE_FIELDS)
        }
        if (!decision.confidence.isFinite() || decision.confidence < MIN_CONFIDENCE) {
            return ValidatedContextAction(ContextActionValidationResult.LOW_CONFIDENCE)
        }
        if (capturedSnapshot.items.isEmpty()) {
            return ValidatedContextAction(ContextActionValidationResult.EMPTY_CONTEXT)
        }
        if (capturedSnapshot.generation != currentGeneration) {
            return ValidatedContextAction(ContextActionValidationResult.STALE_GENERATION)
        }
        if (decision.contextAction != ConversationContextAction.UPDATE &&
            decision.contextAction != ConversationContextAction.RESCHEDULE &&
            decision.contextAction != ConversationContextAction.DELETE &&
            decision.contextAction != ConversationContextAction.MARK_DONE &&
            decision.contextAction != ConversationContextAction.MARK_UNDONE
        ) {
            return ValidatedContextAction(ContextActionValidationResult.INVALID_ACTION)
        }
        val item = capturedSnapshot.items.firstOrNull {
            it.ref.equals(decision.contextRef.trim(), ignoreCase = true)
        } ?: return ValidatedContextAction(ContextActionValidationResult.UNKNOWN_REF)

        return ValidatedContextAction(
            result = ContextActionValidationResult.VALID,
            ref = item.ref,
            action = decision.contextAction
        )
    }
}
