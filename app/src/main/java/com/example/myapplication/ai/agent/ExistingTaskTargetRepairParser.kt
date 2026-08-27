package com.example.myapplication.ai.agent

import org.json.JSONObject
import org.json.JSONTokener

internal data class ExistingTaskTargetRepair(
    val targetTaskTitle: String,
    val confidence: Float
)

/** Strictly validates a target proposal. It never resolves the proposal to a stored task. */
internal object ExistingTaskTargetRepairParser {
    private val fields = setOf("target_task_title", "confidence", "need_clarification")

    fun parse(raw: String): ExistingTaskTargetRepair {
        val tokener = JSONTokener(raw.trim())
        val json = JSONObject(tokener)
        require(tokener.nextClean() == '\u0000') { "Trailing existing-task target repair content" }
        require(json.keys().asSequence().toSet() == fields) {
            "Invalid existing-task target repair fields"
        }
        require(json.opt("target_task_title") is String)
        require(json.opt("confidence") is Number && json.opt("need_clarification") is Boolean)
        val target = json.getString("target_task_title").trim()
        val confidence = json.getDouble("confidence")
        require(target.isNotBlank()) { "Missing existing-task repair target" }
        require(confidence.isFinite() && confidence in 0.80..1.0) {
            "Uncertain existing-task target repair"
        }
        require(!json.getBoolean("need_clarification")) {
            "Existing-task target repair requires clarification"
        }
        return ExistingTaskTargetRepair(target, confidence.toFloat())
    }
}
