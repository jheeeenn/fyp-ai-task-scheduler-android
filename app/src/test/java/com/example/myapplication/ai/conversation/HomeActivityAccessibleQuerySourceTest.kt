package com.example.myapplication.ai.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeActivityAccessibleQuerySourceTest {
    private val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()

    @Test
    fun structuredPresentationIsAuthoritativeAndPhraseDetectionIsGone() {
        assertTrue(source.contains("TaskQueryPresentationReconciler.reconcile("))
        assertTrue(source.contains("presentation = presentationResolution.effective"))
        assertFalse(source.contains("detectQueryReplyMode"))
        assertFalse(source.contains("getMaxTasksForMode"))
        assertFalse(source.contains("QueryReplyMode"))
    }

    @Test
    fun queryCreatesPrivateFixedPageSessionAndNewQueryClearsTheOldOne() {
        val body = source
            .substringAfter("private fun handleQueryTask(")
            .substringBefore("\n    private fun buildNoTasksQueryReply(")
        val clear = body.indexOf("clearAccessibleTaskQuerySession(clearTaskContext = true)")
        val roomQuery = body.indexOf("dao.getRootTasks()")
        val session = body.indexOf("val session = AccessibleTaskQuerySession(")

        assertTrue(source.contains("private var accessibleTaskQuerySession"))
        assertTrue(clear >= 0)
        assertTrue(clear < roomQuery)
        assertTrue(roomQuery < session)
        assertTrue(body.contains("presentation = presentation"))
        assertTrue(body.contains("\"HOME_QUERY_READING_SESSION\""))
        assertTrue(body.contains("pageSize=${'$'}{session.pageSize}"))
    }

    @Test
    fun noResultsAndCountOnlyClearOrExposeNoPublicTaskItems() {
        val body = source
            .substringAfter("private fun handleQueryTask(")
            .substringBefore("\n    private fun buildNoTasksQueryReply(")
        val countObservation = source
            .substringAfter("private fun buildCountOnlyQueryObservation(")
            .substringBefore("private suspend fun publishAndSpeakCurrentQueryPage(")

        assertTrue(body.contains("filteredTasks.isEmpty()"))
        assertTrue(body.contains("accessibleTaskQuerySession = null"))
        assertTrue(body.contains("currentSubtasksByParentId = emptyMap()"))
        assertTrue(countObservation.contains("tasks = emptyList()"))
        assertTrue(countObservation.contains("presentation = TaskQueryPresentationLevel.COUNT_ONLY"))
        assertFalse(countObservation.contains("replaceRecentQueryResults"))
    }

    @Test
    fun pageContextIsReplacedBeforeSpeechInExactlySpokenOrder() {
        val publisher = source
            .substringAfter("private suspend fun publishAndSpeakCurrentQueryPage(")
            .substringBefore("private fun buildQueryPageObservation(")
        val observation = source
            .substringAfter("private fun buildQueryPageObservation(")
            .substringBefore("private fun taskQuerySpeechDetail(")
        val replace = publisher.indexOf("readOnlyTaskContextStore.replaceRecentQueryResults(")
        val speak = publisher.indexOf("speakRepeatableObservation(")

        assertTrue(replace >= 0)
        assertTrue(replace < speak)
        assertTrue(publisher.contains("tasks = pageTasks"))
        assertTrue(observation.contains("tasks = session.currentPageTasks.map"))
        assertTrue(publisher.countOccurrences("replaceRecentQueryResults(") == 1)
    }

    @Test
    fun continueReplacesOnceWithoutMatcherRoomQueryOrMutation() {
        val body = source
            .substringAfter("private fun continueTaskQueryPage()")
            .substringBefore("private fun repeatCurrentTaskQueryPage()")

        assertTrue(body.contains("advanceOnePage()"))
        assertTrue(body.countOccurrences("publishAndSpeakCurrentQueryPage(nextSession)") == 1)
        assertTrue(body.contains("\"HOME_QUERY_PAGE_CONTINUE\""))
        assertTrue(body.contains("\"That was the last group.\""))
        listOf(
            "TaskMatcher",
            "AppDatabase",
            "taskDao",
            "getRootTasks",
            "getSubtasks",
            "insertTask",
            "deleteTask",
            "updateDoneStatus"
        ).forEach { forbidden -> assertFalse(body.contains(forbidden)) }
    }

    @Test
    fun repeatDoesNotReplaceContextAndChecksGenerationStability() {
        val body = source
            .substringAfter("private fun repeatCurrentTaskQueryPage()")
            .substringBefore("private fun repeatLastAuthoritativeSpeech()")

        assertTrue(body.contains("val contextGeneration = readOnlyTaskContextStore.currentGeneration()"))
        assertTrue(body.contains("speakRepeatableObservation("))
        assertTrue(body.contains("contextGenerationUnchanged=true"))
        assertTrue(body.contains("currentGeneration() == contextGeneration"))
        assertFalse(body.contains("replaceRecentQueryResults"))
        assertFalse(body.contains("advanceOnePage"))
    }

    @Test
    fun countAgreementStartsFirstOverviewPageAndStopPathsClearSession() {
        val followUp = source
            .substringAfter("private fun handleQueryReadingFollowUp(")
            .substringBefore("private fun startQueryOverviewFromCount()")
        val start = source
            .substringAfter("private fun startQueryOverviewFromCount()")
            .substringBefore("private fun continueTaskQueryPage()")
        val clear = source
            .substringAfter("private fun clearAccessibleTaskQuerySession(")
            .substringBefore("private fun handleHomeFollowUp")

        assertTrue(followUp.contains("HomeFollowUpContext.QUERY_COUNT"))
        assertTrue(followUp.contains("isSimpleFollowUpAgreement(normalized)"))
        assertTrue(start.contains("session.beginOverview()"))
        assertTrue(start.contains("publishAndSpeakCurrentQueryPage(overviewSession)"))
        assertTrue(clear.contains("accessibleTaskQuerySession = null"))
        assertTrue(clear.contains("authoritativeRepeatState = null"))
        assertTrue(clear.contains("readOnlyTaskContextStore.clear()"))
        assertTrue(source.substringAfter("override fun onAssistantCancelled()").substringBefore("override fun onAssistantSessionStopped()").contains("clearConversationSessionContext()"))
        assertTrue(source.substringAfter("override fun onAssistantSessionStopped()").substringBefore("override fun onResume()").contains("clearConversationSessionContext()"))
    }

    @Test
    fun acceptedStructuredRepeatUsesOnlyAndroidOwnedSpeechAndPreservesContext() {
        val executor = source
            .substringAfter("private fun executeQueryReadingControl(")
            .substringBefore("private fun currentQueryReadingInteractionState()")
        val repeat = source
            .substringAfter("private fun repeatLastAuthoritativeSpeech()")
            .substringBefore("private fun endAssistantConversation()")

        assertTrue(executor.contains("QueryReadingControlPolicy.validate("))
        assertTrue(executor.contains("ConversationQueryReadingMove.REPEAT_LAST -> repeatLastAuthoritativeSpeech()"))
        assertTrue(repeat.contains("assistantSession.speak(repeatState.speech"))
        assertTrue(repeat.contains("currentGeneration() == contextGeneration"))
        listOf(
            "conversationOrchestrator.process(",
            "agentOrchestrator.process(",
            "replaceRecentQueryResults(",
            "dao.",
            "getRootTasks",
            "getSubtasks",
            "TaskMatcher"
        ).forEach { forbidden -> assertFalse(repeat.contains(forbidden)) }
    }

    @Test
    fun authoritativeRepeatStateIsWrittenOnlyAfterDeterministicPageCountOrValidatedContextRead() {
        val repeatableObservation = source
            .substringAfter("private suspend fun speakRepeatableObservation(")
            .substringBefore("private suspend fun speakObservationThenRun(")
        val contextRead = source
            .substringAfter("ConversationRoute.CONTEXT_READ ->")
            .substringBefore("ConversationRoute.CONTEXT_ACTION ->")

        assertTrue(repeatableObservation.contains("val response = renderObservationResponse(observation)"))
        assertTrue(repeatableObservation.contains("speech = response.speech"))
        assertTrue(contextRead.indexOf("if (!validation.isValid)") < contextRead.indexOf("authoritativeRepeatState = AuthoritativeRepeatState("))
        assertTrue(contextRead.contains("kind = RepeatableSpeechKind.CONTEXT_READ"))
        assertFalse(repeatableObservation.contains("TaskEntity"))
        assertFalse(repeatableObservation.contains(".id"))
    }

    @Test
    fun queryReadingControlIsCentralSemanticFallbackWhileExactHandlersStayBounded() {
        val exact = source
            .substringAfter("private fun handleQueryReadingFollowUp(")
            .substringBefore("private fun executeQueryReadingControl(")
        val routes = source
            .substringAfter("when (conversationDecision.route)")
            .substringBefore("val taskAgentInput: String")

        assertTrue(exact.contains("HomeFollowUpContext.QUERY_COUNT"))
        assertTrue(exact.contains("HomeFollowUpContext.QUERY_PAGE"))
        assertFalse(exact.contains("can you say that again"))
        assertFalse(exact.contains("repeat the group"))
        assertFalse(exact.contains("read the next group"))
        assertTrue(routes.contains("ConversationRoute.QUERY_READING_CONTROL"))
        assertTrue(routes.contains("executeQueryReadingControl(conversationDecision.queryReadingMove)"))
    }

    @Test
    fun queryPageRemainsReachableThroughEveryContextualCentralRouteGate() {
        val voiceFlow = source
            .substringAfter("private fun handleVoiceCommand(command: String)")
            .substringBefore("// branches for actions")

        assertTrue(voiceFlow.contains("homeFollowUpContext == HomeFollowUpContext.QUERY_PAGE"))
        assertTrue(voiceFlow.contains("ContextReferenceMutationGuard.containsContextReference("))
        assertTrue(voiceFlow.contains("val isResultInteraction"))
        assertTrue(voiceFlow.contains("ContextReadRepairPolicy.shouldAttempt("))
        assertTrue(voiceFlow.contains("ContextActionRepairPolicy.shouldAttempt("))
        assertTrue(voiceFlow.contains("conversationOrchestrator.process("))
    }

    @Test
    fun queryLogsContainMetadataButNoIdsTitlesEntitiesOrSpeech() {
        val queryRegion = source
            .substringAfter("\"HOME_QUERY_READING_SESSION\"")
            .substringBefore("\n    private fun buildNoTasksQueryReply(")
        val pageRegion = source
            .substringAfter("\"HOME_QUERY_PAGE\"")
            .substringBefore("speakRepeatableObservation(")

        listOf(queryRegion, pageRegion).forEach { log ->
            assertFalse(log.contains("task.id"))
            assertFalse(log.contains("task.title"))
            assertFalse(log.contains("TaskEntity"))
            assertFalse(log.contains("fallbackSpeech"))
        }
        assertTrue(pageRegion.contains("pageNumber="))
        assertTrue(pageRegion.contains("pageCount="))
        assertTrue(pageRegion.contains("pageItemCount="))
        assertTrue(pageRegion.contains("contextGeneration="))
    }

    @Test
    fun temporalResolutionAndFilteringStillUseTheExistingProductionComponents() {
        val body = source
            .substringAfter("private fun handleQueryTask(")
            .substringBefore("\n    private fun buildNoTasksQueryReply(")

        assertTrue(body.contains("temporalQueryResolver.resolve("))
        assertTrue(body.contains("TaskTemporalFilter.filterAndSort(allTasks, queryWindow)"))
        assertTrue(body.contains("TemporalResolutionStatus.UNRESOLVED"))
    }

    private fun String.countOccurrences(needle: String): Int =
        windowed(needle.length).count { it == needle }
}
