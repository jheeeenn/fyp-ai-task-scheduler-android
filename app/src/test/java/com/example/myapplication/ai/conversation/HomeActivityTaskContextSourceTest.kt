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
        assertTrue(contextReadBranch.contains("recordContextReadResponse(speech)"))
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
}
