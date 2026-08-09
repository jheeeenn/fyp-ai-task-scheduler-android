package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationContextFocus
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute
import java.util.Locale

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
            currentDecision.route !in FAILURE_ROUTES ||
            !DELETE_COMMAND.matches(canonicalize(normalizedText))
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

    private fun canonicalize(text: String): String = text
        .lowercase(Locale.ROOT)
        .replace(PUNCTUATION, " ")
        .replace(WHITESPACE, " ")
        .trim()

    private val FAILURE_ROUTES = setOf(
        ConversationRoute.ASK_CLARIFICATION,
        ConversationRoute.UNKNOWN
    )
    private val DELETE_COMMAND = Regex(
        "(?:please\\s+)?(?:delete|remove)\\s+" +
            "(?:(?:this|that)(?:\\s+(?:task|one))?|it)(?:\\s+please)?"
    )
    private val PUNCTUATION = Regex("[.,!?]+")
    private val WHITESPACE = Regex("\\s+")
}
