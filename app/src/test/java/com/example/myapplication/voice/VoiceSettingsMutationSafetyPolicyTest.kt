package com.example.myapplication.voice

import com.example.myapplication.ai.conversation.ConversationSettingAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun clearSpeechSpeedAbsoluteAndRelativeRequestsAreAllowed() {
        listOf(
            "set speech speed to slow" to ConversationSettingAction.SPEECH_SPEED_SLOW,
            "use normal speech speed" to ConversationSettingAction.SPEECH_SPEED_NORMAL,
            "set speech speed to fast" to ConversationSettingAction.SPEECH_SPEED_FAST,
            "use very fast speech speed" to ConversationSettingAction.SPEECH_SPEED_VERY_FAST,
            "make your speaking speed very fast" to ConversationSettingAction.SPEECH_SPEED_VERY_FAST,
            "set speaking speed to normal" to ConversationSettingAction.SPEECH_SPEED_NORMAL,
            "set the voice to very fast" to ConversationSettingAction.SPEECH_SPEED_VERY_FAST,
            "speak faster" to ConversationSettingAction.SPEECH_SPEED_FASTER,
            "talk faster" to ConversationSettingAction.SPEECH_SPEED_FASTER,
            "can you speak a little faster" to ConversationSettingAction.SPEECH_SPEED_FASTER,
            "speed up" to ConversationSettingAction.SPEECH_SPEED_FASTER,
            "speed up your speech" to ConversationSettingAction.SPEECH_SPEED_FASTER,
            "speak slower" to ConversationSettingAction.SPEECH_SPEED_SLOWER,
            "talk slower" to ConversationSettingAction.SPEECH_SPEED_SLOWER,
            "slow down" to ConversationSettingAction.SPEECH_SPEED_SLOWER,
            "slow down your speech" to ConversationSettingAction.SPEECH_SPEED_SLOWER
        ).forEach { (utterance, action) ->
            assertDisposition(utterance, action, VoiceSettingsSafetyDisposition.ALLOW)
        }
    }

    @Test
    fun genericSpeedWordsNeverGroundSpeechSpeed() {
        listOf(
            "create the task quickly",
            "give me a fast answer",
            "use short replies",
            "make the app faster"
        ).forEach { utterance ->
            assertTrue(
                utterance,
                VoiceSettingsMutationSafetyPolicy.groundedTarget(utterance) !=
                    VoiceSettingTarget.SPEECH_SPEED
            )
            assertDisposition(
                utterance,
                ConversationSettingAction.SPEECH_SPEED_FAST,
                VoiceSettingsSafetyDisposition.CLARIFY_SETTING_TARGET
            )
        }
    }

    @Test
    fun veryFastTakesPrecedenceOverFastGrounding() {
        assertDisposition(
            "set speech speed to very fast",
            ConversationSettingAction.SPEECH_SPEED_VERY_FAST,
            VoiceSettingsSafetyDisposition.ALLOW
        )
        assertDisposition(
            "set speech speed to very fast",
            ConversationSettingAction.SPEECH_SPEED_FAST,
            VoiceSettingsSafetyDisposition.CLARIFY_SETTING_TARGET
        )
    }

    @Test
    fun speechSpeedFocusAuthorizesOnlyBoundedContextualSpeedValues() {
        val focus = VoiceSettingConversationFocus(VoiceSettingTarget.SPEECH_SPEED)
        listOf(
            "make it fast" to ConversationSettingAction.SPEECH_SPEED_FAST,
            "make it very fast" to ConversationSettingAction.SPEECH_SPEED_VERY_FAST,
            "make it faster" to ConversationSettingAction.SPEECH_SPEED_FASTER,
            "make it slower" to ConversationSettingAction.SPEECH_SPEED_SLOWER,
            "set it to normal" to ConversationSettingAction.SPEECH_SPEED_NORMAL
        ).forEach { (utterance, action) ->
            assertDisposition(utterance, action, VoiceSettingsSafetyDisposition.ALLOW, focus)
        }

        assertDisposition(
            "make it short",
            ConversationSettingAction.REPLY_LENGTH_SHORT,
            VoiceSettingsSafetyDisposition.CLARIFY_SETTING_TARGET,
            focus
        )
    }

    @Test
    fun explicitTurnBackDirectionsAreAllowedOnlyWithGroundedMatchingTargets() {
        listOf(
            "turn the processing vibration back on" to
                ConversationSettingAction.PROCESSING_HAPTIC_ON,
            "turn back on the processing vibration" to
                ConversationSettingAction.PROCESSING_HAPTIC_ON,
            "turn back on for the processing vibration" to
                ConversationSettingAction.PROCESSING_HAPTIC_ON,
            "turn high contrast back off" to ConversationSettingAction.HIGH_CONTRAST_OFF,
            "turn large text back on" to ConversationSettingAction.LARGE_TEXT_ON,
            "turn the session end vibration back off" to
                ConversationSettingAction.SESSION_END_HAPTIC_OFF
        ).forEach { (utterance, action) ->
            assertDisposition(utterance, action, VoiceSettingsSafetyDisposition.ALLOW)
        }

        assertDisposition(
            "turn high contrast back on",
            ConversationSettingAction.PROCESSING_HAPTIC_ON,
            VoiceSettingsSafetyDisposition.CLARIFY_SETTING_TARGET
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
            "can i change the settings by voice" to ConversationSettingAction.LARGE_TEXT_ON,
            "how does speech speed work" to ConversationSettingAction.SPEECH_SPEED_FAST,
            "what speech speeds are available" to ConversationSettingAction.SPEECH_SPEED_FAST
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
        assertEquals(false, initial.pendingClarification?.requestedEnabled)

        val processing = VoiceSettingsMutationSafetyPolicy.evaluateClarification(
            "the processing one",
            pending = requireNotNull(initial.pendingClarification)
        )
        assertEquals(VoiceSettingsSafetyDisposition.ALLOW, processing?.disposition)
        assertEquals(
            ConversationSettingAction.PROCESSING_HAPTIC_OFF,
            processing?.authorizedAction
        )

        val sessionEnd = VoiceSettingsMutationSafetyPolicy.evaluateClarification(
            "the session end one",
            pending = PendingVoiceSettingClarification(
                requestedEnabled = false,
                scope = VoiceSettingClarificationScope.HAPTIC_SETTING
            )
        )
        assertEquals(VoiceSettingsSafetyDisposition.ALLOW, sessionEnd?.disposition)
        assertEquals(
            ConversationSettingAction.SESSION_END_HAPTIC_OFF,
            sessionEnd?.authorizedAction
        )

        val bareYes = VoiceSettingsMutationSafetyPolicy.evaluateClarification(
            "yes",
            pending = requireNotNull(initial.pendingClarification)
        )
        assertEquals(VoiceSettingsSafetyDisposition.CLARIFY_HAPTIC_TARGET, bareYes?.disposition)
        assertEquals(ConversationSettingAction.NONE, bareYes?.authorizedAction)

    }

    @Test
    fun pendingHapticClarificationResolvesNaturalAndOrdinalProcessingAnswers() {
        val pending = PendingVoiceSettingClarification(
            requestedEnabled = false,
            scope = VoiceSettingClarificationScope.HAPTIC_SETTING
        )
        listOf(
            "processing",
            "the processing",
            "processing one",
            "the processing one",
            "first",
            "first one",
            "the first one"
        ).forEach { answer ->
            val result = VoiceSettingsMutationSafetyPolicy.evaluateClarification(answer, pending)
            assertEquals(answer, VoiceSettingsSafetyDisposition.ALLOW, result?.disposition)
            assertEquals(
                answer,
                ConversationSettingAction.PROCESSING_HAPTIC_OFF,
                result?.authorizedAction
            )
        }
    }

    @Test
    fun pendingHapticClarificationResolvesNaturalAndOrdinalSessionAnswers() {
        val pendingOff = PendingVoiceSettingClarification(
            requestedEnabled = false,
            scope = VoiceSettingClarificationScope.HAPTIC_SETTING
        )
        listOf(
            "session",
            "the session",
            "session one",
            "the session one",
            "session end",
            "the session end",
            "second",
            "second one",
            "the second one"
        ).forEach { answer ->
            val result = VoiceSettingsMutationSafetyPolicy.evaluateClarification(answer, pendingOff)
            assertEquals(answer, VoiceSettingsSafetyDisposition.ALLOW, result?.disposition)
            assertEquals(
                answer,
                ConversationSettingAction.SESSION_END_HAPTIC_OFF,
                result?.authorizedAction
            )
        }

        val pendingOn = pendingOff.copy(requestedEnabled = true)
        val enabled = VoiceSettingsMutationSafetyPolicy.evaluateClarification(
            "the second one",
            pendingOn
        )
        assertEquals(ConversationSettingAction.SESSION_END_HAPTIC_ON, enabled?.authorizedAction)
    }

    @Test
    fun ambiguousHapticAnswerRetriesButExplicitNewCommandEscapes() {
        val pending = PendingVoiceSettingClarification(
            requestedEnabled = false,
            scope = VoiceSettingClarificationScope.HAPTIC_SETTING
        )
        val yes = VoiceSettingsMutationSafetyPolicy.evaluateClarification("yes", pending)
        assertEquals(VoiceSettingsSafetyDisposition.CLARIFY_HAPTIC_TARGET, yes?.disposition)
        assertEquals(false, yes?.pendingClarification?.requestedEnabled)

        assertEquals(
            null,
            VoiceSettingsMutationSafetyPolicy.evaluateClarification(
                "turn on high contrast",
                pending
            )
        )
        assertFalse(
            VoiceSettingsMutationSafetyPolicy.shouldRetainClarification(
                "turn on high contrast",
                pending
            )
        )
        assertTrue(
            VoiceSettingsMutationSafetyPolicy.shouldRetainClarification(
                "maybe that one",
                pending
            )
        )
    }

    @Test
    fun explicitSessionVibrationGroundingIsAllowedWhileGenericHapticStaysAmbiguous() {
        assertDisposition(
            "turn off the session vibration",
            ConversationSettingAction.SESSION_END_HAPTIC_OFF,
            VoiceSettingsSafetyDisposition.ALLOW
        )
        assertDisposition(
            "turn on the session haptic",
            ConversationSettingAction.SESSION_END_HAPTIC_ON,
            VoiceSettingsSafetyDisposition.ALLOW
        )
        assertDisposition(
            "turn off vibration",
            ConversationSettingAction.PROCESSING_HAPTIC_OFF,
            VoiceSettingsSafetyDisposition.CLARIFY_HAPTIC_TARGET
        )
        assertDisposition(
            "turn off the haptic",
            ConversationSettingAction.PROCESSING_HAPTIC_OFF,
            VoiceSettingsSafetyDisposition.CLARIFY_HAPTIC_TARGET
        )
    }

    @Test
    fun onlyExactBigTextAliasIsAddedForLargeText() {
        assertDisposition(
            "turn on big text",
            ConversationSettingAction.LARGE_TEXT_ON,
            VoiceSettingsSafetyDisposition.ALLOW
        )
        listOf("turn on lush tax", "turn on the big tasks", "turn on the big tex").forEach {
            assertDisposition(
                it,
                ConversationSettingAction.LARGE_TEXT_ON,
                VoiceSettingsSafetyDisposition.CLARIFY_SETTING_TARGET
            )
        }
    }

    @Test
    fun guidanceGroundsFocusAndContextualBooleanFollowUpsRemainValueBounded() {
        val guidance = VoiceSettingsMutationSafetyPolicy.evaluate(
            "how do i turn on high contrast",
            ConversationSettingAction.HIGH_CONTRAST_ON
        )
        assertEquals(VoiceSettingsSafetyDisposition.GUIDANCE_ONLY, guidance.disposition)
        assertEquals(VoiceSettingTarget.HIGH_CONTRAST, guidance.groundedTarget)
        val focus = VoiceSettingConversationFocus(requireNotNull(guidance.groundedTarget))

        assertDisposition(
            "can you turn it on",
            ConversationSettingAction.HIGH_CONTRAST_ON,
            VoiceSettingsSafetyDisposition.ALLOW,
            focus
        )
        assertDisposition(
            "turn it back off",
            ConversationSettingAction.HIGH_CONTRAST_OFF,
            VoiceSettingsSafetyDisposition.ALLOW,
            focus
        )
        assertDisposition(
            "turn that off",
            ConversationSettingAction.HIGH_CONTRAST_OFF,
            VoiceSettingsSafetyDisposition.ALLOW,
            focus
        )
        assertDisposition(
            "turn it on",
            ConversationSettingAction.LARGE_TEXT_ON,
            VoiceSettingsSafetyDisposition.CLARIFY_SETTING_TARGET,
            focus
        )
        assertDisposition(
            "what about it",
            ConversationSettingAction.HIGH_CONTRAST_ON,
            VoiceSettingsSafetyDisposition.GUIDANCE_ONLY,
            focus
        )
    }

    @Test
    fun noFocusRetainsOnlyGroundedDirectionAndTargetFollowUpCompletesAction() {
        val initial = VoiceSettingsMutationSafetyPolicy.evaluate(
            "turn it on",
            ConversationSettingAction.HIGH_CONTRAST_ON
        )
        assertEquals(VoiceSettingsSafetyDisposition.CLARIFY_SETTING_TARGET, initial.disposition)
        assertEquals(VoiceSettingClarificationScope.ANY_BOOLEAN_SETTING, initial.pendingClarification?.scope)
        assertEquals(true, initial.pendingClarification?.requestedEnabled)
        assertEquals(null, initial.groundedTarget)

        val followUp = VoiceSettingsMutationSafetyPolicy.evaluateClarification(
            "high contrast",
            requireNotNull(initial.pendingClarification)
        )
        assertEquals(VoiceSettingsSafetyDisposition.ALLOW, followUp?.disposition)
        assertEquals(ConversationSettingAction.HIGH_CONTRAST_ON, followUp?.authorizedAction)
        assertEquals(VoiceSettingTarget.HIGH_CONTRAST, followUp?.groundedTarget)
    }

    @Test
    fun toneAndReplyGuidanceTargetsAuthorizeOnlyExplicitContextualValues() {
        assertEquals(
            VoiceSettingTarget.ASSISTANT_TONE,
            VoiceSettingsMutationSafetyPolicy.groundedTarget("what tones can you use")
        )
        assertDisposition(
            "use the professional one",
            ConversationSettingAction.ASSISTANT_TONE_PROFESSIONAL,
            VoiceSettingsSafetyDisposition.ALLOW,
            VoiceSettingConversationFocus(VoiceSettingTarget.ASSISTANT_TONE)
        )
        assertDisposition(
            "use the short one",
            ConversationSettingAction.REPLY_LENGTH_SHORT,
            VoiceSettingsSafetyDisposition.ALLOW,
            VoiceSettingConversationFocus(VoiceSettingTarget.REPLY_LENGTH)
        )
        assertDisposition(
            "use it",
            ConversationSettingAction.REPLY_LENGTH_SHORT,
            VoiceSettingsSafetyDisposition.CLARIFY_SETTING_TARGET,
            VoiceSettingConversationFocus(VoiceSettingTarget.REPLY_LENGTH)
        )
    }

    @Test
    fun ungroundedModelTargetNeverBecomesAndroidFocusAndContextClearsAtSessionBoundary() {
        val unsafe = VoiceSettingsMutationSafetyPolicy.evaluate(
            "can you turn off the last text",
            ConversationSettingAction.PROCESSING_HAPTIC_OFF
        )
        assertEquals(VoiceSettingsSafetyDisposition.CLARIFY_SETTING_TARGET, unsafe.disposition)
        assertEquals(null, unsafe.groundedTarget)
        val unsafeWithMatchingStaleFocus = VoiceSettingsMutationSafetyPolicy.evaluate(
            "can you turn off the last text",
            ConversationSettingAction.PROCESSING_HAPTIC_OFF,
            VoiceSettingConversationFocus(VoiceSettingTarget.PROCESSING_HAPTIC)
        )
        assertEquals(
            VoiceSettingsSafetyDisposition.CLARIFY_SETTING_TARGET,
            unsafeWithMatchingStaleFocus.disposition
        )
        assertEquals(null, unsafeWithMatchingStaleFocus.groundedTarget)

        val context = VoiceSettingConversationContext()
        context.focus(VoiceSettingTarget.HIGH_CONTRAST)
        context.retain(
            PendingVoiceSettingClarification(true, VoiceSettingClarificationScope.ANY_BOOLEAN_SETTING)
        )
        context.clear()
        assertEquals(null, context.focus)
        assertEquals(null, context.pendingClarification)
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
        expected: VoiceSettingsSafetyDisposition,
        focus: VoiceSettingConversationFocus? = null
    ) {
        val result = VoiceSettingsMutationSafetyPolicy.evaluate(utterance, action, focus)
        assertEquals(utterance, expected, result.disposition)
        if (expected == VoiceSettingsSafetyDisposition.ALLOW) {
            assertEquals(utterance, action, result.authorizedAction)
        } else {
            assertEquals(utterance, ConversationSettingAction.NONE, result.authorizedAction)
        }
    }
}
