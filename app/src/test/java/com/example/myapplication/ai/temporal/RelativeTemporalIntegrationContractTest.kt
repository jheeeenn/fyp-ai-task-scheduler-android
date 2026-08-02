package com.example.myapplication.ai.temporal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RelativeTemporalIntegrationContractTest {
    private val home = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
    private val edit = File("src/main/java/com/example/myapplication/EditTaskActivity.kt").readText()
    private val proposalSession = File(
        "src/main/java/com/example/myapplication/ai/temporal/RelativeTemporalProposalSession.kt"
    ).readText()
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
        assertTrue(
            correction.contains(
                "relativeTemporalAgent.processRelativeTemporalCorrection(\n                    normalized,\n                    correctionContext"
            )
        )
        assertTrue(correction.contains("session.correctionContext()"))
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
        assertTrue(edit.contains("initialSemanticProposal = initialRelativeTemporalSemanticProposal"))
        assertTrue(proposalSession.contains("currentSemanticProposal"))
        assertTrue(proposalSession.contains("currentSemanticProposal = semanticProposal"))
        assertTrue(edit.contains("session.beginCorrection()"))
        assertTrue(edit.contains("session.isCurrent(token)"))
        assertTrue(edit.contains("session.applyCorrection(\n                                    token"))
    }

    @Test
    fun confirmationUsesLatestRevisionAndConditionalAuthoritativeMutation() {
        val save = edit
            .substringAfter("private fun saveTask(expectedProposalRevision")
            .substringBefore("private fun confirmDeleteTask()")
        assertTrue(save.contains("session.claimSave("))
        assertTrue(save.contains("expectedProposalRevision"))
        assertTrue(save.contains("expectedTaskId = claimedTaskId"))
        assertTrue(save.contains("claimedSession?.isCurrentSaveClaim(saveClaim)"))
        assertTrue(save.contains("updateTaskAndSubtasksIfAuthoritativeSnapshotMatches("))
        assertTrue(save.contains("claimedSession?.completeSave(saveClaim) == true"))
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
        val complete = save.indexOf("claimedSession?.completeSave(saveClaim) == true")
        val savedLog = save.indexOf("state=SAVED")
        val reminderCancel = save.indexOf("ReminderHelper.cancelReminder(")
        val reminderSchedule = save.indexOf("ReminderHelper.scheduleReminderFromTask(")
        assertTrue(update >= 0)
        assertTrue(update < complete)
        assertTrue(complete < savedLog)
        assertTrue(savedLog < reminderCancel)
        assertTrue(reminderCancel < reminderSchedule)
    }

    @Test
    fun homeRechecksTheSameAssistantRequestAfterExtractionAndBeforeNavigation() {
        val branch = home
            .substringAfter("ConversationRoute.CONTEXT_ACTION ->")
            .substringBefore("ConversationRoute.QUERY_READING_CONTROL ->")
        val extraction = branch.indexOf("agentOrchestrator.processContextAction(")
        val afterExtraction = branch.indexOf("\"AFTER_EXTRACTION\"", extraction)
        val calculationFetch = branch.indexOf("taskDao.getById(reResolvedTaskId)")
        val afterCalculation = branch.indexOf("\"AFTER_CALCULATION\"", calculationFetch)
        val openingFetch = branch.indexOf("val openingTask =")
        val beforeOpenAfterFetch = branch.indexOf("\"BEFORE_OPEN\"", openingFetch)
        val commit = branch.indexOf("conversationOrchestrator.commitFinalDecision(", openingFetch)
        val open = branch.indexOf("openContextActionEditScreen(")
        val beforeCommit = branch.substring(0, commit).lastIndexOf("\"BEFORE_OPEN\"")
        val beforeNavigation = branch.substring(0, open).lastIndexOf("\"BEFORE_OPEN\"")

        assertTrue(extraction >= 0)
        assertTrue(afterExtraction > extraction)
        assertTrue(afterCalculation > calculationFetch)
        assertTrue(beforeOpenAfterFetch > openingFetch)
        assertTrue(beforeCommit in 0 until commit)
        assertTrue(beforeNavigation in (commit + 1) until open)
        assertTrue(branch.contains("isRelativeTemporalRequestCurrent(\n"))
        assertTrue(home.contains("reason=NEWER_ASSISTANT_REQUEST"))

        val navigation = home
            .substringAfter("private suspend fun openContextActionEditScreen(")
            .substringBefore("private fun todayDateString()")
        val speech = navigation.indexOf("speakObservationThenRun(")
        val activityStart = navigation.indexOf("startActivity(editIntent)")
        assertTrue(navigation.indexOf("isRelativeTemporalRequestCurrent(") in 0 until speech)
        assertTrue(
            navigation.lastIndexOf("isRelativeTemporalRequestCurrent(") in
                (speech + 1) until activityStart
        )
    }

    @Test
    fun saveCoroutineUsesOnlyImmutableClaimedScheduleAndTitle() {
        val save = edit
            .substringAfter("private fun saveTask(expectedProposalRevision")
            .substringBefore("private fun failRelativeTemporalSaveClaim(")
        val coroutine = save.substringAfter("lifecycleScope.launch {")

        assertTrue(save.contains("val claimedTitle = saveClaim?.title ?: proposedTitle"))
        assertTrue(save.contains("val claimedDate = claimedSchedule.date"))
        assertTrue(save.contains("val claimedTime = claimedSchedule.time"))
        assertTrue(coroutine.contains("newTitle = claimedTitle"))
        assertTrue(coroutine.contains("newDueDate = claimedDate"))
        assertTrue(coroutine.contains("newDueTime = claimedTime"))
        assertFalse(coroutine.contains("selectedDate"))
        assertFalse(coroutine.contains("selectedTime"))
        assertFalse(coroutine.contains("etTaskTitle"))
    }

    @Test
    fun buttonAndVoiceSaveShareClaimAndManualSyncIsBlockedWhileSaving() {
        assertTrue(edit.contains("btnSaveTask.setOnClickListenerWithHaptic"))
        assertTrue(edit.contains("saveTask(relativeTemporalSession?.revision)"))
        val relativeHandler = edit
            .substringAfter("private fun handleRelativeTemporalProposalInput(")
            .substringBefore("private fun processRelativeTemporalCorrection(")
        assertTrue(relativeHandler.contains("saveTask(session.revision)"))

        val synchronization = edit
            .substringAfter("private fun synchronizeRelativeProposalFromUi()")
            .substringBefore("private fun advanceTemporalClarification()")
        assertTrue(
            synchronization.contains(
                "session.state != RelativeTemporalProposalState.ACTIVE"
            )
        )
        assertTrue(edit.contains("state=SAVING action=IGNORED"))
    }

    @Test
    fun failedConditionalUpdateCannotLogSavedOrTouchReminders() {
        val save = edit
            .substringAfter("private fun saveTask(expectedProposalRevision")
            .substringBefore("private fun failRelativeTemporalSaveClaim(")
        val failedUpdate = save
            .substringAfter("if (!updated) {")
            .substringBefore("val saveStateCompleted")

        assertTrue(failedUpdate.contains("failRelativeTemporalSaveClaim("))
        assertTrue(failedUpdate.contains("return@launch"))
        assertFalse(failedUpdate.contains("state=SAVED"))
        assertFalse(failedUpdate.contains("ReminderHelper"))
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
    fun exactConversationExitCancelsActiveProposalWithoutCallingCorrectionAgent() {
        val handler = edit
            .substringAfter("private fun handleRelativeTemporalProposalInput(")
            .substringBefore("private fun processRelativeTemporalCorrection(")
        val exitBranch = handler
            .substringAfter("if (isConversationExitCommand(normalized)) {")
            .substringBefore("if (relativeTemporalCorrectionInFlight)")
        val exitPolicy = edit
            .substringAfter("private fun isConversationExitCommand(normalized: String)")
            .substringBefore("private fun applySpokenDate(")

        assertTrue(exitPolicy.contains("\"that's all\""))
        assertTrue(exitBranch.contains("session.cancel()"))
        assertTrue(exitBranch.contains("endAssistantConversation()"))
        assertFalse(exitBranch.contains("processRelativeTemporalCorrection"))
        assertFalse(exitBranch.contains("updateTask"))
        assertFalse(exitBranch.contains("ReminderHelper"))
        assertTrue(
            handler.indexOf("isConversationExitCommand(normalized)") <
                handler.indexOf("if (relativeTemporalCorrectionInFlight)")
        )
        assertTrue(
            handler.indexOf("isConversationExitCommand(normalized)") <
                handler.indexOf("else -> processRelativeTemporalCorrection(normalized)")
        )
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
        assertTrue(relativeHandler.contains("processRelativeTemporalCorrection(\n                    normalized,"))
    }

    @Test
    fun parsedShapeAndCanonicalizationDiagnosticsAreSanitizedAndOrdered() {
        val orchestrator = File(
            "src/main/java/com/example/myapplication/ai/agent/AgentOrchestrator.kt"
        ).readText()
        val initial = orchestrator
            .substringAfter("suspend fun processContextAction(")
            .substringBefore("suspend fun processRelativeTemporalCorrection(")
        val correction = orchestrator
            .substringAfter("suspend fun processRelativeTemporalCorrection(")
            .substringBefore("suspend fun processRoutine(")

        assertTrue(initial.indexOf("contextActionExtractionParser.parse") < initial.indexOf("logParsedRelativeTemporalShape"))
        assertTrue(initial.indexOf("logParsedRelativeTemporalShape") < initial.indexOf("validateWithReport"))
        assertTrue(correction.indexOf("relativeTemporalCorrectionParser.parse") < correction.indexOf("logParsedRelativeTemporalShape"))
        assertTrue(correction.indexOf("logParsedRelativeTemporalShape") < correction.indexOf("validateWithReport"))
        assertTrue(orchestrator.contains("RELATIVE_TEMPORAL_PARSED"))
        assertTrue(orchestrator.contains("RELATIVE_TEMPORAL_CANONICALIZED"))
        assertTrue(orchestrator.contains("replacementDatePresent="))
        assertTrue(orchestrator.contains("replacementTimePresent="))
        assertTrue(orchestrator.contains("correctionRelation="))
        assertTrue(orchestrator.contains("mappedRelativeBase="))
        assertTrue(orchestrator.contains("RELATIVE_TEMPORAL_CORRECTION_CONTEXT"))
        assertTrue(orchestrator.contains("preservedRelativeBase="))
        assertTrue(orchestrator.contains("CORRECTION_RECONSTRUCTED"))
        assertTrue(orchestrator.contains("RELATIVE_TEMPORAL_REPAIR"))
        assertTrue(orchestrator.contains("RELATIVE_TEMPORAL_REPAIR_CANDIDATES"))
        assertTrue(orchestrator.contains("RELATIVE_TEMPORAL_REPAIR_CHOICE"))
        assertTrue(orchestrator.contains("stage=CORRECTION attempt=1"))
        listOf("REQUESTED", "ACCEPTED", "REJECTED", "PARSE_FAILED", "NOT_ELIGIBLE").forEach {
            assertTrue(orchestrator.contains(it))
        }
        val repairLog = orchestrator
            .substringAfter("private fun logCorrectionRepair(")
            .substringBefore("suspend fun processRoutine(")
        assertFalse(repairLog.contains("originalUserText"))
        assertFalse(repairLog.contains("rejectedResponse"))
        assertFalse(repairLog.contains("replacementDateText"))
        assertFalse(repairLog.contains("replacementTimeText"))
        assertTrue(orchestrator.contains("fields=${'$'}{changedFields.joinToString(\",\")}"))

        val parsedLog = orchestrator
            .substringAfter("\"RELATIVE_TEMPORAL_PARSED\"")
            .substringBefore("private fun logCanonicalization")
        assertFalse(parsedLog.contains("replacementDateText="))
        assertFalse(parsedLog.contains("replacementTimeText="))
        assertFalse(parsedLog.contains("rawContent"))
        assertFalse(parsedLog.contains("normalizedText"))
    }
}
