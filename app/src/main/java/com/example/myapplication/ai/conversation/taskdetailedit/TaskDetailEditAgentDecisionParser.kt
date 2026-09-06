package com.example.myapplication.ai.conversation.taskdetailedit

import com.example.myapplication.ai.conversation.ConversationSchemaException
import org.json.JSONObject

class TaskDetailEditAgentDecisionParser {
    fun parse(rawContent: String): TaskDetailEditAgentDecision {
        if (rawContent.isBlank()) throw ConversationSchemaException("Task-detail edit response was blank")
        val jsonText = extractFirstJsonObject(rawContent)
        if (rawContent.trim() != jsonText) {
            throw ConversationSchemaException("Task-detail edit response contains text outside JSON")
        }
        val json = try {
            JSONObject(jsonText)
        } catch (exception: Exception) {
            throw ConversationSchemaException("Task-detail edit response is not strict JSON", exception)
        }
        requireExactFields(json)
        rejectAuthorityFields(json)

        val moveText = requireString(json, "move")
        val move = try {
            TaskDetailEditAgentMove.valueOf(moveText)
        } catch (exception: IllegalArgumentException) {
            throw ConversationSchemaException("Invalid task-detail edit move: $moveText", exception)
        }
        val decision = TaskDetailEditAgentDecision(
            move = move,
            title = requireString(json, "title"),
            dateText = requireString(json, "date_text"),
            timeText = requireString(json, "time_text"),
            clarification = requireString(json, "clarification"),
            confidence = requireNumber(json, "confidence")
        )
        if (decision.confidence !in 0.0..1.0) {
            throw ConversationSchemaException("Task-detail edit confidence is out of range")
        }
        validateShape(decision)
        return decision
    }

    private fun extractFirstJsonObject(rawContent: String): String {
        val start = rawContent.indexOf('{')
        if (start < 0) throw ConversationSchemaException("Task-detail edit response has no JSON object")
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
        throw ConversationSchemaException("Task-detail edit response contains incomplete JSON")
    }

    private fun validateShape(decision: TaskDetailEditAgentDecision) {
        val authorityValues = listOf(decision.title, decision.dateText, decision.timeText)
        when (decision.move) {
            TaskDetailEditAgentMove.SET_TITLE -> {
                requireSchema(decision.title.isNotBlank(), "SET_TITLE requires title")
                requireSchema(decision.dateText.isEmpty() && decision.timeText.isEmpty(), "SET_TITLE forbids schedule fields")
                requireSchema(decision.clarification.isEmpty(), "SET_TITLE forbids clarification")
            }
            TaskDetailEditAgentMove.SET_DATE -> {
                requireSchema(decision.dateText.isNotBlank(), "SET_DATE requires date_text")
                requireSchema(decision.title.isEmpty() && decision.timeText.isEmpty(), "SET_DATE forbids other authority fields")
                requireSchema(decision.clarification.isEmpty(), "SET_DATE forbids clarification")
            }
            TaskDetailEditAgentMove.SET_TIME -> {
                requireSchema(decision.timeText.isNotBlank(), "SET_TIME requires time_text")
                requireSchema(decision.title.isEmpty() && decision.dateText.isEmpty(), "SET_TIME forbids other authority fields")
                requireSchema(decision.clarification.isEmpty(), "SET_TIME forbids clarification")
            }
            TaskDetailEditAgentMove.SET_SCHEDULE -> {
                requireSchema(decision.dateText.isNotBlank() || decision.timeText.isNotBlank(), "SET_SCHEDULE requires date_text or time_text")
                requireSchema(decision.title.isEmpty(), "SET_SCHEDULE forbids title")
                requireSchema(decision.clarification.isEmpty(), "SET_SCHEDULE forbids clarification")
            }
            TaskDetailEditAgentMove.ASK_CLARIFICATION -> {
                requireSchema(decision.clarification.isNotBlank(), "ASK_CLARIFICATION requires clarification")
                requireSchema(authorityValues.all(String::isEmpty), "ASK_CLARIFICATION forbids authority fields")
            }
            TaskDetailEditAgentMove.CONFIRM_SAVE,
            TaskDetailEditAgentMove.REJECT_SAVE,
            TaskDetailEditAgentMove.REQUEST_TITLE_CHANGE,
            TaskDetailEditAgentMove.REQUEST_DATE_CHANGE,
            TaskDetailEditAgentMove.REQUEST_TIME_CHANGE,
            TaskDetailEditAgentMove.READ_TITLE,
            TaskDetailEditAgentMove.READ_DATE,
            TaskDetailEditAgentMove.READ_TIME,
            TaskDetailEditAgentMove.READ_SCHEDULE,
            TaskDetailEditAgentMove.CANCEL,
            TaskDetailEditAgentMove.UNKNOWN -> {
                requireSchema(authorityValues.all(String::isEmpty), "${decision.move} forbids authority fields")
                requireSchema(decision.clarification.isEmpty(), "${decision.move} forbids clarification")
            }
        }
    }

    private fun requireExactFields(json: JSONObject) {
        val keys = json.keys().asSequence().toSet()
        val missing = REQUIRED_FIELDS - keys
        if (missing.isNotEmpty()) throw ConversationSchemaException("Missing task-detail edit fields: ${missing.joinToString()}")
        val extra = keys - REQUIRED_FIELDS
        if (extra.isNotEmpty()) throw ConversationSchemaException("Additional task-detail edit fields: ${extra.joinToString()}")
    }

    private fun rejectAuthorityFields(json: JSONObject) {
        val forbidden = FORBIDDEN_FIELDS.firstOrNull(json::has)
        if (forbidden != null) throw ConversationSchemaException("Forbidden task-detail authority field: $forbidden")
    }

    private fun requireString(json: JSONObject, field: String): String {
        val value = json.get(field)
        if (value !is String) throw ConversationSchemaException("Task-detail edit field '$field' must be a string")
        return value
    }

    private fun requireNumber(json: JSONObject, field: String): Double {
        val value = json.get(field)
        if (value !is Number) throw ConversationSchemaException("Task-detail edit field '$field' must be numeric")
        return value.toDouble()
    }

    private fun requireSchema(condition: Boolean, message: String) {
        if (!condition) throw ConversationSchemaException(message)
    }

    private companion object {
        val REQUIRED_FIELDS = setOf("move", "title", "date_text", "time_text", "clarification", "confidence")
        val FORBIDDEN_FIELDS = setOf(
            "id", "task_id", "room_id", "reminder_id", "database_operation", "save_command",
            "execute", "executed", "success", "taskId", "roomId", "reminderId"
        )
    }
}
