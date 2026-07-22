package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute
import java.util.Locale

/**
 * Fail-closed boundary for contextual task references that Android cannot mutate safely yet.
 * This only recognizes a small bounded set of references; it does not interpret task intent.
 */
object ContextReferenceMutationGuard {
    const val BLOCK_REASON = "CONTEXT_REFERENCE_MUTATION_BLOCKED"

    fun shouldBlock(
        decision: ConversationDecision,
        currentUtterance: String,
        snapshot: ReadOnlyTaskContextSnapshot
    ): Boolean {
        if (decision.route != ConversationRoute.TASK_COMMAND || snapshot.items.isEmpty()) {
            return false
        }

        return listOf(currentUtterance, decision.taskText)
            .asSequence()
            .filter { it.isNotBlank() }
            .any { containsSuppliedContextReference(it, snapshot) }
    }

    private fun containsSuppliedContextReference(
        text: String,
        snapshot: ReadOnlyTaskContextSnapshot
    ): Boolean {
        val suppliedRefs = snapshot.items.map { it.ref.uppercase(Locale.ROOT) }.toSet()
        if (TEMPORARY_REF.findAll(text).any { match ->
                match.value.uppercase(Locale.ROOT) in suppliedRefs
            }
        ) {
            return true
        }

        if (SUPPLIED_RESULT_ORDINAL.findAll(text).any { match ->
                ordinalPosition(match.groupValues[1]) in 1..snapshot.items.size
            }
        ) {
            return true
        }

        if (STANDALONE_SUPPLIED_ORDINAL.findAll(text).any { match ->
                ordinalPosition(match.groupValues[1]) in 1..snapshot.items.size
            }
        ) {
            return true
        }

        if (DEICTIC_TASK_REFERENCE.containsMatchIn(text)) {
            return true
        }

        return CONTEXTUAL_IT.any { pattern -> pattern.containsMatchIn(text) }
    }

    private fun ordinalPosition(value: String): Int = when (value.lowercase(Locale.ROOT)) {
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

    private val ORDINAL = "first|second|third|fourth|fifth|sixth|seventh|eighth|1st|2nd|3rd|4th|5th|6th|7th|8th"
    private val TEMPORARY_REF = Regex("(?i)(?<![A-Za-z0-9_])T[1-9][0-9]*(?![A-Za-z0-9_])")
    private val SUPPLIED_RESULT_ORDINAL = Regex(
        "(?i)\\b(?:the\\s+)?($ORDINAL)\\s+(?:one|task|result|item)\\b"
    )
    private val STANDALONE_SUPPLIED_ORDINAL = Regex(
        "(?i)\\bthe\\s+($ORDINAL)(?=\\s*(?:$|to\\b|on\\b|for\\b))"
    )
    private val DEICTIC_TASK_REFERENCE = Regex("(?i)\\b(?:that|this)\\s+(?:task|one)\\b")
    private val CONTEXTUAL_IT = listOf(
        Regex("(?i)\\b(?:delete|remove|complete|finish|undo|reopen)\\s+it(?:\\s+(?:please|now))?[.!?]?\\s*$"),
        Regex("(?i)\\bmark\\s+it\\s+(?:complete|done|unfinished|undone|active)\\b"),
        Regex("(?i)\\b(?:move|reschedule|change|update)\\s+it\\s+(?:to|on|for|with)\\b")
    )
}
