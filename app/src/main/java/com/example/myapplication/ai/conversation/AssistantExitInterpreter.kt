package com.example.myapplication.ai.conversation

import java.util.Locale

/** Bounded whole-utterance safety reflex for ending an assistant conversation. */
object AssistantExitInterpreter {
    fun isExitUtterance(text: String): Boolean = EXIT_UTTERANCE.matches(canonicalize(text))

    fun isFollowUpExitUtterance(text: String): Boolean {
        val canonical = canonicalize(text)
        return canonical == "no" || EXIT_UTTERANCE.matches(canonical)
    }

    private fun canonicalize(text: String): String = text
        .lowercase(Locale.ROOT)
        .replace('\u2019', '\'')
        .replace(PUNCTUATION, " ")
        .replace(WHITESPACE, " ")
        .trim()

    private val EXIT_UTTERANCE = Regex(
        "(?:" +
            "(?:(?:okay|ok|no)\\s+)?(?:that(?:'s|s| is) all|that(?:'s|s| is) enough)|" +
            "nothing else|" +
            "i(?:'m|m| am) done|" +
            "i don'?t need anything else|" +
            "i(?:'m|m| am) finished for now|" +
            "stop listening|" +
            "goodbye|bye|stop|cancel|no thanks|thank you|thanks|exit|quit|close|end|" +
            "done|all done|finished|that(?:'s|s| is) it" +
        ")"
    )
    private val PUNCTUATION = Regex("[.,!?]+")
    private val WHITESPACE = Regex("\\s+")
}
