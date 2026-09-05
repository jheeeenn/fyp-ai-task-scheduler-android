package com.example.myapplication.ai.conversation.querypresentation

import android.util.Log
import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.TaskQueryPresentation
import com.example.myapplication.ai.conversation.ConversationSchemaException
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import org.json.JSONTokener

fun interface QueryPresentationSemanticClient {
    suspend fun classifyQueryPresentation(normalizedUserText: String): String
}

enum class QueryPresentationSemanticMove {
    COUNT_ONLY,
    OVERVIEW,
    DETAILS,
    UNKNOWN
}

data class QueryPresentationSemanticDecision(
    val presentation: QueryPresentationSemanticMove,
    val confidence: Double,
    val needClarification: Boolean
)

enum class QueryPresentationSemanticSource(val logValue: String) {
    CONVERSATION_AGENT("conversation_agent"),
    EXISTING_PRESENTATION_FALLBACK("existing_presentation_fallback"),
    NOT_APPLICABLE("not_applicable")
}

data class QueryPresentationSemanticResolution(
    val presentation: TaskQueryPresentation,
    val confidence: Double,
    val source: QueryPresentationSemanticSource,
    val agentAttempted: Boolean,
    val reason: String
)

class QueryPresentationSemanticParser {
    fun parse(rawContent: String): QueryPresentationSemanticDecision {
        if (rawContent.isBlank()) {
            throw ConversationSchemaException("Query-presentation semantic response was blank")
        }
        val json = try {
            val tokener = JSONTokener(rawContent.trim())
            val parsed = tokener.nextValue()
            if (parsed !is JSONObject || tokener.nextClean().code != 0) {
                throw ConversationSchemaException(
                    "Query-presentation semantic response must contain one JSON object"
                )
            }
            parsed
        } catch (exception: ConversationSchemaException) {
            throw exception
        } catch (exception: Exception) {
            throw ConversationSchemaException(
                "Query-presentation semantic response is not strict JSON",
                exception
            )
        }
        val keys = json.keys().asSequence().toSet()
        if (keys != REQUIRED_FIELDS) {
            throw ConversationSchemaException(
                "Query-presentation semantic response has an invalid field set"
            )
        }
        val presentationText = json.get("presentation") as? String
            ?: throw ConversationSchemaException("presentation must be a string")
        val presentation = runCatching {
            QueryPresentationSemanticMove.valueOf(presentationText)
        }.getOrElse {
            throw ConversationSchemaException("Invalid query presentation: $presentationText", it)
        }
        val confidence = (json.get("confidence") as? Number)?.toDouble()
            ?: throw ConversationSchemaException("confidence must be numeric")
        val needClarification = json.get("need_clarification") as? Boolean
            ?: throw ConversationSchemaException("need_clarification must be boolean")
        if (!confidence.isFinite() || confidence !in 0.0..1.0) {
            throw ConversationSchemaException("Query-presentation confidence is out of range")
        }
        return QueryPresentationSemanticDecision(
            presentation = presentation,
            confidence = confidence,
            needClarification = needClarification
        )
    }

    private companion object {
        val REQUIRED_FIELDS = setOf("presentation", "confidence", "need_clarification")
    }
}

class QueryPresentationSemanticOrchestrator(
    private val semanticClient: QueryPresentationSemanticClient,
    private val parser: QueryPresentationSemanticParser = QueryPresentationSemanticParser()
) {
    suspend fun resolveForValidatedIntent(
        normalizedUserText: String,
        validatedTaskAgentIntent: String
    ): QueryPresentationSemanticResolution {
        if (validatedTaskAgentIntent != AiIntent.QUERY_TASK.name) {
            return resolution(
                presentation = TaskQueryPresentation.NONE,
                confidence = 0.0,
                source = QueryPresentationSemanticSource.NOT_APPLICABLE,
                agentAttempted = false,
                reason = "NON_QUERY_INTENT"
            )
        }
        return try {
            val decision = parser.parse(
                semanticClient.classifyQueryPresentation(normalizedUserText)
            )
            when {
                decision.needClarification -> fallback(decision.confidence, "CLARIFICATION_REQUIRED")
                decision.confidence < MIN_CONFIDENCE -> fallback(decision.confidence, "LOW_CONFIDENCE")
                decision.presentation == QueryPresentationSemanticMove.UNKNOWN ->
                    fallback(decision.confidence, "UNKNOWN_PRESENTATION")
                else -> resolution(
                    presentation = TaskQueryPresentation.valueOf(decision.presentation.name),
                    confidence = decision.confidence,
                    source = QueryPresentationSemanticSource.CONVERSATION_AGENT,
                    agentAttempted = true,
                    reason = "ACCEPTED"
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            fallback(confidence = 0.0, reason = "REQUEST_OR_SCHEMA_FAILURE")
        }
    }

    private fun fallback(confidence: Double, reason: String) = resolution(
        presentation = TaskQueryPresentation.NONE,
        confidence = confidence,
        source = QueryPresentationSemanticSource.EXISTING_PRESENTATION_FALLBACK,
        agentAttempted = true,
        reason = reason
    )

    private fun resolution(
        presentation: TaskQueryPresentation,
        confidence: Double,
        source: QueryPresentationSemanticSource,
        agentAttempted: Boolean,
        reason: String
    ): QueryPresentationSemanticResolution {
        Log.d(
            "QUERY_PRESENTATION_SEMANTIC",
            "result=$presentation confidence=$confidence source=${source.logValue} " +
                "fallbackReason=${if (presentation == TaskQueryPresentation.NONE) reason else "NONE"}"
        )
        return QueryPresentationSemanticResolution(
            presentation = presentation,
            confidence = confidence,
            source = source,
            agentAttempted = agentAttempted,
            reason = reason
        )
    }

    companion object {
        const val MIN_CONFIDENCE = 0.80
    }
}
