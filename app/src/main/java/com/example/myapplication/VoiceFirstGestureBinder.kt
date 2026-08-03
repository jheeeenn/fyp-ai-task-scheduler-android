package com.example.myapplication

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

object VoiceFirstGestureBinder {
    @SuppressLint("ClickableViewAccessibility")
    fun bindAction(
        view: View,
        speechProvider: () -> String?,
        speak: (String) -> Unit,
        activate: () -> Unit,
        singleTapHaptic: (View) -> Unit = { it.performTapHapticFeedback() },
        doubleTapHaptic: ((View) -> Unit)? = { it.performConfirmationHapticFeedback() }
    ) {
        view.isFocusable = true
        view.setOnClickListener { activate() }
        bind(
            view = view,
            interaction = VoiceFirstTouchInteraction(
                singleTap = {
                    singleTapHaptic(view)
                    speechProvider()?.takeIf { it.isNotBlank() }?.let(speak)
                },
                doubleTap = {
                    doubleTapHaptic?.invoke(view)
                    view.performClick()
                },
                longPress = { view.performLongClick() }
            )
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    fun bindInformation(
        view: View,
        speechProvider: () -> String?,
        speak: (String) -> Unit,
        tapHaptic: (View) -> Unit = { it.performTapHapticFeedback() }
    ) {
        view.isFocusable = true
        val readCurrentInformation = {
            speechProvider()?.takeIf { it.isNotBlank() }?.let(speak)
            Unit
        }
        view.setOnClickListener { readCurrentInformation() }
        bind(
            view = view,
            interaction = VoiceFirstTouchInteraction(
                singleTap = {
                    tapHaptic(view)
                    readCurrentInformation()
                },
                doubleTap = {
                    tapHaptic(view)
                    view.performClick()
                },
                longPress = { view.performLongClick() }
            )
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bind(view: View, interaction: VoiceFirstTouchInteraction) {
        val detector = GestureDetector(
            view.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(event: MotionEvent): Boolean =
                    interaction.onSingleTapConfirmed()

                override fun onDoubleTap(event: MotionEvent): Boolean =
                    interaction.onDoubleTap()

                override fun onLongPress(event: MotionEvent) {
                    interaction.onLongPress()
                }

                override fun onScroll(
                    firstEvent: MotionEvent?,
                    currentEvent: MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean = interaction.onScroll()
            }
        )
        view.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
    }
}
