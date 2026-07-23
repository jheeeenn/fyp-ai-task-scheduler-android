package com.example.myapplication.ai.agent

import org.json.JSONException
import org.json.JSONObject

class ContextActionExtractionParseException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class ContextActionExtractionResponseParser {
    fun parse(rawContent: String): ContextActionExtractionResponse {
        if (rawContent.isBlank()) {
            throw ContextActionExtractionParseException("Context-action extraction returned blank content")
        }
        val jsonText = extractFirstJsonObject(stripMarkdownFences(rawContent))
        val json = try {
            JSONObject(jsonText)
        } catch (e: JSONException) {
            throw ContextActionExtractionParseException("Invalid context-action extraction JSON", e)
        }
        val keys = json.keys().asSequence().toSet()
        val missing = REQUIRED_FIELDS - keys
        val additional = keys - REQUIRED_FIELDS
        if (missing.isNotEmpty()) {
            throw ContextActionExtractionParseException(
                "Context-action extraction missing fields: ${missing.joinToString()}"
            )
        }
        if (additional.isNotEmpty()) {
            throw ContextActionExtractionParseException(
                "Context-action extraction contains additional fields: ${additional.joinToString()}"
            )
        }

        return ContextActionExtractionResponse(
            action = requireString(json, "action"),
            replacementTitle = requireString(json, "replacement_title"),
            newDate = requireString(json, "new_date"),
            newTime = requireString(json, "new_time"),
            confidence = requireNumber(json, "confidence"),
            needClarification = requireBoolean(json, "need_clarification")
        )
    }

    private fun stripMarkdownFences(content: String): String {
        var text = content.trim()
        if (text.startsWith("```")) {
            text = text.removePrefix("```").trimStart()
            if (text.startsWith("json", ignoreCase = true)) {
                text = text.drop(4).trimStart()
            }
            val lastFence = text.lastIndexOf("```")
            if (lastFence >= 0) text = text.substring(0, lastFence).trim()
        }
        return text
    }

    private fun extractFirstJsonObject(text: String): String {
        val start = text.indexOf('{')
        if (start < 0) {
            throw ContextActionExtractionParseException(
                "Context-action extraction did not contain a JSON object"
            )
        }
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val character = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
            } else {
                when (character) {
                    '"' -> inString = true
                    '{' -> depth += 1
                    '}' -> {
                        depth -= 1
                        if (depth == 0) return text.substring(start, index + 1)
                    }
                }
            }
        }
        throw ContextActionExtractionParseException(
            "Context-action extraction contained an incomplete JSON object"
        )
    }

    private fun requireString(json: JSONObject, field: String): String {
        val value = json.get(field)
        if (value !is String) {
            throw ContextActionExtractionParseException("Field '$field' must be a string")
        }
        return value
    }

    private fun requireNumber(json: JSONObject, field: String): Double {
        val value = json.get(field)
        if (value !is Number) {
            throw ContextActionExtractionParseException("Field '$field' must be a number")
        }
        return value.toDouble()
    }

    private fun requireBoolean(json: JSONObject, field: String): Boolean {
        val value = json.get(field)
        if (value !is Boolean) {
            throw ContextActionExtractionParseException("Field '$field' must be a boolean")
        }
        return value
    }

    private companion object {
        val REQUIRED_FIELDS = setOf(
            "action",
            "replacement_title",
            "new_date",
            "new_time",
            "confidence",
            "need_clarification"
        )
    }
}
