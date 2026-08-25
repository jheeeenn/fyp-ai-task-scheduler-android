package com.example.myapplication.ai.conversation

import org.json.JSONObject
import org.json.JSONTokener

enum class NoContextMutationRepairMove {
    TASK_COMMAND,
    ASK_CLARIFICATION
}

data class NoContextMutationRepairDecision(
    val move: NoContextMutationRepairMove,
    val reply: String,
    val confidence: Double
)

class NoContextMutationRepairParser {
    fun parse(rawContent: String): NoContextMutationRepairDecision {
        val content = rawContent.trim()
        if (content.isEmpty()) {
            fail(
                ConversationDecisionFailureCode.BLANK_CONTENT,
                "No-context mutation repair returned blank content"
            )
        }
        val tokener = JSONTokener(content)
        val json = try {
            JSONObject(tokener)
        } catch (exception: Exception) {
            val failureCode = if (content.startsWith("{") && !content.endsWith("}")) {
                ConversationDecisionFailureCode.INCOMPLETE_JSON
            } else {
                ConversationDecisionFailureCode.INVALID_JSON
            }
            fail(failureCode, "Invalid no-context mutation repair JSON", exception)
        }
        if (tokener.nextClean() != '\u0000') {
            fail(
                ConversationDecisionFailureCode.INVALID_JSON,
                "No-context mutation repair contains trailing content"
            )
        }
        val fields = json.keys().asSequence().toSet()
        if (REQUIRED_FIELDS.any { it !in fields }) {
            fail(
                ConversationDecisionFailureCode.MISSING_FIELDS,
                "No-context mutation repair is missing required fields"
            )
        }
        if (fields.any { it !in REQUIRED_FIELDS }) {
            fail(
                ConversationDecisionFailureCode.ADDITIONAL_FIELDS,
                "No-context mutation repair contains unexpected fields"
            )
        }
        if (json.opt("move") !is String ||
            json.opt("reply") !is String ||
            json.opt("confidence") !is Number
        ) {
            fail(
                ConversationDecisionFailureCode.WRONG_PRIMITIVE_TYPE,
                "No-context mutation repair has an invalid primitive type"
            )
        }
        val move = runCatching {
            NoContextMutationRepairMove.valueOf(json.getString("move"))
        }.getOrElse { exception ->
            fail(
                ConversationDecisionFailureCode.UNKNOWN_ENUM_VALUE,
                "Invalid no-context mutation repair move",
                exception
            )
        }
        val reply = json.getString("reply").trim()
        val confidence = json.getDouble("confidence")
        if (!confidence.isFinite() || confidence !in 0.0..1.0) {
            fail(
                ConversationDecisionFailureCode.INVALID_CONFIDENCE,
                "No-context mutation repair confidence is out of range"
            )
        }
        if (confidence < ConversationDecisionParser.MIN_ACCEPTED_ROUTING_CONFIDENCE) {
            fail(
                ConversationDecisionFailureCode.LOW_CONFIDENCE,
                "No-context mutation repair confidence is below the routing threshold"
            )
        }
        when (move) {
            NoContextMutationRepairMove.TASK_COMMAND -> if (reply.isNotEmpty()) {
                fail(
                    ConversationDecisionFailureCode.FORBIDDEN_FIELD,
                    "TASK_COMMAND no-context mutation repair requires an empty reply"
                )
            }
            NoContextMutationRepairMove.ASK_CLARIFICATION -> if (
                reply.isEmpty() || reply.length > MAX_CLARIFICATION_CHARS
            ) {
                fail(
                    ConversationDecisionFailureCode.MISSING_FIELDS,
                    "ASK_CLARIFICATION no-context mutation repair requires a concise reply"
                )
            }
        }
        return NoContextMutationRepairDecision(
            move = move,
            reply = reply,
            confidence = confidence
        )
    }

    private fun fail(
        code: ConversationDecisionFailureCode,
        message: String,
        cause: Throwable? = null
    ): Nothing = throw ConversationSchemaException(
        message = message,
        cause = cause,
        decisionFailureCode = code
    )

    private companion object {
        val REQUIRED_FIELDS = setOf("move", "reply", "confidence")
        const val MAX_CLARIFICATION_CHARS = 160
    }
}
