package com.example.myapplication

import android.view.HapticFeedbackConstants
import android.view.View

fun View.performTapHapticFeedback() {
    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}

fun View.performLongClickHapticFeedback() {
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}

inline fun View.setOnClickListenerWithHaptic(crossinline onClick: (View) -> Unit) {
    setOnClickListener { view ->
        view.performTapHapticFeedback()
        onClick(view)
    }
}
