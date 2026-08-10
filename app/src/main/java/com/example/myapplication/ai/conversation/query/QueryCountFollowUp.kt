package com.example.myapplication.ai.conversation.query

import com.example.myapplication.ai.conversation.ConversationSchemaException
import org.json.JSONObject

enum class QueryCountFollowUpMove {
    START_OVERVIEW,
    STOP,
    NOT_A_QUERY_READING_CONTROL
}

data class QueryCountFollowUpDecision(
    val move: QueryCountFollowUpMove,
    val confidence: Double
)

object QueryCountFollowUpParser {
    const val MIN_CONFIDENCE = 0.80
    private val FIELDS = setOf("move", "confidence")

    fun parse(content: String): QueryCountFollowUpDecision {
        val json = try {
            JSONObject(content.trim())
        } catch (exception: Exception) {
            throw ConversationSchemaException("Invalid query-count follow-up JSON", exception)
        }
        val keys = json.keys().asSequence().toSet()
        if (keys != FIELDS) throw ConversationSchemaException("Invalid query-count follow-up fields")
        if (json.opt("move") !is String || json.opt("confidence") !is Number) {
            throw ConversationSchemaException("Invalid query-count follow-up primitive type")
        }
        val move = runCatching {
            QueryCountFollowUpMove.valueOf(json.getString("move"))
        }.getOrElse {
            throw ConversationSchemaException("Invalid query-count follow-up move", it)
        }
        val confidence = runCatching { json.getDouble("confidence") }.getOrElse {
            throw ConversationSchemaException("Invalid query-count follow-up confidence", it)
        }
        if (!confidence.isFinite() || confidence !in 0.0..1.0) {
            throw ConversationSchemaException("Query-count follow-up confidence out of range")
        }
        return QueryCountFollowUpDecision(
            move = if (confidence >= MIN_CONFIDENCE) {
                move
            } else {
                QueryCountFollowUpMove.NOT_A_QUERY_READING_CONTROL
            },
            confidence = confidence
        )
    }
}
