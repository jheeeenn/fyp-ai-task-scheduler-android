package com.example.myapplication.ai.temporal

import java.util.Locale

object TemporalQueryLabelFormatter {
    private val monthNameWords = setOf(
        "january", "february", "march", "april", "may", "june", "july", "august",
        "september", "october", "november", "december"
    )

    fun spokenLabel(window: TemporalQueryWindow): String? {
        if (window.status != TemporalResolutionStatus.RESOLVED) return null
        if (!window.hasDateConstraint && !window.hasTimeConstraint) return null
        val raw = window.spokenLabel.trim()
        if (raw.isBlank()) return null
        return raw.split(" ").joinToString(" ") { word ->
            if (word.lowercase(Locale.UK) in monthNameWords) {
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.UK) else it.toString() }
            } else {
                word
            }
        }
    }
}
