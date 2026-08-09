package com.example.myapplication.ai.schema

import org.json.JSONArray
import org.json.JSONObject

object AgentResponseSchemas {
    fun routineExtractionResponseFormat(): JSONObject {
        val stepSchema = JSONObject().apply {
            put("type", "object")
            put(
                "properties",
                JSONObject().apply {
                    put("title", stringType())
                    put("date_text", stringType())
                    put("time_text", stringType())
                }
            )
            put(
                "required",
                JSONArray().apply {
                    put("title")
                    put("date_text")
                    put("time_text")
                }
            )
            put("additionalProperties", false)
        }
        return responseFormat(
            name = "routine_extraction_response",
            properties = JSONObject().apply {
                put("routine_title", stringType())
                put(
                    "steps",
                    JSONObject().apply {
                        put("type", "array")
                        put("items", stepSchema)
                        put("minItems", 2)
                        put("maxItems", 5)
                    }
                )
                put("confidence", numberType(minimum = 0.0, maximum = 1.0))
                put("need_clarification", booleanType())
            },
            required = JSONArray().apply {
                put("routine_title")
                put("steps")
                put("confidence")
                put("need_clarification")
            }
        )
    }

    fun safeObservationStyleResponseFormat(): JSONObject {
        return responseFormat(
            name = "safe_observation_style",
            properties = JSONObject().apply {
                put("use_style", booleanType())
                put("lead_in", stringType())
                put("bridge", stringType())
                put("confidence", numberType(minimum = 0.0, maximum = 1.0))
            },
            required = JSONArray().apply {
                put("use_style")
                put("lead_in")
                put("bridge")
                put("confidence")
            }
        )
    }

    fun createDraftMoveResponseFormat(): JSONObject {
        return responseFormat(
            name = "create_draft_move",
            properties = JSONObject().apply {
                put(
                    "move",
                    stringEnum(
                        "CONFIRM_SAVE",
                        "REJECT_SAVE",
                        "CHANGE_FIELD",
                        "PROVIDE_FIELD",
                        "APPLY_UNSPECIFIED_CORRECTION",
                        "CANCEL",
                        "REQUEST_HELP",
                        "UNKNOWN"
                    )
                )
                put("field", stringEnum("", "TITLE", "DATE", "TIME"))
                put("value", stringType())
                put("confidence", numberType(minimum = 0.0, maximum = 1.0))
            },
            required = JSONArray().apply {
                put("move")
                put("field")
                put("value")
                put("confidence")
            }
        )
    }

    fun taskDetailEditMoveResponseFormat(): JSONObject {
        return responseFormat(
            name = "task_detail_edit_move",
            properties = JSONObject().apply {
                put(
                    "move",
                    stringEnum(
                        "SET_TITLE",
                        "SET_DATE",
                        "SET_TIME",
                        "SET_SCHEDULE",
                        "ASK_CLARIFICATION",
                        "CANCEL",
                        "UNKNOWN"
                    )
                )
                put("title", stringType())
                put("date_text", stringType())
                put("time_text", stringType())
                put("clarification", stringType())
                put("confidence", numberType(minimum = 0.0, maximum = 1.0))
            },
            required = JSONArray().apply {
                put("move")
                put("title")
                put("date_text")
                put("time_text")
                put("clarification")
                put("confidence")
            }
        )
    }

    fun routineFollowUpMoveResponseFormat(): JSONObject {
        return responseFormat(
            name = "routine_follow_up_move",
            properties = JSONObject().apply {
                put(
                    "move",
                    stringEnum(
                        "CONFIRM",
                        "REJECT",
                        "CANCEL",
                        "REPEAT",
                        "PROVIDE_SHARED_DATE",
                        "PROVIDE_STEP_TIME",
                        "CHANGE_SHARED_DATE",
                        "CHANGE_STEP_TIME",
                        "CHANGE_STEP_TITLE",
                        "STRUCTURAL_CHANGE",
                        "REQUEST_HELP",
                        "UNKNOWN"
                    )
                )
                put(
                    "step_index",
                    JSONObject().apply {
                        put("type", "integer")
                        put("minimum", 0)
                        put("maximum", 5)
                    }
                )
                put("value", stringType())
                put("confidence", numberType(minimum = 0.0, maximum = 1.0))
            },
            required = JSONArray().apply {
                put("move")
                put("step_index")
                put("value")
                put("confidence")
            }
        )
    }

    fun savedRoutineActionResponseFormat(): JSONObject {
        return responseFormat(
            name = "saved_routine_action",
            properties = JSONObject().apply {
                put("action", stringEnum("LIST", "READ_DETAILS", "RUN", "DELETE", "UNKNOWN"))
                put("routine_title", stringType())
                put("date_text", stringType())
                put("confidence", numberType(minimum = 0.0, maximum = 1.0))
            },
            required = JSONArray().apply {
                put("action")
                put("routine_title")
                put("date_text")
                put("confidence")
            }
        )
    }

