package com.example.myapplication

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

fun View.performTapHapticFeedback() {
    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}

fun View.performLongClickHapticFeedback() {
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}

fun View.performConfirmationHapticFeedback() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    } else {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

inline fun View.setOnClickListenerWithHaptic(crossinline onClick: (View) -> Unit) {
    setOnClickListener { view ->
        view.performTapHapticFeedback()
        onClick(view)
    }
}
