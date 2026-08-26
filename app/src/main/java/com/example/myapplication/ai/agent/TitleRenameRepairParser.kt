package com.example.myapplication.ai.agent

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal object TitleRenameRepairParser {
    private val fields = setOf(
        "target_task_title", "replacement_title", "confidence", "need_clarification"
    )

    fun toTaskAgentJson(raw: String): String {
        val tokener = JSONTokener(raw.trim())
        val json = JSONObject(tokener)
        require(tokener.nextClean() == '\u0000') { "Trailing title-rename repair content" }
        require(json.keys().asSequence().toSet() == fields) { "Invalid title-rename repair fields" }
        require(json.opt("target_task_title") is String && json.opt("replacement_title") is String)
        require(json.opt("confidence") is Number && json.opt("need_clarification") is Boolean)
        val confidence = json.getDouble("confidence")
        require(confidence.isFinite() && confidence in 0.80..1.0)
        require(!json.getBoolean("need_clarification"))
        val target = json.getString("target_task_title").trim()
        val replacement = json.getString("replacement_title").trim()
        require(target.isNotBlank() && replacement.isNotBlank())
        return JSONObject().apply {
            put("action", "UPDATE_TASK")
            put("target_task_title", target)
            put("task_title", replacement)
            listOf("natural_response", "date", "time", "target_date", "target_time", "new_date",
                "new_time", "recurrence", "priority").forEach { put(it, "") }
            put("query_presentation", "NONE")
            put("breakdown_target_preference", "AUTO")
            put("confidence", confidence)
            put("need_clarification", false)
            put("missing_fields", JSONArray())
            put("requires_confirmation", false)
            put("plan", JSONArray())
        }.toString()
    }
}
