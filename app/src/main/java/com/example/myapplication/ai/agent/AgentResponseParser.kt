package com.example.myapplication.ai.agent

import org.json.JSONArray
import org.json.JSONObject

class AgentResponseParser {
    fun parse(raw: String): AgentResponse? {
        val jsonText = extractJsonObject(raw) ?: return null
        val root = JSONObject(jsonText)
        val structured = root.optJSONObject("structured_action") ?: root.optJSONObject("structuredAction") ?: return null

        val action = parseAction(structured.optString("action", "UNKNOWN"))
        val response = root.optString("natural_response", root.optString("naturalResponse", "")).trim()

        val structuredAction = StructuredAction(
            action = action,
            taskTitle = structured.optCleanString("task_title") ?: structured.optCleanString("taskTitle"),
            targetTaskTitle = structured.optCleanString("target_task_title") ?: structured.optCleanString("targetTaskTitle"),
            dateText = structured.optCleanString("date") ?: structured.optCleanString("dateText"),
            timeText = structured.optCleanString("time") ?: structured.optCleanString("timeText"),
            recurrence = structured.optCleanString("recurrence"),
            priority = structured.optCleanString("priority"),
            confidence = structured.optDouble("confidence", 0.0).toFloat(),
            needClarification = structured.optBoolean("need_clarification", structured.optBoolean("needClarification", false)),
            missingFields = parseStringArray(structured.optJSONArray("missing_fields") ?: structured.optJSONArray("missingFields")),
            requiresConfirmation = structured.optBoolean("requires_confirmation", structured.optBoolean("requiresConfirmation", false)),
            source = "gemma"
        )

        return AgentResponse(
            structuredAction = structuredAction,
            naturalResponse = response.ifBlank { fallbackNaturalResponse(structuredAction) },
            rawResponse = raw
        )
    }

    private fun JSONObject.optCleanString(name: String): String? {
        return optString(name, "").trim().takeIf { it.isNotBlank() && it.lowercase() != "null" }
    }

    private fun parseStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return List(array.length()) { index -> array.optString(index) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun parseAction(value: String): AgentActionType {
        return AgentActionType.entries.firstOrNull { it.name == value.trim().uppercase() }
            ?: AgentActionType.UNKNOWN
    }

    private fun extractJsonObject(raw: String): String? {
        val cleaned = raw
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .trim()

        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return cleaned.substring(start, end + 1)
    }

    private fun fallbackNaturalResponse(action: StructuredAction): String {
        return when (action.action) {
            AgentActionType.CLARIFY -> "I need a bit more information."
            AgentActionType.CANCEL -> "Okay, cancelled."
            AgentActionType.UNKNOWN -> "Sorry, I could not understand that."
            else -> "Okay, I understood your request."
        }
    }
}
