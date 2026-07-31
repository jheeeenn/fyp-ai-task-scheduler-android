package com.example.myapplication.ai.temporal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RelativeTemporalProposalValidatorTest {
    private val validator = RelativeTemporalProposalValidator()

    @Test
    fun contradictorySchemaFieldsAreRejected() {
        val exception = assertThrows(RelativeTemporalProposalValidationException::class.java) {
            validator.validate(
                proposal(
                    timeOperation = RelativeTemporalOperation.KEEP,
                    replacementTime = "9 AM"
                )
            )
        }
        assertEquals(
            RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION,
            exception.failure
        )
    }

    @Test
    fun lowNonFiniteAndOutOfRangeConfidenceAreRejected() {
        listOf(0.59, Double.NaN, Double.POSITIVE_INFINITY, 1.01).forEach { confidence ->
            assertThrows(RelativeTemporalProposalValidationException::class.java) {
                validator.validate(
                    proposal(
                        timeOperation = RelativeTemporalOperation.OFFSET,
                        timeOffset = 30,
                        confidence = confidence
                    )
                )
            }
        }
    }

    @Test
    fun dateOffsetBoundsAreInclusiveAndOutsideValuesAreRejected() {
        listOf(-365, 365).forEach { days ->
            validator.validate(
                proposal(
                    dateOperation = RelativeTemporalOperation.OFFSET,
                    dateOffset = days
                )
            )
        }
        listOf(-366, 366).forEach { days ->
            assertThrows(RelativeTemporalProposalValidationException::class.java) {
                validator.validate(
                    proposal(
                        dateOperation = RelativeTemporalOperation.OFFSET,
                        dateOffset = days
                    )
                )
            }
        }
    }

    @Test
    fun bothKeepAndClarificationAreRejected() {
        assertThrows(RelativeTemporalProposalValidationException::class.java) {
            validator.validate(proposal())
        }
        assertThrows(RelativeTemporalProposalValidationException::class.java) {
            validator.validate(
                proposal(
                    timeOperation = RelativeTemporalOperation.OFFSET,
                    timeOffset = 30,
                    clarification = true
                )
            )
        }
    }

    private fun proposal(
        dateOperation: RelativeTemporalOperation = RelativeTemporalOperation.KEEP,
        timeOperation: RelativeTemporalOperation = RelativeTemporalOperation.KEEP,
        replacementDate: String = "",
        replacementTime: String = "",
        dateOffset: Int = 0,
        timeOffset: Int = 0,
        confidence: Double = 0.98,
        clarification: Boolean = false
    ) = RelativeTemporalProposal(
        dateOperation,
        timeOperation,
        RelativeTemporalBase.AUTHORITATIVE_TASK,
        replacementDate,
        replacementTime,
        dateOffset,
        timeOffset,
        confidence,
        clarification
    )
}
