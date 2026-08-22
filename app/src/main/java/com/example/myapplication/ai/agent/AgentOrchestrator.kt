package com.example.myapplication.ai.agent

import android.util.Log
import com.example.myapplication.ai.AiParsedCommand
import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.routine.RoutineExtractionResponse
import com.example.myapplication.ai.routine.RoutineExtractionResponseParser
import com.example.myapplication.ai.temporal.RelativeTemporalBase
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionContext
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionParser
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionRelation
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionRelationMapper
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionResponse
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionValidator
import com.example.myapplication.ai.temporal.RelativeTemporalOperation
import com.example.myapplication.ai.temporal.RelativeTemporalProposalValidationException
import com.example.myapplication.ai.temporal.RelativeTemporalRepairCandidate
import com.example.myapplication.ai.temporal.RelativeTemporalRepairCandidateBuilder
import com.example.myapplication.ai.temporal.RelativeTemporalRepairChoiceParser
import com.example.myapplication.ai.temporal.RelativeTemporalRepairChoiceResponse
import com.example.myapplication.ai.temporal.RelativeTemporalRepairChoiceValidationException
import com.example.myapplication.ai.temporal.RelativeTemporalRepairChoiceValidator
import com.example.myapplication.ai.temporal.RelativeTemporalValidationFailure
import com.example.myapplication.ai.temporal.ValidatedRelativeTemporalCorrection
import com.example.myapplication.diagnostics.DebugDiagnosticLog
import kotlinx.coroutines.CancellationException

class TaskAgentProcessingException(message: String, cause: Throwable) : Exception(message, cause)

private enum class RelativeTemporalExtractionStage {
    INITIAL,
    CORRECTION,
    CORRECTION_RECONSTRUCTED
}

private enum class RelativeTemporalRepairResult {
    REQUESTED,
    ACCEPTED,
    REJECTED,
    PARSE_FAILED,
    NOT_ELIGIBLE
}

