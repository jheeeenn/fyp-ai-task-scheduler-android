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
        return normalizeTodayTimeLabel(raw)
            .split(" ")
            .joinToString(" ") { word -> formatWord(word) }
    }

    private fun normalizeTodayTimeLabel(raw: String): String {
        val lower = raw.lowercase(Locale.UK)
        return when {
            lower == "today morning" -> "this morning"
            lower == "today afternoon" -> "this afternoon"
            lower == "today evening" -> "this evening"
            lower == "today night" || lower == "today tonight" || lower == "tonight" -> "tonight"
            else -> raw
        }
    }

    private fun formatWord(word: String): String {
        val lower = word.lowercase(Locale.UK)
        return when {
            lower in monthNameWords -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.UK) else it.toString() }
            lower == "am" || lower == "pm" -> lower.uppercase(Locale.UK)
            else -> word
        }
    }
}
