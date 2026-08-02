package com.example.myapplication.accessibility

import android.util.Log
import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import java.util.Locale
import java.util.WeakHashMap

object AccessibilityStateHelper {
    private val assistantStates = WeakHashMap<View, AssistantAccessibilityState>()

    fun updateStateDescription(view: View, description: String) {
        ViewCompat.setStateDescription(view, description)
    }

    fun markHeading(view: View) {
        ViewCompat.setAccessibilityHeading(view, true)
    }

    fun updateAssistantState(
        view: View,
        state: AssistantAccessibilityState,
        announce: Boolean
    ): Boolean {
        val previous = assistantStates[view]
        ViewCompat.setStateDescription(view, state.label)
        assistantStates[view] = state
        if (announce && previous != state && view.isAttachedToWindow) {
            view.announceForAccessibility(state.label)
        }
        return previous != state
    }

    fun exposeTypedInputAction(view: View) {
        ViewCompat.replaceAccessibilityAction(
            view,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
            "Type to Assistant"
        ) { target, _ ->
            target.performLongClick()
            true
        }
    }

    fun isScreenReaderActive(view: View): Boolean {
        val manager = view.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.isEnabled && manager.isTouchExplorationEnabled
    }
}

object AccessibilityAnnouncementHelper {
    fun announce(view: View, screen: String, event: String, message: String) {
        Log.d(
            "ACCESSIBILITY_ANNOUNCEMENT",
            "screen=${safeToken(screen)} event=${safeToken(event)}"
        )
        if (view.isAttachedToWindow) {
            view.announceForAccessibility(message)
        }
    }

    fun logState(screen: String, element: String, state: String) {
        Log.d(
            "ACCESSIBILITY_STATE",
            "screen=${safeToken(screen)} element=${safeToken(element)} state=${safeToken(state)}"
        )
    }

    fun diagnosticLine(screen: String, event: String): String =
        "screen=${safeToken(screen)} event=${safeToken(event)}"

    private fun safeToken(value: String): String =
        value.uppercase(Locale.ROOT)
            .map { character ->
                if (character.isLetterOrDigit() || character == '_') character else '_'
            }
            .joinToString(separator = "")
            .take(40)
}
