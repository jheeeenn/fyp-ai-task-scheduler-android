package com.example.myapplication.voice

import android.util.Log
import com.example.myapplication.ai.conversation.ConversationSettingAction
import com.example.myapplication.preferences.AppPreferences
import com.example.myapplication.preferences.PreferenceChangeSource
import com.example.myapplication.preferences.SpeechRatePreset

enum class VoiceSettingExecutionStatus {
    APPLIED,
    UNCHANGED,
    REJECTED
}

data class VoiceSettingExecutionResult(
    val action: ConversationSettingAction,
    val status: VoiceSettingExecutionStatus,
    val speech: String,
    val displayRefreshRequired: Boolean = false
)

/** Executes only the bounded settings actions proposed by the Conversation Agent. */
class VoiceSettingsExecutor(
    private val preferences: AppPreferences
) {
    fun execute(action: ConversationSettingAction): VoiceSettingExecutionResult {
        val result = when (action) {
            ConversationSettingAction.NONE -> rejected(action)
            ConversationSettingAction.LARGE_TEXT_ON -> booleanSetting(
                action = action,
                current = preferences.largeTextEnabled,
                requested = true,
                label = "Large text",
                visual = true,
                apply = { preferences.setLargeTextEnabled(it, PreferenceChangeSource.VOICE) },
                verify = { preferences.largeTextEnabled }
            )
            ConversationSettingAction.LARGE_TEXT_OFF -> booleanSetting(
                action = action,
                current = preferences.largeTextEnabled,
                requested = false,
                label = "Large text",
                visual = true,
                apply = { preferences.setLargeTextEnabled(it, PreferenceChangeSource.VOICE) },
                verify = { preferences.largeTextEnabled }
            )
            ConversationSettingAction.HIGH_CONTRAST_ON -> booleanSetting(
                action = action,
                current = preferences.highContrastEnabled,
                requested = true,
                label = "High contrast",
                visual = true,
                apply = { preferences.setHighContrastEnabled(it, PreferenceChangeSource.VOICE) },
                verify = { preferences.highContrastEnabled }
            )
            ConversationSettingAction.HIGH_CONTRAST_OFF -> booleanSetting(
                action = action,
                current = preferences.highContrastEnabled,
                requested = false,
                label = "High contrast",
                visual = true,
                apply = { preferences.setHighContrastEnabled(it, PreferenceChangeSource.VOICE) },
                verify = { preferences.highContrastEnabled }
            )
            ConversationSettingAction.PROCESSING_HAPTIC_ON -> booleanSetting(
                action = action,
                current = preferences.processingHapticEnabled,
                requested = true,
                label = "Processing haptic feedback",
                apply = preferences::setProcessingHapticEnabled,
                verify = { preferences.processingHapticEnabled }
            )
            ConversationSettingAction.PROCESSING_HAPTIC_OFF -> booleanSetting(
                action = action,
                current = preferences.processingHapticEnabled,
                requested = false,
                label = "Processing haptic feedback",
                apply = preferences::setProcessingHapticEnabled,
                verify = { preferences.processingHapticEnabled }
            )
            ConversationSettingAction.SESSION_END_HAPTIC_ON -> booleanSetting(
                action = action,
                current = preferences.sessionEndHapticEnabled,
                requested = true,
                label = "Session end haptic feedback",
                apply = preferences::setSessionEndHapticEnabled,
                verify = { preferences.sessionEndHapticEnabled }
            )
            ConversationSettingAction.SESSION_END_HAPTIC_OFF -> booleanSetting(
                action = action,
                current = preferences.sessionEndHapticEnabled,
                requested = false,
                label = "Session end haptic feedback",
                apply = preferences::setSessionEndHapticEnabled,
                verify = { preferences.sessionEndHapticEnabled }
            )
            ConversationSettingAction.ASSISTANT_TONE_FRIENDLY -> stringSetting(
                action,
                preferences.assistantTone,
                "Friendly",
                "Assistant tone",
                preferences::setAssistantTone,
                { preferences.assistantTone }
            )
            ConversationSettingAction.ASSISTANT_TONE_NEUTRAL -> stringSetting(
                action,
                preferences.assistantTone,
                "Neutral",
                "Assistant tone",
                preferences::setAssistantTone,
                { preferences.assistantTone }
            )
            ConversationSettingAction.ASSISTANT_TONE_PROFESSIONAL -> stringSetting(
                action,
                preferences.assistantTone,
                "Professional",
                "Assistant tone",
                preferences::setAssistantTone,
                { preferences.assistantTone }
            )
            ConversationSettingAction.REPLY_LENGTH_SHORT -> stringSetting(
                action,
                preferences.replyLength,
                "Short",
                "Reply length",
                preferences::setReplyLength,
                { preferences.replyLength }
            )
            ConversationSettingAction.REPLY_LENGTH_NORMAL -> stringSetting(
                action,
                preferences.replyLength,
                "Normal",
                "Reply length",
                preferences::setReplyLength,
                { preferences.replyLength }
            )
            ConversationSettingAction.REPLY_LENGTH_DETAILED -> stringSetting(
                action,
                preferences.replyLength,
                "Detailed",
                "Reply length",
                preferences::setReplyLength,
                { preferences.replyLength }
            )
            ConversationSettingAction.SPEECH_SPEED_SLOW -> speechSpeedSetting(
                action,
                SpeechRatePreset.SLOW
            )
            ConversationSettingAction.SPEECH_SPEED_NORMAL -> speechSpeedSetting(
                action,
                SpeechRatePreset.NORMAL
            )
            ConversationSettingAction.SPEECH_SPEED_FAST -> speechSpeedSetting(
                action,
                SpeechRatePreset.FAST
            )
            ConversationSettingAction.SPEECH_SPEED_VERY_FAST -> speechSpeedSetting(
                action,
                SpeechRatePreset.VERY_FAST
            )
            ConversationSettingAction.SPEECH_SPEED_FASTER -> speechSpeedSetting(
                action,
                fasterPreset(preferences.speechRatePreset)
            )
            ConversationSettingAction.SPEECH_SPEED_SLOWER -> speechSpeedSetting(
                action,
                slowerPreset(preferences.speechRatePreset)
            )
        }
        Log.d(
            LOG_TAG,
            "action=${action.name} result=${result.status.name} source=VOICE " +
                "displayRefreshPending=${result.displayRefreshRequired}"
        )
        return result
    }

    private fun booleanSetting(
        action: ConversationSettingAction,
        current: Boolean,
        requested: Boolean,
        label: String,
        visual: Boolean = false,
        apply: (Boolean) -> Unit,
        verify: () -> Boolean
    ): VoiceSettingExecutionResult {
        val state = if (requested) "on" else "off"
        if (current == requested) {
            return VoiceSettingExecutionResult(
                action,
                VoiceSettingExecutionStatus.UNCHANGED,
                "$label is already $state."
            )
        }
        apply(requested)
        if (verify() != requested) return rejected(action)
        val refreshSpeech = if (visual) {
            " The screen will refresh when you finish with the assistant."
        } else {
            ""
        }
        return VoiceSettingExecutionResult(
            action,
            VoiceSettingExecutionStatus.APPLIED,
            "$label is $state.$refreshSpeech",
            displayRefreshRequired = visual
        )
    }

    private fun stringSetting(
        action: ConversationSettingAction,
        current: String,
        requested: String,
        label: String,
        apply: (String) -> Unit,
        verify: () -> String
    ): VoiceSettingExecutionResult {
        if (current == requested) {
            return VoiceSettingExecutionResult(
                action,
                VoiceSettingExecutionStatus.UNCHANGED,
                "$label is already $requested."
            )
        }
        apply(requested)
        if (verify() != requested) return rejected(action)
        return VoiceSettingExecutionResult(
            action,
            VoiceSettingExecutionStatus.APPLIED,
            "$label is now $requested."
        )
    }

    private fun speechSpeedSetting(
        action: ConversationSettingAction,
        requested: SpeechRatePreset
    ): VoiceSettingExecutionResult {
        val current = preferences.speechRatePreset
        if (current == requested) {
            return VoiceSettingExecutionResult(
                action,
                VoiceSettingExecutionStatus.UNCHANGED,
                "Speech speed is already ${requested.displayName}."
            )
        }
        preferences.setSpeechRatePreset(requested)
        if (preferences.speechRatePreset != requested) return rejected(action)
        return VoiceSettingExecutionResult(
            action,
            VoiceSettingExecutionStatus.APPLIED,
            "Speech speed is now ${requested.displayName}."
        )
    }

    private fun fasterPreset(current: SpeechRatePreset): SpeechRatePreset = when (current) {
        SpeechRatePreset.SLOW -> SpeechRatePreset.NORMAL
        SpeechRatePreset.NORMAL -> SpeechRatePreset.FAST
        SpeechRatePreset.FAST -> SpeechRatePreset.VERY_FAST
        SpeechRatePreset.VERY_FAST -> SpeechRatePreset.VERY_FAST
    }

    private fun slowerPreset(current: SpeechRatePreset): SpeechRatePreset = when (current) {
        SpeechRatePreset.SLOW -> SpeechRatePreset.SLOW
        SpeechRatePreset.NORMAL -> SpeechRatePreset.SLOW
        SpeechRatePreset.FAST -> SpeechRatePreset.NORMAL
        SpeechRatePreset.VERY_FAST -> SpeechRatePreset.FAST
    }

    private fun rejected(action: ConversationSettingAction) = VoiceSettingExecutionResult(
        action,
        VoiceSettingExecutionStatus.REJECTED,
        "I could not change that setting safely."
    )

    private companion object {
        const val LOG_TAG = "VOICE_SETTINGS"
    }
}
