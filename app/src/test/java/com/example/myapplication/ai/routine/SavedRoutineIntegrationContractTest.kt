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
        assertTrue(controller.contains("deleteCommitted"))
        assertTrue(controller.contains("if (state != SavedRoutineInteractionState.CONFIRMING_DELETE || deleteCommitted) return null"))
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
}
