package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute

enum class ContextReadTitleGroundingResult {
    NOT_APPLICABLE,
    MATCHED_MODEL_REF,
    GROUNDED_BLANK_MODEL_REF,
    MODEL_REF_MISMATCH,
    AMBIGUOUS_TITLE,
    STALE_GENERATION
}

data class ContextReadTitleGrounding(
    val result: ContextReadTitleGroundingResult,
    val decision: ConversationDecision,
    val expectedRef: String = ""
) {
    val isAccepted: Boolean
        get() = result == ContextReadTitleGroundingResult.MATCHED_MODEL_REF ||
            result == ContextReadTitleGroundingResult.GROUNDED_BLANK_MODEL_REF
}

/** Android-owned title evidence for read-only selection from the current supplied list. */
object ContextReadTitleGroundingPolicy {
    fun reconcile(
        normalizedText: String,
        decision: ConversationDecision,
        capturedSnapshot: ReadOnlyTaskContextSnapshot,
        currentGeneration: Long
    ): ContextReadTitleGrounding {
        fun result(
            value: ContextReadTitleGroundingResult,
            expectedRef: String = ""
        ) = ContextReadTitleGrounding(value, decision, expectedRef)

        if (decision.route != ConversationRoute.CONTEXT_READ ||
            capturedSnapshot.scope == TaskContextScope.NONE ||
            capturedSnapshot.items.isEmpty()
        ) {
            return result(ContextReadTitleGroundingResult.NOT_APPLICABLE)
        }
        if (capturedSnapshot.generation != currentGeneration) {
            return result(ContextReadTitleGroundingResult.STALE_GENERATION)
        }

        val matches = SuppliedTaskTitleMatcher.matches(normalizedText, capturedSnapshot)
        if (matches.isEmpty()) {
            return result(ContextReadTitleGroundingResult.NOT_APPLICABLE)
        }
        if (matches.size > 1) {
            return if (ContextReferenceMutationGuard.hasExplicitContextSelector(normalizedText)) {
                result(ContextReadTitleGroundingResult.NOT_APPLICABLE)
            } else {
                result(ContextReadTitleGroundingResult.AMBIGUOUS_TITLE)
            }
        }

        val expectedRef = matches.single().ref
        val modelRef = decision.contextRef.trim()
        if (modelRef.isBlank()) {
            return ContextReadTitleGrounding(
                result = ContextReadTitleGroundingResult.GROUNDED_BLANK_MODEL_REF,
                decision = decision.copy(contextRef = expectedRef),
                expectedRef = expectedRef
            )
        }
        if (!modelRef.equals(expectedRef, ignoreCase = true)) {
            return result(
                ContextReadTitleGroundingResult.MODEL_REF_MISMATCH,
                expectedRef
            )
        }
        return ContextReadTitleGrounding(
            result = ContextReadTitleGroundingResult.MATCHED_MODEL_REF,
            decision = decision.copy(contextRef = expectedRef),
            expectedRef = expectedRef
        )
    }
}
