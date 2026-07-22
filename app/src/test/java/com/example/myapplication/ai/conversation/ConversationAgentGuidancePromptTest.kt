package com.example.myapplication.ai.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationAgentGuidancePromptTest {
    private val prompt = ConversationAgentClient.ROUTING_SYSTEM_PROMPT

    @Test
    fun acknowledgesHybridLocalInteractionBoundary() {
        assertTrue(prompt.contains("bounded local interaction handlers did not already resolve"))
        assertTrue(prompt.contains("open natural conversation, app guidance, routing and open-ended clarification"))
        assertFalse(prompt.contains("Every user utterance is sent to you first"))
    }

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
        assertTrue(prompt.contains("no authoritative read-only task context supplies the answer"))
        assertTrue(prompt.contains("ASK_CLARIFICATION"))
        assertTrue(prompt.contains("Do not use TASK_COMMAND merely because"))
        assertTrue(prompt.contains("\"task\", \"schedule\", \"class\", or a date"))
        assertTrue(prompt.contains("Operational success must never be claimed by routing"))
        assertTrue(prompt.contains("Never claim that an operation succeeded, completed, or changed task data"))
    }

    @Test
    fun contextualFactsUseStructuredAndroidRenderedRoute() {
        assertTrue(prompt.contains("CONTEXT_READ"))
        assertTrue(prompt.contains("select exactly one supplied temporary ref"))
        assertTrue(prompt.contains("Keep task_text and reply empty"))
        assertTrue(prompt.contains("Android will verify the ref"))
        assertTrue(prompt.contains("Contextual examples are illustrative, not an exhaustive phrase dictionary"))
        assertTrue(prompt.contains("User: What was the second one?"))
        assertTrue(prompt.contains("\"context_ref\":\"T2\",\"context_detail\":\"SUMMARY\""))
        assertTrue(prompt.contains("User: Delete the second one."))
        assertTrue(prompt.contains("\"route\":\"ASK_CLARIFICATION\""))
    }

    @Test
    fun contextualGuidanceSupportsFlexibleOrdinalsAndUniqueTitles() {
        assertTrue(prompt.contains("identified by an ordinal, a supplied temporary ref, or one unique supplied title"))
        assertTrue(prompt.contains("The noun may be omitted"))
        assertTrue(prompt.contains("what time is the first"))
        assertTrue(prompt.contains("what time it is for the second"))
        assertTrue(prompt.contains("Use ASK_CLARIFICATION only for genuine ambiguity"))
        assertTrue(prompt.contains("Example supplied snapshot: T3 has the unique title Podcast"))
        assertTrue(prompt.contains("two supplied titles both contain Podcast"))
        assertTrue(prompt.contains("Current validated task focus"))
        assertTrue(prompt.contains("what time is it?"))
        assertTrue(prompt.contains("When focus Available is false"))
    }

    @Test
    fun repairPromptIsReadOnlyAndGenerationBounded() {
        val repairPrompt = ConversationAgentClient.CONTEXT_READ_REPAIR_SYSTEM_PROMPT

        assertTrue(repairPrompt.contains("Allowed routes are CONTEXT_READ and ASK_CLARIFICATION only"))
        assertTrue(repairPrompt.contains("Never return TASK_COMMAND, DIRECT_REPLY, END_SESSION or UNKNOWN"))
        assertTrue(repairPrompt.contains("Match titles case-insensitively using only supplied items"))
        assertTrue(repairPrompt.contains("more than one supplied title plausibly matches"))
        assertTrue(repairPrompt.contains("Mutation requests must remain ASK_CLARIFICATION"))
        assertTrue(repairPrompt.contains("Current validated task focus"))
        assertTrue(repairPrompt.contains("authoritative Android-validated conversational focus"))
        assertTrue(repairPrompt.contains("what time is it?"))
        assertTrue(repairPrompt.contains("If Available is false, there is no validated focus"))
    }
}
