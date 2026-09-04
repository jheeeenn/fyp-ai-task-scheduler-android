package com.example.myapplication

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.accessibility.AccessibilityStateHelper
import com.example.myapplication.accessibility.AssistantAccessibilityState
import com.example.myapplication.accessibility.resolveThemeColor
import com.example.myapplication.voice.AssistantTranscriptEvent
import com.example.myapplication.voice.AssistantTranscriptRole
import com.google.android.material.switchmaterial.SwitchMaterial

/** Reusable developer-only presentation bound to the currently visible real assistant host. */
class DeveloperAssistantOverlay private constructor(
    private val activity: AppCompatActivity,
    private val onSubmit: (String) -> Unit
) {
    private val dialog = Dialog(activity)
    private lateinit var transcriptContainer: LinearLayout
    private lateinit var transcriptScroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private lateinit var statusText: TextView
    private lateinit var ttsSwitch: SwitchMaterial
    private val sessionObserver: () -> Unit = {
        activity.runOnUiThread {
            if (!activity.isFinishing && !activity.isDestroyed) renderSession()
        }
    }

    init {
        configureDialog()
        bindViews()
        bindInteractions()
        DeveloperTestSession.addObserver(sessionObserver)
    }

    fun dismiss() {
        DeveloperTestSession.removeObserver(sessionObserver)
        if (dialog.isShowing) dialog.dismiss()
    }

    @Suppress("DEPRECATION")
    private fun configureDialog() {
        dialog.apply {
            setContentView(R.layout.dialog_developer_test)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    exitDeveloperTesting()
                    true
                } else {
                    false
                }
            }
            show()
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
        }
    }

    private fun bindViews() {
        AccessibilityStateHelper.markHeading(dialog.findViewById(R.id.tvDeveloperTestTitle))
        transcriptContainer = dialog.findViewById(R.id.developerTranscriptContainer)
        transcriptScroll = dialog.findViewById(R.id.developerTranscriptScroll)
        input = dialog.findViewById(R.id.etDeveloperTestInput)
        sendButton = dialog.findViewById(R.id.btnDeveloperTestSend)
        statusText = dialog.findViewById(R.id.tvDeveloperTestStatus)
        ttsSwitch = dialog.findViewById(R.id.switchDeveloperTestTts)
    }

    private fun bindInteractions() {
        ttsSwitch.setOnCheckedChangeListener { _, enabled ->
            DeveloperTestSession.setTtsEnabled(enabled)
        }
        sendButton.setOnClickListener { submitInput() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitInput()
                true
            } else {
                false
            }
        }
        dialog.findViewById<Button>(R.id.btnDeveloperTestReset).setOnClickListener {
            restartDeveloperTesting()
        }
        dialog.findViewById<Button>(R.id.btnDeveloperTestBack).setOnClickListener {
            exitDeveloperTesting()
        }
        input.requestFocus()
    }

    private fun submitInput() {
        val typedText = input.text?.toString()?.trim().orEmpty()
        if (typedText.isBlank()) return
        input.text?.clear()
        onSubmit(typedText)
    }

    private fun renderSession() {
        if (!DeveloperTestSession.isActive) return
        val waitingForReply = DeveloperTestSession.assistantState in setOf(
            AssistantAccessibilityState.PROCESSING,
            AssistantAccessibilityState.SPEAKING
        )
        statusText.apply {
            text = activity.getString(
                if (waitingForReply) R.string.developer_test_processing
                else R.string.developer_test_ready
            )
            contentDescription = text
        }
        input.isEnabled = !waitingForReply
        sendButton.isEnabled = !waitingForReply
        ttsSwitch.isChecked = DeveloperTestSession.ttsEnabled
        renderTranscript(DeveloperTestSession.transcriptSnapshot())
        if (!waitingForReply) input.requestFocus()
    }

    private fun renderTranscript(events: List<AssistantTranscriptEvent>) {
        transcriptContainer.removeAllViews()
        events.forEach { event -> transcriptContainer.addView(messageView(event)) }
        transcriptScroll.post { transcriptScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun messageView(event: AssistantTranscriptEvent): TextView {
        val isUser = event.role == AssistantTranscriptRole.USER
        val role = activity.getString(
            if (isUser) R.string.developer_test_role_user
            else R.string.developer_test_role_assistant
        )
        return TextView(activity).apply {
            text = "$role\n${event.text}"
            contentDescription = "$role, ${event.text}"
            textSize = 17f
            setTextColor(activity.resolveThemeColor(R.attr.appColorTextPrimaryLight))
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
                if (isUser) marginStart = 44.dp else marginEnd = 44.dp
            }
        }
    }

    private fun restartDeveloperTesting() {
        dismiss()
        DeveloperTestSession.reset()
        activity.startActivity(
            Intent(activity, DeveloperTestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
    }

    private fun exitDeveloperTesting() {
        dismiss()
        DeveloperTestSession.deactivate()
        activity.startActivity(
            Intent(activity, AdvancedSettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
    }

    private val Int.dp: Int
        get() = (this * activity.resources.displayMetrics.density).toInt()

    companion object {
        fun attach(
            activity: AppCompatActivity,
            onSubmit: (String) -> Unit
        ): DeveloperAssistantOverlay? {
            if (!DeveloperTestSession.isActive) return null
            return DeveloperAssistantOverlay(activity, onSubmit)
        }
    }
}
