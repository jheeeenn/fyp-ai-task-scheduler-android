package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.accessibility.AccessibilityActivity
import com.example.myapplication.accessibility.AccessibleAssistantInputDialog
import com.example.myapplication.accessibility.AccessibilityStateHelper
import com.example.myapplication.accessibility.AssistantAccessibilityState
import com.example.myapplication.accessibility.resolveThemeColor
import com.example.myapplication.ai.conversation.ConversationAgentClient
import com.example.myapplication.ai.conversation.ConversationSettingTarget
import com.example.myapplication.preferences.AppPreferences
import com.example.myapplication.preferences.PreferenceChangeSource
import com.example.myapplication.voice.AssistantResponseManager
import com.example.myapplication.voice.AssistantVoiceHost
import com.example.myapplication.voice.AssistantVoiceSession
import com.example.myapplication.voice.ConversationAgentSettingsSelectionClient
import com.example.myapplication.voice.SettingsCardSelectionAuthority
import com.example.myapplication.voice.SettingsCardSelectionMove
import com.example.myapplication.voice.SettingsCardSelectionOrchestrator
import com.example.myapplication.voice.SettingsCardSelectionResolution
import com.example.myapplication.voice.VoiceSettingExecutionStatus
import com.example.myapplication.voice.VoiceSettingsExecutor
import kotlinx.coroutines.launch

class SettingsActivity : AccessibilityActivity(), AssistantVoiceHost {

    companion object {
        const val PREFS_NAME = AppPreferences.PREFS_NAME
        const val KEY_ASSISTANT_TONE = AppPreferences.KEY_ASSISTANT_TONE
        const val KEY_REPLY_LENGTH = AppPreferences.KEY_REPLY_LENGTH
        const val KEY_SPEECH_RATE = AppPreferences.KEY_SPEECH_RATE
        const val KEY_LARGE_TEXT = AppPreferences.KEY_LARGE_TEXT
        const val KEY_HIGH_CONTRAST = AppPreferences.KEY_HIGH_CONTRAST
        const val KEY_PROCESSING_HAPTIC_FEEDBACK = AppPreferences.KEY_PROCESSING_HAPTIC_FEEDBACK
        const val KEY_SESSION_END_HAPTIC_FEEDBACK = AppPreferences.KEY_SESSION_END_HAPTIC_FEEDBACK
        const val KEY_LM_STUDIO_ENDPOINT = AppPreferences.KEY_LM_STUDIO_ENDPOINT
        const val KEY_CONVERSATION_AGENT_ENDPOINT = AppPreferences.KEY_CONVERSATION_AGENT_ENDPOINT
        const val KEY_TASK_AGENT_ENDPOINT = AppPreferences.KEY_TASK_AGENT_ENDPOINT
        const val DEFAULT_LM_STUDIO_ENDPOINT = AppPreferences.DEFAULT_CONVERSATION_AGENT_ENDPOINT
        const val DEFAULT_SPEECH_RATE = AppPreferences.DEFAULT_SPEECH_RATE
        const val DEFAULT_CONVERSATION_AGENT_ENDPOINT = AppPreferences.DEFAULT_CONVERSATION_AGENT_ENDPOINT
        const val DEFAULT_TASK_AGENT_ENDPOINT = AppPreferences.DEFAULT_TASK_AGENT_ENDPOINT
        private const val EXTRA_RESTORE_SETTING_FOCUS = "restore_setting_focus"
        private const val MAX_SELECTION_RETRIES = 2
    }

    private lateinit var appPreferences: AppPreferences
    private lateinit var voiceHelper: VoiceHelper
    private lateinit var voiceSettingsExecutor: VoiceSettingsExecutor
    private lateinit var assistantSession: AssistantVoiceSession
    private lateinit var selectionOrchestrator: SettingsCardSelectionOrchestrator

