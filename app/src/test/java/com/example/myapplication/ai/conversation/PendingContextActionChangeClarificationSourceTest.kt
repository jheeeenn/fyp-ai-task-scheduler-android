package com.example.myapplication.ai.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PendingContextActionChangeClarificationSourceTest {
    private val home = File(
        "src/main/java/com/example/myapplication/HomeActivity.kt"
    ).readText()

    @Test
    fun pendingStateRetainsActionGroundedRefGenerationAndAuthoritativeSnapshot() {
        val state = home
            .substringAfter("private data class PendingContextActionChangeClarification(")
            .substringBefore("private data class PendingContextActionResolution(")

        assertTrue(state.contains("val action: ConversationContextAction"))
        assertTrue(state.contains("val authorityValidatedRef: String"))
        assertTrue(state.contains("val capturedGeneration: Long"))
        assertTrue(state.contains("val authoritativeTaskSnapshot: TaskEntity"))
        assertTrue(state.contains("val originalNormalizedRequest: String"))
        assertTrue(state.contains("val returnContext: HomeFollowUpContext"))
        assertTrue(home.contains("HomeFollowUpContext.CONTEXT_ACTION_CHANGE_CLARIFICATION"))
    }

    @Test
    fun continuationOwnsTurnAndBypassesFreshConversationAndCreateRouting() {
        val command = home
            .substringAfter("private fun handleVoiceCommand(command: String)")
            .substringBefore("private suspend fun executeDailyBriefing")
        val resolution = command
            .substringAfter("val pendingTargetResolution =")
            .substringBefore("val contextActionRequestText")
        val decision = command
            .substringAfter("var conversationDecision = pendingTargetResolution.decision")
            .substringBefore("if (conversationDecision.route == ConversationRoute.CONTEXT_READ")
        val actionBranch = command
            .substringAfter("val taskAgentInput: String")
            .substringAfter("ConversationRoute.CONTEXT_ACTION -> {")
            .substringBefore("ConversationRoute.QUERY_READING_CONTROL ->")

        assertTrue(command.contains("CONTEXT_ACTION_CHANGE_CLARIFICATION"))
        assertTrue(command.contains("!pendingContextTargetOwnsTurn && handleContextItemRead"))
        assertTrue(resolution.contains("resolvePendingContextActionChange("))
        assertTrue(decision.contains("?: try"))
        assertTrue(decision.contains("conversationOrchestrator.process("))
        assertTrue(
            command.indexOf("resolvePendingContextActionChange(") <
                command.indexOf("conversationOrchestrator.process(")
        )
        assertTrue(actionBranch.contains("normalizedText = contextActionRequestText"))
        assertTrue(actionBranch.contains("expectedAction = validation.action"))
        assertFalse(actionBranch.contains("CreateTaskActivity"))
    }

    @Test
    fun continuationRevalidatesGenerationRefRoomTaskAndSnapshotBeforeExtraction() {
        val resolver = home
            .substringAfter("private suspend fun resolvePendingContextActionChange(")
            .substringBefore("private suspend fun resolvePendingContextActionTarget(")

        assertTrue(resolver.contains("pending.capturedGeneration == taskContextCapture.snapshot.generation"))
        assertTrue(resolver.contains("readOnlyTaskContextStore.currentGeneration()"))
        assertTrue(resolver.contains("snapshotRefCount == 1"))
        assertTrue(resolver.contains("readOnlyTaskContextStore.resolveRef("))
        assertTrue(resolver.contains("taskDao().getById(taskId)"))
        assertTrue(resolver.contains("readOnlyTaskContextStore.matchesResolvedTask("))
        assertTrue(resolver.contains("sameContextActionTaskSnapshot("))
        assertTrue(resolver.contains("isEligibleContextActionTarget("))
        assertTrue(resolver.contains("contextAction = pending.action"))
        assertTrue(resolver.contains("source = \"android_pending_context_change\""))
        assertTrue(resolver.contains("originalActionRequest = normalizedText"))
        assertFalse(resolver.contains("ConversationRoute.TASK_COMMAND"))
        assertFalse(resolver.contains("CREATE_TASK"))
    }

    @Test
    fun extractionFailureRetainsClarificationWhileStaleCancelAndSuccessClearIt() {
        val actionBranch = home
            .substringAfter("ConversationRoute.CONTEXT_ACTION -> {")
            .substringBefore("ConversationRoute.QUERY_READING_CONTROL ->")
        val resolver = home
            .substringAfter("private suspend fun resolvePendingContextActionChange(")
            .substringBefore("private suspend fun resolvePendingContextActionTarget(")
        val clearSession = home
            .substringAfter("private fun clearConversationSessionContext()")
            .substringBefore("private fun clearAccessibleTaskQuerySession(")

        assertTrue(actionBranch.contains("beginOrRetainContextActionChangeClarification("))
        assertTrue(actionBranch.contains("RelativeTemporalSpeechRenderer.semanticClarification()"))
        assertTrue(
            actionBranch.indexOf("matchesResolvedTask(") <
                actionBranch.indexOf("beginOrRetainContextActionChangeClarification(")
        )
        assertTrue(resolver.contains("clearPendingContextActionChangeClarification(restoreContext = false)"))
        assertTrue(resolver.contains("state=STALE"))
        assertTrue(clearSession.contains("clearPendingContextActionChangeClarification(restoreContext = false)"))
        assertTrue(
            actionBranch.indexOf("clearPendingContextActionChangeClarification(restoreContext = true)") <
                actionBranch.indexOf("openContextActionEditScreen(")
        )
    }

    @Test
    fun explicitCancelClearsThePendingChangeBeforeAnyFreshRouting() {
        val command = home
            .substringAfter("private fun handleVoiceCommand(command: String)")
            .substringBefore("lifecycleScope.launch")
        val end = home
            .substringAfter("private fun endAssistantConversation()")
            .substringBefore("private fun clearConversationSessionContext()")
        val clearSession = home
            .substringAfter("private fun clearConversationSessionContext()")
            .substringBefore("private fun clearAccessibleTaskQuerySession(")

        assertTrue(command.indexOf("isConversationExitCommand(normalized)") < command.indexOf("pendingContextTargetOwnsTurn"))
        assertTrue(command.contains("endAssistantConversation()"))
        assertTrue(end.contains("clearConversationSessionContext()"))
        assertTrue(clearSession.contains("clearPendingContextActionChangeClarification(restoreContext = false)"))
    }
}
