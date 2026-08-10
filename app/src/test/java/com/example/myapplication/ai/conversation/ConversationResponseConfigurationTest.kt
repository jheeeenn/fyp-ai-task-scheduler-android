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
        assertTrue(decisionSchema.contains("context_action"))
        assertTrue(decisionSchema.contains("CONTEXT_ACTION"))
        assertTrue(decisionSchema.contains("QUERY_READING_CONTROL"))
        assertTrue(decisionSchema.contains("query_reading_move"))
        assertTrue(decisionSchema.contains("query_presentation_hint"))
        assertTrue(AgentResponseSchemas.contextReadRepairResponseFormat().toString().contains("context_read_repair"))
        assertTrue(AgentResponseSchemas.contextActionRepairResponseFormat().toString().contains("context_action_repair"))
        val verbalization = AgentResponseSchemas.responseVerbalizationResponseFormat().toString()
        assertTrue(verbalization.contains("response_verbalization"))
        assertTrue(verbalization.contains("speech_template"))
        assertFalse(verbalization.contains("response_type"))
        assertFalse(verbalization.contains("listen_again"))
    }
    @Test fun responsePromptContainsAuthorityRules() {
        val prompt = ConversationAgentClient.RESPONSE_SYSTEM_PROMPT
        assertTrue(prompt.contains("presentation-only response verbalizer"))
        assertTrue(prompt.contains("For AUTHORITATIVE_MESSAGE, speech_template must contain {authoritative_message} exactly once"))
        assertTrue(prompt.contains("For TASK_CONFIRMATION"))
        assertTrue(prompt.contains("must contain {task_title} exactly once"))
        assertTrue(prompt.contains("For TASK_ACTION_RESULT"))
        assertTrue(prompt.contains("For TASK_TRANSITION"))
        assertTrue(prompt.contains("CREATE_TASK uses {transition_target}"))
        assertTrue(prompt.contains("Every protected value is deliberately hidden"))
        assertTrue(prompt.contains("Never output another placeholder"))
        assertTrue(prompt.contains("Never guess, restate, paraphrase, or add a task title"))
        assertTrue(prompt.contains("Only TASK_CONFIRMATION may ask a question"))
        assertTrue(prompt.contains("Room IDs"))
        assertTrue(prompt.contains("temporary refs such as T1 or T2"))
        assertTrue(prompt.contains("must remain concise"))
    }
}
