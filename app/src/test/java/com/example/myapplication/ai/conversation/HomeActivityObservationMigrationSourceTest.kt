package com.example.myapplication.ai.conversation

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class HomeActivityObservationMigrationSourceTest {
    private val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()

    @Test fun detailedQueryUsesStructuredPageAndNeverReplyLengthCoverage() {
        val body = source.substringAfter("private fun buildQueryPageObservation(")
            .substringBefore("private fun taskQuerySpeechDetail(")
        assertTrue(body.contains("session.currentPageTasks.map"))
        assertTrue(body.contains("session.presentation == TaskQueryPresentation.DETAILS"))
        assertTrue(body.contains("queryPage = TaskQueryPageObservation("))
        assertFalse(body.contains("getMaxTasksForMode"))
        assertFalse(body.contains(".take("))
    }

    @Test fun initialQueryExposesStructuredTasksAndGuidanceButNotFallbackAsFacts() {
        val body = source.substringAfter("private fun handleQueryTask(")
            .substringBefore("\n    private fun buildNoTasksQueryReply(")
        assertTrue(body.contains("val filteredTasks = TaskTemporalFilter.filterAndSort"))
        assertTrue(body.contains("orderedTasks = filteredTasks"))
        assertTrue(body.contains("dateText = queryWindow.spokenLabel"))
        assertTrue(body.contains("Offer to create a new task."))
        assertTrue(body.contains("tasks = emptyList()"))
        assertTrue(body.contains("buildCountOnlyQueryObservation(session)"))
        assertTrue(body.contains("publishAndSpeakCurrentQueryPage("))
        assertTrue(body.contains("requestToken"))
        assertTrue(body.contains("authorization"))
        assertFalse(body.contains("facts = listOf(spokenReply)"))
    }

    @Test fun ambiguityResolvedMutationUsesCmasObservationPath() {
        val body = source.substringAfter("private fun handleTaskMatchAmbiguity").substringBefore("private fun speakThenOpen")
        assertTrue(body.contains("speakObservationThenRun(ExecutionObservation(ExecutionOperation.UPDATE_TASK"))
        assertTrue(body.contains("speakObservationThenRun(ExecutionObservation(ExecutionOperation.RESCHEDULE_TASK"))
        assertTrue(body.contains("speakObservation(ExecutionObservation(ExecutionOperation.MARK_DONE"))
        assertTrue(body.contains("chosenTask.copy(isDone = true)"))
        assertTrue(body.contains("speakObservation(ExecutionObservation(ExecutionOperation.MARK_UNDONE"))
        assertTrue(body.contains("chosenTask.copy(isDone = false)"))
    }

    @Test fun deleteCancellationProducesCancelledObservation() {
        val body = source
            .substringAfter("private fun handleConversationFollowUp")
            .substringAfter("HomeFollowUpContext.DELETE_CONFIRMATION -> {")
            .substringBefore("HomeFollowUpContext.BREAKDOWN_CONFIRMATION")
        assertTrue(body.contains("ExecutionOperation.DELETE_TASK"))
        assertTrue(body.contains("ExecutionOutcome.CANCELLED"))
    }

    @Test fun operationalResponsesUseProtectedConversationVerbalizationWithAndroidFallback() {
        val body = source
            .substringAfter("private fun renderObservationResponse")
            .substringBefore("private fun deliverObservationResponse")

        assertTrue(body.contains("AndroidObservationResponseRenderer.render(observation)"))
        assertTrue(body.contains("conversationOrchestrator.respondToObservation("))
        assertTrue(body.contains("responseVerbalizationTone()"))
        assertTrue(body.contains("responseVerbalizationVerbosity()"))
        assertTrue(body.contains("conversationOrchestrator.recordDeliveredObservationResponse(observation, response)"))
        assertTrue(body.contains("source=${'$'}{response.source}"))
    }

    @Test fun observationDeliveryPreservesHintListenAgainAndNavigationCallback() {
        val deliveryBody = source
            .substringAfter("private fun deliverObservationResponse")
            .substringBefore("private suspend fun speakObservation")
        val speakBody = source
            .substringAfter("private suspend fun speakObservation")
            .substringBefore("private fun observedTask")

        assertTrue(deliveryBody.contains("response.hint.ifBlank { observation.fallbackHint }"))
        assertTrue(deliveryBody.contains("observation.listenAgain"))
        assertTrue(deliveryBody.contains("assistantSession.speakThenRun(response.speech) { afterSpeech() }"))
        assertTrue(deliveryBody.contains("assistantSession.speakThenListenAgain(response.speech)"))
        assertTrue(deliveryBody.contains("assistantSession.speakThenStop("))
        assertTrue(deliveryBody.contains("dismissPanel = true"))
        assertFalse(
            deliveryBody.contains(
                "assistantSession.speak(response.speech, listenAgain = false)"
            )
        )
        assertTrue(speakBody.contains("val response = renderObservationResponse(observation)"))
        assertTrue(speakBody.contains("recordObservationResponse(observation, response)"))
        assertTrue(speakBody.contains("deliverObservationResponse(observation, response)"))
        assertTrue(speakBody.contains("deliverObservationResponse(observation, response, action)"))
        assertTrue(speakBody.contains("ResponseVerbalizationDeliveryGuard.runIfCurrent("))
    }

    @Test fun naturalConversationAndLocalFollowUpInfrastructureRemainPresent() {
        val directReplyBody = source
            .substringAfter("ConversationRoute.DIRECT_REPLY ->")
            .substringBefore("ConversationRoute.ASK_CLARIFICATION ->")

        assertTrue(directReplyBody.contains("conversationDecision.reply"))
        assertTrue(source.contains("conversationIntentClassifier = LocalConversationIntentClassifier(this)"))
        assertTrue(source.contains("private enum class HomeFollowUpContext"))
        assertTrue(source.contains("private fun handleHomeFollowUp("))
        assertTrue(source.contains("private fun handleConversationIntent("))
    }
}
