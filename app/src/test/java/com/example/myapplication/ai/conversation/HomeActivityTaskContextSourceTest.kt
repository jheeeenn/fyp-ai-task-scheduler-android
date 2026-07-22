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
    fun homePassesOnlyPromptSnapshotToConversationOrchestrator() {
        assertTrue(source.contains("readOnlyTaskContextSnapshot = readOnlyTaskContextStore.snapshotForPrompt()"))
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
}
