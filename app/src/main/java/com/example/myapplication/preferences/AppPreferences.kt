package com.example.myapplication.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class AppPreferences internal constructor(
    private val storage: PreferenceStorage
) {
    constructor(context: Context) : this(
        SharedPreferencesStorage(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        )
    )

    val largeTextEnabled: Boolean
        get() = storage.getBoolean(KEY_LARGE_TEXT, false)

    val highContrastEnabled: Boolean
        get() = storage.getBoolean(KEY_HIGH_CONTRAST, false)

    val processingHapticEnabled: Boolean
        get() = storage.getBoolean(KEY_PROCESSING_HAPTIC_FEEDBACK, true)

    val sessionEndHapticEnabled: Boolean
        get() = storage.getBoolean(KEY_SESSION_END_HAPTIC_FEEDBACK, true)

    val assistantTone: String
        get() = storage.getString(KEY_ASSISTANT_TONE, DEFAULT_ASSISTANT_TONE)
            ?: DEFAULT_ASSISTANT_TONE

    val replyLength: String
        get() = storage.getString(KEY_REPLY_LENGTH, DEFAULT_REPLY_LENGTH)
            ?: DEFAULT_REPLY_LENGTH

    val speechRatePreset: SpeechRatePreset
        get() = SpeechRatePreset.fromStoredValue(
            storage.getString(KEY_SPEECH_RATE, DEFAULT_SPEECH_RATE)
        )

    val conversationAgentEndpoint: String
        get() = configuredEndpoint(KEY_CONVERSATION_AGENT_ENDPOINT)
            ?: DEFAULT_CONVERSATION_AGENT_ENDPOINT

    val taskAgentEndpoint: String
        get() = configuredEndpoint(KEY_TASK_AGENT_ENDPOINT) ?: DEFAULT_TASK_AGENT_ENDPOINT

    fun setLargeTextEnabled(value: Boolean, source: PreferenceChangeSource) {
        storage.putBoolean(KEY_LARGE_TEXT, value)
        logAccessibilityChange(AccessibilitySetting.LARGE_TEXT, value, source)
    }

    fun setHighContrastEnabled(value: Boolean, source: PreferenceChangeSource) {
        storage.putBoolean(KEY_HIGH_CONTRAST, value)
        logAccessibilityChange(AccessibilitySetting.HIGH_CONTRAST, value, source)
    }

    fun setProcessingHapticEnabled(value: Boolean) {
        storage.putBoolean(KEY_PROCESSING_HAPTIC_FEEDBACK, value)
    }

    fun setSessionEndHapticEnabled(value: Boolean) {
        storage.putBoolean(KEY_SESSION_END_HAPTIC_FEEDBACK, value)
    }

    fun setAssistantTone(value: String) {
        storage.putString(KEY_ASSISTANT_TONE, value)
    }

    fun setReplyLength(value: String) {
        storage.putString(KEY_REPLY_LENGTH, value)
    }

    fun setSpeechRatePreset(preset: SpeechRatePreset) {
        storage.putString(KEY_SPEECH_RATE, preset.displayName)
    }

    fun setConversationAgentEndpoint(value: String) {
        storage.putString(KEY_CONVERSATION_AGENT_ENDPOINT, value)
    }

    fun setTaskAgentEndpoint(value: String) {
        storage.putString(KEY_TASK_AGENT_ENDPOINT, value)
    }

    fun configuredConversationAgentEndpoint(): String? =
        configuredEndpoint(KEY_CONVERSATION_AGENT_ENDPOINT)

    fun configuredTaskAgentEndpoint(): String? = configuredEndpoint(KEY_TASK_AGENT_ENDPOINT)

    private fun configuredEndpoint(dedicatedKey: String): String? {
        val dedicatedValue = if (storage.contains(dedicatedKey)) {
            storage.getString(dedicatedKey, null)
        } else {
            null
        }
        if (!dedicatedValue.isNullOrBlank()) return dedicatedValue

        val legacyValue = storage.getString(KEY_LM_STUDIO_ENDPOINT, null)
        return legacyValue?.takeIf(String::isNotBlank)
    }

    private fun logAccessibilityChange(
        setting: AccessibilitySetting,
        value: Boolean,
        source: PreferenceChangeSource
    ) {
        Log.d(
            ACCESSIBILITY_LOG_TAG,
            "setting=${setting.name} value=${if (value) "ON" else "OFF"} source=${source.name}"
        )
    }

    companion object {
        const val PREFS_NAME = "assistant_settings"
        const val KEY_ASSISTANT_TONE = "assistant_tone"
        const val KEY_REPLY_LENGTH = "reply_length"
        const val KEY_SPEECH_RATE = "speech_rate"
        const val KEY_LARGE_TEXT = "large_text"
        const val KEY_HIGH_CONTRAST = "high_contrast"
        const val KEY_PROCESSING_HAPTIC_FEEDBACK = "processing_haptic_feedback"
        const val KEY_SESSION_END_HAPTIC_FEEDBACK = "session_end_haptic_feedback"
        const val KEY_LM_STUDIO_ENDPOINT = "lm_studio_endpoint"
        const val KEY_CONVERSATION_AGENT_ENDPOINT = "conversation_agent_endpoint"
        const val KEY_TASK_AGENT_ENDPOINT = "task_agent_endpoint"

        const val DEFAULT_ASSISTANT_TONE = "Friendly"
        const val DEFAULT_REPLY_LENGTH = "Normal"
        const val DEFAULT_SPEECH_RATE = "Normal"
        const val DEFAULT_CONVERSATION_AGENT_ENDPOINT =
            "http://192.168.0.132:1234/v1/chat/completions"
        const val DEFAULT_TASK_AGENT_ENDPOINT =
            "http://192.168.0.132:1234/v1/chat/completions"

        private const val ACCESSIBILITY_LOG_TAG = "ACCESSIBILITY_SETTINGS"
    }
}

enum class PreferenceChangeSource {
    TOUCH,
    VOICE,
    SYSTEM
}

private enum class AccessibilitySetting {
    LARGE_TEXT,
    HIGH_CONTRAST
}

internal interface PreferenceStorage {
    fun contains(key: String): Boolean
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getString(key: String, defaultValue: String?): String?
    fun putBoolean(key: String, value: Boolean)
    fun putString(key: String, value: String)
}

private class SharedPreferencesStorage(
    private val preferences: SharedPreferences
) : PreferenceStorage {
    override fun contains(key: String): Boolean = preferences.contains(key)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        preferences.getBoolean(key, defaultValue)

    override fun getString(key: String, defaultValue: String?): String? =
        preferences.getString(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }
}
