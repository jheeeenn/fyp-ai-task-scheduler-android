package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeveloperTestHarnessSourceContractTest {
    private val mainRoot = File("src/main")
    private val sourceRoot = mainRoot.resolve("java/com/example/myapplication")
    private val developer = source("DeveloperTestActivity.kt")
    private val developerSession = source("DeveloperTestSession.kt")
    private val overlay = source("DeveloperAssistantOverlay.kt")
    private val home = source("HomeActivity.kt")
    private val create = source("CreateTaskActivity.kt")
    private val edit = source("EditTaskActivity.kt")
    private val detail = source("TaskDetailActivity.kt")
    private val settings = source("SettingsActivity.kt")
    private val voiceSession = source("voice/AssistantVoiceSession.kt")
    private val voiceHelper = source("VoiceHelper.kt")
    private val advanced = source("AdvancedSettingsActivity.kt")
    private val advancedLayout = mainRoot.resolve("res/layout/activity_advanced_settings.xml").readText()
    private val developerLayout = mainRoot.resolve("res/layout/dialog_developer_test.xml").readText()
    private val assistantBubble =
        mainRoot.resolve("res/drawable/bg_developer_assistant_message.xml").readText()
    private val colors = mainRoot.resolve("res/values/colors.xml").readText()
    private val themes = mainRoot.resolve("res/values/themes.xml").readText()
    private val manifest = mainRoot.resolve("AndroidManifest.xml").readText()

    @Test
    fun developerEntryIsExplicitInternalAndStillInheritsHomeProductionLogic() {
        val activation = advanced.indexOf("DeveloperTestSession.activate()")
        val launch = advanced.indexOf("DeveloperTestActivity::class.java")
        val declaration = manifest.substringAfter("android:name=\".DeveloperTestActivity\"")
            .substringBefore("/>")

        assertTrue(advancedLayout.contains("@+id/cardDeveloperTesting"))
        assertTrue(activation >= 0 && activation < launch)
        assertTrue(developer.contains("class DeveloperTestActivity : HomeActivity()"))
        assertTrue(declaration.contains("android:exported=\"false\""))
        assertTrue(declaration.contains("android:screenOrientation=\"portrait\""))
        assertTrue(developer.contains("if (!DeveloperTestSession.isActive) finish()"))
        assertTrue(developerSession.contains("var isActive: Boolean = false"))
        assertFalse(developerSession.contains("SharedPreferences"))
    }

    @Test
    fun developerInfrastructureContainsNoBusinessOrAiPipeline() {
        val infrastructure = listOf(developer, developerSession, overlay).joinToString("\n")
        listOf(
            "ConversationOrchestrator(",
            "AgentOrchestrator(",
            "LaptopAgentClient(",
            "ConversationAgentClient(",
            "AppDatabase",
            "RoutineDraftController(",
            "BreakdownDraftController(",
            "TaskMatcher",
            "TaskEntity"
        ).forEach { forbidden ->
            assertFalse("Developer infrastructure must not contain $forbidden", infrastructure.contains(forbidden))
        }
    }

    @Test
    fun homeDeveloperInputStillUsesTheExistingAssistantHostPath() {
        val hook = home.substringAfter("protected fun submitPersistentTypedAssistantText")
            .substringBefore("override fun onResume")
        val host = home.substringAfter("override fun onAssistantFinalText(text: String)")
            .substringBefore("override fun onAssistantCancelled")
        val typed = voiceSession.substringAfter("fun submitTypedText")
            .substringBefore("fun onTypedInputCancelled")

        assertTrue(home.contains("onSubmit = ::submitPersistentTypedAssistantText"))
        assertTrue(hook.contains("assistantSession.submitTypedText(text, clearConversation = false)"))
        assertTrue(typed.contains("host.onAssistantFinalText("))
        assertTrue(host.contains("handleVoiceCommand(text)"))
    }

    @Test
    fun normalVoiceIsTheFailClosedDefaultAndRecognitionIsGuarded() {
        assertTrue(
            voiceSession.contains(
                "private val interactionMode: AssistantInteractionMode =\n        AssistantInteractionMode.NORMAL_VOICE"
            )
        )
        assertTrue(voiceSession.contains("private val shouldSpeakAudio: () -> Boolean = { true }"))
        assertTrue(developerSession.contains("AssistantInteractionMode.NORMAL_VOICE"))
        val initialization = voiceSession.substringAfter("fun ensureInitialized()")
            .substringBefore("fun startPassiveSession")
        val startVoiceFlow = voiceSession.substringAfter("fun startVoiceFlow()")
            .substringBefore("fun onAudioPermissionGranted")
        val recognition = voiceSession.substringAfter("fun startVoiceRecognition()")
            .substringBefore("fun speak(text: String")
        val restart = voiceSession.substringAfter("private fun postRecognitionRestart(")
            .substringBefore("private fun cancelRecognitionIfActive")

        listOf(initialization, startVoiceFlow, recognition, restart).forEach { body ->
            assertTrue(body.contains("if (!usesNormalVoiceInteraction) return"))
        }
        assertTrue(initialization.indexOf("if (!usesNormalVoiceInteraction) return") <
            initialization.indexOf("SpeechRecognizer.createSpeechRecognizer"))
    }

    @Test
    fun createTaskUsesSharedDeveloperTransportAndItsRealConfirmationHandler() {
        assertDeveloperAwareHost(create)
        val submit = create.substringAfter("DeveloperAssistantOverlay.attach(")
            .substringBefore(")\n        )")
        val host = create.substringAfter("override fun onAssistantFinalText(text: String)")
            .substringBefore("override fun onAssistantCancelled")

        assertTrue(submit.contains("assistantSession.submitTypedText(text, clearConversation = false)"))
        assertTrue(host.contains("handleVoiceCommand(text)"))
        assertTrue(create.contains("assistantSession.expectConfirmation()"))
        assertTrue(create.contains("promptHelper.askSaveTask(buildTaskSummary())"))
    }

    @Test
    fun everyCurrentAssistantHostPropagatesTheDeveloperTransport() {
        mapOf(
            "HomeActivity" to home,
            "CreateTaskActivity" to create,
            "EditTaskActivity" to edit,
            "TaskDetailActivity" to detail,
            "SettingsActivity" to settings
        ).forEach { (name, activity) ->
            assertTrue("$name must use process-local developer mode", activity.contains("DeveloperTestSession.interactionMode()"))
            assertTrue("$name must use shared TTS state", activity.contains("DeveloperTestSession::shouldSpeakAudio") || activity.contains("DeveloperTestSession.shouldSpeakAudio()"))
            assertTrue("$name must record the shared transcript", activity.contains("DeveloperTestSession::recordTranscript") || activity.contains("DeveloperTestSession.recordTranscript(event)"))
            assertTrue("$name must present the reusable overlay", activity.contains("DeveloperAssistantOverlay.attach("))
        }
    }

    @Test
    fun sharedTranscriptSurvivesHomeToCreateTaskActivityTransition() {
        assertTrue(developerSession.contains("private val transcript = mutableListOf<AssistantTranscriptEvent>()"))
        assertTrue(developerSession.contains("transcript += event"))
        assertTrue(developerSession.contains("fun transcriptSnapshot(): List<AssistantTranscriptEvent> = transcript.toList()"))
        assertTrue(home.contains("DeveloperTestSession.recordTranscript(event)"))
        assertTrue(create.contains("transcriptObserver = DeveloperTestSession::recordTranscript"))
        assertTrue(overlay.contains("DeveloperTestSession.transcriptSnapshot()"))
        assertFalse(create.contains("DeveloperTestSession.reset()"))
    }

    @Test
    fun developerTtsIsSharedSilentByDefaultAndNeverRestartsRecognition() {
        assertTrue(developerSession.contains("var ttsEnabled: Boolean = false"))
        assertTrue(developerSession.contains("fun shouldSpeakAudio(): Boolean = !isActive || ttsEnabled"))
        assertTrue(overlay.contains("DeveloperTestSession.setTtsEnabled(enabled)"))
        assertTrue(developerLayout.contains("android:checked=\"false\""))
        assertTrue(voiceSession.contains("voiceHelper.setShouldSpeakAudio(shouldSpeakAudio)"))
        assertTrue(voiceHelper.contains("if (!shouldSpeakAudio())"))
        assertTrue(voiceHelper.contains("onFinished(true)"))
        val speak = voiceSession.substringAfter("fun speak(text: String")
            .substringBefore("fun speakThenStop")
        assertTrue(speak.contains("usesNormalVoiceInteraction &&"))
        assertTrue(speak.contains("postRecognitionRestart(350L)"))
    }

    @Test
    fun assistantBubbleUsesDarkThemeTextForItsLightThemeSurface() {
        assertTrue(assistantBubble.contains("?attr/appColorSurface"))
        assertTrue(overlay.contains("R.attr.appColorTextPrimaryLight"))
        assertFalse(overlay.contains("R.attr.appColorTextPrimaryDark"))
        assertTrue(themes.contains("<item name=\"appColorSurface\">@color/hc_surface</item>"))
        assertTrue(themes.contains("<item name=\"appColorTextPrimaryLight\">@color/hc_text_primary_light</item>"))
        assertTrue(colors.contains("<color name=\"hc_surface\">#FFFFFF</color>"))
        assertTrue(colors.contains("<color name=\"hc_text_primary_light\">#050A14</color>"))
    }

    @Test
    fun resetClearsProcessStateAndRecreatesTheRealHomeHostWithoutPersistentWrites() {
        val reset = developerSession.substringAfter("fun reset()")
            .substringBefore("fun interactionMode")
        val restart = overlay.substringAfter("private fun restartDeveloperTesting()")
            .substringBefore("private fun exitDeveloperTesting()")
        val infrastructure = developerSession + overlay

        assertTrue(reset.contains("transcript.clear()"))
        assertTrue(reset.contains("ttsEnabled = false"))
        assertTrue(restart.contains("DeveloperTestSession.reset()"))
        assertTrue(restart.contains("DeveloperTestActivity::class.java"))
        assertTrue(restart.contains("Intent.FLAG_ACTIVITY_CLEAR_TOP"))
        assertFalse(infrastructure.contains("AppDatabase"))
        assertFalse(infrastructure.contains("AppPreferences"))
        assertFalse(infrastructure.contains("deleteTask"))
        assertFalse(infrastructure.contains("clearAllTables"))
    }

    private fun assertDeveloperAwareHost(activity: String) {
        assertTrue(activity.contains("interactionMode = DeveloperTestSession.interactionMode()"))
        assertTrue(activity.contains("shouldSpeakAudio = DeveloperTestSession::shouldSpeakAudio"))
        assertTrue(activity.contains("transcriptObserver = DeveloperTestSession::recordTranscript"))
        assertTrue(activity.contains("DeveloperAssistantOverlay.attach("))
    }

    private fun source(path: String): String = sourceRoot.resolve(path).readText()
}
