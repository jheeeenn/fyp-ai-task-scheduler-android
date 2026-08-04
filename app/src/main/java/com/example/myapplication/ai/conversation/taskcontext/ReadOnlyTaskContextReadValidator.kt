package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute

enum class ContextReadValidationResult {
    VALID,
    INVALID_ROUTE,
    LOW_CONFIDENCE,
    NO_CAPTURED_ITEMS,
    STALE_GENERATION,
    UNKNOWN_REF,
    INVALID_DETAIL,
    INCOMPATIBLE_DETAIL
}

data class ValidatedContextRead(
    val result: ContextReadValidationResult,
    val item: ReadOnlyTaskContextItem? = null,
    val detail: ConversationContextDetail = ConversationContextDetail.NONE
) {
    val isValid: Boolean
        get() = result == ContextReadValidationResult.VALID && item != null
}

object ReadOnlyTaskContextReadValidator {
    const val MIN_CONFIDENCE = 0.80

    fun validate(
        decision: ConversationDecision,
        capturedSnapshot: ReadOnlyTaskContextSnapshot,
        currentGeneration: Long,
        normalizedText: String = ""
    ): ValidatedContextRead {
        if (decision.route != ConversationRoute.CONTEXT_READ) {
            return ValidatedContextRead(ContextReadValidationResult.INVALID_ROUTE)
        }
        if (decision.confidence < MIN_CONFIDENCE) {
            return ValidatedContextRead(ContextReadValidationResult.LOW_CONFIDENCE)
        }
        if (capturedSnapshot.items.isEmpty()) {
            return ValidatedContextRead(ContextReadValidationResult.NO_CAPTURED_ITEMS)
        }
        if (currentGeneration != capturedSnapshot.generation) {
            return ValidatedContextRead(ContextReadValidationResult.STALE_GENERATION)
        }
        if (decision.contextDetail == ConversationContextDetail.NONE) {
            return ValidatedContextRead(ContextReadValidationResult.INVALID_DETAIL)
        }
        if (!ContextReadDetailCompatibilityPolicy.isCompatible(normalizedText, decision.contextDetail)) {
            return ValidatedContextRead(ContextReadValidationResult.INCOMPATIBLE_DETAIL)
        }

        val item = capturedSnapshot.items.firstOrNull {
            it.ref.equals(decision.contextRef.trim(), ignoreCase = true)
        } ?: return ValidatedContextRead(ContextReadValidationResult.UNKNOWN_REF)

        return ValidatedContextRead(
            result = ContextReadValidationResult.VALID,
            item = item,
            detail = decision.contextDetail
        )
    }
}
