package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute

enum class ContextItemReadDisposition {
    NOT_APPLICABLE,
    RESOLVED
}

data class ContextItemReadResolution(
    val disposition: ContextItemReadDisposition,
    val decision: ConversationDecision? = null,
    val validation: ValidatedContextRead? = null
)

/**
 * Bounded Android-owned resolution for explicit, read-only contextual questions.
 *
 * It resolves only one supplied selector and one locally unambiguous detail. It never queries
 * task data or supplies an implicit selector.
 */
object ContextItemReadPolicy {
    fun resolve(
        normalizedText: String,
        capturedSnapshot: ReadOnlyTaskContextSnapshot,
        currentGeneration: Long
    ): ContextItemReadResolution {
        if (
            capturedSnapshot.items.isEmpty() ||
            capturedSnapshot.scope !in SUPPORTED_SCOPES ||
            ContextReferenceMutationGuard.containsMutationWording(normalizedText) ||
            ContextReferenceMutationGuard.explicitContextSelectorCount(normalizedText) != 1
        ) {
            return notApplicable()
        }

        val refs = ContextReferenceMutationGuard.explicitSuppliedRefs(
            text = normalizedText,
            snapshot = capturedSnapshot
        )
        if (refs.size != 1) return notApplicable()

        val detail = resolveDetail(normalizedText) ?: return notApplicable()
        val decision = ConversationDecision(
            route = ConversationRoute.CONTEXT_READ,
            contextRef = refs.single(),
            contextDetail = detail,
            confidence = 1.0,
            listenAgain = true,
            source = "android_context_item_read"
        )
        val validation = ReadOnlyTaskContextReadValidator.validate(
            decision = decision,
            capturedSnapshot = capturedSnapshot,
            currentGeneration = currentGeneration
        )
        if (!validation.isValid) return notApplicable()

        return ContextItemReadResolution(
            disposition = ContextItemReadDisposition.RESOLVED,
            decision = decision,
            validation = validation
        )
    }

    private fun resolveDetail(text: String): ConversationContextDetail? {
        if (MIXED_DATE_TIME_QUESTION.containsMatchIn(text)) return null
        val matches = buildSet {
            if (TIME_QUESTION.containsMatchIn(text)) add(ConversationContextDetail.TIME)
            if (DATE_QUESTION.containsMatchIn(text)) add(ConversationContextDetail.DATE)
            if (STATUS_QUESTION.containsMatchIn(text)) add(ConversationContextDetail.STATUS)
            if (SUBTASK_QUESTION.containsMatchIn(text)) add(ConversationContextDetail.SUBTASKS)
            if (
                SUMMARY_QUESTION.containsMatchIn(text) &&
                !EXPLICIT_DETAIL_NOUN.containsMatchIn(text)
            ) {
                add(ConversationContextDetail.SUMMARY)
            }
        }
        return matches.singleOrNull()
    }

    private fun notApplicable() =
        ContextItemReadResolution(ContextItemReadDisposition.NOT_APPLICABLE)

    private val SUPPORTED_SCOPES = setOf(
        TaskContextScope.RECENT_QUERY_RESULTS,
        TaskContextScope.DAILY_BRIEFING,
        TaskContextScope.CONTEXT_SUGGESTION,
        TaskContextScope.TASK_DETAIL
    )
    private val TIME_QUESTION = Regex(
        "(?i)\\b(?:what|which)(?:'s|\\s+is)?\\s+(?:(?:its|the)\\s+)?time\\b"
    )
    private val DATE_QUESTION = Regex(
        "(?i)\\b(?:what|which)(?:'s|\\s+is)?\\s+(?:(?:its|the)\\s+)?date\\b"
    )
    private val MIXED_DATE_TIME_QUESTION = Regex(
        "(?i)\\b(?:what|which)\\b[^.!?]*\\btime\\b[^.!?]*\\bdate\\b|" +
            "\\b(?:what|which)\\b[^.!?]*\\bdate\\b[^.!?]*\\btime\\b"
    )
    private val STATUS_QUESTION = Regex(
        "(?i)^\\s*(?:is|was)\\b[^.!?]*\\b(?:complete|completed|done|active|unfinished|overdue)\\b"
    )
    private val SUBTASK_QUESTION = Regex(
        "(?i)\\b(?:what|which|how\\s+many)\\b[^.!?]*\\bsubtasks?\\b"
    )
    private val SUMMARY_QUESTION = Regex(
        "(?i)^\\s*what\\s+(?:is|was)\\s+(?!the\\s+(?:time|date)\\b)"
    )
    private val EXPLICIT_DETAIL_NOUN = Regex(
        "(?i)\\b(?:time|date|status|title|subtask|subtasks)\\b"
    )
}
