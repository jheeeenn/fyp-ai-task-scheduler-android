package com.example.myapplication.ai.conversation

import java.util.Locale

/** Rejects model-routed terminal decisions for bounded question-like utterances. */
object ConversationEndSessionSafetyPolicy {
    const val CLARIFICATION =
        "Are you asking whether there are more tasks, or would you like to end the assistant?"

    fun shouldRejectModelEndSession(normalizedText: String): Boolean {
        val canonical = normalizedText
            .lowercase(Locale.ROOT)
            .replace(PUNCTUATION, " ")
            .replace(WHITESPACE, " ")
            .trim()
        if (EXPLICIT_CLOSING_CLAUSE.containsMatchIn(canonical)) return false
        return QUESTION_LIKE_ENDING.matches(canonical)
    }

    private val QUESTION_LIKE_ENDING = Regex(
        "(?:is\\s+that\\s+(?:all|everything)|" +
            "are\\s+(?:those|these)\\s+all(?:\\s+the)?\\s+tasks?)"
    )
    private val EXPLICIT_CLOSING_CLAUSE = Regex(
        "\\b(?:i\\s+think\\s+i(?:'m|m|\\s+am)\\s+done(?:\\s+for\\s+now)?|" +
            "i(?:'m|m|\\s+am)\\s+done(?:\\s+for\\s+now)?|" +
            "that\\s+will\\s+be\\s+all|" +
            "i\\s+don'?t\\s+need\\s+anything\\s+else)\\b"
    )
    private val PUNCTUATION = Regex("[.,!?]+")
    private val WHITESPACE = Regex("\\s+")
}
