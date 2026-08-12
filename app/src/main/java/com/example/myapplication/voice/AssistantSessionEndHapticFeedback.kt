package com.example.myapplication.voice

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.content.ContextCompat

internal class AndroidSessionEndVibrationPerformer(context: Context) {
    private val vibrator =
        ContextCompat.getSystemService(context.applicationContext, Vibrator::class.java)

    fun vibrate(durationMs: Long): Boolean {
        val target = vibrator ?: return false
        if (!target.hasVibrator()) return false

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(
                    durationMs,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    target.vibrate(
                        effect,
                        VibrationAttributes.createForUsage(
                            VibrationAttributes.USAGE_ACCESSIBILITY
                        )
                    )
                } else {
                    target.vibrate(effect)
                }
            } else {
                @Suppress("DEPRECATION")
                target.vibrate(durationMs)
            }
            true
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    fun cancel() {
        try {
            vibrator?.cancel()
        } catch (_: SecurityException) {
        } catch (_: RuntimeException) {
        }
    }
}

internal class AssistantSessionEndHapticFeedback(
    private val isEnabled: () -> Boolean,
    private val performVibration: (Long) -> Boolean,
    private val cancelVibration: () -> Unit,
    private val durationMs: Long = DURATION_MS,
    private val log: (String) -> Unit = { message -> Log.d(LOG_TAG, message) }
) {
    private var lastClaimedSessionGeneration: Long? = null
    private var destroyed = false

    fun deliverOnce(sessionGeneration: Long) {
        if (destroyed || lastClaimedSessionGeneration == sessionGeneration) return
        lastClaimedSessionGeneration = sessionGeneration

        if (!isEnabled()) {
            log("state=DISABLED")
            return
        }

        val delivered = performVibration(durationMs)
        log(
            "state=${if (delivered) "DELIVERED" else "UNAVAILABLE"} " +
                "durationMs=$durationMs"
        )
    }

    fun cancelActive() {
        cancelVibration()
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        cancelActive()
    }

    companion object {
        const val DURATION_MS = 1_000L
        private const val LOG_TAG = "SESSION_END_HAPTIC"
    }
}
