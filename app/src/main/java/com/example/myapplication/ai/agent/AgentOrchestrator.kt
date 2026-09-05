package com.example.myapplication.ai.agent

import android.util.Log
import com.example.myapplication.ai.AiParsedCommand
import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.TaskCommandContradictionDetector
import com.example.myapplication.ai.TaskQueryDetail
import com.example.myapplication.ai.TaskQueryPresentation
import com.example.myapplication.ai.breakdown.BreakdownTargetPreference
import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.routine.RoutineExtractionResponse
import com.example.myapplication.ai.routine.RoutineExtractionResponseParser
import com.example.myapplication.ai.temporal.RelativeTemporalBase
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionContext
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionMove
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionParser
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionRelation
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionRelationMapper
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionResponse
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionValidator
import com.example.myapplication.ai.temporal.RelativeTemporalExpectedField
import com.example.myapplication.ai.temporal.RelativeTemporalFieldConstraintCanonicalizer
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
    private val relativeTemporalFieldConstraintCanonicalizer:
        RelativeTemporalFieldConstraintCanonicalizer =
            RelativeTemporalFieldConstraintCanonicalizer(),
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
            val namedRename = TaskCommandContradictionDetector.isNamedTaskRename(normalizedText)
            if (namedRename && !isCompletePureTitleRename(normalizedCommand)) {
                val reason = if (
                    normalizedCommand.intent == AiIntent.UPDATE_TASK.name &&
                    normalizedCommand.taskTitle.isNullOrBlank()
                ) "MISSING_REPLACEMENT_TITLE" else "CONTRADICTORY_PRIMARY_RESULT"
                Log.d(
                    "TASK_ACTION_GUARD",
                    "inputCategory=EXISTING_TASK_TITLE_CHANGE " +
                        "primaryAction=${normalizedCommand.intent} result=REPAIR_REQUIRED reason=$reason"
                )
                return repairTitleRename(normalizedText)
            }
            val scheduleChange = if (normalizedCommand.intent in setOf(
                    AiIntent.CREATE_TASK.name, AiIntent.RESCHEDULE_TASK.name
                )
            ) {
                TaskCommandContradictionDetector.scheduleChangeEvidence(normalizedText)
            } else null
            if (scheduleChange != null && (
                    normalizedCommand.intent == AiIntent.CREATE_TASK.name ||
                        hasRescheduleTemporalRoleContradiction(normalizedCommand, scheduleChange)
                )
            ) {
                val reason = if (normalizedCommand.intent == AiIntent.RESCHEDULE_TASK.name) {
                    "DESTINATION_TEMPORAL_ROLE_MISMATCH"
                } else "CONTRADICTORY_PRIMARY_ACTION"
                Log.d(
                    "TASK_ACTION_GUARD",
                    "inputCategory=EXISTING_TASK_SCHEDULE_CHANGE " +
                        "primaryAction=${normalizedCommand.intent} result=REPAIR_REQUIRED reason=$reason"
                )
                return repairReschedule(normalizedText, scheduleChange)
            }
            val namedQueryEvidence = TaskCommandContradictionDetector.namedScheduleReadEvidence(normalizedText)
            val consistentCommand = if (namedQueryEvidence != null) {
                if (!isCompleteNamedScheduleQuery(normalizedCommand, namedQueryEvidence) ||
                    agentResponse.requires_confirmation
                ) {
                    Log.d(
                        "TASK_NAMED_QUERY_GUARD",
                        "primaryAction=${normalizedCommand.intent} expectedDetail=${namedQueryEvidence.expectedDetail} " +
                            "primaryDetail=${normalizedCommand.queryDetail} result=REPAIR_REQUIRED"
                    )
                    return repairNamedScheduleQuery(normalizedText, namedQueryEvidence.expectedDetail)
                }
                // Presentation is Android-owned for this dedicated read path, not a task fact.
                normalizedCommand.copy(queryPresentation = TaskQueryPresentation.DETAILS)
            } else normalizedCommand
            val targetEvidence =
                TaskCommandContradictionDetector.namedExistingTaskTargetEvidence(normalizedText)
            if (targetEvidence != null && consistentCommand.intent != targetEvidence.expectedAction) {
                throw TaskAgentValidationException(
                    "Named existing-task operation contradicts primary action"
                )
            }
            val missingTitles = consistentCommand.taskTitle.isNullOrBlank() &&
                consistentCommand.targetTaskTitle.isNullOrBlank()
            val lacksTemporalTarget = consistentCommand.targetDateText.isNullOrBlank() &&
                consistentCommand.targetTimeText.isNullOrBlank()
            val structurallyEligible = consistentCommand.intent in TARGET_REPAIR_ACTIONS &&
                lacksTemporalTarget
            if (missingTitles && (targetEvidence != null || structurallyEligible)) {
                Log.d(
                    "TASK_TARGET_GUARD",
                    "expectedAction=${consistentCommand.intent} namedEvidence=${targetEvidence != null} " +
                        "temporalTarget=${!lacksTemporalTarget} result=REPAIR_REQUIRED"
                )
                return repairExistingTaskTarget(
                    normalizedText,
                    consistentCommand.intent,
                    consistentCommand
                )
            }
            val targetConsistentCommand = consistentCommand
            val validatedCommand = actionValidator.validate(targetConsistentCommand)
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

    private fun isCompleteNamedScheduleQuery(
        command: AiParsedCommand,
        evidence: TaskCommandContradictionDetector.NamedScheduleReadEvidence
    ): Boolean =
        command.intent == AiIntent.QUERY_TASK.name &&
            command.taskTitle.isNullOrBlank() && !command.targetTaskTitle.isNullOrBlank() &&
            command.queryDetail == evidence.expectedDetail && command.queryPresentation != TaskQueryPresentation.NONE &&
            (evidence.hasTargetDate ||
                (command.dateText.isNullOrBlank() && command.targetDateText.isNullOrBlank())) &&
            (evidence.hasTargetTime ||
                (command.timeText.isNullOrBlank() && command.targetTimeText.isNullOrBlank())) &&
            command.newDateText.isNullOrBlank() && command.newTimeText.isNullOrBlank() &&
            // Named reads use deterministic speech. Re-extract rather than trust any model prose.
            command.naturalResponse.isNullOrBlank() &&
            !command.needsClarification && command.missingFields.isEmpty() &&
            command.recurrence.isNullOrBlank() && command.priority.isNullOrBlank() &&
            command.plan.isEmpty() && command.breakdownTargetPreference == BreakdownTargetPreference.AUTO

    private suspend fun repairNamedScheduleQuery(
        normalizedText: String,
        expectedDetail: TaskQueryDetail
    ): AiParsedCommand = try {
        // The original utterance is the only request input; discard all rejected primary fields.
        val raw = laptopAgentClient.processNamedScheduleQueryRepair(normalizedText)
        val response = taskAgentResponseParser.parse(
            NamedScheduleQueryRepairParser.toTaskAgentJson(raw, expectedDetail)
        )
        val command = actionValidator.validate(taskActionNormalizer.normalize(response))
        Log.d("TASK_NAMED_QUERY_REPAIR", "attempt=1 detail=${command.queryDetail} result=ACCEPTED")
        command
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.d("TASK_NAMED_QUERY_REPAIR", "attempt=1 result=REJECTED")
        throw e // process() fails closed; no second repair or fallback to the primary result.
    }

    private suspend fun repairExistingTaskTarget(
        normalizedText: String,
        expectedAction: String,
        command: AiParsedCommand
    ): AiParsedCommand = try {
        val raw = laptopAgentClient.processExistingTaskTargetRepair(normalizedText, expectedAction)
        val repair = ExistingTaskTargetRepairParser.parse(raw)
        require(TaskCommandContradictionDetector.isGroundedNamedTarget(
            normalizedText,
            repair.targetTaskTitle
        )) { "Existing-task repair target is not literally grounded in the original request" }
        // The action and every non-target field remain from the already-normalized primary command.
        val repaired = actionValidator.validate(
            command.copy(targetTaskTitle = repair.targetTaskTitle)
        )
        Log.d("TASK_TARGET_REPAIR", "attempt=1 expectedAction=$expectedAction result=ACCEPTED")
        repaired
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.d("TASK_TARGET_REPAIR", "attempt=1 expectedAction=$expectedAction result=REJECTED")
        throw e
    }

    private fun hasRescheduleTemporalRoleContradiction(
        command: AiParsedCommand,
        evidence: TaskCommandContradictionDetector.ScheduleChangeEvidence
    ): Boolean =
        (evidence.hasDestinationDate && command.newDateText.isNullOrBlank()) ||
            (evidence.hasDestinationTime && command.newTimeText.isNullOrBlank()) ||
            (!evidence.hasTargetDate && !command.targetDateText.isNullOrBlank()) ||
            (!evidence.hasTargetTime && !command.targetTimeText.isNullOrBlank())

    private suspend fun repairReschedule(
        normalizedText: String,
        evidence: TaskCommandContradictionDetector.ScheduleChangeEvidence
    ): AiParsedCommand = try {
        // Extract afresh from the complete request; all rejected primary fields are discarded.
        val raw = laptopAgentClient.processRescheduleRepair(normalizedText)
        val response = taskAgentResponseParser.parse(RescheduleRepairParser.toTaskAgentJson(raw, evidence))
        val command = actionValidator.validate(taskActionNormalizer.normalize(response))
        Log.d("TASK_ACTION_REPAIR", "expectedAction=RESCHEDULE_TASK result=ACCEPTED")
        command
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.d("TASK_ACTION_REPAIR", "expectedAction=RESCHEDULE_TASK result=REJECTED")
        throw e // process() supplies the existing safe TaskAgentProcessingException; no CREATE fallback.
    }

    private fun isCompletePureTitleRename(command: AiParsedCommand): Boolean =
        command.intent == AiIntent.UPDATE_TASK.name &&
            !command.needsClarification && command.missingFields.isEmpty() &&
            !command.targetTaskTitle.isNullOrBlank() &&
            !command.taskTitle.isNullOrBlank() &&
            command.dateText.isNullOrBlank() && command.timeText.isNullOrBlank() &&
            command.targetDateText.isNullOrBlank() && command.targetTimeText.isNullOrBlank() &&
            command.newDateText.isNullOrBlank() && command.newTimeText.isNullOrBlank() &&
            command.recurrence.isNullOrBlank() && command.priority.isNullOrBlank()

    private suspend fun repairTitleRename(normalizedText: String): AiParsedCommand = try {
        val raw = laptopAgentClient.processTitleRenameRepair(normalizedText)
        val response = taskAgentResponseParser.parse(TitleRenameRepairParser.toTaskAgentJson(raw))
        val command = actionValidator.validate(taskActionNormalizer.normalize(response))
        Log.d(
            "TASK_ACTION_REPAIR",
            "expectedAction=UPDATE_TASK repairType=TITLE_RENAME result=ACCEPTED"
        )
        command
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.d(
            "TASK_ACTION_REPAIR",
            "expectedAction=UPDATE_TASK repairType=TITLE_RENAME result=REJECTED"
        )
        throw e
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
        context: RelativeTemporalCorrectionContext,
        expectedField: RelativeTemporalExpectedField? = null
    ): ValidatedRelativeTemporalCorrection {
        return try {
            logRelativeTemporalCorrectionContext(context)
            val rawContent = laptopAgentClient.processRelativeTemporalCorrection(
                normalizedText,
                context
            )
            val response = relativeTemporalCorrectionParser.parse(rawContent)
            logParsedRelativeTemporalShape(RelativeTemporalExtractionStage.CORRECTION, response)
            val fieldConflictFailure =
                relativeTemporalFieldConstraintCanonicalizer.conflictingFieldPayloadFailure(
                    response,
                    expectedField
                )
            val correction = try {
                if (fieldConflictFailure != null) {
                    throw RelativeTemporalProposalValidationException(
                        fieldConflictFailure,
                        "Relative-temporal payload conflicts with the expected edit field"
                    )
                }
                val validation = relativeTemporalCorrectionValidator.validateWithReport(response)
                logCanonicalization(
                    RelativeTemporalExtractionStage.CORRECTION,
                    validation.canonicalizationReport.changedFields
                )
                validation.correction
            } catch (exception: RelativeTemporalProposalValidationException) {
                if (fieldConflictFailure != null) {
                    logCorrectionRepair(
                        exception.failure,
                        RelativeTemporalRepairResult.NOT_ELIGIBLE
                    )
                    throw exception
                }
                val fieldCanonicalization =
                    relativeTemporalFieldConstraintCanonicalizer.canonicalize(
                        rejected = response,
                        failure = exception.failure,
                        expectedField = expectedField
                    )
                if (fieldCanonicalization != null) {
                    val validation = relativeTemporalCorrectionValidator.validateWithReport(
                        fieldCanonicalization.response
                    )
                    Log.d(
                        "RELATIVE_TEMPORAL_FIELD_CONSTRAINT",
                        "expectedField=$expectedField " +
                            "canonicalizedFields=${fieldCanonicalization.changedFields.joinToString()} " +
                            "result=REVALIDATED"
                    )
                    logCanonicalization(
                        RelativeTemporalExtractionStage.CORRECTION,
                        fieldCanonicalization.changedFields +
                            validation.canonicalizationReport.changedFields
                    )
                    validation.correction
                } else {
                    if (!isEligibleCorrectionRepair(exception.failure)) {
                        logCorrectionRepair(
                            exception.failure,
                            RelativeTemporalRepairResult.NOT_ELIGIBLE
                        )
                        throw exception
                    }
                    processRelativeTemporalCorrectionRepair(
                        normalizedText,
                        response,
                        exception.failure
                    )
                }
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
            move = "NOT_APPLICABLE",
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
            move = response.move,
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
        move: String,
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
                "move=${sanitizedCorrectionMove(move)} " +
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

    private fun sanitizedCorrectionMove(value: String): String =
        value.takeIf { candidate ->
            RelativeTemporalCorrectionMove.entries.any { it.name == candidate }
        } ?: if (value == "NOT_APPLICABLE") "NOT_APPLICABLE" else "UNKNOWN"

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
        val TARGET_REPAIR_ACTIONS = setOf(
            AiIntent.DELETE_TASK.name,
            AiIntent.RESCHEDULE_TASK.name,
            AiIntent.MARK_DONE.name,
            AiIntent.MARK_UNDONE.name
        )
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
