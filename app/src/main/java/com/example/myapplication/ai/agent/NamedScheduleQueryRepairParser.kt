package com.example.myapplication.ai.agent

import com.example.myapplication.ai.TaskQueryDetail
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Validates compact extraction against original-text evidence, then reconstructs the full contract. */
internal object NamedScheduleQueryRepairParser {
    private val fields = setOf("target_task_title", "confidence", "need_clarification")

    fun toTaskAgentJson(raw: String, expectedDetail: TaskQueryDetail): String {
        require(expectedDetail != TaskQueryDetail.NONE) { "Named schedule-read evidence is required" }
        val tokener = JSONTokener(raw.trim())
        val json = JSONObject(tokener)
        require(tokener.nextClean() == '\u0000') { "Trailing named-query repair content" }
        require(json.keys().asSequence().toSet() == fields) { "Invalid named-query repair fields" }
        require(json.opt("target_task_title") is String)
        require(json.opt("confidence") is Number && json.opt("need_clarification") is Boolean)
        val target = json.getString("target_task_title").trim()
        val confidence = json.getDouble("confidence")
        require(target.isNotBlank()) { "Missing named-query target" }
        require(confidence.isFinite() && confidence in 0.80..1.0) { "Uncertain named-query repair" }
        require(!json.getBoolean("need_clarification")) { "Named-query repair requires clarification" }
        return JSONObject().apply {
            put("action", "QUERY_TASK")
            put("target_task_title", target)
            listOf("task_title", "natural_response", "date", "time", "target_date", "target_time",
                "new_date", "new_time", "recurrence", "priority").forEach { put(it, "") }
            put("query_presentation", "DETAILS")
            put("query_detail", expectedDetail.name)
            put("breakdown_target_preference", "AUTO")
            put("confidence", confidence)
            put("need_clarification", false)
            put("missing_fields", JSONArray())
            put("requires_confirmation", false)
            put("plan", JSONArray())
        }.toString()
    }
}
