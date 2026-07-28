package com.example.myapplication.ai.agent

import android.util.Log
import com.example.myapplication.diagnostics.DebugDiagnosticLog
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class TaskAgentParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

class TaskAgentResponseParser {
    fun parse(rawResponse: String): TaskAgentResponse {
        val content = extractMessageContentIfEnvelope(rawResponse).trim()
        val unfenced = stripMarkdownFences(content)
        val jsonText = extractFirstJsonObject(unfenced)

        val json = try {
            JSONObject(jsonText)
        } catch (e: JSONException) {
            DebugDiagnosticLog.longEvent(
                "TASK_AGENT_PARSE",
                "Invalid JSON from LM Studio: $jsonText; category=${e::class.java.simpleName}"
            )
            throw TaskAgentParseException("Invalid task-agent JSON", e)
        }

        listOf(
            "natural_response", "action", "task_title", "target_task_title", "date", "time",
            "target_date", "target_time", "new_date", "new_time", "recurrence", "priority",
            "query_presentation", "breakdown_target_preference", "confidence",
            "need_clarification", "missing_fields", "requires_confirmation", "plan"
        ).forEach { requireField(json, it) }

        val response = TaskAgentResponse(
            natural_response = json.optString("natural_response", ""),
            action = json.optString("action", ""),
            task_title = json.optString("task_title", ""),
            target_task_title = json.optString("target_task_title", ""),
            date = json.optString("date", ""),
            time = json.optString("time", ""),
            target_date = json.optString("target_date", ""),
            target_time = json.optString("target_time", ""),
            new_date = json.optString("new_date", ""),
            new_time = json.optString("new_time", ""),
            recurrence = json.optString("recurrence", ""),
            priority = json.optString("priority", ""),
            query_presentation = json.optString("query_presentation", ""),
            breakdown_target_preference =
                json.optString("breakdown_target_preference", ""),
            confidence = json.optDouble("confidence", 0.0).toFloat(),
            need_clarification = json.optBoolean("need_clarification", false),
            missing_fields = json.optJSONArray("missing_fields").toStringList(),
            requires_confirmation = json.optBoolean("requires_confirmation", false),
            plan = json.optJSONArray("plan").toStrictStringList()
        )

        Log.d("TASK_AGENT_PARSE", "action=${response.action}, confidence=${response.confidence}")
        return response
    }

    private fun extractMessageContentIfEnvelope(rawResponse: String): String {
        return try {
            val root = JSONObject(rawResponse)
            val choices = root.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                choices.getJSONObject(0).getJSONObject("message").getString("content")
            } else {
                rawResponse
            }
        } catch (_: JSONException) {
            rawResponse
        }
    }

    private fun stripMarkdownFences(content: String): String {
        var text = content.trim()
        if (text.startsWith("```")) {
            text = text.removePrefix("```").trimStart()
            if (text.startsWith("json", ignoreCase = true)) {
                text = text.drop(4).trimStart()
            }
            val lastFence = text.lastIndexOf("```")
            if (lastFence >= 0) {
                text = text.substring(0, lastFence).trim()
            }
        }
        return text
    }

    private fun extractFirstJsonObject(text: String): String {
        val start = text.indexOf('{')
        if (start < 0) {
            throw TaskAgentParseException("Task-agent response did not contain a JSON object")
        }

        var depth = 0
        var inString = false
        var escaped = false

        for (index in start until text.length) {
            val char = text[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (char == '\\' && inString) {
                escaped = true
                continue
            }
            if (char == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                when (char) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return text.substring(start, index + 1)
                        }
                    }
                }
            }
        }

        throw TaskAgentParseException("Task-agent response contained an incomplete JSON object")
    }

    private fun requireField(json: JSONObject, fieldName: String) {
        if (!json.has(fieldName) || json.isNull(fieldName)) {
            throw TaskAgentParseException("Task-agent JSON missing required field '$fieldName'")
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optString(index).takeIf { it.isNotBlank() }
        }
    }

    private fun JSONArray?.toStrictStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { index ->
            val value = get(index)
            if (value !is String) {
                throw TaskAgentParseException(
                    "Task-agent plan entries must be title strings"
                )
            }
            value
        }
    }
}
