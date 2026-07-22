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

        val taskText = requireString(json, "task_text")
        val reply = requireString(json, "reply")
        val contextRef = requireString(json, "context_ref")
        val contextDetailText = requireString(json, "context_detail")
        val contextDetail = try {
            ConversationContextDetail.valueOf(contextDetailText)
        } catch (e: IllegalArgumentException) {
            throw ConversationSchemaException(
                "Invalid ConversationDecision context_detail: $contextDetailText",
                e
            )
        }
        val contextActionText = requireString(json, "context_action")
        val contextAction = try {
            ConversationContextAction.valueOf(contextActionText)
        } catch (e: IllegalArgumentException) {
            throw ConversationSchemaException(
                "Invalid ConversationDecision context_action: $contextActionText",
                e
            )
        }
        validateRouteFields(route, taskText, reply, contextRef, contextDetail, contextAction)

        return ConversationDecision(
            route = route,
            taskText = taskText,
            reply = reply,
            contextRef = contextRef,
            contextDetail = contextDetail,
            contextAction = contextAction,
            confidence = confidence,
            listenAgain = requireBoolean(json, "listen_again")
        )
    }

    private fun validateRouteFields(
        route: ConversationRoute,
        taskText: String,
        reply: String,
        contextRef: String,
        contextDetail: ConversationContextDetail,
        contextAction: ConversationContextAction
    ) {
        if (route == ConversationRoute.CONTEXT_READ) {
            if (taskText.isNotEmpty() || reply.isNotEmpty()) {
                throw ConversationSchemaException(
                    "CONTEXT_READ requires empty task_text and reply"
                )
            }
            if (!TEMPORARY_REF.matches(contextRef)) {
                throw ConversationSchemaException(
                    "CONTEXT_READ requires a temporary context_ref"
                )
            }
            if (contextDetail == ConversationContextDetail.NONE) {
                throw ConversationSchemaException(
                    "CONTEXT_READ requires a non-NONE context_detail"
                )
            }
            if (contextAction != ConversationContextAction.NONE) {
                throw ConversationSchemaException("CONTEXT_READ requires context_action NONE")
            }
            return
        }

        if (route == ConversationRoute.CONTEXT_ACTION) {
            if (taskText.isNotEmpty() || reply.isNotEmpty()) {
                throw ConversationSchemaException(
                    "CONTEXT_ACTION requires empty task_text and reply"
                )
            }
            if (!TEMPORARY_REF.matches(contextRef)) {
                throw ConversationSchemaException(
                    "CONTEXT_ACTION requires a temporary context_ref"
                )
            }
            if (contextDetail != ConversationContextDetail.NONE) {
                throw ConversationSchemaException("CONTEXT_ACTION requires context_detail NONE")
            }
            if (contextAction == ConversationContextAction.NONE) {
                throw ConversationSchemaException(
                    "CONTEXT_ACTION requires UPDATE or RESCHEDULE context_action"
                )
            }
            return
        }

        if (contextRef.isNotEmpty() ||
            contextDetail != ConversationContextDetail.NONE ||
            contextAction != ConversationContextAction.NONE
        ) {
            throw ConversationSchemaException(
                "Non-context routes require empty context_ref, NONE context_detail, and NONE context_action"
            )
        }
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
            "context_ref",
            "context_detail",
            "context_action",
            "confidence",
            "listen_again"
        )

        private val TEMPORARY_REF = Regex("T[1-9][0-9]*", RegexOption.IGNORE_CASE)

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
