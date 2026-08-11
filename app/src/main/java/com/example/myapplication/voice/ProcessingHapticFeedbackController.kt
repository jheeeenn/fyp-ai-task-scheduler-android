package com.example.myapplication.voice

import android.os.Handler
import android.os.Looper
import android.util.Log

internal interface ProcessingHapticScheduler {
    fun postDelayed(callback: Runnable, delayMs: Long)
    fun removeCallbacks(callback: Runnable)
}

internal class MainLooperProcessingHapticScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper())
) : ProcessingHapticScheduler {
    override fun postDelayed(callback: Runnable, delayMs: Long) {
        handler.postDelayed(callback, delayMs)
    }

    override fun removeCallbacks(callback: Runnable) {
        handler.removeCallbacks(callback)
    }
}

internal class ProcessingHapticFeedbackController(
    private val scheduler: ProcessingHapticScheduler,
    private val isEnabled: () -> Boolean,
    private val performPulse: () -> Boolean,
    private val initialDelayMs: Long = INITIAL_DELAY_MS,
    private val pulseIntervalMs: Long = PULSE_INTERVAL_MS,
    private val log: (String) -> Unit = { message -> Log.d(LOG_TAG, message) }
) {
    private var running = false
    private var destroyed = false
    private var acceptsStarts = true

    private val pulseCallback = object : Runnable {
        override fun run() {
            if (!running) return
            if (!isEnabled()) {
                stop(REASON_DISABLED)
                log("state=DISABLED")
                return
            }
            if (!performPulse()) {
                stop(REASON_UNAVAILABLE)
                return
            }

            log("state=PULSE")
            scheduler.postDelayed(this, pulseIntervalMs)
        }
    }

    fun start() {
        if (running || destroyed || !acceptsStarts) return
        if (!isEnabled()) {
            log("state=DISABLED")
            return
        }

        running = true
        log("state=START_DELAYED")
        scheduler.postDelayed(pulseCallback, initialDelayMs)
    }

    fun stop(reason: String) {
        scheduler.removeCallbacks(pulseCallback)
        if (!running) return

        running = false
        log("state=STOPPED reason=$reason")
    }

    fun destroy() {
        stop(REASON_SESSION_STOPPED)
        acceptsStarts = false
        destroyed = true
    }

    fun onLifecycleStarted() {
        if (!destroyed) acceptsStarts = true
    }

    fun onLifecycleStopped() {
        acceptsStarts = false
        stop(REASON_SESSION_STOPPED)
    }

    internal fun isRunningForTest(): Boolean = running

    companion object {
        const val INITIAL_DELAY_MS = 1_500L
        const val PULSE_INTERVAL_MS = 1_800L
        const val REASON_DISABLED = "DISABLED"
        const val REASON_UNAVAILABLE = "UNAVAILABLE"
        const val REASON_SESSION_STOPPED = "SESSION_STOPPED"
        private const val LOG_TAG = "PROCESSING_HAPTIC"
    }
}
