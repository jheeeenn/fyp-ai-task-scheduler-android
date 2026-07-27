package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute

enum class ContextItemRestatementDisposition {
    NOT_APPLICABLE,
    RESOLVED,
    UNAVAILABLE_SELECTOR,
    AMBIGUOUS_SELECTOR
}

data class ContextItemRestatementResolution(
    val disposition: ContextItemRestatementDisposition,
    val decision: ConversationDecision? = null,
    val validation: ValidatedContextRead? = null,
    val clarification: String = ""
)

/**
 * Bounded Android-owned semantic resolution for restating one current context item.
 *
 * It does not query Room, infer task facts, or handle generic/page repetition.
 */
object ContextItemRestatementPolicy {
    const val UNAVAILABLE_CLARIFICATION =
        "That task is not in the current results. Please choose one of the spoken tasks."
    const val AMBIGUOUS_CLARIFICATION =
        "Please choose one of the spoken tasks to repeat."

    fun resolve(
        normalizedText: String,
        capturedSnapshot: ReadOnlyTaskContextSnapshot,
        currentGeneration: Long
    ): ContextItemRestatementResolution {
        if (capturedSnapshot.items.isEmpty() ||
            capturedSnapshot.scope !in SUPPORTED_SCOPES ||
            !RESTATEMENT_WORDING.containsMatchIn(normalizedText)
        ) {
            return ContextItemRestatementResolution(
                ContextItemRestatementDisposition.NOT_APPLICABLE
            )
        }

        val selectorCount =
            ContextReferenceMutationGuard.explicitContextSelectorCount(normalizedText)
        if (selectorCount == 0) {
            return ContextItemRestatementResolution(
                ContextItemRestatementDisposition.NOT_APPLICABLE
            )
        }
        if (selectorCount != 1) {
            return ContextItemRestatementResolution(
                disposition = ContextItemRestatementDisposition.AMBIGUOUS_SELECTOR,
                clarification = AMBIGUOUS_CLARIFICATION
            )
        }

        val refs = ContextReferenceMutationGuard.explicitSuppliedRefs(
            text = normalizedText,
            snapshot = capturedSnapshot
        )
        if (refs.size != 1) {
            return ContextItemRestatementResolution(
                disposition = ContextItemRestatementDisposition.UNAVAILABLE_SELECTOR,
                clarification = UNAVAILABLE_CLARIFICATION
            )
        }

        val decision = ConversationDecision(
            route = ConversationRoute.CONTEXT_READ,
            contextRef = refs.single(),
            contextDetail = ConversationContextDetail.SUMMARY,
            confidence = 1.0,
            listenAgain = true,
            source = "android_context_item_restatement"
        )
        val validation = ReadOnlyTaskContextReadValidator.validate(
            decision = decision,
            capturedSnapshot = capturedSnapshot,
            currentGeneration = currentGeneration
        )
        if (!validation.isValid) {
            return ContextItemRestatementResolution(
                disposition = ContextItemRestatementDisposition.UNAVAILABLE_SELECTOR,
                clarification = UNAVAILABLE_CLARIFICATION
            )
        }
        return ContextItemRestatementResolution(
            disposition = ContextItemRestatementDisposition.RESOLVED,
            decision = decision,
            validation = validation
        )
    }

    private val SUPPORTED_SCOPES = setOf(
        TaskContextScope.RECENT_QUERY_RESULTS,
        TaskContextScope.DAILY_BRIEFING
    )
    private val RESTATEMENT_WORDING = Regex(
        "(?i)\\b(?:repeat|what\\s+was)\\b|" +
            "\\b(?:say|read|tell)\\b[^.!?]*\\bagain\\b|" +
            "\\bagain\\b[^.!?]*\\b(?:say|read|tell)\\b"
    )
}
