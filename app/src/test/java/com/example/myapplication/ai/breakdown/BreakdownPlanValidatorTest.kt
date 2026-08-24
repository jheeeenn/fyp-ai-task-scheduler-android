package com.example.myapplication.ai.breakdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakdownPlanValidatorTest {
    @Test
    fun twoToFiveUniqueOrderedTitlesAreAcceptedAndTrimmed() {
        val meaningfulTitles = listOf(
            "Define project requirements",
            "Research implementation options",
            "Build the first prototype",
            "Test the prototype",
            "Review the results"
        )
        (2..5).forEach { count ->
            val proposed = meaningfulTitles.take(count).map { "  $it  " }

            val result = BreakdownPlanValidator.validate("Final year project", proposed)

            assertTrue(result is BreakdownPlanValidationResult.Accepted)
            assertEquals(
                meaningfulTitles.take(count),
                (result as BreakdownPlanValidationResult.Accepted).titles
            )
        }
    }

    @Test
    fun meaningfulActionablePlanIsAccepted() {
        val result = BreakdownPlanValidator.validate(
            "Final year project",
            listOf(
                "Review requirements",
                "Draft implementation",
                "Test the prototype"
            )
        )

        assertEquals(
            listOf("Review requirements", "Draft implementation", "Test the prototype"),
            (result as BreakdownPlanValidationResult.Accepted).titles
        )
    }

    @Test
    fun standaloneGenericNumberedAndOrdinalPlaceholdersAreRejected() {
        listOf(
            listOf("Step 1", "Step 2", "Step 3"),
            listOf("Task 1", "Task 2"),
            listOf("Subtask 1", "Subtask 2"),
            listOf("First step", "Second step"),
            listOf("First task", "Second task"),
            listOf("Step number 1", "Step number 2")
        ).forEach { proposed ->
            assertRejected(BreakdownPlanValidationReason.PLACEHOLDER_TITLE, proposed)
        }
    }

    @Test
    fun meaningfulNumberedAndGenericWordTitlesRemainValid() {
        val proposed = listOf(
            "Draft chapter 1",
            "Review step 2 requirements",
            "Prepare task 1 dataset",
            "Complete the first experiment"
        )

        val result = BreakdownPlanValidator.validate("Final year project", proposed)

        assertEquals(
            proposed,
            (result as BreakdownPlanValidationResult.Accepted).titles
        )
    }

    @Test
    fun invalidCountsAreRejected() {
        listOf(
            listOf("one"),
            (1..6).map { "Draft section $it" }
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
