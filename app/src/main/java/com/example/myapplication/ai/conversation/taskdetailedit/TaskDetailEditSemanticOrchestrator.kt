package com.example.myapplication.ai.conversation.taskdetailedit

import android.util.Log
import com.example.myapplication.voice.TextNormalizer
import kotlinx.coroutines.CancellationException

fun interface TaskDetailEditSemanticClient {
    suspend fun interpretTaskDetailEditMove(userText: String, contextSummary: String): String
}

class TaskDetailEditSemanticOrchestrator(
    private val semanticClient: TaskDetailEditSemanticClient,
    private val parser: TaskDetailEditAgentDecisionParser = TaskDetailEditAgentDecisionParser(),
    private val validator: TaskDetailEditAgentDecisionValidator = TaskDetailEditAgentDecisionValidator()
) {
    fun resolveImmediate(userText: String): TaskDetailEditMoveResolution? {
        if (TextNormalizer.normalize(userText) !in SAFETY_CANCEL_PHRASES) return null
        return TaskDetailEditMoveResolution(
            proposal = TaskDetailEditProposal.Cancel,
            move = TaskDetailEditAgentMove.CANCEL,
            source = TaskDetailEditMoveSource.LOCAL_SAFETY_REFLEX,
            confidence = 1.0,
            agentAttempted = false,
            reason = "EXPLICIT_CANCEL"
        )
    }

    suspend fun resolve(
        userText: String,
        context: TaskDetailEditAgentContext,
        localCandidate: TaskDetailEditLocalCandidate
    ): TaskDetailEditMoveResolution {
        return try {
            val raw = semanticClient.interpretTaskDetailEditMove(userText, context.toPromptText())
            val decision = try {
                parser.parse(raw)
            } catch (schemaFailure: Exception) {
                Log.d("TASK_DETAIL_EDIT_RESOLUTION", boundedLog(context, "UNKNOWN", "conversation_agent_primary", 0.0, true, "SCHEMA_REPAIR"))
                return attemptRepair(userText, context, localCandidate)
            }
            if (decision.move == TaskDetailEditAgentMove.UNKNOWN) {
                return attemptRepair(userText, context, localCandidate)
            }
            val validation = validator.validate(decision, context.requestedField)
            if (!validation.accepted) {
                return localFallback(context, localCandidate, validation.reason)
            }
            resolution(
                context = context,
                proposal = validation.proposal,
                move = decision.move,
                source = TaskDetailEditMoveSource.CONVERSATION_AGENT_PRIMARY,
                confidence = decision.confidence,
                attempted = true,
                reason = validation.reason
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.d("TASK_DETAIL_EDIT_RESOLUTION", boundedLog(context, "UNKNOWN", "conversation_agent_primary", 0.0, true, "AGENT_FAILED"))
            localFallback(context, localCandidate, "AGENT_FAILED")
        }
    }

    private suspend fun attemptRepair(
        userText: String,
        context: TaskDetailEditAgentContext,
        localCandidate: TaskDetailEditLocalCandidate
    ): TaskDetailEditMoveResolution = try {
        val raw = semanticClient.interpretTaskDetailEditMove(userText, context.toRepairPromptText())
        val decision = parser.parse(raw)
        val validation = validator.validate(decision, context.requestedField)
        if (decision.move == TaskDetailEditAgentMove.UNKNOWN || !validation.accepted) {
            localFallback(context, localCandidate, "REPAIR_REJECTED_${validation.reason}")
        } else {
            resolution(
                context,
                validation.proposal,
                decision.move,
                TaskDetailEditMoveSource.CONVERSATION_AGENT_REPAIR,
                decision.confidence,
                attempted = true,
                reason = "REPAIR_ACCEPTED"
            )
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        localFallback(context, localCandidate, "REPAIR_FAILED")
    }

    private fun localFallback(
        context: TaskDetailEditAgentContext,
        candidate: TaskDetailEditLocalCandidate,
        reason: String
    ): TaskDetailEditMoveResolution {
        val proposal = when (candidate) {
            is TaskDetailEditLocalCandidate.Title -> TaskDetailEditProposal.Title(candidate.value)
            is TaskDetailEditLocalCandidate.Schedule ->
                TaskDetailEditProposal.Schedule(candidate.dueDate, candidate.dueTime)
            is TaskDetailEditLocalCandidate.Clarification ->
                TaskDetailEditProposal.Clarification(candidate.question)
            TaskDetailEditLocalCandidate.Invalid -> TaskDetailEditProposal.Unknown
        }
        val source = if (proposal == TaskDetailEditProposal.Unknown) {
            TaskDetailEditMoveSource.DETERMINISTIC_UNKNOWN
        } else {
            TaskDetailEditMoveSource.LOCAL_FAILURE_FALLBACK
        }
        return resolution(
            context,
            proposal,
            moveFor(proposal),
            source,
            confidence = if (proposal == TaskDetailEditProposal.Unknown) 0.0 else 1.0,
            attempted = true,
            reason = reason
        )
    }

    private fun resolution(
        context: TaskDetailEditAgentContext,
        proposal: TaskDetailEditProposal,
        move: TaskDetailEditAgentMove,
        source: TaskDetailEditMoveSource,
        confidence: Double,
        attempted: Boolean,
        reason: String
    ): TaskDetailEditMoveResolution {
        Log.d("TASK_DETAIL_EDIT_RESOLUTION", boundedLog(context, move.name, source.logValue, confidence, attempted, reason))
        return TaskDetailEditMoveResolution(proposal, move, source, confidence, attempted, reason)
    }

    private fun boundedLog(
        context: TaskDetailEditAgentContext,
        move: String,
        source: String,
        confidence: Double,
        attempted: Boolean,
        reason: String
    ): String = "target=${context.requestedField} state=${context.interactionState} move=$move " +
        "source=$source confidence=$confidence agentAttempted=$attempted reason=$reason " +
        "draftRevision=${context.draftRevision} generation=${context.interactionGeneration}"

    private fun moveFor(proposal: TaskDetailEditProposal): TaskDetailEditAgentMove = when (proposal) {
        is TaskDetailEditProposal.Title -> TaskDetailEditAgentMove.SET_TITLE
        is TaskDetailEditProposal.Schedule -> TaskDetailEditAgentMove.SET_SCHEDULE
        is TaskDetailEditProposal.Clarification -> TaskDetailEditAgentMove.ASK_CLARIFICATION
        TaskDetailEditProposal.Cancel -> TaskDetailEditAgentMove.CANCEL
        TaskDetailEditProposal.Unknown -> TaskDetailEditAgentMove.UNKNOWN
    }

    private companion object {
        val SAFETY_CANCEL_PHRASES = setOf("cancel", "stop", "never mind", "nevermind")
    }
}
