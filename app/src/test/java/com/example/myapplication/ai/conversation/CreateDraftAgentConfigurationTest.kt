package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.schema.AgentResponseSchemas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CreateDraftAgentConfigurationTest {
    private val prompt = ConversationAgentClient.CREATE_DRAFT_SYSTEM_PROMPT
    private val clientSource = File("src/main/java/com/example/myapplication/ai/conversation/ConversationAgentClient.kt").readText()

    @Test
    fun createDraftRequestUsesDedicatedDeterministicConfiguration() {
        assertEquals(0.0, ConversationAgentClient.CREATE_DRAFT_TEMPERATURE, 0.0)
        assertTrue(ConversationAgentClient.CREATE_DRAFT_MAX_TOKENS in 96..128)
        assertTrue(clientSource.contains("RequestKind.CREATE_DRAFT_MOVE"))
        assertTrue(clientSource.contains("AgentResponseSchemas.createDraftMoveResponseFormat()"))
        assertTrue(clientSource.contains("CONVO_CREATE_DRAFT_SCHEMA"))
        assertTrue(clientSource.contains("google/gemma-4-e2b"))
        assertTrue(clientSource.contains("put(\"stream\", false)"))
        assertTrue(AgentResponseSchemas.createDraftMoveResponseFormat().toString().contains("create_draft_move"))
    }

    @Test
    fun promptPreservesAndroidAuthorityAndLiteralTemporalMeaning() {
        assertTrue(prompt.contains("one utterance inside an existing create-task draft workflow"))
        assertTrue(prompt.contains("do not create or save tasks", ignoreCase = true))
        assertTrue(prompt.contains("do not validate dates or times", ignoreCase = true))
        assertTrue(prompt.contains("do not calculate calendar dates", ignoreCase = true))
        assertTrue(prompt.contains("Do not infer an AM/PM value"))
        assertTrue(prompt.contains("Use the supplied state context as authoritative"))
        assertTrue(prompt.contains("advisory local candidate is a proposal, not an authoritative decision"))
        assertTrue(prompt.contains("Independently decide the bounded semantic move"))
        assertTrue(prompt.contains("Android will validate and execute your structured decision"))
        assertTrue(prompt.contains("Do not convert \"tomorrow\" to a calendar date"))
        assertTrue(prompt.contains("Do not convert \"morning\" to a clock time"))
        assertTrue(prompt.contains("Do not invent missing values"))
        assertFalse(prompt.contains("save the task now"))
    }

    @Test
    fun promptContainsBoundedExamplesAndJsonOnlyRule() {
        assertTrue(prompt.contains("User: \"just 9 AM\""))
        assertTrue(prompt.contains("User: \"move the time to 10 AM\""))
        assertTrue(prompt.contains("User: \"could you use revision as the name\""))
        assertTrue(prompt.contains("User: \"what's the time to 10 AM\""))
        assertTrue(prompt.contains("User: \"that looks right, save it\""))
        assertTrue(prompt.contains("User: \"use next Friday\""))
        assertTrue(prompt.contains("User: \"later\""))
        assertTrue(prompt.contains("Do not output explanations, markdown, task-agent fields"))
    }
}
