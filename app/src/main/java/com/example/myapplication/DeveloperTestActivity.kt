package com.example.myapplication

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.myapplication.accessibility.AccessibilityStateHelper
import com.example.myapplication.accessibility.AssistantAccessibilityState
import com.example.myapplication.accessibility.resolveThemeColor
import com.example.myapplication.voice.AssistantInteractionMode
import com.example.myapplication.voice.AssistantTranscriptEvent
import com.example.myapplication.voice.AssistantTranscriptRole
import com.google.android.material.switchmaterial.SwitchMaterial

class DeveloperTestActivity : HomeActivity() {
    private var developerDialog: Dialog? = null
    private var ttsEnabled = false
    private var transcriptContainer: LinearLayout? = null
    private var transcriptScroll: ScrollView? = null
    private var input: EditText? = null
    private var sendButton: Button? = null
    private var statusText: TextView? = null

    override val assistantInteractionMode: AssistantInteractionMode
        get() = AssistantInteractionMode.DEVELOPER_TEXT

    override fun shouldSpeakAssistantAudio(): Boolean = ttsEnabled

    override fun preserveAssistantSessionWhileStopped(): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showDeveloperInterface()
    }

    override fun onAssistantTranscript(event: AssistantTranscriptEvent) {
        runOnUiThread {
            appendTranscript(event)
        }
    }

    override fun onAssistantPresentationStateChanged(state: AssistantAccessibilityState) {
        runOnUiThread {
            val waitingForReply = state == AssistantAccessibilityState.PROCESSING ||
                state == AssistantAccessibilityState.SPEAKING
            statusText?.apply {
                text = if (waitingForReply) {
                    getString(R.string.developer_test_processing)
                } else {
                    getString(R.string.developer_test_ready)
                }
                contentDescription = text
            }
            input?.isEnabled = !waitingForReply
            sendButton?.isEnabled = !waitingForReply
            if (!waitingForReply) input?.requestFocus()
        }
    }

    @Suppress("DEPRECATION")
    private fun showDeveloperInterface() {
        val dialog = Dialog(this).apply {
            setContentView(R.layout.dialog_developer_test)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    finish()
                    true
                } else {
                    false
                }
            }
            show()
            window?.apply {
                setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
        }
        developerDialog = dialog

        AccessibilityStateHelper.markHeading(dialog.findViewById(R.id.tvDeveloperTestTitle))
        transcriptContainer = dialog.findViewById(R.id.developerTranscriptContainer)
        transcriptScroll = dialog.findViewById(R.id.developerTranscriptScroll)
        input = dialog.findViewById(R.id.etDeveloperTestInput)
        sendButton = dialog.findViewById(R.id.btnDeveloperTestSend)
        statusText = dialog.findViewById(R.id.tvDeveloperTestStatus)

        dialog.findViewById<SwitchMaterial>(R.id.switchDeveloperTestTts).apply {
            isChecked = false
            ttsEnabled = false
            setOnCheckedChangeListener { _, enabled -> ttsEnabled = enabled }
        }
        sendButton?.setOnClickListener { submitInput() }
        input?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitInput()
                true
            } else {
                false
            }
        }
        dialog.findViewById<Button>(R.id.btnDeveloperTestReset).setOnClickListener {
            developerDialog?.dismiss()
            developerDialog = null
            recreate()
        }
        dialog.findViewById<Button>(R.id.btnDeveloperTestBack).setOnClickListener {
            finish()
        }
        input?.requestFocus()
    }

    private fun submitInput() {
        val typedText = input?.text?.toString()?.trim().orEmpty()
        if (typedText.isBlank()) return
        input?.text?.clear()
        submitPersistentTypedAssistantText(typedText)
    }

    private fun appendTranscript(event: AssistantTranscriptEvent) {
        val container = transcriptContainer ?: return
        val isUser = event.role == AssistantTranscriptRole.USER
        val role = getString(
            if (isUser) R.string.developer_test_role_user
            else R.string.developer_test_role_assistant
        )
        val horizontalInset = 44.dp
        val messageView = TextView(this).apply {
            text = "$role\n${event.text}"
            contentDescription = "$role, ${event.text}"
            textSize = 17f
            setTextColor(
                resolveThemeColor(
                    if (isUser) R.attr.appColorTextPrimaryLight
                    else R.attr.appColorTextPrimaryDark
                )
            )
            setPadding(16.dp, 12.dp, 16.dp, 12.dp)
            setBackgroundResource(
                if (isUser) R.drawable.bg_developer_user_message
                else R.drawable.bg_developer_assistant_message
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dp
                if (isUser) marginStart = horizontalInset else marginEnd = horizontalInset
            }
        }
        container.addView(messageView)
        transcriptScroll?.post {
            transcriptScroll?.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        developerDialog?.dismiss()
        developerDialog = null
        transcriptContainer = null
        transcriptScroll = null
        input = null
        sendButton = null
        statusText = null
        super.onDestroy()
    }
}
