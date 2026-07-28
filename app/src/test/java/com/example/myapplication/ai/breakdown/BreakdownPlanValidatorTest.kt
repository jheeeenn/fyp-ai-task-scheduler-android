package com.example.myapplication.ai.breakdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakdownPlanValidatorTest {
    @Test
    fun twoToFiveUniqueOrderedTitlesAreAcceptedAndTrimmed() {
        (2..5).forEach { count ->
            val proposed = (1..count).map { "  Step $it  " }

            val result = BreakdownPlanValidator.validate("Final year project", proposed)

            assertTrue(result is BreakdownPlanValidationResult.Accepted)
            assertEquals(
                (1..count).map { "Step $it" },
                (result as BreakdownPlanValidationResult.Accepted).titles
            )
        }
    }

    @Test
    fun invalidCountsAreRejected() {
        listOf(
            listOf("one"),
            (1..6).map { "step $it" }
        ).forEach { proposed ->
            assertRejected(
                BreakdownPlanValidationReason.INVALID_COUNT,
                proposed
            )
        }
    }

    @Test
    fun emptyDuplicateOversizedAndParentEquivalentTitlesAreRejected() {
        assertRejected(
            BreakdownPlanValidationReason.EMPTY_TITLE,
            listOf("first", "   ")
        )
        assertRejected(
            BreakdownPlanValidationReason.DUPLICATE_TITLE,
            listOf("Review literature", "  review LITERATURE ")
        )
        assertRejected(
            BreakdownPlanValidationReason.TITLE_TOO_LONG,
            listOf("a".repeat(BreakdownPlanValidator.MAX_TITLE_LENGTH + 1), "valid")
        )
        assertRejected(
            BreakdownPlanValidationReason.PARENT_EQUIVALENT,
            listOf("Final year project task", "Draft the introduction")
        )
    }

    private fun assertRejected(
        expected: BreakdownPlanValidationReason,
        proposed: List<String>
    ) {
        val result = BreakdownPlanValidator.validate("Final year project", proposed)
        assertEquals(
            expected,
            (result as BreakdownPlanValidationResult.Rejected).reason
        )
    }
}
