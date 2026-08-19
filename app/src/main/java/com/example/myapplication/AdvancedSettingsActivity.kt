package com.example.myapplication

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.myapplication.accessibility.AccessibilityActivity
import com.example.myapplication.accessibility.AccessibilityStateHelper
import com.example.myapplication.preferences.AppPreferences

class AdvancedSettingsActivity : AccessibilityActivity() {
    private lateinit var appPreferences: AppPreferences
    private lateinit var voiceHelper: VoiceHelper
    private lateinit var conversationEndpointValue: TextView
    private lateinit var taskEndpointValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_settings)

        appPreferences = AppPreferences(this)
        voiceHelper = VoiceHelper(this)
        AccessibilityStateHelper.markHeading(findViewById(R.id.tvAdvancedSettingsTitle))
        AccessibilityStateHelper.markHeading(findViewById(R.id.tvAiConnectionsHeading))
        conversationEndpointValue = findViewById(R.id.tvConversationAgentEndpointValue)
        taskEndpointValue = findViewById(R.id.tvTaskAgentEndpointValue)
        renderEndpoints()
        bindInteractions()
    }

    private fun bindInteractions() {
        VoiceFirstGestureBinder.bindAction(
            view = findViewById<LinearLayout>(R.id.cardConversationAgentEndpoint),
            speechProvider = SettingsControlSpeechRenderer::conversationEndpoint,
            speak = voiceHelper::speak,
            activate = {
                showEndpointDialog(
                    title = getString(R.string.conversation_agent_endpoint),
                    currentValue = appPreferences.conversationAgentEndpoint,
                    defaultValue = AppPreferences.DEFAULT_CONVERSATION_AGENT_ENDPOINT
                ) { endpoint ->
                    appPreferences.setConversationAgentEndpoint(endpoint)
                    renderEndpoints()
                }
            }
        )
        VoiceFirstGestureBinder.bindAction(
            view = findViewById<LinearLayout>(R.id.cardTaskAgentEndpoint),
            speechProvider = SettingsControlSpeechRenderer::taskEndpoint,
            speak = voiceHelper::speak,
            activate = {
                showEndpointDialog(
                    title = getString(R.string.task_agent_endpoint),
                    currentValue = appPreferences.taskAgentEndpoint,
                    defaultValue = AppPreferences.DEFAULT_TASK_AGENT_ENDPOINT
                ) { endpoint ->
                    appPreferences.setTaskAgentEndpoint(endpoint)
                    renderEndpoints()
                }
            }
        )
        VoiceFirstGestureBinder.bindAction(
            view = findViewById<Button>(R.id.btnBackToSettings),
            speechProvider = { getString(R.string.back_to_settings) },
            speak = voiceHelper::speak,
            activate = ::finish
        )
    }

    private fun renderEndpoints() {
        conversationEndpointValue.text = appPreferences.conversationAgentEndpoint
        taskEndpointValue.text = appPreferences.taskAgentEndpoint
        findViewById<LinearLayout>(R.id.cardConversationAgentEndpoint).contentDescription =
            getString(R.string.conversation_agent_endpoint)
        findViewById<LinearLayout>(R.id.cardTaskAgentEndpoint).contentDescription =
            getString(R.string.task_agent_endpoint)
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
                val normalizedEndpoint =
                    normalizeLmStudioEndpoint(endpointInput.text.toString())
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

    internal fun normalizeLmStudioEndpoint(input: String): String? {
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

    override fun onDestroy() {
        voiceHelper.shutdown()
        super.onDestroy()
    }
}
