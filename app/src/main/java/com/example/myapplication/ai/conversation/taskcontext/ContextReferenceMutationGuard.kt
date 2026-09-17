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
            .any { containsContextReference(it, snapshot) }
    }

    fun containsContextReference(
        text: String,
        snapshot: ReadOnlyTaskContextSnapshot
    ): Boolean {
        if (snapshot.items.isEmpty()) return false
        if (explicitSuppliedRefs(text, snapshot).isNotEmpty()) {
            return true
        }

        if (DEICTIC_TASK_REFERENCE.containsMatchIn(text)) {
            return true
        }

        return CONTEXTUAL_IT.any { pattern -> pattern.containsMatchIn(text) }
    }

    fun isUnsupportedMutationWording(
        text: String,
        snapshot: ReadOnlyTaskContextSnapshot
    ): Boolean = containsContextReference(text, snapshot) &&
        containsMutationWording(text)

    fun containsMutationWording(text: String): Boolean =
        MUTATION_WORDING.containsMatchIn(text)

    fun explicitSuppliedRefs(
        text: String,
        snapshot: ReadOnlyTaskContextSnapshot
    ): Set<String> {
        if (snapshot.items.isEmpty()) return emptySet()
        val itemByRef = snapshot.items.associateBy { it.ref.uppercase(Locale.ROOT) }
        val refs = linkedSetOf<String>()
        ContextReferenceSelectorPatterns.temporaryRef.findAll(text).forEach { match ->
            itemByRef[match.value.uppercase(Locale.ROOT)]?.let { refs += it.ref }
        }
        sequenceOf(
            ContextReferenceSelectorPatterns.suppliedResultOrdinal,
            ContextReferenceSelectorPatterns.standaloneSuppliedOrdinal
        )
            .flatMap { pattern -> pattern.findAll(text) }
            .forEach { match ->
                val position = ContextReferenceSelectorPatterns.ordinalPosition(
                    match.groupValues[1]
                )
                snapshot.items.getOrNull(position - 1)?.let { refs += it.ref }
            }
        if (snapshot.items.size == 2) {
            ContextReferenceSelectorPatterns.pairSelector.findAll(text).forEach { match ->
                val position = if (
                    match.groupValues[1].equals("former", ignoreCase = true)
                ) 1 else 2
                snapshot.items.getOrNull(position - 1)?.let { refs += it.ref }
            }
        }
        return refs
    }

    fun hasExplicitContextSelector(text: String): Boolean =
        explicitContextSelectorCount(text) > 0

    fun explicitContextSelectorCount(text: String): Int =
        ContextReferenceSelectorPatterns.temporaryRef.findAll(text).count() +
            ContextReferenceSelectorPatterns.suppliedResultOrdinal.findAll(text).count() +
            ContextReferenceSelectorPatterns.standaloneSuppliedOrdinal.findAll(text).count() +
            ContextReferenceSelectorPatterns.pairSelector.findAll(text).count()

    fun hasFocusReference(text: String): Boolean =
        DEICTIC_TASK_REFERENCE.containsMatchIn(text) ||
            FOCUS_PRONOUN.containsMatchIn(text)

    fun containsUniqueSuppliedTitle(
        text: String,
        snapshot: ReadOnlyTaskContextSnapshot
    ): Boolean {
        val normalized = text.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return false
        val matches = snapshot.items.count { item ->
            item.title.isNotBlank() && normalized.contains(item.title.lowercase(Locale.ROOT))
        }
        return matches == 1
    }

    private val DEICTIC_TASK_REFERENCE = Regex("(?i)\\b(?:that|this)\\s+(?:task|one)\\b")
    private val FOCUS_PRONOUN = Regex("(?i)\\b(?:it|its|that one|that task)\\b")
    private val CONTEXTUAL_IT = listOf(
        Regex("(?i)\\b(?:delete|remove|complete|finish|undo|reopen)\\s+it(?:\\s+(?:please|now))?[.!?]?\\s*$"),
        Regex("(?i)\\bmark\\s+it\\s+(?:as\\s+)?(?:complete|completed|done|incomplete|unfinished|undone|active)\\b"),
        Regex("(?i)\\b(?:complete|finish|reopen)\\s+this(?:\\s+task)?(?:\\s+(?:please|now))?[.!?]?\\s*$"),
        Regex("(?i)\\b(?:move|reschedule|change|update)\\s+it\\s+(?:to|on|for|with)\\b"),
        Regex("(?i)\\b(?:edit|update|change|rename)\\s+its\\b"),
        Regex("(?i)\\b(?:edit|update|change)\\s+it(?:\\s+please)?[.!?]?\\s*$")
    )
    private val MUTATION_WORDING = Regex(
        "(?i)\\b(?:delete|remove|move|reschedule|mark|complete|finish|undo|reopen|update|change|edit|rename|break\\s+down)\\b|" +
            "\\b(?:this|that)\\s+(?:task|one)\\s+(?:is\\s+)?(?:completed|incomplete|unfinished)\\b|" +
            "\\b(?:this|that)\\s+(?:task|one)\\s+is\\s+not\\s+done\\b"
    )
}
