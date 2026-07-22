package com.example.myapplication.ai.conversation

import android.util.Log
import kotlinx.coroutines.CancellationException

class ConversationOrchestratorException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class ConversationOrchestrator(
    private val conversationAgentClient: ConversationAgentClient,
    private val parser: ConversationDecisionParser,
    private val responseParser: ConversationResponseParser = ConversationResponseParser(),
    private val memory: ConversationSessionMemory = ConversationSessionMemory()
) {
    /**
     * Retained for controlled future experiments with model-based observation verbalization.
     * HomeActivity renders authoritative production task responses deterministically for factual
     * completeness and accessibility.
     */
    suspend fun respondToObservation(
        observation: ExecutionObservation,
        appContextSummary: String
    ): ConversationResponse {
        memory.recordObservation(observation)
        return try {
            val rawContent = conversationAgentClient.respondToObservation(
                observationJson = observation.toAgentJson(),
                memorySnapshot = memory.snapshotForPrompt(),
                appContextSummary = appContextSummary
            )
            val response = responseParser.parse(rawContent)
            val expectedType = observation.outcome.toConversationResponseType()
            if (response.responseType != expectedType) {
                Log.e(
                    "CONVO_OBSERVATION",
                    "response type ${response.responseType} incompatible with authoritative outcome ${observation.outcome}; expected $expectedType"
                )
                throw ConversationSchemaException("ConversationResponse response_type does not match ExecutionObservation outcome")
            }
            memory.recordFinalSpokenResponse(response.speech)
            response
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CONVO_OBSERVATION", "response verbalization failed; using deterministic fallback", e)
            val fallback = ConversationResponse(
                speech = observation.fallbackSpeech,
                hint = observation.fallbackHint,
                responseType = observation.outcome.toConversationResponseType(),
                source = "deterministic_fallback"
            )
            memory.recordFinalSpokenResponse(fallback.speech)
            fallback
        }
    }

    fun recordDeterministicObservation(
        observation: ExecutionObservation,
        response: ConversationResponse
    ) {
        memory.recordObservation(observation)
        memory.recordFinalSpokenResponse(response.speech)
    }

    suspend fun process(
        normalizedText: String,
        appContextSummary: String,
        readOnlyTaskContextSnapshot: String = NO_TASK_CONTEXT
    ): ConversationDecision {
        memory.recordUser(normalizedText)
        val routingMemory = appendTaskContext(
            memorySnapshot = memory.snapshotForPrompt(),
            readOnlyTaskContextSnapshot = readOnlyTaskContextSnapshot
        )

        val parsed = try {
            val rawContent = conversationAgentClient.process(
                userText = normalizedText,
                memorySnapshot = routingMemory,
                appContextSummary = appContextSummary
            )
            parser.parse(rawContent)
        } catch (e: ConversationSchemaException) {
            retryWithRepair(normalizedText, appContextSummary, readOnlyTaskContextSnapshot, e)
        } catch (e: ConversationAgentResponseException) {
            retryWithRepair(normalizedText, appContextSummary, readOnlyTaskContextSnapshot, e)
        }

        val decision = normalizeDecision(parsed, normalizedText)
        memory.updateFromDecision(decision)
        return decision
    }

    private suspend fun retryWithRepair(
        normalizedText: String,
        appContextSummary: String,
        readOnlyTaskContextSnapshot: String,
        firstFailure: Exception
    ): ConversationDecision {
        Log.e("CONVO_ORCH_SCHEMA", "first response invalid, retrying once", firstFailure)

        return try {
            val repairContent = conversationAgentClient.processRepair(
                userText = normalizedText,
                appContextSummary = appendTaskContext(
                    memorySnapshot = appContextSummary,
                    readOnlyTaskContextSnapshot = readOnlyTaskContextSnapshot
                )
            )
            val repairedDecision = parser.parse(repairContent)
            Log.d("CONVO_ORCH_SCHEMA", "repair response accepted")
            repairedDecision
        } catch (repairFailure: Exception) {
            Log.e("CONVO_ORCH_SCHEMA", "repair response failed", repairFailure)
            throw ConversationOrchestratorException(
                "Conversation Agent failed after schema retry",
                repairFailure
            )
        }
    }

    private fun normalizeDecision(
        decision: ConversationDecision,
        normalizedText: String
    ): ConversationDecision {
        return when (decision.route) {
            ConversationRoute.TASK_COMMAND -> decision.copy(
                taskText = decision.taskText.ifBlank { normalizedText }
            )
            ConversationRoute.DIRECT_REPLY -> decision.copy(
                reply = decision.reply.ifBlank { "Hi. I can help you create, check, reschedule, delete, complete, or break down tasks." }
            )
            ConversationRoute.ASK_CLARIFICATION -> decision.copy(
                reply = decision.reply.ifBlank { "Could you say that another way, or tell me which task you mean?" }
            )
            ConversationRoute.UNKNOWN -> decision.copy(
                reply = decision.reply.ifBlank { "I can help with task scheduling. Try asking me to create, check, reschedule, delete, complete, or break down a task." }
            )
            ConversationRoute.END_SESSION -> decision.copy(
                reply = decision.reply.ifBlank { "Okay, stopping the assistant." },
                listenAgain = false
            )
        }
    }

    fun clearSessionMemory() {
        memory.clear()
    }

    private fun appendTaskContext(
        memorySnapshot: String,
        readOnlyTaskContextSnapshot: String
    ): String = buildString {
        append(memorySnapshot.trim())
        appendLine()
        appendLine()
        appendLine("Read-only task context:")
        append(
            readOnlyTaskContextSnapshot.takeIf { it.isNotBlank() }
                ?: NO_TASK_CONTEXT
        )
    }

    private companion object {
        val NO_TASK_CONTEXT = """
            Scope: NONE
            Generation: 0
            Items: None
            Truncated: false
        """.trimIndent()
    }
}
