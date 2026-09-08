package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.schema.AgentResponseSchemas
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ConversationResponseConfigurationTest {
    @Test fun temperaturesAreSeparate() {
        assertEquals(0.0, ConversationAgentClient.ROUTING_TEMPERATURE, 0.0)
        assertEquals(0.35, ConversationAgentClient.RESPONSE_TEMPERATURE, 0.01)
        assertEquals(128, ConversationAgentClient.RESPONSE_MAX_TOKENS)
    }
    @Test fun schemasRemainSeparate() {
        val decisionSchema = AgentResponseSchemas.conversationDecisionResponseFormat().toString()
        assertTrue(decisionSchema.contains("conversation_decision"))
        assertTrue(decisionSchema.contains("APP_NAVIGATION"))
        assertTrue(decisionSchema.contains("navigation_target"))
        listOf("NONE", "CREATE_TASK", "TODAY_TASKS", "SCHEDULED_TASKS", "SETTINGS")
            .forEach { assertTrue(decisionSchema.contains(it)) }
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
    @Test fun routingPromptDistinguishesNavigationFromOperationsAndGuidance() {
        val prompt = ConversationAgentClient.ROUTING_SYSTEM_PROMPT
        assertTrue(prompt.contains("\"Open settings\" -> APP_NAVIGATION"))
        assertTrue(prompt.contains("\"Turn on high contrast\" -> SETTINGS_ACTION"))
        assertTrue(prompt.contains("\"Open create task\" -> APP_NAVIGATION"))
        assertTrue(prompt.contains("\"Create a task\" -> TASK_COMMAND"))
        assertTrue(prompt.contains("\"Open today's tasks\" -> APP_NAVIGATION"))
        assertTrue(prompt.contains("\"What tasks do I have today?\" -> TASK_COMMAND"))
        assertTrue(prompt.contains("\"How do I open settings?\" -> DIRECT_REPLY"))
        assertTrue(prompt.contains("\"Where are the settings?\" are DIRECT_REPLY"))
        assertTrue(prompt.contains("Advanced Settings is not supported voice navigation"))
    }
    @Test fun decisionDiagnosticsIncludeBoundedNavigationTarget() {
        val orchestrator = File(
            "src/main/java/com/example/myapplication/ai/conversation/ConversationOrchestrator.kt"
        ).readText()
        val diagnostic = orchestrator
            .substringAfter("\"CONVERSATION_DECISION_DEBUG\"")
            .substringBefore("return decision")

        assertTrue(diagnostic.contains("navigation_target=${'$'}{decision.navigationTarget.name}"))
    }
    @Test fun responsePromptContainsAuthorityRules() {
        val prompt = ConversationAgentClient.RESPONSE_SYSTEM_PROMPT
        assertTrue(prompt.contains("presentation-only response verbalizer"))
        assertTrue(prompt.contains("Follow response_act exactly"))
        assertTrue(prompt.contains("REPORT_AND_REQUEST_INPUT"))
        assertTrue(prompt.contains("meaning_detail narrows"))
        assertTrue(prompt.contains("Use every name in required_placeholders exactly once"))
        assertTrue(prompt.contains("optional_placeholders may be used zero or one time"))
        assertTrue(prompt.contains("Never output a placeholder outside available_placeholders"))
        assertTrue(prompt.contains("Avoid unnecessary repetition"))
        assertTrue(prompt.contains("Every protected value is deliberately hidden"))
        assertTrue(prompt.contains("Do not claim an operation happened unless"))
        assertTrue(prompt.contains("ASK_CONFIRMATION or ASK_CLARIFICATION"))
        assertTrue(prompt.contains("Room IDs"))
        assertTrue(prompt.contains("temporary refs such as T1 or T2"))
        assertTrue(prompt.contains("must remain concise"))
        assertTrue(prompt.contains("semantic illustrations for schema reliability, not wording templates"))
        assertTrue(prompt.contains("Do not vary wording merely to appear random"))
        assertFalse(prompt.contains("Okay — I'll open {transition_target} for you."))
    }
}
