package com.example.myapplication.preferences

import com.example.myapplication.ai.conversation.ConversationSettingAction
import com.example.myapplication.voice.VoiceSettingExecutionStatus
import com.example.myapplication.voice.VoiceSettingConversationFocus
import com.example.myapplication.voice.VoiceSettingContextualActionResolver
import com.example.myapplication.voice.VoiceSettingTarget
import com.example.myapplication.voice.VoiceSettingsExecutor
import com.example.myapplication.voice.VoiceSettingsMutationSafetyPolicy
import com.example.myapplication.voice.VoiceSettingsSafetyDisposition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoiceSettingsExecutorTest {
    @Test
    fun everySupportedActionAppliesPersistsAndThenIsIdempotent() {
        ConversationSettingAction.entries
            .filterNot { it == ConversationSettingAction.NONE }
            .forEach { action ->
                val preferences = AppPreferences(FakeVoicePreferenceStorage())
                prepareOpposite(preferences, action)
                val executor = VoiceSettingsExecutor(preferences)

                val applied = executor.execute(action)
                assertEquals(action.name, VoiceSettingExecutionStatus.APPLIED, applied.status)
                assertRequestedValue(preferences, action)
                assertEquals(action.name, action.isVisual(), applied.displayRefreshRequired)

                val unchanged = executor.execute(action)
                assertEquals(action.name, VoiceSettingExecutionStatus.UNCHANGED, unchanged.status)
                assertFalse(action.name, unchanged.displayRefreshRequired)
                assertTrue(action.name, unchanged.speech.contains("already"))
            }
    }

    @Test
    fun noneIsRejectedAndCannotMutateDeveloperEndpoints() {
        val preferences = AppPreferences(FakeVoicePreferenceStorage())
        preferences.setConversationAgentEndpoint("conversation-value")
        preferences.setTaskAgentEndpoint("task-value")

        val result = VoiceSettingsExecutor(preferences).execute(ConversationSettingAction.NONE)

        assertEquals(VoiceSettingExecutionStatus.REJECTED, result.status)
        assertEquals("conversation-value", preferences.conversationAgentEndpoint)
        assertEquals("task-value", preferences.taskAgentEndpoint)
        assertFalse(ConversationSettingAction.entries.any { it.name.contains("ENDPOINT") })
    }

    @Test
    fun visualMutationsUseVoicePreferenceSource() {
        val source = File(
            "src/main/java/com/example/myapplication/voice/VoiceSettingsExecutor.kt"
        ).readText()

        assertTrue(source.contains("setLargeTextEnabled(it, PreferenceChangeSource.VOICE)"))
        assertTrue(source.contains("setHighContrastEnabled(it, PreferenceChangeSource.VOICE)"))
    }

    @Test
    fun absoluteSpeechSpeedActionsPersistBoundedPresetsWithDeterministicSpeech() {
        val cases = mapOf(
            ConversationSettingAction.SPEECH_SPEED_SLOW to SpeechRatePreset.SLOW,
            ConversationSettingAction.SPEECH_SPEED_NORMAL to SpeechRatePreset.NORMAL,
            ConversationSettingAction.SPEECH_SPEED_FAST to SpeechRatePreset.FAST,
            ConversationSettingAction.SPEECH_SPEED_VERY_FAST to SpeechRatePreset.VERY_FAST
        )

        cases.forEach { (action, expected) ->
            val preferences = AppPreferences(FakeVoicePreferenceStorage()).apply {
                setSpeechRatePreset(SpeechRatePreset.NORMAL)
            }
            val result = VoiceSettingsExecutor(preferences).execute(action)

            assertEquals(action.name, expected, preferences.speechRatePreset)
            assertEquals(action.name, false, result.displayRefreshRequired)
            assertEquals(
                action.name,
                if (expected == SpeechRatePreset.NORMAL) {
                    VoiceSettingExecutionStatus.UNCHANGED
                } else {
                    VoiceSettingExecutionStatus.APPLIED
                },
                result.status
            )
            assertEquals(
                action.name,
                "Speech speed is ${if (expected == SpeechRatePreset.NORMAL) "already" else "now"} ${expected.displayName}.",
                result.speech
            )
        }
    }

    @Test
    fun relativeSpeechSpeedActionsStepAndStopAtBoundaries() {
        val cases = listOf(
            Triple(SpeechRatePreset.SLOW, ConversationSettingAction.SPEECH_SPEED_FASTER, SpeechRatePreset.NORMAL),
            Triple(SpeechRatePreset.NORMAL, ConversationSettingAction.SPEECH_SPEED_FASTER, SpeechRatePreset.FAST),
            Triple(SpeechRatePreset.FAST, ConversationSettingAction.SPEECH_SPEED_FASTER, SpeechRatePreset.VERY_FAST),
            Triple(SpeechRatePreset.VERY_FAST, ConversationSettingAction.SPEECH_SPEED_FASTER, SpeechRatePreset.VERY_FAST),
            Triple(SpeechRatePreset.VERY_FAST, ConversationSettingAction.SPEECH_SPEED_SLOWER, SpeechRatePreset.FAST),
            Triple(SpeechRatePreset.FAST, ConversationSettingAction.SPEECH_SPEED_SLOWER, SpeechRatePreset.NORMAL),
            Triple(SpeechRatePreset.NORMAL, ConversationSettingAction.SPEECH_SPEED_SLOWER, SpeechRatePreset.SLOW),
            Triple(SpeechRatePreset.SLOW, ConversationSettingAction.SPEECH_SPEED_SLOWER, SpeechRatePreset.SLOW)
        )

        cases.forEach { (initial, action, expected) ->
            val preferences = AppPreferences(FakeVoicePreferenceStorage()).apply {
                setSpeechRatePreset(initial)
            }
            val result = VoiceSettingsExecutor(preferences).execute(action)

            assertEquals("$initial $action", expected, preferences.speechRatePreset)
            assertEquals(
                "$initial $action",
                if (initial == expected) VoiceSettingExecutionStatus.UNCHANGED else VoiceSettingExecutionStatus.APPLIED,
                result.status
            )
            assertEquals(
                "$initial $action",
                "Speech speed is ${if (initial == expected) "already" else "now"} ${expected.displayName}.",
                result.speech
            )
            assertFalse("$initial $action", result.displayRefreshRequired)
        }
    }

    @Test
    fun hapticClarificationChangesOnlyTheExplicitFollowUpTarget() {
        val preferences = AppPreferences(FakeVoicePreferenceStorage())
        preferences.setProcessingHapticEnabled(true)
        preferences.setSessionEndHapticEnabled(true)
        val initial = VoiceSettingsMutationSafetyPolicy.evaluate(
            "turn off vibration",
            ConversationSettingAction.PROCESSING_HAPTIC_OFF
        )

        assertEquals(VoiceSettingsSafetyDisposition.CLARIFY_HAPTIC_TARGET, initial.disposition)
        assertTrue(preferences.processingHapticEnabled)
        assertTrue(preferences.sessionEndHapticEnabled)

        val followUp = VoiceSettingsMutationSafetyPolicy.evaluateClarification(
            "the processing one",
            pending = requireNotNull(initial.pendingClarification)
        )
        assertEquals(VoiceSettingsSafetyDisposition.ALLOW, followUp?.disposition)
        VoiceSettingsExecutor(preferences).execute(requireNotNull(followUp).authorizedAction)

        assertFalse(preferences.processingHapticEnabled)
        assertTrue(preferences.sessionEndHapticEnabled)

        preferences.setProcessingHapticEnabled(true)
        preferences.setSessionEndHapticEnabled(true)
        val ordinalFollowUp = VoiceSettingsMutationSafetyPolicy.evaluateClarification(
            "the second one",
            pending = requireNotNull(initial.pendingClarification)
        )
        assertEquals(VoiceSettingsSafetyDisposition.ALLOW, ordinalFollowUp?.disposition)
        VoiceSettingsExecutor(preferences).execute(
            requireNotNull(ordinalFollowUp).authorizedAction
        )

        assertTrue(preferences.processingHapticEnabled)
        assertFalse(preferences.sessionEndHapticEnabled)
    }

    @Test
    fun safetyVetoesNeverReachExecutorOrChangePreferences() {
        val cases = listOf(
            "how do i turn on high contrast" to ConversationSettingAction.HIGH_CONTRAST_ON,
            "turn off vibration" to ConversationSettingAction.PROCESSING_HAPTIC_OFF,
            "can you turn off the last text" to ConversationSettingAction.PROCESSING_HAPTIC_OFF,
            "turn on high contrast" to ConversationSettingAction.LARGE_TEXT_ON
        )

        cases.forEach { (utterance, proposedAction) ->
            val preferences = AppPreferences(FakeVoicePreferenceStorage())
            val before = preferenceSnapshot(preferences)
            val safety = VoiceSettingsMutationSafetyPolicy.evaluate(utterance, proposedAction)
            if (safety.disposition == VoiceSettingsSafetyDisposition.ALLOW) {
                VoiceSettingsExecutor(preferences).execute(safety.authorizedAction)
            }

            assertTrue(safety.disposition != VoiceSettingsSafetyDisposition.ALLOW)
            assertEquals(utterance, before, preferenceSnapshot(preferences))
        }
    }

    @Test
    fun contextualRecoveryExecutesFocusedActionInsteadOfWrongModelTarget() {
        val highContrastPreferences = AppPreferences(FakeVoicePreferenceStorage()).apply {
            setHighContrastEnabled(true, PreferenceChangeSource.SYSTEM)
            setProcessingHapticEnabled(true)
        }
        val highContrastFocus = VoiceSettingConversationFocus(VoiceSettingTarget.HIGH_CONTRAST)
        val recoveredHighContrast = requireNotNull(
            VoiceSettingContextualActionResolver.resolve(
                "can you turn it off",
                highContrastFocus
            )
        )
        assertEquals(ConversationSettingAction.HIGH_CONTRAST_OFF, recoveredHighContrast)
        val highContrastSafety = VoiceSettingsMutationSafetyPolicy.evaluate(
            "can you turn it off",
            recoveredHighContrast,
            highContrastFocus
        )
        assertEquals(VoiceSettingsSafetyDisposition.ALLOW, highContrastSafety.disposition)
        VoiceSettingsExecutor(highContrastPreferences).execute(highContrastSafety.authorizedAction)
        assertFalse(highContrastPreferences.highContrastEnabled)
        assertTrue(highContrastPreferences.processingHapticEnabled)

        val largeTextPreferences = AppPreferences(FakeVoicePreferenceStorage()).apply {
            setLargeTextEnabled(false, PreferenceChangeSource.SYSTEM)
            setHighContrastEnabled(false, PreferenceChangeSource.SYSTEM)
        }
        val largeTextFocus = VoiceSettingConversationFocus(VoiceSettingTarget.LARGE_TEXT)
        val recoveredLargeText = requireNotNull(
            VoiceSettingContextualActionResolver.resolve("turn it on", largeTextFocus)
        )
        assertEquals(ConversationSettingAction.LARGE_TEXT_ON, recoveredLargeText)
        val largeTextSafety = VoiceSettingsMutationSafetyPolicy.evaluate(
            "turn it on",
            recoveredLargeText,
            largeTextFocus
        )
        assertEquals(VoiceSettingsSafetyDisposition.ALLOW, largeTextSafety.disposition)
        VoiceSettingsExecutor(largeTextPreferences).execute(largeTextSafety.authorizedAction)
        assertTrue(largeTextPreferences.largeTextEnabled)
        assertFalse(largeTextPreferences.highContrastEnabled)
    }

    @Test
    fun targetlessContextualRequestCannotRecoverOrMutateWithoutFocus() {
        val preferences = AppPreferences(FakeVoicePreferenceStorage())
        val before = preferenceSnapshot(preferences)
        val recovered = VoiceSettingContextualActionResolver.resolve("turn it off", null)
        val modelSafety = VoiceSettingsMutationSafetyPolicy.evaluate(
            "turn it off",
            ConversationSettingAction.HIGH_CONTRAST_OFF
        )

        assertEquals(null, recovered)
        assertEquals(VoiceSettingsSafetyDisposition.CLARIFY_SETTING_TARGET, modelSafety.disposition)
        assertEquals(before, preferenceSnapshot(preferences))
    }

    private fun prepareOpposite(
        preferences: AppPreferences,
        action: ConversationSettingAction
    ) {
        when (action) {
            ConversationSettingAction.LARGE_TEXT_ON ->
                preferences.setLargeTextEnabled(false, PreferenceChangeSource.SYSTEM)
            ConversationSettingAction.LARGE_TEXT_OFF ->
                preferences.setLargeTextEnabled(true, PreferenceChangeSource.SYSTEM)
            ConversationSettingAction.HIGH_CONTRAST_ON ->
                preferences.setHighContrastEnabled(false, PreferenceChangeSource.SYSTEM)
            ConversationSettingAction.HIGH_CONTRAST_OFF ->
                preferences.setHighContrastEnabled(true, PreferenceChangeSource.SYSTEM)
            ConversationSettingAction.PROCESSING_HAPTIC_ON ->
                preferences.setProcessingHapticEnabled(false)
            ConversationSettingAction.PROCESSING_HAPTIC_OFF ->
                preferences.setProcessingHapticEnabled(true)
            ConversationSettingAction.SESSION_END_HAPTIC_ON ->
                preferences.setSessionEndHapticEnabled(false)
            ConversationSettingAction.SESSION_END_HAPTIC_OFF ->
                preferences.setSessionEndHapticEnabled(true)
            ConversationSettingAction.ASSISTANT_TONE_FRIENDLY ->
                preferences.setAssistantTone("Neutral")
            ConversationSettingAction.ASSISTANT_TONE_NEUTRAL ->
                preferences.setAssistantTone("Friendly")
            ConversationSettingAction.ASSISTANT_TONE_PROFESSIONAL ->
                preferences.setAssistantTone("Friendly")
            ConversationSettingAction.REPLY_LENGTH_SHORT ->
                preferences.setReplyLength("Normal")
            ConversationSettingAction.REPLY_LENGTH_NORMAL ->
                preferences.setReplyLength("Short")
            ConversationSettingAction.REPLY_LENGTH_DETAILED ->
                preferences.setReplyLength("Normal")
            ConversationSettingAction.SPEECH_SPEED_SLOW ->
                preferences.setSpeechRatePreset(SpeechRatePreset.NORMAL)
            ConversationSettingAction.SPEECH_SPEED_NORMAL ->
                preferences.setSpeechRatePreset(SpeechRatePreset.SLOW)
            ConversationSettingAction.SPEECH_SPEED_FAST ->
                preferences.setSpeechRatePreset(SpeechRatePreset.NORMAL)
            ConversationSettingAction.SPEECH_SPEED_VERY_FAST ->
                preferences.setSpeechRatePreset(SpeechRatePreset.FAST)
            ConversationSettingAction.SPEECH_SPEED_FASTER ->
                preferences.setSpeechRatePreset(SpeechRatePreset.FAST)
            ConversationSettingAction.SPEECH_SPEED_SLOWER ->
                preferences.setSpeechRatePreset(SpeechRatePreset.NORMAL)
            ConversationSettingAction.NONE -> Unit
        }
    }

    private fun assertRequestedValue(
        preferences: AppPreferences,
        action: ConversationSettingAction
    ) {
        when (action) {
            ConversationSettingAction.LARGE_TEXT_ON -> assertTrue(preferences.largeTextEnabled)
            ConversationSettingAction.LARGE_TEXT_OFF -> assertFalse(preferences.largeTextEnabled)
            ConversationSettingAction.HIGH_CONTRAST_ON -> assertTrue(preferences.highContrastEnabled)
            ConversationSettingAction.HIGH_CONTRAST_OFF -> assertFalse(preferences.highContrastEnabled)
            ConversationSettingAction.PROCESSING_HAPTIC_ON -> assertTrue(preferences.processingHapticEnabled)
            ConversationSettingAction.PROCESSING_HAPTIC_OFF -> assertFalse(preferences.processingHapticEnabled)
            ConversationSettingAction.SESSION_END_HAPTIC_ON -> assertTrue(preferences.sessionEndHapticEnabled)
            ConversationSettingAction.SESSION_END_HAPTIC_OFF -> assertFalse(preferences.sessionEndHapticEnabled)
            ConversationSettingAction.ASSISTANT_TONE_FRIENDLY -> assertEquals("Friendly", preferences.assistantTone)
            ConversationSettingAction.ASSISTANT_TONE_NEUTRAL -> assertEquals("Neutral", preferences.assistantTone)
            ConversationSettingAction.ASSISTANT_TONE_PROFESSIONAL -> assertEquals("Professional", preferences.assistantTone)
            ConversationSettingAction.REPLY_LENGTH_SHORT -> assertEquals("Short", preferences.replyLength)
            ConversationSettingAction.REPLY_LENGTH_NORMAL -> assertEquals("Normal", preferences.replyLength)
            ConversationSettingAction.REPLY_LENGTH_DETAILED -> assertEquals("Detailed", preferences.replyLength)
            ConversationSettingAction.SPEECH_SPEED_SLOW -> assertEquals(SpeechRatePreset.SLOW, preferences.speechRatePreset)
            ConversationSettingAction.SPEECH_SPEED_NORMAL -> assertEquals(SpeechRatePreset.NORMAL, preferences.speechRatePreset)
            ConversationSettingAction.SPEECH_SPEED_FAST -> assertEquals(SpeechRatePreset.FAST, preferences.speechRatePreset)
            ConversationSettingAction.SPEECH_SPEED_VERY_FAST -> assertEquals(SpeechRatePreset.VERY_FAST, preferences.speechRatePreset)
            ConversationSettingAction.SPEECH_SPEED_FASTER -> assertEquals(SpeechRatePreset.VERY_FAST, preferences.speechRatePreset)
            ConversationSettingAction.SPEECH_SPEED_SLOWER -> assertEquals(SpeechRatePreset.SLOW, preferences.speechRatePreset)
            ConversationSettingAction.NONE -> error("NONE has no requested value")
        }
    }

    private fun ConversationSettingAction.isVisual(): Boolean = this in setOf(
        ConversationSettingAction.LARGE_TEXT_ON,
        ConversationSettingAction.LARGE_TEXT_OFF,
        ConversationSettingAction.HIGH_CONTRAST_ON,
        ConversationSettingAction.HIGH_CONTRAST_OFF
    )

    private fun preferenceSnapshot(preferences: AppPreferences): List<Any> = listOf(
        preferences.largeTextEnabled,
        preferences.highContrastEnabled,
        preferences.processingHapticEnabled,
        preferences.sessionEndHapticEnabled,
        preferences.assistantTone,
        preferences.replyLength,
        preferences.speechRatePreset,
        preferences.conversationAgentEndpoint,
        preferences.taskAgentEndpoint
    )
}

private class FakeVoicePreferenceStorage : PreferenceStorage {
    private val values = mutableMapOf<String, Any>()

    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        values[key] as? Boolean ?: defaultValue
    override fun getString(key: String, defaultValue: String?): String? =
        values[key] as? String ?: defaultValue
    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }
    override fun putString(key: String, value: String) {
        values[key] = value
    }
}
