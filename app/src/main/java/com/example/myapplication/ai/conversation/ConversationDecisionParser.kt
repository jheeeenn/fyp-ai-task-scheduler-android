package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.TaskQueryPresentation
import org.json.JSONObject

enum class ConversationDecisionFailureCode {
    BLANK_CONTENT,
    INVALID_JSON,
    INCOMPLETE_JSON,
    FORBIDDEN_FIELD,
    MISSING_FIELDS,
    ADDITIONAL_FIELDS,
    WRONG_PRIMITIVE_TYPE,
    UNKNOWN_ENUM_VALUE,
    INVALID_CONFIDENCE,
    INVALID_CONTEXT_REF,
    INVALID_CONTEXT_DETAIL,
    INVALID_CONTEXT_ACTION,
    INVALID_QUERY_READING_MOVE,
    LOW_CONFIDENCE,
    LISTEN_AGAIN_REQUIRED
}

class ConversationSchemaException(
    message: String,
    cause: Throwable? = null,
    val decisionFailureCode: ConversationDecisionFailureCode? = null
) : Exception(message, cause)

data class RawConversationDecision(
    val route: ConversationRoute,
    val taskText: String,
    val reply: String,
    val contextRef: String,
    val contextDetail: ConversationContextDetail,
    val contextAction: ConversationContextAction,
    val queryReadingMove: ConversationQueryReadingMove,
    val queryPresentationHint: TaskQueryPresentation,
    val confidence: Double,
    val listenAgain: Boolean
)

data class CanonicalizationReport(
    val fields: List<String> = emptyList()
) {
    val wasCanonicalized: Boolean
        get() = fields.isNotEmpty()
}

data class ConversationDecisionParseResult(
    val decision: ConversationDecision,
    val canonicalizationReport: CanonicalizationReport
)

/** Stage one: strict structural and primitive decoding with no route-specific recovery. */
class ConversationDecisionStructuralDecoder {
    fun decode(rawContent: String): RawConversationDecision {
        if (rawContent.isBlank()) {
            fail(
                ConversationDecisionFailureCode.BLANK_CONTENT,
                "Conversation Agent returned blank content"
            )
        }

        val jsonText = extractFirstJsonObject(rawContent)
        val json = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            fail(
                ConversationDecisionFailureCode.INVALID_JSON,
                "Conversation Agent content is not valid JSON",
                e
            )
        }

        rejectTaskAgentFields(json)
        requireExactFields(json)

        val route = enumValue(
            field = "route",
            value = requireString(json, "route"),
            values = ConversationRoute.entries.associateBy(ConversationRoute::name)
        )
        val contextDetail = enumValue(
            field = "context_detail",
            value = requireString(json, "context_detail"),
            values = ConversationContextDetail.entries.associateBy(ConversationContextDetail::name)
        )
        val contextAction = enumValue(
            field = "context_action",
            value = requireString(json, "context_action"),
            values = ConversationContextAction.entries.associateBy(ConversationContextAction::name)
        )
        val queryReadingMove = enumValue(
            field = "query_reading_move",
            value = requireString(json, "query_reading_move"),
            values = ConversationQueryReadingMove.entries.associateBy(ConversationQueryReadingMove::name)
        )
        val queryPresentationText = requireString(json, "query_presentation_hint")
        val queryPresentationHint = TaskQueryPresentation.fromWireValue(queryPresentationText)
            ?: fail(
                ConversationDecisionFailureCode.UNKNOWN_ENUM_VALUE,
                "Invalid ConversationDecision query_presentation_hint"
            )
        val confidence = requireNumber(json, "confidence")
        if (!confidence.isFinite() || confidence !in 0.0..1.0) {
            fail(
                ConversationDecisionFailureCode.INVALID_CONFIDENCE,
                "ConversationDecision confidence is out of range"
            )
        }