private enum class RelativeTemporalRepairChoiceResult {
    SELECTED,
    REJECTED
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
    private val relativeTemporalRepairCandidateBuilder: RelativeTemporalRepairCandidateBuilder =
        RelativeTemporalRepairCandidateBuilder(relativeTemporalCorrectionValidator),
    private val relativeTemporalRepairChoiceParser: RelativeTemporalRepairChoiceParser =
        RelativeTemporalRepairChoiceParser(),
    private val relativeTemporalRepairChoiceValidator: RelativeTemporalRepairChoiceValidator =
        RelativeTemporalRepairChoiceValidator(),
    private val routineExtractionParser: RoutineExtractionResponseParser =
        RoutineExtractionResponseParser(),
    private val initialContextRescheduleRecoveryPolicy:
        InitialContextRescheduleClarificationRecoveryPolicy =
            InitialContextRescheduleClarificationRecoveryPolicy()
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
            val validation = try {
                contextActionExtractionValidator.validateWithReport(response, expectedAction)
            } catch (exception: RelativeTemporalProposalValidationException) {
                if (exception.failure !in INITIAL_CONTEXT_RECOVERABLE_FAILURES) {
                    throw exception
                }
                when (
                    val recovery = initialContextRescheduleRecoveryPolicy.recover(
                        originalNormalizedRequest = normalizedText,
                        response = response,
                        expectedAction = expectedAction,
                        validationFailure = exception.failure
                    )
                ) {
                    is InitialContextRescheduleRecoveryResult.Accepted -> {
                        val recoveredValidation =
                            contextActionExtractionValidator.validateWithReport(
                                recovery.response,
                                expectedAction
                            )
                        Log.d(
                            "INITIAL_CONTEXT_RESCHEDULE_RECOVERY",
                            "trigger=${exception.failure} result=ACCEPTED_EXACT_ANDROID_MATCH"
                        )
                        recoveredValidation
                    }
                    is InitialContextRescheduleRecoveryResult.Rejected -> {
                        Log.d(
                            "INITIAL_CONTEXT_RESCHEDULE_RECOVERY",
                            "trigger=${exception.failure} result=REJECTED reason=${recovery.reason}"
                        )
                        throw exception
                    }
                }
            }
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
                "result=REJECTED failureReason=${relativeTemporalFailureReason(e)}"
            )
            Log.e("AGENT_ORCHESTRATOR", "Context-action extraction failed closed", e)
            throw TaskAgentProcessingException("Task agent failed to extract context action", e)
        }
    }

    suspend fun processRelativeTemporalCorrection(
        normalizedText: String,
        context: RelativeTemporalCorrectionContext
    ): ValidatedRelativeTemporalCorrection {
        return try {
            logRelativeTemporalCorrectionContext(context)
            val rawContent = laptopAgentClient.processRelativeTemporalCorrection(
                normalizedText,
                context
            )
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
                "result=REJECTED failureReason=${relativeTemporalFailureReason(e)}"
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
        val candidates = relativeTemporalRepairCandidateBuilder.build(
            rejectedResponse,
            validationFailure
        )
        logCorrectionRepairCandidates(validationFailure, candidates.size)
        if (candidates.isEmpty()) {
            logCorrectionRepair(validationFailure, RelativeTemporalRepairResult.REJECTED)
            throw IllegalArgumentException("No valid relative-temporal repair candidates")
        }
        logCorrectionRepair(validationFailure, RelativeTemporalRepairResult.REQUESTED)
        val rawContent = try {
            laptopAgentClient.processRelativeTemporalCorrectionRepair(
                originalUserText,
                validationFailure,
                candidates
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logCorrectionRepair(validationFailure, RelativeTemporalRepairResult.REJECTED)
            throw exception
        }
        val choiceResponse = try {
            relativeTemporalRepairChoiceParser.parse(rawContent)
        } catch (exception: ContextActionExtractionParseException) {
            logCorrectionRepair(validationFailure, RelativeTemporalRepairResult.PARSE_FAILED)
            throw exception
        }
        val choice = try {
            relativeTemporalRepairChoiceValidator.validate(choiceResponse, candidates)
        } catch (exception: RelativeTemporalRepairChoiceValidationException) {
            logCorrectionRepairChoice(
                choiceResponse,
                candidates,
                RelativeTemporalRepairChoiceResult.REJECTED
            )
            logCorrectionRepair(validationFailure, RelativeTemporalRepairResult.REJECTED)
            throw exception
        }
        logCorrectionRepairChoice(
            choiceResponse,
            candidates,
            RelativeTemporalRepairChoiceResult.SELECTED
        )
        val reconstructed = relativeTemporalRepairCandidateBuilder.reconstructResponse(
            choice.candidate,
            choice.confidence
        )
        logParsedRelativeTemporalShape(
            RelativeTemporalExtractionStage.CORRECTION_RECONSTRUCTED,
            reconstructed
        )
        val validation = try {
            relativeTemporalCorrectionValidator.validateWithReport(reconstructed)
        } catch (exception: RelativeTemporalProposalValidationException) {
            logCorrectionRepair(validationFailure, RelativeTemporalRepairResult.REJECTED)
            throw exception
        }
        logCanonicalization(
            RelativeTemporalExtractionStage.CORRECTION_RECONSTRUCTED,
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

    private fun logCorrectionRepairCandidates(
        trigger: RelativeTemporalValidationFailure,
        candidateCount: Int
    ) {
        Log.d(
            "RELATIVE_TEMPORAL_REPAIR_CANDIDATES",
            "stage=CORRECTION trigger=${trigger.name} candidateCount=$candidateCount"
        )
    }

    private fun logCorrectionRepairChoice(
        response: RelativeTemporalRepairChoiceResponse,
        candidates: List<RelativeTemporalRepairCandidate>,
        result: RelativeTemporalRepairChoiceResult
    ) {
        val allowedRefs = candidates.mapTo(mutableSetOf()) { it.choiceRef }.apply { add("CLARIFY") }
        val choiceRef = response.choiceRef.takeIf { it in allowedRefs } ?: "UNKNOWN"
        val preservedRelativeBase = candidates.singleOrNull {
            it.choiceRef == response.choiceRef
        }?.response?.correctionRelation
            ?.let(RelativeTemporalCorrectionRelationMapper::map)
            ?.name ?: "UNKNOWN"
        val confidence = response.confidence.takeIf { it.isFinite() } ?: -1.0
        Log.d(
            "RELATIVE_TEMPORAL_REPAIR_CHOICE",
            "attempt=1 choiceRef=$choiceRef preservedRelativeBase=$preservedRelativeBase " +
                "confidence=$confidence result=${result.name}"
        )
    }

    private fun logRelativeTemporalCorrectionContext(
        context: RelativeTemporalCorrectionContext
    ) {
        Log.d(
            "RELATIVE_TEMPORAL_CORRECTION_CONTEXT",
            "revision=${context.proposalRevision} " +
                "previousDateOperation=${context.previousDateOperation} " +
                "previousTimeOperation=${context.previousTimeOperation} " +
                "previousRelativeBase=${context.previousRelativeBase} " +
                "previousDateOffsetDays=${context.previousDateOffsetDays} " +
                "previousTimeOffsetMinutes=${context.previousTimeOffsetMinutes} " +
                "previousDateLiteralPresent=${context.previousDateLiteralPresent} " +
                "previousTimeLiteralPresent=${context.previousTimeLiteralPresent}"
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
            correctionRelation = "NOT_APPLICABLE",
            mappedRelativeBase = response.relativeBase,
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
            correctionRelation = response.correctionRelation,
            mappedRelativeBase = RelativeTemporalCorrectionRelationMapper.map(
                response.correctionRelation
            )?.name ?: "UNKNOWN",
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
        correctionRelation: String,
        mappedRelativeBase: String,
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
                "correctionRelation=${sanitizedCorrectionRelation(correctionRelation)} " +
                "mappedRelativeBase=${sanitizedRelativeBase(mappedRelativeBase)} " +
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

    private fun sanitizedRelativeBase(value: String): String =
        value.takeIf { candidate ->
            RelativeTemporalBase.entries.any { it.name == candidate }
        } ?: "UNKNOWN"

    private fun sanitizedCorrectionRelation(value: String): String =
        value.takeIf { candidate ->
            RelativeTemporalCorrectionRelation.entries.any { it.name == candidate }
        } ?: if (value == "NOT_APPLICABLE") "NOT_APPLICABLE" else "UNKNOWN"

    private fun relativeTemporalFailureReason(exception: Exception): String =
        (exception as? RelativeTemporalProposalValidationException)?.failure?.name
            ?: exception::class.java.simpleName

    private companion object {
        val INITIAL_CONTEXT_RECOVERABLE_FAILURES = setOf(
            RelativeTemporalValidationFailure.CLARIFICATION_REQUIRED,
            RelativeTemporalValidationFailure.MALFORMED_DATE_COMBINATION,
            RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION
        )
        val REPAIRABLE_CORRECTION_FAILURES = setOf(
            RelativeTemporalValidationFailure.MALFORMED_DATE_COMBINATION,
            RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION,
            RelativeTemporalValidationFailure.ZERO_DATE_OFFSET,
            RelativeTemporalValidationFailure.ZERO_TIME_OFFSET
        )
    }
}
