package com.example.myapplication.ai.conversation

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeActivityTaskContextSourceTest {
    private val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()

    @Test
    fun authoritativeQueryResultsPopulateContextBeforeObservationSpeech() {
        val body = source
            .substringAfter("private suspend fun publishAndSpeakCurrentQueryPage(")
            .substringBefore("private fun buildQueryPageObservation(")
        val replacement = body.indexOf("readOnlyTaskContextStore.replaceRecentQueryResults(")
        val speech = body.indexOf("speakRepeatableObservation(")

        assertTrue(replacement >= 0)
        assertTrue(replacement < speech)
        assertTrue(body.contains("tasks = pageTasks"))
        assertTrue(body.contains("subtasksByParentId = session.subtasksByParentId"))
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
        val explicitEnd = source.substringAfter("private fun endAssistantConversation()").substringBefore("private fun clearConversationSessionContext(")
        val clearHelper = source.substringAfter("private fun clearConversationSessionContext(").substringBefore("private fun handleHomeFollowUp")

        assertTrue(cancelled.contains("clearConversationSessionContext("))
        assertTrue(stopped.contains("clearConversationSessionContext("))
        assertTrue(explicitEnd.contains("clearConversationSessionContext("))
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
    fun contextReadIsValidatedBeforeTaskAgentWithRoomAccessOnlyForSubtasks() {
        val contextReadBranch = source
            .substringAfter("ConversationRoute.CONTEXT_READ ->")
            .substringBefore("ConversationRoute.CONTEXT_ACTION ->")
        val contextReadExecutor = source
            .substringAfter("private fun executeContextRead(")
            .substringBefore("private fun handleQueryReadingFollowUp(")
        val contextBranchStart = source.indexOf("ConversationRoute.CONTEXT_READ ->")
        val taskAgentCall = source.indexOf("agentOrchestrator.process(taskAgentInput)")

        assertTrue(contextReadBranch.contains("ReadOnlyTaskContextReadValidator.validate("))
        assertTrue(contextReadBranch.contains("capturedSnapshot = taskContextCapture.snapshot"))
        assertTrue(contextReadBranch.contains("executeContextRead("))
        assertTrue(contextReadExecutor.contains("ReadOnlyTaskContextResponseRenderer.render("))
        assertTrue(contextReadExecutor.contains("recordAuthoritativeContextRead("))
        assertTrue(contextReadBranch.contains("return@launch"))
        assertTrue(contextBranchStart < taskAgentCall)
        val subtaskRead = contextReadExecutor
            .substringAfter("if (validation.detail == ConversationContextDetail.SUBTASKS)")
            .substringBefore("val speech = ReadOnlyTaskContextResponseRenderer.render(")
        assertTrue(subtaskRead.contains("AuthoritativeSubtaskReader("))
        assertTrue(subtaskRead.contains("dao::getById, dao::getSubtasks"))
        assertTrue(subtaskRead.contains("listenAgain = true"))
        val capturedRead = contextReadExecutor.substringAfter("val speech = ReadOnlyTaskContextResponseRenderer.render(")
        listOf(contextReadBranch, capturedRead).forEach { body ->
            assertTrue(body.contains("agentOrchestrator").not())
            assertTrue(body.contains("AppDatabase").not())
            assertTrue(body.contains("taskDao()").not())
            assertTrue(body.contains("resolveRef(").not())
            assertTrue(body.contains(".id").not())
        }
    }

    @Test
    fun contextReadLogContainsOnlyNonSensitiveValidationMetadata() {
        val contextReadExecutor = source
            .substringAfter("private fun executeContextRead(")
            .substringBefore("private fun handleQueryReadingFollowUp(")
        val log = contextReadExecutor
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
    fun contextualCommandsBypassClassifierAndReachCentralPath() {
        val voiceFlow = source
            .substringAfter("private fun handleVoiceCommand(command: String)")
            .substringBefore("// branches for actions")

        assertTrue(voiceFlow.contains("ContextReferenceMutationGuard.containsContextReference("))
        assertTrue(voiceFlow.contains("contextual reference deferred to Conversation Agent"))
        assertTrue(voiceFlow.contains("conversationOrchestrator.process("))
        val beforeRouteExecution = voiceFlow.substringBefore("when (conversationDecision.route)")
        assertTrue(beforeRouteExecution.contains("CreateTaskActivity").not())
        assertTrue(voiceFlow.contains("ConversationRoute.APP_NAVIGATION"))
        assertTrue(voiceFlow.contains("updateDoneStatus").not())
    }

    @Test
    fun contextActionValidatesResolvesRefRefetchesAndThenOpensEditScreen() {
        val branch = source
            .substringAfter("val taskAgentInput: String")
            .substringAfter("ConversationRoute.CONTEXT_ACTION ->")
            .substringBefore("ConversationRoute.DIRECT_REPLY ->")
        val validation = branch.indexOf("ContextActionDecisionValidator.validate(")
        val grounding = branch.indexOf("ContextActionReferenceGroundingValidator.validate(")
        val resolve = branch.indexOf("readOnlyTaskContextStore.resolveRef(")
        val firstFetch = branch.indexOf("taskDao.getById(privateTaskId)")
        val extraction = branch.indexOf("agentOrchestrator.processContextAction(")
        val secondFetch = branch.indexOf("taskDao.getById(reResolvedTaskId)")
        val open = branch.indexOf("openContextActionEditScreen(")

        assertTrue(validation >= 0)
        assertTrue(grounding >= 0)
        assertTrue(grounding < validation)
        assertTrue(validation < resolve)
        assertTrue(resolve < firstFetch)
        assertTrue(firstFetch < extraction)
        assertTrue(extraction < secondFetch)
        assertTrue(secondFetch < open)
        assertTrue(branch.contains("currentGeneration() != capturedGeneration"))
        assertTrue(branch.contains("reResolvedTaskId != privateTaskId"))
        assertTrue(branch.countOccurrences("readOnlyTaskContextStore.resolveRef(") >= 2)
        assertTrue(branch.contains("readOnlyTaskContextStore.matchesResolvedTask("))
        assertTrue(branch.contains("TaskMatcher").not())
        assertTrue(branch.contains("findTaskMatchResult").not())
        assertTrue(branch.contains("updateDoneStatus").not())
        assertTrue(branch.contains("deleteTask").not())
        assertTrue(branch.contains("insertTask").not())
        assertTrue(branch.contains("normalizedText = normalized"))
        assertTrue(branch.contains("conversationDecision.taskText").not())
        assertTrue(branch.contains("assistantSession.speak(conversationDecision.reply").not())
    }

    private fun String.countOccurrences(needle: String): Int =
        windowed(needle.length).count { it == needle }

    @Test
    fun groundingFailureReturnsBeforeResolutionDatabaseAndExtraction() {
        val branch = source
            .substringAfter("val taskAgentInput: String")
            .substringAfter("ConversationRoute.CONTEXT_ACTION ->")
            .substringBefore("ConversationRoute.DIRECT_REPLY ->")
        val failure = branch
            .substringAfter("if (!grounding.isValid)")
            .substringBefore("val capturedGeneration")

        assertTrue(failure.contains("contextActionTargetQuestion("))
        val question = source
            .substringAfter("private fun contextActionTargetQuestion")
            .substringBefore("private fun sameContextActionTaskSnapshot")
        assertTrue(question.contains("Which task do you want to reschedule?"))
        assertTrue(question.contains("Which task do you want to delete?"))
        assertTrue(question.contains("Which task do you want to edit?"))
        assertTrue(failure.contains("return@launch"))
        assertTrue(failure.contains("resolveRef(").not())
        assertTrue(failure.contains("getById(").not())
        assertTrue(failure.contains("processContextAction(").not())
        assertTrue(failure.contains("TaskMatcher").not())
        assertTrue(failure.contains("EditTaskActivity").not())
    }

    @Test
    fun contextActionEditHelperUsesAuthoritativeTaskAndOnlyPrefillsChanges() {
        val helper = source
            .substringAfter("private suspend fun openContextActionEditScreen(")
            .substringBefore("private fun todayDateString()")

        assertTrue(helper.contains("EditTaskActivity::class.java"))
        assertTrue(helper.contains("putExtra(\"task_id\", task.id)"))
        assertTrue(helper.contains("putExtra(\"task_title\", task.title)"))
        assertTrue(helper.contains("putExtra(\"task_date\", task.dueDate)"))
        assertTrue(helper.contains("putExtra(\"task_time\", task.dueTime)"))
        assertTrue(helper.contains("putExtra(\"opened_by_assistant\", true)"))
        assertTrue(helper.contains("putExtra(\"prefill_title\", extractedChange.replacementTitle)"))
        assertTrue(helper.contains("putExtra(\"prefill_new_date_text\", calculatedTemporal?.schedule?.date)"))
        assertTrue(helper.contains("putExtra(\"prefill_new_time_text\", calculatedTemporal?.schedule?.time)"))
        assertTrue(helper.contains("putExtra(\"assistant_mode\", \"reschedule\")"))
        assertTrue(helper.contains("putExtra(\"relative_temporal_proposal\", calculatedTemporal != null)"))
        assertTrue(helper.contains("startActivity(editIntent)"))
    }

    @Test
    fun contextActionLogsContainNoPrivateIdsTitlesOrDatabaseObjects() {
        val branch = source
            .substringAfter("val taskAgentInput: String")
            .substringAfter("ConversationRoute.CONTEXT_ACTION ->")
            .substringBefore("ConversationRoute.DIRECT_REPLY ->")
        val homeLog = branch
            .substringAfter("\"HOME_CONTEXT_ACTION\"")
            .substringBefore(")\n")
        val resolvedLog = branch
            .substringAfter("\"CONTEXT_ACTION_TARGET_RESOLVED\"")
            .substringBefore(")\n")

        listOf(homeLog, resolvedLog).forEach { log ->
            assertTrue(log.contains("privateTaskId").not())
            assertTrue(log.contains("reResolvedTaskId").not())
            assertTrue(log.contains("task.title").not())
            assertTrue(log.contains("initiallyFetchedTask").not())
            assertTrue(log.contains("authoritativeTask").not())
        }
        assertTrue(homeLog.contains("groundedRef="))
        assertTrue(homeLog.contains("action="))
        assertTrue(homeLog.contains("capturedGeneration="))
        assertTrue(homeLog.contains("validation="))
        assertTrue(resolvedLog.contains("eligible="))
    }

    @Test
    fun contextActionRepairIsMutuallyExclusiveAndUsesSameCapture() {
        val requestFlow = source
            .substringAfter("val taskContextCapture = readOnlyTaskContextStore.capture()")
            .substringBefore("// branches for actions")

        assertTrue(requestFlow.contains("val contextActionRepairEligible = !contextRepairEligible"))
        assertTrue(requestFlow.contains("ContextActionRepairPolicy.shouldAttempt("))
        assertTrue(requestFlow.contains("processContextActionRepair("))
        assertTrue(requestFlow.contains("CONTEXT_ACTION_REPAIR_ATTEMPTED"))
        assertTrue(requestFlow.contains("CONTEXT_ACTION_REPAIR_ACCEPTED"))
        assertTrue(requestFlow.contains("CONTEXT_ACTION_REPAIR_REJECTED"))
        assertTrue(requestFlow.contains("CONTEXT_ACTION_REPAIR_FAILED"))
        assertTrue(requestFlow.contains("capturedSnapshot = taskContextCapture.snapshot"))
        val repairedContextAction = requestFlow
            .substringAfter("ConversationRoute.CONTEXT_ACTION ->")
            .substringBefore("ConversationRoute.ASK_CLARIFICATION ->")
        val structural = repairedContextAction.indexOf("ContextActionDecisionValidator.validate(")
        val grounding = repairedContextAction.indexOf("ContextActionReferenceGroundingValidator.validate(")
        val acceptance = repairedContextAction.indexOf("CONTEXT_ACTION_REPAIR_ACCEPTED")
        assertTrue(structural >= 0)
        assertTrue(grounding >= 0)
        assertTrue(grounding < structural)
        assertTrue(structural < acceptance)
    }

    @Test
    fun queryPageContinuationUsesPrivateSessionWithoutTaskAgent() {
        val handler = source
            .substringAfter("private fun continueTaskQueryPage(")
            .substringBefore("private fun repeatCurrentTaskQueryPage()")

        assertTrue(handler.contains("advanceOnePage()"))
        assertTrue(handler.contains("publishAndSpeakCurrentQueryPage("))
        assertTrue(handler.contains("nextSession"))
        assertTrue(handler.contains("requestToken"))
        assertTrue(handler.contains("authorization"))
        assertTrue(handler.contains("agentOrchestrator").not())
        assertTrue(handler.contains("TaskMatcher").not())
        assertTrue(handler.contains("taskDao").not())
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
        assertTrue(requestFlow.contains("executeContextRead("))
        assertTrue(source.contains("recordAuthoritativeContextRead("))
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
