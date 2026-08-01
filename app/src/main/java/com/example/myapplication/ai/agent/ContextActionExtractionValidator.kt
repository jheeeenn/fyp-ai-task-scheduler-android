package com.example.myapplication.ai.agent

import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.temporal.RelativeTemporalBase
import com.example.myapplication.ai.temporal.RelativeTemporalCanonicalizationReport
import com.example.myapplication.ai.temporal.RelativeTemporalOperation
import com.example.myapplication.ai.temporal.RelativeTemporalProposal
import com.example.myapplication.ai.temporal.RelativeTemporalProposalCanonicalizer
import com.example.myapplication.ai.temporal.RelativeTemporalProposalValidator

data class ContextActionExtractionValidation(
    val changeSet: ContextActionChangeSet,
    val canonicalizationReport: RelativeTemporalCanonicalizationReport
)

class ContextActionExtractionValidator(
    private val temporalValidator: RelativeTemporalProposalValidator =
        RelativeTemporalProposalValidator(),
    private val temporalCanonicalizer: RelativeTemporalProposalCanonicalizer =
        RelativeTemporalProposalCanonicalizer()
) {
    fun validate(
        response: ContextActionExtractionResponse,
        expectedAction: ConversationContextAction
    ): ContextActionChangeSet = validateWithReport(response, expectedAction).changeSet

    fun validateWithReport(
        response: ContextActionExtractionResponse,
        expectedAction: ConversationContextAction
    ): ContextActionExtractionValidation {
        val actualAction = when (response.action.trim().uppercase()) {
            AiIntent.UPDATE_TASK.name -> ConversationContextAction.UPDATE
            AiIntent.RESCHEDULE_TASK.name -> ConversationContextAction.RESCHEDULE
            else -> fail("Unsupported context-action extraction action")
        }
        if (actualAction != expectedAction || expectedAction == ConversationContextAction.NONE) {
            fail("Context-action extraction action does not match expected action")
        }
        val replacementTitle = response.replacementTitle.clean().takeIf { it.isNotEmpty() }
        if (actualAction == ConversationContextAction.RESCHEDULE && replacementTitle != null) {
            fail("RESCHEDULE context action must not return a replacement title")
        }
        if (actualAction == ConversationContextAction.UPDATE) {
            validateUpdateOnlyResponse(response)
            return ContextActionExtractionValidation(
                changeSet = ContextActionChangeSet(
                    action = actualAction,
                    replacementTitle = replacementTitle,
                    confidence = response.confidence.toFloat()
                ),
                canonicalizationReport = RelativeTemporalCanonicalizationReport.NONE
            )
        }

        val canonicalized = temporalCanonicalizer.canonicalize(
            RelativeTemporalProposal(
                dateOperation = response.dateOperation.toOperation("date_operation"),
                timeOperation = response.timeOperation.toOperation("time_operation"),
                relativeBase = response.relativeBase.toRelativeBase(),
                replacementDateText = response.replacementDateText,
                replacementTimeText = response.replacementTimeText,
                dateOffsetDays = response.dateOffsetDays,
                timeOffsetMinutes = response.timeOffsetMinutes,
                confidence = response.confidence,
                needClarification = response.needClarification
            )
        )
        val proposal = temporalValidator.validate(canonicalized.proposal)
        if (proposal.relativeBase != RelativeTemporalBase.AUTHORITATIVE_TASK) {
            fail("Initial context reschedule must use the authoritative task as its base")
        }
        return ContextActionExtractionValidation(
            changeSet = ContextActionChangeSet(
                action = actualAction,
                replacementTitle = replacementTitle,
                newDateText = proposal.replacementDateText.takeIf {
                    proposal.dateOperation == RelativeTemporalOperation.SET
                },
                newTimeText = proposal.replacementTimeText.takeIf {
                    proposal.timeOperation == RelativeTemporalOperation.SET
                },
                confidence = response.confidence.toFloat(),
                temporalProposal = proposal
            ),
            canonicalizationReport = canonicalized.report
        )
    }

    private fun validateUpdateOnlyResponse(response: ContextActionExtractionResponse) {
        if (!response.confidence.isFinite() ||
            response.confidence < MIN_CONFIDENCE ||
            response.confidence > 1.0
        ) {
            fail("Context-action extraction confidence is below $MIN_CONFIDENCE")
        }
        if (response.needClarification) fail("Context-action extraction requested clarification")
        if (
            response.dateOperation != RelativeTemporalOperation.KEEP.name ||
            response.timeOperation != RelativeTemporalOperation.KEEP.name ||
            response.relativeBase != RelativeTemporalBase.AUTHORITATIVE_TASK.name ||
            response.replacementDateText.isNotBlank() ||
            response.replacementTimeText.isNotBlank() ||
            response.dateOffsetDays != 0 ||
            response.timeOffsetMinutes != 0
        ) {
            fail("UPDATE context action must not return temporal changes")
        }
    }

    private fun String.toOperation(field: String): RelativeTemporalOperation =
        try {
            RelativeTemporalOperation.valueOf(this)
        } catch (_: IllegalArgumentException) {
            fail("Unknown $field value")
        }

    private fun String.toRelativeBase(): RelativeTemporalBase =
        try {
            RelativeTemporalBase.valueOf(this)
        } catch (_: IllegalArgumentException) {
            fail("Unknown relative_base value")
        }

    private fun String.clean(): String = trim().replace(Regex("\\s+"), " ")

    private fun fail(message: String): Nothing = throw TaskAgentValidationException(message)

    companion object {
        const val MIN_CONFIDENCE = RelativeTemporalProposal.MIN_CONFIDENCE
    }
}
