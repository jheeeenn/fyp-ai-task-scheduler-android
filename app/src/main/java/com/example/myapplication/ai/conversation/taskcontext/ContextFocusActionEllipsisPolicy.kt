package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationContextFocus
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute
import java.util.Locale

/** Narrow safety fallback for action-only utterances after Gemma routing has already run. */
object ContextFocusActionEllipsisPolicy {
    const val SOURCE = "android_context_focus_action_ellipsis"

    fun resolve(
        normalizedText: String,
        currentDecision: ConversationDecision,
        capturedSnapshot: ReadOnlyTaskContextSnapshot,
        currentGeneration: Long,
        currentFocus: ConversationContextFocus?
    ): ConversationDecision? {
        if (currentDecision.route !in FALLBACK_ROUTES) return null
        val action = boundedAction(normalizedText) ?: return null
        val focus = currentFocus ?: return null
        if (!focus.available ||
            focus.generation != capturedSnapshot.generation ||
            currentGeneration != capturedSnapshot.generation
        ) return null
        val focusedItems = capturedSnapshot.items.filter {
            it.ref.equals(focus.ref, ignoreCase = true)
        }
        if (focusedItems.size != 1) return null
        return ConversationDecision(
            route = ConversationRoute.CONTEXT_ACTION,
            contextRef = focusedItems.single().ref,
            contextDetail = ConversationContextDetail.NONE,
            contextAction = action,
            confidence = 1.0,
            listenAgain = false,
            source = SOURCE
        )
    }

    fun isBoundedActionOnly(text: String): Boolean = boundedAction(text) != null

    private fun boundedAction(text: String): ConversationContextAction? = when (
        text.lowercase(Locale.ROOT).replace(PUNCTUATION, " ").replace(WHITESPACE, " ").trim()
    ) {
        "delete", "delete please", "please delete", "remove", "remove please", "please remove" ->
            ConversationContextAction.DELETE
        "edit", "edit please", "please edit", "update", "update please", "please update" ->
            ConversationContextAction.UPDATE
        "reschedule", "reschedule please", "please reschedule" ->
            ConversationContextAction.RESCHEDULE
        else -> null
    }

    private val FALLBACK_ROUTES = setOf(
        ConversationRoute.TASK_COMMAND,
        ConversationRoute.ASK_CLARIFICATION,
        ConversationRoute.UNKNOWN
    )
    private val PUNCTUATION = Regex("[.,!?]+")
    private val WHITESPACE = Regex("\\s+")
}