        return RawConversationDecision(
            route = route,
            taskText = requireString(json, "task_text"),
            reply = requireString(json, "reply"),
            contextRef = requireString(json, "context_ref"),
            contextDetail = contextDetail,
            contextAction = contextAction,
            queryReadingMove = queryReadingMove,
            queryPresentationHint = queryPresentationHint,
            confidence = confidence,
            listenAgain = requireBoolean(json, "listen_again")
        )
    }

    private fun extractFirstJsonObject(rawContent: String): String {
        val start = rawContent.indexOf('{')
        if (start < 0) {
            fail(
                ConversationDecisionFailureCode.INVALID_JSON,
                "Conversation Agent content does not contain a JSON object"
            )
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
                        if (depth == 0) return rawContent.substring(start, index + 1)
                    }
                }
            }
        }

        fail(
            ConversationDecisionFailureCode.INCOMPLETE_JSON,
            "Conversation Agent content contains an incomplete JSON object"
        )
    }

    private fun rejectTaskAgentFields(json: JSONObject) {
        if (TASK_AGENT_FIELDS.any(json::has)) {
            fail(
                ConversationDecisionFailureCode.FORBIDDEN_FIELD,
                "ConversationDecision contains a forbidden task-agent field"
            )
        }
    }

    private fun requireExactFields(json: JSONObject) {
        val keys = json.keys().asSequence().toSet()
        if (REQUIRED_FIELDS.any { it !in keys }) {
            fail(
                ConversationDecisionFailureCode.MISSING_FIELDS,
                "ConversationDecision is missing required fields"
            )
        }
        if (keys.any { it !in REQUIRED_FIELDS }) {
            fail(
                ConversationDecisionFailureCode.ADDITIONAL_FIELDS,
                "ConversationDecision contains additional fields"
            )
        }
    }

    private fun requireString(json: JSONObject, field: String): String {
        val value = json.get(field)
        if (value !is String) {
            fail(
                ConversationDecisionFailureCode.WRONG_PRIMITIVE_TYPE,
                "ConversationDecision has a field with the wrong primitive type"
            )
        }
        return value
    }

    private fun requireNumber(json: JSONObject, field: String): Double {
        val value = json.get(field)
        if (value !is Number) {
            fail(
                ConversationDecisionFailureCode.WRONG_PRIMITIVE_TYPE,
                "ConversationDecision has a field with the wrong primitive type"
            )
        }
        return value.toDouble()
    }

    private fun requireBoolean(json: JSONObject, field: String): Boolean {
        val value = json.get(field)
        if (value !is Boolean) {
            fail(
                ConversationDecisionFailureCode.WRONG_PRIMITIVE_TYPE,
                "ConversationDecision has a field with the wrong primitive type"
            )
        }
        return value
    }

    private fun <T> enumValue(
        field: String,
        value: String,
        values: Map<String, T>
    ): T = values[value] ?: fail(
        ConversationDecisionFailureCode.UNKNOWN_ENUM_VALUE,
        "ConversationDecision contains an unknown enum value for $field"
    )

    private fun fail(
        code: ConversationDecisionFailureCode,
        message: String,
        cause: Throwable? = null
    ): Nothing = throw ConversationSchemaException(message, cause, code)

    private companion object {
        val REQUIRED_FIELDS = setOf(
            "route",
            "task_text",
            "reply",
            "context_ref",
            "context_detail",
            "context_action",
            "query_reading_move",
            "query_presentation_hint",
            "confidence",
            "listen_again"
        )
        val TASK_AGENT_FIELDS = setOf(
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

/** Stage two-a: discard only fields that cannot carry authority for the selected route. */
object ConversationDecisionCanonicalizer {
    fun canonicalize(raw: RawConversationDecision): ConversationDecisionParseResult {
        val canonical = ConversationDecision(
            route = raw.route,
            taskText = "",
            reply = if (raw.route in REPLY_ROUTES) raw.reply else "",
            contextRef = if (raw.route in CONTEXT_ROUTES) raw.contextRef else "",
            contextDetail = if (raw.route == ConversationRoute.CONTEXT_READ) {
                raw.contextDetail
            } else {
                ConversationContextDetail.NONE
            },
            contextAction = if (raw.route == ConversationRoute.CONTEXT_ACTION) {
                raw.contextAction
            } else {
                ConversationContextAction.NONE
            },
            queryReadingMove = if (raw.route == ConversationRoute.QUERY_READING_CONTROL) {
                raw.queryReadingMove
            } else {
                ConversationQueryReadingMove.NONE
            },
            queryPresentationHint = if (raw.route == ConversationRoute.TASK_COMMAND) {
                raw.queryPresentationHint
            } else {
                TaskQueryPresentation.NONE
            },
            confidence = raw.confidence,
            listenAgain = raw.listenAgain
        )
        return ConversationDecisionParseResult(
            decision = canonical,
            canonicalizationReport = CanonicalizationReport(
                fields = changedInactiveFields(raw, canonical)
            )
        )
    }

    private fun changedInactiveFields(
        raw: RawConversationDecision,
        canonical: ConversationDecision
    ): List<String> = buildList {
        if (raw.taskText != canonical.taskText) add("task_text")
        if (raw.reply != canonical.reply) add("reply")
        if (raw.contextRef != canonical.contextRef) add("context_ref")
        if (raw.contextDetail != canonical.contextDetail) add("context_detail")
        if (raw.contextAction != canonical.contextAction) add("context_action")
        if (raw.queryReadingMove != canonical.queryReadingMove) add("query_reading_move")
        if (raw.queryPresentationHint != canonical.queryPresentationHint) {
            add("query_presentation_hint")
        }
    }

    private val CONTEXT_ROUTES = setOf(
        ConversationRoute.CONTEXT_READ,
        ConversationRoute.CONTEXT_ACTION
    )
    private val REPLY_ROUTES = setOf(
        ConversationRoute.DIRECT_REPLY,
        ConversationRoute.ASK_CLARIFICATION,
        ConversationRoute.END_SESSION,
        ConversationRoute.UNKNOWN
    )
}

/** Stage two-b: validate only the fields that still carry authority after canonicalization. */
object ConversationDecisionContractValidator {
    fun validate(decision: ConversationDecision) {
        when (decision.route) {
            ConversationRoute.SMART_ROUTINE_BUILDER,
            ConversationRoute.SAVED_ROUTINE_ACTION,
            ConversationRoute.DAILY_BRIEFING,
            ConversationRoute.CONTEXT_AWARE_SUGGESTION -> {
                if (decision.confidence < ConversationDecisionParser.MIN_ACCEPTED_ROUTING_CONFIDENCE) {
                    fail(
                        ConversationDecisionFailureCode.LOW_CONFIDENCE,
                        "ConversationDecision confidence is below the accepted routing threshold"
                    )
                }
                if (!decision.listenAgain) {
                    fail(
                        ConversationDecisionFailureCode.LISTEN_AGAIN_REQUIRED,
                        "ConversationDecision route requires listen_again true"
                    )
                }
            }
            ConversationRoute.CONTEXT_READ -> {
                if (!TEMPORARY_REF.matches(decision.contextRef)) {
                    fail(
                        ConversationDecisionFailureCode.INVALID_CONTEXT_REF,
                        "CONTEXT_READ requires a temporary context_ref"
                    )
                }
                if (decision.contextDetail == ConversationContextDetail.NONE) {
                    fail(
                        ConversationDecisionFailureCode.INVALID_CONTEXT_DETAIL,
                        "CONTEXT_READ requires a non-NONE context_detail"
                    )
                }
            }
            ConversationRoute.CONTEXT_ACTION -> {
                if (!TEMPORARY_REF.matches(decision.contextRef)) {
                    fail(
                        ConversationDecisionFailureCode.INVALID_CONTEXT_REF,
                        "CONTEXT_ACTION requires a temporary context_ref"
                    )
                }
                if (decision.contextAction !in setOf(
                        ConversationContextAction.UPDATE,
                        ConversationContextAction.RESCHEDULE,
                        ConversationContextAction.DELETE
                    )
                ) {
                    fail(
                        ConversationDecisionFailureCode.INVALID_CONTEXT_ACTION,
                        "CONTEXT_ACTION requires UPDATE, RESCHEDULE, or DELETE context_action"
                    )
                }
            }
            ConversationRoute.QUERY_READING_CONTROL -> {
                if (decision.queryReadingMove == ConversationQueryReadingMove.NONE) {
                    fail(
                        ConversationDecisionFailureCode.INVALID_QUERY_READING_MOVE,
                        "QUERY_READING_CONTROL requires a non-NONE query_reading_move"
                    )
                }
            }
            ConversationRoute.TASK_COMMAND,
            ConversationRoute.DIRECT_REPLY,
            ConversationRoute.ASK_CLARIFICATION,
            ConversationRoute.END_SESSION,
            ConversationRoute.UNKNOWN -> Unit
        }
    }

    private fun fail(
        code: ConversationDecisionFailureCode,
        message: String
    ): Nothing = throw ConversationSchemaException(message, decisionFailureCode = code)

    private val TEMPORARY_REF = Regex("T[1-9][0-9]*", RegexOption.IGNORE_CASE)
}

class ConversationDecisionParser(
    private val structuralDecoder: ConversationDecisionStructuralDecoder =
        ConversationDecisionStructuralDecoder()
) {
    fun parse(rawContent: String): ConversationDecision =
        parseWithReport(rawContent).decision

    fun parseWithReport(rawContent: String): ConversationDecisionParseResult {
        val raw = structuralDecoder.decode(rawContent)
        val canonicalized = ConversationDecisionCanonicalizer.canonicalize(raw)
        ConversationDecisionContractValidator.validate(canonicalized.decision)
        return canonicalized
    }

    companion object {
        const val MIN_ACCEPTED_ROUTING_CONFIDENCE = 0.80
    }
}
