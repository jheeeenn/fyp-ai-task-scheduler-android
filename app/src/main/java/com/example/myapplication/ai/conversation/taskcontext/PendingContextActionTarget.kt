package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationSchemaException
import org.json.JSONObject

enum class PendingContextActionTargetMove {
    SELECT_TARGET,
    ASK_CLARIFICATION,
    NOT_A_TARGET_ANSWER
}

data class PendingContextActionTargetDecision(
    val move: PendingContextActionTargetMove,
    val contextRef: String,
    val confidence: Double
)

object PendingContextActionTargetParser {
    const val MIN_CONFIDENCE = 0.80
    private val FIELDS = setOf("move", "context_ref", "confidence")
    private val TEMPORARY_REF = Regex("T[1-9][0-9]*", RegexOption.IGNORE_CASE)

    fun parse(content: String): PendingContextActionTargetDecision {
        val json = try {
            JSONObject(content.trim())
        } catch (exception: Exception) {
            throw ConversationSchemaException("Invalid pending context target JSON", exception)
        }
        if (json.keys().asSequence().toSet() != FIELDS) {
            throw ConversationSchemaException("Invalid pending context target fields")
        }
        if (json.opt("move") !is String ||
            json.opt("context_ref") !is String ||
            json.opt("confidence") !is Number
        ) {
            throw ConversationSchemaException("Invalid pending context target primitive type")
        }
        val move = runCatching {
            PendingContextActionTargetMove.valueOf(json.getString("move"))
        }.getOrElse {
            throw ConversationSchemaException("Invalid pending context target move", it)
        }
        val ref = runCatching { json.getString("context_ref") }.getOrElse {
            throw ConversationSchemaException("Invalid pending context target ref", it)
        }
        val confidence = runCatching { json.getDouble("confidence") }.getOrElse {
            throw ConversationSchemaException("Invalid pending context target confidence", it)
        }
        if (!confidence.isFinite() || confidence !in 0.0..1.0) {
            throw ConversationSchemaException("Pending context target confidence out of range")
        }
        if (move == PendingContextActionTargetMove.SELECT_TARGET && !TEMPORARY_REF.matches(ref)) {
            throw ConversationSchemaException("Selected pending context target requires a temporary ref")
        }
        if (move != PendingContextActionTargetMove.SELECT_TARGET && ref.isNotEmpty()) {
            throw ConversationSchemaException("Non-selection pending context target requires an empty ref")
        }
        return if (confidence >= MIN_CONFIDENCE) {
            PendingContextActionTargetDecision(move, ref, confidence)
        } else {
            PendingContextActionTargetDecision(
                PendingContextActionTargetMove.ASK_CLARIFICATION,
                "",
                confidence
            )
        }
    }
}
