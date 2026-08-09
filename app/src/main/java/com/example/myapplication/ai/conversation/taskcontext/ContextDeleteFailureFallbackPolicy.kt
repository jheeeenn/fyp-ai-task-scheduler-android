package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationContextFocus
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute

/** Narrow fail-closed fallback after semantic contextual-action routing has been attempted. */
object ContextDeleteFailureFallbackPolicy {
    const val SOURCE = "android_context_delete_failure_fallback"

    fun resolve(
        normalizedText: String,
        currentDecision: ConversationDecision,
        capturedSnapshot: ReadOnlyTaskContextSnapshot,
        currentGeneration: Long,
        currentFocus: ConversationContextFocus?,
        agentAttempted: Boolean
    ): ConversationDecision? {
        if (!agentAttempted ||
            !DELETE_WORDING.containsMatchIn(normalizedText) ||
            !FOCUS_EXPRESSION.containsMatchIn(normalizedText)
        ) {
            return null
        }
        val focus = currentFocus ?: return null
        if (!focus.available ||
            focus.generation != capturedSnapshot.generation ||
            currentGeneration != capturedSnapshot.generation
        ) {
            return null
        }
        val focusedItems = capturedSnapshot.items.filter {
            it.ref.equals(focus.ref, ignoreCase = true)
        }
        if (focusedItems.size != 1) return null

        if (isUsableContextualDelete(
                normalizedText = normalizedText,
                decision = currentDecision,
                capturedSnapshot = capturedSnapshot,
                currentGeneration = currentGeneration,
                currentFocus = focus
            )
        ) {
            return null
        }

        return ConversationDecision(
            route = ConversationRoute.CONTEXT_ACTION,
            contextRef = focusedItems.single().ref,
            contextDetail = ConversationContextDetail.NONE,
            contextAction = ConversationContextAction.DELETE,
            confidence = 1.0,
            listenAgain = false,
            source = SOURCE
        )
    }

    private fun isUsableContextualDelete(
        normalizedText: String,
        decision: ConversationDecision,
        capturedSnapshot: ReadOnlyTaskContextSnapshot,
        currentGeneration: Long,
        currentFocus: ConversationContextFocus
    ): Boolean {
        if (decision.route != ConversationRoute.CONTEXT_ACTION ||
            decision.contextAction != ConversationContextAction.DELETE
        ) {
            return false
        }
        val validation = ContextActionDecisionValidator.validate(
            decision = decision,
            capturedSnapshot = capturedSnapshot,
            currentGeneration = currentGeneration
        )
        if (!validation.isValid) return false
        return ContextActionReferenceGroundingValidator.validate(
            normalizedText = normalizedText,
            decision = decision,
            capturedSnapshot = capturedSnapshot,
            currentFocus = currentFocus
        ).isValid
    }

    private val DELETE_WORDING = Regex("(?i)\\b(?:delete|remove)\\b")
    private val FOCUS_EXPRESSION = Regex(
        "(?i)\\b(?:this(?:\\s+(?:task|one))?|that(?:\\s+(?:task|one))?|it)\\b"
    )
}
