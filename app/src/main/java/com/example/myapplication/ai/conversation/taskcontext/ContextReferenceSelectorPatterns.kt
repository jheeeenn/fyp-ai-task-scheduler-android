package com.example.myapplication.ai.conversation.taskcontext

import java.util.Locale

/** Shared lexical selectors; authority still comes from the supplied snapshot and generation. */
internal object ContextReferenceSelectorPatterns {
    const val ORDINAL =
        "first|second|third|fourth|fifth|sixth|seventh|eighth|1st|2nd|3rd|4th|5th|6th|7th|8th"
    private const val CONTEXT_ITEM_NOUN = "one|task|subtasks?|result|item"

    val temporaryRef = Regex("(?i)(?<![A-Za-z0-9_])T[1-9][0-9]*(?![A-Za-z0-9_])")
    val suppliedResultOrdinal = Regex(
        "(?i)\\b(?:the\\s+)?($ORDINAL)\\s+(?:$CONTEXT_ITEM_NOUN)\\b"
    )
    val standaloneSuppliedOrdinal = Regex(
        "(?i)\\bthe\\s+($ORDINAL)(?=\\s*(?:$|to\\b|on\\b|for\\b))"
    )
    val pairSelector = Regex(
        "(?i)\\b(?:the\\s+)?(former|latter)\\s+(?:$CONTEXT_ITEM_NOUN)\\b"
    )

    fun ordinalPosition(value: String): Int = when (value.lowercase(Locale.ROOT)) {
        "first", "1st" -> 1
        "second", "2nd" -> 2
        "third", "3rd" -> 3
        "fourth", "4th" -> 4
        "fifth", "5th" -> 5
        "sixth", "6th" -> 6
        "seventh", "7th" -> 7
        "eighth", "8th" -> 8
        else -> -1
    }
}
