package com.example.myapplication.ai.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeActivityAppGuidanceSourceTest {
    private val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
    private val contextBuilder = source
        .substringAfter("private fun buildConversationAppContextSummary()")
        .substringBefore("private fun handleVoiceCommand")

    @Test
    fun buildsTrustedHomeGuidanceWithVoiceAndTypedInput() {
        assertTrue(contextBuilder.contains("AppGuidanceContext("))
        assertTrue(contextBuilder.contains("currentScreen = \"Home\""))
        assertTrue(contextBuilder.contains("Activate the Talk Assistant button to speak."))
        assertTrue(contextBuilder.contains("Long-press the Talk Assistant button to type"))
        assertTrue(contextBuilder.contains("Voice and typed inputs use the same assistant pipeline."))
        assertTrue(contextBuilder.contains("interactionState = homeFollowUpContext.name"))
        assertTrue(contextBuilder.contains("return guidanceContext.toPromptText()"))
        assertFalse(contextBuilder.contains("Android-generated speech"))
        assertTrue(contextBuilder.contains("The app reads verified task-query results."))
        assertTrue(
            contextBuilder.contains(
                "App guidance must not claim that an operation occurred unless the app successfully completed it."
            )
        )
    }

    @Test
    fun everyHomeFollowUpStateHasUserFacingGuidanceWithoutPendingIds() {
        listOf(
            "HomeFollowUpContext.NONE -> Pair(",
            "HomeFollowUpContext.AFTER_NO_TASKS -> Pair(",
            "HomeFollowUpContext.AFTER_TASK_SUMMARY -> Pair(",
            "HomeFollowUpContext.AFTER_TASK_DETAILS -> Pair(",
            "HomeFollowUpContext.QUERY_COUNT -> Pair(",
            "HomeFollowUpContext.QUERY_PAGE -> Pair(",
            "HomeFollowUpContext.TASK_MATCH_AMBIGUITY -> Pair(",
            "HomeFollowUpContext.DELETE_CONFIRMATION -> Pair(",
            "HomeFollowUpContext.BREAKDOWN_CONFIRMATION -> Pair(",
            "HomeFollowUpContext.BREAKDOWN_SCHEDULE_COLLECTION -> Pair("
        ).forEach { branch -> assertTrue("Missing guidance branch: $branch", contextBuilder.contains(branch)) }

        assertFalse(contextBuilder.contains("pendingDeleteTaskId"))
        assertFalse(contextBuilder.contains("candidate1Id"))
        assertFalse(contextBuilder.contains("candidate2Id"))
        assertFalse(contextBuilder.contains("lastQueryDate"))
        assertFalse(contextBuilder.contains("lastQueryWindow"))
        assertFalse(contextBuilder.contains("TaskEntity"))
        assertFalse(contextBuilder.contains("task.title"))
    }

    @Test
    fun conversationAndAndroidAuthorityBoundariesRemainIntact() {
        val directReplyBody = source
            .substringAfter("ConversationRoute.DIRECT_REPLY ->")
            .substringBefore("ConversationRoute.ASK_CLARIFICATION ->")
        val taskCommandBody = source
            .substringAfter("ConversationRoute.TASK_COMMAND ->")
            .substringBefore("ConversationRoute.UNKNOWN ->")
        val observationBody = source
            .substringAfter("private fun renderObservationResponse")
            .substringBefore("private fun deliverObservationResponse")

        assertTrue(source.contains("LocalConversationIntentClassifier"))
        assertTrue(source.contains("private fun handleConversationIntent("))
        assertTrue(source.contains("private fun handleHomeFollowUp("))
        assertTrue(directReplyBody.contains("conversationDecision.reply"))
        assertTrue(taskCommandBody.contains("conversationDecision.taskText"))
        assertTrue(source.contains("agentOrchestrator.process(taskAgentInput)"))
        assertTrue(observationBody.contains("AndroidObservationResponseRenderer.render(observation)"))
        assertTrue(observationBody.contains("conversationOrchestrator.respondToObservation("))
        assertTrue(observationBody.contains("responseVerbalizationTone()"))
        assertTrue(observationBody.contains("responseVerbalizationVerbosity()"))
    }

    @Test
    fun resultFollowUpGuidanceSupportsReadsButNotRelativeMutation() {
        val summary = contextBuilder
            .substringAfter("HomeFollowUpContext.AFTER_TASK_SUMMARY -> Pair(")
            .substringBefore("HomeFollowUpContext.AFTER_TASK_DETAILS -> Pair(")
        val details = contextBuilder
            .substringAfter("HomeFollowUpContext.AFTER_TASK_DETAILS -> Pair(")
            .substringBefore("HomeFollowUpContext.TASK_MATCH_AMBIGUITY -> Pair(")

        listOf(summary, details).forEach { guidance ->
            assertTrue(guidance.contains("Ask what the first, second, or another supplied result was."))
            assertTrue(guidance.contains("date, time, status, or subtask summary"))
            assertTrue(guidance.contains("using the task name"))
            assertFalse(guidance.contains("Delete the second"))
            assertFalse(guidance.contains("mutate the supplied result"))
        }
    }
}
