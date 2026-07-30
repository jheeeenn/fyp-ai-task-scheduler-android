package com.example.myapplication.ai.conversation.suggestion

import org.json.JSONObject

class ContextSuggestionSchemaException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class ContextSuggestionDecisionParser {
    fun parse(rawContent: String): ContextSuggestionDecision {
        if (rawContent.isBlank()) {
            throw ContextSuggestionSchemaException("Context suggestion response is blank")
        }
        val json = try {
            JSONObject(rawContent.trim())
        } catch (e: Exception) {
            throw ContextSuggestionSchemaException(
                "Context suggestion response is not valid JSON",
                e
            )
        }
        val keys = json.keys().asSequence().toSet()
        if (keys != REQUIRED_FIELDS) {
            throw ContextSuggestionSchemaException(
                "Context suggestion response fields do not match the strict contract"
            )
        }
        REQUIRED_STRING_FIELDS.forEach { field ->
            if (json.get(field) !is String) {
                throw ContextSuggestionSchemaException(
                    "Context suggestion field '$field' must be a string"
                )
            }
        }
        if (json.get("confidence") !is Number) {
            throw ContextSuggestionSchemaException(
                "Context suggestion field 'confidence' must be a number"
            )
        }
        val typeText = json.getString("suggestion_type")
        val type = try {
            ContextSuggestionType.valueOf(typeText)
        } catch (e: IllegalArgumentException) {
            throw ContextSuggestionSchemaException(
                "Unknown context suggestion type: $typeText",
                e
            )
        }
        val confidence = json.getDouble("confidence")
        if (!confidence.isFinite() || confidence !in 0.0..1.0) {
            throw ContextSuggestionSchemaException(
                "Context suggestion confidence is out of range"
            )
        }
        return ContextSuggestionDecision(
            suggestionType = type,
            primaryRef = json.getString("primary_ref"),
            secondaryRef = json.getString("secondary_ref"),
            confidence = confidence
        )
    }

    private companion object {
        val REQUIRED_FIELDS = setOf(
            "suggestion_type",
            "primary_ref",
            "secondary_ref",
            "confidence"
        )
        val REQUIRED_STRING_FIELDS = setOf(
            "suggestion_type",
            "primary_ref",
            "secondary_ref"
        )
    }
}
