package com.example.myapplication.ai.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeConversationStabilizationSourceTest {
    private val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()

    @Test
    fun contextualDeleteFallbackRunsOnlyAfterSemanticRoutingAndRepair() {
        val voiceFlow = source.substringAfter("private fun handleVoiceCommand(command: String)")
        val primary = voiceFlow.indexOf("conversationOrchestrator.process(")
        val failureFallback = voiceFlow.indexOf("ContextDeleteFailureFallbackPolicy.resolve(")
        val repair = voiceFlow.indexOf("conversationOrchestrator.processContextActionRepair(")
        val fallback = voiceFlow.indexOf("ContextDeleteFailureFallbackPolicy.resolve(", repair)
        val routeExecution = voiceFlow.indexOf(
            "when (conversationDecision.route)",
            fallback
        )
        val fallbackBlock = voiceFlow.substring(fallback, routeExecution)

        assertTrue(primary >= 0)
        assertTrue(failureFallback > primary)
        assertTrue(repair > failureFallback)
        assertTrue(fallback > repair)
        assertTrue(routeExecution > fallback)
        assertTrue(fallbackBlock.contains("agentAttempted = true"))
        assertTrue(fallbackBlock.contains("ContextActionDecisionValidator.validate("))
        assertTrue(fallbackBlock.contains("ContextActionReferenceGroundingValidator.validate("))
    }

    @Test
    fun fallbackStillUsesTheExistingAuthoritativeDeleteExecutionChain() {
        val contextAction = source
            .substringAfter("ConversationRoute.CONTEXT_ACTION -> {")
            .substringBefore("ConversationRoute.QUERY_READING_CONTROL ->")
        val deleteBranch = contextAction
            .substringAfter("validation.action == ConversationContextAction.DELETE")
            .substringBefore("val extractedChange")

        assertTrue(contextAction.contains("readOnlyTaskContextStore.resolveRef("))
        assertTrue(contextAction.contains("taskDao.getById(privateTaskId)"))
        assertTrue(contextAction.contains("readOnlyTaskContextStore.matchesResolvedTask("))
        assertTrue(deleteBranch.contains("askDeleteConfirmation("))
        assertFalse(deleteBranch.contains("deleteTaskAndSubtasks"))
    }

    @Test
    fun localClassifierAndFollowUpActionsRemainBoundedByActualUtterance() {
        val classifierGate = source
            .substringAfter("val convoResult = conversationIntentClassifier.classify(normalized)")
            .substringBefore("handleHomeFollowUp(normalized)")
        val handler = source
            .substringAfter("private fun handleConversationIntent(")
            .substringBefore("private fun extractSpokenTaskPhrase")
        val afterNoTasks = handler
            .substringAfter("HomeFollowUpContext.AFTER_NO_TASKS -> {")
            .substringBefore("HomeFollowUpContext.AFTER_TASK_SUMMARY")
        val deleteConfirmation = handler
            .substringAfter("HomeFollowUpContext.DELETE_CONFIRMATION -> {")
            .substringBefore("HomeFollowUpContext.BREAKDOWN_CONFIRMATION")

        assertTrue(classifierGate.contains("LocalConversationIntentClassifier.shouldExecuteLocally"))
        assertTrue(afterNoTasks.contains("isBoundedCreateFollowUpControl(normalized)"))
        assertTrue(afterNoTasks.contains("isSimpleFollowUpEndCommand(normalized)"))
        assertTrue(deleteConfirmation.contains("BoundedConfirmationPolicy.resolve(normalized)"))
        assertTrue(deleteConfirmation.contains("BoundedConfirmationResult.AFFIRM"))
        assertTrue(source.contains("handleBoundedDeleteConfirmation(normalized)"))
        assertTrue(AssistantExitInterpreter.isFollowUpExitUtterance("no"))
        assertFalse(AssistantExitInterpreter.isFollowUpExitUtterance("is that"))
    }

    @Test
    fun affirmativeDeleteAnswerCannotMutateOutsidePendingDeleteContext() {
        val boundedHandler = source
            .substringAfter("private fun handleBoundedDeleteConfirmation")
            .substringBefore("private fun cancelPendingDeleteConfirmation")

        assertTrue(
            boundedHandler.contains(
                "homeFollowUpContext != HomeFollowUpContext.DELETE_CONFIRMATION"
            )
        )
        assertTrue(boundedHandler.contains("BoundedConfirmationResult.AFFIRM"))
        assertTrue(boundedHandler.contains("confirmPendingDelete()"))
    }

    @Test
    fun modelEndSessionIsGuardedBeforeTerminalDelivery() {
        val guard = source.indexOf("ConversationEndSessionSafetyPolicy.shouldRejectModelEndSession")
        val terminal = source.indexOf("ConversationRoute.END_SESSION -> {")

        assertTrue(guard >= 0)
        assertTrue(terminal > guard)
        assertTrue(source.contains("source = \"android_end_session_semantic_guard\""))
        assertTrue(source.contains("assistantSession.endConversation(conversationDecision.reply)"))
    }
}
