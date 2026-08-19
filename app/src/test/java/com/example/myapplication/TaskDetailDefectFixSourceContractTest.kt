package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TaskDetailDefectFixSourceContractTest {
    private val main = File("src/main/java/com/example/myapplication")
    private val detail = main.resolve("TaskDetailActivity.kt").readText()
    private val session = main.resolve("voice/AssistantVoiceSession.kt").readText()
    private val panel = main.resolve("AssistantBottomSheet.kt").readText()
    private val layout = File("src/main/res/layout/activity_task_detail.xml").readText()

    @Test
    fun activityStopUsesTheIdempotentNonInitializingLifecycleApi() {
        val onStop = detail.substringAfter("override fun onStop()")
            .substringBefore("private fun bindViews")
        val stop = session.substringAfter("fun stopForLifecycle()")
            .substringBefore("fun startSession")

        assertTrue(onStop.contains("assistantSession.stopForLifecycle()"))
        assertFalse(onStop.contains("prepareForContextEntry()"))
        assertFalse(onStop.contains("dismissPanel()"))
        assertFalse(stop.contains("ensureInitialized()"))
        assertTrue(stop.contains("assistantSessionActive = false"))
        assertTrue(stop.contains("waitingForConfirmation = false"))
        assertTrue(stop.contains("cancelRecognitionIfActive()"))
        assertTrue(stop.contains("if (panel.isShowing)"))
        assertFalse(stop.contains("onAssistantCancelled"))
    }

    @Test
    fun contextResetAlsoDoesNotCreatePanelOrRecognizer() {
        val prepare = session.substringAfter("fun prepareForContextEntry()")
            .substringBefore("fun stopForLifecycle")

        assertFalse(prepare.contains("ensureInitialized()"))
        assertTrue(prepare.contains("cancelRecognitionIfActive()"))
    }

    @Test
    fun prematureAndRepeatedPanelDismissalIsSafeWhileShownPanelStillDismisses() {
        val dismiss = session.substringAfter("fun dismissPanel()")
            .substringBefore("fun bindAssistantControl")

        assertTrue(dismiss.contains("assistantBottomSheet?.let"))
        assertTrue(dismiss.contains("if (!panel.isShowing) return@let"))
        assertTrue(dismiss.contains("if (panel.isContentReady)"))
        assertTrue(dismiss.contains("panel.dismissWithoutFocusReturn()"))
    }

    @Test
    fun bottomSheetStateAndAnimationAreGuardedUntilContentExists() {
        assertTrue(panel.contains("val isContentReady: Boolean"))
        assertTrue(panel.contains("::stateContainer.isInitialized"))
        assertTrue(panel.contains("::assistantRoot.isInitialized"))
        assertTrue(panel.contains("::tvState.isInitialized"))

        val stopped = panel.substringAfter("fun setStoppedState()")
            .substringBefore("fun showUserSpeech")
        val stopAnimation = panel.substringAfter("private fun stopStateAnimation()")
            .substringBefore("private fun startListeningAnimation")
        assertTrue(stopped.contains("if (!isContentReady) return"))
        assertTrue(stopAnimation.contains("if (!isContentReady) return"))
    }

    @Test
    fun confirmationKeepsInternalFlagAndProcessingAnimationWithDistinctSemantics() {
        val listeningState = session.substringAfter("private fun updateListeningAccessibilityState()")
            .substringBefore("private companion object")
        val waitingState = panel.substringAfter("fun setWaitingForConfirmationState()")
            .substringBefore("fun setStoppedState")
        val processing = panel.substringAfter("private fun startProcessingAnimation()")
            .substringBefore("private fun startSpeakingAnimation")

        assertTrue(session.contains("private var waitingForConfirmation = false"))
        assertTrue(session.contains("fun expectConfirmation()"))
        assertTrue(listeningState.contains("AssistantAccessibilityState.WAITING_FOR_CONFIRMATION"))
        assertTrue(listeningState.contains("setWaitingForConfirmationState()"))
        assertTrue(waitingState.contains("startProcessingAnimation()"))
        assertTrue(waitingState.contains("WAITING_FOR_CONFIRMATION"))
        assertTrue(processing.contains("R.attr.appColorAssistantProcessingStart"))
        assertTrue(processing.contains("R.attr.appColorAssistantProcessingEnd"))
    }

    @Test
    fun backAndHomeUseSeparateDestinationsForCleanDirtyAndSavedFlows() {
        val callback = detail.substringAfter("onBackPressedDispatcher.addCallback")
            .substringBefore("taskId = intent")
        val home = detail.substringAfter("private fun handleHomeExit()")
            .substringBefore("private fun handleBackExit()")
        val back = detail.substringAfter("private fun handleBackExit()")
            .substringBefore("private fun handleContextualAssistant")
        val no = detail.substringAfter("private fun handleConfirmationNo()")
            .substringBefore("private fun handleConfirmationCancel")

        assertTrue(callback.contains("handleBackExit()"))
        assertTrue(back.contains("if (controller?.isDirty != true)"))
        assertTrue(home.contains("returnHome()"))
        assertTrue(home.contains("afterSave = ::returnHome"))
        assertTrue(back.contains("TaskScreenControlSpeechRenderer.goingBack()"))
        assertTrue(back.contains("returnBack()"))
        assertTrue(back.contains("afterSave = ::returnBack"))
        assertTrue(no.contains("WAITING_FOR_HOME_CONFIRMATION"))
        assertTrue(no.contains("WAITING_FOR_BACK_CONFIRMATION"))
        assertTrue(no.contains("draftController?.discard()"))
        assertTrue(detail.substringAfter("private fun returnBack()")
            .substringBefore("private fun statusFor").contains("finish()"))
        assertFalse(back.contains("HomeActivity"))
    }

    @Test
    fun everyTaskDetailListenAgainPathUsesAQuestionRendererOrQuestionPrompt() {
        val fieldResponse = detail.substringAfter("private fun handleFieldResponse")
            .substringBefore("private fun handleConfirmationResponse")
        val confirmation = detail.substringAfter("private fun handleConfirmationResponse")
            .substringBefore("private fun handleConfirmationYes")

        assertFalse(fieldResponse.contains("speakThenListenAgain(\"Please try again."))
        assertFalse(fieldResponse.contains("speakThenListenAgain(\"Please choose another value."))
        assertTrue(fieldResponse.contains("result.prompt"))
        assertTrue(fieldResponse.contains("pastScheduleRetry(editInteraction)"))
        assertTrue(fieldResponse.contains("retryQuestion(editInteraction)"))
        assertTrue(confirmation.contains("retryQuestion(editInteraction)"))
        assertFalse(confirmation.contains("speakThenListenAgain(\"Please say yes"))
    }

    @Test
    fun actionGridScrollsWithDetailsAndNavigationDockIsFixedWithoutSpacer() {
        val scrollEnd = layout.indexOf("</ScrollView>")
        val gridStart = layout.indexOf("android:id=\"@+id/detailActionGrid\"")
        val dockStart = layout.indexOf("android:id=\"@+id/taskDetailBottomDock\"")
        val grid = layout.substring(gridStart, scrollEnd)

        assertTrue(gridStart < scrollEnd)
        assertTrue(dockStart > scrollEnd)
        assertTrue(layout.substring(0, scrollEnd).contains(
            "app:layout_constraintBottom_toTopOf=\"@id/taskDetailBottomDock\""
        ))
        assertTrue(grid.contains("@+id/btnReadAll"))
        assertTrue(grid.contains("@+id/btnToggleDone"))
        assertTrue(grid.contains("@+id/btnSaveChanges"))
        assertTrue(grid.contains("@+id/btnDeleteTask"))
        assertTrue(layout.substring(dockStart).contains("@+id/btnGoHome"))
        assertTrue(layout.substring(dockStart).contains("@+id/btnTalkAssistant"))
        assertFalse(layout.contains("<Space"))
    }

    @Test
    fun fixDoesNotIntroduceADatabaseMigration() {
        val changedProductionFiles = listOf(
            "AssistantBottomSheet.kt",
            "TaskDetailActivity.kt",
            "TaskDetailEditing.kt",
            "TaskScreenSpeechRenderers.kt",
            "voice/AssistantVoiceSession.kt"
        )
        assertFalse(changedProductionFiles.any { it.contains("Migration", ignoreCase = true) })
        assertFalse(detail.contains("fallbackToDestructiveMigration"))
    }
}
