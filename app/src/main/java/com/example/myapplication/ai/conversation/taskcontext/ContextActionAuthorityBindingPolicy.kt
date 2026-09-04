package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.conversation.ConversationContextFocus
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute
import java.util.Locale

enum class ContextActionAuthorityBindingResult {
    UNCHANGED,
    BOUND_TO_CURRENT_FOCUS
}

data class ContextActionAuthorityBinding(
    val decision: ConversationDecision,
    val result: ContextActionAuthorityBindingResult,
    val modelRef: String = "",
    val authoritativeRef: String = ""
)

/**
 * Reconciles only a model-selected temporary ref when Android has stronger, unique focus
 * authority. Semantic action selection and every execution decision remain model/activity owned.
 */
object ContextActionAuthorityBindingPolicy {
    fun reconcile(
        normalizedText: String,
        decision: ConversationDecision,
        capturedSnapshot: ReadOnlyTaskContextSnapshot,
        contextFocus: ConversationContextFocus?
    ): ContextActionAuthorityBinding {
        fun unchanged() = ContextActionAuthorityBinding(
            decision = decision,
            result = ContextActionAuthorityBindingResult.UNCHANGED
        )

        if (decision.route != ConversationRoute.CONTEXT_ACTION ||
            decision.contextAction !in SUPPORTED_ACTIONS
        ) {
            return unchanged()
        }

        val modelRef = decision.contextRef.trim()
        if (modelRef.isBlank() || !TEMPORARY_REF.matches(modelRef)) return unchanged()

        if (capturedSnapshot.scope == TaskContextScope.NONE ||
            capturedSnapshot.truncated ||
            capturedSnapshot.items.size != 1
        ) {
            return unchanged()
        }
        val soleItem = capturedSnapshot.items.single()
        val suppliedRefs = capturedSnapshot.items
            .map { it.ref.trim().uppercase(Locale.ROOT) }
            .filter { TEMPORARY_REF.matches(it) }
            .distinct()
        if (suppliedRefs.size != 1) return unchanged()

        val authoritativeRef = soleItem.ref.trim()
        if (modelRef.equals(authoritativeRef, ignoreCase = true)) return unchanged()

        if (!ContextReferenceMutationGuard.hasFocusReference(normalizedText) ||
            ContextReferenceMutationGuard.hasExplicitContextSelector(normalizedText) ||
            ContextReferenceMutationGuard.explicitSuppliedRefs(
                normalizedText,
                capturedSnapshot
            ).isNotEmpty() ||
            ContextReferenceMutationGuard.containsUniqueSuppliedTitle(
                normalizedText,
                capturedSnapshot
            )
        ) {
            return unchanged()
        }

        val focus = contextFocus?.takeIf { it.available } ?: return unchanged()
        if (focus.generation != capturedSnapshot.generation ||
            !focus.ref.trim().equals(authoritativeRef, ignoreCase = true)
        ) {
            return unchanged()
        }

        return ContextActionAuthorityBinding(
            decision = decision.copy(contextRef = authoritativeRef),
            result = ContextActionAuthorityBindingResult.BOUND_TO_CURRENT_FOCUS,
            modelRef = modelRef,
            authoritativeRef = authoritativeRef
        )
    }

    private val SUPPORTED_ACTIONS = setOf(
        ConversationContextAction.UPDATE,
        ConversationContextAction.RESCHEDULE,
        ConversationContextAction.DELETE,
        ConversationContextAction.MARK_DONE,
        ConversationContextAction.MARK_UNDONE
    )
    private val TEMPORARY_REF = Regex("T[1-9][0-9]*", RegexOption.IGNORE_CASE)
}
