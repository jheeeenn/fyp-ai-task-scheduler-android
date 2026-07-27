package com.example.myapplication.diagnostics

import android.util.Log
import com.example.myapplication.BuildConfig

/**
 * Debug-build-only diagnostics for values that may contain user or task data.
 *
 * Release builds keep using the existing count-only/state-only logs. Long payloads are split
 * here so Logcat preserves the complete diagnostic transcript in its original order.
 */
object DebugDiagnosticLog {
    internal const val MAX_PAYLOAD_CHARS = 3_000

    fun event(tag: String, message: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(tag, message)
    }

    fun longEvent(tag: String, message: String) {
        if (!BuildConfig.DEBUG) return
        val chunks = chunk(message)
        if (chunks.size == 1) {
            Log.d(tag, chunks.single())
            return
        }
        chunks.forEachIndexed { index, payload ->
            Log.d(tag, "chunk=${index + 1}/${chunks.size}\n$payload")
        }
    }

    internal fun chunk(
        message: String,
        maximumPayloadChars: Int = MAX_PAYLOAD_CHARS
    ): List<String> {
        require(maximumPayloadChars > 0)
        if (message.length <= maximumPayloadChars) return listOf(message)
        return message.chunked(maximumPayloadChars)
    }
}
