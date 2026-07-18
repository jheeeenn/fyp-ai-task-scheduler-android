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
        assertTrue(AgentResponseSchemas.conversationDecisionResponseFormat().toString().contains("conversation_decision"))
        assertTrue(AgentResponseSchemas.conversationResponseResponseFormat().toString().contains("conversation_response"))
        assertTrue(AgentResponseSchemas.conversationResponseResponseFormat().toString().contains("response_type"))
    }
    @Test fun responsePromptContainsAuthorityRules() {
        val prompt = ConversationAgentClient.RESPONSE_SYSTEM_PROMPT
        assertTrue(prompt.contains("Do not add facts"))
        assertTrue(prompt.contains("Do not claim success unless outcome is SUCCESS or PARTIAL_SUCCESS"))
        assertTrue(prompt.contains("concise and suitable for TTS"))
    }
}
