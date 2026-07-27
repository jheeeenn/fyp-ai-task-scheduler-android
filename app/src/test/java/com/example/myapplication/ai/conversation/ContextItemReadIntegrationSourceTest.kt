package com.example.myapplication.ai.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContextItemReadIntegrationSourceTest {
    private val home =
        File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
    private val policy =
        File(
            "src/main/java/com/example/myapplication/ai/conversation/taskcontext/" +
                "ContextItemReadPolicy.kt"
        ).readText()

    @Test
    fun boundedDetailReadRunsBeforeGeneralConversationRouting() {
        val command = home
            .substringAfter("private fun handleVoiceCommand(command: String)")
            .substringBefore("lifecycleScope.launch {")

        val boundedRead = command.indexOf("handleContextItemRead(normalized)")
        val generalRouting = command.indexOf("conversationIntentClassifier.classify(")

        assertTrue(boundedRead >= 0)
        assertTrue(boundedRead < generalRouting)
    }

    @Test
    fun routingPromptKeepsExplicitDetailInsteadOfSummary() {
        val prompt = ConversationAgentClient.ROUTING_SYSTEM_PROMPT

        assertTrue(prompt.contains("\"what time\" uses TIME"))
        assertTrue(prompt.contains("\"what date\" uses DATE"))
        assertTrue(
            prompt.contains(
                "Do not use SUMMARY when one of those details is explicitly requested."
            )
        )
    }

    @Test
    fun boundedReadHasNoRoomConversationAgentOrTaskAgentAccess() {
        val handler = home
            .substringAfter("private fun handleContextItemRead(")
            .substringBefore("private fun executeContextRead(")
        val forbidden = listOf(
            "AppDatabase",
            "taskDao",
            "getRootTasks",
            "getSubtasks",
            "agentOrchestrator",
            "conversationOrchestrator.process(",
            "processContextReadRepair("
        )

        forbidden.forEach { token ->
            assertFalse("handler contains $token", handler.contains(token))
            assertFalse("policy contains $token", policy.contains(token))
        }
        assertTrue(handler.contains("ContextItemReadPolicy.resolve("))
        assertTrue(handler.contains("executeContextRead("))
    }

    @Test
    fun authoritativeRendererFocusAndRepeatAreSharedAndWrittenOnce() {
        val executor = home
            .substringAfter("private fun executeContextRead(")
            .substringBefore("private fun handleQueryReadingFollowUp(")
        val success = executor.substringAfter("val item = requireNotNull(validation.item)")

        assertTrue(success.contains("ReadOnlyTaskContextResponseRenderer.render("))
        assertEquals(1, success.split("recordAuthoritativeContextRead(").size - 1)
        assertEquals(
            1,
            success.split("authoritativeRepeatState = AuthoritativeRepeatState(").size - 1
        )
        assertEquals(1, success.split("assistantSession.speak(").size - 1)
        assertFalse(executor.contains("readOnlyTaskContextStore.clear("))
        assertFalse(executor.contains("replaceDailyBriefingResults("))
    }
}
