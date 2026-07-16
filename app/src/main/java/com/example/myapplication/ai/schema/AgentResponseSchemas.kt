package com.example.myapplication.ai.schema

import org.json.JSONArray
import org.json.JSONObject

object AgentResponseSchemas {
    fun conversationDecisionResponseFormat(): JSONObject {
        return responseFormat(
            name = "conversation_decision",
            properties = JSONObject().apply {
                put("route", stringEnum("TASK_COMMAND", "DIRECT_REPLY", "ASK_CLARIFICATION", "END_SESSION", "UNKNOWN"))
                put("task_text", stringType())
                put("reply", stringType())
                put("confidence", numberType(minimum = 0.0, maximum = 1.0))
                put("listen_again", booleanType())
            },
            required = JSONArray().apply {
                put("route")
                put("task_text")
                put("reply")
                put("confidence")
                put("listen_again")
            }
        )
    }

    fun taskAgentResponseFormat(): JSONObject {
        return responseFormat(
            name = "task_agent_response",
            properties = JSONObject().apply {
                put("natural_response", stringType())
                put(
                    "action",
                    stringEnum(
                        "CREATE_TASK",
                        "QUERY_TASK",
                        "RESCHEDULE_TASK",
                        "UPDATE_TASK",
                        "DELETE_TASK",
                        "MARK_DONE",
                        "MARK_UNDONE",
                        "BREAKDOWN_TASK",
                        "UNKNOWN"
                    )
                )
                put("task_title", stringType())
                put("target_task_title", stringType())
                put("date", stringType())
                put("time", stringType())
                put("target_date", stringType())
                put("target_time", stringType())
                put("new_date", stringType())
                put("new_time", stringType())
                put("recurrence", stringEnum("", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"))
                put("priority", stringEnum("", "LOW", "MEDIUM", "HIGH"))
                put("confidence", numberType(minimum = 0.0, maximum = 1.0))
                put("need_clarification", booleanType())
                put("missing_fields", stringArrayType())
                put("requires_confirmation", booleanType())
                put("plan", stringArrayType())
            },
            required = JSONArray().apply {
                put("natural_response")
                put("action")
                put("task_title")
                put("target_task_title")
                put("date")
                put("time")
                put("target_date")
                put("target_time")
                put("new_date")
                put("new_time")
                put("recurrence")
                put("priority")
                put("confidence")
                put("need_clarification")
                put("missing_fields")
                put("requires_confirmation")
                put("plan")
            }
        )
    }

    private fun responseFormat(name: String, properties: JSONObject, required: JSONArray): JSONObject {
        return JSONObject().apply {
            put("type", "json_schema")
            put(
                "json_schema",
                JSONObject().apply {
                    put("name", name)
                    put("strict", true)
                    put(
                        "schema",
                        JSONObject().apply {
                            put("type", "object")
                            put("properties", properties)
                            put("required", required)
                            put("additionalProperties", false)
                        }
                    )
                }
            )
        }
    }

    private fun stringType(): JSONObject = JSONObject().apply {
        put("type", "string")
    }

    private fun stringEnum(vararg values: String): JSONObject = stringType().apply {
        put("enum", JSONArray().apply { values.forEach { put(it) } })
    }

    private fun numberType(minimum: Double, maximum: Double): JSONObject = JSONObject().apply {
        put("type", "number")
        put("minimum", minimum)
        put("maximum", maximum)
    }

    private fun booleanType(): JSONObject = JSONObject().apply {
        put("type", "boolean")
    }

    private fun stringArrayType(): JSONObject = JSONObject().apply {
        put("type", "array")
        put("items", stringType())
    }
}
