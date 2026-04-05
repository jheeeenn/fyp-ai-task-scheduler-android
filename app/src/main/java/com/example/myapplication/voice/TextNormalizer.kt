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
        "set title by " to "set title buy ",
        "tmr" to "tomorrow",
        "tommorow" to "tomorrow",
        "moning" to "morning",
        "2 o clock" to "2 pm"
    )

    fun normalize(raw: String): String {
        var text = raw.lowercase().trim()

        // Normalize punctuated AM/PM forms:
        // "p. m." -> "pm", "a.m." -> "am", etc.
        text = text.replace(Regex("\\ba\\.?\\s*m\\.?\\b"), "am")
        text = text.replace(Regex("\\bp\\.?\\s*m\\.?\\b"), "pm")

        // normalize "2pm" -> "2 pm"
        text = text.replace(Regex("\\b(\\d{1,2})(am|pm)\\b")) { match ->
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