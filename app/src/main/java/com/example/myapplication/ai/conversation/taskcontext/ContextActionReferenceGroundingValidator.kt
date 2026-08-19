package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextFocus
import com.example.myapplication.ai.conversation.ConversationDecision
import java.util.Locale

enum class ContextActionReferenceGroundingResult {
    VALID_EXPLICIT_REF,
    VALID_ORDINAL,
    VALID_UNIQUE_TITLE,
    VALID_CURRENT_FOCUS,
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
            ContextActionReferenceGroundingResult.VALID_CURRENT_FOCUS
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

        val spokenRefs = TEMPORARY_REF.findAll(normalizedText)
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

        val ordinalPositions = sequenceOf(SUPPLIED_RESULT_ORDINAL, STANDALONE_SUPPLIED_ORDINAL)
            .flatMap { pattern -> pattern.findAll(normalizedText) }
            .map { match -> ordinalPosition(match.groupValues[1]) }
            .filter { it > 0 }
            .toMutableList()
        PAIR_SELECTOR.findAll(normalizedText)
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

        val titleMatches = suppliedTitleMatches(normalizedText, capturedSnapshot)
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

    private fun suppliedTitleMatches(
        normalizedText: String,
        snapshot: ReadOnlyTaskContextSnapshot
    ): List<ReadOnlyTaskContextItem> {
        val utterance = normalize(normalizedText)
        if (utterance.isEmpty()) return emptyList()
        return snapshot.items.filter { item ->
            val title = normalize(item.title)
            title.isNotEmpty() && TITLE_BOUNDARY_TEMPLATE
                .replace("{{TITLE}}", Regex.escape(title))
                .toRegex(RegexOption.IGNORE_CASE)
                .containsMatchIn(utterance)
        }
    }

    private fun normalize(value: String): String = value
        .trim()
        .replace(WHITESPACE, " ")
        .lowercase(Locale.ROOT)

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

    private const val ORDINAL =
        "first|second|third|fourth|fifth|sixth|seventh|eighth|1st|2nd|3rd|4th|5th|6th|7th|8th"
    private const val TITLE_BOUNDARY_TEMPLATE = "(?<![\\p{L}\\p{N}_]){{TITLE}}(?![\\p{L}\\p{N}_])"
    private val WHITESPACE = Regex("\\s+")
    private val TEMPORARY_REF = Regex("(?i)(?<![A-Za-z0-9_])T[1-9][0-9]*(?![A-Za-z0-9_])")
    private val SUPPLIED_RESULT_ORDINAL = Regex(
        "(?i)\\b(?:the\\s+)?($ORDINAL)\\s+(?:one|task|result|item)\\b"
    )
    private val STANDALONE_SUPPLIED_ORDINAL = Regex(
        "(?i)\\bthe\\s+($ORDINAL)(?=\\s*(?:$|to\\b|on\\b|for\\b))"
    )
    private val PAIR_SELECTOR = Regex(
        "(?i)\\b(?:the\\s+)?(former|latter)\\s+(?:one|task|result|item)\\b"
    )
    private val FOCUS_REFERENCE = Regex(
        "(?i)\\b(?:it|its|that\\s+(?:task|one)|this\\s+(?:task|one))\\b"
    )
    private val BARE_FOCUS_REFERENCE = Regex("(?i)\\b(?:this|that)\\b")
}
