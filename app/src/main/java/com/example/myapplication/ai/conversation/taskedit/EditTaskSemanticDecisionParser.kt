package com.example.myapplication.ai.conversation.taskedit

import com.example.myapplication.ai.conversation.ConversationSchemaException
import org.json.JSONObject

class EditTaskSemanticDecisionParser {
    fun parse(rawContent: String): EditTaskSemanticDecision {
        if (rawContent.isBlank()) {
            throw ConversationSchemaException("Edit-task semantic response was blank")
        }
        val jsonText = extractJsonObject(rawContent)
        if (rawContent.trim() != jsonText) {
            throw ConversationSchemaException("Edit-task semantic response contains text outside JSON")
        }
        val json = try {
            JSONObject(jsonText)
        } catch (exception: Exception) {
            throw ConversationSchemaException(
                "Edit-task semantic response is not strict JSON",
                exception
            )
        }
        requireExactFields(json)
        rejectAuthorityFields(json)

        val moveText = requireString(json, "move")
        val move = try {
            EditTaskSemanticMove.valueOf(moveText)
        } catch (exception: IllegalArgumentException) {
            throw ConversationSchemaException("Invalid EditTask semantic move: $moveText", exception)
        }
        val decision = EditTaskSemanticDecision(
            move = move,
            title = requireString(json, "title"),
            dateText = requireString(json, "date_text"),
            timeText = requireString(json, "time_text"),
            confidence = requireNumber(json, "confidence")
        )
        if (!decision.confidence.isFinite() || decision.confidence !in 0.0..1.0) {
            throw ConversationSchemaException("Edit-task semantic confidence is out of range")
        }
        validateShape(decision)
        return decision
    }

    private fun validateShape(decision: EditTaskSemanticDecision) {
        when (decision.move) {
            EditTaskSemanticMove.CHANGE_TITLE -> {
                requireSchema(decision.title.isNotBlank(), "CHANGE_TITLE requires title")
                requireSchema(
                    decision.dateText.isEmpty() && decision.timeText.isEmpty(),
                    "CHANGE_TITLE forbids temporal fields"
                )
            }

            EditTaskSemanticMove.CHANGE_DATE -> {
                requireSchema(decision.dateText.isNotBlank(), "CHANGE_DATE requires date_text")
                requireSchema(
                    decision.title.isEmpty() && decision.timeText.isEmpty(),
                    "CHANGE_DATE forbids other value fields"
                )
            }

            EditTaskSemanticMove.CHANGE_TIME -> {
                requireSchema(decision.timeText.isNotBlank(), "CHANGE_TIME requires time_text")
                requireSchema(
                    decision.title.isEmpty() && decision.dateText.isEmpty(),
                    "CHANGE_TIME forbids other value fields"
                )
            }

            EditTaskSemanticMove.CHANGE_SCHEDULE -> {
                requireSchema(
                    decision.dateText.isNotBlank() && decision.timeText.isNotBlank(),
                    "CHANGE_SCHEDULE requires date_text and time_text"
                )
                requireSchema(decision.title.isEmpty(), "CHANGE_SCHEDULE forbids title")
            }

            EditTaskSemanticMove.READ_TITLE,
            EditTaskSemanticMove.READ_DATE,
            EditTaskSemanticMove.READ_TIME,
            EditTaskSemanticMove.READ_SCHEDULE -> requireSchema(
                decision.title.isEmpty() &&
                    decision.dateText.isEmpty() &&
                    decision.timeText.isEmpty(),
                "${decision.move} forbids value fields"
            )

            else -> requireSchema(
                decision.title.isEmpty() &&
                    decision.dateText.isEmpty() &&
                    decision.timeText.isEmpty(),
                "${decision.move} forbids value fields"
            )
        }
    }

    private fun extractJsonObject(rawContent: String): String {
        val start = rawContent.indexOf('{')
        if (start < 0) throw ConversationSchemaException("Edit-task response has no JSON object")
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until rawContent.length) {
            val character = rawContent[index]
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
                        if (depth == 0) return rawContent.substring(start, index + 1)
                    }
                }
            }
        }
        throw ConversationSchemaException("Edit-task response contains incomplete JSON")
    }

    private fun requireExactFields(json: JSONObject) {
        val keys = json.keys().asSequence().toSet()
        val missing = REQUIRED_FIELDS - keys
        if (missing.isNotEmpty()) {
            throw ConversationSchemaException("Missing EditTask semantic fields: ${missing.joinToString()}")
        }
        val extra = keys - REQUIRED_FIELDS
        if (extra.isNotEmpty()) {
            throw ConversationSchemaException("Additional EditTask semantic fields: ${extra.joinToString()}")
        }
    }

    private fun rejectAuthorityFields(json: JSONObject) {
        val forbidden = FORBIDDEN_FIELDS.firstOrNull(json::has) ?: return
        throw ConversationSchemaException("Forbidden EditTask authority field: $forbidden")
    }

    private fun requireString(json: JSONObject, field: String): String {
        val value = json.get(field)
        if (value !is String) {
            throw ConversationSchemaException("EditTask semantic field '$field' must be a string")
        }
        return value
    }

    private fun requireNumber(json: JSONObject, field: String): Double {
        val value = json.get(field)
        if (value !is Number) {
            throw ConversationSchemaException("EditTask semantic field '$field' must be numeric")
        }
        return value.toDouble()
    }

    private fun requireSchema(condition: Boolean, message: String) {
        if (!condition) throw ConversationSchemaException(message)
    }

    private companion object {
        val REQUIRED_FIELDS = setOf("move", "title", "date_text", "time_text", "confidence")
        val FORBIDDEN_FIELDS = setOf(
            "id",
            "task_id",
            "room_id",
            "reminder_id",
            "database_operation",
            "save_command",
            "execute",
            "executed",
            "success",
            "taskId",
            "roomId",
            "reminderId"
        )
    }
}
