package com.example.myapplication.ai.temporal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RelativeTemporalProposalCanonicalizerTest {
    private val canonicalizer = RelativeTemporalProposalCanonicalizer()
    private val validator = RelativeTemporalProposalValidator()

    @Test
    fun emptyDateSetArtifactBecomesKeepAndReportsOnlyDateOperation() {
        val result = canonicalizer.canonicalize(
            proposal(
                dateOperation = RelativeTemporalOperation.SET,
                timeOperation = RelativeTemporalOperation.OFFSET,
                timeOffsetMinutes = 30
            )
        )

        assertEquals(RelativeTemporalOperation.KEEP, result.proposal.dateOperation)
        assertEquals(RelativeTemporalOperation.OFFSET, result.proposal.timeOperation)
        assertEquals(listOf("date_operation"), result.report.changedFields)
        validator.validate(result.proposal)
    }

    @Test
    fun emptyTimeSetArtifactBecomesKeepWithoutChangingDateAuthority() {
        val result = canonicalizer.canonicalize(
            proposal(
                dateOperation = RelativeTemporalOperation.OFFSET,
                timeOperation = RelativeTemporalOperation.SET,
                dateOffsetDays = 1
            )
        )

        assertEquals(RelativeTemporalOperation.OFFSET, result.proposal.dateOperation)
        assertEquals(RelativeTemporalOperation.KEEP, result.proposal.timeOperation)
        assertEquals(listOf("time_operation"), result.report.changedFields)
        validator.validate(result.proposal)
    }

    @Test
    fun setDateWithAnyOffsetIsNotCanonicalizedAndRemainsAStrictFailure() {
        listOf("", "tomorrow").forEach { replacement ->
            val result = canonicalizer.canonicalize(
                proposal(
                    dateOperation = RelativeTemporalOperation.SET,
                    timeOperation = RelativeTemporalOperation.KEEP,
                    replacementDateText = replacement,
                    dateOffsetDays = 1
                )
            )

            assertEquals(RelativeTemporalOperation.SET, result.proposal.dateOperation)
            assertEquals(emptyList<String>(), result.report.changedFields)
            val exception = assertThrows(RelativeTemporalProposalValidationException::class.java) {
                validator.validate(result.proposal)
            }
            assertEquals(
                RelativeTemporalValidationFailure.MALFORMED_DATE_COMBINATION,
                exception.failure
            )
        }
    }

    @Test
    fun offsetTimeWithZeroOffsetRemainsAStrictFailure() {
        val result = canonicalizer.canonicalize(
            proposal(
                dateOperation = RelativeTemporalOperation.KEEP,
                timeOperation = RelativeTemporalOperation.OFFSET
            )
        )

        assertEquals(RelativeTemporalOperation.OFFSET, result.proposal.timeOperation)
        assertEquals(emptyList<String>(), result.report.changedFields)
        val exception = assertThrows(RelativeTemporalProposalValidationException::class.java) {
            validator.validate(result.proposal)
        }
        assertEquals(RelativeTemporalValidationFailure.ZERO_TIME_OFFSET, exception.failure)
    }

    @Test
    fun twoEmptySetArtifactsBecomeKeepKeepThenFailAsNoChange() {
        val result = canonicalizer.canonicalize(
            proposal(
                dateOperation = RelativeTemporalOperation.SET,
                timeOperation = RelativeTemporalOperation.SET
            )
        )

        assertEquals(
            listOf("date_operation", "time_operation"),
            result.report.changedFields
        )
        val exception = assertThrows(RelativeTemporalProposalValidationException::class.java) {
            validator.validate(result.proposal)
        }
        assertEquals(RelativeTemporalValidationFailure.NO_CHANGE, exception.failure)
    }

    @Test
    fun lowConfidenceStillFailsAfterSafeCanonicalization() {
        val result = canonicalizer.canonicalize(
            proposal(
                dateOperation = RelativeTemporalOperation.SET,
                timeOperation = RelativeTemporalOperation.OFFSET,
                timeOffsetMinutes = 30,
                confidence = 0.79
            )
        )

        val exception = assertThrows(RelativeTemporalProposalValidationException::class.java) {
            validator.validate(result.proposal)
        }
        assertEquals(RelativeTemporalValidationFailure.LOW_CONFIDENCE, exception.failure)
    }

    private fun proposal(
        dateOperation: RelativeTemporalOperation,
        timeOperation: RelativeTemporalOperation,
        replacementDateText: String = "",
        replacementTimeText: String = "",
        dateOffsetDays: Int = 0,
        timeOffsetMinutes: Int = 0,
        confidence: Double = 0.97
    ) = RelativeTemporalProposal(
        dateOperation = dateOperation,
        timeOperation = timeOperation,
        relativeBase = RelativeTemporalBase.AUTHORITATIVE_TASK,
        replacementDateText = replacementDateText,
        replacementTimeText = replacementTimeText,
        dateOffsetDays = dateOffsetDays,
        timeOffsetMinutes = timeOffsetMinutes,
        confidence = confidence,
        needClarification = false
    )
}
