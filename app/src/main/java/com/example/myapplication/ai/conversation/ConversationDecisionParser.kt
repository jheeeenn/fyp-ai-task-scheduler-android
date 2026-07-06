package com.example.myapplication.ai.conversation

import org.json.JSONObject

class ConversationDecisionParser {
    fun parse(rawContent: String): ConversationDecision {
        val jsonText = extractJsonObject(rawContent.trim())
        val json = JSONObject(jsonText)

        if (json.has("route")) {
            return parseConversationSchema(json)
        }

        if (json.has("action")) {
            return parseLegacyTaskAgentSchema(json)
        }

        return ConversationDecision(
            route = ConversationRoute.UNKNOWN,
            reply = "I can help with task scheduling. Try asking me to create, check, reschedule, delete, complete, or break down a task.",
            confidence = json.optDouble("confidence", 0.0),
            listenAgain = true
        )
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

    private fun parseLegacyTaskAgentSchema(json: JSONObject): ConversationDecision {
        val action = json.optString("action", "").uppercase()
        val naturalResponse = json.optString("natural_response", "")
        val confidence = json.optDouble("confidence", 0.0)

        return when {
            action == "UNKNOWN" && naturalResponse.isNotBlank() -> ConversationDecision(
                route = ConversationRoute.DIRECT_REPLY,
                reply = naturalResponse,
                confidence = confidence,
                listenAgain = true
            )
            action.isNotBlank() && action != "UNKNOWN" -> ConversationDecision(
                route = ConversationRoute.TASK_COMMAND,
                taskText = "",
                confidence = confidence,
                listenAgain = true
            )
            else -> ConversationDecision(
                route = ConversationRoute.UNKNOWN,
                reply = "I can help with task scheduling. Try asking me to create, check, reschedule, delete, complete, or break down a task.",
                confidence = confidence,
                listenAgain = true
            )
        }
    }
}
