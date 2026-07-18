package com.example.myapplication.ai.conversation

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class HomeActivityObservationMigrationSourceTest {
    private val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()

    @Test fun detailedFollowUpQueryUsesLastQueryWindowAndDetailedMode() {
        val body = source.substringAfter("private fun handleDetailedFollowUpQuery()").substringBefore("private fun openCreateTaskFromFollowUp")
        assertTrue(body.contains("dateText = lastQueryWindow.spokenLabel"))
        assertTrue(body.contains("responseManager.getMaxTasksForMode(QueryDetailMode.DETAILED)"))
        assertTrue(body.contains("val hint = responseManager.hintCreateOrRead()"))
        assertFalse(body.contains("showAssistantReply(reply)"))
        assertFalse(body.contains("setIdleState()"))
    }

    @Test fun ambiguityResolvedMutationUsesCmasObservationPath() {
        val body = source.substringAfter("private fun handleTaskMatchAmbiguity").substringBefore("private fun speakThenOpen")
        assertTrue(body.contains("speakObservationThenRun(ExecutionObservation(ExecutionOperation.UPDATE_TASK"))
        assertTrue(body.contains("speakObservationThenRun(ExecutionObservation(ExecutionOperation.RESCHEDULE_TASK"))
        assertTrue(body.contains("speakObservation(ExecutionObservation(ExecutionOperation.MARK_DONE"))
        assertTrue(body.contains("chosenTask.copy(isDone = true)"))
        assertTrue(body.contains("speakObservation(ExecutionObservation(ExecutionOperation.MARK_UNDONE"))
        assertTrue(body.contains("chosenTask.copy(isDone = false)"))
    }

    @Test fun deleteCancellationProducesCancelledObservation() {
        val body = source.substringAfter("HomeFollowUpContext.DELETE_CONFIRMATION").substringBefore("HomeFollowUpContext.BREAKDOWN_CONFIRMATION")
        assertTrue(body.contains("ExecutionOperation.DELETE_TASK"))
        assertTrue(body.contains("ExecutionOutcome.CANCELLED"))
    }
}
