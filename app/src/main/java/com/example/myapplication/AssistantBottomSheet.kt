package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.view.animation.LinearInterpolator

import android.graphics.drawable.GradientDrawable


class AssistantBottomSheet(
    private val activity: AppCompatActivity
) : BottomSheetDialog(activity) {
    private var onDoubleTapCancel: (() -> Unit)? = null
    private var lastTapTime: Long = 0L
    private val doubleTapWindowMs = 350L

    private lateinit var assistantRoot: View
    private var defaultBorderColor: Int = 0
    private var stateAnimator: ValueAnimator? = null
    private var defaultStateColor: Int = 0
    private lateinit var stateContainer: View

    private lateinit var tvAssistantHint: TextView
    private lateinit var tvState: TextView
    private lateinit var tvUserSpeech: TextView
    private lateinit var tvAssistantReply: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = layoutInflater.inflate(
            R.layout.bottomsheet_assistant,
            null
        )

        setContentView(view)

        // to prevent outside tap dismissal
        setCancelable(false)
        setCanceledOnTouchOutside(false)

        tvState = view.findViewById(R.id.tvAssistantState)

        stateContainer = view.findViewById(R.id.stateContainer)
        defaultStateColor = Color.parseColor("#2A2047")

        assistantRoot = view.findViewById(R.id.assistantRoot)
        defaultBorderColor = Color.parseColor("#2A2047")

        tvUserSpeech = view.findViewById(R.id.tvUserSpeech)
        tvAssistantReply = view.findViewById(R.id.tvAssistantReply)
        tvAssistantHint = view.findViewById(R.id.tvAssistantHint)

        assistantRoot.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapTime <= doubleTapWindowMs) {
                onDoubleTapCancel?.invoke()
            }
            lastTapTime = now
        }

    }
    fun setOnDoubleTapCancelListener(listener: (() -> Unit)?) {
        onDoubleTapCancel = listener
    }
    fun showAssistantHint(text: String) {
        tvAssistantHint.text = text
    }

    fun clearHint() {
        tvAssistantHint.text = ""
    }
    fun setIdleState() {
        stopStateAnimation()
        tvState.text = "Assistant ready"
    }

    fun setListeningState() {
        tvState.text = "Listening..."
        startListeningAnimation()
    }

    fun setProcessingState() {
        tvState.text = "Processing..."
        startProcessingAnimation()
    }

    fun setErrorState(text: String) {
        stopStateAnimation()
        tvState.text = text
    }

    fun setSpeakingState() {
        tvState.text = "Speaking..."
        startSpeakingAnimation()
    }
    fun showUserSpeech(text: String) {
        tvUserSpeech.text = text
    }

    fun showAssistantReply(text: String) {
        tvAssistantReply.text = text
    }

    fun clearConversation() {
        tvUserSpeech.text = ""
        tvAssistantReply.text = ""
        tvAssistantHint.text = ""
    }

    // helper functions

    private fun stopStateAnimation() {
        stateAnimator?.cancel()
        stateAnimator = null
        stateContainer.setBackgroundColor(defaultStateColor)
        updatePanelBorder(defaultBorderColor)
    }

    private fun startListeningAnimation() {
        stopStateAnimation()

        stateAnimator = ValueAnimator.ofObject(
            ArgbEvaluator(),
            android.graphics.Color.parseColor("#7A5C00"),
            android.graphics.Color.parseColor("#FFD54F")
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
            android.graphics.Color.parseColor("#7F1D1D"),
            android.graphics.Color.parseColor("#FF5252")
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
            android.graphics.Color.parseColor("#1B5E20"),
            android.graphics.Color.parseColor("#66BB6A")
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