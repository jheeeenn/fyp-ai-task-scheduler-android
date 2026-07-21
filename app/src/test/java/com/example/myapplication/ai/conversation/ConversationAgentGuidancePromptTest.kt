package com.example.myapplication.ai.conversation

import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationAgentGuidancePromptTest {
    private val prompt = ConversationAgentClient.ROUTING_SYSTEM_PROMPT

    @Test
    fun distinguishesGuidanceFromExecution() {
        assertTrue(prompt.contains("Guidance and execution distinction"))
        assertTrue(prompt.contains("\"How do I create a task?\" is DIRECT_REPLY"))
        assertTrue(prompt.contains("\"Create a task called revision\" is TASK_COMMAND"))
        assertTrue(prompt.contains("\"Can you delete tasks?\" is DIRECT_REPLY"))
        assertTrue(prompt.contains("\"Delete the revision task\" is TASK_COMMAND"))
    }

    @Test
    fun appContextIsAuthoritativeAndUnsupportedFeaturesAreNotInvented() {
        assertTrue(prompt.contains("App context is the only authority for app guidance"))
        assertTrue(prompt.contains("Do not invent screens, buttons, features"))
        assertTrue(prompt.contains("available integrations"))
    }

    @Test
    fun guidanceUsesAccessibleSpokenStyle() {
        assertTrue(prompt.contains("spoken TTS delivery"))
        assertTrue(prompt.contains("one to three short sentences"))
        assertTrue(prompt.contains("Avoid visual-only instructions"))
        assertTrue(prompt.contains("Long-press the Talk Assistant button to type"))
    }

    @Test
    fun unsafeOrUnclearRoutingFailsClosed() {
        assertTrue(prompt.contains("without authoritatively supplied selectable choices"))
        assertTrue(prompt.contains("ASK_CLARIFICATION"))
        assertTrue(prompt.contains("Do not use TASK_COMMAND merely because"))
        assertTrue(prompt.contains("\"task\", \"schedule\", \"class\", or a date"))
        assertTrue(prompt.contains("Operational success must never be claimed by routing"))
    }
}
