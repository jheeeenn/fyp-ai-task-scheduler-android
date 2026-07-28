package com.example.myapplication.ai.routine.saved

import org.json.JSONObject

enum class SavedRoutineAction {
    LIST,
    READ_DETAILS,
    RUN,
    DELETE,
    UNKNOWN
}

data class SavedRoutineActionDecision(
    val action: SavedRoutineAction,
    val routineTitle: String,
    val dateText: String,
    val confidence: Double
)

class SavedRoutineActionSchemaException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class SavedRoutineActionParser {
    fun parse(rawContent: String): SavedRoutineActionDecision {
        if (rawContent.isBlank()) {
            throw SavedRoutineActionSchemaException("Saved-routine action returned blank content")
        }
        val jsonText = extractFirstJsonObject(rawContent)
        val json = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            throw SavedRoutineActionSchemaException("Saved-routine action is not valid JSON", e)
        }
        val keys = json.keys().asSequence().toSet()
        if (keys != REQUIRED_FIELDS) {
            throw SavedRoutineActionSchemaException(
                "Saved-routine action requires exactly ${REQUIRED_FIELDS.joinToString()}"
            )
        }
        val actionText = requireString(json, "action")
        val action = try {
            SavedRoutineAction.valueOf(actionText)
        } catch (e: IllegalArgumentException) {
            throw SavedRoutineActionSchemaException("Invalid saved-routine action: $actionText", e)
        }
        val title = requireString(json, "routine_title").trim()
        val date = requireString(json, "date_text").trim()
        val confidenceValue = json.get("confidence")
        if (confidenceValue !is Number) {
            throw SavedRoutineActionSchemaException("confidence must be a number")
        }
        val confidence = confidenceValue.toDouble()
        if (!confidence.isFinite() || confidence !in MIN_CONFIDENCE..1.0) {
            throw SavedRoutineActionSchemaException("confidence is below the accepted threshold")
        }
        val validFields = when (action) {
            SavedRoutineAction.LIST -> title.isEmpty() && date.isEmpty()
            SavedRoutineAction.READ_DETAILS,
            SavedRoutineAction.DELETE -> title.isNotEmpty() && date.isEmpty()
            SavedRoutineAction.RUN -> title.isNotEmpty()
            SavedRoutineAction.UNKNOWN -> title.isEmpty() && date.isEmpty()
        }
        if (!validFields) {
            throw SavedRoutineActionSchemaException("Invalid field combination for $action")
        }
        return SavedRoutineActionDecision(action, title, date, confidence)
    }

    private fun requireString(json: JSONObject, name: String): String {
        val value = json.get(name)
        if (value !is String) {
            throw SavedRoutineActionSchemaException("$name must be a string")
        }
        return value
    }

    private fun extractFirstJsonObject(raw: String): String {
        val start = raw.indexOf('{')
        if (start < 0) throw SavedRoutineActionSchemaException("Missing JSON object")
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until raw.length) {
            val char = raw[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '{' -> depth += 1
                    '}' -> {
                        depth -= 1
                        if (depth == 0) return raw.substring(start, index + 1)
                    }
                }
            }
        }
        throw SavedRoutineActionSchemaException("Incomplete JSON object")
    }

    companion object {
        const val MIN_CONFIDENCE = 0.80
        val REQUIRED_FIELDS = setOf("action", "routine_title", "date_text", "confidence")
    }
}

fun interface SavedRoutineSemanticClient {
    suspend fun interpretSavedRoutineAction(userText: String): String
}

class SavedRoutineSemanticOrchestrator(
    private val client: SavedRoutineSemanticClient,
    private val parser: SavedRoutineActionParser = SavedRoutineActionParser()
) {
    suspend fun interpret(userText: String): SavedRoutineActionDecision =
        parser.parse(client.interpretSavedRoutineAction(userText))
}
