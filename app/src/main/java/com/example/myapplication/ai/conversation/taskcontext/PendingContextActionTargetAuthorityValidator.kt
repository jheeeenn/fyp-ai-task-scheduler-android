package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.conversation.ConversationContextFocus
import com.example.myapplication.ai.conversation.ConversationDecision
import java.util.Locale

enum class PendingContextActionTargetAuthorityResult {
    ACCEPTED_DETERMINISTIC_SELECTOR,
    ACCEPTED_UNIQUE_PARTIAL_TITLE,
    INVALID_MOVE,
    LOW_CONFIDENCE,
    ACTION_CHANGED,
    STALE_GENERATION,
    SUPPLIED_REFS_CHANGED,
    UNKNOWN_REF,
    EXPLICIT_SELECTOR_MISMATCH,
    AMBIGUOUS_PARTIAL_TITLE,
    NO_TARGET_CORROBORATION
}

data class PendingContextActionTargetAuthority(
    val result: PendingContextActionTargetAuthorityResult,
    val ref: String = ""
) {
    val isAccepted: Boolean
        get() = result ==
            PendingContextActionTargetAuthorityResult.ACCEPTED_DETERMINISTIC_SELECTOR ||
            result == PendingContextActionTargetAuthorityResult.ACCEPTED_UNIQUE_PARTIAL_TITLE
}

/**
 * Authority boundary for the second turn of an already-pending contextual target question.
 *
 * The Conversation Agent may semantically propose one of Android's temporary refs. Android still
 * verifies the pending action, generation, complete supplied-ref set, explicit selectors and a
 * bounded lexical link to the authoritative title. This policy is intentionally not used for
 * ordinary context-action grounding.
 */
object PendingContextActionTargetAuthorityValidator {
    fun validate(
        normalizedText: String,
        interpretation: PendingContextActionTargetDecision,
        candidate: ConversationDecision,
        pendingAction: ConversationContextAction,
        pendingGeneration: Long,
        pendingSuppliedRefs: Set<String>,
        capturedSnapshot: ReadOnlyTaskContextSnapshot,
        currentGeneration: Long,
        currentFocus: ConversationContextFocus?
    ): PendingContextActionTargetAuthority {
        if (interpretation.move != PendingContextActionTargetMove.SELECT_TARGET) {
            return rejected(PendingContextActionTargetAuthorityResult.INVALID_MOVE)
        }
        if (!interpretation.confidence.isFinite() ||
            interpretation.confidence < ContextActionDecisionValidator.MIN_CONFIDENCE
        ) {
            return rejected(PendingContextActionTargetAuthorityResult.LOW_CONFIDENCE)
        }
        if (candidate.contextAction != pendingAction) {
            return rejected(PendingContextActionTargetAuthorityResult.ACTION_CHANGED)
        }
        if (capturedSnapshot.generation != pendingGeneration ||
            currentGeneration != pendingGeneration
        ) {
            return rejected(PendingContextActionTargetAuthorityResult.STALE_GENERATION)
        }

        val currentRefs = capturedSnapshot.items
            .map { it.ref.uppercase(Locale.ROOT) }
            .toSet()
        val expectedRefs = pendingSuppliedRefs.map { it.uppercase(Locale.ROOT) }.toSet()
        if (currentRefs != expectedRefs) {
            return rejected(PendingContextActionTargetAuthorityResult.SUPPLIED_REFS_CHANGED)
        }

        val selectedItem = capturedSnapshot.items.firstOrNull {
            it.ref.equals(interpretation.contextRef, ignoreCase = true)
        } ?: return rejected(PendingContextActionTargetAuthorityResult.UNKNOWN_REF)
        if (!candidate.contextRef.equals(selectedItem.ref, ignoreCase = true)) {
            return rejected(PendingContextActionTargetAuthorityResult.UNKNOWN_REF)
        }

        val ordinaryGrounding = ContextActionReferenceGroundingValidator.validate(
            normalizedText = normalizedText,
            decision = candidate,
            capturedSnapshot = capturedSnapshot,
            currentFocus = currentFocus
        )
        if (ordinaryGrounding.isValid) {
            return accepted(
                PendingContextActionTargetAuthorityResult.ACCEPTED_DETERMINISTIC_SELECTOR,
                ordinaryGrounding.ref
            )
        }
        if (ordinaryGrounding.result != ContextActionReferenceGroundingResult.NO_REFERENCE_EVIDENCE) {
            return rejected(PendingContextActionTargetAuthorityResult.EXPLICIT_SELECTOR_MISMATCH)
        }

        val utteranceTokens = meaningfulTokens(normalizedText)
        if (utteranceTokens.isEmpty()) {
            return rejected(PendingContextActionTargetAuthorityResult.NO_TARGET_CORROBORATION)
        }
        val partialMatches = capturedSnapshot.items.filter { item ->
            meaningfulTokens(item.title).any(utteranceTokens::contains)
        }
        if (partialMatches.size > 1) {
            return rejected(PendingContextActionTargetAuthorityResult.AMBIGUOUS_PARTIAL_TITLE)
        }
        val partialMatch = partialMatches.singleOrNull()
            ?: return rejected(PendingContextActionTargetAuthorityResult.NO_TARGET_CORROBORATION)
        if (!partialMatch.ref.equals(selectedItem.ref, ignoreCase = true)) {
            return rejected(PendingContextActionTargetAuthorityResult.EXPLICIT_SELECTOR_MISMATCH)
        }
        return accepted(
            PendingContextActionTargetAuthorityResult.ACCEPTED_UNIQUE_PARTIAL_TITLE,
            partialMatch.ref
        )
    }

    private fun meaningfulTokens(value: String): Set<String> = TOKEN.findAll(value)
        .map { it.value.lowercase(Locale.ROOT) }
        .filter { token -> token.length >= MIN_TOKEN_LENGTH && token !in NON_TITLE_TOKENS }
        .toSet()

    private fun accepted(
        result: PendingContextActionTargetAuthorityResult,
        ref: String
    ) = PendingContextActionTargetAuthority(result, ref)

    private fun rejected(result: PendingContextActionTargetAuthorityResult) =
        PendingContextActionTargetAuthority(result)

    private const val MIN_TOKEN_LENGTH = 3
    private val TOKEN = Regex("[\\p{L}\\p{N}]+")
    private val NON_TITLE_TOKENS = setOf(
        "the", "task", "one", "item", "result", "please", "my", "this", "that",
        "which", "about", "for", "with"
    )
}