    fun contextSuggestionDecisionResponseFormat(): JSONObject {
        return responseFormat(
            name = "context_suggestion_decision",
            properties = JSONObject().apply {
                put(
                    "suggestion_type",
                    stringEnum(
                        "FOCUS_TASK",
                        "CONTINUE_SUBTASK",
                        "BREAK_DOWN_TASK",
                        "REVIEW_CLOSE_SCHEDULE",
                        "NO_CLOSE_SCHEDULE",
                        "NO_SUGGESTION"
                    )
                )
                put("primary_ref", stringType())
                put("secondary_ref", stringType())
                put("confidence", numberType(minimum = 0.0, maximum = 1.0))
            },
            required = JSONArray().apply {
                put("suggestion_type")
                put("primary_ref")
                put("secondary_ref")
                put("confidence")
            }
        )
    }

    fun conversationDecisionResponseFormat(): JSONObject {
        return responseFormat(
            name = "conversation_decision",
            properties = JSONObject().apply {
                put("route", stringEnum("TASK_COMMAND", "SMART_ROUTINE_BUILDER", "SAVED_ROUTINE_ACTION", "DAILY_BRIEFING", "CONTEXT_AWARE_SUGGESTION", "CONTEXT_READ", "CONTEXT_ACTION", "QUERY_READING_CONTROL", "DIRECT_REPLY", "ASK_CLARIFICATION", "END_SESSION", "UNKNOWN"))
                put("task_text", stringType())
                put("reply", stringType())
                put("context_ref", stringType())
                put("context_detail", stringEnum("NONE", "SUMMARY", "TITLE", "DATE", "TIME", "DATE_TIME", "STATUS", "SUBTASKS"))
                put("context_action", stringEnum("NONE", "UPDATE", "RESCHEDULE", "DELETE"))
                put("query_reading_move", stringEnum("NONE", "START_OVERVIEW", "CONTINUE", "REPEAT_LAST", "REPEAT_PAGE", "STOP"))
                put("query_presentation_hint", stringEnum("NONE", "COUNT_ONLY", "OVERVIEW", "DETAILS"))
                put("confidence", numberType(minimum = 0.0, maximum = 1.0))
                put("listen_again", booleanType())
            },
            required = JSONArray().apply {
                put("route")
                put("task_text")
                put("reply")
                put("context_ref")
                put("context_detail")
                put("context_action")
                put("query_reading_move")
                put("query_presentation_hint")
                put("confidence")
                put("listen_again")
            }
        )
    }

    fun contextReadRepairResponseFormat(): JSONObject {
        return responseFormat(
            name = "context_read_repair",
            properties = JSONObject().apply {
                put("route", stringEnum("CONTEXT_READ", "ASK_CLARIFICATION"))
                put("task_text", stringType())
                put("reply", stringType())
                put("context_ref", stringType())
                put("context_detail", stringEnum("NONE", "SUMMARY", "TITLE", "DATE", "TIME", "DATE_TIME", "STATUS", "SUBTASKS"))
                put("context_action", stringEnum("NONE"))
                put("query_reading_move", stringEnum("NONE"))
                put("query_presentation_hint", stringEnum("NONE"))
                put("confidence", numberType(minimum = 0.0, maximum = 1.0))
                put("listen_again", booleanType())
            },
            required = JSONArray().apply {
                put("route")
                put("task_text")
                put("reply")
                put("context_ref")
                put("context_detail")
                put("context_action")
                put("query_reading_move")
                put("query_presentation_hint")
                put("confidence")
                put("listen_again")
            }
        )
    }

    fun contextActionRepairResponseFormat(): JSONObject {
        return responseFormat(
            name = "context_action_repair",
            properties = JSONObject().apply {
                put("route", stringEnum("CONTEXT_ACTION", "ASK_CLARIFICATION"))
                put("task_text", stringType())
                put("reply", stringType())
                put("context_ref", stringType())
                put("context_detail", stringEnum("NONE"))
                put("context_action", stringEnum("NONE", "UPDATE", "RESCHEDULE", "DELETE"))
                put("query_reading_move", stringEnum("NONE"))
                put("query_presentation_hint", stringEnum("NONE"))
                put("confidence", numberType(minimum = 0.0, maximum = 1.0))
                put("listen_again", booleanType())
            },
            required = JSONArray().apply {
                put("route")
                put("task_text")
                put("reply")
                put("context_ref")
                put("context_detail")
                put("context_action")
                put("query_reading_move")
                put("query_presentation_hint")
                put("confidence")
                put("listen_again")
            }
        )
    }

