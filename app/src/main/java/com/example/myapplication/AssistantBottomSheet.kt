package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.accessibility.AccessibilityAnnouncementHelper
import com.example.myapplication.accessibility.AccessibilityStateHelper
import com.example.myapplication.accessibility.AssistantAccessibilitySemantics
import com.example.myapplication.accessibility.AssistantAccessibilityState
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.graphics.drawable.GradientDrawable
import com.example.myapplication.accessibility.resolveThemeColor


class AssistantBottomSheet(
    private val activity: AppCompatActivity,
    private val speakIdentification: (String) -> Unit,
    private val onStateChanged: (AssistantAccessibilityState) -> Unit = {},
    private val onPanelDismissed: () -> Unit = {}
) : BottomSheetDialog(activity) {
    private var onDoubleTapCancel: (() -> Unit)? = null

    private lateinit var assistantRoot: View
    private var defaultBorderColor: Int = 0
    private var stateAnimator: ValueAnimator? = null
    private var defaultStateColor: Int = 0
    private lateinit var stateContainer: View

    private lateinit var tvAssistantHint: TextView
    private lateinit var tvState: TextView
    private lateinit var tvUserSpeech: TextView
    private lateinit var tvAssistantReply: TextView
    private var focusReturnView: View? = null
    private var restoreFocusOnDismiss = true

    val isContentReady: Boolean
        get() = ::stateContainer.isInitialized &&
            ::assistantRoot.isInitialized &&
            ::tvState.isInitialized

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = layoutInflater.inflate(
            R.layout.bottomsheet_assistant,
            null
        )

        setContentView(view)
        AccessibilityStateHelper.markHeading(view.findViewById(R.id.assistantTitle))

        // to prevent outside tap dismissal
        setCancelable(false)
        setCanceledOnTouchOutside(false)

        tvState = view.findViewById(R.id.tvAssistantState)

        stateContainer = view.findViewById(R.id.stateContainer)
        defaultStateColor = activity.resolveThemeColor(R.attr.appColorAssistantIdle)

        assistantRoot = view.findViewById(R.id.assistantRoot)
        defaultBorderColor = activity.resolveThemeColor(R.attr.appColorAssistantIdle)

        tvUserSpeech = view.findViewById(R.id.tvUserSpeech)
        tvAssistantReply = view.findViewById(R.id.tvAssistantReply)
        tvAssistantHint = view.findViewById(R.id.tvAssistantHint)

        VoiceFirstGestureBinder.bindAction(
            view = view.findViewById<Button>(R.id.btnStopAssistant),
            speechProvider = AssistantPanelControlSpeechRenderer::stop,
            speak = speakIdentification,
            activate = { onDoubleTapCancel?.invoke() }
        )
        VoiceFirstGestureBinder.bindAction(
            view = view.findViewById<Button>(R.id.btnTypeAssistantInput),
            speechProvider = AssistantPanelControlSpeechRenderer::typeInput,
            speak = speakIdentification,
            activate = { onTypedInputRequested?.invoke() }
        )

        assistantRoot.contentDescription = "Assistant panel"
        VoiceFirstGestureBinder.bindAction(
            view = assistantRoot,
            speechProvider = { null },
            speak = speakIdentification,
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
    private var onTypedInputRequested: (() -> Unit)? = null

    fun setOnDoubleTapCancelListener(listener: (() -> Unit)?) {
        onDoubleTapCancel = listener
    }
    fun setOnTypedInputRequestedListener(listener: (() -> Unit)?) {
        onTypedInputRequested = listener
    }
    fun setFocusReturnView(view: View?) {
        focusReturnView = view
    }
    fun dismissWithoutFocusReturn() {
        restoreFocusOnDismiss = false
        dismiss()
    }
    fun showAssistantHint(text: String) {
        tvAssistantHint.text = text
        tvAssistantHint.contentDescription = if (text.isBlank()) {
            "No next action suggested"
        } else {
            "What you can do next, $text"
        }
    }

    fun clearHint() {
        tvAssistantHint.text = ""
        tvAssistantHint.contentDescription = "No next action suggested"
    }
    fun setIdleState() {
        if (!isContentReady) return
        stopStateAnimation()
        applyState(AssistantAccessibilityState.READY, announce = false)
    }

    fun setListeningState() {
        if (!isContentReady) return
        applyState(AssistantAccessibilityState.LISTENING, announce = true)
        startListeningAnimation()
    }

    fun setProcessingState() {
        if (!isContentReady) return
        applyState(AssistantAccessibilityState.PROCESSING, announce = true)
        startProcessingAnimation()
    }

    fun setErrorState(@Suppress("UNUSED_PARAMETER") text: String) {
        if (!isContentReady) return
        stopStateAnimation()
        applyState(AssistantAccessibilityState.ERROR, announce = false)
    }

    fun setSpeakingState() {
        if (!isContentReady) return
        applyState(AssistantAccessibilityState.SPEAKING, announce = false)
        startSpeakingAnimation()
    }
    fun setWaitingForConfirmationState() {
        if (!isContentReady) return
        applyState(AssistantAccessibilityState.WAITING_FOR_CONFIRMATION, announce = true)
        startProcessingAnimation()
    }
    fun setStoppedState() {
        if (!isContentReady) return
        stopStateAnimation()
        applyState(AssistantAccessibilityState.STOPPED, announce = false)
    }
    fun showUserSpeech(text: String) {
        tvUserSpeech.text = text
        tvUserSpeech.contentDescription = if (text.isBlank()) "Nothing entered yet" else "You said, $text"
    }

    fun showAssistantReply(text: String) {
        tvAssistantReply.text = text
        tvAssistantReply.contentDescription = if (text.isBlank()) "No assistant reply yet" else "Assistant reply, $text"
    }

    fun clearConversation() {
        tvUserSpeech.text = ""
        tvAssistantReply.text = ""
        tvAssistantHint.text = ""
        tvUserSpeech.contentDescription = "Nothing entered yet"
        tvAssistantReply.contentDescription = "No assistant reply yet"
        tvAssistantHint.contentDescription = "No next action suggested"
    }

    fun performProcessingHapticPulse(): Boolean {
        if (!isShowing || !isContentReady || !assistantRoot.isAttachedToWindow) return false
        return assistantRoot.performProcessingHapticFeedback()
    }

    private fun applyState(state: AssistantAccessibilityState, announce: Boolean) {
        tvState.text = state.label
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

    // helper functions

    private fun stopStateAnimation() {
        stateAnimator?.cancel()
        stateAnimator = null
        if (!isContentReady) return
        stateContainer.setBackgroundColor(defaultStateColor)
        updatePanelBorder(defaultBorderColor)
    }

    private fun startListeningAnimation() {
        stopStateAnimation()

        stateAnimator = ValueAnimator.ofObject(
            ArgbEvaluator(),
            activity.resolveThemeColor(R.attr.appColorAssistantListeningStart),
            activity.resolveThemeColor(R.attr.appColorAssistantListeningEnd)
        ).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                stateContainer.setBackgroundColor(color)
                updatePanelBorder(color)
            }
            start()
        }
    }

    private fun startProcessingAnimation() {
        stopStateAnimation()

        stateAnimator = ValueAnimator.ofObject(
            ArgbEvaluator(),
            activity.resolveThemeColor(R.attr.appColorAssistantProcessingStart),
            activity.resolveThemeColor(R.attr.appColorAssistantProcessingEnd)
        ).apply {
            duration = 650
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                stateContainer.setBackgroundColor(color)
                updatePanelBorder(color)
            }
            start()
        }
    }

    private fun startSpeakingAnimation() {
        stopStateAnimation()

        stateAnimator = ValueAnimator.ofObject(
            ArgbEvaluator(),
            activity.resolveThemeColor(R.attr.appColorAssistantSpeakingStart),
            activity.resolveThemeColor(R.attr.appColorAssistantSpeakingEnd)
        ).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                stateContainer.setBackgroundColor(color)
                updatePanelBorder(color)
            }
            start()
        }
    }
    private fun updatePanelBorder(color: Int) {
        val background = assistantRoot.background
        if (background is GradientDrawable) {
            background.setStroke(4.dpToPx(), color)
        }
    }
    private fun Int.dpToPx(): Int {
        return (this * activity.resources.displayMetrics.density).toInt()
    }
}
