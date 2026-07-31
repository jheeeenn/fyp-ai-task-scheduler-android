package com.example.myapplication.ai.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContextRescheduleCollectionSourceTest {
    private val home = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()

    @Test
    fun relativeProposalIsCalculatedOnlyAfterGroundingAndAuthoritativeRefetch() {
        val branch = home
            .substringAfter("ConversationRoute.CONTEXT_ACTION ->")
            .substringBefore("ConversationRoute.QUERY_READING_CONTROL ->")
        val grounding = branch.indexOf("ContextActionReferenceGroundingValidator.validate(")
        val extraction = branch.indexOf("agentOrchestrator.processContextAction(")
        val calculationFetch = branch.indexOf("val calculationTask =")
        val calculation = branch.indexOf("RelativeTemporalChangeCalculator().calculate(")
        val openingFetch = branch.indexOf("val openingTask =")
        val open = branch.indexOf("openContextActionEditScreen(")

        assertTrue(grounding >= 0)
        assertTrue(grounding < extraction)
        assertTrue(extraction < calculationFetch)
        assertTrue(calculationFetch < calculation)
        assertTrue(calculation < openingFetch)
        assertTrue(openingFetch < open)
        assertTrue(branch.contains("sameContextActionTaskSnapshot(initiallyFetchedTask, calculationTask)"))
        assertTrue(branch.contains("sameContextActionTaskSnapshot(calculationTask, openingTask)"))
    }

    @Test
    fun calculationFailureAndPastResultReturnBeforeOpeningEditor() {
        val branch = home
            .substringAfter("val calculation = if (")
            .substringBefore("conversationOrchestrator.commitFinalDecision(conversationDecision)")
        val failure = branch.substringAfter("is RelativeTemporalCalculationResult.Failure")
            .substringBefore("is RelativeTemporalCalculationResult.PastSchedule")
        val past = branch.substringAfter("is RelativeTemporalCalculationResult.PastSchedule")
            .substringBefore("is RelativeTemporalCalculationResult.Success")

        assertTrue(failure.contains("calculationClarification"))
        assertTrue(failure.contains("return@launch"))
        assertTrue(past.contains("pastSchedule"))
        assertTrue(past.contains("return@launch"))
        assertFalse(failure.contains("openContextActionEditScreen("))
        assertFalse(past.contains("openContextActionEditScreen("))
    }

    @Test
    fun extractionLogContainsOnlyBoundedBooleanOutcomeMetadata() {
        val log = home
            .substringAfter("\"HOME_CONTEXT_RESCHEDULE_EXTRACTION\"")
            .substringBefore(")\n")

        assertTrue(log.contains("hasDateChange="))
        assertTrue(log.contains("hasTimeChange="))
        assertTrue(log.contains("clarificationRequired="))
        listOf(
            "task.title", "task.id", "privateTaskId", "replacementDateText",
            "replacementTimeText", "normalized", "date=", "time="
        ).forEach { forbidden -> assertFalse("log contains $forbidden", log.contains(forbidden)) }
    }

    @Test
    fun editIntentContainsOnlyAndroidCalculatedExactProposalAndDoesNotSave() {
        val helper = home
            .substringAfter("private suspend fun openContextActionEditScreen(")
            .substringBefore("private fun todayDateString()")

        assertTrue(helper.contains("putExtra(\"task_id\", task.id)"))
        assertTrue(helper.contains("calculatedTemporal?.schedule?.date"))
        assertTrue(helper.contains("calculatedTemporal?.schedule?.time"))
        assertTrue(helper.contains("\"relative_temporal_proposal\""))
        assertTrue(helper.contains("\"relative_temporal_revision\""))
        assertFalse(helper.contains("updateTask("))
        assertFalse(helper.contains("saveTask("))
    }
}
