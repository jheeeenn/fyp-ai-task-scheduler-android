package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextDetail

/** Keeps an otherwise valid context route from dropping an explicitly requested schedule field. */
object ContextReadDetailCompatibilityPolicy {
    fun isCompatible(normalizedText: String, proposed: ConversationContextDetail): Boolean {
        val required = requiredDetail(normalizedText) ?: return true
        return proposed == required
    }

    fun requiredDetail(normalizedText: String): ConversationContextDetail? = when {
        DATE_TIME_QUESTION.containsMatchIn(normalizedText) -> ConversationContextDetail.DATE_TIME
        DATE_QUESTION.containsMatchIn(normalizedText) -> ConversationContextDetail.DATE
        TIME_QUESTION.containsMatchIn(normalizedText) -> ConversationContextDetail.TIME
        else -> null
    }

    private val DATE_TIME_QUESTION = Regex(
        "(?i)\\b(?:date|day)\\b[^.!?]*\\btime\\b|" +
            "\\btime\\b[^.!?]*\\b(?:date|day)\\b|" +
            "\\bwhen\\s+(?:is|was)\\s+(?:it|this\\s+task|the\\s+task|t[1-9][0-9]*)\\b"
    )
    private val DATE_QUESTION = Regex(
        "(?i)\\b(?:what|which)(?:'s|\\s+is)?\\s+(?:(?:its|the)\\s+)?(?:date|day)\\b"
    )
    private val TIME_QUESTION = Regex(
        "(?i)\\b(?:what|which)(?:'s|\\s+is)?\\s+(?:(?:its|the)\\s+)?time\\b"
    )
}
