package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeveloperTestHarnessSourceContractTest {
    private val mainRoot = File("src/main")
    private val sourceRoot = mainRoot.resolve("java/com/example/myapplication")
    private val developer = sourceRoot.resolve("DeveloperTestActivity.kt").readText()
    private val home = sourceRoot.resolve("HomeActivity.kt").readText()
    private val session = sourceRoot.resolve("voice/AssistantVoiceSession.kt").readText()
    private val voiceHelper = sourceRoot.resolve("VoiceHelper.kt").readText()
    private val advanced = sourceRoot.resolve("AdvancedSettingsActivity.kt").readText()
    private val advancedLayout = mainRoot.resolve("res/layout/activity_advanced_settings.xml").readText()
    private val developerLayout = mainRoot.resolve("res/layout/dialog_developer_test.xml").readText()
    private val manifest = mainRoot.resolve("AndroidManifest.xml").readText()

    @Test
    fun advancedSettingsAloneExposesDeveloperTestingEntry() {
        assertTrue(advancedLayout.contains("@+id/cardDeveloperTesting"))
        assertTrue(advancedLayout.contains("@string/developer_testing"))
        assertTrue(advanced.contains("DeveloperTestActivity::class.java"))
        assertFalse(sourceRoot.resolve("AppGuidanceCatalog.kt").takeIf(File::exists)
            ?.readText().orEmpty().contains("DeveloperTestActivity"))
    }

    @Test
    fun developerActivityIsPrivatePortraitAndUsesAFullScreenOverlay() {
        val declaration = manifest.substringAfter("android:name=\".DeveloperTestActivity\"")
            .substringBefore("/>")

        assertTrue(declaration.contains("android:exported=\"false\""))
        assertTrue(declaration.contains("android:screenOrientation=\"portrait\""))
        assertTrue(developer.contains("class DeveloperTestActivity : HomeActivity()"))
        assertTrue(developer.contains("Dialog(this)"))
        assertTrue(developer.contains("WindowManager.LayoutParams.MATCH_PARENT"))
        val onCreate = developer.substringAfter("override fun onCreate")
            .substringBefore("override fun onAssistantTranscript")
        assertFalse(onCreate.contains("setContentView("))
    }

    @Test
    fun developerHarnessDoesNotConstructASecondBusinessPipeline() {
        listOf(
            "ConversationOrchestrator(",
            "AgentOrchestrator(",
            "LaptopAgentClient(",
            "ConversationAgentClient(",
            "AppDatabase",
            "RoutineDraftController(",
            "BreakdownDraftController("
        ).forEach { forbidden ->
            assertFalse("Developer harness must not contain $forbidden", developer.contains(forbidden))
        }
    }

    @Test
    fun persistentTypedInputReachesTheExistingHomeAssistantHostPath() {
        val hook = home.substringAfter("protected fun submitPersistentTypedAssistantText")
            .substringBefore("override fun onResume")
        val host = home.substringAfter("override fun onAssistantFinalText(text: String)")
            .substringBefore("override fun onAssistantCancelled")
        val typed = session.substringAfter("fun submitTypedText")
            .substringBefore("fun onTypedInputCancelled")

        assertTrue(developer.contains("submitPersistentTypedAssistantText(typedText)"))
        assertTrue(hook.contains("assistantSession.submitTypedText(text, clearConversation = false)"))
        assertTrue(typed.contains("host.onAssistantFinalText("))
        assertTrue(host.contains("handleVoiceCommand(text)"))
    }

    @Test
    fun normalVoiceDefaultsAndDeveloperModeGuardsAreExplicit() {
        assertTrue(session.contains("AssistantInteractionMode.NORMAL_VOICE"))
        assertTrue(session.contains("private val shouldSpeakAudio: () -> Boolean = { true }"))
        assertTrue(session.contains("if (!usesNormalVoiceInteraction) return"))
        val initialization = session.substringAfter("fun ensureInitialized()")
            .substringBefore("fun startPassiveSession")
        val restart = session.substringAfter("private fun postRecognitionRestart(")
            .substringBefore("private fun cancelRecognitionIfActive")

        assertTrue(initialization.startsWith(" {\n        if (!usesNormalVoiceInteraction) return"))
        assertTrue(initialization.contains("SpeechRecognizer.createSpeechRecognizer"))
        assertTrue(restart.contains("if (!usesNormalVoiceInteraction) return"))
        assertTrue(developer.contains("AssistantInteractionMode.DEVELOPER_TEXT"))
        assertTrue(session.contains("usesNormalVoiceInteraction && AppPreferences(activity).processingHapticEnabled"))
        assertTrue(session.contains("usesNormalVoiceInteraction && AppPreferences(activity).sessionEndHapticEnabled"))
    }

    @Test
    fun transcriptTtsAndRepeatedInputStayAtTheSessionPresentationBoundary() {
        assertTrue(session.contains("transcriptObserver(AssistantTranscriptEvent(AssistantTranscriptRole.USER, text))"))
        assertTrue(session.contains("transcriptObserver(AssistantTranscriptEvent(AssistantTranscriptRole.ASSISTANT, text))"))
        assertTrue(session.contains("voiceHelper.setShouldSpeakAudio(shouldSpeakAudio)"))
        assertTrue(voiceHelper.contains("if (!shouldSpeakAudio())"))
        assertTrue(voiceHelper.contains("onFinished(true)"))
        assertTrue(developer.contains("override fun shouldSpeakAssistantAudio(): Boolean = ttsEnabled"))
        assertTrue(developerLayout.contains("@+id/switchDeveloperTestTts"))
        assertTrue(developerLayout.contains("android:checked=\"false\""))
        assertTrue(developerLayout.contains("@+id/etDeveloperTestInput"))
        assertTrue(developerLayout.contains("@+id/btnDeveloperTestSend"))
        assertTrue(developer.contains("EditorInfo.IME_ACTION_SEND"))
    }

    @Test
    fun resetRecreatesOnlyTheInMemoryActivitySession() {
        val reset = developer.substringAfter("R.id.btnDeveloperTestReset")
            .substringBefore("R.id.btnDeveloperTestBack")

        assertTrue(reset.contains("recreate()"))
        assertFalse(reset.contains("AppDatabase"))
        assertFalse(reset.contains("AppPreferences"))
        assertFalse(reset.contains("delete"))
        assertFalse(reset.contains("clearAll"))
    }
}
