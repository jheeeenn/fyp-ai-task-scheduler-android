package com.example.myapplication.ai.conversation

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class HomeActivityObservationMigrationSourceTest {
    private val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()

    @Test fun detailedFollowUpQueryUsesLastQueryWindowAndDetailedMode() {
        val body = source.substringAfter("private fun handleDetailedFollowUpQuery()").substringBefore("private fun openCreateTaskFromFollowUp")
        assertTrue(body.contains("dateText = lastQueryWindow.spokenLabel"))
        assertTrue(body.contains("responseManager.getMaxTasksForMode(QueryDetailMode.DETAILED)"))
        assertTrue(body.contains("detail = if (filteredTasks.isEmpty())"))
        assertTrue(body.contains("Offer to create a new task."))
        assertTrue(body.contains("After giving the task details, ask whether the user needs anything else."))
        assertTrue(body.contains("fallbackSpeech = spokenReply"))
        assertFalse(body.contains("facts = listOf(spokenReply)"))
        assertTrue(body.contains("val hint = responseManager.hintCreateOrRead()"))
        assertFalse(body.contains("showAssistantReply(reply)"))
        assertFalse(body.contains("setIdleState()"))
    }

    @Test fun initialQueryExposesStructuredTasksAndGuidanceButNotFallbackAsFacts() {
        val body = source.substringAfter("private fun handleQueryTask(").substringBefore("private fun detectQueryReplyMode")
        assertTrue(body.contains("taskCount = filteredTasks.size"))
        assertTrue(body.contains("dateText = queryWindow.spokenLabel"))
        assertTrue(body.contains("detail = responseGuidance"))
        assertTrue(body.contains("tasks = filteredTasks.take(responseManager.getMaxTasksForMode"))
        assertTrue(body.contains(".map { observedTask(it) }"))
        assertTrue(body.contains("Offer to create a new task."))
        assertTrue(body.contains("After giving the requested summary, offer to read more task details."))
        assertTrue(body.contains("After giving the task details, ask whether the user needs anything else."))
        assertTrue(body.contains("fallbackSpeech = spokenReply"))
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

    @Test fun operationalResponsesUseAndroidRendererWithoutResponseAgent() {
        val body = source
            .substringAfter("private fun renderObservationResponse")
            .substringBefore("private fun deliverObservationResponse")

        assertTrue(body.contains("AndroidObservationResponseRenderer.render(observation)"))
        assertTrue(body.contains("conversationOrchestrator.recordDeterministicObservation(observation, response)"))
        assertTrue(body.contains("source=${'$'}{response.source}"))
        assertFalse(source.contains("conversationOrchestrator.respondToObservation("))
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
        assertTrue(speakBody.contains("deliverObservationResponse(observation, renderObservationResponse(observation))"))
        assertTrue(speakBody.contains("deliverObservationResponse(observation, renderObservationResponse(observation), action)"))
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
