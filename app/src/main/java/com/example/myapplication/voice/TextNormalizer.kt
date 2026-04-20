package com.example.myapplication.voice

object TextNormalizer {

    private val fillerWords = listOf(
        "please",
        "can you",
        "could you",
        "uh",
        "um",
        "hey app",
        "assistant"
    )

    private val replacements = linkedMapOf(
        "tmr" to "tomorrow",
        "tommorow" to "tomorrow",
        "tomoro" to "tomorrow",
        "moning" to "morning",
        "2 o clock" to "2 pm",
        "read it all" to "read all",
        "rate of" to "read all",
        "reddit of" to "read all",
        "no need lah" to "no need"
    )

    fun normalize(raw: String): String {
        var text = raw.lowercase().trim()
        text = text.replace(Regex("[.,!?]+"), " ")

        // Normalize punctuated AM/PM forms:
        // "p. m." -> "pm", "a.m." -> "am", etc.
        text = text.replace(Regex("\\ba\\.?\\s*m\\.?\\b"), "am")
        text = text.replace(Regex("\\bp\\.?\\s*m\\.?\\b"), "pm")

        // normalize "2pm" -> "2 pm"
        text = text.replace(Regex("\\b(\\d{1,2})(am|pm)\\b")) { match ->
            "${match.groupValues[1]} ${match.groupValues[2]}"
        }

        // normalize "8:24pm" -> "8:24 pm"
        text = text.replace(Regex("\\b(\\d{1,2}:\\d{2})(am|pm)\\b")) { match ->
            "${match.groupValues[1]} ${match.groupValues[2]}"
        }

        fillerWords.forEach { filler ->
            text = text.replace(filler, " ")
        }

        replacements.forEach { (wrong, correct) ->
            text = text.replace(wrong, correct)
        }

        text = text.replace(Regex("\\s+"), " ").trim()
        return text
    }
}