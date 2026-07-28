package com.example.myapplication.ai.breakdown

import com.example.myapplication.ai.agent.LaptopAgentClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BreakdownIntegrationContractTest {
    @Test
    fun homeUsesOneBoundedControllerAndResolvesActiveRootsBeforeConfirmation() {
        val source = homeSource()
        val branch = source
            .substringAfter("AiIntent.BREAKDOWN_TASK.name ->")
            .substringBefore("else ->")
        val resolution = source
            .substringAfter("private suspend fun beginBreakdownTargetResolution")
            .substringBefore("private fun handleExistingBreakdownTarget")

        assertTrue(branch.contains("beginBreakdownTargetResolution("))
        assertFalse(branch.contains("dao.insert("))
        assertTrue(resolution.contains("dao.getRootActiveTasks()"))
        assertTrue(resolution.contains("BreakdownTargetResolver.resolve"))
        assertTrue(resolution.contains("dao.getSubtasks"))
        assertTrue(
            resolution.indexOf("targetPreference == BreakdownTargetPreference.NEW_ROOT") <
                resolution.indexOf("dao.getRootActiveTasks()")
        )
        assertFalse(source.contains("pendingBreakdownTitle"))
        assertFalse(source.contains("pendingBreakdownPlan"))
        assertFalse(source.contains("private fun startBreakdownConfirmation"))
    }

    @Test
    fun roomTransactionsRecheckParentsAndInsertOrderedInheritedChildren() {
        val dao = File("src/main/java/com/example/myapplication/data/TaskDao.kt").readText()
        val existing = dao
            .substringAfter("suspend fun insertSubtasksIntoExistingRootAtomically")
            .substringBefore("suspend fun insertNewRootWithSubtasksAtomically")
        val newRoot = dao
            .substringAfter("suspend fun insertNewRootWithSubtasksAtomically")
            .substringBefore("@Query(\"UPDATE tasks SET isDone")

        assertTrue(
            dao.substringBefore("suspend fun insertSubtasksIntoExistingRootAtomically")
                .trimEnd()
                .endsWith("@Transaction")
        )
        assertTrue(existing.contains("getById(parentTaskId)"))
        assertTrue(existing.contains("parent.parentTaskId != null || parent.isDone"))
        assertTrue(existing.contains("getSubtasks(parentTaskId).isNotEmpty()"))
        assertTrue(existing.contains("dueDate = parent.dueDate"))
        assertTrue(existing.contains("dueTime = parent.dueTime"))
        assertTrue(existing.contains("parentTaskId = parent.id"))
        assertTrue(existing.contains("subtaskOrder = index"))
        assertTrue(existing.contains("insertAll(children)"))

        assertTrue(newRoot.contains("val parentId = insert(parent)"))
        assertTrue(newRoot.contains("parentTaskId = parentId"))
        assertTrue(newRoot.contains("subtaskOrder = index"))
        assertTrue(newRoot.contains("insertAll(children)"))
    }

    @Test
    fun saveBecomesTerminalBeforeTransactionAndSchedulesOnlyNewRoot() {
        val source = homeSource()
        val save = source
            .substringAfter("private fun savePendingBreakdown")
            .substringBefore("private fun handleBreakdownDraftFailure")

        assertTrue(
            save.indexOf("breakdownDraftController.markSaving") <
                save.indexOf("BreakdownPersistenceCoordinator")
        )
        assertTrue(save.contains("insertSubtasksIntoExistingRootAtomically"))
        assertTrue(save.contains("insertNewRootWithSubtasksAtomically"))
        assertTrue(save.contains("ReminderHelper.scheduleReminderFromTask"))
        assertFalse(save.contains("plan.forEach"))
        assertTrue(save.contains("completeSaving"))
        assertTrue(save.contains("assistantSession.assistantSessionActive"))
        assertTrue(save.contains("BreakdownSaveResultCategory.PARTIAL_REMINDER_FAILURE"))
        assertTrue(save.contains("but I could not schedule its reminder."))
        assertTrue(save.contains("ExecutionOutcome.PARTIAL_SUCCESS"))
    }

    @Test
    fun newRootProposalIsNeutralAboutExistingTaskSearch() {
        val proposalBuilder = homeSource()
            .substringAfter("private fun buildBreakdownProposalSpeech")
            .substringBefore("private fun")
        val newRootSpeech = proposalBuilder
            .substringAfter("BreakdownDraftMode.NEW_ROOT ->")
            .substringBefore("null ->")
            .lowercase()

        assertTrue(newRootSpeech.contains("i propose creating"))
        assertFalse(newRootSpeech.contains("did not find"))
        assertFalse(newRootSpeech.contains("existing task"))
        assertFalse(newRootSpeech.contains("searched"))
        assertFalse(newRootSpeech.contains("queried"))
    }

    @Test
    fun modelContractsContainNoPrivateTaskIdentityAndNoPhraseDictionaryParsing() {
        val prompt = LaptopAgentClient.BREAKDOWN_FOLLOW_UP_SYSTEM_PROMPT
        val context = BreakdownFollowUpContext(
            state = BreakdownDraftState.WAITING_FOR_CONFIRMATION,
            mode = BreakdownDraftMode.EXISTING_ROOT,
            parentTitle = "Project",
            proposedSubtasks = listOf("Research", "Draft"),
            revision = 2
        ).toPromptText()
        val source = homeSource()
        val followUp = source
            .substringAfter("private fun handleBreakdownConfirmationFollowUp")
            .substringBefore("private fun interpretBreakdownFeedbackSemantically")

        assertFalse(context.contains("parentTaskId"))
        assertFalse(context.contains("candidate"))
        assertFalse(context.contains("987654321"))
        assertTrue(prompt.contains("semantically interpret"))
        assertTrue(prompt.contains("REVISE"))
        assertTrue(prompt.contains("2 to 5"))
        assertTrue(followUp.contains("BreakdownControlInterpreter.interpret"))
        assertTrue(followUp.contains("interpretBreakdownFeedbackSemantically"))
        assertFalse(followUp.contains("contains("))
        assertFalse(source.contains("normalized.contains(\"create them\")"))
        assertFalse(source.contains("normalized.contains(\"add them\")"))
        assertFalse(source.contains("normalized.contains(\"create new\")"))
        assertFalse(source.contains("Regex(\"create"))
    }

    @Test
    fun noSchemaMigrationWasAddedForBreakdownMilestone() {
        val database = File("src/main/java/com/example/myapplication/data/AppDatabase.kt")
            .readText()
        assertTrue(database.contains("version = 7"))
        assertFalse(database.contains("MIGRATION_7_8"))
    }

    private fun homeSource() =
        File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
}
