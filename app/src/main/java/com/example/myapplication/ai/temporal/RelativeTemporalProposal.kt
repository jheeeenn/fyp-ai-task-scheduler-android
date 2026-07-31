package com.example.myapplication.ai.temporal

enum class RelativeTemporalOperation {
    KEEP,
    SET,
    OFFSET
}

enum class RelativeTemporalBase {
    AUTHORITATIVE_TASK,
    CURRENT_PROPOSAL
}

/**
 * A bounded semantic proposal. It contains operations and literal replacement text only;
 * final task facts are always calculated by Android.
 *
 * Offsets are deliberately bounded to one year for dates and one week for times. These limits
 * cover useful conversational changes while preventing an accidental model output from moving a
 * task an unreasonably large distance.
 */
data class RelativeTemporalProposal(
    val dateOperation: RelativeTemporalOperation,
    val timeOperation: RelativeTemporalOperation,
    val relativeBase: RelativeTemporalBase,
    val replacementDateText: String,
    val replacementTimeText: String,
    val dateOffsetDays: Int,
    val timeOffsetMinutes: Int,
    val confidence: Double,
    val needClarification: Boolean
) {
    companion object {
        const val MIN_CONFIDENCE = 0.80
        const val MAX_ABSOLUTE_DATE_OFFSET_DAYS = 365
        const val MAX_ABSOLUTE_TIME_OFFSET_MINUTES = 10_080
    }
}

enum class RelativeTemporalValidationFailure {
    UNKNOWN_DATE_OPERATION,
    UNKNOWN_TIME_OPERATION,
    UNKNOWN_RELATIVE_BASE,
    NON_FINITE_CONFIDENCE,
    LOW_CONFIDENCE,
    CLARIFICATION_REQUIRED,
    MALFORMED_DATE_COMBINATION,
    MALFORMED_TIME_COMBINATION,
    ZERO_DATE_OFFSET,
    ZERO_TIME_OFFSET,
    DATE_OFFSET_OUT_OF_BOUNDS,
    TIME_OFFSET_OUT_OF_BOUNDS,
    NO_CHANGE
}

class RelativeTemporalProposalValidationException(
    val failure: RelativeTemporalValidationFailure,
    message: String
) : IllegalArgumentException(message)

/** Validates strict operation combinations without calculating final dates or times. */
class RelativeTemporalProposalValidator {
    fun validate(proposal: RelativeTemporalProposal): RelativeTemporalProposal {
        if (!proposal.confidence.isFinite()) {
            fail(RelativeTemporalValidationFailure.NON_FINITE_CONFIDENCE, "Confidence must be finite")
        }
        if (proposal.confidence !in RelativeTemporalProposal.MIN_CONFIDENCE..1.0) {
            fail(
                RelativeTemporalValidationFailure.LOW_CONFIDENCE,
                "Confidence is below ${RelativeTemporalProposal.MIN_CONFIDENCE}"
            )
        }
        if (proposal.needClarification) {
            fail(
                RelativeTemporalValidationFailure.CLARIFICATION_REQUIRED,
                "Semantic interpretation requires clarification"
            )
        }

        validateDate(proposal)
        validateTime(proposal)
        if (
            proposal.dateOperation == RelativeTemporalOperation.KEEP &&
            proposal.timeOperation == RelativeTemporalOperation.KEEP
        ) {
            fail(RelativeTemporalValidationFailure.NO_CHANGE, "Proposal does not request a change")
        }
        return proposal.copy(
            replacementDateText = proposal.replacementDateText.clean(),
            replacementTimeText = proposal.replacementTimeText.clean()
        )
    }

    private fun validateDate(proposal: RelativeTemporalProposal) {
        val replacement = proposal.replacementDateText.clean()
        when (proposal.dateOperation) {
            RelativeTemporalOperation.KEEP -> if (replacement.isNotEmpty() || proposal.dateOffsetDays != 0) {
                fail(
                    RelativeTemporalValidationFailure.MALFORMED_DATE_COMBINATION,
                    "KEEP date requires empty replacement text and zero offset"
                )
            }
            RelativeTemporalOperation.SET -> if (replacement.isEmpty() || proposal.dateOffsetDays != 0) {
                fail(
                    RelativeTemporalValidationFailure.MALFORMED_DATE_COMBINATION,
                    "SET date requires replacement text and zero offset"
                )
            }
            RelativeTemporalOperation.OFFSET -> {
                if (replacement.isNotEmpty()) {
                    fail(
                        RelativeTemporalValidationFailure.MALFORMED_DATE_COMBINATION,
                        "OFFSET date requires empty replacement text"
                    )
                }
                if (proposal.dateOffsetDays == 0) {
                    fail(RelativeTemporalValidationFailure.ZERO_DATE_OFFSET, "Date offset must not be zero")
                }
                if (
                    proposal.dateOffsetDays !in
                    -RelativeTemporalProposal.MAX_ABSOLUTE_DATE_OFFSET_DAYS..
                        RelativeTemporalProposal.MAX_ABSOLUTE_DATE_OFFSET_DAYS
                ) {
                    fail(
                        RelativeTemporalValidationFailure.DATE_OFFSET_OUT_OF_BOUNDS,
                        "Date offset is outside the supported bounds"
                    )
                }
            }
        }
    }

    private fun validateTime(proposal: RelativeTemporalProposal) {
        val replacement = proposal.replacementTimeText.clean()
        when (proposal.timeOperation) {
            RelativeTemporalOperation.KEEP -> if (replacement.isNotEmpty() || proposal.timeOffsetMinutes != 0) {
                fail(
                    RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION,
                    "KEEP time requires empty replacement text and zero offset"
                )
            }
            RelativeTemporalOperation.SET -> if (replacement.isEmpty() || proposal.timeOffsetMinutes != 0) {
                fail(
                    RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION,
                    "SET time requires replacement text and zero offset"
                )
            }
            RelativeTemporalOperation.OFFSET -> {
                if (replacement.isNotEmpty()) {
                    fail(
                        RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION,
                        "OFFSET time requires empty replacement text"
                    )
                }
                if (proposal.timeOffsetMinutes == 0) {
                    fail(RelativeTemporalValidationFailure.ZERO_TIME_OFFSET, "Time offset must not be zero")
                }
                if (
                    proposal.timeOffsetMinutes !in
                    -RelativeTemporalProposal.MAX_ABSOLUTE_TIME_OFFSET_MINUTES..
                        RelativeTemporalProposal.MAX_ABSOLUTE_TIME_OFFSET_MINUTES
                ) {
                    fail(
                        RelativeTemporalValidationFailure.TIME_OFFSET_OUT_OF_BOUNDS,
                        "Time offset is outside the supported bounds"
                    )
                }
            }
        }
    }

    private fun String.clean(): String = trim().replace(Regex("\\s+"), " ")

    private fun fail(failure: RelativeTemporalValidationFailure, message: String): Nothing {
        throw RelativeTemporalProposalValidationException(failure, message)
    }
}
