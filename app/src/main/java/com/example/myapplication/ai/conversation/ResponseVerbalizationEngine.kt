package com.example.myapplication.ai.conversation

import android.util.Log
import kotlinx.coroutines.CancellationException

/** Shared presentation-only model call, validation, substitution, and deterministic fallback. */
class ResponseVerbalizationEngine(
    private val conversationAgentClient: ConversationAgentClient,
    private val parser: ResponseVerbalizationParser = ResponseVerbalizationParser()
) {
    suspend fun verbalize(plan: ResponseVerbalizationPlan): ConversationResponse {
        val startedAt = System.currentTimeMillis()
        Log.d(
            "RESPONSE_VERBALIZATION_REQUEST",
            "operation=${plan.operation} outcome=${plan.outcome} " +
                "responseType=${plan.responseType} act=${plan.responseAct} " +
                "meaning=${plan.meaningDetail} tone=${plan.tone} verbosity=${plan.verbosity}"
        )
        return try {
            val rawContent = conversationAgentClient.respondToObservation(
                observationJson = plan.toSafeAgentJson(),
                memorySnapshot = "",
                appContextSummary = ""
            )
            val envelope = parser.parse(rawContent)
            val validation = ResponseVerbalizationValidator.evaluate(plan, envelope)
            val latencyMs = System.currentTimeMillis() - startedAt
            Log.d(
                "RESPONSE_VERBALIZATION_RESULT",
                "operation=${plan.operation} outcome=${plan.outcome} " +
                    "accepted=${validation.accepted} source=conversation_agent " +
                    "validation=${validation.reason} latencyMs=$latencyMs"
            )
            if (!validation.accepted) {
                fallback(plan, validation.reason.name, latencyMs)
            } else {
                val response = plan.deterministicResponse.copy(
                    speech = ResponseVerbalizationComposer.compose(plan, envelope),
                    source = "conversation_agent_verbalization"
                )
                Log.d(
                    "RESPONSE_VERBALIZATION_ACCEPTED",
                    "operation=${plan.operation} outcome=${plan.outcome} " +
                        "source=${response.source} latencyMs=$latencyMs"
                )
                response
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ConversationSchemaException) {
            val latencyMs = System.currentTimeMillis() - startedAt
            Log.d(
                "RESPONSE_VERBALIZATION_RESULT",
                "operation=${plan.operation} outcome=${plan.outcome} " +
                    "accepted=false source=conversation_agent " +
                    "validation=${ResponseVerbalizationValidationReason.INVALID_FORMAT} " +
                    "latencyMs=$latencyMs"
            )
            fallback(
                plan,
                ResponseVerbalizationValidationReason.INVALID_FORMAT.name,
                latencyMs
            )
        } catch (e: Exception) {
            val latencyMs = System.currentTimeMillis() - startedAt
            val reason = if (
                e.message.orEmpty().contains("timed out", ignoreCase = true) ||
                e.message.orEmpty().contains("timeout", ignoreCase = true)
            ) {
                "TIMEOUT"
            } else {
                "REQUEST_FAILURE"
            }
            Log.e(
                "RESPONSE_VERBALIZATION_RESULT",
                "operation=${plan.operation} outcome=${plan.outcome} " +
                    "accepted=false source=conversation_agent validation=$reason " +
                    "latencyMs=$latencyMs",
                e
            )
            fallback(plan, reason, latencyMs)
        }
    }

    private fun fallback(
        plan: ResponseVerbalizationPlan,
        reason: String,
        latencyMs: Long
    ): ConversationResponse {
        Log.d(
            "RESPONSE_VERBALIZATION_FALLBACK",
            "operation=${plan.operation} outcome=${plan.outcome} reason=$reason " +
                "source=android_deterministic latencyMs=$latencyMs"
        )
        return plan.deterministicResponse
    }
}
