package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TaskDetailVoiceInteractionSourceContractTest {
    private val mainRoot = File("src/main/java/com/example/myapplication")
    private val layoutRoot = File("src/main/res/layout")

    @Test
    fun darkInformationPanelsUseLightOnDarkTextTokens() {
        val layout = layoutRoot.resolve("activity_task_detail.xml").readText()
        listOf(
            "detailTitleSurface",
            "detailDateSurface",
            "detailTimeSurface",
            "detailSubtaskProgressSurface"
        ).forEach { id ->
            val panel = layout.substringAfter("android:id=\"@+id/$id\"")
                .substringBefore("</LinearLayout>")
            assertTrue(panel.contains("android:background=\"@drawable/bg_info_panel\""))
            assertTrue(panel.contains("android:textColor=\"@color/ui_text_secondary_dark\""))
            assertTrue(panel.contains("android:textColor=\"@color/ui_text_primary_dark\""))
            assertFalse(panel.contains("android:textColor=\"@color/ui_text_secondary_light\""))
            assertFalse(panel.contains("android:textColor=\"@color/ui_text_primary_light\""))
        }
    }

    @Test
    fun lightSubtaskRowsKeepDarkText() {
        val detail = mainRoot.resolve("TaskDetailActivity.kt").readText()
        val rowRenderer = detail.substringAfter("private fun renderSubtasks")
            .substringBefore("private fun readAll")

        assertTrue(rowRenderer.contains("R.drawable.bg_subtask_item"))
        assertTrue(rowRenderer.contains("R.color.ui_text_primary_light"))
        assertFalse(rowRenderer.contains("R.color.ui_text_primary_dark"))
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
    fun homeLoadsRoomContextThenSeedsT1BeforeStartingAssistant() {
        val home = mainRoot.resolve("HomeActivity.kt").readText()
        val loader = home.substringAfter("private fun loadTaskDetailAssistantEntry")
            .substringBefore("private fun handleUnavailableTaskDetailEntry")

        assertOrdered(
            loader,
            "dao.getById(taskId)",
            "dao.getSubtasks(taskId)",
            "replaceTaskDetailResult(task, subtasks)",
            "readOnlyTaskContextStore.capture()",
            "setAuthoritativeContextFocus(",
            "selectedRef = \"T1\"",
            "homeFollowUpContext = HomeFollowUpContext.AFTER_TASK_DETAILS",
            "assistantSession.startPassiveSession()",
            "What would you like to know?"
        )
        assertFalse(loader.contains("putExtra(\"task_title\""))
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
        assertFalse(detail.contains("AlertDialog"))
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
        assertTrue(decline.contains("ExecutionOutcome.CANCELLED"))
        assertFalse(decline.contains("deleteTaskAndSubtasks"))
        assertFalse(decline.contains("cancelReminder"))
    }

    @Test
    fun completionFeedbackOccursOnlyAfterRoomReloadAndHasFailureRecovery() {
        val detail = mainRoot.resolve("TaskDetailActivity.kt").readText()
        val toggle = detail.substringAfter("private fun toggleDone()")
            .substringBefore("private fun editTask")

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
    fun taskDetailsDoNotHostASecondAssistantImplementation() {
        val detail = mainRoot.resolve("TaskDetailActivity.kt").readText()

        assertFalse(detail.contains("AssistantVoiceSession"))
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
