package com.example.myapplication.ai

class LocalTaskParser {

    private val localDateParser = LocalDateParser()
    private val twelveHourPattern = Regex("""\b\d{1,2}(?:(?::|\s)\d{2})?\s?(am|pm)\b""")
    private val twentyFourHourPattern = Regex("""\b\d{1,2}(?::|\s)\d{2}\b""")
    private val quarterPastPattern = Regex("""\bquarter\s+past\s+\d{1,2}\s?(am|pm)\b""")
    private val halfPastPattern = Regex("""\bhalf\s+past\s+\d{1,2}\s?(am|pm)\b""")
    private val quarterToPattern = Regex("""\bquarter\s+to\s+\d{1,2}\s?(am|pm)\b""")
    private val oClockPattern = Regex("""\b\d{1,2}\s*o'?clock\s?(am|pm)\b""")

    fun parse(normalizedText: String, localIntentResult: LocalIntentResult): AiParsedCommand {
        return when (localIntentResult.intent) {
            AiIntent.CREATE_TASK -> parseCreateTask(normalizedText, localIntentResult)
            AiIntent.RESCHEDULE_TASK -> parseRescheduleTask(normalizedText, localIntentResult)
            AiIntent.DELETE_TASK -> parseDeleteTask(normalizedText, localIntentResult)
            AiIntent.QUERY_TASK -> AiParsedCommand(
                intent = AiIntent.QUERY_TASK.name,
                confidence = localIntentResult.confidence,
                source = "local"
            )
            /*AiIntent.UPDATE_TASK -> AiParsedCommand(
                intent = AiIntent.UPDATE_TASK.name,
                taskTitle = normalizedText,
                confidence = localIntentResult.confidence,
                source = "local"
            )*/
            AiIntent.UPDATE_TASK -> parseUpdateTask(normalizedText, localIntentResult)

            AiIntent.MARK_DONE -> parseMarkDoneTask(normalizedText, localIntentResult)
            AiIntent.MARK_UNDONE -> parseMarkUndoneTask(normalizedText, localIntentResult)

            AiIntent.UNKNOWN -> AiParsedCommand(
                intent = AiIntent.UNKNOWN.name,
                confidence = localIntentResult.confidence,
                source = "local"
            )
        }
    }

