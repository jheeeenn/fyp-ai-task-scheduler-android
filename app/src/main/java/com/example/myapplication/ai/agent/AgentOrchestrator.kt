package com.example.myapplication.ai.agent

import android.util.Log
import com.example.myapplication.ai.AiParsedCommand
import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.routine.RoutineExtractionResponse
import com.example.myapplication.ai.routine.RoutineExtractionResponseParser
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionParser
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionResponse
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionValidator
import com.example.myapplication.ai.temporal.RelativeTemporalOperation
import com.example.myapplication.ai.temporal.RelativeTemporalProposalValidationException
import com.example.myapplication.ai.temporal.RelativeTemporalValidationFailure
import com.example.myapplication.ai.temporal.ValidatedRelativeTemporalCorrection
import com.example.myapplication.diagnostics.DebugDiagnosticLog
import kotlinx.coroutines.CancellationException

class TaskAgentProcessingException(message: String, cause: Throwable) : Exception(message, cause)

private enum class RelativeTemporalExtractionStage {
    INITIAL,
    CORRECTION,
    CORRECTION_REPAIR
}

private enum class RelativeTemporalRepairResult {
    REQUESTED,
    ACCEPTED,
    REJECTED,
    PARSE_FAILED,
    NOT_ELIGIBLE
}

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
            logParsedRelativeTemporalShape(RelativeTemporalExtractionStage.INITIAL, response)
            val validation = contextActionExtractionValidator.validateWithReport(
                response,
                expectedAction
            )
            logCanonicalization(
                RelativeTemporalExtractionStage.INITIAL,
                validation.canonicalizationReport.changedFields
            )
            val change = validation.changeSet
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
            logParsedRelativeTemporalShape(RelativeTemporalExtractionStage.CORRECTION, response)
            val correction = try {
                val validation = relativeTemporalCorrectionValidator.validateWithReport(response)
                logCanonicalization(
                    RelativeTemporalExtractionStage.CORRECTION,
                    validation.canonicalizationReport.changedFields
                )
                validation.correction
            } catch (exception: RelativeTemporalProposalValidationException) {
                if (!isEligibleCorrectionRepair(exception.failure)) {
                    logCorrectionRepair(exception.failure, RelativeTemporalRepairResult.NOT_ELIGIBLE)
                    throw exception
                }
                processRelativeTemporalCorrectionRepair(
                    normalizedText,
                    response,
                    exception.failure
                )
            }
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

    private suspend fun processRelativeTemporalCorrectionRepair(
        originalUserText: String,
        rejectedResponse: RelativeTemporalCorrectionResponse,
        validationFailure: RelativeTemporalValidationFailure
    ): ValidatedRelativeTemporalCorrection {
        logCorrectionRepair(validationFailure, RelativeTemporalRepairResult.REQUESTED)
        val rawContent = try {
            laptopAgentClient.processRelativeTemporalCorrectionRepair(
                originalUserText,
                rejectedResponse,
                validationFailure
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logCorrectionRepair(validationFailure, RelativeTemporalRepairResult.REJECTED)
            throw exception
        }
        val response = try {
            relativeTemporalCorrectionParser.parse(rawContent)
        } catch (exception: ContextActionExtractionParseException) {
            logCorrectionRepair(validationFailure, RelativeTemporalRepairResult.PARSE_FAILED)
            throw exception
        }
        logParsedRelativeTemporalShape(RelativeTemporalExtractionStage.CORRECTION_REPAIR, response)
        val validation = try {
            relativeTemporalCorrectionValidator.validateWithReport(response)
        } catch (exception: RelativeTemporalProposalValidationException) {
            logCorrectionRepair(validationFailure, RelativeTemporalRepairResult.REJECTED)
            throw exception
        }
        logCanonicalization(
            RelativeTemporalExtractionStage.CORRECTION_REPAIR,
            validation.canonicalizationReport.changedFields
        )
        logCorrectionRepair(validationFailure, RelativeTemporalRepairResult.ACCEPTED)
        return validation.correction
    }

    private fun isEligibleCorrectionRepair(
        failure: RelativeTemporalValidationFailure
    ): Boolean = failure in REPAIRABLE_CORRECTION_FAILURES

    private fun logCorrectionRepair(
        trigger: RelativeTemporalValidationFailure,
        result: RelativeTemporalRepairResult
    ) {
        Log.d(
            "RELATIVE_TEMPORAL_REPAIR",
            "stage=CORRECTION attempt=1 trigger=${trigger.name} result=${result.name}"
        )
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

    private fun logParsedRelativeTemporalShape(
        stage: RelativeTemporalExtractionStage,
        response: ContextActionExtractionResponse
    ) {
        logParsedRelativeTemporalShape(
            stage = stage,
            dateOperation = response.dateOperation,
            timeOperation = response.timeOperation,
            replacementDatePresent = response.replacementDateText.isNotBlank(),
            replacementTimePresent = response.replacementTimeText.isNotBlank(),
            dateOffsetDays = response.dateOffsetDays,
            timeOffsetMinutes = response.timeOffsetMinutes,
            confidence = response.confidence,
            clarificationRequired = response.needClarification
        )
    }

    private fun logParsedRelativeTemporalShape(
        stage: RelativeTemporalExtractionStage,
        response: RelativeTemporalCorrectionResponse
    ) {
        logParsedRelativeTemporalShape(
            stage = stage,
            dateOperation = response.dateOperation,
            timeOperation = response.timeOperation,
            replacementDatePresent = response.replacementDateText.isNotBlank(),
            replacementTimePresent = response.replacementTimeText.isNotBlank(),
            dateOffsetDays = response.dateOffsetDays,
            timeOffsetMinutes = response.timeOffsetMinutes,
            confidence = response.confidence,
            clarificationRequired = response.needClarification
        )
    }

    private fun logParsedRelativeTemporalShape(
        stage: RelativeTemporalExtractionStage,
        dateOperation: String,
        timeOperation: String,
        replacementDatePresent: Boolean,
        replacementTimePresent: Boolean,
        dateOffsetDays: Int,
        timeOffsetMinutes: Int,
        confidence: Double,
        clarificationRequired: Boolean
    ) {
        Log.d(
            "RELATIVE_TEMPORAL_PARSED",
            "stage=${stage.name} " +
                "dateOperation=${sanitizedOperation(dateOperation)} " +
                "timeOperation=${sanitizedOperation(timeOperation)} " +
                "replacementDatePresent=$replacementDatePresent " +
                "replacementTimePresent=$replacementTimePresent " +
                "dateOffsetDays=$dateOffsetDays " +
                "timeOffsetMinutes=$timeOffsetMinutes " +
                "confidence=$confidence " +
                "clarificationRequired=$clarificationRequired"
        )
    }

    private fun logCanonicalization(
        stage: RelativeTemporalExtractionStage,
        changedFields: List<String>
    ) {
        if (changedFields.isEmpty()) return
        Log.d(
            "RELATIVE_TEMPORAL_CANONICALIZED",
            "stage=${stage.name} fields=${changedFields.joinToString(",")}"
        )
    }

    private fun sanitizedOperation(value: String): String =
        value.takeIf { candidate ->
            RelativeTemporalOperation.entries.any { operation -> operation.name == candidate }
        } ?: "UNKNOWN"

    private companion object {
        val REPAIRABLE_CORRECTION_FAILURES = setOf(
            RelativeTemporalValidationFailure.MALFORMED_DATE_COMBINATION,
            RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION,
            RelativeTemporalValidationFailure.ZERO_DATE_OFFSET,
            RelativeTemporalValidationFailure.ZERO_TIME_OFFSET
        )
    }
}
