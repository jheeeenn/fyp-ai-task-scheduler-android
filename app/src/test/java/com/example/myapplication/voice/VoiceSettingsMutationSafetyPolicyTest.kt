package com.example.myapplication.voice

import com.example.myapplication.ai.conversation.ConversationSettingAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoiceSettingsMutationSafetyPolicyTest {
    @Test
    fun clearAndPoliteExecutionRequestsAreAllowed() {
        assertDisposition(
            "turn on high contrast",
            ConversationSettingAction.HIGH_CONTRAST_ON,
            VoiceSettingsSafetyDisposition.ALLOW
        )
        assertDisposition(
            "can you turn on high contrast",
            ConversationSettingAction.HIGH_CONTRAST_ON,
            VoiceSettingsSafetyDisposition.ALLOW
        )
        assertDisposition(
            "could you turn off large text",
            ConversationSettingAction.LARGE_TEXT_OFF,
            VoiceSettingsSafetyDisposition.ALLOW
        )
        assertDisposition(
            "can you use professional tone",
            ConversationSettingAction.ASSISTANT_TONE_PROFESSIONAL,
            VoiceSettingsSafetyDisposition.ALLOW
        )
        assertDisposition(
            "could you keep your replies short",
            ConversationSettingAction.REPLY_LENGTH_SHORT,
            VoiceSettingsSafetyDisposition.ALLOW
        )
    }

    @Test
    fun clearGuidanceQuestionsAreVetoedBeforeGrounding() {
        listOf(
            "how do i turn on high contrast" to ConversationSettingAction.HIGH_CONTRAST_ON,
            "how can i enable large text" to ConversationSettingAction.LARGE_TEXT_ON,
            "how do i make your replies shorter" to ConversationSettingAction.REPLY_LENGTH_SHORT,
            "what does high contrast do" to ConversationSettingAction.HIGH_CONTRAST_ON,
            "what is processing haptic feedback" to ConversationSettingAction.PROCESSING_HAPTIC_ON,
            "what settings can i change" to ConversationSettingAction.HIGH_CONTRAST_ON,
            "can i change the settings by voice" to ConversationSettingAction.LARGE_TEXT_ON
        ).forEach { (utterance, action) ->
            val result = VoiceSettingsMutationSafetyPolicy.evaluate(utterance, action)
            assertEquals(utterance, VoiceSettingsSafetyDisposition.GUIDANCE_ONLY, result.disposition)
            assertEquals(utterance, ConversationSettingAction.NONE, result.authorizedAction)
            assertTrue(utterance, result.speech.isNotBlank())
        }
    }

    @Test
    fun genericHapticRequestsRequireTargetClarification() {
        listOf(
            "turn off vibration" to ConversationSettingAction.PROCESSING_HAPTIC_OFF,
            "enable vibration" to ConversationSettingAction.SESSION_END_HAPTIC_ON,
            "can you open the haptic" to ConversationSettingAction.PROCESSING_HAPTIC_ON,
            "stop the haptic" to ConversationSettingAction.PROCESSING_HAPTIC_OFF
        ).forEach { (utterance, action) ->
            val result = VoiceSettingsMutationSafetyPolicy.evaluate(utterance, action)
            assertEquals(
                utterance,
                VoiceSettingsSafetyDisposition.CLARIFY_HAPTIC_TARGET,
                result.disposition
            )
            assertEquals(utterance, ConversationSettingAction.NONE, result.authorizedAction)
        }
    }

    @Test
    fun distinguishedProcessingAndSessionEndHapticsAreAllowed() {
        listOf(
            "turn off the processing vibration" to ConversationSettingAction.PROCESSING_HAPTIC_OFF,
            "stop vibrating while you re thinking" to ConversationSettingAction.PROCESSING_HAPTIC_OFF,
            "disable the heartbeat while processing" to ConversationSettingAction.PROCESSING_HAPTIC_OFF,
            "turn off the vibration while you re working" to ConversationSettingAction.PROCESSING_HAPTIC_OFF,
            "turn off the session end vibration" to ConversationSettingAction.SESSION_END_HAPTIC_OFF,
            "don t vibrate when the conversation finishes" to ConversationSettingAction.SESSION_END_HAPTIC_OFF,
            "stop the vibration when you re done" to ConversationSettingAction.SESSION_END_HAPTIC_OFF,
            "disable the final vibration" to ConversationSettingAction.SESSION_END_HAPTIC_OFF
        ).forEach { (utterance, action) ->
            assertDisposition(utterance, action, VoiceSettingsSafetyDisposition.ALLOW)
        }
    }

    @Test
    fun ungroundedAndMismatchedModelActionsFailClosed() {
        assertDisposition(
            "can you turn off the last text",
            ConversationSettingAction.PROCESSING_HAPTIC_OFF,
            VoiceSettingsSafetyDisposition.CLARIFY_SETTING_TARGET
        )
        assertDisposition(
            "turn on high contrast",
            ConversationSettingAction.LARGE_TEXT_ON,
            VoiceSettingsSafetyDisposition.CLARIFY_SETTING_TARGET
        )
        assertDisposition(
            "turn off high contrast",
            ConversationSettingAction.HIGH_CONTRAST_ON,
            VoiceSettingsSafetyDisposition.CLARIFY_SETTING_TARGET
        )
    }

    @Test
    fun boundedToneReplyAndDisplayAliasesRemainGrounded() {
        listOf(
            Triple("be more formal", ConversationSettingAction.ASSISTANT_TONE_PROFESSIONAL, VoiceSettingsSafetyDisposition.ALLOW),
            Triple("be warmer", ConversationSettingAction.ASSISTANT_TONE_FRIENDLY, VoiceSettingsSafetyDisposition.ALLOW),
            Triple("use a neutral tone", ConversationSettingAction.ASSISTANT_TONE_NEUTRAL, VoiceSettingsSafetyDisposition.ALLOW),
            Triple("keep your answers concise", ConversationSettingAction.REPLY_LENGTH_SHORT, VoiceSettingsSafetyDisposition.ALLOW),
            Triple("give me more detail", ConversationSettingAction.REPLY_LENGTH_DETAILED, VoiceSettingsSafetyDisposition.ALLOW),
            Triple("go back to normal replies", ConversationSettingAction.REPLY_LENGTH_NORMAL, VoiceSettingsSafetyDisposition.ALLOW),
            Triple("make the text bigger", ConversationSettingAction.LARGE_TEXT_ON, VoiceSettingsSafetyDisposition.ALLOW),
            Triple("i want more contrast", ConversationSettingAction.HIGH_CONTRAST_ON, VoiceSettingsSafetyDisposition.ALLOW)
        ).forEach { (utterance, action, disposition) ->
            assertDisposition(utterance, action, disposition)
        }
    }

    @Test
    fun clarificationContinuationUsesRetainedDirectionButRequiresExplicitTarget() {
        val initial = VoiceSettingsMutationSafetyPolicy.evaluate(
            "turn off vibration",
            ConversationSettingAction.PROCESSING_HAPTIC_OFF
        )
        assertEquals(VoiceSettingsSafetyDisposition.CLARIFY_HAPTIC_TARGET, initial.disposition)
        assertEquals(false, initial.pendingHapticEnabled)

        val processing = VoiceSettingsMutationSafetyPolicy.evaluateHapticClarification(
            "the processing one",
            requestedEnabled = requireNotNull(initial.pendingHapticEnabled)
        )
        assertEquals(VoiceSettingsSafetyDisposition.ALLOW, processing?.disposition)
        assertEquals(
            ConversationSettingAction.PROCESSING_HAPTIC_OFF,
            processing?.authorizedAction
        )

        val sessionEnd = VoiceSettingsMutationSafetyPolicy.evaluateHapticClarification(
            "the session end one",
            requestedEnabled = false
        )
        assertEquals(VoiceSettingsSafetyDisposition.ALLOW, sessionEnd?.disposition)
        assertEquals(
            ConversationSettingAction.SESSION_END_HAPTIC_OFF,
            sessionEnd?.authorizedAction
        )

        val bareYes = VoiceSettingsMutationSafetyPolicy.evaluateHapticClarification(
            "yes",
            requestedEnabled = false
        )
        assertEquals(VoiceSettingsSafetyDisposition.CLARIFY_HAPTIC_TARGET, bareYes?.disposition)
        assertEquals(ConversationSettingAction.NONE, bareYes?.authorizedAction)

        assertNull(
            VoiceSettingsMutationSafetyPolicy.evaluateHapticClarification(
                "turn on high contrast",
                requestedEnabled = false
            )
        )
    }

    @Test
    fun safetyPolicyIsPureAndDoesNotOwnPreferenceMutation() {
        val source = File(
            "src/main/java/com/example/myapplication/voice/" +
                "VoiceSettingsMutationSafetyPolicy.kt"
        ).readText()

        assertTrue(!source.contains("AppPreferences"))
        assertTrue(!source.contains("VoiceSettingsExecutor"))
        assertTrue(!source.contains("SharedPreferences"))
    }

    private fun assertDisposition(
        utterance: String,
        action: ConversationSettingAction,
        expected: VoiceSettingsSafetyDisposition
    ) {
        val result = VoiceSettingsMutationSafetyPolicy.evaluate(utterance, action)
        assertEquals(utterance, expected, result.disposition)
        if (expected == VoiceSettingsSafetyDisposition.ALLOW) {
            assertEquals(utterance, action, result.authorizedAction)
        } else {
            assertEquals(utterance, ConversationSettingAction.NONE, result.authorizedAction)
        }
    }
}
