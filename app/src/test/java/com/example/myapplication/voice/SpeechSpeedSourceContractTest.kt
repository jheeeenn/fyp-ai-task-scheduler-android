package com.example.myapplication.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpeechSpeedSourceContractTest {
    private val mainRoot = File("src/main/java/com/example/myapplication")
    private val voiceHelper = mainRoot.resolve("VoiceHelper.kt").readText()
    private val settings = mainRoot.resolve("SettingsActivity.kt").readText()
    private val reminder = mainRoot.resolve("ReminderSpeechService.kt").readText()
    private val executor = mainRoot.resolve("voice/VoiceSettingsExecutor.kt").readText()
    private val home = mainRoot.resolve("HomeActivity.kt").readText()
    private val settingsLayout = File("src/main/res/layout/activity_settings.xml").readText()

    @Test
    fun voiceHelperOwnsStoredAndLiveSpeechRateApplication() {
        assertTrue(voiceHelper.contains("AppPreferences(context.applicationContext)"))
        assertTrue(voiceHelper.contains("speechRatePreset = appPreferences.speechRatePreset"))
        assertTrue(voiceHelper.contains("applySpeechRate(speechRatePreset)"))
        assertTrue(voiceHelper.contains("tts.setSpeechRate(preset.rate)"))
        assertTrue(voiceHelper.contains("refreshSpeechRateFromPreferences()"))
        val speakWithResult = voiceHelper
            .substringAfter("fun speakWithResult(")
            .substringBefore("fun applySpeechRate(")
        assertOrdered(speakWithResult, "refreshSpeechRateFromPreferences()", "speakInternal(text")
    }

    @Test
    fun settingsCardUsesAuthoritativeExecutorAndAppliesStoredBoundedRateBeforeConfirmation() {
        assertTrue(settingsLayout.contains("android:id=\"@+id/cardSpeechSpeed\""))
        assertTrue(settingsLayout.contains("android:id=\"@+id/tvSpeechSpeedValue\""))
        assertTrue(settingsLayout.contains("android:text=\"@string/speech_speed\""))
        val selection = settings
            .substringAfter("SettingsCardSelectionMove.SELECT_VALUE ->")
            .substringBefore("SettingsCardSelectionMove.ASK_OPTIONS")
        assertOrdered(selection, "voiceSettingsExecutor.execute", "voiceHelper.applySpeechRate")
        assertTrue(selection.contains("appPreferences.speechRatePreset"))
        assertTrue(settings.contains("SettingsCardSelectionAuthority"))
        assertFalse(settings.contains("recreateAfterVisualSettingChange(R.id.cardSpeechSpeed"))
    }

    @Test
    fun reminderInheritsTheSharedVoiceHelperWithoutDuplicateRateLogic() {
        assertTrue(reminder.contains("voiceHelper = VoiceHelper(applicationContext)"))
        assertTrue(reminder.contains("helper.speak(request.spokenText)"))
        assertFalse(reminder.contains("setSpeechRate"))
        assertFalse(reminder.contains("SpeechRatePreset"))
    }

    @Test
    fun voiceSettingsExecutorPersistsBeforeSharedVoiceHelperSpeaksConfirmation() {
        assertTrue(executor.contains("preferences.setSpeechRatePreset(requested)"))
        assertFalse(executor.contains("TextToSpeech"))
        assertFalse(executor.contains("VoiceHelper"))
        val executeAllowed = home
            .substringAfter("private fun executeAllowedVoiceSetting(action: ConversationSettingAction)")
            .substringBefore("private fun deliverVoiceSettingsSafetyResponse")
        assertOrdered(
            executeAllowed,
            "voiceSettingsExecutor.execute(action)",
            "assistantSession.speak(result.speech"
        )
    }

    private fun assertOrdered(source: String, first: String, second: String) {
        val firstIndex = source.indexOf(first)
        val secondIndex = source.indexOf(second)
        assertTrue("Missing $first", firstIndex >= 0)
        assertTrue("Missing $second", secondIndex >= 0)
        assertTrue("Expected $first before $second", firstIndex < secondIndex)
    }
}
