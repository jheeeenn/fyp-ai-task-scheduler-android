package com.example.myapplication

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.accessibility.AccessibilityAnnouncementHelper
import com.example.myapplication.accessibility.AccessibilityStateHelper
import com.example.myapplication.accessibility.AssistantAccessibilitySemantics
import com.example.myapplication.accessibility.AssistantAccessibilityState
import com.example.myapplication.accessibility.resolveThemeColor
import com.google.android.material.bottomsheet.BottomSheetDialog

class AssistantBottomSheet(
    private val activity: AppCompatActivity,
    private val onStateChanged: (AssistantAccessibilityState) -> Unit = {},
    private val onPanelDismissed: () -> Unit = {}
) : BottomSheetDialog(activity) {
    private var onDoubleTapCancel: (() -> Unit)? = null

    private lateinit var assistantRoot: View
    private lateinit var stateContainer: View
    private lateinit var stateIndicator: View
    private lateinit var tvState: TextView
    private lateinit var userTranscriptSection: View
    private lateinit var assistantReplySection: View
    private lateinit var tvUserSpeech: TextView
    private lateinit var tvAssistantReply: TextView
    private var focusReturnView: View? = null
    private var restoreFocusOnDismiss = true

    val isContentReady: Boolean
        get() = ::stateContainer.isInitialized &&
            ::stateIndicator.isInitialized &&
            ::assistantRoot.isInitialized &&
            ::tvState.isInitialized

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = layoutInflater.inflate(R.layout.bottomsheet_assistant, null)
        setContentView(view)
        AccessibilityStateHelper.markHeading(view.findViewById(R.id.assistantTitle))

        setCancelable(false)
        setCanceledOnTouchOutside(false)

        assistantRoot = view.findViewById(R.id.assistantRoot)
        stateContainer = view.findViewById(R.id.stateContainer)
        stateIndicator = view.findViewById(R.id.assistantStateIndicator)
        tvState = view.findViewById(R.id.tvAssistantState)
        userTranscriptSection = view.findViewById(R.id.userTranscriptSection)
        assistantReplySection = view.findViewById(R.id.assistantReplySection)
        tvUserSpeech = view.findViewById(R.id.tvUserSpeech)
        tvAssistantReply = view.findViewById(R.id.tvAssistantReply)

        assistantRoot.contentDescription = "Assistant panel"
        VoiceFirstGestureBinder.bindAction(
            view = assistantRoot,
            speechProvider = { null },
            speak = { _ -> },
            activate = { onDoubleTapCancel?.invoke() }
        )

        setOnDismissListener {
            onPanelDismissed()
            val shouldRestoreFocus = restoreFocusOnDismiss
            restoreFocusOnDismiss = true
            if (shouldRestoreFocus && !activity.isFinishing && !activity.isDestroyed) {
                AccessibilityStateHelper.restoreAccessibilityFocus(focusReturnView)
            }
        }
    }

    fun setOnDoubleTapCancelListener(listener: (() -> Unit)?) {
        onDoubleTapCancel = listener
    }

    fun setFocusReturnView(view: View?) {
        focusReturnView = view
    }

    fun dismissWithoutFocusReturn() {
        restoreFocusOnDismiss = false
        dismiss()
    }

    fun setIdleState() {
        if (!isContentReady) return
        applyState(AssistantAccessibilityState.READY, announce = false)
    }

    fun setListeningState() {
        if (!isContentReady) return
        applyState(AssistantAccessibilityState.LISTENING, announce = true)
    }

    fun setProcessingState() {
        if (!isContentReady) return
        applyState(AssistantAccessibilityState.PROCESSING, announce = true)
    }

    fun setErrorState(@Suppress("UNUSED_PARAMETER") text: String) {
        if (!isContentReady) return
        applyState(AssistantAccessibilityState.ERROR, announce = false)
    }

    fun setSpeakingState() {
        if (!isContentReady) return
        applyState(AssistantAccessibilityState.SPEAKING, announce = false)
    }

    fun setWaitingForConfirmationState() {
        if (!isContentReady) return
        applyState(AssistantAccessibilityState.WAITING_FOR_CONFIRMATION, announce = true)
    }

    fun setStoppedState() {
        if (!isContentReady) return
        applyState(AssistantAccessibilityState.STOPPED, announce = false)
    }

    fun showUserSpeech(text: String) {
        val visible = text.isNotBlank()
        tvUserSpeech.text = text
        userTranscriptSection.visibility = if (visible) View.VISIBLE else View.GONE
        tvUserSpeech.contentDescription = if (visible) "You said, $text" else null
    }

    fun showAssistantReply(text: String) {
        val visible = text.isNotBlank()
        tvAssistantReply.text = text
        assistantReplySection.visibility = if (visible) View.VISIBLE else View.GONE
        tvAssistantReply.contentDescription = if (visible) "Assistant reply, $text" else null
    }

    fun clearConversation() {
        showUserSpeech("")
        showAssistantReply("")
    }

    fun performProcessingHapticPulse(): Boolean {
        if (!isShowing || !isContentReady || !assistantRoot.isAttachedToWindow) return false
        return assistantRoot.performProcessingHapticFeedback()
    }

    private fun applyState(state: AssistantAccessibilityState, announce: Boolean) {
        tvState.text = state.label
        stateIndicator.backgroundTintList = ColorStateList.valueOf(indicatorColor(state))
        stateContainer.contentDescription = "Assistant status"
        val changed = AccessibilityStateHelper.updateAssistantState(stateContainer, state, announce)
        if (changed) {
            AccessibilityAnnouncementHelper.logState(
                screen = "ASSISTANT_PANEL",
                element = "ASSISTANT_STATUS",
                state = AssistantAccessibilitySemantics.diagnosticState(state)
            )
            onStateChanged(state)
        }
    }

    private fun indicatorColor(state: AssistantAccessibilityState): Int =
        activity.resolveThemeColor(
            when (state) {
                AssistantAccessibilityState.READY,
                AssistantAccessibilityState.STOPPED -> R.attr.appColorAssistantStateNeutral
                AssistantAccessibilityState.LISTENING -> R.attr.appColorAssistantStateListening
                AssistantAccessibilityState.PROCESSING -> R.attr.appColorAssistantStateProcessing
                AssistantAccessibilityState.WAITING_FOR_CONFIRMATION ->
                    R.attr.appColorAssistantStateWaiting
                AssistantAccessibilityState.SPEAKING -> R.attr.appColorAssistantStateSpeaking
                AssistantAccessibilityState.ERROR -> R.attr.appColorAssistantStateError
            }
        )
}
