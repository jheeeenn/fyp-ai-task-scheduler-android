package com.example.myapplication.ai.conversation

import org.json.JSONObject

class ConversationSchemaException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class ConversationDecisionParser {
    fun parse(rawContent: String): ConversationDecision {
        if (rawContent.isBlank()) {
            throw ConversationSchemaException("Conversation Agent returned blank content")
        }

        val jsonText = extractFirstJsonObject(rawContent)
        val json = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            throw ConversationSchemaException("Conversation Agent content is not valid JSON", e)
        }

        rejectTaskAgentFields(json)
        requireExactFields(json)

        val routeText = requireString(json, "route")
        val route = try {
            ConversationRoute.valueOf(routeText)
        } catch (e: IllegalArgumentException) {
            throw ConversationSchemaException("Invalid ConversationDecision route: $routeText", e)
        }

        val confidence = requireNumber(json, "confidence")
        if (confidence < 0.0 || confidence > 1.0) {
            throw ConversationSchemaException("ConversationDecision confidence out of range: $confidence")
        }

        return ConversationDecision(
            route = route,
            taskText = requireString(json, "task_text"),
            reply = requireString(json, "reply"),
            confidence = confidence,
            listenAgain = requireBoolean(json, "listen_again")
        )
    }

    private fun extractFirstJsonObject(rawContent: String): String {
        val start = rawContent.indexOf('{')
        if (start < 0) {
            throw ConversationSchemaException("Conversation Agent content does not contain a JSON object")
        }

        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until rawContent.length) {
            val char = rawContent[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return rawContent.substring(start, index + 1)
                        }
                    }
                }
            }
        }

        throw ConversationSchemaException("Conversation Agent content contains an incomplete JSON object")
    }

    private fun rejectTaskAgentFields(json: JSONObject) {
        val forbiddenField = TASK_AGENT_FIELDS.firstOrNull { json.has(it) }
        if (forbiddenField != null) {
            throw ConversationSchemaException("ConversationDecision contains forbidden task-agent field: $forbiddenField")
        }
    }

    private fun requireExactFields(json: JSONObject) {
        val keys = json.keys().asSequence().toSet()
        val missing = REQUIRED_FIELDS.filterNot { keys.contains(it) }
        if (missing.isNotEmpty()) {
            throw ConversationSchemaException("ConversationDecision missing required fields: ${missing.joinToString()}")
        }

        val extra = keys.filterNot { REQUIRED_FIELDS.contains(it) }
        if (extra.isNotEmpty()) {
            throw ConversationSchemaException("ConversationDecision contains additional fields: ${extra.joinToString()}")
        }
    }

    private fun requireString(json: JSONObject, field: String): String {
        val value = json.get(field)
        if (value !is String) {
            throw ConversationSchemaException("ConversationDecision field '$field' must be a string")
        }
        return value
    }

    private fun requireNumber(json: JSONObject, field: String): Double {
        val value = json.get(field)
        if (value !is Number) {
            throw ConversationSchemaException("ConversationDecision field '$field' must be a number")
        }
        return value.toDouble()
    }

    private fun requireBoolean(json: JSONObject, field: String): Boolean {
        val value = json.get(field)
        if (value !is Boolean) {
            throw ConversationSchemaException("ConversationDecision field '$field' must be a boolean")
        }
        return value
    }

    companion object {
        private val REQUIRED_FIELDS = setOf(
            "route",
            "task_text",
            "reply",
            "confidence",
            "listen_again"
        )

        private val TASK_AGENT_FIELDS = setOf(
            "action",
            "natural_response",
            "task_title",
            "target_task_title",
            "date",
            "time",
            "recurrence",
            "priority",
            "missing_fields",
            "requires_confirmation",
            "plan"
        )
    }
}
