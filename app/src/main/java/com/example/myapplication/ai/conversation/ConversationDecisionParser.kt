package com.example.myapplication.ai.conversation

import org.json.JSONObject

class ConversationDecisionParser {
    fun parse(rawContent: String): ConversationDecision {
        val json = JSONObject(rawContent.trim())
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
}
