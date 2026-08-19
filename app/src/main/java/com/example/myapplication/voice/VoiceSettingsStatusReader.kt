package com.example.myapplication.voice

import com.example.myapplication.ai.conversation.ConversationSettingTarget
import com.example.myapplication.preferences.AppPreferences

enum class VoiceSettingReadStatus {
    READ,
    REJECTED
}

data class VoiceSettingReadResult(
    val target: ConversationSettingTarget,
    val status: VoiceSettingReadStatus,
    val speech: String
)

/** Reads only Android-owned user-facing preferences and never writes state. */
class VoiceSettingsStatusReader(
    private val preferences: AppPreferences
) {
    fun read(target: ConversationSettingTarget): VoiceSettingReadResult = when (target) {
        ConversationSettingTarget.NONE -> VoiceSettingReadResult(
            target,
            VoiceSettingReadStatus.REJECTED,
            "I could not identify which setting to read."
        )
        ConversationSettingTarget.LARGE_TEXT -> booleanResult(
            target,
            "Large text",
            preferences.largeTextEnabled
        )
        ConversationSettingTarget.HIGH_CONTRAST -> booleanResult(
            target,
            "High contrast",
            preferences.highContrastEnabled
        )
        ConversationSettingTarget.PROCESSING_HAPTIC -> booleanResult(
            target,
            "Processing haptic feedback",
            preferences.processingHapticEnabled
        )
        ConversationSettingTarget.SESSION_END_HAPTIC -> booleanResult(
            target,
            "Session end haptic feedback",
            preferences.sessionEndHapticEnabled
        )
        ConversationSettingTarget.ASSISTANT_TONE -> valueResult(
            target,
            "Assistant tone",
            preferences.assistantTone
        )
        ConversationSettingTarget.REPLY_LENGTH -> valueResult(
            target,
            "Reply length",
            preferences.replyLength
        )
        ConversationSettingTarget.SPEECH_SPEED -> valueResult(
            target,
            "Speech speed",
            preferences.speechRatePreset.displayName
        )
    }

    private fun booleanResult(
        target: ConversationSettingTarget,
        label: String,
        enabled: Boolean
    ) = VoiceSettingReadResult(
        target,
        VoiceSettingReadStatus.READ,
        "$label is currently ${if (enabled) "on" else "off"}."
    )

    private fun valueResult(
        target: ConversationSettingTarget,
        label: String,
        value: String
    ) = VoiceSettingReadResult(
        target,
        VoiceSettingReadStatus.READ,
        "$label is currently $value."
    )
}

fun ConversationSettingTarget.voiceSettingTarget(): VoiceSettingTarget? = when (this) {
    ConversationSettingTarget.NONE -> null
    ConversationSettingTarget.LARGE_TEXT -> VoiceSettingTarget.LARGE_TEXT
    ConversationSettingTarget.HIGH_CONTRAST -> VoiceSettingTarget.HIGH_CONTRAST
    ConversationSettingTarget.PROCESSING_HAPTIC -> VoiceSettingTarget.PROCESSING_HAPTIC
    ConversationSettingTarget.SESSION_END_HAPTIC -> VoiceSettingTarget.SESSION_END_HAPTIC
    ConversationSettingTarget.ASSISTANT_TONE -> VoiceSettingTarget.ASSISTANT_TONE
    ConversationSettingTarget.REPLY_LENGTH -> VoiceSettingTarget.REPLY_LENGTH
    ConversationSettingTarget.SPEECH_SPEED -> VoiceSettingTarget.SPEECH_SPEED
}
