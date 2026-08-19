package com.example.myapplication.voice

import com.example.myapplication.ai.conversation.ConversationSettingAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceSettingContextualActionResolverTest {
    @Test
    fun booleanFocusAndCurrentDirectionFullyResolveContextualAction() {
        assertResolved(
            VoiceSettingTarget.HIGH_CONTRAST,
            "can you turn it off",
            ConversationSettingAction.HIGH_CONTRAST_OFF
        )
        assertResolved(
            VoiceSettingTarget.HIGH_CONTRAST,
            "turn it back on",
            ConversationSettingAction.HIGH_CONTRAST_ON
        )
        assertResolved(
            VoiceSettingTarget.LARGE_TEXT,
            "turn it on",
            ConversationSettingAction.LARGE_TEXT_ON
        )
        assertResolved(
            VoiceSettingTarget.PROCESSING_HAPTIC,
            "switch it off",
            ConversationSettingAction.PROCESSING_HAPTIC_OFF
        )
    }

    @Test
    fun explicitDifferentTargetOverridesOldFocusInsteadOfUsingFallback() {
        assertNull(
            VoiceSettingContextualActionResolver.resolve(
                "turn off large text",
                VoiceSettingConversationFocus(VoiceSettingTarget.HIGH_CONTRAST)
            )
        )
    }

    @Test
    fun noFocusGuidanceMissingValueAndUngroundedSpeechFailClosed() {
        assertNull(VoiceSettingContextualActionResolver.resolve("turn it off", null))
        listOf(
            "how do i turn it on",
            "what about it",
            "do something with it",
            "change it",
            "can you fix it",
            "can you turn off the last text"
        ).forEach { utterance ->
            assertNull(
                utterance,
                VoiceSettingContextualActionResolver.resolve(
                    utterance,
                    VoiceSettingConversationFocus(VoiceSettingTarget.HIGH_CONTRAST)
                )
            )
        }
    }

    @Test
    fun genericHapticNeverBorrowsAnUnrelatedFocus() {
        assertNull(
            VoiceSettingContextualActionResolver.resolve(
                "turn off that vibration",
                VoiceSettingConversationFocus(VoiceSettingTarget.HIGH_CONTRAST)
            )
        )
    }

    @Test
    fun nonBooleanFocusRequiresExplicitBoundedValue() {
        assertResolved(
            VoiceSettingTarget.ASSISTANT_TONE,
            "use the professional one",
            ConversationSettingAction.ASSISTANT_TONE_PROFESSIONAL
        )
        assertResolved(
            VoiceSettingTarget.REPLY_LENGTH,
            "use the short one",
            ConversationSettingAction.REPLY_LENGTH_SHORT
        )
        assertNull(
            VoiceSettingContextualActionResolver.resolve(
                "use it",
                VoiceSettingConversationFocus(VoiceSettingTarget.ASSISTANT_TONE)
            )
        )
        assertNull(
            VoiceSettingContextualActionResolver.resolve(
                "change it",
                VoiceSettingConversationFocus(VoiceSettingTarget.REPLY_LENGTH)
            )
        )
    }

    @Test
    fun speechSpeedFocusResolvesOnlyBoundedAbsoluteAndRelativeValues() {
        val cases = mapOf(
            "make it fast" to ConversationSettingAction.SPEECH_SPEED_FAST,
            "make it very fast" to ConversationSettingAction.SPEECH_SPEED_VERY_FAST,
            "make it faster" to ConversationSettingAction.SPEECH_SPEED_FASTER,
            "make it slower" to ConversationSettingAction.SPEECH_SPEED_SLOWER,
            "set it to normal" to ConversationSettingAction.SPEECH_SPEED_NORMAL,
            "set it back to normal" to ConversationSettingAction.SPEECH_SPEED_NORMAL
        )
        cases.forEach { (utterance, expected) ->
            assertResolved(VoiceSettingTarget.SPEECH_SPEED, utterance, expected)
        }

        assertNull(
            VoiceSettingContextualActionResolver.resolve(
                "make it professional",
                VoiceSettingConversationFocus(VoiceSettingTarget.SPEECH_SPEED)
            )
        )
    }

    private fun assertResolved(
        target: VoiceSettingTarget,
        utterance: String,
        expected: ConversationSettingAction
    ) {
        assertEquals(
            expected,
            VoiceSettingContextualActionResolver.resolve(
                utterance,
                VoiceSettingConversationFocus(target)
            )
        )
    }
}
