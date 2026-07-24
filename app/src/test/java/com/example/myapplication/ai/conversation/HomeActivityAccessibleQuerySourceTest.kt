package com.example.myapplication.ai.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeActivityAccessibleQuerySourceTest {
    private val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()

    @Test
    fun structuredPresentationIsAuthoritativeAndPhraseDetectionIsGone() {
        val queryCall = source
            .substringAfter("handleQueryTask(")
            .substringBefore(")")
        assertTrue(queryCall.contains("presentation = aiResult.queryPresentation"))
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
        val speak = publisher.indexOf("speakObservation(buildQueryPageObservation(session))")

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
            .substringBefore("private fun endAssistantConversation()")

        assertTrue(body.contains("val contextGeneration = readOnlyTaskContextStore.currentGeneration()"))
        assertTrue(body.contains("speakObservation(buildQueryPageObservation(session))"))
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
        assertTrue(clear.contains("readOnlyTaskContextStore.clear()"))
        assertTrue(source.substringAfter("override fun onAssistantCancelled()").substringBefore("override fun onAssistantSessionStopped()").contains("clearConversationSessionContext()"))
        assertTrue(source.substringAfter("override fun onAssistantSessionStopped()").substringBefore("override fun onResume()").contains("clearConversationSessionContext()"))
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
            .substringBefore("speakObservation(buildQueryPageObservation(session))")

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
