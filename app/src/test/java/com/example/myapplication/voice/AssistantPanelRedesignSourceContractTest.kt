package com.example.myapplication.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssistantPanelRedesignSourceContractTest {
    private val main = File("src/main")
    private val sourceRoot = main.resolve("java/com/example/myapplication")
    private val layout = main.resolve("res/layout/bottomsheet_assistant.xml").readText()
    private val panel = sourceRoot.resolve("AssistantBottomSheet.kt").readText()
    private val session = sourceRoot.resolve("voice/AssistantVoiceSession.kt").readText()

    @Test
    fun panelContainsOnlyAdaptiveStateTranscriptAndReplyPresentation() {
        val root = layout.substringAfter("android:id=\"@+id/assistantRoot\"")
            .substringBefore("android:id=\"@+id/stateContainer\"")

        assertTrue(layout.contains("<androidx.core.widget.NestedScrollView"))
        assertTrue(root.contains("android:layout_height=\"wrap_content\""))
        assertFalse(root.contains("android:minHeight=\"420dp\""))
        assertTrue(layout.contains("@+id/assistantStateIndicator"))
        assertTrue(layout.contains("@+id/tvAssistantState"))
        assertTrue(layout.contains("@+id/tvUserSpeech"))
        assertTrue(layout.contains("@+id/tvAssistantReply"))
        assertTrue(layout.contains("@+id/userTranscriptSection"))
        assertTrue(layout.contains("@+id/assistantReplySection"))
        assertEquals(2, layout.countOccurrences("android:visibility=\"gone\""))

        listOf("tvHintLabel", "tvAssistantHint", "btnStopAssistant", "btnTypeAssistantInput")
            .forEach { obsoleteId -> assertFalse(layout.contains(obsoleteId)) }
    }

    @Test
    fun blankConversationSectionsCollapseAndPopulatedSectionsBecomeVisible() {
        val user = panel.substringAfter("fun showUserSpeech")
            .substringBefore("fun showAssistantReply")
        val reply = panel.substringAfter("fun showAssistantReply")
            .substringBefore("fun clearConversation")
        val clear = panel.substringAfter("fun clearConversation")
            .substringBefore("fun performProcessingHapticPulse")

        listOf(user, reply).forEach { block ->
            assertTrue(block.contains("text.isNotBlank()"))
            assertTrue(block.contains("View.VISIBLE else View.GONE"))
        }
        assertTrue(clear.contains("showUserSpeech(\"\")"))
        assertTrue(clear.contains("showAssistantReply(\"\")"))
    }

    @Test
    fun oneQuietSharedRootGestureRemainsTheOnlyPanelTouchBinding() {
        assertEquals(1, panel.countOccurrences("VoiceFirstGestureBinder.bindAction"))
        assertTrue(panel.contains("view = assistantRoot"))
        assertTrue(panel.contains("speechProvider = { null }"))
        assertTrue(panel.contains("speak = { _ -> }"))
        assertTrue(panel.contains("activate = { onDoubleTapCancel?.invoke() }"))
        assertFalse(panel.contains("System.currentTimeMillis"))
        assertFalse(panel.contains("lastTapTime"))
        assertFalse(panel.contains("doubleTapWindowMs"))
    }

    @Test
    fun stateUsesExplicitWordingAndAStaticSemanticIndicatorOnly() {
        val mapping = panel.substringAfter("private fun indicatorColor")

        listOf(
            "appColorAssistantStateNeutral",
            "appColorAssistantStateListening",
            "appColorAssistantStateProcessing",
            "appColorAssistantStateWaiting",
            "appColorAssistantStateSpeaking",
            "appColorAssistantStateError"
        ).forEach { color -> assertTrue(mapping.contains(color)) }
        assertTrue(panel.contains("tvState.text = state.label"))
        assertTrue(panel.contains("AccessibilityStateHelper.updateAssistantState"))
        assertTrue(panel.contains("AccessibilityAnnouncementHelper.logState"))
        assertTrue(panel.contains("onStateChanged(state)"))
        assertFalse(panel.contains("ValueAnimator"))
        assertFalse(panel.contains("stateContainer.setBackground"))
        assertFalse(panel.contains("assistantRoot.setBackground"))
    }

    @Test
    fun panelAndEveryStateResourceAreOpaqueInNormalAndHighContrastThemes() {
        val panelDrawable = main.resolve("res/drawable/bg_assistant_panel.xml").readText()
        val indicatorDrawable = main.resolve("res/drawable/bg_assistant_state_indicator.xml").readText()
        val colors = main.resolve("res/values/colors.xml").readText()

        assertTrue(panelDrawable.contains("<solid"))
        assertTrue(indicatorDrawable.contains("<solid"))
        assertFalse(panelDrawable.contains("android:alpha"))
        assertFalse(indicatorDrawable.contains("android:alpha"))
        listOf(
            "assistant_state_neutral",
            "assistant_state_listening",
            "assistant_state_processing",
            "assistant_state_waiting",
            "assistant_state_speaking",
            "assistant_state_error",
            "hc_assistant_state_neutral",
            "hc_assistant_state_listening",
            "hc_assistant_state_processing",
            "hc_assistant_state_waiting",
            "hc_assistant_state_speaking",
            "hc_assistant_state_error"
        ).forEach { name ->
            assertTrue(Regex("name=\"$name\">#FF[0-9A-Fa-f]{6}<").containsMatchIn(colors))
        }
    }

    @Test
    fun continuationPromptHelperStillSpeaksEveryPrompt() {
        val prompts = sourceRoot.resolve("voice/AssistantPromptHelper.kt").readText()
        listOf(
            "askTitle",
            "askDate",
            "askTime",
            "askWhatToChange",
            "askSaveTask",
            "askSaveChanges",
            "ambiguity",
            "retryAmbiguity",
            "resetAmbiguity",
            "speakInfo"
        ).forEach { function ->
            val block = prompts.substringAfter("fun $function")
                .substringBefore("\n    fun ")
            assertTrue("$function must retain spoken delivery", block.contains("session.speak("))
        }
        assertFalse(prompts.contains("showAssistantHint"))
    }

    @Test
    fun externalTypedFallbackSettingsSelectionAndSessionSafetyRemain() {
        listOf("HomeActivity.kt", "CreateTaskActivity.kt", "EditTaskActivity.kt")
            .forEach { fileName ->
                val activity = sourceRoot.resolve(fileName).readText()
                assertTrue(activity.contains("btnTalkAssistant.setOnLongClickListener"))
                assertTrue(activity.contains("showTypedAssistantInputDialog()"))
                assertTrue(activity.contains("assistantSession.submitTypedText"))
            }

        val settings = sourceRoot.resolve("SettingsActivity.kt").readText()
        assertTrue(settings.contains("activeSelectionTarget"))
        assertTrue(settings.contains("ConversationAgentSettingsSelectionClient"))
        assertTrue(settings.contains("VoiceSettingsExecutor"))

        listOf(
            "stopListeningBeforeSpeak",
            "pauseListeningForAssistantSpeech",
            "postRecognitionRestart",
            "cancelRecognitionIfActive",
            "sessionGeneration",
            "callbackGeneration",
            "isForceStopping",
            "terminalDeliveryActive",
            "recognitionRequestActive",
            "waitingForConfirmation",
            "retryCount",
            "forceStop",
            "speakThenStop",
            "speakThenRun",
            "endConversation",
            "handleListenFailure"
        ).forEach { safeguard -> assertTrue("Missing $safeguard", session.contains(safeguard)) }
    }

    private fun String.countOccurrences(value: String): Int =
        windowed(value.length).count { it == value }
}
