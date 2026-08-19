package com.example.myapplication

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
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
    private lateinit var assistantTitle: TextView
    private lateinit var userTranscriptSection: View
    private lateinit var assistantReplySection: View
    private lateinit var tvUserSpeech: TextView
    private lateinit var tvAssistantReply: TextView
    private var focusReturnView: View? = null
    private var restoreFocusOnDismiss = true
    private var stateAnimator: ValueAnimator? = null
    private var defaultStateColor: Int = 0
    private var defaultBorderColor: Int = 0

    val isContentReady: Boolean
        get() = ::stateContainer.isInitialized &&
            ::stateIndicator.isInitialized &&
            ::assistantRoot.isInitialized &&
            ::tvState.isInitialized &&
            ::assistantTitle.isInitialized

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
        assistantTitle = view.findViewById(R.id.assistantTitle)
        userTranscriptSection = view.findViewById(R.id.userTranscriptSection)
        assistantReplySection = view.findViewById(R.id.assistantReplySection)
        tvUserSpeech = view.findViewById(R.id.tvUserSpeech)
        tvAssistantReply = view.findViewById(R.id.tvAssistantReply)
        defaultStateColor = activity.resolveThemeColor(R.attr.appColorAssistantIdle)
        defaultBorderColor = defaultStateColor
        updateStateTreatment(defaultStateColor)

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
        updateStateTreatment(
            activity.resolveThemeColor(R.attr.appColorAssistantProcessingEnd)
        )
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

    private fun stopStateAnimation() {
        stateAnimator?.cancel()
        stateAnimator = null
        if (!isContentReady) return
        updateStateContainer(defaultStateColor)
        updatePanelBorder(defaultBorderColor)
        updateStateTextContrast(defaultStateColor)
    }

    private fun startListeningAnimation() {
        startStateAnimation(
            startColorAttr = R.attr.appColorAssistantListeningStart,
            endColorAttr = R.attr.appColorAssistantListeningEnd,
            durationMs = LISTENING_ANIMATION_DURATION_MS
        )
    }

    private fun startProcessingAnimation() {
        startStateAnimation(
            startColorAttr = R.attr.appColorAssistantProcessingStart,
            endColorAttr = R.attr.appColorAssistantProcessingEnd,
            durationMs = PROCESSING_ANIMATION_DURATION_MS
        )
    }

    private fun startSpeakingAnimation() {
        startStateAnimation(
            startColorAttr = R.attr.appColorAssistantSpeakingStart,
            endColorAttr = R.attr.appColorAssistantSpeakingEnd,
            durationMs = SPEAKING_ANIMATION_DURATION_MS
        )
    }

    private fun startStateAnimation(startColorAttr: Int, endColorAttr: Int, durationMs: Long) {
        stopStateAnimation()
        stateAnimator = ValueAnimator.ofObject(
            ArgbEvaluator(),
            activity.resolveThemeColor(startColorAttr),
            activity.resolveThemeColor(endColorAttr)
        ).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                updateStateTreatment(animator.animatedValue as Int)
            }
            start()
        }
    }

    private fun updateStateTreatment(color: Int) {
        updateStateContainer(color)
        updatePanelBorder(color)
        updateStateTextContrast(color)
    }

    private fun updateStateContainer(color: Int) {
        val background = stateContainer.background
        if (background is GradientDrawable) {
            background.mutate()
            background.setColor(color)
        }
    }

    private fun updatePanelBorder(color: Int) {
        val background = assistantRoot.background
        if (background is GradientDrawable) {
            background.mutate()
            background.setStroke(PANEL_BORDER_WIDTH_DP.dpToPx(), color)
        }
    }

    private fun updateStateTextContrast(backgroundColor: Int) {
        val foreground = if (ColorUtils.calculateLuminance(backgroundColor) >= 0.5) {
            Color.BLACK
        } else {
            Color.WHITE
        }
        tvState.setTextColor(foreground)
        assistantTitle.setTextColor(foreground)
        stateIndicator.backgroundTintList = ColorStateList.valueOf(foreground)
    }

    private fun Int.dpToPx(): Int =
        (this * activity.resources.displayMetrics.density).toInt()

    private companion object {
        const val LISTENING_ANIMATION_DURATION_MS = 700L
        const val PROCESSING_ANIMATION_DURATION_MS = 650L
        const val SPEAKING_ANIMATION_DURATION_MS = 900L
        const val PANEL_BORDER_WIDTH_DP = 4
    }
}
