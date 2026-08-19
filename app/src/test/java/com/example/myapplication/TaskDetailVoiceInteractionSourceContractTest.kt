package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TaskDetailVoiceInteractionSourceContractTest {
    private val mainRoot = File("src/main/java/com/example/myapplication")
    private val layoutRoot = File("src/main/res/layout")

    @Test
    fun taskDetailInformationSurfacesUseDedicatedOpaquePanelsAndLightOnDarkText() {
        val layout = layoutRoot.resolve("activity_task_detail.xml").readText()
        listOf(
            "detailTitleSurface",
            "detailDateSurface",
            "detailTimeSurface",
            "detailSubtaskProgressSurface"
        ).forEach { id ->
            val panel = layout.substringAfter("android:id=\"@+id/$id\"")
                .substringBefore("</LinearLayout>")
            assertTrue(panel.contains("android:background=\"@drawable/bg_task_detail_surface\""))
            assertTrue(panel.contains("android:textColor=\"?attr/appColorTextSecondaryDark\""))
            assertTrue(panel.contains("android:textColor=\"?attr/appColorTextPrimaryDark\""))
            assertFalse(panel.contains("android:textColor=\"?attr/appColorTextSecondaryLight\""))
            assertFalse(panel.contains("android:textColor=\"?attr/appColorTextPrimaryLight\""))
        }
    }

    @Test
    fun lightSubtaskRowsKeepDarkText() {
        val detail = mainRoot.resolve("TaskDetailActivity.kt").readText()
        val rowRenderer = detail.substringAfter("private fun renderSubtasks")
            .substringBefore("private fun readAll")

        assertTrue(rowRenderer.contains("R.drawable.bg_subtask_item"))
        assertTrue(rowRenderer.contains("R.drawable.bg_task_status_completed"))
        assertTrue(rowRenderer.contains("R.attr.appColorTextPrimaryLight"))
        assertFalse(rowRenderer.contains("R.attr.appColorTextPrimaryDark"))
    }

    @Test
    fun taskDetailLaunchContractCarriesOnlyAuthoritativeIdAndMode() {
        val contract = mainRoot.resolve("HomeAssistantEntryContract.kt").readText()
        val detail = mainRoot.resolve("TaskDetailActivity.kt").readText()
        val launcher = detail.substringAfter("private fun launchHomeAssistant")
            .substringBefore("private fun statusFor")

        assertTrue(contract.contains("EXTRA_OPEN_ASSISTANT"))
        assertTrue(contract.contains("EXTRA_CONTEXT_TASK_ID"))
        assertTrue(contract.contains("EXTRA_ENTRY_MODE"))
        assertTrue(contract.contains("TASK_DETAIL_CONTEXT"))
        assertTrue(contract.contains("TASK_DETAIL_DELETE_CONFIRMATION"))
        assertTrue(launcher.contains("taskId = task.id"))
        assertTrue(launcher.contains("entryMode = entryMode"))
        assertFalse(launcher.contains("task.title"))
        assertFalse(launcher.contains("task.dueDate"))
        assertFalse(launcher.contains("task.dueTime"))
    }

    @Test
    fun homePublishesAndFocusesTaskDetailBeforeStartingEitherAssistantEntry() {
        val home = mainRoot.resolve("HomeActivity.kt").readText()
        val loader = home.substringAfter("private fun loadTaskDetailAssistantEntry")
            .substringBefore("private fun handleUnavailableTaskDetailEntry")
        val publisher = home.substringAfter("private fun publishTaskDetailAssistantContext")
            .substringBefore("private fun handleUnavailableTaskDetailEntry")
        val normalEntry = loader.substringAfter("HomeAssistantEntryMode.TASK_DETAIL_CONTEXT -> {")
            .substringBefore("HomeAssistantEntryMode.TASK_DETAIL_DELETE_CONFIRMATION -> {")
        val deleteEntry = loader
            .substringAfter("HomeAssistantEntryMode.TASK_DETAIL_DELETE_CONFIRMATION -> {")
            .substringBefore("HomeAssistantEntryMode.GENERIC -> Unit")

        assertOrdered(
            loader,
            "dao.getById(taskId)",
            "dao.getSubtasks(taskId)",
            "HomeAssistantEntryMode.TASK_DETAIL_CONTEXT -> {",
            "publishTaskDetailAssistantContext(task, subtasks)",
            "HomeAssistantEntryMode.TASK_DETAIL_DELETE_CONFIRMATION -> {"
        )
        assertOrdered(
            publisher,
            "replaceTaskDetailResult(task, subtasks)",
            "readOnlyTaskContextStore.capture()",
            "capture.snapshot.items.single()",
            "setAuthoritativeContextFocus(",
            "selectedRef = item.ref",
            "capturedGeneration = capture.snapshot.generation"
        )
        assertOrdered(
            normalEntry,
            "publishTaskDetailAssistantContext(task, subtasks)",
            "homeFollowUpContext = HomeFollowUpContext.AFTER_TASK_DETAILS",
            "assistantSession.startPassiveSession()",
            "What would you like to know?"
        )
        assertOrdered(
            deleteEntry,
            "publishTaskDetailAssistantContext(task, subtasks)",
            "contextScope=\${capture.snapshot.scope}",
            "contextItemCount=\${capture.snapshot.items.size}",
            "focusEstablished=true",
            "assistantSession.startPassiveSession()",
            "askDeleteConfirmation(task)"
        )
        assertFalse(loader.contains("putExtra(\"task_title\""))
        assertFalse(deleteEntry.contains("task.id"))
        assertFalse(deleteEntry.contains("pendingDeleteTaskId"))
    }

    @Test
    fun entryLifecycleConsumesIntentsAndGenericSessionsClearTaskContext() {
        val home = mainRoot.resolve("HomeActivity.kt").readText()
        val contract = mainRoot.resolve("HomeAssistantEntryContract.kt").readText()
        val generic = home.substringAfter("private fun prepareGenericAssistantSession")
            .substringBefore("private fun loadTaskDetailAssistantEntry")

        assertTrue(home.contains("override fun onNewIntent(intent: Intent)"))
        assertTrue(home.contains("HomeAssistantEntryContract.consume(intent)"))
        assertTrue(contract.contains("intent.removeExtra(EXTRA_OPEN_ASSISTANT)"))
        assertTrue(contract.contains("intent.removeExtra(EXTRA_CONTEXT_TASK_ID)"))
        assertTrue(contract.contains("intent.removeExtra(EXTRA_ENTRY_MODE)"))
        assertTrue(generic.contains("clearConversationSessionContext()"))
        assertTrue(generic.contains("clearPendingDeleteState()"))
    }

    @Test
    fun detailDeleteUsesExistingExactIdConfirmationWithoutMatching() {
        val detail = mainRoot.resolve("TaskDetailActivity.kt").readText()
        val home = mainRoot.resolve("HomeActivity.kt").readText()
        val loader = home.substringAfter("private fun loadTaskDetailAssistantEntry")
            .substringBefore("private fun handleUnavailableTaskDetailEntry")
        val confirmation = home.substringAfter("private fun askDeleteConfirmation")
            .substringBefore("private suspend fun beginBreakdownTargetResolution")

        assertTrue(detail.contains("HomeAssistantEntryMode.TASK_DETAIL_DELETE_CONFIRMATION"))
        assertFalse(detail.contains("dao.deleteTaskAndSubtasks"))
        assertTrue(loader.contains("dao.getById(taskId)"))
        assertTrue(loader.contains("askDeleteConfirmation(task)"))
        assertFalse(loader.contains("TaskMatcher"))
        assertTrue(confirmation.contains("pendingDeleteTaskId = task.id"))
        assertTrue(confirmation.contains("HomeFollowUpContext.DELETE_CONFIRMATION"))
        assertTrue(confirmation.contains("dao.getById(taskId)"))
        assertTrue(confirmation.contains("dao.deleteTaskAndSubtasks(taskId)"))
        assertTrue(confirmation.contains("ReminderHelper.cancelReminder"))
    }

    @Test
    fun deletionStillRequiresExplicitConfirmationAndDeclineDoesNotMutateRoom() {
        val home = mainRoot.resolve("HomeActivity.kt").readText()
        val deleteFollowUp = home.substringAfterLast("HomeFollowUpContext.DELETE_CONFIRMATION ->")
            .substringBefore("HomeFollowUpContext.ROUTINE_CREATE_NAME")
        val decline = deleteFollowUp.substringAfter("ConversationIntent.CONFIRM_NO")
            .substringBefore("true\n                    }")

        assertTrue(deleteFollowUp.contains("ConversationIntent.CONFIRM_YES"))
        assertTrue(deleteFollowUp.contains("confirmPendingDelete()"))
        assertTrue(decline.contains("cancelPendingDeleteConfirmation()"))
        assertTrue(home.substringAfter("private fun cancelPendingDeleteConfirmation").contains("ExecutionOutcome.CANCELLED"))
        assertFalse(decline.contains("deleteTaskAndSubtasks"))
        assertFalse(decline.contains("cancelReminder"))
    }

    @Test
    fun contextualReadDoesNotClearPendingDeleteAndYesOrNoStillOwnConfirmation() {
        val home = mainRoot.resolve("HomeActivity.kt").readText()
        val executor = home.substringAfter("private fun executeContextRead(")
            .substringBefore("private fun handleQueryReadingFollowUp(")
        val deleteFollowUp = home.substringAfterLast("HomeFollowUpContext.DELETE_CONFIRMATION ->")
            .substringBefore("HomeFollowUpContext.BREAKDOWN_CONFIRMATION")

        assertFalse(executor.contains("clearPendingDeleteState()"))
        assertFalse(executor.contains("homeFollowUpContext ="))
        assertFalse(executor.contains("deleteTaskAndSubtasks"))
        assertTrue(deleteFollowUp.contains("confirmPendingDelete()"))
        assertTrue(deleteFollowUp.contains("cancelPendingDeleteConfirmation()"))
        val cancel = home.substringAfter("private fun cancelPendingDeleteConfirmation")
            .substringBefore("private fun handleContextItemRestatement")
        assertTrue(cancel.contains("clearPendingDeleteState()"))
        assertTrue(cancel.contains("ExecutionOutcome.CANCELLED"))
    }

    @Test
    fun completionFeedbackOccursOnlyAfterRoomReloadAndHasFailureRecovery() {
        val detail = mainRoot.resolve("TaskDetailActivity.kt").readText()
        val toggle = detail.substringAfter("private fun toggleDone()")
            .substringBefore("private fun launchHomeAssistant")

        assertOrdered(
            toggle,
            "dao.updateDoneStatusForTaskAndSubtasks",
            "loadAuthoritativeSnapshot()",
            "applySnapshot(refreshed)",
            "performConfirmationHapticFeedback()",
            "R.string.task_marked_complete"
        )
        assertTrue(toggle.contains("R.string.task_marked_incomplete"))
        assertTrue(toggle.contains("if (taskMutationInProgress) return"))
        assertTrue(toggle.contains("setActionButtonsEnabled(false)"))
        assertTrue(toggle.contains("R.string.task_update_failed"))
        assertTrue(toggle.contains("setActionButtonsEnabled(true)"))
    }

    @Test
    fun confirmationHapticHasSafeApiFallback() {
        val haptics = mainRoot.resolve("HapticFeedbackExtensions.kt").readText()

        assertTrue(haptics.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.R"))
        assertTrue(haptics.contains("HapticFeedbackConstants.CONFIRM"))
        assertTrue(haptics.contains("HapticFeedbackConstants.LONG_PRESS"))
    }

    @Test
    fun taskDetailsHostOnlyTheBoundedSharedEditAssistant() {
        val detail = mainRoot.resolve("TaskDetailActivity.kt").readText()

        assertTrue(detail.contains("AssistantVoiceSession("))
        assertTrue(detail.contains("TaskDetailEditInteraction"))
        assertFalse(detail.contains("SpeechRecognizer"))
        assertFalse(detail.contains("ConversationOrchestrator"))
        assertTrue(detail.contains("Intent(this, HomeActivity::class.java)"))
    }

    private fun assertOrdered(source: String, vararg markers: String) {
        var lastIndex = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker)
            assertTrue("Missing or out-of-order marker: $marker", index > lastIndex)
            lastIndex = index
        }
    }
}
