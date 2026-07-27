package com.example.myapplication.ai.routine

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class RoutineExtractionParseException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class RoutineExtractionResponseParser {
    fun parse(rawResponse: String): RoutineExtractionResponse {
        val content = extractMessageContentIfEnvelope(rawResponse).trim()
        val jsonText = extractFirstJsonObject(stripMarkdownFences(content))
        val json = try {
            JSONObject(jsonText)
        } catch (e: JSONException) {
            throw RoutineExtractionParseException("Invalid routine-extraction JSON", e)
        }

        requireExactFields(json, ROOT_FIELDS, "Routine extraction")
        val title = requireString(json, "routine_title", "Routine extraction")
        val confidence = requireNumber(json, "confidence", "Routine extraction")
        if (!confidence.isFinite() || confidence !in 0.0..1.0) {
            throw RoutineExtractionParseException("Routine extraction confidence is out of range")
        }
        val needClarification =
            requireBoolean(json, "need_clarification", "Routine extraction")
        val stepsJson = json.optJSONArray("steps")
            ?: throw RoutineExtractionParseException("Routine extraction steps must be an array")
        if (stepsJson.length() !in MIN_STEPS..MAX_STEPS) {
            throw RoutineExtractionParseException(
                "Routine extraction requires $MIN_STEPS to $MAX_STEPS steps"
            )
        }

        val steps = (0 until stepsJson.length()).map { index ->
            val step = stepsJson.optJSONObject(index)
                ?: throw RoutineExtractionParseException(
                    "Routine extraction step ${index + 1} must be an object"
                )
            requireExactFields(step, STEP_FIELDS, "Routine extraction step ${index + 1}")
            RoutineStepExtraction(
                title = requireString(step, "title", "Routine extraction step ${index + 1}"),
                dateText = requireString(
                    step,
                    "date_text",
                    "Routine extraction step ${index + 1}"
                ),
                timeText = requireString(
                    step,
                    "time_text",
                    "Routine extraction step ${index + 1}"
                )
            )
        }

        return RoutineExtractionResponse(
            routineTitle = title,
            steps = steps,
            confidence = confidence,
            needClarification = needClarification
        )
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
        if (!text.startsWith("```")) return text
        text = text.removePrefix("```").trimStart()
        if (text.startsWith("json", ignoreCase = true)) {
            text = text.drop(4).trimStart()
        }
        val lastFence = text.lastIndexOf("```")
        return if (lastFence >= 0) text.substring(0, lastFence).trim() else text
    }

    private fun extractFirstJsonObject(text: String): String {
        val start = text.indexOf('{')
        if (start < 0) {
            throw RoutineExtractionParseException(
                "Routine extraction did not contain a JSON object"
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
        throw RoutineExtractionParseException(
            "Routine extraction contained an incomplete JSON object"
        )
    }

    private fun requireExactFields(
        json: JSONObject,
        expected: Set<String>,
        label: String
    ) {
        val actual = json.keys().asSequence().toSet()
        val missing = expected - actual
        if (missing.isNotEmpty()) {
            throw RoutineExtractionParseException(
                "$label is missing required fields: ${missing.sorted().joinToString()}"
            )
        }
        val additional = actual - expected
        if (additional.isNotEmpty()) {
            throw RoutineExtractionParseException(
                "$label contains additional fields: ${additional.sorted().joinToString()}"
            )
        }
    }

    private fun requireString(json: JSONObject, field: String, label: String): String {
        val value = json.opt(field)
        if (value !is String) {
            throw RoutineExtractionParseException("$label field '$field' must be a string")
        }
        return value
    }

    private fun requireNumber(json: JSONObject, field: String, label: String): Double {
        val value = json.opt(field)
        if (value !is Number) {
            throw RoutineExtractionParseException("$label field '$field' must be a number")
        }
        return value.toDouble()
    }

    private fun requireBoolean(json: JSONObject, field: String, label: String): Boolean {
        val value = json.opt(field)
        if (value !is Boolean) {
            throw RoutineExtractionParseException("$label field '$field' must be a boolean")
        }
        return value
    }

    private companion object {
        const val MIN_STEPS = 2
        const val MAX_STEPS = 5
        val ROOT_FIELDS = setOf(
            "routine_title",
            "steps",
            "confidence",
            "need_clarification"
        )
        val STEP_FIELDS = setOf("title", "date_text", "time_text")
    }
}
