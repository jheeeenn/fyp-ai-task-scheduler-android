package com.example.myapplication.ai.conversation

import org.json.JSONObject

class ConversationDecisionParser {
    fun parse(rawContent: String, originalUserText: String): ConversationDecision {
        val jsonText = extractJsonObject(rawContent.trim())
        val json = JSONObject(jsonText)

        if (json.has("route")) {
            return parseConversationSchema(json)
        }

        if (json.has("action")) {
            return parseLegacyTaskAgentSchema(json, originalUserText)
        }

        return unknownDecision(json.optDouble("confidence", 0.0))
    }

    private fun extractJsonObject(trimmedContent: String): String {
        val firstObjectStart = trimmedContent.indexOf('{')
        val lastObjectEnd = trimmedContent.lastIndexOf('}')
        return if (firstObjectStart >= 0 && lastObjectEnd > firstObjectStart) {
            trimmedContent.substring(firstObjectStart, lastObjectEnd + 1)
        } else {
            trimmedContent
        }
    }

    private fun parseConversationSchema(json: JSONObject): ConversationDecision {
        val route = runCatching {
            ConversationRoute.valueOf(json.optString("route", "UNKNOWN"))
        }.getOrDefault(ConversationRoute.UNKNOWN)

        return ConversationDecision(
            route = route,
            taskText = json.optString("task_text", ""),
            reply = json.optString("reply", ""),
            confidence = json.optDouble("confidence", 0.0),
            listenAgain = json.optBoolean("listen_again", true)
        )
    }

    private fun parseLegacyTaskAgentSchema(
        json: JSONObject,
        originalUserText: String
    ): ConversationDecision {
        val action = json.optString("action", "").uppercase()
        val naturalResponse = json.optString("natural_response", "")
        val confidence = json.optDouble("confidence", 0.0)

        if (naturalResponse.isNotBlank() && originalTextLooksConversational(originalUserText)) {
            return ConversationDecision(
                route = ConversationRoute.DIRECT_REPLY,
                reply = naturalResponse,
                confidence = confidence,
                listenAgain = true
            )
        }

        if (originalTextLooksTaskRelated(originalUserText) && action.isNotBlank() && action != "UNKNOWN") {
            return ConversationDecision(
                route = ConversationRoute.TASK_COMMAND,
                taskText = originalUserText,
                confidence = confidence,
                listenAgain = true
            )
        }

        if (naturalResponse.isNotBlank()) {
            return ConversationDecision(
                route = ConversationRoute.DIRECT_REPLY,
                reply = naturalResponse,
                confidence = confidence,
                listenAgain = true
            )
        }

        return unknownDecision(confidence)
    }

    private fun unknownDecision(confidence: Double): ConversationDecision {
        return ConversationDecision(
            route = ConversationRoute.UNKNOWN,
            reply = "I can help with task scheduling. Try asking me to create, check, reschedule, delete, complete, or break down a task.",
            confidence = confidence,
            listenAgain = true
        )
    }

    private fun originalTextLooksConversational(text: String): Boolean {
        val normalized = text.lowercase()
        return CONVERSATIONAL_HINTS.any { normalized.contains(it) }
    }

    private fun originalTextLooksTaskRelated(text: String): Boolean {
        val normalized = text.lowercase()
        return TASK_RELATED_HINTS.any { normalized.contains(it) }
    }

    companion object {
        private val TASK_RELATED_HINTS = listOf(
            "remind me",
            "create task",
            "add task",
            "schedule",
            "what task",
            "what tasks",
            "tasks do i have",
            "do i have anything",
            "delete",
            "remove",
            "reschedule",
            "move",
            "change time",
            "mark",
            "as done",
            "complete",
            "undone",
            "break down",
            "split",
            "edit task",
            "update task"
        )

        private val CONVERSATIONAL_HINTS = listOf(
            "hello",
            "hi",
            "hey",
            "how are you",
            "what can you do",
            "help",
            "thanks",
            "thank you"
        )
    }
}
