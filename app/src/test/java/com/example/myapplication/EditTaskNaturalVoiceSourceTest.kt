package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EditTaskNaturalVoiceSourceTest {
    private val source =
        File("src/main/java/com/example/myapplication/EditTaskActivity.kt").readText()
    private val createSource =
        File("src/main/java/com/example/myapplication/CreateTaskActivity.kt").readText()

    @Test
    fun oneSentenceTemporalCommandsRunBeforeGenericEditHelp() {
        val voice = source
            .substringAfter("private fun handleVoiceInput(text: String)")
            .substringBefore("private fun repeatPendingTemporalPrompt()")
        val bounded = voice.indexOf("handleOneSentenceTemporalCommand(normalized)")
        val genericHelp = voice.indexOf(
            "Please return to the main assistant for a new command"
        )

        assertTrue(bounded >= 0)
        assertTrue(bounded < genericHelp)
    }

    @Test
    fun validOneSentenceChangeAsksForSaveButNeverSavesDirectly() {
        val handler = source
            .substringAfter("private fun handleOneSentenceTemporalCommand(")
            .substringBefore("private fun enterTemporalCollection(")

        assertTrue(handler.contains("EditTemporalCommandPolicy.resolve("))
        assertTrue(handler.contains("applyTemporalResolution("))
        assertTrue(handler.contains("askToSaveChanges()"))
        assertFalse(handler.contains("saveTask()"))
        assertFalse(handler.contains("dao.updateTask("))
    }

    @Test
    fun saveRemainsBehindExplicitVoiceConfirmation() {
        val voice = source
            .substringAfter("private fun handleVoiceInput(text: String)")
            .substringBefore("private fun repeatPendingTemporalPrompt()")
        val confirmation = voice
            .substringAfter("if (waitingForSaveConfirmation)")
            .substringBefore("// If we already asked what field value to set")

        assertTrue(confirmation.contains("isYes(normalized)"))
        assertTrue(confirmation.contains("saveTask()"))
        assertTrue(source.contains("waitingForSaveConfirmation = true"))
    }

    @Test
    fun emptyAssistantRescheduleEntersFocusedDateOrTimeCollection() {
        val initialization = source
            .substringAfter("val temporalChanged = applyProposedTemporalChange(")
            .substringBefore("private fun openDatePicker()")
        val pendingValue = source
            .substringAfter("EditFieldTarget.DATE_OR_TIME -> {")
            .substringBefore("EditFieldTarget.NONE -> false")

        assertTrue(initialization.contains("rescheduleCollectionRequired"))
        assertTrue(initialization.contains("enterTemporalCollection(EditTemporalTarget.DATE_OR_TIME)"))
        assertTrue(source.contains("What date or time would you like to use?"))
        assertTrue(pendingValue.contains("applyProposedTemporalChange("))
        assertTrue(pendingValue.contains("askToSaveChanges()"))
    }

    @Test
    fun assistantTitlePrefillUsesExistingSingleSaveConfirmationPath() {
        val initialization = source
            .substringAfter("val titlePrefillChanged = isTitlePrefillChanged(")
            .substringBefore("private fun openDatePicker()")
        val confirmation = initialization.indexOf(
            "hasPendingPrefillChange -> askToSaveChanges()"
        )
        val genericIntro = initialization.indexOf("responseManager.editIntro(")
        val askToSave = source
            .substringAfter("private fun askToSaveChanges()")
            .substringBefore("private fun authoritativeSnapshotMatches")

        assertTrue(initialization.contains("etTaskTitle.setText(prefillTitle)"))
        assertTrue(initialization.contains("titlePrefillChanged || temporalChanged"))
        assertTrue(initialization.contains("EDIT_ASSISTANT_PREFILL"))
        assertTrue(initialization.contains("next=\$prefillNextState"))
        assertTrue(confirmation >= 0)
        assertTrue(confirmation < genericIntro)
        assertEquals(1, initialization.split("askToSaveChanges()").size - 1)
        assertTrue(askToSave.contains("waitingForSaveConfirmation = true"))
        assertTrue(askToSave.contains("assistantSession.expectConfirmation()"))
        assertTrue(askToSave.contains("promptHelper.askSaveChanges(buildEditSummary())"))
        assertFalse(initialization.contains("saveTask()"))
    }

    @Test
    fun titlePrefillChangeDetectionUsesTrimmedAuthoritativeTitle() {
        assertTrue(isTitlePrefillChanged("leaving home", "leave home"))
        assertFalse(isTitlePrefillChanged(" leave home ", "leave home"))
        assertFalse(isTitlePrefillChanged("", "leave home"))
        assertFalse(isTitlePrefillChanged(null, "leave home"))
    }

    @Test
    fun editAllowsPastPickerDatesWhileCreateRemainsFutureOnly() {
        val editPicker = source
            .substringAfter("private fun openDatePicker()")
            .substringBefore("private fun openTimePicker()")
        val createPicker = createSource
            .substringAfter("private fun openDatePicker()")
            .substringBefore("private fun openTimePicker()")

        assertFalse(editPicker.contains("datePicker.minDate"))
        assertTrue(createPicker.contains("datePicker.minDate"))
    }

    @Test
    fun overdueTitleOnlyEditRetainsScheduleAndUsesRoomMutation() {
        val save = source
            .substringAfter("private fun saveTask(expectedProposalRevision")
            .substringBefore("private fun markEditSaveFailed(")

        assertTrue(save.contains("ExactTemporalSchedule(selectedDate, selectedTime)"))
        assertTrue(save.contains("val claimedSchedule = saveClaim?.schedule ?: ordinaryScheduleSnapshot"))
        assertTrue(save.contains("dao.updateTask(claimedTaskId, claimedTitle, claimedDate, claimedTime)"))
        assertTrue(save.contains("schedulePast=true result=ALLOWED_EXISTING_TASK_EDIT"))
        assertFalse(save.contains("InvalidPastSchedule"))
        assertFalse(save.contains("responseManager.pastDateTime()"))
    }

    @Test
    fun confirmationKeepsPanelVisibleUntilAsyncSaveSucceeds() {
        val voice = source
            .substringAfter("private fun handleVoiceInput(text: String)")
            .substringBefore("private fun handleRelativeTemporalProposalInput(")
        val directSave = voice
            .substringAfter("if (isSaveCommand(normalized))")
            .substringBefore("if (isConversationExitCommand(normalized))")
        val affirmative = voice
            .substringAfter("isYes(normalized) ->")
            .substringBefore("isNo(normalized) ->")
        val relative = source
            .substringAfter("private fun handleRelativeTemporalProposalInput(")
            .substringBefore("private fun processRelativeTemporalCorrection(")
        val relativeSave = relative
            .substringAfter("isSaveCommand(normalized) || isYes(normalized) ->")
            .substringBefore("isNo(normalized) ->")
        val save = source
            .substringAfter("private fun saveTask(expectedProposalRevision")
            .substringBefore("private fun markEditSaveFailed(")
        val successDismiss = save.indexOf("assistantSession.dismissPanel()")

        assertTrue(directSave.contains("saveTask()"))
        assertFalse(directSave.contains("dismissPanel()"))
        assertTrue(affirmative.contains("saveTask()"))
        assertFalse(affirmative.contains("dismissPanel()"))
        assertTrue(relativeSave.contains("saveTask(session.revision)"))
        assertFalse(relativeSave.contains("dismissPanel()"))
        assertTrue(successDismiss > save.indexOf("ReminderEligibilityPolicy.evaluateForScheduling"))
        assertTrue(successDismiss < save.indexOf("state=SUCCEEDED panelDismissed=true"))
    }

    @Test
    fun editSaveGuardPreventsDuplicateOrdinaryAndRelativeMutations() {
        val save = source
            .substringAfter("private fun saveTask(expectedProposalRevision")
            .substringBefore("private fun markEditSaveFailed(")
        val failure = source
            .substringAfter("private fun markEditSaveFailed(")
            .substringBefore("private fun failRelativeTemporalSaveClaim(")

        assertTrue(source.contains("private var isEditSaveInFlight = false"))
        assertTrue(save.contains("if (isEditSaveInFlight)"))
        assertTrue(save.contains("isEditSaveInFlight = true"))
        assertTrue(save.contains("state=STARTED panelDismissed=false"))
        assertTrue(save.contains("session.claimSave("))
        assertTrue(save.contains("claimedSession?.isCurrentSaveClaim(saveClaim)"))
        assertTrue(failure.contains("isEditSaveInFlight = false"))
        assertTrue(failure.contains("state=FAILED panelRetained=true"))
    }

    @Test
    fun pastReminderRejectionIsReportedAsSuccessfulSaveWithoutScheduling() {
        val save = source
            .substringAfter("private fun saveTask(expectedProposalRevision")
            .substringBefore("private fun markEditSaveFailed(")
        val duePast = save
            .substringAfter("if (dueNotInFuture) {")
            .substringBefore("} else if (reminderExpected)")

        assertTrue(save.indexOf("ReminderHelper.cancelReminder(") < save.indexOf("evaluateForScheduling("))
        assertTrue(save.contains("schedulingEligibility is ReminderSchedulingEligibility.Eligible"))
        assertTrue(save.contains("ReminderSchedulingRejection.DUE_NOT_IN_FUTURE"))
        assertTrue(save.contains("result=NOT_SCHEDULED_EXPECTED"))
        assertTrue(duePast.contains("Task updated. No reminder was scheduled"))
        assertFalse(duePast.contains("scheduleReminderFromTask"))
        assertFalse(duePast.contains("could not be scheduled"))
    }

    @Test
    fun legacyExactFieldSelectionCommandsRemainPresent() {
        val dateCommands = source
            .substringAfter("private fun isDateFieldCommand(")
            .substringBefore("private fun isTimeFieldCommand(")
        val timeCommands = source
            .substringAfter("private fun isTimeFieldCommand(")
            .substringBefore("private fun isTitleFieldCommand(")

        assertTrue(dateCommands.contains("normalized == \"date\""))
        assertTrue(dateCommands.contains("normalized == \"change date\""))
        assertTrue(timeCommands.contains("normalized == \"time\""))
        assertTrue(timeCommands.contains("normalized == \"change time\""))
    }
}
