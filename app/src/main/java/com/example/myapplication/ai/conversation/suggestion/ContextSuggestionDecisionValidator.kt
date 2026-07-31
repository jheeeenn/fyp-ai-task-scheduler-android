package com.example.myapplication.ai.conversation.suggestion

enum class ContextSuggestionValidationResult {
    ACCEPTED,
    MALFORMED_RESPONSE,
    REQUEST_FAILED,
    LOW_CONFIDENCE,
    UNKNOWN_PRIMARY_REF,
    INVALID_SECONDARY_REF,
    DUPLICATE_REFS,
    NO_UNFINISHED_SUBTASK,
    NOT_BREAKDOWN_ELIGIBLE,
    UNKNOWN_CLOSE_PAIR,
    NO_CLOSE_SCHEDULE_WITH_PAIRS,
    NO_SUGGESTION_WITH_CANDIDATES,
    SUGGESTION_WITHOUT_CANDIDATES
}

object ContextSuggestionDecisionValidator {
    const val MIN_CONFIDENCE = 0.80

    fun validate(
        decision: ContextSuggestionDecision,
        snapshot: ContextSuggestionSnapshot
    ): ContextSuggestionValidationResult {
        if (decision.confidence < MIN_CONFIDENCE) {
            return ContextSuggestionValidationResult.LOW_CONFIDENCE
        }
        if (
            decision.primaryRef.isNotEmpty() &&
            decision.primaryRef == decision.secondaryRef
        ) {
            return ContextSuggestionValidationResult.DUPLICATE_REFS
        }
        return when (decision.suggestionType) {
            ContextSuggestionType.NO_CLOSE_SCHEDULE -> {
                if (
                    decision.primaryRef.isNotEmpty() ||
                    decision.secondaryRef.isNotEmpty()
                ) {
                    ContextSuggestionValidationResult.INVALID_SECONDARY_REF
                } else if (snapshot.closePairs.isNotEmpty()) {
                    ContextSuggestionValidationResult.NO_CLOSE_SCHEDULE_WITH_PAIRS
                } else {
                    ContextSuggestionValidationResult.ACCEPTED
                }
            }
            ContextSuggestionType.NO_SUGGESTION -> {
                if (
                    decision.primaryRef.isNotEmpty() ||
                    decision.secondaryRef.isNotEmpty()
                ) {
                    ContextSuggestionValidationResult.INVALID_SECONDARY_REF
                } else if (snapshot.candidates.isNotEmpty()) {
                    ContextSuggestionValidationResult.NO_SUGGESTION_WITH_CANDIDATES
                } else {
                    ContextSuggestionValidationResult.ACCEPTED
                }
            }
            ContextSuggestionType.FOCUS_TASK -> {
                oneCandidateResult(decision, snapshot)
            }
            ContextSuggestionType.CONTINUE_SUBTASK -> {
                val base = oneCandidateResult(decision, snapshot)
                if (base != ContextSuggestionValidationResult.ACCEPTED) {
                    base
                } else if (
                    requireNotNull(snapshot.candidate(decision.primaryRef))
                        .unfinishedSubtaskCount <= 0
                ) {
                    ContextSuggestionValidationResult.NO_UNFINISHED_SUBTASK
                } else {
                    ContextSuggestionValidationResult.ACCEPTED
                }
            }
            ContextSuggestionType.BREAK_DOWN_TASK -> {
                val base = oneCandidateResult(decision, snapshot)
                if (base != ContextSuggestionValidationResult.ACCEPTED) {
                    base
                } else if (
                    !requireNotNull(snapshot.candidate(decision.primaryRef))
                        .structurallyEligibleForBreakdown
                ) {
                    ContextSuggestionValidationResult.NOT_BREAKDOWN_ELIGIBLE
                } else {
                    ContextSuggestionValidationResult.ACCEPTED
                }
            }
            ContextSuggestionType.REVIEW_CLOSE_SCHEDULE -> {
                when {
                    snapshot.candidates.isEmpty() ->
                        ContextSuggestionValidationResult.SUGGESTION_WITHOUT_CANDIDATES
                    snapshot.candidate(decision.primaryRef) == null ->
                        ContextSuggestionValidationResult.UNKNOWN_PRIMARY_REF
                    snapshot.candidate(decision.secondaryRef) == null ->
                        ContextSuggestionValidationResult.INVALID_SECONDARY_REF
                    snapshot.closePair(
                        decision.primaryRef,
                        decision.secondaryRef
                    ) == null -> ContextSuggestionValidationResult.UNKNOWN_CLOSE_PAIR
                    else -> ContextSuggestionValidationResult.ACCEPTED
                }
            }
        }
    }

    private fun oneCandidateResult(
        decision: ContextSuggestionDecision,
        snapshot: ContextSuggestionSnapshot
    ): ContextSuggestionValidationResult = when {
        snapshot.candidates.isEmpty() ->
            ContextSuggestionValidationResult.SUGGESTION_WITHOUT_CANDIDATES
        snapshot.candidate(decision.primaryRef) == null ->
            ContextSuggestionValidationResult.UNKNOWN_PRIMARY_REF
        decision.secondaryRef.isNotEmpty() ->
            ContextSuggestionValidationResult.INVALID_SECONDARY_REF
        else -> ContextSuggestionValidationResult.ACCEPTED
    }
}
