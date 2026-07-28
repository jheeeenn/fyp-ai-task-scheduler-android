package com.example.myapplication.ai.routine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SavedRoutineIntegrationContractTest {
    private val home =
        File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()

    @Test
    fun activeDraftAndSavedRoutineStatePrecedeGeneralRouting() {
        val dispatch = home
            .substringAfter("private fun handleVoiceCommand")
            .substringBefore("lifecycleScope.launch")

        assertTrue(
            dispatch.indexOf("routineDraftController.state") <
                dispatch.indexOf("savedRoutineInteractionController.state")
        )
        assertTrue(
            dispatch.indexOf("savedRoutineInteractionController.state") <
                dispatch.indexOf("conversationIntentClassifier.classify")
        )
        assertTrue(dispatch.contains("handleSavedRoutineInteractionFollowUp"))
        assertTrue(home.contains("SAVED_ROUTINE_STALE"))
        assertTrue(home.contains("isAssistantRequestCurrent(requestToken)"))
    }

    @Test
    fun deleteIsExplicitlyConfirmedAndRepeatedConfirmationCannotDeleteTwice() {
        val selected = home
            .substringAfter("private suspend fun executeSelectedSavedRoutine")
            .substringBefore("private fun deleteConfirmedSavedRoutine")
        val deletion = home
            .substringAfter("private fun deleteConfirmedSavedRoutine")
            .substringBefore("private fun renderSavedRoutineDetails")
        val controller = File(
            "src/main/java/com/example/myapplication/ai/routine/saved/" +
                "SavedRoutineInteractionController.kt"
        ).readText()

        assertTrue(selected.contains("beginDeleteConfirmation"))
        assertTrue(selected.contains("Do you want me to delete"))
        assertFalse(selected.substringBefore("beginDeleteConfirmation").contains("deleteRoutineAndSteps"))
        assertTrue(deletion.contains("claimDelete()"))
        assertTrue(deletion.contains("deleteRoutineAndSteps"))
        assertTrue(controller.contains("state = SavedRoutineInteractionState.DELETING"))
        assertTrue(controller.contains("if (state != SavedRoutineInteractionState.CONFIRMING_DELETE) return null"))
        assertTrue(controller.contains("if (state == SavedRoutineInteractionState.DELETING) return false"))
        assertTrue(deletion.contains("completeDelete(generation)"))
        assertTrue(deletion.contains("assistantSession.assistantSessionActive"))
        assertFalse(deletion.contains("savedRoutineInteractionController.clear()"))
    }

    @Test
    fun committedDeletionSurvivesPanelAndSessionCleanupUntilDatabaseCompletion() {
        val cancelled = home
            .substringAfter("override fun onAssistantCancelled()")
            .substringBefore("override fun onAssistantSessionStopped()")
        val stopped = home
            .substringAfter("override fun onAssistantSessionStopped()")
            .substringBefore("override fun onResume()")
        val followUp = home
            .substringAfter("private fun handleSavedRoutineInteractionFollowUp")
            .substringBefore("private suspend fun executeSelectedSavedRoutine")
        val destroy = home
            .substringAfter("override fun onDestroy()")
            .substringBefore("super.onDestroy()")

        assertTrue(cancelled.contains("savedRoutineInteractionController.clear()"))
        assertTrue(stopped.contains("savedRoutineInteractionController.clear()"))
        assertTrue(followUp.contains("SavedRoutineInteractionState.DELETING"))
        assertTrue(followUp.contains("deletion is already being processed"))
        assertTrue(destroy.contains("clearForActivityDestruction()"))
    }

    @Test
    fun libraryCommandsUseOneBoundedSemanticCallRatherThanPhraseRouting() {
        val actionFlow = home
            .substringAfter("private suspend fun handleSavedRoutineAction")
            .substringBefore("private suspend fun listSavedRoutines")

        assertTrue(actionFlow.contains("savedRoutineSemanticOrchestrator.interpret(normalizedRequest)"))
        assertFalse(actionFlow.contains("processRepair"))
        assertFalse(actionFlow.contains("contains(\"list"))
        assertFalse(actionFlow.contains("contains(\"read"))
        assertFalse(actionFlow.contains("contains(\"run"))
        assertFalse(actionFlow.contains("contains(\"delete"))
    }

    @Test
    fun roomIdsRemainPrivateAndOnlySelectedIdDiagnosticIsDebugGated() {
        val context = File(
            "src/main/java/com/example/myapplication/ai/routine/followup/" +
                "RoutineFollowUpAgentContext.kt"
        ).readText()
        val diagnostics = File(
            "src/main/java/com/example/myapplication/diagnostics/DebugDiagnosticLog.kt"
        ).readText()

        assertFalse(context.contains("savedRoutineId"))
        assertFalse(context.contains("routineId"))
        assertTrue(home.contains("DebugDiagnosticLog.event(\n            \"SAVED_ROUTINE_SELECTED\""))
        assertTrue(diagnostics.contains("if (!BuildConfig.DEBUG) return"))
        assertFalse(
            Regex("""Log\.[diew]\([^)]*routineId""", setOf(RegexOption.DOT_MATCHES_ALL))
                .containsMatchIn(home)
        )
    }

    @Test
    fun newAndSavedOriginsUseDifferentAtomicPersistencePaths() {
        val coordinator = File(
            "src/main/java/com/example/myapplication/ai/routine/" +
                "RoutinePersistenceCoordinator.kt"
        ).readText()
        val dao = File("src/main/java/com/example/myapplication/data/RoutineDao.kt").readText()

        assertTrue(coordinator.contains("RoutineDraftOrigin.NEW_ROUTINE"))
        assertTrue(coordinator.contains("insertNewRoutineWithFirstOccurrence"))
        assertTrue(coordinator.contains("RoutineDraftOrigin.SAVED_ROUTINE"))
        assertTrue(coordinator.contains("insertSavedRoutineOccurrence"))
        assertTrue(dao.contains("@Transaction"))
        assertTrue(dao.contains("insertRoutineWithFirstOccurrence"))
        assertTrue(dao.indexOf("insertSteps") < dao.indexOf("insertTasks(occurrenceTasks)"))
    }

    @Test
    fun savedRoutineDateAndStoredTimeFailuresUseSeparateSpeech() {
        val selected = home
            .substringAfter("SavedRoutineAction.RUN ->")
            .substringBefore("SavedRoutineAction.DELETE ->")
        val updateHandler = home
            .substringAfter("private fun handleRoutineDraftUpdate")
            .substringBefore("private fun logRoutineDraftState")

        assertTrue(selected.contains("invalidDateSpeech ="))
        assertTrue(selected.contains("I could not understand that date"))
        assertTrue(selected.contains("invalidTimeSpeech ="))
        assertTrue(selected.contains("invalid stored step time"))
        assertTrue(updateHandler.contains("RoutineDraftIssue.INVALID_DATE"))
        assertTrue(updateHandler.contains("invalidDateSpeech ?: invalidSpeech"))
        assertTrue(updateHandler.contains("RoutineDraftIssue.INVALID_TIME"))
        assertTrue(updateHandler.contains("invalidTimeSpeech ?: invalidSpeech"))
    }
}
