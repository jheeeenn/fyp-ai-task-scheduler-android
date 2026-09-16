package com.example.myapplication.ai.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContextualDeleteAndNaturalEndingSourceTest {
    private val main = File("src/main/java/com/example/myapplication")
    private val home = main.resolve("HomeActivity.kt").readText()
    private val prompt = ConversationAgentClient.ROUTING_SYSTEM_PROMPT
    private val repairPrompt = ConversationAgentClient.CONTEXT_ACTION_REPAIR_SYSTEM_PROMPT

    @Test
    fun contextualDeleteUsesPrivateRefRefetchAndExistingConfirmation() {
        val contextAction = home
            .substringAfter("ConversationRoute.CONTEXT_ACTION -> {")
            .substringBefore("ConversationRoute.QUERY_READING_CONTROL ->")
        val deleteBranch = contextAction
            .substringAfter("validation.action == ConversationContextAction.DELETE")
            .substringBefore("val extractedChange")

        assertTrue(contextAction.contains("ContextActionDecisionValidator.validate("))
        assertTrue(contextAction.contains("ContextActionReferenceGroundingValidator.validate("))
        assertTrue(contextAction.contains("readOnlyTaskContextStore.resolveRef("))
        assertTrue(contextAction.contains("taskDao.getById(privateTaskId)"))
        assertTrue(contextAction.contains("readOnlyTaskContextStore.matchesResolvedTask("))
        assertTrue(deleteBranch.contains("conversationOrchestrator.commitFinalDecision"))
        assertTrue(deleteBranch.contains("askDeleteConfirmation(requireNotNull(initiallyFetchedTask))"))
        assertFalse(deleteBranch.contains("agentOrchestrator.processContextAction"))
        assertFalse(deleteBranch.contains("TaskMatcher"))
        assertFalse(deleteBranch.contains("deleteTaskAndSubtasks"))
        assertFalse(contextAction.contains("Log.d(\"HOME_CONTEXT_ACTION\", privateTaskId"))
    }

    @Test
    fun deletionStillOccursOnlyInsideExistingYesConfirmationFlow() {
        val ask = home
            .substringAfter("private fun askDeleteConfirmation")
            .substringBefore("private fun clearPendingDeleteState")
        val confirm = home
            .substringAfter("private fun confirmPendingDelete()")
            .substringBefore("private suspend fun beginBreakdownTargetResolution")
        val contextAction = home
            .substringAfter("ConversationRoute.CONTEXT_ACTION -> {")
            .substringBefore("ConversationRoute.QUERY_READING_CONTROL ->")

        assertTrue(ask.contains("pendingDeleteTaskId = task.id"))
        assertTrue(ask.contains("HomeFollowUpContext.DELETE_CONFIRMATION"))
        assertTrue(ask.contains("Delete ${'$'}{task.title}? Please say yes or no."))
        assertFalse(ask.contains("deleteTaskAndSubtasks"))
        assertFalse(contextAction.contains("deleteTaskAndSubtasks"))
        assertTrue(confirm.contains("dao.getById(taskId)"))
        assertTrue(confirm.contains("dao.deleteTaskAndSubtasks(taskId)"))
        assertTrue(confirm.contains("ReminderHelper.cancelReminder"))
    }

    @Test
    fun taskDetailDeleteButtonAndDeterministicNoPathRemainShared() {
        val detail = main.resolve("TaskDetailActivity.kt").readText()
        val deleteFollowUp = home
            .substringAfter("HomeFollowUpContext.DELETE_CONFIRMATION -> {")
            .substringBefore("HomeFollowUpContext.BREAKDOWN_CONFIRMATION")

        assertTrue(detail.contains("HomeAssistantEntryMode.TASK_DETAIL_DELETE_CONFIRMATION"))
        assertFalse(detail.contains("dao.deleteTaskAndSubtasks"))
        assertTrue(deleteFollowUp.contains("ConversationIntent.CONFIRM_NO"))
        assertTrue(deleteFollowUp.contains("cancelPendingDeleteConfirmation()"))
        val cancel = home.substringAfter("private fun cancelPendingDeleteConfirmation")
            .substringBefore("private fun handleContextItemRestatement")
        assertTrue(cancel.contains("clearPendingDeleteState()"))
        assertTrue(cancel.contains("ExecutionOutcome.CANCELLED"))
    }

    @Test
    fun deletePreservationIsCancelledBeforeAgentRoutingWithoutRoomMutation() {
        val boundedHandler = home
            .substringAfter("private fun handleBoundedDeleteConfirmation")
            .substringBefore("private fun cancelPendingDeleteConfirmation")

        assertTrue(boundedHandler.contains("DeleteConfirmationPolicy.resolve(normalized)"))
        assertTrue(boundedHandler.contains("BoundedConfirmationResult.REJECT"))
        assertTrue(boundedHandler.contains("cancelPendingDeleteConfirmation()"))
        assertFalse(boundedHandler.contains("deleteTaskAndSubtasks"))
    }

    @Test
    fun promptsSupportDeleteGroundingAndNaturalEndSession() {
        assertTrue(prompt.contains("context_action\":\"DELETE"))
        assertTrue(prompt.contains("Current validated focus: T1. User: Delete this task."))
        assertTrue(prompt.contains("Current validated focus: T1. User: Remove it."))
        assertTrue(prompt.contains("Available: false\nUser: Delete this task."))
        assertTrue(repairPrompt.contains("Use DELETE only for an explicit deletion request"))
        assertTrue(repairPrompt.contains("Never choose T1 as a default"))
        listOf(
            "User: Okay, that's all.",
            "User: No, that's all.",
            "User: I don't need anything else.",
            "User: I'm finished for now."
        ).forEach { assertTrue(prompt.contains(it)) }
        assertTrue(prompt.contains("Do not ask whether anything else"))
    }

    @Test
    fun bothLocalExitChecksUseOneInterpreterAndOneTerminalApi() {
        val exitChecks = home
            .substringAfter("private fun isConversationExitCommand")
            .substringBefore("private fun isSimpleFollowUpAgreement")
        val terminal = home
            .substringAfter("private fun endAssistantConversation()")
            .substringBefore("private fun clearConversationSessionContext")

        assertTrue(exitChecks.contains("AssistantExitInterpreter.isExitUtterance"))
        assertTrue(exitChecks.contains("AssistantExitInterpreter.isFollowUpExitUtterance"))
        assertTrue(terminal.contains("assistantSession.endConversation("))
        assertFalse(terminal.contains("assistantSession.speakThenStop("))
        assertFalse(terminal.contains("onAssistantCancelled"))
    }
}
