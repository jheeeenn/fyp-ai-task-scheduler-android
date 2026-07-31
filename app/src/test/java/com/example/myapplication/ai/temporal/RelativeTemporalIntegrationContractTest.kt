package com.example.myapplication.ai.temporal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RelativeTemporalIntegrationContractTest {
    private val home = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
    private val edit = File("src/main/java/com/example/myapplication/EditTaskActivity.kt").readText()
    private val dao = File("src/main/java/com/example/myapplication/data/TaskDao.kt").readText()

    @Test
    fun initialFlowKeepsOriginalUtteranceAndAuthoritativeSchedule() {
        val branch = home
            .substringAfter("ConversationRoute.CONTEXT_ACTION ->")
            .substringBefore("ConversationRoute.QUERY_READING_CONTROL ->")
        assertTrue(branch.contains("normalizedText = normalized"))
        assertFalse(branch.contains("normalizedText = conversationDecision.taskText"))
        assertTrue(branch.contains("date = calculationTask?.dueDate"))
        assertTrue(branch.contains("time = calculationTask?.dueTime"))
        assertTrue(branch.contains("currentProposal = null"))
        assertTrue(branch.contains("sameContextActionTaskSnapshot"))
    }

    @Test
    fun correctionUsesOneBoundedSemanticCallAndNeverGeneralConversation() {
        val handler = edit
            .substringAfter("private fun handleRelativeTemporalProposalInput(")
            .substringBefore("private fun processRelativeTemporalCorrection(")
        val correction = edit
            .substringAfter("private fun processRelativeTemporalCorrection(")
            .substringBefore("private suspend fun authoritativeTaskStillMatches")
        assertTrue(handler.contains("else -> processRelativeTemporalCorrection(normalized)"))
        assertTrue(correction.contains("relativeTemporalAgent.processRelativeTemporalCorrection(normalized)"))
        assertTrue(correction.contains("authoritativeTaskStillMatches()"))
        assertFalse(correction.contains("processEditCommand("))
        assertFalse(correction.contains("handleOneSentenceTemporalCommand("))
    }

    @Test
    fun authoritativeOriginalAndCurrentProposalRemainSeparate() {
        assertTrue(edit.contains("RelativeTemporalProposalSession("))
        assertTrue(edit.contains("authoritativeOriginalDate"))
        assertTrue(edit.contains("session.authoritativeOriginal"))
        assertTrue(edit.contains("session.currentProposal"))
        assertTrue(edit.contains("session.beginCorrection()"))
        assertTrue(edit.contains("session.isCurrent(token)"))
        assertTrue(edit.contains("session.applyCorrection(token"))
    }

    @Test
    fun confirmationUsesLatestRevisionAndConditionalAuthoritativeMutation() {
        val save = edit
            .substringAfter("private fun saveTask(expectedProposalRevision")
            .substringBefore("private fun confirmDeleteTask()")
        assertTrue(save.contains("proposalForConfirmation("))
        assertTrue(save.contains("expectedProposalRevision"))
        assertTrue(save.contains("authoritativeSnapshotMatches(existingTask)"))
        assertTrue(save.contains("updateTaskAndSubtasksIfAuthoritativeSnapshotMatches("))
        assertTrue(save.contains("markSaved(it.revision)"))
        assertTrue(dao.contains("WHERE id = :id"))
        assertTrue(dao.contains("dueDate IS :expectedDueDate"))
        assertTrue(dao.contains("dueTime IS :expectedDueTime"))
        assertTrue(dao.contains("isDone = :expectedIsDone"))
    }

    @Test
    fun remindersAndRoomStayBehindExplicitConfirmation() {
        val correction = edit
            .substringAfter("private fun processRelativeTemporalCorrection(")
            .substringBefore("private suspend fun authoritativeTaskStillMatches")
        assertFalse(correction.contains("updateTask"))
        assertFalse(correction.contains("ReminderHelper"))

        val save = edit
            .substringAfter("private fun saveTask(expectedProposalRevision")
            .substringBefore("private fun confirmDeleteTask()")
        val update = save.indexOf("updateTaskAndSubtasksIfAuthoritativeSnapshotMatches(")
        val reminderCancel = save.indexOf("ReminderHelper.cancelReminder(")
        assertTrue(update >= 0)
        assertTrue(update < reminderCancel)
    }

    @Test
    fun cancellationDiscardsProposalWithoutMutation() {
        val handler = edit
            .substringAfter("private fun handleRelativeTemporalProposalInput(")
            .substringBefore("private fun processRelativeTemporalCorrection(")
        val cancellation = handler
            .substringAfter("isRelativeCancellationCommand(normalized)")
            .substringBefore("isRelativeRepeatCommand(normalized)")
        assertTrue(cancellation.contains("session.cancel()"))
        assertTrue(cancellation.contains("finish()"))
        assertFalse(cancellation.contains("updateTask"))
        assertFalse(cancellation.contains("ReminderHelper"))
    }

    @Test
    fun voiceAndTypedCorrectionsShareTheSameFinalTextHandler() {
        assertTrue(edit.contains("assistantSession.submitTypedText(typedText, clearConversation = false)"))
        assertTrue(edit.contains("override fun onAssistantFinalText(text: String)"))
        assertTrue(edit.contains("handleVoiceInput(text)"))
        assertTrue(edit.contains("btnTalkAssistant.setOnLongClickListener"))
    }

    @Test
    fun spokenProposalContainsTitleExactScheduleBoundaryAndQuestion() {
        val renderer = File(
            "src/main/java/com/example/myapplication/ai/temporal/RelativeTemporalSpeechRenderer.kt"
        ).readText()
        assertTrue(renderer.contains("taskTitle"))
        assertTrue(renderer.contains("spokenDate(schedule.date)"))
        assertTrue(renderer.contains("schedule.time"))
        assertTrue(renderer.contains("This crosses into another day."))
        assertTrue(renderer.contains("Should I save the change?"))
    }

    @Test
    fun noPhraseSpecificRelativeTimeRouterWasAdded() {
        val calculator = File(
            "src/main/java/com/example/myapplication/ai/temporal/RelativeTemporalChangeCalculator.kt"
        ).readText()
        val relativeHandler = edit
            .substringAfter("private fun processRelativeTemporalCorrection(")
            .substringBefore("private suspend fun authoritativeTaskStillMatches")
        assertFalse(calculator.contains("normalizedText"))
        assertFalse(relativeHandler.contains("Regex("))
        assertFalse(relativeHandler.contains("when (normalized"))
        assertTrue(relativeHandler.contains("processRelativeTemporalCorrection(normalized)"))
    }
}
