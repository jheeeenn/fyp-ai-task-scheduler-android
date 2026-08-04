package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationContextFocus
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute

object ContextFocusCarryForwardPolicy {
    const val SOURCE = "android_context_focus_fallback"

    fun resolve(
        normalizedText: String,
        focus: ConversationContextFocus?,
        capturedSnapshot: ReadOnlyTaskContextSnapshot,
        isResultInteraction: Boolean
    ): ConversationDecision? {
        if (!isResultInteraction || focus == null || !focus.available) return null
        if (focus.generation != capturedSnapshot.generation) return null
        if (capturedSnapshot.items.none { it.ref.equals(focus.ref, ignoreCase = true) }) return null
        if (ContextReferenceMutationGuard.containsMutationWording(normalizedText)) return null

        val explicitRefs = ContextReferenceMutationGuard.explicitSuppliedRefs(
            normalizedText,
            capturedSnapshot
        )
        if (explicitRefs.any { !it.equals(focus.ref, ignoreCase = true) }) return null
        if (ContextReferenceMutationGuard.hasExplicitContextSelector(normalizedText) &&
            explicitRefs.isEmpty()
        ) {
            return null
        }

        val detail = requestedDetail(normalizedText) ?: return null
        return ConversationDecision(
            route = ConversationRoute.CONTEXT_READ,
            contextRef = focus.ref,
            contextDetail = detail,
            confidence = 1.0,
            listenAgain = true,
            source = SOURCE
        )
    }

    private fun requestedDetail(text: String): ConversationContextDetail? = when {
        DATE_TIME_QUESTION.containsMatchIn(text) -> ConversationContextDetail.DATE_TIME
        TIME_QUESTION.containsMatchIn(text) -> ConversationContextDetail.TIME
        DATE_QUESTION.containsMatchIn(text) -> ConversationContextDetail.DATE
        STATUS_QUESTION.containsMatchIn(text) -> ConversationContextDetail.STATUS
        SUBTASK_QUESTION.containsMatchIn(text) -> ConversationContextDetail.SUBTASKS
        TITLE_QUESTION.containsMatchIn(text) -> ConversationContextDetail.TITLE
        SUMMARY_QUESTION.containsMatchIn(text) -> ConversationContextDetail.SUMMARY
        else -> null
    }

    private val TIME_QUESTION = Regex(
        "(?i)\\bwhat(?:'s|\\s+is)?\\s+(?:(?:its|the)\\s+)?time\\b"
    )
    private val DATE_TIME_QUESTION = Regex(
        "(?i)\\b(?:date|day)\\b[^.!?]*\\btime\\b|" +
            "\\btime\\b[^.!?]*\\b(?:date|day)\\b|" +
            "\\bwhen\\s+(?:is|was)\\s+(?:it|the\\s+task|this\\s+task)\\b"
    )
    private val DATE_QUESTION = Regex(
        "(?i)\\b(?:what|which)(?:'s|\\s+is)?\\s+(?:(?:its|the)\\s+)?(?:date|day)\\b"
    )
    private val STATUS_QUESTION = Regex(
        "(?i)\\b(?:is\\s+it\\s+(?:completed|done|active|overdue)|" +
            "what(?:'s|\\s+is)\\s+(?:its|the)\\s+status)\\b"
    )
    private val SUBTASK_QUESTION = Regex(
        "(?i)\\b(?:how\\s+many\\s+subtasks|what\\s+subtasks|subtasks?\\s+does\\s+it\\s+have)\\b"
    )
    private val TITLE_QUESTION = Regex(
        "(?i)\\b(?:what\\s+(?:was|is)\\s+it\\s+called|what(?:'s|\\s+is)\\s+its\\s+(?:name|title))\\b"
    )
    private val SUMMARY_QUESTION = Regex(
        "(?i)\\b(?:what\\s+was\\s+it|tell\\s+me\\s+about\\s+it|" +
            "read\\s+(?:its\\s+details|the\\s+task|it))\\b"
    )
}
