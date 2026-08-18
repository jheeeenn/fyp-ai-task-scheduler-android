package com.example.myapplication.preferences

import com.example.myapplication.ai.conversation.ConversationSettingTarget
import com.example.myapplication.voice.VoiceSettingReadStatus
import com.example.myapplication.voice.VoiceSettingsStatusReader
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSettingsStatusReaderTest {
    @Test
    fun readsAllSixCurrentValuesWithDeterministicSpeechAndNoWrites() {
        val storage = TrackingPreferenceStorage()
        val preferences = AppPreferences(storage).apply {
            setLargeTextEnabled(true, PreferenceChangeSource.SYSTEM)
            setHighContrastEnabled(false, PreferenceChangeSource.SYSTEM)
            setProcessingHapticEnabled(true)
            setSessionEndHapticEnabled(false)
            setAssistantTone("Professional")
            setReplyLength("Short")
        }
        storage.writeCount = 0
        val reader = VoiceSettingsStatusReader(preferences)

        val expected = mapOf(
            ConversationSettingTarget.LARGE_TEXT to "Large text is currently on.",
            ConversationSettingTarget.HIGH_CONTRAST to "High contrast is currently off.",
            ConversationSettingTarget.PROCESSING_HAPTIC to
                "Processing haptic feedback is currently on.",
            ConversationSettingTarget.SESSION_END_HAPTIC to
                "Session end haptic feedback is currently off.",
            ConversationSettingTarget.ASSISTANT_TONE to
                "Assistant tone is currently Professional.",
            ConversationSettingTarget.REPLY_LENGTH to "Reply length is currently Short."
        )

        expected.forEach { (target, speech) ->
            val result = reader.read(target)
            assertEquals(target.name, VoiceSettingReadStatus.READ, result.status)
            assertEquals(target.name, speech, result.speech)
        }
        assertEquals(0, storage.writeCount)
    }

    @Test
    fun noneIsRejectedWithoutWriting() {
        val storage = TrackingPreferenceStorage()
        val result = VoiceSettingsStatusReader(AppPreferences(storage))
            .read(ConversationSettingTarget.NONE)

        assertEquals(VoiceSettingReadStatus.REJECTED, result.status)
        assertEquals(0, storage.writeCount)
    }
}

private class TrackingPreferenceStorage : PreferenceStorage {
    private val values = mutableMapOf<String, Any>()
    var writeCount: Int = 0

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        values[key] as? Boolean ?: defaultValue

    override fun getString(key: String, defaultValue: String?): String? =
        values[key] as? String ?: defaultValue

    override fun putBoolean(key: String, value: Boolean) {
        writeCount++
        values[key] = value
    }

    override fun putString(key: String, value: String) {
        writeCount++
        values[key] = value
    }
}
