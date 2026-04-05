// TO BE DISCARDED.................

package com.example.myapplication.voice

import com.example.myapplication.ai.LocalDateParser
object SlotExtractor {

    fun extract(normalizedText: String, intent: IntentType): ParsedCommand {
        return when (intent) {
            IntentType.SET_TITLE -> {
                val title = removeLeadingPattern(
                    normalizedText,
                    listOf("set title", "title", "change title")
                )

                ParsedCommand(
                    intent = intent,
                    title = title,
                    normalizedText = normalizedText,
                    confidence = if (!title.isNullOrBlank()) 0.85f else 0.4f
                )
            }

            IntentType.SET_DATE -> {
                val dateText = removeLeadingPattern(
                    normalizedText,
                    listOf("set date", "change date", "date")
                )

                ParsedCommand(
                    intent = intent,
                    dateText = dateText,
                    normalizedText = normalizedText,
                    confidence = if (!dateText.isNullOrBlank()) 0.85f else 0.4f
                )
            }

            IntentType.SET_TIME -> {
                val timeText = removeLeadingPattern(
                    normalizedText,
                    listOf("set time", "change time", "time")
                )

                ParsedCommand(
                    intent = intent,
                    timeText = timeText,
                    normalizedText = normalizedText,
                    confidence = if (!timeText.isNullOrBlank()) 0.85f else 0.4f
                )
            }

            IntentType.CREATE_TASK -> {
                extractCreateTaskSlots(normalizedText)
            }

            else -> {
                ParsedCommand(
                    intent = intent,
                    normalizedText = normalizedText,
                    confidence = 0.5f
                )
            }
        }
    }

    private fun extractCreateTaskSlots(text: String): ParsedCommand {
        var working = text

        val localDateParser = LocalDateParser()

        // Remove leading create-task phrases
        working = removeLeadingPattern(
            working,
            listOf(
                "remind me",
                "set a reminder",
                "create task",
                "add task",
                "new task",
                "i need to remember"
            )
        )

        // Date extraction using LocalDateParser
        var extractedDate: String? = null
        val dateResult = localDateParser.extractFromSentence(working)
        if (dateResult.success && !dateResult.matchedPhrase.isNullOrBlank()) {
            extractedDate = dateResult.matchedPhrase
            working = working.replace(dateResult.matchedPhrase!!, " ").trim()
        }

        // Time extraction
        var extractedTime: String? = null

        val twelveHourPattern = Regex("""\b\d{1,2}(:\d{2})?\s?(am|pm)\b""")
        val twentyFourHourPattern = Regex("""\b\d{1,2}(?::|\s)\d{2}\b""")

        val twelveMatch = twelveHourPattern.find(working)
        if (twelveMatch != null) {
            extractedTime = twelveMatch.value.trim()
            working = working.replace(twelveMatch.value, " ").trim()
        } else {
            val twentyFourMatch = twentyFourHourPattern.find(working)
            if (twentyFourMatch != null) {
                extractedTime = twentyFourMatch.value.trim()
                working = working.replace(twentyFourMatch.value, " ").trim()
            }
        }

        // Remove leftover connector words
        working = working
            .replace(Regex("""\bto\b"""), " ")
            .replace(Regex("""\bat\b"""), " ")
            .replace(Regex("""\bon\b"""), " ")
            .replace(Regex("""\bfor\b"""), " ")
            .replace(Regex("""\b[ap]m\b"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val extractedTitle = working.ifBlank { null }

        return ParsedCommand(
            intent = IntentType.CREATE_TASK,
            title = extractedTitle,
            dateText = extractedDate,
            timeText = extractedTime,
            normalizedText = text,
            confidence = 0.85f
        )
    }

    private fun removeLeadingPattern(text: String, patterns: List<String>): String {
        for (pattern in patterns.sortedByDescending { it.length }) {
            if (text.startsWith(pattern)) {
                return text.removePrefix(pattern).trim()
            }
        }
        return text.trim()
    }
}