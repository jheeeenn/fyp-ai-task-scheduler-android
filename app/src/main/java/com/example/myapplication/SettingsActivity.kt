package com.example.myapplication

import android.app.AlertDialog
import android.app.Dialog
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.example.myapplication.accessibility.AccessibilityActivity
import com.example.myapplication.accessibility.AccessibilityStateHelper
import com.example.myapplication.accessibility.AssistantAccessibilityState
import com.example.myapplication.accessibility.resolveThemeColor
import com.example.myapplication.preferences.AppPreferences
import com.example.myapplication.preferences.PreferenceChangeSource
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AccessibilityActivity() {

    companion object {
        const val PREFS_NAME = AppPreferences.PREFS_NAME
        const val KEY_ASSISTANT_TONE = AppPreferences.KEY_ASSISTANT_TONE
        const val KEY_REPLY_LENGTH = AppPreferences.KEY_REPLY_LENGTH
        const val KEY_LARGE_TEXT = AppPreferences.KEY_LARGE_TEXT
        const val KEY_HIGH_CONTRAST = AppPreferences.KEY_HIGH_CONTRAST
        const val KEY_PROCESSING_HAPTIC_FEEDBACK = AppPreferences.KEY_PROCESSING_HAPTIC_FEEDBACK
        const val KEY_SESSION_END_HAPTIC_FEEDBACK = AppPreferences.KEY_SESSION_END_HAPTIC_FEEDBACK
        const val KEY_LM_STUDIO_ENDPOINT = AppPreferences.KEY_LM_STUDIO_ENDPOINT
        const val KEY_CONVERSATION_AGENT_ENDPOINT = AppPreferences.KEY_CONVERSATION_AGENT_ENDPOINT
        const val KEY_TASK_AGENT_ENDPOINT = AppPreferences.KEY_TASK_AGENT_ENDPOINT
        const val DEFAULT_LM_STUDIO_ENDPOINT = AppPreferences.DEFAULT_CONVERSATION_AGENT_ENDPOINT
        const val DEFAULT_CONVERSATION_AGENT_ENDPOINT = AppPreferences.DEFAULT_CONVERSATION_AGENT_ENDPOINT
        const val DEFAULT_TASK_AGENT_ENDPOINT = AppPreferences.DEFAULT_TASK_AGENT_ENDPOINT
        private const val EXTRA_RESTORE_SETTING_FOCUS = "restore_setting_focus"
    }

    private lateinit var appPreferences: AppPreferences
    private lateinit var tvToneValue: TextView
    private lateinit var tvReplyLengthValue: TextView
    private lateinit var tvConversationAgentEndpointValue: TextView
    private lateinit var tvTaskAgentEndpointValue: TextView

    private lateinit var switchLargeText: SwitchMaterial
    private lateinit var switchHighContrast: SwitchMaterial
    private lateinit var switchProcessingHaptic: SwitchMaterial
    private lateinit var switchSessionEndHaptic: SwitchMaterial
    private lateinit var voiceHelper: VoiceHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        appPreferences = AppPreferences(this)
        voiceHelper = VoiceHelper(this)
        bindHeadings()
        bindViews()
        renderCurrentValues()
        bindInteractions()
        restoreVisualSettingFocusIfRequested()
    }

    private fun bindHeadings() {
        listOf(
            R.id.tvSettingsTitle,
            R.id.tvAssistantPreferencesHeading,
            R.id.tvAccessibilityHeading,
            R.id.tvDeveloperHeading
        ).forEach { AccessibilityStateHelper.markHeading(findViewById(it)) }
    }

    private fun bindViews() {
        tvToneValue = findViewById(R.id.tvToneValue)
        tvReplyLengthValue = findViewById(R.id.tvReplyLengthValue)
        tvConversationAgentEndpointValue = findViewById(R.id.tvConversationAgentEndpointValue)
        tvTaskAgentEndpointValue = findViewById(R.id.tvTaskAgentEndpointValue)
        switchLargeText = findViewById(R.id.switchLargeText)
        switchHighContrast = findViewById(R.id.switchHighContrast)
        switchProcessingHaptic = findViewById(R.id.switchProcessingHaptic)
        switchSessionEndHaptic = findViewById(R.id.switchSessionEndHaptic)

        AccessibilityStateHelper.updateAssistantState(
            findViewById(R.id.btnTalkAssistant),
            AssistantAccessibilityState.READY,
            announce = false
        )
    }

    private fun renderCurrentValues() {
        tvToneValue.text = appPreferences.assistantTone
        tvReplyLengthValue.text = appPreferences.replyLength
        tvConversationAgentEndpointValue.text = appPreferences.conversationAgentEndpoint
        tvTaskAgentEndpointValue.text = appPreferences.taskAgentEndpoint

        switchLargeText.isChecked = appPreferences.largeTextEnabled
        switchHighContrast.isChecked = appPreferences.highContrastEnabled
        switchProcessingHaptic.isChecked = appPreferences.processingHapticEnabled
        switchSessionEndHaptic.isChecked = appPreferences.sessionEndHapticEnabled

        updateSwitchSemantics(switchLargeText, R.string.large_text_description)
        updateSwitchSemantics(switchHighContrast, R.string.high_contrast_description)
        updateSwitchSemantics(switchProcessingHaptic, R.string.processing_haptic_description)
        updateSwitchSemantics(switchSessionEndHaptic, R.string.session_end_haptic_description)
        updateMultiOptionSemantics()
        updateEndpointSemantics()
    }

    private fun bindInteractions() {
        VoiceFirstGestureBinder.bindAction(
            view = findViewById<LinearLayout>(R.id.cardTone),
            speechProvider = {
                SettingsControlSpeechRenderer.assistantTone(appPreferences.assistantTone)
            },
            speak = ::speakControlIdentification,
            activate = {
                showOptionDialog(
                    title = getString(R.string.assistant_tone),
                    options = listOf("Friendly", "Neutral", "Professional"),
                    currentValue = appPreferences.assistantTone
                ) { selected ->
                    appPreferences.setAssistantTone(selected)
                    tvToneValue.text = selected
                    updateMultiOptionSemantics()
                }
            }
        )

        VoiceFirstGestureBinder.bindAction(
            view = findViewById<LinearLayout>(R.id.cardReplyLength),
            speechProvider = {
                SettingsControlSpeechRenderer.replyLength(appPreferences.replyLength)
            },
            speak = ::speakControlIdentification,
            activate = {
                showOptionDialog(
                    title = getString(R.string.reply_length),
                    options = listOf("Short", "Normal", "Detailed"),
                    currentValue = appPreferences.replyLength
                ) { selected ->
                    appPreferences.setReplyLength(selected)
                    tvReplyLengthValue.text = selected
                    updateMultiOptionSemantics()
                }
            }
        )

        switchLargeText.setOnCheckedChangeListener { button, enabled ->
            button.performTapHapticFeedback()
            appPreferences.setLargeTextEnabled(enabled, PreferenceChangeSource.TOUCH)
            updateSwitchSemantics(switchLargeText, R.string.large_text_description)
            recreateAfterVisualSettingChange(button.id)
        }

        switchHighContrast.setOnCheckedChangeListener { button, enabled ->
            button.performTapHapticFeedback()
            appPreferences.setHighContrastEnabled(enabled, PreferenceChangeSource.TOUCH)
            updateSwitchSemantics(switchHighContrast, R.string.high_contrast_description)
            recreateAfterVisualSettingChange(button.id)
        }

        switchProcessingHaptic.setOnCheckedChangeListener { button, enabled ->
            button.performTapHapticFeedback()
            appPreferences.setProcessingHapticEnabled(enabled)
            updateSwitchSemantics(switchProcessingHaptic, R.string.processing_haptic_description)
        }

        switchSessionEndHaptic.setOnCheckedChangeListener { button, enabled ->
            button.performTapHapticFeedback()
            appPreferences.setSessionEndHapticEnabled(enabled)
            updateSwitchSemantics(switchSessionEndHaptic, R.string.session_end_haptic_description)
        }

        VoiceFirstGestureBinder.bindAction(
            view = findViewById<LinearLayout>(R.id.cardConversationAgentEndpoint),
            speechProvider = {
                SettingsControlSpeechRenderer.conversationEndpoint(
                    appPreferences.conversationAgentEndpoint
                )
            },
            speak = ::speakControlIdentification,
            activate = {
                showEndpointDialog(
                    title = getString(R.string.conversation_agent_endpoint),
                    currentValue = appPreferences.conversationAgentEndpoint,
                    defaultValue = DEFAULT_CONVERSATION_AGENT_ENDPOINT
                ) { endpoint ->
                    appPreferences.setConversationAgentEndpoint(endpoint)
                    tvConversationAgentEndpointValue.text = endpoint
                    updateEndpointSemantics()
                }
            }
        )

        VoiceFirstGestureBinder.bindAction(
            view = findViewById<LinearLayout>(R.id.cardTaskAgentEndpoint),
            speechProvider = {
                SettingsControlSpeechRenderer.taskEndpoint(appPreferences.taskAgentEndpoint)
            },
            speak = ::speakControlIdentification,
            activate = {
                showEndpointDialog(
                    title = getString(R.string.task_agent_endpoint),
                    currentValue = appPreferences.taskAgentEndpoint,
                    defaultValue = DEFAULT_TASK_AGENT_ENDPOINT
                ) { endpoint ->
                    appPreferences.setTaskAgentEndpoint(endpoint)
                    tvTaskAgentEndpointValue.text = endpoint
                    updateEndpointSemantics()
                }
            }
        )

        VoiceFirstGestureBinder.bindAction(
            view = findViewById<Button>(R.id.btnGoHome),
            speechProvider = SettingsControlSpeechRenderer::home,
            speak = ::speakControlIdentification,
            activate = ::finish
        )
        VoiceFirstGestureBinder.bindAction(
            view = findViewById<Button>(R.id.btnTalkAssistant),
            speechProvider = SettingsControlSpeechRenderer::assistant,
            speak = ::speakControlIdentification,
            activate = {
                startActivity(Intent(this, HomeActivity::class.java).apply {
                    putExtra("open_assistant_on_arrival", true)
                })
            }
        )
    }

    private fun speakControlIdentification(text: String) {
        voiceHelper.speak(text)
    }

    private fun updateSwitchSemantics(switch: SwitchMaterial, descriptionRes: Int) {
        switch.contentDescription = "${switch.text}. ${getString(descriptionRes)}"
        AccessibilityStateHelper.updateStateDescription(
            switch,
            getString(if (switch.isChecked) R.string.setting_on else R.string.setting_off)
        )
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

    private fun updateMultiOptionSemantics() {
        findViewById<LinearLayout>(R.id.cardTone).contentDescription =
            "${getString(R.string.assistant_tone)}, ${appPreferences.assistantTone}. " +
                getString(R.string.assistant_tone_description)
        findViewById<LinearLayout>(R.id.cardReplyLength).contentDescription =
            "${getString(R.string.reply_length)}, ${appPreferences.replyLength}. " +
                getString(R.string.reply_length_description)
    }

    private fun updateEndpointSemantics() {
        findViewById<LinearLayout>(R.id.cardConversationAgentEndpoint).contentDescription =
            "${getString(R.string.conversation_agent_endpoint)}, ${appPreferences.conversationAgentEndpoint}"
        findViewById<LinearLayout>(R.id.cardTaskAgentEndpoint).contentDescription =
            "${getString(R.string.task_agent_endpoint)}, ${appPreferences.taskAgentEndpoint}"
    }

    private fun showEndpointDialog(
        title: String,
        currentValue: String,
        defaultValue: String,
        onEndpointSelected: (String) -> Unit
    ) {
        val endpointInput = EditText(this).apply {
            setText(currentValue)
            setSelectAllOnFocus(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(endpointInput)
            .setPositiveButton(R.string.save_changes, null)
            .setNegativeButton(R.string.reset_to_default) { _, _ ->
                onEndpointSelected(defaultValue)
            }
            .setNeutralButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val normalizedEndpoint = normalizeLmStudioEndpoint(endpointInput.text.toString())
                if (normalizedEndpoint == null) {
                    Toast.makeText(this, R.string.endpoint_blank_error, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                onEndpointSelected(normalizedEndpoint)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun normalizeLmStudioEndpoint(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.endsWith("/v1/chat/completions")) return trimmed

        val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val withoutTrailingSlash = withScheme.trimEnd('/')
        val schemeEnd = withoutTrailingSlash.indexOf("://") + 3
        val hasPath = withoutTrailingSlash.substring(schemeEnd).contains("/")
        val baseEndpoint = if (
            !hasPath && !withoutTrailingSlash.substring(schemeEnd).contains(":")
        ) {
            "$withoutTrailingSlash:1234"
        } else {
            withoutTrailingSlash
        }
        return "$baseEndpoint/v1/chat/completions"
    }

    @SuppressLint("InflateParams")
    private fun showOptionDialog(
        title: String,
        options: List<String>,
        currentValue: String,
        onSelected: (String) -> Unit
    ) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_setting_options, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvDialogTitle).apply {
            text = title
            AccessibilityStateHelper.markHeading(this)
        }
        val radioGroup = view.findViewById<RadioGroup>(R.id.radioGroupOptions)
        options.forEach { option ->
            radioGroup.addView(RadioButton(this).apply {
                text = option
                textSize = 20f
                setTextColor(this@SettingsActivity.resolveThemeColor(R.attr.appColorTextPrimaryDark))
                isChecked = option == currentValue
                minHeight = resources.getDimensionPixelSize(R.dimen.setting_option_min_height)
            })
        }

        VoiceFirstGestureBinder.bindAction(
            view = view.findViewById<Button>(R.id.btnDialogSave),
            speechProvider = SettingsControlSpeechRenderer::saveOption,
            speak = ::speakControlIdentification,
            activate = {
                val checkedId = radioGroup.checkedRadioButtonId
                if (checkedId != -1) {
                    onSelected(radioGroup.findViewById<RadioButton>(checkedId).text.toString())
                    dialog.dismiss()
                }
            }
        )
        dialog.show()
    }

    override fun onDestroy() {
        voiceHelper.shutdown()
        super.onDestroy()
    }

}
