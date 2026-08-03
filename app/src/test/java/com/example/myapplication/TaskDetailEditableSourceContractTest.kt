package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TaskDetailEditableSourceContractTest {
    private val main = File("src/main/java/com/example/myapplication")
    private val layout = File("src/main/res/layout/activity_task_detail.xml").readText()
    private val detail = main.resolve("TaskDetailActivity.kt").readText()

    @Test
    fun saveReplacesVisibleEditWhileLegacyActivityRemainsRegistered() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(layout.contains("@+id/btnSaveChanges"))
        assertTrue(layout.contains("@string/save_changes"))
        assertFalse(layout.contains("btnEditTask"))
        assertFalse(detail.contains("private fun editTask()"))
        assertFalse(detail.contains("Intent(this, EditTaskActivity::class.java)"))
        assertTrue(manifest.contains("android:name=\".EditTaskActivity\""))
    }

    @Test
    fun dirtyStateHasVisibleTextAndNonColourSignal() {
        assertTrue(layout.contains("@+id/detailUnsavedChanges"))
        assertTrue(layout.contains("@string/unsaved_changes"))
        assertTrue(detail.contains("R.drawable.bg_save_changes_dirty"))
        assertTrue(detail.contains("unsavedChangesText.visibility"))
        assertTrue(detail.contains("if (dirty) R.drawable.bg_save_changes_dirty else R.drawable.bg_action_button"))
    }

    @Test
    fun fieldsUseDraftSpeechDoubleTapActionsAndManualLongPressEditors() {
        assertTrue(detail.contains("draftController?.draft?.let { TaskDetailSpeechRenderer.title"))
        assertTrue(detail.contains("startFieldEdit(TaskDetailEditInteraction.WAITING_FOR_TITLE)"))
        assertTrue(detail.contains("startFieldEdit(TaskDetailEditInteraction.WAITING_FOR_DATE)"))
        assertTrue(detail.contains("startFieldEdit(TaskDetailEditInteraction.WAITING_FOR_TIME)"))
        assertTrue(detail.contains("titleSurface.setOnLongClickListener"))
        assertTrue(detail.contains("DatePickerDialog("))
        assertTrue(detail.contains("TimePickerDialog("))
        val manualTitle = detail.substringAfter("private fun showManualTitleEditor")
            .substringBefore("private fun showManualDatePicker")
        assertTrue(manualTitle.contains("controller.changeTitle(title)"))
        assertFalse(manualTitle.contains("dao."))
    }

    @Test
    fun saveUsesAuthoritativeTransactionReloadAndReminderResult() {
        val save = detail.substringAfter("private fun performAuthoritativeSave")
            .substringBefore("private suspend fun reloadAfterSaveConflict")
        assertTrue(save.contains("controller.isCurrent(claim)"))
        assertTrue(save.contains("updateTaskAndSubtasksIfAuthoritativeSnapshotMatches"))
        assertTrue(save.contains("expectedIsDone = claim.base.isDone"))
        assertTrue(save.contains("loadAuthoritativeSnapshot()"))
        assertTrue(save.contains("controller.replaceFromRoom(refreshed.first)"))
        assertTrue(save.contains("synchronizeReminderAfterSave(refreshed.first)"))
        assertTrue(save.indexOf("Task changes saved.") > save.indexOf("loadAuthoritativeSnapshot()"))
    }

    @Test
    fun saveAndCompletionUseSavedRoomStateForReminderWork() {
        val reminder = detail.substringAfter("private fun synchronizeReminderAfterSave")
            .substringBefore("private fun finishSaveSpeech")
        val completion = detail.substringAfter("private fun toggleDone()")
            .substringBefore("private fun syncReminderAfterCompletionChange")
        assertTrue(reminder.contains("ReminderHelper.cancelReminder(this, task.id)"))
        assertTrue(reminder.contains("task.isDone"))
        assertTrue(reminder.contains("ReminderHelper.scheduleReminderFromTask(this, task)"))
        assertTrue(completion.contains("synchronizeDraftAfterCompletion(refreshed.first)"))
        assertTrue(completion.contains("syncReminderAfterCompletionChange(refreshed.first)"))
    }

    @Test
    fun dirtyHomeBackAssistantAndDeleteUseBoundedConfirmationStates() {
        assertTrue(detail.contains("onBackPressedDispatcher.addCallback"))
        assertTrue(detail.contains("handleHomeOrBackExit()"))
        assertTrue(detail.contains("WAITING_FOR_HOME_CONFIRMATION"))
        assertTrue(detail.contains("WAITING_FOR_ASSISTANT_EXIT_CONFIRMATION"))
        assertTrue(detail.contains("WAITING_FOR_DELETE_DISCARD_CONFIRMATION"))
        assertTrue(detail.contains("HomeAssistantEntryMode.TASK_DETAIL_CONTEXT"))
        assertTrue(detail.contains("HomeAssistantEntryMode.TASK_DETAIL_DELETE_CONFIRMATION"))
        assertTrue(detail.contains("TaskDetailEditSpeechRenderer.confirmHomeExit()"))
        assertTrue(detail.contains("TaskDetailEditSpeechRenderer.confirmAssistantExit()"))
        assertTrue(detail.contains("TaskDetailEditSpeechRenderer.confirmDeleteDiscard()"))
    }

    @Test
    fun onlyHomeAndAssistantAreAnchoredWhileTaskContentAndActionsScroll() {
        val scrollEnd = layout.indexOf("</ScrollView>")
        assertTrue(layout.indexOf("@+id/detailActionGrid") in 0 until scrollEnd)
        assertTrue(layout.indexOf("@+id/btnGoHome") > scrollEnd)
        assertTrue(layout.indexOf("@+id/btnTalkAssistant") > scrollEnd)
        assertTrue(layout.contains("@+id/detailDateTimeRow"))
    }

    @Test
    fun localAssistantIsBoundedAndUsesSharedVoiceSessionAndTypedInput() {
        assertTrue(detail.contains("AssistantVoiceSession("))
        assertTrue(detail.contains("normalizeFinalTextForHost = false"))
        assertTrue(detail.contains("AccessibleAssistantInputDialog.show("))
        assertTrue(detail.contains("TaskDetailEditInteraction.WAITING_FOR_TITLE"))
        assertFalse(detail.contains("SpeechRecognizer"))
        assertFalse(detail.contains("ConversationOrchestrator"))
    }
}
