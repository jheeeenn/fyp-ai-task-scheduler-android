package com.example.myapplication.ai.agent

import com.example.myapplication.ai.TaskCommandContradictionDetector.ScheduleChangeEvidence
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Strict compact extraction; Android fixes the action and every unrelated full-schema field. */
internal object RescheduleRepairParser {
    fun toTaskAgentJson(raw: String, evidence: ScheduleChangeEvidence): String {
        val tokener = JSONTokener(raw.trim())
        val json = JSONObject(tokener)
        require(tokener.nextClean() == '\u0000') { "Trailing reschedule repair content" }
        require(json.keys().asSequence().toSet() == FIELDS) { "Invalid reschedule repair fields" }
        listOf("target_task_title", "new_date", "new_time").forEach {
            require(json.opt(it) is String) { "Invalid reschedule repair string" }
        }
        require(json.opt("confidence") is Number) { "Invalid reschedule repair confidence" }
        require(json.opt("need_clarification") is Boolean) { "Invalid reschedule clarification" }
        val confidence = json.getDouble("confidence")
        require(confidence.isFinite() && confidence in 0.60..1.0) {
            "Uncertain reschedule repair"
        }
        // No incomplete extraction may reach Home (and especially not the original CREATE).
        require(!json.getBoolean("need_clarification")) { "Reschedule repair requires clarification" }
        require(json.getString("target_task_title").isNotBlank()) { "Missing reschedule target" }
        require(json.getString("new_date").isNotBlank() || json.getString("new_time").isNotBlank()) {
            "Missing reschedule destination"
        }
        require(!evidence.hasDestinationDate || json.getString("new_date").isNotBlank()) {
            "Reschedule repair dropped requested date"
        }
        require(!evidence.hasDestinationTime || json.getString("new_time").isNotBlank()) {
            "Reschedule repair dropped requested time"
        }
        return JSONObject().apply {
            put("action", "RESCHEDULE_TASK")
            listOf(
                "natural_response", "task_title", "date", "time", "target_date", "target_time",
                "recurrence", "priority"
            ).forEach { put(it, "") }
            put("target_task_title", json.getString("target_task_title"))
            put("new_date", json.getString("new_date"))
            put("new_time", json.getString("new_time"))
            put("confidence", confidence)
            put("need_clarification", false)
            put("query_presentation", "NONE")
            put("breakdown_target_preference", "AUTO")
            put("missing_fields", JSONArray())
            put("requires_confirmation", false)
            put("plan", JSONArray())
        }.toString()
    }

    private val FIELDS = setOf(
        "target_task_title", "new_date", "new_time", "confidence", "need_clarification"
    )
}
