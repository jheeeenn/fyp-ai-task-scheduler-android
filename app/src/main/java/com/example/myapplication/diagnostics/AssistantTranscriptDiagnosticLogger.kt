package com.example.myapplication.diagnostics

import android.util.Log
import com.example.myapplication.BuildConfig

internal enum class AssistantTranscriptVisibility {
    FULL,
    REDACTED
}

internal object AssistantTranscriptLogPolicy {
    fun visibility(debugBuild: Boolean): AssistantTranscriptVisibility =
        if (debugBuild) AssistantTranscriptVisibility.FULL else AssistantTranscriptVisibility.REDACTED
}

internal object AssistantTranscriptLogFormatter {
    fun user(
        text: String,
        source: String,
        visibility: AssistantTranscriptVisibility
    ): String = buildString {
        append("role=USER\nsource=")
        append(source)
        appendContent(text, visibility)
    }

    fun assistant(
        text: String,
        listenAgain: Boolean,
        visibility: AssistantTranscriptVisibility
    ): String = buildString {
        append("role=ASSISTANT\ndelivery=SPEAK\nlistenAgain=")
        append(listenAgain)
        appendContent(text, visibility)
    }

    private fun StringBuilder.appendContent(
        text: String,
        visibility: AssistantTranscriptVisibility
    ) {
        when (visibility) {
            AssistantTranscriptVisibility.FULL -> append("\ntext=").append(text)
            AssistantTranscriptVisibility.REDACTED -> {
                append("\ncontent=REDACTED\ncharacterCount=").append(text.length)
            }
        }
    }
}

internal object AssistantTranscriptDiagnosticLogger {
    private val visibility = AssistantTranscriptLogPolicy.visibility(BuildConfig.DEBUG)

    fun user(text: String, source: String) {
        log(AssistantTranscriptLogFormatter.user(text, source, visibility))
    }

    fun assistant(text: String, listenAgain: Boolean) {
        log(AssistantTranscriptLogFormatter.assistant(text, listenAgain, visibility))
    }

    private fun log(message: String) {
        if (visibility == AssistantTranscriptVisibility.FULL) {
            DebugDiagnosticLog.longEvent("ASSISTANT_TRANSCRIPT", message)
        } else {
            Log.d("ASSISTANT_TRANSCRIPT", message)
        }
    }
}
