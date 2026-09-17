package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextFocus
import com.example.myapplication.ai.conversation.ConversationDecision
import java.util.Locale

enum class ContextActionReferenceGroundingResult {
    VALID_EXPLICIT_REF,
    VALID_ORDINAL,
    VALID_UNIQUE_TITLE,
    VALID_CURRENT_FOCUS,
    VALID_TASK_DETAIL_IMPLICIT_FOCUS,
    VALID_SINGLE_RESULT_IMPLICIT_FOCUS,
    NO_REFERENCE_EVIDENCE,
    MISSING_CURRENT_FOCUS,
    SELECTED_REF_MISMATCH,
    AMBIGUOUS_TITLE,
    STALE_FOCUS
}

data class GroundedContextActionReference(
    val result: ContextActionReferenceGroundingResult,
    val ref: String = ""
) {
    val isValid: Boolean
        get() = result in VALID_RESULTS

    private companion object {
        val VALID_RESULTS = setOf(
            ContextActionReferenceGroundingResult.VALID_EXPLICIT_REF,
            ContextActionReferenceGroundingResult.VALID_ORDINAL,
            ContextActionReferenceGroundingResult.VALID_UNIQUE_TITLE,
            ContextActionReferenceGroundingResult.VALID_CURRENT_FOCUS,
            ContextActionReferenceGroundingResult.VALID_TASK_DETAIL_IMPLICIT_FOCUS,
            ContextActionReferenceGroundingResult.VALID_SINGLE_RESULT_IMPLICIT_FOCUS
        )
    }
}

/**
 * Derives reference authority from the user's utterance or Android-validated focus. Snapshot
 * order is never used unless the user supplied an ordinal that deterministically names it.
 */
object ContextActionReferenceGroundingValidator {
    fun validate(
        normalizedText: String,
        decision: ConversationDecision,
        capturedSnapshot: ReadOnlyTaskContextSnapshot,
        currentFocus: ConversationContextFocus?
    ): GroundedContextActionReference {
        val selectedRef = decision.contextRef.trim()

        val spokenRefs = ContextReferenceSelectorPatterns.temporaryRef.findAll(normalizedText)
            .map { it.value.uppercase(Locale.ROOT) }
            .distinct()
            .toList()
        if (spokenRefs.isNotEmpty()) {
            val expected = spokenRefs.singleOrNull()?.let { spokenRef ->
                capturedSnapshot.items.firstOrNull {
                    it.ref.equals(spokenRef, ignoreCase = true)
                }?.ref
            }
            return compareSelected(
                selectedRef = selectedRef,
                expectedRef = expected,
                validResult = ContextActionReferenceGroundingResult.VALID_EXPLICIT_REF
            )
        }

        val ordinalPositions = sequenceOf(
            ContextReferenceSelectorPatterns.suppliedResultOrdinal,
            ContextReferenceSelectorPatterns.standaloneSuppliedOrdinal
        )
            .flatMap { pattern -> pattern.findAll(normalizedText) }
            .map { match ->
                ContextReferenceSelectorPatterns.ordinalPosition(match.groupValues[1])
            }
            .filter { it > 0 }
            .toMutableList()
        ContextReferenceSelectorPatterns.pairSelector.findAll(normalizedText)
            .map { match ->
                if (capturedSnapshot.items.size == 2) {
                    if (match.groupValues[1].equals("former", ignoreCase = true)) 1 else 2
                } else {
                    -1
                }
            }
            .filter { it > 0 }
            .forEach(ordinalPositions::add)
        val distinctOrdinalPositions = ordinalPositions.distinct()
        if (distinctOrdinalPositions.isNotEmpty()) {
            val expected = distinctOrdinalPositions.singleOrNull()?.let { position ->
                capturedSnapshot.items.getOrNull(position - 1)?.ref
            }
            return compareSelected(
                selectedRef = selectedRef,
                expectedRef = expected,
                validResult = ContextActionReferenceGroundingResult.VALID_ORDINAL
            )
        }

        val hasQualifiedFocusReference = FOCUS_REFERENCE.containsMatchIn(normalizedText)
        val hasBareFocusReference = !hasQualifiedFocusReference &&
            BARE_FOCUS_REFERENCE.containsMatchIn(normalizedText)
        if (hasQualifiedFocusReference || hasBareFocusReference) {
            if (currentFocus == null || !currentFocus.available) {
                return GroundedContextActionReference(
                    ContextActionReferenceGroundingResult.MISSING_CURRENT_FOCUS
                )
            }
            val focusItems = capturedSnapshot.items.filter {
                it.ref.equals(currentFocus.ref, ignoreCase = true)
            }
            if (currentFocus.generation != capturedSnapshot.generation || focusItems.size != 1) {
                return GroundedContextActionReference(
                    ContextActionReferenceGroundingResult.STALE_FOCUS
                )
            }
            val focusItem = focusItems.single()
            val isStrictSingleFocusContext =
                capturedSnapshot.items.size == 1
            if (hasBareFocusReference && !isStrictSingleFocusContext) {
                return GroundedContextActionReference(
                    ContextActionReferenceGroundingResult.NO_REFERENCE_EVIDENCE
                )
            }
            return compareSelected(
                selectedRef = selectedRef,
                expectedRef = focusItem.ref,
                validResult = ContextActionReferenceGroundingResult.VALID_CURRENT_FOCUS
            )
        }

        if (ContextFocusActionEllipsisPolicy.isBoundedActionOnly(normalizedText)) {
            if (currentFocus == null || !currentFocus.available) {
                return GroundedContextActionReference(
                    ContextActionReferenceGroundingResult.MISSING_CURRENT_FOCUS
                )
            }
            val focusItems = capturedSnapshot.items.filter {
                it.ref.equals(currentFocus.ref, ignoreCase = true)
            }
            if (currentFocus.generation != capturedSnapshot.generation || focusItems.size != 1) {
                return GroundedContextActionReference(
                    ContextActionReferenceGroundingResult.STALE_FOCUS
                )
            }
            val focusItem = focusItems.single()
            return compareSelected(
                selectedRef = selectedRef,
                expectedRef = focusItem.ref,
                validResult = ContextActionReferenceGroundingResult.VALID_CURRENT_FOCUS
            )
        }

        val titleMatches = SuppliedTaskTitleMatcher.matches(normalizedText, capturedSnapshot)
        if (titleMatches.size > 1) {
            return GroundedContextActionReference(
                ContextActionReferenceGroundingResult.AMBIGUOUS_TITLE
            )
        }
        if (titleMatches.size == 1) {
            return compareSelected(
                selectedRef = selectedRef,
                expectedRef = titleMatches.single().ref,
                validResult = ContextActionReferenceGroundingResult.VALID_UNIQUE_TITLE
            )
        }

        if (capturedSnapshot.scope == TaskContextScope.TASK_DETAIL &&
            capturedSnapshot.items.size == 1
        ) {
            if (currentFocus == null || !currentFocus.available) {
                return GroundedContextActionReference(
                    ContextActionReferenceGroundingResult.MISSING_CURRENT_FOCUS
                )
            }
            val expectedRef = strictTaskDetailImplicitFocusRef(
                capturedSnapshot,
                currentFocus
            ) ?: return GroundedContextActionReference(
                ContextActionReferenceGroundingResult.STALE_FOCUS
            )
            return compareSelected(
                selectedRef = selectedRef,
                expectedRef = expectedRef,
                validResult =
                    ContextActionReferenceGroundingResult.VALID_TASK_DETAIL_IMPLICIT_FOCUS
            )
        }

        if (capturedSnapshot.scope == TaskContextScope.RECENT_QUERY_RESULTS &&
            capturedSnapshot.items.size == 1
        ) {
            if (currentFocus == null || !currentFocus.available) {
                return GroundedContextActionReference(
                    ContextActionReferenceGroundingResult.MISSING_CURRENT_FOCUS
                )
            }
            val expectedRef = strictSingleResultImplicitFocusRef(
                capturedSnapshot,
                currentFocus
            ) ?: return GroundedContextActionReference(
                ContextActionReferenceGroundingResult.STALE_FOCUS
            )
            return compareSelected(
                selectedRef = selectedRef,
                expectedRef = expectedRef,
                validResult =
                    ContextActionReferenceGroundingResult.VALID_SINGLE_RESULT_IMPLICIT_FOCUS
            )
        }

        return GroundedContextActionReference(
            ContextActionReferenceGroundingResult.NO_REFERENCE_EVIDENCE
        )
    }

