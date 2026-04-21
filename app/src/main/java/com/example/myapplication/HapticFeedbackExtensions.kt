package com.example.myapplication

import android.view.HapticFeedbackConstants
import android.view.View

fun View.performTapHapticFeedback() {
    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}

inline fun View.setOnClickListenerWithHaptic(crossinline onClick: (View) -> Unit) {
    setOnClickListener { view ->
        view.performTapHapticFeedback()
        onClick(view)
    }
}
