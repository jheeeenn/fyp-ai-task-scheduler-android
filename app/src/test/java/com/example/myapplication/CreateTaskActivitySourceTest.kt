package com.example.myapplication

import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateTaskDialogState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CreateTaskActivitySourceTest {
    private val source = File("src/main/java/com/example/myapplication/CreateTaskActivity.kt").readText()
    private val voiceHandler = source
        .substringAfter("private fun handleVoiceCommand")
        .substringBefore("private fun openDatePicker")
    private val moveHandler = source
        .substringAfter("private fun handleCreateDraftMove")
        .substringBefore("private fun handleFieldChange")
    private val saveBody = source
        .substringAfter("private fun saveTask")
        .substringBefore("private fun formatDateForSpeech")

    @Test
    fun assistantActivationResumesFromNativeDraftWithoutAutoSaving() {
        val activation = source
            .substringAfter("speechProvider = TaskFormControlSpeechRenderer::assistant")
            .substringBefore("btnTalkAssistant.setOnLongClickListener")
        val resume = source
            .substringAfter("private fun resumeCreateAssistantFromDraft")
            .substringBefore("private fun applyIncomingPrefill")
        val sessionStart = activation.indexOf("assistantSession.startSession()")
        val draftResume = activation.indexOf("resumeCreateAssistantFromDraft()")
        val completeResume = resume
            .substringAfter("CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION ->")
            .substringBefore("else -> Unit")

        assertTrue(activation.contains("resumeCreateAssistantFromDraft()"))
        assertTrue(sessionStart >= 0)
        assertTrue(sessionStart < draftResume)
        assertFalse(activation.contains("WAITING_FOR_TITLE"))
        assertTrue(resume.contains("etTaskTitle.text.toString().trim()"))
        assertTrue(resume.contains("CreateDraftResumePolicy.nextState"))
        assertTrue(resume.contains("WAITING_FOR_SAVE_CONFIRMATION"))
        assertTrue(completeResume.contains("assistantSession.expectConfirmation()"))
        assertTrue(completeResume.contains("promptHelper.askSaveTask(buildTaskSummary())"))
        assertTrue(resume.contains("CREATE_RESUME"))
        assertFalse(resume.contains("saveTask()"))
    }

    @Test
    fun voiceInputUsesAgentPrimaryResolutionAndCentralMoveHandler() {
        assertTrue(source.contains("private val createDraftMoveInterpreter = CreateDraftMoveInterpreter()"))
        assertTrue(source.contains("private lateinit var createDraftSemanticOrchestrator: CreateDraftSemanticOrchestrator"))
        assertTrue(voiceHandler.contains("createDraftSemanticOrchestrator.proposeLocal(normalized, capturedState)"))
        assertTrue(voiceHandler.contains("createDraftSemanticOrchestrator.resolveImmediate(localCandidate, capturedState)"))
        assertTrue(voiceHandler.contains("requestCreateDraftPrimary(normalized, capturedState, localCandidate)"))
        assertFalse(voiceHandler.contains("requestCreateDraftPrimary(rawCommand.trim()"))
        assertTrue(voiceHandler.contains("createDraftSemanticOrchestrator.resolve("))
        assertTrue(voiceHandler.contains("handleCreateDraftMove(result.move)"))
        assertTrue(moveHandler.contains("CreateDraftMove.ConfirmSave ->"))
        assertTrue(moveHandler.contains("is CreateDraftMove.ChangeField ->"))
        assertTrue(moveHandler.contains("CreateDraftMove.Unknown ->"))
    }

    @Test
    fun createWorkflowUsesBoundedPrimaryConversationAgentWithoutTaskAgent() {
        assertTrue(source.contains("ConversationAgentClient(this)"))
        assertTrue(source.contains("CreateDraftSemanticOrchestrator("))
        listOf(
            "LaptopAgentClient",
            "AgentOrchestrator",
            "TaskAgentClient"
        ).forEach { forbidden -> assertFalse(source.contains(forbidden)) }
        assertFalse(source.contains("ConversationOrchestrator("))
    }

    @Test
    fun saveTaskRemainsTheOnlyRoomInsertionAuthority() {
        assertEquals(1, Regex("dao\\.insert\\(").findAll(source).count())
        assertTrue(saveBody.contains("dao.insert("))
        assertTrue(saveBody.contains("ReminderHelper.scheduleReminderFromTask("))
        assertTrue(moveHandler.contains("saveTask()"))
    }

    @Test
    fun unknownConfirmationDoesNotApplyOrOverwriteTheTitle() {
        val unknownBranch = moveHandler
            .substringAfter("CreateDraftMove.Unknown ->")
            .substringBefore("}")
        assertTrue(unknownBranch.contains("recoverFromUnknownMove()"))
        assertFalse(unknownBranch.contains("applyTitle("))
    }

    @Test
    fun titleChangesUseStructuredFieldAndExtractedValue() {
        val fieldChangeBody = source
            .substringAfter("private fun handleFieldChange")
            .substringBefore("private fun handleProvidedField")
        val titleBody = source
            .substringAfter("private fun applyProvidedTitle")
            .substringBefore("private fun applyProvidedDate")

        assertTrue(fieldChangeBody.contains("CreateTaskDialogState.WAITING_FOR_TITLE"))
        assertTrue(fieldChangeBody.contains("responseManager.askChangeTitle()"))
        assertTrue(fieldChangeBody.contains("handleProvidedField(field, value)"))
        assertTrue(titleBody.contains("applyTitle(value)"))
        assertFalse(titleBody.contains("rawCommand"))
    }

    @Test
    fun initialExplicitTitleCommandContinuesToTheNextMissingStep() {
        assertFalse(
            isCreateDraftFieldReplacement(
                field = CreateDraftField.TITLE,
                state = CreateTaskDialogState.WAITING_FOR_TITLE,
                pendingReplacementField = null,
                hasTitle = false,
                hasSelectedDate = false,
                hasSelectedTime = false
            )
        )

        val fieldChangeBody = source
            .substringAfter("private fun handleFieldChange")
            .substringBefore("private fun handleProvidedField")
        val titleBody = source
            .substringAfter("private fun applyProvidedTitle")
            .substringBefore("private fun applyProvidedDate")
        val confirmationBody = source
            .substringAfter("private fun returnToSaveConfirmation")
            .substringBefore("private fun isDraftCompleteForSaveConfirmation")

        assertTrue(fieldChangeBody.contains("pendingReplacementField = if (replacingField) field else null"))
        assertTrue(titleBody.contains("moveToNextMissingStep()"))
        assertTrue(confirmationBody.contains("if (!isDraftCompleteForSaveConfirmation())"))
        assertTrue(confirmationBody.indexOf("moveToNextMissingStep()") < confirmationBody.indexOf("WAITING_FOR_SAVE_CONFIRMATION"))
    }

    @Test
    fun explicitTitleCommandAtSaveConfirmationRemainsAReplacement() {
        assertTrue(
            isCreateDraftFieldReplacement(
                field = CreateDraftField.TITLE,
                state = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION,
                pendingReplacementField = null,
                hasTitle = true,
                hasSelectedDate = true,
                hasSelectedTime = true
            )
        )

        val titleBody = source
            .substringAfter("private fun applyProvidedTitle")
            .substringBefore("private fun applyProvidedDate")
        assertTrue(titleBody.contains("returnToSaveConfirmation(CreateDraftField.TITLE)"))
    }

    @Test
    fun pendingMarkerPreservesTwoTurnTitleReplacement() {
        val firstTurnIsReplacement = isCreateDraftFieldReplacement(
            field = CreateDraftField.TITLE,
            state = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION,
            pendingReplacementField = null,
            hasTitle = true,
            hasSelectedDate = true,
            hasSelectedTime = true
        )
        assertTrue(firstTurnIsReplacement)

        assertTrue(
            isCreateDraftFieldReplacement(
                field = CreateDraftField.TITLE,
                state = CreateTaskDialogState.WAITING_FOR_TITLE,
                pendingReplacementField = CreateDraftField.TITLE,
                hasTitle = true,
                hasSelectedDate = true,
                hasSelectedTime = true
            )
        )

        val fieldChangeBody = source
            .substringAfter("private fun handleFieldChange")
            .substringBefore("private fun handleProvidedField")
        val providedFieldBody = source
            .substringAfter("private fun handleProvidedField")
            .substringBefore("private fun applyProvidedTitle")
        assertTrue(fieldChangeBody.contains("CreateTaskDialogState.WAITING_FOR_TITLE"))
        assertTrue(providedFieldBody.contains("pendingReplacementField == field"))
    }

    @Test
    fun dateAndTimeChangesRetainAndroidTemporalResolution() {
        assertTrue(source.contains("applySpokenDate(value, replacingConstraint = replacingField)"))
        assertTrue(source.contains("applySpokenTime(timeCandidate, replacingConstraint = replacingField)"))
        assertTrue(source.contains("TemporalExpressionResolver()"))
        assertTrue(source.contains("TemporalActionPolicy.evaluate"))
    }

    @Test
    fun primaryAgentResultsUseTheSameAndroidMoveHandler() {
        val primaryBody = source
            .substringAfter("private fun requestCreateDraftPrimary")
            .substringBefore("private fun logCreateMoveResolution")
        assertTrue(primaryBody.contains("createDraftSemanticOrchestrator.resolve("))
        assertTrue(primaryBody.contains("handleCreateDraftMove(result.move)"))
        assertFalse(primaryBody.contains("applyTitle("))
        assertFalse(primaryBody.contains("applySpokenDate("))
        assertFalse(primaryBody.contains("applySpokenTime("))
        assertFalse(primaryBody.contains("saveTask()"))
    }

    @Test
    fun semanticRequestDisablesAndReliablyRestoresMutableControls() {
        val primaryBody = source
            .substringAfter("private fun requestCreateDraftPrimary")
            .substringBefore("private fun logCreateMoveResolution")
        val controlsBody = source
            .substringAfter("private fun setCreateDraftControlsEnabled")
            .substringBefore("private fun invalidateCreateDraftResolution")
        val finallyBody = primaryBody.substringAfter("finally {")

        listOf("btnSaveTask", "btnTalkAssistant").forEach { control ->
            assertTrue(source.contains("private lateinit var $control: Button"))
            assertTrue(controlsBody.contains("$control.isEnabled"))
        }
        listOf("dateInfoGroup", "timeInfoGroup").forEach { control ->
            assertTrue(source.contains("private lateinit var $control: View"))
            assertTrue(controlsBody.contains("$control.isEnabled"))
        }
        assertTrue(controlsBody.contains("etTaskTitle.isEnabled"))
        assertTrue(controlsBody.contains("!isFinishing"))
        assertTrue(controlsBody.contains("!isDestroyed"))
        assertTrue(controlsBody.contains("!isCreateTaskExitPending"))
        assertTrue(controlsBody.contains("!isSavingTask"))
        assertTrue(controlsBody.contains("etTaskTitle.isEnabled = canEnable"))
        assertTrue(controlsBody.contains("btnSaveTask.isEnabled = canEnable"))
        assertTrue(controlsBody.contains("dateInfoGroup.isEnabled = canEnable"))
        assertTrue(controlsBody.contains("timeInfoGroup.isEnabled = canEnable"))
        assertTrue(controlsBody.contains("btnTalkAssistant.isEnabled = canEnable"))
        assertFalse(controlsBody.contains("btnCancelTask"))
        assertFalse(controlsBody.contains("btnGoHome"))
        assertTrue(
            primaryBody.indexOf("setCreateDraftControlsEnabled(false)") <
                    primaryBody.indexOf("lifecycleScope.launch")
        )
        assertTrue(finallyBody.contains("setCreateDraftControlsEnabled(true)"))
        assertTrue(primaryBody.contains("category=STALE_DRAFT_RESULT_DISCARDED"))
        assertTrue(
            primaryBody.substringAfter("category=STALE_DRAFT_RESULT_DISCARDED")
                .contains("setCreateDraftControlsEnabled(true)")
        )
    }

    @Test
    fun cancelAndReturnHomeInvalidateSemanticRequestBeforeExistingActions() {
        val onCreateBody = source
            .substringAfter("override fun onCreate")
            .substringBefore("//function definitions")
        val cancelClickBody = onCreateBody
            .substringAfter("view = btnCancelTask")
            .substringBefore("view = btnGoHome")
        val homeClickBody = onCreateBody
            .substringAfter("view = btnGoHome")
            .substringBefore("view = btnTalkAssistant")

        assertTrue(cancelClickBody.contains("invalidateCreateDraftResolution()"))
        assertTrue(cancelClickBody.contains("if (!isSavingTask)"))
        assertTrue(
            cancelClickBody.indexOf("if (!isSavingTask)") <
                    cancelClickBody.indexOf("invalidateCreateDraftResolution()") &&
                    cancelClickBody.indexOf("invalidateCreateDraftResolution()") <
                    cancelClickBody.indexOf("handleCreateDraftMove(CreateDraftMove.Cancel)")
        )
        assertTrue(homeClickBody.contains("invalidateCreateDraftResolution()"))
        assertTrue(homeClickBody.contains("if (!isSavingTask)"))
        assertTrue(
            homeClickBody.indexOf("if (!isSavingTask)") <
                    homeClickBody.indexOf("invalidateCreateDraftResolution()") &&
                    homeClickBody.indexOf("invalidateCreateDraftResolution()") <
                    homeClickBody.indexOf("assistantSession.speakThenRun")
        )
    }

    @Test
    fun saveIsBlockedDuringResolutionAndDuplicateInsertionIsGuarded() {
        assertTrue(source.contains("private var isSavingTask = false"))
        assertTrue(saveBody.contains("if (isSavingTask || isResolvingCreateDraftMove) return"))
        assertTrue(saveBody.contains("isSavingTask = true"))
        assertTrue(
            saveBody.indexOf("InvalidPastSchedule") < saveBody.indexOf("isSavingTask = true")
        )
        assertTrue(
            saveBody.indexOf("isSavingTask = true") < saveBody.indexOf("lifecycleScope.launch")
        )
        assertTrue(saveBody.contains("isSavingTask = true\n        setCreateDraftControlsEnabled(false)"))
        assertTrue(saveBody.contains("category=INSERT_FAILED"))
        assertTrue(saveBody.contains("isSavingTask = false"))
        assertTrue(
            saveBody.indexOf("isSavingTask = false") <
                    saveBody.indexOf("setCreateDraftControlsEnabled(true)", saveBody.indexOf("isSavingTask = false"))
        )
        assertEquals(1, Regex("dao\\.insert\\(").findAll(source).count())
    }

    @Test
    fun saveCoroutineUsesOnlyImmutableValidatedSnapshots() {
        val coroutineBody = saveBody.substringAfter("lifecycleScope.launch")
        listOf(
            "finalTitle", "finalDate", "finalTime", "taskToInsert"
        ).forEach { snapshot ->
            assertTrue(saveBody.contains("val $snapshot ="))
            assertTrue(saveBody.indexOf("val $snapshot =") < saveBody.indexOf("lifecycleScope.launch"))
        }
        assertTrue(saveBody.contains("title = finalTitle"))
        assertTrue(saveBody.contains("dueDate = finalDate"))
        assertTrue(saveBody.contains("dueTime = finalTime"))
        assertTrue(coroutineBody.contains("dao.insert(taskToInsert)"))
        assertTrue(coroutineBody.contains("taskToInsert.copy(id = insertedId)"))
        assertTrue(coroutineBody.contains("ReminderHelper.scheduleReminderFromTask"))
        listOf(
            "selectedDate", "selectedTime", "selectedYear", "selectedMonth",
            "selectedDay", "selectedHour24", "selectedMinute"
        ).forEach { mutableField -> assertFalse(coroutineBody.contains(mutableField)) }
    }

    @Test
    fun agentCannotOverrideAndroidTemporalPolicyRejection() {
        val primaryBody = source
            .substringAfter("private fun requestCreateDraftPrimary")
            .substringBefore("private fun logCreateMoveResolution")
        val timeBody = source
            .substringAfter("private fun applyProvidedTime")
            .substringBefore("private fun applyUnspecifiedCorrection")

        assertTrue(primaryBody.contains("handleCreateDraftMove(result.move)"))
        assertFalse(primaryBody.contains("TemporalActionPolicy"))
        assertTrue(timeBody.contains("applySpokenTime(timeCandidate, replacingConstraint = replacingField)"))
        assertTrue(timeBody.contains("invalidTemporalTimeMessage()"))
        assertTrue(source.contains("TemporalActionPolicy.validateClarification"))
    }

    @Test
    fun stalePrimaryResultsAreDiscardedAndConcurrentInputIsIgnored() {
        val primaryBody = source
            .substringAfter("private fun requestCreateDraftPrimary")
            .substringBefore("private fun logCreateMoveResolution")
        assertTrue(source.contains("private var isResolvingCreateDraftMove = false"))
        assertTrue(source.contains("private var createDraftResolutionGeneration = 0L"))
        assertTrue(source.contains("private var createDraftRevision = 0L"))
        assertTrue(voiceHandler.contains("if (isResolvingCreateDraftMove)"))
        assertTrue(primaryBody.contains("val requestDraftRevision = createDraftRevision"))
        assertTrue(primaryBody.contains("requestGeneration != createDraftResolutionGeneration"))
        assertTrue(primaryBody.contains("dialogState != capturedState"))
        assertTrue(primaryBody.contains("requestDraftRevision != createDraftRevision"))
        assertTrue(primaryBody.contains("category=STALE_DRAFT_RESULT_DISCARDED"))
        assertTrue(
            primaryBody.indexOf("requestDraftRevision != createDraftRevision") <
                    primaryBody.indexOf("handleCreateDraftMove(result.move)")
        )
    }

    @Test
    fun authoritativeDraftMutationsAdvanceTheRevisionOnceThroughSharedHelpers() {
        val titleBody = source
            .substringAfter("private fun applyTitle")
            .substringBefore("private fun buildTaskSummary")
        val dateBody = source
            .substringAfter("private fun acceptExactDate")
            .substringBefore("private fun applySpokenTime")
        val timeBody = source
            .substringAfter("private fun acceptExactMinute")
            .substringBefore("private fun advanceTemporalClarification")
        val resetBody = source
            .substringAfter("private fun resetTaskDraftState")
            .substringBefore("private fun markCreateDraftChanged")
        val prefillBody = source
            .substringAfter("private fun applyIncomingPrefill")
            .substringBefore("private fun applyTemporalPrefill")
        val datePickerBody = source
            .substringAfter("private fun openDatePicker")
            .substringBefore("private fun openTimePicker")
        val timePickerBody = source
            .substringAfter("private fun openTimePicker")
            .substringBefore("private fun saveTask")
        val sessionCancellationBody = source
            .substringAfter("override fun onAssistantCancelled")
            .substringBefore("override fun onAssistantSessionStopped")

        assertEquals(1, Regex("markCreateDraftChanged\\(\\)").findAll(titleBody).count())
        assertEquals(1, Regex("markCreateDraftChanged\\(\\)").findAll(dateBody).count())
        assertEquals(1, Regex("markCreateDraftChanged\\(\\)").findAll(timeBody).count())
        assertEquals(1, Regex("markCreateDraftChanged\\(\\)").findAll(resetBody).count())
        assertTrue(source.contains("private fun markCreateDraftChanged()"))
        assertTrue(source.contains("createDraftRevision += 1"))
        assertTrue(prefillBody.contains("resetTaskDraftState()"))
        assertTrue(prefillBody.contains("applyTitle(prefillTitle)"))
        assertTrue(datePickerBody.contains("acceptExactDate("))
        assertFalse(datePickerBody.contains("markCreateDraftChanged()"))
        assertTrue(timePickerBody.contains("acceptExactMinute("))
        assertFalse(timePickerBody.contains("markCreateDraftChanged()"))
        assertTrue(sessionCancellationBody.contains("dialogState != CreateTaskDialogState.IDLE"))
        assertTrue(sessionCancellationBody.contains("markCreateDraftChanged()"))
    }

    @Test
    fun agentFailureRecoveryDoesNotDirectlyMutateDraft() {
        val primaryBody = source
            .substringAfter("private fun requestCreateDraftPrimary")
            .substringBefore("private fun logCreateMoveResolution")
        val unknownBranch = moveHandler
            .substringAfter("CreateDraftMove.Unknown ->")
            .substringBefore("}")
        assertTrue(primaryBody.contains("handleCreateDraftMove(result.move)"))
        assertTrue(unknownBranch.contains("recoverFromUnknownMove()"))
        assertFalse(primaryBody.contains("pendingTaskState.title ="))
        assertFalse(primaryBody.contains("selectedDate ="))
        assertFalse(primaryBody.contains("selectedTime ="))
    }

    @Test
    fun unspecifiedCorrectionNeverFallsBackToTitle() {
        val correctionBody = source
            .substringAfter("private fun applyUnspecifiedCorrection")
            .substringBefore("private fun returnToSaveConfirmation")
        val move = com.example.myapplication.voice.CreateDraftMoveInterpreter().interpret(
            "make it just 9 am",
            CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
        )

        assertTrue(move is com.example.myapplication.voice.CreateDraftMove.ApplyUnspecifiedCorrection)
        assertFalse(correctionBody.contains("applyTitle("))
        assertFalse(correctionBody.contains("CreateDraftField.TITLE"))
        assertTrue(correctionBody.contains("correctionNotUnderstood()"))
    }

    @Test
    fun cancellationAndDeterministicResponsesDoNotSave() {
        val cancelBody = source
            .substringAfter("private fun cancelCreateDraft")
            .substringBefore("private fun provideCreateDraftHelp")
        assertTrue(cancelBody.contains("resetTaskDraftState()"))
        assertTrue(cancelBody.contains("responseManager.cancelCreate()"))
        assertTrue(cancelBody.contains("finish()"))
        assertFalse(cancelBody.contains("saveTask()"))
        assertFalse(cancelBody.contains("dao.insert("))
        assertTrue(source.contains("responseManager.saveConfirmationHelp()"))
        assertTrue(source.contains("responseManager.correctionNotUnderstood()"))
    }

    @Test
    fun oldCompetingPhraseInterpretationWasRemoved() {
        listOf(
            "handleFollowUpInput",
            "tryApplyInlineCorrection",
            "extractInlineCorrectionValue",
            "looksLikeReasonableTitle",
            "private fun isYes",
            "private fun isNo"
        ).forEach { removed -> assertFalse(source.contains(removed)) }
    }

    @Test
    fun lifecycleStopInvalidatesAssistantWorkWithoutClearingNativeDraft() {
        val onStop = source
            .substringAfter("override fun onStop()")
            .substringBefore("private fun clearTransientCreateAssistantState")
        val transientClear = source
            .substringAfter("private fun clearTransientCreateAssistantState")
            .substringBefore("override fun onAssistantTypedInputRequested")

        assertTrue(onStop.contains("if (!isChangingConfigurations)"))
        assertTrue(onStop.contains("invalidateCreateDraftResolution()"))
        assertTrue(onStop.contains("assistantSession.stopForLifecycle()"))
        assertTrue(onStop.contains("clearTransientCreateAssistantState()"))
        assertTrue(
            onStop.indexOf("invalidateCreateDraftResolution()") <
                onStop.indexOf("assistantSession.stopForLifecycle()")
        )
        assertTrue(transientClear.contains("dialogState = CreateTaskDialogState.IDLE"))
        assertFalse(transientClear.contains("etTaskTitle.setText"))
        assertFalse(transientClear.contains("selectedDate ="))
        assertFalse(transientClear.contains("selectedTime ="))
        assertFalse(transientClear.contains("pendingTaskState.clear()"))
        assertFalse(onStop.contains("isSavingTask = false"))
        assertFalse(onStop.contains("isCreateTaskExitPending = false"))
    }

    @Test
    fun delayedPrefillIsCancelledOnStopAndRescheduledWhileUnconsumed() {
        val runnable = source
            .substringAfter("private val incomingPrefillRunnable")
            .substringBefore("private var dialogState")
        val onStart = source
            .substringAfter("override fun onStart()")
            .substringBefore("override fun onStop()")
        val onStop = source
            .substringAfter("override fun onStop()")
            .substringBefore("private fun scheduleIncomingPrefill")
        val schedule = source
            .substringAfter("private fun scheduleIncomingPrefill")
            .substringBefore("private fun clearTransientCreateAssistantState")

        assertTrue(runnable.contains("canRunCreateAssistantCallback()"))
        assertTrue(runnable.contains("applyIncomingPrefill()"))
        assertTrue(onStop.contains("removeCallbacks(incomingPrefillRunnable)"))
        assertTrue(onStart.contains("scheduleIncomingPrefill()"))
        assertTrue(schedule.contains("if (hasConsumedPrefill || isFinishing || isDestroyed) return"))
        assertTrue(schedule.contains("postDelayed(incomingPrefillRunnable, 1500L)"))
        assertFalse(onStop.contains("hasConsumedPrefill = true"))
        assertFalse(onStop.contains("clearPrefillExtras()"))
    }

    @Test
    fun learnedTimeLookupMustRemainForegroundAndCurrentBeforeSpeaking() {
        val lookup = source
            .substringAfter("private fun moveToNextMissingStep()")
            .substringBefore("private fun String.resolvedTimeMinute")
        val guard = source
            .substringAfter("private fun isCurrentLearnedTimeRequest")
            .substringBefore("private fun String.resolvedTimeMinute")
        val learnedResult = lookup.indexOf("timePreferenceLearner.getLearnedTimeForPhrase")
        val staleGuard = lookup.indexOf("if (!isCurrentLearnedTimeRequest(", learnedResult)
        val suggestion = lookup.indexOf("suggestedLearnedTime =", staleGuard)
        val speech = lookup.indexOf("speakAndContinueListening(", staleGuard)

        assertTrue(lookup.contains("val requestGeneration = createDraftResolutionGeneration"))
        assertTrue(lookup.contains("val requestDraftRevision = createDraftRevision"))
        assertTrue(lookup.contains("val requestDialogState = dialogState"))
        assertTrue(learnedResult >= 0)
        assertTrue(staleGuard > learnedResult)
        assertTrue(suggestion > staleGuard)
        assertTrue(speech > staleGuard)
        assertTrue(guard.contains("Lifecycle.State.STARTED"))
        assertTrue(guard.contains("!isFinishing"))
        assertTrue(guard.contains("!isDestroyed"))
        assertTrue(guard.contains("requestGeneration == createDraftResolutionGeneration"))
        assertTrue(guard.contains("requestDraftRevision == createDraftRevision"))
        assertTrue(guard.contains("requestDialogState == dialogState"))
        assertTrue(guard.contains("semanticPhrase == pendingSemanticTimePhrase"))
    }
}
