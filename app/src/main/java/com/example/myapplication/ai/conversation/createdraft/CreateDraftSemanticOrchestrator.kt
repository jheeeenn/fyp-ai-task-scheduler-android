package com.example.myapplication.ai.conversation.createdraft

import android.util.Log
import com.example.myapplication.voice.CreateDraftMove
import com.example.myapplication.voice.CreateDraftMoveInterpreter
import com.example.myapplication.voice.CreateTaskDialogState
import kotlinx.coroutines.CancellationException

fun interface CreateDraftSemanticClient {
    suspend fun interpretCreateDraftMove(userText: String, contextSummary: String): String
}

enum class CreateDraftMoveSource(val logValue: String) {
    LOCAL("local"),
    CONVERSATION_AGENT_FALLBACK("conversation_agent_fallback"),
    DETERMINISTIC_FALLBACK("deterministic_fallback")
}

enum class CreateDraftFallbackReason {
    LOCAL_UNKNOWN,
    TEMPORAL_UNRESOLVED,
    UNSPECIFIED_CORRECTION_UNRESOLVED
}

data class CreateDraftMoveResolution(
    val move: CreateDraftMove,
    val source: CreateDraftMoveSource,
    val confidence: Double,
    val fallbackAttempted: Boolean
)

class CreateDraftSemanticOrchestrator(
    private val localInterpreter: CreateDraftMoveInterpreter,
    private val semanticClient: CreateDraftSemanticClient,
    private val parser: CreateDraftAgentDecisionParser = CreateDraftAgentDecisionParser(),
    private val validator: CreateDraftAgentDecisionValidator = CreateDraftAgentDecisionValidator()
) {
    fun resolveLocal(userText: String, state: CreateTaskDialogState): CreateDraftMoveResolution {
        return CreateDraftMoveResolution(
            move = localInterpreter.interpret(userText, state),
            source = CreateDraftMoveSource.LOCAL,
            confidence = 1.0,
            fallbackAttempted = false
        )
    }

    suspend fun resolve(
        userText: String,
        state: CreateTaskDialogState,
        context: CreateDraftAgentContext,
        fallbackReason: CreateDraftFallbackReason? = null
    ): CreateDraftMoveResolution {
        val local = resolveLocal(userText, state)
        val reason = fallbackReason ?: if (local.move == CreateDraftMove.Unknown) {
            CreateDraftFallbackReason.LOCAL_UNKNOWN
        } else {
            return local
        }

        if (state == CreateTaskDialogState.READY_TO_SAVE) {
            return deterministicUnknown(fallbackAttempted = false)
        }

        return try {
            val rawContent = semanticClient.interpretCreateDraftMove(userText, context.toPromptText())
            val decision = parser.parse(rawContent)
            val validation = validator.validate(decision, state)
            val result = if (validation.accepted) {
                CreateDraftMoveResolution(
                    move = validation.move,
                    source = CreateDraftMoveSource.CONVERSATION_AGENT_FALLBACK,
                    confidence = decision.confidence,
                    fallbackAttempted = true
                )
            } else {
                deterministicUnknown(fallbackAttempted = true)
            }
            logFallback(reason, state, if (validation.accepted) "ACCEPTED" else "VALIDATION_REJECTED")
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CREATE_MOVE_FALLBACK", "reason=$reason state=$state category=FAILED", e)
            deterministicUnknown(fallbackAttempted = true)
        }
    }

    private fun deterministicUnknown(fallbackAttempted: Boolean) = CreateDraftMoveResolution(
        move = CreateDraftMove.Unknown,
        source = CreateDraftMoveSource.DETERMINISTIC_FALLBACK,
        confidence = 0.0,
        fallbackAttempted = fallbackAttempted
    )

    private fun logFallback(
        reason: CreateDraftFallbackReason,
        state: CreateTaskDialogState,
        category: String
    ) {
        Log.d("CREATE_MOVE_FALLBACK", "reason=$reason state=$state category=$category")
    }
}
