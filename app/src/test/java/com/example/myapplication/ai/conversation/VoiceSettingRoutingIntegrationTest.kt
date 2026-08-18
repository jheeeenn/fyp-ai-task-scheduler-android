package com.example.myapplication.ai.conversation

import com.example.myapplication.voice.VoiceSettingConversationFocus
import com.example.myapplication.voice.VoiceSettingTarget
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSettingRoutingIntegrationTest {
    @Test
    fun orchestratorSuppliesAuthoritativeVoiceSettingSectionToAgent() = runBlocking {
        val client = CapturingRoutingClient()
        val orchestrator = ConversationOrchestrator(
            conversationAgentClient = client,
            parser = ConversationDecisionParser()
        )

        val decision = orchestrator.process(
            normalizedText = "can you turn it off",
            appContextSummary = "App guidance",
            voiceSettingRoutingContext = VoiceSettingRoutingContext.from(
                VoiceSettingConversationFocus(VoiceSettingTarget.HIGH_CONTRAST)
            )
        )

        assertEquals(ConversationSettingAction.HIGH_CONTRAST_OFF, decision.settingAction)
        assertTrue(client.memorySnapshot.contains("Voice-setting context:"))
        assertTrue(client.memorySnapshot.contains("Available: true"))
        assertTrue(client.memorySnapshot.contains("Target: HIGH_CONTRAST"))
        assertTrue(client.memorySnapshot.contains("not authority for a task operation"))
    }

    private class CapturingRoutingClient : ConversationAgentClient(context = null) {
        var memorySnapshot: String = ""

        override suspend fun process(
            userText: String,
            memorySnapshot: String,
            appContextSummary: String
        ): String {
            this.memorySnapshot = memorySnapshot
            return JSONObject()
                .put("route", "SETTINGS_ACTION")
                .put("task_text", "")
                .put("reply", "")
                .put("context_ref", "")
                .put("context_detail", "NONE")
                .put("context_action", "NONE")
                .put("setting_action", "HIGH_CONTRAST_OFF")
                .put("setting_target", "NONE")
                .put("query_reading_move", "NONE")
                .put("query_presentation_hint", "NONE")
                .put("confidence", 0.97)
                .put("listen_again", true)
                .toString()
        }
    }
}
