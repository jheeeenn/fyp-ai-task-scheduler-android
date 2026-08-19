package com.example.myapplication.voice

import com.example.myapplication.ai.conversation.ConversationSettingTarget
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute

/** Narrow read-only recovery for one explicitly grounded current-value question. */
object VoiceSettingsReadRecoveryPolicy {
    fun recoverDecision(normalizedUtterance: String): ConversationDecision? =
        recoverTarget(normalizedUtterance)?.let { target ->
            ConversationDecision(
                route = ConversationRoute.SETTINGS_READ,
                settingTarget = target,
                confidence = 1.0,
                listenAgain = true,
                source = "android_voice_settings_read_recovery"
            )
        }

    fun recoverTarget(normalizedUtterance: String): ConversationSettingTarget? {
        val text = normalizedUtterance.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val target = VoiceSettingsMutationSafetyPolicy.groundedTarget(text) ?: return null
        val isBooleanStatusQuestion =
            (text.startsWith("is ") || text.startsWith("are ")) && containsAny(
                text,
                " on", " off", "enabled", "disabled", "turned", "currently", "set to"
            )
        val asksWhatOrWhich = text.startsWith("what ") || text.startsWith("which ")
        val asksCurrentValue = asksWhatOrWhich && containsAny(
            text,
            "current value", "currently", "set to"
        )
        val isValueStatusQuestion = asksCurrentValue ||
            (target in setOf(
                VoiceSettingTarget.ASSISTANT_TONE,
                VoiceSettingTarget.REPLY_LENGTH,
                VoiceSettingTarget.SPEECH_SPEED
            ) && asksWhatOrWhich && containsAny(text, "using", "do you use"))
            || (target == VoiceSettingTarget.SPEECH_SPEED && asksWhatOrWhich &&
                containsAny(text, "speaking at"))
        if (!isBooleanStatusQuestion && !isValueStatusQuestion) return null
        return target.conversationSettingTarget()
    }

    private fun VoiceSettingTarget.conversationSettingTarget(): ConversationSettingTarget =
        when (this) {
            VoiceSettingTarget.LARGE_TEXT -> ConversationSettingTarget.LARGE_TEXT
            VoiceSettingTarget.HIGH_CONTRAST -> ConversationSettingTarget.HIGH_CONTRAST
            VoiceSettingTarget.PROCESSING_HAPTIC -> ConversationSettingTarget.PROCESSING_HAPTIC
            VoiceSettingTarget.SESSION_END_HAPTIC -> ConversationSettingTarget.SESSION_END_HAPTIC
            VoiceSettingTarget.ASSISTANT_TONE -> ConversationSettingTarget.ASSISTANT_TONE
            VoiceSettingTarget.REPLY_LENGTH -> ConversationSettingTarget.REPLY_LENGTH
            VoiceSettingTarget.SPEECH_SPEED -> ConversationSettingTarget.SPEECH_SPEED
        }

    private fun containsAny(text: String, vararg values: String): Boolean =
        values.any(text::contains)
}
