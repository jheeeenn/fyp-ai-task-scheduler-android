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
    CONVERSATION_AGENT_PRIMARY("conversation_agent_primary"),
    LOCAL_SAFETY_REFLEX("local_safety_reflex"),
    LOCAL_FAILURE_FALLBACK("local_failure_fallback"),
    DETERMINISTIC_UNKNOWN("deterministic_unknown")
}

data class CreateDraftMoveResolution(
    val move: CreateDraftMove,
    val source: CreateDraftMoveSource,
    val confidence: Double,
    val agentAttempted: Boolean
)

class CreateDraftSemanticOrchestrator(
    private val localInterpreter: CreateDraftMoveInterpreter,
    private val semanticClient: CreateDraftSemanticClient,
    private val parser: CreateDraftAgentDecisionParser = CreateDraftAgentDecisionParser(),
    private val validator: CreateDraftAgentDecisionValidator = CreateDraftAgentDecisionValidator()
) {
    fun proposeLocal(userText: String, state: CreateTaskDialogState): CreateDraftMove =
        localInterpreter.interpret(userText, state)

    fun resolveImmediate(
        localCandidate: CreateDraftMove,
        state: CreateTaskDialogState
    ): CreateDraftMoveResolution? {
        if (state == CreateTaskDialogState.READY_TO_SAVE) {
            return deterministicUnknown(agentAttempted = false)
        }
        if (localCandidate == CreateDraftMove.Cancel) {
            return CreateDraftMoveResolution(
                move = CreateDraftMove.Cancel,
                source = CreateDraftMoveSource.LOCAL_SAFETY_REFLEX,
                confidence = 1.0,
                agentAttempted = false
            )
        }
        return null
    }

    suspend fun resolve(
        userText: String,
        state: CreateTaskDialogState,
        context: CreateDraftAgentContext,
        localCandidate: CreateDraftMove
    ): CreateDraftMoveResolution {
        resolveImmediate(localCandidate, state)?.let { return it }

        return try {
            val rawContent = semanticClient.interpretCreateDraftMove(userText, context.toPromptText())
            val decision = parser.parse(rawContent)
            val validation = validator.validate(decision, state)
            if (validation.accepted) {
                Log.d("CREATE_MOVE_PRIMARY", "state=$state category=ACCEPTED")
                CreateDraftMoveResolution(
                    move = validation.move,
                    source = CreateDraftMoveSource.CONVERSATION_AGENT_PRIMARY,
                    confidence = decision.confidence,
                    agentAttempted = true
                )
            } else {
                Log.d("CREATE_MOVE_PRIMARY", "state=$state category=VALIDATION_REJECTED")
                localFailureFallback(localCandidate, state)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CREATE_MOVE_PRIMARY", "state=$state category=FAILED", e)
            localFailureFallback(localCandidate, state)
        }
    }

    private fun localFailureFallback(
        localCandidate: CreateDraftMove,
        state: CreateTaskDialogState
    ): CreateDraftMoveResolution {
        val validation = validator.validateLocalCandidate(localCandidate, state)
        return if (validation.accepted) {
            CreateDraftMoveResolution(
                move = validation.move,
                source = CreateDraftMoveSource.LOCAL_FAILURE_FALLBACK,
                confidence = 1.0,
                agentAttempted = true
            )
        } else {
            deterministicUnknown(agentAttempted = true)
        }
    }

    private fun deterministicUnknown(agentAttempted: Boolean) = CreateDraftMoveResolution(
        move = CreateDraftMove.Unknown,
        source = CreateDraftMoveSource.DETERMINISTIC_UNKNOWN,
        confidence = 0.0,
        agentAttempted = agentAttempted
    )
}
