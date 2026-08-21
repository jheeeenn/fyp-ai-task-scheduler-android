package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssistantTerminalFeedbackSourceContractTest {
    private val edit = source("EditTaskActivity.kt")
    private val create = source("CreateTaskActivity.kt")
    private val home = source("HomeActivity.kt")
    private val settings = source("SettingsActivity.kt")

    @Test
    fun editSaveSpeaksAuthoritativeTerminalResultBeforeFinishing() {
        val save = function(edit, "private fun saveTask(", "private fun markEditSaveFailed(")
        val taskNotFound = save
            .substringAfter("if (existingTask == null)")
            .substringBefore("if (saveClaim != null")

        assertFalse(save.contains("assistantSession.dismissPanel()"))
        assertTrue(save.contains("Task updated and reminder rescheduled."))
        assertTrue(save.contains("Task updated. No reminder was scheduled because the task time is in the past."))
        assertTrue(save.contains("Task updated. Reminder removed."))
        assertTrue(save.contains("Task updated, but the reminder could not be scheduled."))
        assertTrue(save.contains("assistantSession.speakThenRun(terminalMessage)"))
        assertTrue(
            save.indexOf("state=SUCCEEDED mutationComplete=true") <
                save.indexOf("assistantSession.speakThenRun(terminalMessage)")
        )
        assertTrue(
            save.indexOf("assistantSession.speakThenRun(terminalMessage)") <
                save.lastIndexOf("finish()")
        )

        assertTrue(taskNotFound.contains("assistantSession.speakThenRun(\"That task no longer exists.\")"))
        assertTrue(
            taskNotFound.indexOf("speakThenRun") < taskNotFound.indexOf("finish()")
        )
    }

    @Test
    fun editVoiceDeleteRequiresBoundedPendingConfirmation() {
        val voice = function(
            edit,
            "private fun handleVoiceInput(text: String)",
            "private fun handleRelativeTemporalProposalInput("
        )
        val localDelete = voice
            .substringAfter("normalized == \"delete\"")
            .substringBefore("Please return to the main assistant")
        val agentDelete = edit
            .substringAfter("AiIntent.DELETE_TASK.name ->")
            .substringBefore("AiIntent.QUERY_TASK.name ->")
        val confirmation = function(
            edit,
            "private fun handlePendingDeleteConfirmation(",
            "private fun performConfirmedDelete("
        )

        assertTrue(edit.contains("private var waitingForDeleteConfirmation = false"))
        assertTrue(voice.contains("handlePendingDeleteConfirmation(normalized)"))
        assertTrue(localDelete.contains("requestVoiceDeleteConfirmation()"))
        assertFalse(localDelete.contains("deleteTaskAndSubtasks"))
        assertTrue(agentDelete.contains("requestVoiceDeleteConfirmation()"))
        assertFalse(agentDelete.contains("deleteTaskAndSubtasks"))
        assertTrue(confirmation.contains("BoundedConfirmationPolicy.resolve(normalized)"))
        assertTrue(confirmation.contains("BoundedConfirmationResult.AFFIRM"))
        assertTrue(confirmation.contains("BoundedConfirmationResult.REJECT"))
        assertTrue(confirmation.contains("BoundedConfirmationResult.CANCEL"))
        assertTrue(confirmation.contains("BoundedConfirmationResult.UNKNOWN"))
        assertTrue(confirmation.contains("Okay. I didn't delete the task."))
        assertTrue(confirmation.contains("Please say yes to delete the task, or no to keep it."))
    }

    @Test
    fun confirmedVoiceAndTouchDeleteSpeakCompletionBeforeFinish() {
        val touch = function(edit, "private fun confirmDeleteTask()", "private fun requestVoiceDeleteConfirmation()")
        val request = function(
            edit,
            "private fun requestVoiceDeleteConfirmation()",
            "private fun handlePendingDeleteConfirmation("
        )
        val deletion = function(
            edit,
            "private fun performConfirmedDelete(",
            "private fun handleVoiceInput(text: String)"
        )

        assertTrue(touch.contains("performConfirmedDelete(source = \"TOUCH_CONFIRMATION\")"))
        assertTrue(request.contains("waitingForDeleteConfirmation = true"))
        assertTrue(request.contains("assistantSession.expectConfirmation()"))
        assertTrue(request.contains("Are you sure you want to delete this task?"))
        assertTrue(deletion.contains("deleteTaskAndSubtasks(taskId)"))
        assertFalse(deletion.contains("assistantSession.dismissPanel()"))
        assertTrue(deletion.contains("assistantSession.speakThenRun(\"Task deleted.\")"))
        assertTrue(deletion.indexOf("speakThenRun(\"Task deleted.\")") < deletion.indexOf("finish()"))
    }

    @Test
    fun otherAssistantHostsRetainTheirTerminalDeliveryAbstractions() {
        val createTerminal = function(
            create,
            "private fun speakThenFinish(text: String)",
            "private fun resetTaskDraftState()"
        )
        val homeDelivery = function(
            home,
            "private fun deliverObservationResponse(",
            "private suspend fun speakObservation("
        )

        assertTrue(createTerminal.contains("assistantSession.speakThenRun(text)"))
        assertTrue(createTerminal.indexOf("speakThenRun") < createTerminal.indexOf("finish()"))
        assertTrue(homeDelivery.contains("assistantSession.speakThenRun"))
        assertTrue(homeDelivery.contains("assistantSession.speakThenListenAgain"))
        assertTrue(homeDelivery.contains("assistantSession.speakThenStop"))
        assertTrue(settings.contains("assistantSession.speakThenStop(result.speech)"))
        assertTrue(settings.contains("assistantSession.speakThenStop(\"Okay. I didn't change the setting.\")"))
    }

    private fun source(name: String): String =
        File("src/main/java/com/example/myapplication/$name").readText()

    private fun function(source: String, start: String, end: String): String = source
        .substringAfter(start)
        .substringBefore(end)
}
