package com.example.myapplication.ai.routine.followup

import com.example.myapplication.ai.routine.PendingRoutineDraft
import com.example.myapplication.ai.routine.RoutineDraftState
import com.example.myapplication.ai.routine.RoutineFollowUpInterpreter
import com.example.myapplication.ai.routine.RoutineFollowUpMove
import com.example.myapplication.diagnostics.DebugDiagnosticLog
import kotlinx.coroutines.CancellationException

fun interface RoutineFollowUpSemanticClient {
    suspend fun interpretRoutineFollowUp(
        userText: String,
        contextSummary: String
    ): String
}

enum class RoutineFollowUpMoveSource {
    LOCAL,
    SEMANTIC_PRIMARY,
    SEMANTIC_FAILURE
}

data class RoutineFollowUpMoveResolution(
    val move: RoutineFollowUpMove,
    val source: RoutineFollowUpMoveSource,
    val confidence: Double,
    val validationResult: String,
    val agentAttempted: Boolean
)

class RoutineFollowUpSemanticOrchestrator(
    private val semanticClient: RoutineFollowUpSemanticClient,
    private val parser: RoutineFollowUpAgentDecisionParser =
        RoutineFollowUpAgentDecisionParser(),
    private val validator: RoutineFollowUpAgentDecisionValidator =
        RoutineFollowUpAgentDecisionValidator()
) {
    fun proposeLocal(userText: String): RoutineFollowUpMove =
        RoutineFollowUpInterpreter.interpret(userText)

    fun resolveImmediate(
        localMove: RoutineFollowUpMove,
        state: RoutineDraftState,
        draft: PendingRoutineDraft?
    ): RoutineFollowUpMoveResolution? {
        val validation = validator.validateLocal(localMove, state, draft)
        return if (validation.accepted) {
            RoutineFollowUpMoveResolution(
                move = validation.move,
                source = RoutineFollowUpMoveSource.LOCAL,
                confidence = 1.0,
                validationResult = validation.reason,
                agentAttempted = false
            )
        } else {
            null
        }
    }

    suspend fun resolveSemantic(
        userText: String,
        context: RoutineFollowUpAgentContext,
        localMove: RoutineFollowUpMove
    ): RoutineFollowUpMoveResolution {
        return try {
            val rawContent = semanticClient.interpretRoutineFollowUp(
                userText,
                context.toPromptText()
            )
            val decision = parser.parse(rawContent)
            val validation = validator.validate(
                decision = decision,
                state = context.state,
                draft = context.toValidationDraft(),
                userText = userText,
                localMove = localMove
            )
            DebugDiagnosticLog.event(
                "ROUTINE_MOVE_AGENT_DECISION",
                "capturedState=${context.state.name}\n" +
                    "capturedRevision=${context.revision}\n" +
                    "move=${decision.move.name}\n" +
                    "stepIndex=${decision.stepIndex}\n" +
                    "value=${decision.value}\n" +
                    "confidence=${decision.confidence}\n" +
                    "validationResult=${validation.reason}\n" +
                    "source=SEMANTIC_PRIMARY"
            )
            RoutineFollowUpMoveResolution(
                move = validation.move,
                source = RoutineFollowUpMoveSource.SEMANTIC_PRIMARY,
                confidence = decision.confidence,
                validationResult = validation.reason,
                agentAttempted = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            RoutineFollowUpMoveResolution(
                move = RoutineFollowUpMove.Unknown,
                source = RoutineFollowUpMoveSource.SEMANTIC_FAILURE,
                confidence = 0.0,
                validationResult = "REQUEST_OR_SCHEMA_FAILURE",
                agentAttempted = true
            )
        }
    }

    private fun RoutineFollowUpAgentContext.toValidationDraft(): PendingRoutineDraft? {
        if (steps.isEmpty()) return null
        return PendingRoutineDraft(
            title = routineTitle,
            revision = revision,
            steps = steps.map { step ->
                com.example.myapplication.ai.routine.PendingRoutineStep(
                    title = step.title,
                    originalDateText = step.unresolvedDatePhrase.ifBlank { null },
                    originalTimeText = step.unresolvedTimePhrase.ifBlank { null },
                    resolvedDate = step.resolvedDate.ifBlank { null },
                    resolvedTime = step.resolvedTime.ifBlank { null }
                )
            }
        )
    }
}