    fun contextActionExtractionResponseFormat(): JSONObject {
        return responseFormat(
            name = "context_action_extraction",
            properties = JSONObject().apply {
                put("action", stringEnum("UPDATE_TASK", "RESCHEDULE_TASK"))
                put("replacement_title", stringType())
                put("date_operation", stringEnum("KEEP", "SET", "OFFSET"))
                put("time_operation", stringEnum("KEEP", "SET", "OFFSET"))
                put(
                    "relative_base",
                    stringEnum("AUTHORITATIVE_TASK", "CURRENT_PROPOSAL")
                )
                put("replacement_date_text", stringType())
                put("replacement_time_text", stringType())
                put("date_offset_days", integerType(-365, 365))
                put("time_offset_minutes", integerType(-10_080, 10_080))
                put("confidence", numberType(minimum = 0.0, maximum = 1.0))
                put("need_clarification", booleanType())
            },
            required = JSONArray().apply {
                put("action")
                put("replacement_title")
                put("date_operation")
                put("time_operation")
                put("relative_base")
                put("replacement_date_text")
                put("replacement_time_text")
                put("date_offset_days")
                put("time_offset_minutes")
                put("confidence")
                put("need_clarification")
            }
        )
    }

    fun relativeTemporalCorrectionResponseFormat(): JSONObject {
        return responseFormat(
            name = "relative_temporal_correction",
            properties = JSONObject().apply {
                put("move", stringEnum("APPLY_CHANGE", "RESTORE_ORIGINAL", "UNKNOWN"))
                put("date_operation", stringEnum("KEEP", "SET", "OFFSET"))
                put("time_operation", stringEnum("KEEP", "SET", "OFFSET"))
                put(
                    "correction_relation",
                    stringEnum("REPLACE_PREVIOUS", "BUILD_ON_CURRENT", "UNCLEAR")
                )
                put("replacement_date_text", stringType())
                put("replacement_time_text", stringType())
                put("date_offset_days", integerType(-365, 365))
                put("time_offset_minutes", integerType(-10_080, 10_080))
                put("confidence", numberType(minimum = 0.0, maximum = 1.0))
                put("need_clarification", booleanType())
            },
            required = JSONArray().apply {
                put("move")
                put("date_operation")
                put("time_operation")
                put("correction_relation")
                put("replacement_date_text")
                put("replacement_time_text")
                put("date_offset_days")
                put("time_offset_minutes")
                put("confidence")
                put("need_clarification")
            }
        )
    }

    fun relativeTemporalRepairChoiceResponseFormat(
        availableChoiceRefs: List<String>
    ): JSONObject {
        val choices = (availableChoiceRefs + "CLARIFY").distinct()
        require(availableChoiceRefs.isNotEmpty())
        return responseFormat(
            name = "relative_temporal_repair_choice",
            properties = JSONObject().apply {
                put("choice_ref", stringEnum(*choices.toTypedArray()))
                put("confidence", numberType(minimum = 0.0, maximum = 1.0))
                put("need_clarification", booleanType())
            },
            required = JSONArray().apply {
                put("choice_ref")
                put("confidence")
                put("need_clarification")
            }
        )
    }

    fun conversationResponseResponseFormat(): JSONObject {
        return responseFormat(
            name = "conversation_response",
            properties = JSONObject().apply {
                put("speech", stringType())
                put("hint", stringType())
                put("response_type", stringEnum("ACKNOWLEDGEMENT", "INFORMATION", "REQUEST_CONFIRMATION", "REQUEST_CLARIFICATION", "SUCCESS", "PARTIAL_SUCCESS", "ERROR", "SESSION_END"))
            },
            required = JSONArray().apply {
                put("speech")
                put("hint")
                put("response_type")
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
                put("query_presentation", stringEnum("NONE", "COUNT_ONLY", "OVERVIEW", "DETAILS"))
                put("breakdown_target_preference", stringEnum("AUTO", "NEW_ROOT"))
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
                put("query_presentation")
                put("breakdown_target_preference")
                put("confidence")
                put("need_clarification")
                put("missing_fields")
                put("requires_confirmation")
                put("plan")
            }
        )
    }

    fun breakdownFollowUpResponseFormat(): JSONObject {
        return responseFormat(
            name = "breakdown_follow_up_response",
            properties = JSONObject().apply {
                put(
                    "move",
                    stringEnum("CONFIRM", "REJECT", "CANCEL", "REVISE", "UNKNOWN")
                )
                put("plan", stringArrayType())
                put("confidence", numberType(minimum = 0.0, maximum = 1.0))
            },
            required = JSONArray().apply {
                put("move")
                put("plan")
                put("confidence")
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

    private fun integerType(minimum: Int, maximum: Int): JSONObject = JSONObject().apply {
        put("type", "integer")
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
