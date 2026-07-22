package com.example.myapplication.ai.conversation

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeActivityTaskContextSourceTest {
    private val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()

    @Test
    fun authoritativeQueryResultsPopulateContextBeforeObservationSpeech() {
        val body = source.substringAfter("private fun handleQueryTask(").substringBefore("private fun detectQueryReplyMode")
        val afterFilteredResults = body.substringAfter("val filteredTasks = TaskTemporalFilter.filterAndSort")
        val replacement = afterFilteredResults.indexOf("readOnlyTaskContextStore.replaceRecentQueryResults(")
        val speech = afterFilteredResults.indexOf("speakObservation(")

        assertTrue(replacement >= 0)
        assertTrue(replacement < speech)
        assertTrue(afterFilteredResults.contains("tasks = filteredTasks"))
        assertTrue(afterFilteredResults.contains("subtasksByParentId = currentSubtasksByParentId"))
    }

    @Test
    fun ambiguityCandidatesPopulateContextInSpokenOrder() {
        val body = source.substringAfter("private fun askTaskMatchClarification(").substringBefore("private fun handleTaskMatchAmbiguity")

        assertTrue(body.contains("readOnlyTaskContextStore.replaceTaskMatchChoices(listOf(bestTask, secondTask))"))
        assertTrue(body.contains("choices = listOf(bestTask.title, secondTask.title)"))
    }

    @Test
    fun homeCapturesOneSnapshotForPromptAndContextReadValidation() {
        val requestFlow = source
            .substringAfter("Log.d(\"CONVO_ORCH\", \"normalized='")
            .substringBefore("// branches for actions")
        val capture = requestFlow.indexOf("val taskContextCapture = readOnlyTaskContextStore.capture()")
        val request = requestFlow.indexOf("conversationOrchestrator.process(")
        val validation = requestFlow.indexOf("capturedSnapshot = taskContextCapture.snapshot")

        assertTrue(capture >= 0)
        assertTrue(capture < request)
        assertTrue(request < validation)
        assertTrue(requestFlow.contains("readOnlyTaskContextSnapshot = taskContextCapture.promptText"))
        assertTrue(requestFlow.contains("currentGeneration = readOnlyTaskContextStore.currentGeneration()"))
        assertTrue(requestFlow.contains("snapshotForPrompt()").not())
        assertTrue(source.contains("private val readOnlyTaskContextStore = ReadOnlyTaskContextStore()"))
    }

    @Test
    fun cancellationStopAndExplicitEndClearSessionContext() {
        val cancelled = source.substringAfter("override fun onAssistantCancelled()").substringBefore("override fun onAssistantSessionStopped()")
        val stopped = source.substringAfter("override fun onAssistantSessionStopped()").substringBefore("override fun onResume()")
        val explicitEnd = source.substringAfter("private fun endAssistantConversation()").substringBefore("private fun clearConversationSessionContext()")
        val clearHelper = source.substringAfter("private fun clearConversationSessionContext()").substringBefore("private fun handleHomeFollowUp")

        assertTrue(cancelled.contains("clearConversationSessionContext()"))
        assertTrue(stopped.contains("clearConversationSessionContext()"))
        assertTrue(explicitEnd.contains("clearConversationSessionContext()"))
        assertTrue(clearHelper.contains("readOnlyTaskContextStore.clear()"))
        assertTrue(clearHelper.contains("conversationOrchestrator.clearSessionMemory()"))
    }

    @Test
    fun contextualReferenceGuardReturnsBeforeTaskAgentDelegation() {
        val taskCommandBranch = source
            .substringAfter("ConversationRoute.TASK_COMMAND ->")
            .substringBefore("ConversationRoute.UNKNOWN ->")
        val branchStart = source.indexOf("ConversationRoute.TASK_COMMAND ->")
        val guardCall = source.indexOf("ContextReferenceMutationGuard.shouldBlock", branchStart)
        val taskAgentCall = source.indexOf("agentOrchestrator.process(taskAgentInput)", branchStart)

        assertTrue(taskCommandBranch.contains("ContextReferenceMutationGuard.shouldBlock"))
        assertTrue(taskCommandBranch.contains("ContextReferenceMutationGuard.BLOCK_REASON"))
        assertTrue(taskCommandBranch.contains("Please say the task name for that change."))
        assertTrue(taskCommandBranch.contains("listenAgain = true"))
        assertTrue(taskCommandBranch.contains("return@launch"))
        assertTrue(guardCall in (branchStart + 1) until taskAgentCall)
        assertTrue(taskCommandBranch.contains("AppDatabase").not())
        assertTrue(taskCommandBranch.contains("taskDao()").not())
    }

    @Test
    fun contextReadIsValidatedAndRenderedBeforeTaskAgentWithoutRoomAccess() {
        val contextReadBranch = source
            .substringAfter("ConversationRoute.CONTEXT_READ ->")
            .substringBefore("ConversationRoute.DIRECT_REPLY ->")
        val contextBranchStart = source.indexOf("ConversationRoute.CONTEXT_READ ->")
        val taskAgentCall = source.indexOf("agentOrchestrator.process(taskAgentInput)")

        assertTrue(contextReadBranch.contains("ReadOnlyTaskContextReadValidator.validate("))
        assertTrue(contextReadBranch.contains("capturedSnapshot = taskContextCapture.snapshot"))
        assertTrue(contextReadBranch.contains("ReadOnlyTaskContextResponseRenderer.render("))
        assertTrue(contextReadBranch.contains("recordAuthoritativeContextRead("))
        assertTrue(contextReadBranch.contains("return@launch"))
        assertTrue(contextBranchStart < taskAgentCall)
        assertTrue(contextReadBranch.contains("agentOrchestrator").not())
        assertTrue(contextReadBranch.contains("AppDatabase").not())
        assertTrue(contextReadBranch.contains("taskDao()").not())
        assertTrue(contextReadBranch.contains("resolveRef(").not())
        assertTrue(contextReadBranch.contains(".id").not())
    }

    @Test
    fun contextReadLogContainsOnlyNonSensitiveValidationMetadata() {
        val contextReadBranch = source
            .substringAfter("ConversationRoute.CONTEXT_READ ->")
            .substringBefore("ConversationRoute.DIRECT_REPLY ->")
        val log = contextReadBranch
            .substringAfter("Log.d(")
            .substringBefore(")\n")

        assertTrue(log.contains("route="))
        assertTrue(log.contains("ref="))
        assertTrue(log.contains("detail="))
        assertTrue(log.contains("capturedGeneration="))
        assertTrue(log.contains("validation="))
        assertTrue(log.contains("title=").not())
        assertTrue(log.contains("Room").not())
    }

    @Test
    fun resultStatesDoNotLocallyOpenCreationFromClassifierCreateOne() {
        val handler = source
            .substringAfter("private fun handleConversationIntent(")
            .substringBefore("private fun extractSpokenTaskPhrase")
        val summary = handler
            .substringAfter("HomeFollowUpContext.AFTER_TASK_SUMMARY ->")
            .substringBefore("HomeFollowUpContext.AFTER_TASK_DETAILS ->")
        val details = handler
            .substringAfter("HomeFollowUpContext.AFTER_TASK_DETAILS ->")
            .substringBefore("HomeFollowUpContext.DELETE_CONFIRMATION ->")

        assertTrue(summary.contains("ConversationIntent.CREATE_ONE").not())
        assertTrue(details.contains("ConversationIntent.CREATE_ONE").not())
        assertTrue(summary.contains("openCreateTaskFromFollowUp()").not())
        assertTrue(details.contains("openCreateTaskFromFollowUp()").not())
    }

    @Test
    fun contextualCommandsBypassClassifierAndReachCentralPathWithoutScreenTransition() {
        val voiceFlow = source
            .substringAfter("private fun handleVoiceCommand(command: String)")
            .substringBefore("// branches for actions")

        assertTrue(voiceFlow.contains("ContextReferenceMutationGuard.containsContextReference("))
        assertTrue(voiceFlow.contains("contextual reference deferred to Conversation Agent"))
        assertTrue(voiceFlow.contains("conversationOrchestrator.process("))
        assertTrue(voiceFlow.contains("CreateTaskActivity").not())
        assertTrue(voiceFlow.contains("taskDao()").not())
        assertTrue(voiceFlow.contains("updateDoneStatus").not())
    }

    @Test
    fun afterDetailsReadAllRerunsDetailedQueryWithoutTaskAgent() {
        val handler = source
            .substringAfter("private fun handleConversationIntent(")
            .substringBefore("private fun extractSpokenTaskPhrase")
        val details = handler
            .substringAfter("HomeFollowUpContext.AFTER_TASK_DETAILS ->")
            .substringBefore("HomeFollowUpContext.DELETE_CONFIRMATION ->")

        assertTrue(details.contains("ConversationIntent.READ_ALL"))
        assertTrue(details.contains("handleDetailedFollowUpQuery()"))
        assertTrue(details.contains("agentOrchestrator").not())
    }

    @Test
    fun repairUsesSameCaptureAndIsLimitedToPrimaryAgentAbstention() {
        val requestFlow = source
            .substringAfter("val taskContextCapture = readOnlyTaskContextStore.capture()")
            .substringBefore("// branches for actions")

        assertTrue(requestFlow.contains("ContextReadRepairPolicy.shouldAttempt("))
        assertTrue(requestFlow.contains("readOnlyTaskContextSnapshot = taskContextCapture.promptText"))
        assertTrue(requestFlow.contains("capturedSnapshot = taskContextCapture.snapshot"))
        assertTrue(requestFlow.contains("processContextReadRepair("))
        assertTrue(requestFlow.contains("CONTEXT_REPAIR_ATTEMPTED"))
        assertTrue(requestFlow.contains("CONTEXT_REPAIR_ACCEPTED"))
        assertTrue(requestFlow.contains("CONTEXT_REPAIR_ABSTAINED"))
        assertTrue(requestFlow.contains("CONTEXT_REPAIR_REJECTED"))
        assertTrue(requestFlow.contains("CONTEXT_REPAIR_FAILED"))
    }

    @Test
    fun homePassesTypedFocusAndCommitsOnlyFinalSpokenRoutes() {
        val requestFlow = source
            .substringAfter("val taskContextCapture = readOnlyTaskContextStore.capture()")
            .substringBefore("// branches for actions")
        assertTrue(requestFlow.contains("contextFocusForSnapshot("))
        assertTrue(requestFlow.contains("taskContextCapture.snapshot"))
        assertTrue(requestFlow.contains("contextFocus = contextFocus"))
        assertTrue(requestFlow.contains("Current validated task focus").not())
        assertTrue(requestFlow.contains("ConversationRoute.ASK_CLARIFICATION ->"))
        assertTrue(requestFlow.contains("ConversationRoute.DIRECT_REPLY ->"))
        assertTrue(requestFlow.contains("conversationOrchestrator.commitFinalDecision(conversationDecision)"))
        assertTrue(requestFlow.contains("ConversationRoute.CONTEXT_READ ->"))
        assertTrue(requestFlow.contains("recordAuthoritativeContextRead("))
    }

    @Test
    fun contextFocusFallbackUsesCapturedSnapshotValidatorAndSafeLogs() {
        val requestFlow = source
            .substringAfter("val taskContextCapture = readOnlyTaskContextStore.capture()")
            .substringBefore("// branches for actions")
        val fallback = requestFlow
            .substringAfter("ContextFocusCarryForwardPolicy.resolve(")
            .substringBefore("if (conversationDecision.route != ConversationRoute.CONTEXT_READ)")

        assertTrue(fallback.contains("capturedSnapshot = taskContextCapture.snapshot"))
        assertTrue(fallback.contains("ReadOnlyTaskContextReadValidator.validate("))
        assertTrue(fallback.contains("currentGeneration = readOnlyTaskContextStore.currentGeneration()"))
        assertTrue(fallback.contains("CONTEXT_FOCUS_FALLBACK_ACCEPTED"))
        assertTrue(fallback.contains("CONTEXT_FOCUS_FALLBACK_REJECTED"))
        assertTrue(requestFlow.contains("CONTEXT_FOCUS_AVAILABLE"))
        assertTrue(requestFlow.contains("CONTEXT_FOCUS_STALE"))
        assertTrue(fallback.contains("title=").not())
        assertTrue(fallback.contains("Room").not())
        assertTrue(fallback.contains("resolveRef(").not())
        assertTrue(fallback.contains("agentOrchestrator").not())
    }

    @Test
    fun captureLoggingContainsOnlySnapshotMetadata() {
        val captureLog = source
            .substringAfter("\"HOME_CONTEXT_CAPTURE\"")
            .substringBefore(")\n")

        assertTrue(captureLog.contains("scope="))
        assertTrue(captureLog.contains("generation="))
        assertTrue(captureLog.contains("itemCount="))
        assertTrue(captureLog.contains("truncated="))
        assertTrue(captureLog.contains("title").not())
        assertTrue(captureLog.contains("dueDate").not())
        assertTrue(captureLog.contains("dueTime").not())
        assertTrue(captureLog.contains("Room").not())
    }
}
