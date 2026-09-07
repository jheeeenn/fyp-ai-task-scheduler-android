package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.ai.conversation.ConversationSchemaException
import com.example.myapplication.voice.CreateDraftField
import org.json.JSONObject

class CreateDraftAgentDecisionParser {
    fun parse(rawContent: String): CreateDraftAgentDecision {
        if (rawContent.isBlank()) {
            throw ConversationSchemaException("Create-draft response was blank")
        }

        val jsonText = extractFirstJsonObject(rawContent)
        if (rawContent.trim() != jsonText) {
            throw ConversationSchemaException("Create-draft response contains text outside JSON")
        }
        val json = try {
            JSONObject(jsonText)
        } catch (e: ConversationSchemaException) {
            throw e
        } catch (e: Exception) {
            throw ConversationSchemaException("Create-draft response is not valid JSON", e)
        }

        rejectTaskAgentFields(json)
        requireExactFields(json)

        val moveText = requireString(json, "move")
        val move = try {
            CreateDraftAgentMoveType.valueOf(moveText)
        } catch (e: IllegalArgumentException) {
            throw ConversationSchemaException("Invalid create-draft move: $moveText", e)
        }
        val fieldText = requireString(json, "field")
        val field = when (fieldText) {
            "" -> null
            "TITLE", "DATE", "TIME" -> CreateDraftField.valueOf(fieldText)
            else -> throw ConversationSchemaException("Invalid create-draft field: $fieldText")
        }
        val value = requireString(json, "value")
        val dateText = requireString(json, "date_text")
        val timeText = requireString(json, "time_text")
        val confidence = requireNumber(json, "confidence")
        if (confidence !in 0.0..1.0) {
            throw ConversationSchemaException("Create-draft confidence out of range: $confidence")
        }

        validateMoveShape(move, field, value, dateText, timeText)
        val canonicalField = when (move) {
            CreateDraftAgentMoveType.READ_TITLE,
            CreateDraftAgentMoveType.READ_DATE,
            CreateDraftAgentMoveType.READ_TIME -> null
            else -> field
        }
        return CreateDraftAgentDecision(move, canonicalField, value, dateText, timeText, confidence)
    }

    private fun validateMoveShape(
        move: CreateDraftAgentMoveType,
        field: CreateDraftField?,
        value: String,
        dateText: String,
        timeText: String
    ) {
        when (move) {
            CreateDraftAgentMoveType.CHANGE_FIELD -> {
                if (field == null) throw ConversationSchemaException("CHANGE_FIELD requires a field")
                requireEmptySchedule(move, dateText, timeText)
            }
            CreateDraftAgentMoveType.PROVIDE_FIELD -> {
                if (field == null) throw ConversationSchemaException("PROVIDE_FIELD requires a field")
                if (value.isBlank()) throw ConversationSchemaException("PROVIDE_FIELD requires a value")
                requireEmptySchedule(move, dateText, timeText)
            }
            CreateDraftAgentMoveType.PROVIDE_SCHEDULE -> {
                if (field != null) throw ConversationSchemaException("PROVIDE_SCHEDULE requires an empty field")
                if (value.isNotEmpty()) throw ConversationSchemaException("PROVIDE_SCHEDULE requires an empty value")
                if (dateText.isBlank() || timeText.isBlank()) {
                    throw ConversationSchemaException("PROVIDE_SCHEDULE requires date_text and time_text")
                }
            }
            CreateDraftAgentMoveType.READ_TITLE -> requireReadShape(
                move, field, CreateDraftField.TITLE, value, dateText, timeText
            )
            CreateDraftAgentMoveType.READ_DATE -> requireReadShape(
                move, field, CreateDraftField.DATE, value, dateText, timeText
            )
            CreateDraftAgentMoveType.READ_TIME -> requireReadShape(
                move, field, CreateDraftField.TIME, value, dateText, timeText
            )
            CreateDraftAgentMoveType.CONFIRM_SAVE,
            CreateDraftAgentMoveType.REJECT_SAVE,
            CreateDraftAgentMoveType.READ_SCHEDULE,
            CreateDraftAgentMoveType.READ_SUMMARY,
            CreateDraftAgentMoveType.CANCEL,
            CreateDraftAgentMoveType.REQUEST_HELP,
            CreateDraftAgentMoveType.UNKNOWN -> {
                if (field != null) throw ConversationSchemaException("$move requires an empty field")
                if (value.isNotEmpty()) throw ConversationSchemaException("$move requires an empty value")
                requireEmptySchedule(move, dateText, timeText)
            }
        }
    }

    private fun requireReadShape(
        move: CreateDraftAgentMoveType,
        field: CreateDraftField?,
        matchingField: CreateDraftField,
        value: String,
        dateText: String,
        timeText: String
    ) {
        if (field != null && field != matchingField) {
            throw ConversationSchemaException("$move contains a contradictory field")
        }
        if (value.isNotEmpty()) throw ConversationSchemaException("$move requires an empty value")
        requireEmptySchedule(move, dateText, timeText)
    }

    private fun requireEmptySchedule(
        move: CreateDraftAgentMoveType,
        dateText: String,
        timeText: String
    ) {
        if (dateText.isNotEmpty() || timeText.isNotEmpty()) {
            throw ConversationSchemaException("$move requires empty date_text and time_text")
        }
    }

    private fun extractFirstJsonObject(rawContent: String): String {
        val start = rawContent.indexOf('{')
        if (start < 0) throw ConversationSchemaException("Create-draft response has no JSON object")
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
                        if (depth == 0) return rawContent.substring(start, index + 1)
                    }
                }
            }
        }
        throw ConversationSchemaException("Create-draft response contains incomplete JSON")
    }

    private fun rejectTaskAgentFields(json: JSONObject) {
        val forbidden = TASK_AGENT_FIELDS.firstOrNull(json::has)
        if (forbidden != null) {
            throw ConversationSchemaException("Create-draft response contains task-agent field: $forbidden")
        }
    }

    private fun requireExactFields(json: JSONObject) {
        val keys = json.keys().asSequence().toSet()
        val missing = REQUIRED_FIELDS - keys
        if (missing.isNotEmpty()) {
            throw ConversationSchemaException("Create-draft response missing fields: ${missing.joinToString()}")
        }
        val additional = keys - REQUIRED_FIELDS
        if (additional.isNotEmpty()) {
            throw ConversationSchemaException("Create-draft response contains additional fields: ${additional.joinToString()}")
        }
    }

    private fun requireString(json: JSONObject, field: String): String {
        val value = json.get(field)
        if (value !is String) throw ConversationSchemaException("Create-draft field '$field' must be a string")
        return value
    }

    private fun requireNumber(json: JSONObject, field: String): Double {
        val value = json.get(field)
        if (value !is Number) throw ConversationSchemaException("Create-draft field '$field' must be a number")
        return value.toDouble()
    }

    private companion object {
        val REQUIRED_FIELDS = setOf("move", "field", "value", "date_text", "time_text", "confidence")
        val TASK_AGENT_FIELDS = setOf(
            "action", "natural_response", "task_title", "target_task_title", "date", "time",
            "target_date", "target_time", "new_date", "new_time", "recurrence", "priority",
            "need_clarification", "missing_fields", "requires_confirmation", "plan"
        )
    }
}
