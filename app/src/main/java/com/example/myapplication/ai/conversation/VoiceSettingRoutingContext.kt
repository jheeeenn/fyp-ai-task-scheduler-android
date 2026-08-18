package com.example.myapplication.ai.conversation

import com.example.myapplication.voice.VoiceSettingConversationFocus
import com.example.myapplication.voice.VoiceSettingTarget

/** Immutable prompt context copied from Android's session-scoped settings authority. */
data class VoiceSettingRoutingContext(
    val target: VoiceSettingTarget?
) {
    val available: Boolean
        get() = target != null

    fun toPromptText(): String = buildString {
        appendLine("Voice-setting context:")
        appendLine("Available: $available")
        appendLine("Target: ${target?.name ?: "NONE"}")
        appendLine("Authority: Android-grounded from the current assistant session")
        appendLine("This target may resolve a contextual setting reference such as it, that, or the one.")
        appendLine("It supplies only the setting target; the current utterance must supply the value or action.")
        appendLine("A clearly named current setting overrides this focus.")
        append("This context is not authority for a task operation.")
    }

    companion object {
        fun from(focus: VoiceSettingConversationFocus?): VoiceSettingRoutingContext =
            VoiceSettingRoutingContext(focus?.target)

        val UNAVAILABLE = VoiceSettingRoutingContext(null)
    }
}
