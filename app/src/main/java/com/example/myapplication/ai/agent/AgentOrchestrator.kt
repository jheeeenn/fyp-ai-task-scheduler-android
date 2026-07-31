package com.example.myapplication.ai.agent

import android.util.Log
import com.example.myapplication.ai.AiParsedCommand
import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.routine.RoutineExtractionResponse
import com.example.myapplication.ai.routine.RoutineExtractionResponseParser
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionParser
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionValidator
import com.example.myapplication.ai.temporal.ValidatedRelativeTemporalCorrection
import com.example.myapplication.diagnostics.DebugDiagnosticLog
import kotlinx.coroutines.CancellationException

class TaskAgentProcessingException(message: String, cause: Throwable) : Exception(message, cause)

class AgentOrchestrator(
    private val laptopAgentClient: LaptopAgentClient,
    private val taskAgentResponseParser: TaskAgentResponseParser,
    private val taskActionNormalizer: TaskActionNormalizer,
    private val actionValidator: ActionValidator,
    private val contextActionExtractionParser: ContextActionExtractionResponseParser =
        ContextActionExtractionResponseParser(),
    private val contextActionExtractionValidator: ContextActionExtractionValidator =
        ContextActionExtractionValidator(),
    private val relativeTemporalCorrectionParser: RelativeTemporalCorrectionParser =
        RelativeTemporalCorrectionParser(),
    private val relativeTemporalCorrectionValidator: RelativeTemporalCorrectionValidator =
        RelativeTemporalCorrectionValidator(),
    private val routineExtractionParser: RoutineExtractionResponseParser =
        RoutineExtractionResponseParser()
) {
    suspend fun process(normalizedText: String): AiParsedCommand {
        return try {
            Log.d("AGENT_ORCHESTRATOR", "Trying LM Studio task agent first")
            val rawContent = laptopAgentClient.process(normalizedText)
            val agentResponse = taskAgentResponseParser.parse(rawContent)
            val normalizedCommand = taskActionNormalizer.normalize(agentResponse)
            val validatedCommand = actionValidator.validate(normalizedCommand)
            Log.d("AGENT_ORCHESTRATOR", "LM Studio task agent accepted ${validatedCommand.intent}")
            validatedCommand
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugDiagnosticLog.event(
                "AGENT_ORCHESTRATOR",
                "Task agent failed closed for text='$normalizedText'; category=${e::class.java.simpleName}"
            )
            throw TaskAgentProcessingException("Task agent failed to process command", e)
        }
    }

    suspend fun processContextAction(
        normalizedText: String,
        expectedAction: ConversationContextAction
    ): ContextActionChangeSet {
        return try {
            Log.d("AGENT_ORCHESTRATOR", "Trying bounded context-action extraction")
            val rawContent = laptopAgentClient.processContextAction(normalizedText, expectedAction)
            val response = contextActionExtractionParser.parse(rawContent)
            val change = contextActionExtractionValidator.validate(response, expectedAction)
            change.temporalProposal?.let { proposal ->
                Log.d(
                    "RELATIVE_TEMPORAL_EXTRACTION",
                    "dateOperation=${proposal.dateOperation} " +
                        "timeOperation=${proposal.timeOperation} " +
                        "relativeBase=${proposal.relativeBase} " +
                        "confidence=${proposal.confidence} " +
                        "clarificationRequired=${proposal.needClarification}"
                )
                Log.d("RELATIVE_TEMPORAL_VALIDATION", "result=ACCEPTED failureReason=NONE")
            }
            change
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(
                "RELATIVE_TEMPORAL_VALIDATION",
                "result=REJECTED failureReason=${e::class.java.simpleName}"
            )
            Log.e("AGENT_ORCHESTRATOR", "Context-action extraction failed closed", e)
            throw TaskAgentProcessingException("Task agent failed to extract context action", e)
        }
    }

    suspend fun processRelativeTemporalCorrection(
        normalizedText: String
    ): ValidatedRelativeTemporalCorrection {
        return try {
            val rawContent = laptopAgentClient.processRelativeTemporalCorrection(normalizedText)
            val response = relativeTemporalCorrectionParser.parse(rawContent)
            val correction = relativeTemporalCorrectionValidator.validate(response)
            val proposal = (correction as? ValidatedRelativeTemporalCorrection.Apply)?.proposal
            Log.d(
                "RELATIVE_TEMPORAL_EXTRACTION",
                "dateOperation=${proposal?.dateOperation ?: "KEEP"} " +
                    "timeOperation=${proposal?.timeOperation ?: "KEEP"} " +
                    "relativeBase=${proposal?.relativeBase ?: "AUTHORITATIVE_TASK"} " +
                    "confidence=${proposal?.confidence ?: response.confidence} " +
                    "clarificationRequired=${response.needClarification}"
            )
            Log.d("RELATIVE_TEMPORAL_VALIDATION", "result=ACCEPTED failureReason=NONE")
            correction
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(
                "RELATIVE_TEMPORAL_VALIDATION",
                "result=REJECTED failureReason=${e::class.java.simpleName}"
            )
            throw TaskAgentProcessingException(
                "Task agent failed to interpret relative-temporal correction",
                e
            )
        }
    }

    suspend fun processRoutine(normalizedText: String): RoutineExtractionResponse {
        return try {
            Log.d("ROUTINE_EXTRACTION", "Starting bounded routine extraction")
            val rawContent = laptopAgentClient.processRoutine(normalizedText)
            val response = routineExtractionParser.parse(rawContent)
            DebugDiagnosticLog.event(
                "ROUTINE_EXTRACTION_DEBUG",
                "routineTitle=${response.routineTitle}\n" +
                    "stepCount=${response.steps.size}\n" +
                    "confidence=${response.confidence}\n" +
                    "needClarification=${response.needClarification}"
            )
            response.steps.forEachIndexed { index, step ->
                DebugDiagnosticLog.event(
                    "ROUTINE_EXTRACTION_STEP",
                    "index=${index + 1}\n" +
                        "title=${step.title}\n" +
                        "dateText=${step.dateText}\n" +
                        "timeText=${step.timeText}"
                )
            }
            Log.d(
                "ROUTINE_EXTRACTION",
                "stepCount=${response.steps.size} " +
                    "needClarification=${response.needClarification}"
            )
            response
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ROUTINE_EXTRACTION", "Routine extraction failed closed", e)
            throw TaskAgentProcessingException(
                "Task agent failed to extract a routine",
                e
            )
        }
    }
}