    private fun compareSelected(
        selectedRef: String,
        expectedRef: String?,
        validResult: ContextActionReferenceGroundingResult
    ): GroundedContextActionReference {
        if (expectedRef == null) {
            return GroundedContextActionReference(
                ContextActionReferenceGroundingResult.SELECTED_REF_MISMATCH
            )
        }
        if (selectedRef.isNotBlank() && !selectedRef.equals(expectedRef, ignoreCase = true)) {
            return GroundedContextActionReference(
                ContextActionReferenceGroundingResult.SELECTED_REF_MISMATCH
            )
        }
        return GroundedContextActionReference(validResult, expectedRef)
    }

    private val FOCUS_REFERENCE = Regex(
        "(?i)\\b(?:it|its|that\\s+(?:task|one)|this\\s+(?:task|one))\\b"
    )
    private val BARE_FOCUS_REFERENCE = Regex("(?i)\\b(?:this|that)\\b")
}

internal fun strictTaskDetailImplicitFocusRef(
    capturedSnapshot: ReadOnlyTaskContextSnapshot,
    currentFocus: ConversationContextFocus?
): String? = strictImplicitFocusRef(
    capturedSnapshot = capturedSnapshot,
    currentFocus = currentFocus,
    requiredScope = TaskContextScope.TASK_DETAIL
)

internal fun strictSingleResultImplicitFocusRef(
    capturedSnapshot: ReadOnlyTaskContextSnapshot,
    currentFocus: ConversationContextFocus?
): String? = strictImplicitFocusRef(
    capturedSnapshot = capturedSnapshot,
    currentFocus = currentFocus,
    requiredScope = TaskContextScope.RECENT_QUERY_RESULTS
)

private fun strictImplicitFocusRef(
    capturedSnapshot: ReadOnlyTaskContextSnapshot,
    currentFocus: ConversationContextFocus?,
    requiredScope: TaskContextScope
): String? {
    if (capturedSnapshot.scope != requiredScope ||
        capturedSnapshot.items.size != 1 ||
        currentFocus?.available != true ||
        currentFocus.generation != capturedSnapshot.generation
    ) {
        return null
    }
    return capturedSnapshot.items
        .filter { it.ref.equals(currentFocus.ref, ignoreCase = true) }
        .singleOrNull()
        ?.ref
}
