package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsRedesignSourceContractTest {
    private val mainRoot = File("src/main/java/com/example/myapplication")
    private val layoutRoot = File("src/main/res/layout")
    private val settings = mainRoot.resolve("SettingsActivity.kt").readText()
    private val advanced = mainRoot.resolve("AdvancedSettingsActivity.kt").readText()
    private val layout = layoutRoot.resolve("activity_settings.xml").readText()
    private val advancedLayout = layoutRoot.resolve("activity_advanced_settings.xml").readText()

    @Test
    fun normalCardsShowOnlyNamesAndCurrentValuesWithoutDescriptionsOrSwitches() {
        listOf(
            "assistant_tone_description",
            "reply_length_description",
            "speech_speed_description",
            "large_text_description",
            "high_contrast_description",
            "processing_haptic_description",
            "session_end_haptic_description"
        ).forEach { description -> assertFalse(layout.contains(description)) }
        assertFalse(layout.contains("SettingsDescriptionText"))
        assertFalse(layout.contains("SwitchMaterial"))
        listOf(
            "tvToneValue",
            "tvReplyLengthValue",
            "tvSpeechSpeedValue",
            "tvLargeTextValue",
            "tvHighContrastValue",
            "tvProcessingHapticValue",
            "tvSessionEndHapticValue"
        ).forEach { id -> assertTrue(layout.contains("@+id/$id")) }
    }

    @Test
    fun booleanCardsUseVoiceFirstActionsAndOnlyTheirAuthoritativePreferenceWrites() {
        assertTrue(settings.contains("bindBooleanCard("))
        assertTrue(settings.contains("VoiceFirstGestureBinder.bindAction("))
        val expected = mapOf(
            "toggleLargeText" to "setLargeTextEnabled(enabled, PreferenceChangeSource.TOUCH)",
            "toggleHighContrast" to
                "setHighContrastEnabled(enabled, PreferenceChangeSource.TOUCH)",
            "toggleProcessingHaptic" to "setProcessingHapticEnabled(enabled)",
            "toggleSessionEndHaptic" to "setSessionEndHapticEnabled(enabled)"
        )
        expected.forEach { (function, write) ->
            val body = functionBody(function)
            assertTrue(body.contains(write))
            assertTrue(body.contains("renderCurrentValues()"))
            assertFalse(body.contains("setAssistantTone"))
            assertFalse(body.contains("setReplyLength"))
            assertFalse(body.contains("setSpeechRatePreset"))
        }
        assertTrue(settings.contains("recreateAfterVisualSettingChange(focusViewId)"))
        assertTrue(settings.contains("AccessibilityStateHelper.restoreAccessibilityFocus"))
        assertTrue(settings.contains("R.id.cardLargeText"))
        assertTrue(settings.contains("R.id.cardHighContrast"))
    }

    @Test
    fun multiValueCardsOwnFixedTargetsAndUseBoundedExecutor() {
        listOf("ASSISTANT_TONE", "REPLY_LENGTH", "SPEECH_SPEED").forEach { target ->
            assertTrue(settings.contains("ConversationSettingTarget.$target"))
        }
        assertTrue(settings.contains("activeSelectionTarget = target"))
        assertTrue(settings.contains("SettingsCardSelectionAuthority.validate(target, resolution.action)"))
        assertTrue(settings.contains("voiceSettingsExecutor.execute(validated.action)"))
        assertTrue(settings.contains("ConversationAgentSettingsSelectionClient(ConversationAgentClient(this))"))
        assertFalse(settings.contains("showOptionDialog"))
        assertFalse(settings.contains("RadioButton"))
    }

    @Test
    fun localSessionHasBoundedRetryCancelAndLifecycleCleanup() {
        assertTrue(settings.contains("MAX_SELECTION_RETRIES = 2"))
        assertTrue(settings.contains("SettingsCardSelectionMove.CANCEL"))
        assertTrue(settings.contains("clearSelectionContext()"))
        assertTrue(settings.contains("SettingsCardSelectionMove.UNKNOWN"))
        assertTrue(settings.contains("assistantSession.speakThenListenAgain"))
        assertTrue(settings.contains("assistantSession.speakThenStop"))
        assertTrue(settings.contains("assistantSession.stopForLifecycle()"))
        assertTrue(settings.contains("assistantSession.destroy()"))
        assertTrue(settings.contains("voiceHelper.shutdown()"))
        assertTrue(settings.contains("AccessibleAssistantInputDialog.show("))
        assertTrue(settings.contains("assistantSession.submitTypedText"))
    }

    @Test
    fun normalSettingsDockUsesApprovedSharedOneToTwoPattern() {
        val dock = layout.substringAfter("android:id=\"@+id/settingsBottomDock\"")
            .substringBefore("</LinearLayout>")
        val home = dock.substringAfter("android:id=\"@+id/btnGoHome\"").substringBefore("/>")
        val assistant = dock.substringAfter("android:id=\"@+id/btnTalkAssistant\"").substringBefore("/>")
        assertTrue(dock.contains("android:layout_height=\"108dp\""))
        assertTrue(dock.contains("android:orientation=\"horizontal\""))
        assertTrue(home.contains("android:layout_weight=\"1\""))
        assertTrue(assistant.contains("android:layout_weight=\"2\""))
        assertTrue(assistant.contains("android:layout_marginStart=\"14dp\""))
        assertTrue(assistant.contains("@drawable/bg_task_list_assistant_action"))
    }

    @Test
    fun endpointsExistOnlyOnDeclaredAdvancedScreenAndRemainManual() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse(layout.contains("cardConversationAgentEndpoint"))
        assertFalse(layout.contains("cardTaskAgentEndpoint"))
        assertTrue(advancedLayout.contains("@+id/cardConversationAgentEndpoint"))
        assertTrue(advancedLayout.contains("@+id/cardTaskAgentEndpoint"))
        assertTrue(manifest.contains("android:name=\".AdvancedSettingsActivity\""))
        assertTrue(advanced.contains("showEndpointDialog("))
        assertTrue(advanced.contains("normalizeLmStudioEndpoint("))
        assertTrue(advanced.contains("setConversationAgentEndpoint(endpoint)"))
        assertTrue(advanced.contains("setTaskAgentEndpoint(endpoint)"))
        assertTrue(advanced.contains("R.string.reset_to_default"))
        assertTrue(advanced.contains("R.string.endpoint_blank_error"))
        assertFalse(advanced.contains("ConversationAgentClient"))
        assertFalse(advanced.contains("VoiceSettingsExecutor"))
    }

    @Test
    fun settingsSurfacesAreOpaqueAndHighContrastMapped() {
        val colors = File("src/main/res/values/colors.xml").readText()
        val themes = File("src/main/res/values/themes.xml").readText()
        listOf("bg_settings_card.xml", "bg_settings_advanced_card.xml").forEach { name ->
            val drawable = File("src/main/res/drawable/$name").readText()
            assertTrue(drawable.contains("<solid"))
            assertFalse(drawable.contains("alpha"))
            assertFalse(drawable.contains("<gradient"))
        }
        listOf(
            "settings_card_surface",
            "settings_advanced_surface",
            "settings_outline",
            "settings_state_on",
            "settings_state_off",
            "hc_settings_card_surface",
            "hc_settings_outline",
            "hc_settings_state_on"
        ).forEach { name -> assertTrue(colors.contains("name=\"$name\">#FF")) }
        assertTrue(themes.contains("appColorSettingsCardSurface"))
        assertTrue(themes.contains("@color/hc_settings_card_surface"))
    }

    private fun functionBody(name: String): String =
        settings.substringAfter("private fun $name")
            .substringBefore("\n    private fun ")
}
