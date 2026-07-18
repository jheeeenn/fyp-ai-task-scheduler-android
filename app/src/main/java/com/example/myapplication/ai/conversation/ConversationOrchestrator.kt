package com.example.myapplication.ai.conversation

import android.util.Log

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
            memory.recordFinalSpokenResponse(response.speech)
            response
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

    suspend fun process(normalizedText: String, appContextSummary: String): ConversationDecision {
        memory.recordUser(normalizedText)

        val parsed = try {
            val rawContent = conversationAgentClient.process(
                userText = normalizedText,
                memorySnapshot = memory.snapshotForPrompt(),
                appContextSummary = appContextSummary
            )
            parser.parse(rawContent)
        } catch (e: ConversationSchemaException) {
            retryWithRepair(normalizedText, appContextSummary, e)
        } catch (e: ConversationAgentResponseException) {
            retryWithRepair(normalizedText, appContextSummary, e)
        }

        val decision = normalizeDecision(parsed, normalizedText)
        memory.updateFromDecision(decision)
        return decision
    }

    private suspend fun retryWithRepair(
        normalizedText: String,
        appContextSummary: String,
        firstFailure: Exception
    ): ConversationDecision {
        Log.e("CONVO_ORCH_SCHEMA", "first response invalid, retrying once", firstFailure)

        return try {
            val repairContent = conversationAgentClient.processRepair(
                userText = normalizedText,
                appContextSummary = appContextSummary
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
}
