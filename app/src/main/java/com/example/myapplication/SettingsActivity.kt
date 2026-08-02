package com.example.myapplication

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.accessibility.AccessibilityStateHelper
import com.example.myapplication.accessibility.AssistantAccessibilityState

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "assistant_settings"
        const val KEY_ASSISTANT_TONE = "assistant_tone"
        const val KEY_REPLY_LENGTH = "reply_length"
        const val KEY_LARGE_TEXT = "large_text"
        const val KEY_HIGH_CONTRAST = "high_contrast"
        const val KEY_LM_STUDIO_ENDPOINT = "lm_studio_endpoint"
        const val KEY_CONVERSATION_AGENT_ENDPOINT = "conversation_agent_endpoint"
        const val KEY_TASK_AGENT_ENDPOINT = "task_agent_endpoint"
        const val DEFAULT_LM_STUDIO_ENDPOINT = "http://192.168.0.132:1234/v1/chat/completions"
        const val DEFAULT_CONVERSATION_AGENT_ENDPOINT = "http://192.168.0.132:1234/v1/chat/completions"
        const val DEFAULT_TASK_AGENT_ENDPOINT = "http://192.168.0.132:1234/v1/chat/completions"
    }

    private lateinit var prefs: SharedPreferences

    private lateinit var tvToneValue: TextView
    private lateinit var tvReplyLengthValue: TextView
    private lateinit var tvLargeTextValue: TextView
    private lateinit var tvHighContrastValue: TextView
    private lateinit var tvConversationAgentEndpointValue: TextView
    private lateinit var tvTaskAgentEndpointValue: TextView

    private var assistantTone: String = "Friendly"
    private var replyLength: String = "Normal"
    private var largeText: Boolean = false
    private var highContrast: Boolean = false
    private var conversationAgentEndpoint: String = DEFAULT_CONVERSATION_AGENT_ENDPOINT
    private var taskAgentEndpoint: String = DEFAULT_TASK_AGENT_ENDPOINT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        AccessibilityStateHelper.markHeading(findViewById(R.id.tvSettingsTitle))

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val cardTone = findViewById<LinearLayout>(R.id.cardTone)
        val cardReplyLength = findViewById<LinearLayout>(R.id.cardReplyLength)
        val cardLargeText = findViewById<LinearLayout>(R.id.cardLargeText)
        val cardHighContrast = findViewById<LinearLayout>(R.id.cardHighContrast)
        val cardConversationAgentEndpoint = findViewById<LinearLayout>(R.id.cardConversationAgentEndpoint)
        val cardTaskAgentEndpoint = findViewById<LinearLayout>(R.id.cardTaskAgentEndpoint)

        val btnGoHome = findViewById<Button>(R.id.btnGoHome)
        val btnTalkAssistant = findViewById<Button>(R.id.btnTalkAssistant)
        AccessibilityStateHelper.updateAssistantState(
            btnTalkAssistant,
            AssistantAccessibilityState.READY,
            announce = false
        )

        tvToneValue = findViewById(R.id.tvToneValue)
        tvReplyLengthValue = findViewById(R.id.tvReplyLengthValue)
        tvLargeTextValue = findViewById(R.id.tvLargeTextValue)
        tvHighContrastValue = findViewById(R.id.tvHighContrastValue)
        tvConversationAgentEndpointValue = findViewById(R.id.tvConversationAgentEndpointValue)
        tvTaskAgentEndpointValue = findViewById(R.id.tvTaskAgentEndpointValue)

        loadSettings()
        updateUiValues()

        cardTone.setOnClickListenerWithHaptic {
            showOptionDialog(
                title = "Assistant Tone",
                options = listOf("Friendly", "Neutral", "Professional"),
                currentValue = assistantTone
            ) { selected ->
                assistantTone = selected
                saveSettings()
                updateUiValues()
            }
        }

        cardReplyLength.setOnClickListenerWithHaptic {
            showOptionDialog(
                title = "Reply Length",
                options = listOf("Short", "Normal", "Detailed"),
                currentValue = replyLength
            ) { selected ->
                replyLength = selected
                saveSettings()
                updateUiValues()
            }
        }

        cardLargeText.setOnClickListenerWithHaptic {
            showOptionDialog(
                title = "Large Text",
                options = listOf("Off", "On"),
                currentValue = if (largeText) "On" else "Off"
            ) { selected ->
                largeText = selected == "On"
                saveSettings()
                updateUiValues()
            }
        }

        cardHighContrast.setOnClickListenerWithHaptic {
            showOptionDialog(
                title = "High Contrast",
                options = listOf("Off", "On"),
                currentValue = if (highContrast) "On" else "Off"
            ) { selected ->
                highContrast = selected == "On"
                saveSettings()
                updateUiValues()
            }
        }

        cardConversationAgentEndpoint.setOnClickListenerWithHaptic {
            showEndpointDialog(
                title = "Conversation Agent Endpoint",
                currentValue = conversationAgentEndpoint,
                defaultValue = DEFAULT_CONVERSATION_AGENT_ENDPOINT
            ) { endpoint ->
                conversationAgentEndpoint = endpoint
                saveSettings()
                updateUiValues()
            }
        }

        cardTaskAgentEndpoint.setOnClickListenerWithHaptic {
            showEndpointDialog(
                title = "Task Agent Endpoint",
                currentValue = taskAgentEndpoint,
                defaultValue = DEFAULT_TASK_AGENT_ENDPOINT
            ) { endpoint ->
                taskAgentEndpoint = endpoint
                saveSettings()
                updateUiValues()
            }
        }

        btnGoHome.setOnClickListenerWithHaptic {
            finish()
        }

        btnTalkAssistant.setOnClickListenerWithHaptic {
            startActivity(Intent(this, HomeActivity::class.java).apply {
                putExtra("open_assistant_on_arrival", true)
            })
        }
    }

    private fun loadSettings() {
        assistantTone = prefs.getString(KEY_ASSISTANT_TONE, "Friendly") ?: "Friendly"
        replyLength = prefs.getString(KEY_REPLY_LENGTH, "Normal") ?: "Normal"
        largeText = prefs.getBoolean(KEY_LARGE_TEXT, false)
        highContrast = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
        conversationAgentEndpoint = loadEndpointPreference(
            dedicatedKey = KEY_CONVERSATION_AGENT_ENDPOINT,
            dedicatedDefault = DEFAULT_CONVERSATION_AGENT_ENDPOINT
        )
        taskAgentEndpoint = loadEndpointPreference(
            dedicatedKey = KEY_TASK_AGENT_ENDPOINT,
            dedicatedDefault = DEFAULT_TASK_AGENT_ENDPOINT
        )
    }

    private fun saveSettings() {
        prefs.edit()
            .putString(KEY_ASSISTANT_TONE, assistantTone)
            .putString(KEY_REPLY_LENGTH, replyLength)
            .putBoolean(KEY_LARGE_TEXT, largeText)
            .putBoolean(KEY_HIGH_CONTRAST, highContrast)
            .putString(KEY_CONVERSATION_AGENT_ENDPOINT, conversationAgentEndpoint)
            .putString(KEY_TASK_AGENT_ENDPOINT, taskAgentEndpoint)
            .apply()
    }

    private fun updateUiValues() {
        tvToneValue.text = assistantTone
        tvReplyLengthValue.text = replyLength
        tvLargeTextValue.text = if (largeText) "On" else "Off"
        tvHighContrastValue.text = if (highContrast) "On" else "Off"
        tvConversationAgentEndpointValue.text = conversationAgentEndpoint
        tvTaskAgentEndpointValue.text = taskAgentEndpoint
        findViewById<LinearLayout>(R.id.cardTone).contentDescription =
            "Assistant tone, $assistantTone"
        findViewById<LinearLayout>(R.id.cardReplyLength).contentDescription =
            "Reply length, $replyLength"
        findViewById<LinearLayout>(R.id.cardLargeText).contentDescription =
            "Large text, ${if (largeText) "On" else "Off"}"
        findViewById<LinearLayout>(R.id.cardHighContrast).contentDescription =
            "High contrast, ${if (highContrast) "On" else "Off"}"
        findViewById<LinearLayout>(R.id.cardConversationAgentEndpoint).contentDescription =
            "Conversation agent endpoint, $conversationAgentEndpoint"
        findViewById<LinearLayout>(R.id.cardTaskAgentEndpoint).contentDescription =
            "Task agent endpoint, $taskAgentEndpoint"
    }

    private fun loadEndpointPreference(dedicatedKey: String, dedicatedDefault: String): String {
        val dedicatedValue = if (prefs.contains(dedicatedKey)) {
            prefs.getString(dedicatedKey, null)
        } else {
            null
        }
        if (!dedicatedValue.isNullOrBlank()) return dedicatedValue

        val legacyValue = prefs.getString(KEY_LM_STUDIO_ENDPOINT, null)
        if (!legacyValue.isNullOrBlank()) return legacyValue

        return dedicatedDefault
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
            .setPositiveButton("Save", null)
            .setNegativeButton("Reset to Default") { _, _ ->
                onEndpointSelected(defaultValue)
            }
            .setNeutralButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val normalizedEndpoint = normalizeLmStudioEndpoint(endpointInput.text.toString())
                if (normalizedEndpoint == null) {
                    Toast.makeText(this, "Endpoint cannot be blank.", Toast.LENGTH_SHORT).show()
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

        val baseEndpoint = if (!hasPath && !withoutTrailingSlash.substring(schemeEnd).contains(":")) {
            "$withoutTrailingSlash:1234"
        } else {
            withoutTrailingSlash
        }

        return "$baseEndpoint/v1/chat/completions"
    }

    private fun showOptionDialog(
        title: String,
        options: List<String>,
        currentValue: String,
        onSelected: (String) -> Unit
    ) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_setting_options, null)
        dialog.setContentView(view)

        val tvDialogTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        val radioGroupOptions = view.findViewById<RadioGroup>(R.id.radioGroupOptions)
        val btnDialogSave = view.findViewById<Button>(R.id.btnDialogSave)

        tvDialogTitle.text = title

        options.forEach { option ->
            val radioButton = RadioButton(this).apply {
                text = option
                textSize = 20f
                setTextColor(android.graphics.Color.parseColor("#F5F1FF"))
                isChecked = option == currentValue
            }
            radioGroupOptions.addView(radioButton)
        }

        btnDialogSave.setOnClickListenerWithHaptic {
            val checkedId = radioGroupOptions.checkedRadioButtonId
            if (checkedId != -1) {
                val selectedButton = radioGroupOptions.findViewById<RadioButton>(checkedId)
                onSelected(selectedButton.text.toString())
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
