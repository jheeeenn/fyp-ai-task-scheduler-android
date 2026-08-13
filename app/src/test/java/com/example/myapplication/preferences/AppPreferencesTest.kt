package com.example.myapplication.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun accessibilityValuesPersistAcrossManagerInstances() {
        val storage = FakePreferenceStorage()
        val first = AppPreferences(storage)

        assertFalse(first.largeTextEnabled)
        assertFalse(first.highContrastEnabled)

        first.setLargeTextEnabled(true, PreferenceChangeSource.TOUCH)
        first.setHighContrastEnabled(true, PreferenceChangeSource.TOUCH)

        val recreated = AppPreferences(storage)
        assertTrue(recreated.largeTextEnabled)
        assertTrue(recreated.highContrastEnabled)
    }

    @Test
    fun existingPreferenceDefaultsAndStoredValuesRemainCompatible() {
        val storage = FakePreferenceStorage()
        val preferences = AppPreferences(storage)

        assertEquals("Friendly", preferences.assistantTone)
        assertEquals("Normal", preferences.replyLength)
        assertTrue(preferences.processingHapticEnabled)
        assertTrue(preferences.sessionEndHapticEnabled)

        preferences.setAssistantTone("Professional")
        preferences.setReplyLength("Detailed")
        preferences.setProcessingHapticEnabled(false)
        preferences.setSessionEndHapticEnabled(false)

        val recreated = AppPreferences(storage)
        assertEquals("Professional", recreated.assistantTone)
        assertEquals("Detailed", recreated.replyLength)
        assertFalse(recreated.processingHapticEnabled)
        assertFalse(recreated.sessionEndHapticEnabled)
    }

    @Test
    fun dedicatedAndLegacyEndpointValuesRemainCompatibleAndEditable() {
        val storage = FakePreferenceStorage()
        val preferences = AppPreferences(storage)

        assertNull(preferences.configuredConversationAgentEndpoint())
        assertNull(preferences.configuredTaskAgentEndpoint())

        storage.putString(AppPreferences.KEY_LM_STUDIO_ENDPOINT, "http://legacy")
        assertEquals("http://legacy", preferences.conversationAgentEndpoint)
        assertEquals("http://legacy", preferences.taskAgentEndpoint)

        preferences.setConversationAgentEndpoint("http://conversation")
        preferences.setTaskAgentEndpoint("http://task")
        assertEquals("http://conversation", AppPreferences(storage).conversationAgentEndpoint)
        assertEquals("http://task", AppPreferences(storage).taskAgentEndpoint)
    }
}

private class FakePreferenceStorage : PreferenceStorage {
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
