package com.example.myapplication.ai.routine.followup

import com.example.myapplication.ai.conversation.ConversationSchemaException
import org.json.JSONObject

class RoutineFollowUpAgentDecisionParser {
    fun parse(rawContent: String): RoutineFollowUpAgentDecision {
        if (rawContent.isBlank()) {
            throw ConversationSchemaException("Routine follow-up response was blank")
        }
        val jsonText = extractFirstJsonObject(rawContent)
        if (rawContent.trim() != jsonText) {
            throw ConversationSchemaException(
                "Routine follow-up response contains text outside JSON"
            )
        }
        val json = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            throw ConversationSchemaException(
                "Routine follow-up response is not valid JSON",
                e
            )
        }
        requireExactFields(json)

        val moveText = requireString(json, "move")
        val move = try {
            RoutineFollowUpAgentMove.valueOf(moveText)
        } catch (e: IllegalArgumentException) {
            throw ConversationSchemaException(
                "Invalid routine follow-up move: $moveText",
                e
            )
        }
        val stepIndex = requireInteger(json, "step_index")
        if (stepIndex !in 0..5) {
            throw ConversationSchemaException(
                "Routine follow-up step_index out of range: $stepIndex"
            )
        }
        val value = requireString(json, "value")
        val confidence = requireNumber(json, "confidence")
        if (!confidence.isFinite() || confidence !in 0.0..1.0) {
            throw ConversationSchemaException(
                "Routine follow-up confidence out of range: $confidence"
            )
        }
        validateShape(move, stepIndex, value)
        return RoutineFollowUpAgentDecision(move, stepIndex, value, confidence)
    }

    private fun validateShape(
        move: RoutineFollowUpAgentMove,
        stepIndex: Int,
        value: String
    ) {
        when (move) {
            RoutineFollowUpAgentMove.CHANGE_STEP_TIME,
            RoutineFollowUpAgentMove.CHANGE_STEP_TITLE -> {
                if (stepIndex !in 1..5) {
                    throw ConversationSchemaException("$move requires step_index from 1 to 5")
                }
                if (value.isBlank()) {
                    throw ConversationSchemaException("$move requires a non-empty value")
                }
            }
            RoutineFollowUpAgentMove.PROVIDE_SHARED_DATE,
            RoutineFollowUpAgentMove.PROVIDE_STEP_TIME,
            RoutineFollowUpAgentMove.CHANGE_SHARED_DATE -> {
                if (stepIndex != 0) {
                    throw ConversationSchemaException("$move requires step_index 0")
                }
                if (value.isBlank()) {
                    throw ConversationSchemaException("$move requires a non-empty value")
                }
            }
            RoutineFollowUpAgentMove.CONFIRM,
            RoutineFollowUpAgentMove.REJECT,
            RoutineFollowUpAgentMove.CANCEL,
            RoutineFollowUpAgentMove.REPEAT,
            RoutineFollowUpAgentMove.STRUCTURAL_CHANGE,
            RoutineFollowUpAgentMove.REQUEST_HELP,
            RoutineFollowUpAgentMove.UNKNOWN -> {
                if (stepIndex != 0) {
                    throw ConversationSchemaException("$move requires step_index 0")
                }
                if (value.isNotEmpty()) {
                    throw ConversationSchemaException("$move requires an empty value")
                }
            }
        }
    }

    private fun extractFirstJsonObject(rawContent: String): String {
        val start = rawContent.indexOf('{')
        if (start < 0) {
            throw ConversationSchemaException("Routine follow-up response has no JSON object")
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
                    '{' -> depth += 1
                    '}' -> {
                        depth -= 1
                        if (depth == 0) return rawContent.substring(start, index + 1)
                    }
                }
            }
        }
        throw ConversationSchemaException(
            "Routine follow-up response contains incomplete JSON"
        )
    }

    private fun requireExactFields(json: JSONObject) {
        val keys = json.keys().asSequence().toSet()
        val missing = REQUIRED_FIELDS - keys
        if (missing.isNotEmpty()) {
            throw ConversationSchemaException(
                "Routine follow-up response missing fields: ${missing.joinToString()}"
            )
        }
        val additional = keys - REQUIRED_FIELDS
        if (additional.isNotEmpty()) {
            throw ConversationSchemaException(
                "Routine follow-up response contains additional fields: ${additional.joinToString()}"
            )
        }
    }

    private fun requireString(json: JSONObject, field: String): String {
        val value = json.get(field)
        if (value !is String) {
            throw ConversationSchemaException(
                "Routine follow-up field '$field' must be a string"
            )
        }
        return value
    }

    private fun requireInteger(json: JSONObject, field: String): Int {
        val value = json.get(field)
        if (value !is Number) {
            throw ConversationSchemaException(
                "Routine follow-up field '$field' must be an integer"
            )
        }
        val doubleValue = value.toDouble()
        val intValue = value.toInt()
        if (!doubleValue.isFinite() || doubleValue != intValue.toDouble()) {
            throw ConversationSchemaException(
                "Routine follow-up field '$field' must be an integer"
            )
        }
        return intValue
    }

    private fun requireNumber(json: JSONObject, field: String): Double {
        val value = json.get(field)
        if (value !is Number) {
            throw ConversationSchemaException(
                "Routine follow-up field '$field' must be a number"
            )
        }
        return value.toDouble()
    }

    private companion object {
        val REQUIRED_FIELDS = setOf("move", "step_index", "value", "confidence")
    }
}