    private lateinit var toneValue: TextView
    private lateinit var replyLengthValue: TextView
    private lateinit var speechSpeedValue: TextView
    private lateinit var largeTextValue: TextView
    private lateinit var highContrastValue: TextView
    private lateinit var processingHapticValue: TextView
    private lateinit var sessionEndHapticValue: TextView
    private lateinit var assistantButton: Button

    private var activeSelectionTarget: ConversationSettingTarget? = null
    private var selectionRetryCount = 0

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) assistantSession.onAudioPermissionGranted()
            else assistantSession.onAudioPermissionDenied()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        appPreferences = AppPreferences(this)
        voiceHelper = VoiceHelper(this)
        voiceSettingsExecutor = VoiceSettingsExecutor(appPreferences)
        bindViews()
        selectionOrchestrator = SettingsCardSelectionOrchestrator(
            ConversationAgentSettingsSelectionClient(ConversationAgentClient(this))
        )
        assistantSession = AssistantVoiceSession(
            activity = this,
            host = this,
            voiceHelper = voiceHelper,
            responseManager = AssistantResponseManager.fromPreferences(this),
            audioPermissionLauncher = audioPermissionLauncher,
            normalizeFinalTextForHost = false,
            onAccessibilityStateChanged = { state ->
                AccessibilityStateHelper.updateAssistantState(
                    assistantButton,
                    state,
                    announce = false
                )
            }
        )
        assistantSession.bindAssistantControl(assistantButton)
        renderCurrentValues()
        bindInteractions()
        restoreVisualSettingFocusIfRequested()
    }

    private fun bindViews() {
        listOf(
            R.id.tvSettingsTitle,
            R.id.tvAssistantPreferencesHeading,
            R.id.tvAccessibilityHeading
        ).forEach { AccessibilityStateHelper.markHeading(findViewById(it)) }

        toneValue = findViewById(R.id.tvToneValue)
        replyLengthValue = findViewById(R.id.tvReplyLengthValue)
        speechSpeedValue = findViewById(R.id.tvSpeechSpeedValue)
        largeTextValue = findViewById(R.id.tvLargeTextValue)
        highContrastValue = findViewById(R.id.tvHighContrastValue)
        processingHapticValue = findViewById(R.id.tvProcessingHapticValue)
        sessionEndHapticValue = findViewById(R.id.tvSessionEndHapticValue)
        assistantButton = findViewById(R.id.btnTalkAssistant)
        AccessibilityStateHelper.updateAssistantState(
            assistantButton,
            AssistantAccessibilityState.READY,
            announce = false
        )
    }

    private fun renderCurrentValues() {
        toneValue.text = appPreferences.assistantTone
        replyLengthValue.text = appPreferences.replyLength
        speechSpeedValue.text = appPreferences.speechRatePreset.displayName
        renderBooleanValue(largeTextValue, appPreferences.largeTextEnabled)
        renderBooleanValue(highContrastValue, appPreferences.highContrastEnabled)
        renderBooleanValue(processingHapticValue, appPreferences.processingHapticEnabled)
        renderBooleanValue(sessionEndHapticValue, appPreferences.sessionEndHapticEnabled)
        updateCardSemantics()
    }

    private fun bindInteractions() {
        bindSelectionCard(
            R.id.cardTone,
            ConversationSettingTarget.ASSISTANT_TONE,
            SettingsControlSpeechRenderer.assistantTone(appPreferences.assistantTone)
        )
        bindSelectionCard(
            R.id.cardReplyLength,
            ConversationSettingTarget.REPLY_LENGTH,
            SettingsControlSpeechRenderer.replyLength(appPreferences.replyLength)
        )
        bindSelectionCard(
            R.id.cardSpeechSpeed,
            ConversationSettingTarget.SPEECH_SPEED,
            SettingsControlSpeechRenderer.speechSpeed(appPreferences.speechRatePreset.displayName)
        )

        bindBooleanCard(
            cardId = R.id.cardLargeText,
            speech = { conciseState("Large Text", appPreferences.largeTextEnabled) },
            toggle = ::toggleLargeText
        )
        bindBooleanCard(
            cardId = R.id.cardHighContrast,
            speech = { conciseState("High Contrast", appPreferences.highContrastEnabled) },
            toggle = ::toggleHighContrast
        )
        bindBooleanCard(
            cardId = R.id.cardProcessingHaptic,
            speech = {
                conciseState("Processing Haptic", appPreferences.processingHapticEnabled)
            },
            toggle = ::toggleProcessingHaptic
        )
        bindBooleanCard(
            cardId = R.id.cardSessionEndHaptic,
            speech = {
                conciseState("Session End Haptic", appPreferences.sessionEndHapticEnabled)
            },
            toggle = ::toggleSessionEndHaptic
        )

        VoiceFirstGestureBinder.bindAction(
            view = findViewById<LinearLayout>(R.id.cardAdvancedSettings),
            speechProvider = { getString(R.string.advanced_settings) },
            speak = ::speakControlIdentification,
            activate = {
                startActivity(Intent(this, AdvancedSettingsActivity::class.java))
            }
        )
        VoiceFirstGestureBinder.bindAction(
            view = findViewById<Button>(R.id.btnGoHome),
            speechProvider = SettingsControlSpeechRenderer::home,
            speak = ::speakControlIdentification,
            activate = ::finish
        )
        VoiceFirstGestureBinder.bindAction(
            view = assistantButton,
            speechProvider = SettingsControlSpeechRenderer::assistant,
            speak = ::speakControlIdentification,
            activate = {
                startActivity(Intent(this, HomeActivity::class.java).apply {
                    putExtra("open_assistant_on_arrival", true)
                })
            }
        )
    }

    private fun bindSelectionCard(
        cardId: Int,
        target: ConversationSettingTarget,
        initialSpeech: String
    ) {
        val card = findViewById<LinearLayout>(cardId)
        VoiceFirstGestureBinder.bindAction(
            view = card,
            speechProvider = { selectionIdentification(target) ?: initialSpeech },
            speak = ::speakControlIdentification,
            activate = { startLocalSelection(target, card) }
        )
    }

    private fun bindBooleanCard(
        cardId: Int,
        speech: () -> String,
        toggle: () -> Unit
    ) {
        VoiceFirstGestureBinder.bindAction(
            view = findViewById<LinearLayout>(cardId),
            speechProvider = speech,
            speak = ::speakControlIdentification,
            activate = toggle
        )
    }

    private fun toggleLargeText() {
        val enabled = !appPreferences.largeTextEnabled
        appPreferences.setLargeTextEnabled(enabled, PreferenceChangeSource.TOUCH)
        renderCurrentValues()
        speakThenRecreate("Large Text is now ${onOff(enabled).lowercase()}.", R.id.cardLargeText)
    }

    private fun toggleHighContrast() {
        val enabled = !appPreferences.highContrastEnabled
        appPreferences.setHighContrastEnabled(enabled, PreferenceChangeSource.TOUCH)
        renderCurrentValues()
        speakThenRecreate(
            "High Contrast is now ${onOff(enabled).lowercase()}.",
            R.id.cardHighContrast
        )
    }

    private fun toggleProcessingHaptic() {
        val enabled = !appPreferences.processingHapticEnabled
        appPreferences.setProcessingHapticEnabled(enabled)
        renderCurrentValues()
        voiceHelper.speak("Processing Haptic is now ${onOff(enabled).lowercase()}.")
    }

    private fun toggleSessionEndHaptic() {
        val enabled = !appPreferences.sessionEndHapticEnabled
        appPreferences.setSessionEndHapticEnabled(enabled)
        renderCurrentValues()
        voiceHelper.speak("Session End Haptic is now ${onOff(enabled).lowercase()}.")
    }

    private fun speakThenRecreate(message: String, focusViewId: Int) {
        voiceHelper.speak(message) {
            runOnUiThread { recreateAfterVisualSettingChange(focusViewId) }
        }
    }

    private fun startLocalSelection(target: ConversationSettingTarget, card: View) {
        activeSelectionTarget = target
        selectionRetryCount = 0
        assistantSession.bindAssistantControl(card)
        assistantSession.startPassiveSession()
        assistantSession.speakThenListenAgain(selectionPrompt(target))
    }

    override fun onAssistantFinalText(text: String) {
        val target = activeSelectionTarget ?: return
        lifecycleScope.launch {
            val resolution = selectionOrchestrator.resolve(text, target)
            if (activeSelectionTarget != target) return@launch
            handleSelectionResolution(target, resolution)
        }
    }

    private fun handleSelectionResolution(
        target: ConversationSettingTarget,
        resolution: SettingsCardSelectionResolution
    ) {
        when (resolution.move) {
            SettingsCardSelectionMove.SELECT_VALUE -> {
                val validated = SettingsCardSelectionAuthority.validate(target, resolution.action)
                if (validated.move != SettingsCardSelectionMove.SELECT_VALUE) {
                    speakWrongTarget(target)
                    return
                }
                val result = voiceSettingsExecutor.execute(validated.action)
                if (result.status == VoiceSettingExecutionStatus.REJECTED) {
                    speakUnknown(target)
                    return
                }
                if (target == ConversationSettingTarget.SPEECH_SPEED) {
                    voiceHelper.applySpeechRate(appPreferences.speechRatePreset)
                }
                renderCurrentValues()
                clearSelectionContext()
                assistantSession.speakThenStop(result.speech)
            }
            SettingsCardSelectionMove.ASK_OPTIONS ->
                assistantSession.speakThenListenAgain(optionsPrompt(target))
            SettingsCardSelectionMove.ASK_CURRENT_VALUE ->
                assistantSession.speakThenListenAgain(currentValuePrompt(target))
            SettingsCardSelectionMove.CANCEL -> {
                clearSelectionContext()
                assistantSession.speakThenStop("Okay. I didn't change the setting.")
            }
            SettingsCardSelectionMove.WRONG_TARGET -> speakWrongTarget(target)
            SettingsCardSelectionMove.UNKNOWN -> speakUnknown(target)
        }
    }

    private fun speakWrongTarget(target: ConversationSettingTarget) {
        assistantSession.speakThenListenAgain(
            "You're changing ${label(target)}. ${optionsPrompt(target)}"
        )
    }

    private fun speakUnknown(target: ConversationSettingTarget) {
        selectionRetryCount += 1
        val message = "I didn't get a valid ${label(target)} value. ${optionsPrompt(target)}"
        if (selectionRetryCount <= MAX_SELECTION_RETRIES) {
            assistantSession.speakThenListenAgain(message)
        } else {
            clearSelectionContext()
            assistantSession.speakThenStop(
                "I couldn't get a valid ${label(target)} value, so I didn't change it."
            )
        }
    }

    private fun selectionPrompt(target: ConversationSettingTarget): String =
        "${label(target)} is currently ${currentValue(target)}. What would you like to set it to?"

    private fun optionsPrompt(target: ConversationSettingTarget): String {
        val options = SettingsCardSelectionAuthority.options(target)
        val spoken = options.dropLast(1).joinToString(", ") + ", or " + options.last()
        return "You can choose $spoken. Which would you like?"
    }

    private fun currentValuePrompt(target: ConversationSettingTarget): String =
        "${label(target)} is currently ${currentValue(target)}. What would you like to set it to?"

    private fun selectionIdentification(target: ConversationSettingTarget): String? =
        if (SettingsCardSelectionAuthority.options(target).isEmpty()) null
        else "${label(target)}, ${currentValue(target)}"

    private fun label(target: ConversationSettingTarget): String = when (target) {
        ConversationSettingTarget.ASSISTANT_TONE -> "Assistant Tone"
        ConversationSettingTarget.REPLY_LENGTH -> "Reply Length"
        ConversationSettingTarget.SPEECH_SPEED -> "Speech Speed"
        else -> "Setting"
    }

    private fun currentValue(target: ConversationSettingTarget): String = when (target) {
        ConversationSettingTarget.ASSISTANT_TONE -> appPreferences.assistantTone
        ConversationSettingTarget.REPLY_LENGTH -> appPreferences.replyLength
        ConversationSettingTarget.SPEECH_SPEED -> appPreferences.speechRatePreset.displayName
        else -> ""
    }

    private fun updateCardSemantics() {
        updateCardDescription(R.id.cardTone, "Assistant Tone", appPreferences.assistantTone)
        updateCardDescription(R.id.cardReplyLength, "Reply Length", appPreferences.replyLength)
        updateCardDescription(
            R.id.cardSpeechSpeed,
            "Speech Speed",
            appPreferences.speechRatePreset.displayName
        )
        updateCardDescription(R.id.cardLargeText, "Large Text", onOff(appPreferences.largeTextEnabled))
        updateCardDescription(
            R.id.cardHighContrast,
            "High Contrast",
            onOff(appPreferences.highContrastEnabled)
        )
        updateCardDescription(
            R.id.cardProcessingHaptic,
            "Processing Haptic",
            onOff(appPreferences.processingHapticEnabled)
        )
        updateCardDescription(
            R.id.cardSessionEndHaptic,
            "Session End Haptic",
            onOff(appPreferences.sessionEndHapticEnabled)
        )
        findViewById<View>(R.id.cardAdvancedSettings).contentDescription =
            getString(R.string.advanced_settings)
    }

    private fun updateCardDescription(cardId: Int, label: String, value: String) {
        findViewById<View>(cardId).contentDescription = "$label, $value"
    }

    private fun renderBooleanValue(view: TextView, enabled: Boolean) {
        view.text = onOff(enabled)
        view.setTextColor(
            resolveThemeColor(
                if (enabled) R.attr.appColorSettingsStateOn
                else R.attr.appColorSettingsStateOff
            )
        )
    }

    private fun conciseState(label: String, enabled: Boolean): String =
        "$label, ${onOff(enabled)}"

    private fun onOff(enabled: Boolean): String =
        getString(if (enabled) R.string.setting_on else R.string.setting_off)

    private fun speakControlIdentification(text: String) {
        voiceHelper.speak(text)
    }

    private fun recreateAfterVisualSettingChange(focusViewId: Int) {
        intent.putExtra(EXTRA_RESTORE_SETTING_FOCUS, focusViewId)
        recreate()
    }

    private fun restoreVisualSettingFocusIfRequested() {
        val focusViewId = intent.getIntExtra(EXTRA_RESTORE_SETTING_FOCUS, 0)
        if (focusViewId == 0) return
        intent.removeExtra(EXTRA_RESTORE_SETTING_FOCUS)
        val focusTarget = findViewById<View>(focusViewId)
        focusTarget.post {
            AccessibilityStateHelper.restoreAccessibilityFocus(focusTarget)
        }
    }

    private fun clearSelectionContext() {
        activeSelectionTarget = null
        selectionRetryCount = 0
        if (::assistantSession.isInitialized) {
            assistantSession.bindAssistantControl(assistantButton)
        }
    }

    override fun onAssistantCancelled() {
        clearSelectionContext()
    }

    override fun onAssistantSessionStopped() {
        clearSelectionContext()
    }

    override fun onAssistantTypedInputRequested() {
        if (activeSelectionTarget == null) return
        AccessibleAssistantInputDialog.show(
            activity = this,
            title = "Type setting value",
            message = "Typed and voice responses use the same bounded setting selection.",
            emptyError = "Please type a setting value",
            onCancel = assistantSession::onTypedInputCancelled
        ) { typedText ->
            assistantSession.submitTypedText(typedText, clearConversation = false)
        }
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            assistantSession.stopForLifecycle()
            clearSelectionContext()
        }
        super.onStop()
    }

    override fun onDestroy() {
        assistantSession.destroy()
        voiceHelper.shutdown()
        super.onDestroy()
    }
}
