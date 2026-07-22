package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.schema.AgentResponseSchemas
import org.junit.Assert.*
import org.junit.Test

class ConversationResponseConfigurationTest {
    @Test fun temperaturesAreSeparate() {
        assertEquals(0.0, ConversationAgentClient.ROUTING_TEMPERATURE, 0.0)
        assertEquals(0.35, ConversationAgentClient.RESPONSE_TEMPERATURE, 0.01)
        assertEquals(128, ConversationAgentClient.RESPONSE_MAX_TOKENS)
    }
    @Test fun schemasRemainSeparate() {
        val decisionSchema = AgentResponseSchemas.conversationDecisionResponseFormat().toString()
        assertTrue(decisionSchema.contains("conversation_decision"))
        assertTrue(decisionSchema.contains("CONTEXT_READ"))
        assertTrue(decisionSchema.contains("context_ref"))
        assertTrue(decisionSchema.contains("context_detail"))
        assertTrue(AgentResponseSchemas.contextReadRepairResponseFormat().toString().contains("context_read_repair"))
        assertTrue(AgentResponseSchemas.conversationResponseResponseFormat().toString().contains("conversation_response"))
        assertTrue(AgentResponseSchemas.conversationResponseResponseFormat().toString().contains("response_type"))
    }
    @Test fun responsePromptContainsAuthorityRules() {
        val prompt = ConversationAgentClient.RESPONSE_SYSTEM_PROMPT
        assertTrue(prompt.contains("response_type must exactly equal expected_response_type"))
        assertTrue(prompt.contains("Do not infer, replace or reinterpret the expected response type"))
        assertTrue(prompt.contains("NOT_FOUND is an informational result"))
        assertTrue(prompt.contains("CANCELLED should acknowledge"))
        assertTrue(prompt.contains("does not end the assistant session"))
        assertTrue(prompt.contains("Android owns listen-again behaviour"))
        assertTrue(prompt.contains("Do not add facts"))
        assertTrue(prompt.contains("Do not claim success unless outcome is SUCCESS or PARTIAL_SUCCESS"))
        assertTrue(prompt.contains("concise and suitable for TTS"))
    }
}