    private fun parseCreateTask(
        normalizedText: String,
        localIntentResult: LocalIntentResult
    ): AiParsedCommand {
        var working = normalizedText

        // Remove leading create-task phrases
        working = removeLeadingPattern(
            working,
            listOf(
                "create a task",
                "create task",
                "add a task",
                "add task",
                "new task",
                "set a reminder",
                "remind me",
                "i need to remember"
            )
        )
        working = working
            .replace(Regex("""^create\s+us\b"""), "")
            .replace(Regex("""^create\s+uh\b"""), "")
            .replace(Regex("""^create\s+the\b"""), "")
            .replace(Regex("""^create\s+a\b"""), "")
            .trim()

        // Extract date
        var extractedDate: String? = null
        val dateResult = localDateParser.extractFromSentence(working)
        if (dateResult.success && !dateResult.matchedPhrase.isNullOrBlank()) {
            extractedDate = dateResult.matchedPhrase
            working = working.replace(dateResult.matchedPhrase!!, " ").trim()
        }

        // Extract time
        var extractedTime: String? = null

        val semanticTimePatterns = listOf(
            "after breakfast",
            "morning",
            "this morning",
            "before lunch",
            "at lunch",
            "after lunch",
            "noon",
            "afternoon",
            "this afternoon",
            "after class",
            "evening",
            "after dinner",
            "tonight"
        )

        val semanticMatch = semanticTimePatterns.firstOrNull { phrase ->
            working.contains(phrase)
        }

        if (semanticMatch != null) {
            extractedTime = semanticMatch
            working = working.replace(semanticMatch, " ").trim()
        } else {
            val extracted = extractClockTimePhrase(working)
            extractedTime = extracted.first
            working = extracted.second
        }

        // Clean leftover connector words
        working = working
            .replace(Regex("""\bto\b"""), " ")
            .replace(Regex("""\bat\b"""), " ")
            .replace(Regex("""\bon\b"""), " ")
            .replace(Regex("""\bfor\b"""), " ")
            .replace(Regex("""\bthe\b"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val extractedTitle = working.ifBlank { null }

        return AiParsedCommand(
            intent = AiIntent.CREATE_TASK.name,
            taskTitle = extractedTitle,
            dateText = extractedDate,
            timeText = extractedTime,
            confidence = localIntentResult.confidence,
            source = "local"
        )
    }

    private fun parseRescheduleTask(
        normalizedText: String,
        localIntentResult: LocalIntentResult
    ): AiParsedCommand {
        var working = normalizedText

        working = removeLeadingPattern(
            working,
            listOf(
                "reschedule my task",
                "reschedule task",
                "reschedule my reminder",
                "reschedule reminder",
                "reschedule",
                "move my task",
                "move task",
                "move my reminder",
                "move reminder",
                "move",
                "change my task",
                "change task"
            )
        )

        var extractedDate: String? = null
        val dateResult = localDateParser.extractFromSentence(working)
        if (dateResult.success && !dateResult.matchedPhrase.isNullOrBlank()) {
            extractedDate = dateResult.matchedPhrase
            working = working.replace(dateResult.matchedPhrase!!, " ").trim()
        }

        var extractedTime: String? = null

        val semanticTimePatterns = listOf(
            "after breakfast",
            "morning",
            "this morning",
            "before lunch",
            "at lunch",
            "after lunch",
            "noon",
            "afternoon",
            "this afternoon",
            "after class",
            "evening",
            "after dinner",
            "tonight"
        )

        val semanticMatch = semanticTimePatterns.firstOrNull { phrase ->
            working.contains(phrase)
        }

        if (semanticMatch != null) {
            extractedTime = semanticMatch
            working = working.replace(semanticMatch, " ").trim()
        } else {
            val extracted = extractClockTimePhrase(working)
            extractedTime = extracted.first
            working = extracted.second
        }

        working = working
            .replace(Regex("""^\bmy\b\s*"""), "")
            .replace(Regex("""^\bthe\b\s*"""), "")
            .replace(Regex("""\bto\b"""), " ")
            .replace(Regex("""\bon\b"""), " ")
            .replace(Regex("""\bat\b"""), " ")
            .replace(Regex("""\btask\b$"""), "")
            .replace(Regex("""\breminder\b$"""), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        val targetTitle = working.ifBlank { null }

        return AiParsedCommand(
            intent = AiIntent.RESCHEDULE_TASK.name,
            targetTaskTitle = targetTitle,
            dateText = extractedDate,
            timeText = extractedTime,
            confidence = localIntentResult.confidence,
            source = "local"
        )
    }

    private fun parseUpdateTask(
        normalizedText: String,
        localIntentResult: LocalIntentResult
    ): AiParsedCommand {
        var working = normalizedText

        working = removeLeadingPattern(
            working,
            listOf(
                "edit task",
                "edit my task",
                "edit",
                "update task",
                "update my task",
                "update",
                "change task",
                "change my task"
            )
        )
        working = working
            .replace(Regex("""^\bmy\b\s*"""), "")
            .replace(Regex("""^\bthe\b\s*"""), "")
            .replace(Regex("""\btask\b$"""), "")
            .replace(Regex("""\breminder\b$"""), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        var extractedDate: String? = null
        val dateResult = localDateParser.extractFromSentence(working)
        if (dateResult.success && !dateResult.matchedPhrase.isNullOrBlank()) {
            extractedDate = dateResult.matchedPhrase
            working = working.replace(dateResult.matchedPhrase!!, " ").trim()
        }

        var extractedTime: String? = null

        val semanticTimePatterns = listOf(
            "after breakfast",
            "morning",
            "this morning",
            "before lunch",
            "at lunch",
            "after lunch",
            "noon",
            "afternoon",
            "this afternoon",
            "after class",
            "evening",
            "after dinner",
            "tonight"
        )

        val semanticMatch = semanticTimePatterns.firstOrNull { phrase ->
            working.contains(phrase)
        }

        if (semanticMatch != null) {
            extractedTime = semanticMatch
            working = working.replace(semanticMatch, " ").trim()
        } else {
            val extracted = extractClockTimePhrase(working)
            extractedTime = extracted.first
            working = extracted.second
        }

        working = working
            .replace(Regex("""\bchange time to\b"""), " ")
            .replace(Regex("""\bchange date to\b"""), " ")
            .replace(Regex("""\brename\b"""), " ")
            .replace(Regex("""\bto\b"""), " ")
            .replace(Regex("""\bon\b"""), " ")
            .replace(Regex("""\bat\b"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val targetTitle = working.ifBlank { null }

        return AiParsedCommand(
            intent = AiIntent.UPDATE_TASK.name,
            targetTaskTitle = targetTitle,
            dateText = extractedDate,
            timeText = extractedTime,
            confidence = localIntentResult.confidence,
            source = "local"
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

    private fun extractClockTimePhrase(text: String): Pair<String?, String> {
        val regexes = listOf(
            twelveHourPattern,
            twentyFourHourPattern,
            quarterPastPattern,
            halfPastPattern,
            quarterToPattern,
            oClockPattern
        )

        for (regex in regexes) {
            val match = regex.find(text) ?: continue
            val value = match.value.trim()
            val updated = text.replace(match.value, " ").replace(Regex("\\s+"), " ").trim()
            return Pair(value, updated)
        }

        return Pair(null, text)
    }

    private fun parseMarkDoneTask(
        normalizedText: String,
        localIntentResult: LocalIntentResult
    ): AiParsedCommand {
        var working = normalizedText

        working = removeLeadingPattern(
            working,
            listOf(
                "mark my task as done",
                "mark task as done",
                "mark my reminder as done",
                "mark reminder as done",
                "mark as done",
                "mark done",
                "complete my task",
                "complete task",
                "complete my reminder",
                "complete reminder",
                "complete",
                "finish my task",
                "finish task",
                "finish my reminder",
                "finish reminder",
                "finish"
            )
        )

        working = working
            .replace(Regex("""^\bmy\b\s*"""), "")
            .replace(Regex("""^\bthe\b\s*"""), "")
            .replace(Regex("""\btask\b$"""), "")
            .replace(Regex("""\breminder\b$"""), "")
            .replace(Regex("""\bas done\b"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val targetTitle = working.ifBlank { null }

        return AiParsedCommand(
            intent = AiIntent.MARK_DONE.name,
            targetTaskTitle = targetTitle,
            confidence = localIntentResult.confidence,
            source = "local"
        )
    }

    private fun parseMarkUndoneTask(
        normalizedText: String,
        localIntentResult: LocalIntentResult
    ): AiParsedCommand {
        var working = normalizedText

        working = removeLeadingPattern(
            working,
            listOf(
                "mark my task as undone",
                "mark task as undone",
                "mark my reminder as undone",
                "mark reminder as undone",
                "mark as undone",
                "mark undone",
                "mark my task as not done",
                "mark task as not done",
                "mark my reminder as not done",
                "mark reminder as not done",
                "reopen my task",
                "reopen task",
                "reopen my reminder",
                "reopen reminder",
                "reopen"
            )
        )

        working = working
            .replace(Regex("""^\bmy\b\s*"""), "")
            .replace(Regex("""^\bthe\b\s*"""), "")
            .replace(Regex("""\btask\b$"""), "")
            .replace(Regex("""\breminder\b$"""), "")
            .replace(Regex("""\bas undone\b"""), " ")
            .replace(Regex("""\bas not done\b"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val targetTitle = working.ifBlank { null }

        return AiParsedCommand(
            intent = AiIntent.MARK_UNDONE.name,
            targetTaskTitle = targetTitle,
            confidence = localIntentResult.confidence,
            source = "local"
        )
    }


    private fun parseDeleteTask(
        normalizedText: String,
        localIntentResult: LocalIntentResult
    ): AiParsedCommand {
        var working = normalizedText

        working = removeLeadingPattern(
            working,
            listOf(
                "delete my task",
                "delete task",
                "delete my reminder",
                "delete reminder",
                "delete",
                "remove my task",
                "remove task",
                "remove my reminder",
                "remove reminder",
                "remove"
            )
        )

        working = working
            .replace(Regex("""^\bmy\b\s*"""), "")
            .replace(Regex("""^\bthe\b\s*"""), "")
            .replace(Regex("""\btask\b$"""), "")
            .replace(Regex("""\breminder\b$"""), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        val targetTitle = working.ifBlank { null }

        return AiParsedCommand(
            intent = AiIntent.DELETE_TASK.name,
            targetTaskTitle = targetTitle,
            confidence = localIntentResult.confidence,
            source = "local"
        )
    }
}
