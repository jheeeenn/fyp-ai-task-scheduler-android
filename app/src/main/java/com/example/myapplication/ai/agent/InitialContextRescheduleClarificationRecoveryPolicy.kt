package com.example.myapplication.ai.agent

import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.temporal.RelativeTemporalBase
import com.example.myapplication.ai.temporal.RelativeTemporalOperation
import com.example.myapplication.ai.temporal.RelativeTemporalProposal
import com.example.myapplication.ai.temporal.RelativeTemporalProposalValidationException
import com.example.myapplication.ai.temporal.RelativeTemporalProposalValidator
import com.example.myapplication.ai.temporal.RelativeTemporalValidationFailure
import com.example.myapplication.ai.temporal.TemporalExpressionResolver
import java.util.Calendar

enum class InitialContextRescheduleRecoveryReason {
    WRONG_VALIDATION_FAILURE,
    WRONG_ACTION,
    NON_EXACT_ORIGINAL,
    MODEL_ORIGINAL_MISMATCH,
    OFFSET_NOT_RECOVERABLE,
    EXTRA_TEMPORAL_FIELD,
    LOW_CONFIDENCE,
    MALFORMED_PROPOSAL
}

sealed class InitialContextRescheduleRecoveryResult {
    data class Accepted(val response: ContextActionExtractionResponse) :
        InitialContextRescheduleRecoveryResult()

    data class Rejected(val reason: InitialContextRescheduleRecoveryReason) :
        InitialContextRescheduleRecoveryResult()
}

/**
 * Recovers only an unnecessary model abstention for an exact initial contextual reschedule.
 * Android independently resolves both the original utterance and every model SET literal before
 * returning a response that must pass the normal extraction validator again.
 */
class InitialContextRescheduleClarificationRecoveryPolicy(
    private val resolver: TemporalExpressionResolver = TemporalExpressionResolver(),
    private val proposalValidator: RelativeTemporalProposalValidator =
        RelativeTemporalProposalValidator(),
    private val nowProvider: () -> Calendar = { Calendar.getInstance() }
) {
    fun recover(
        originalNormalizedRequest: String,
        response: ContextActionExtractionResponse,
        expectedAction: ConversationContextAction,
        validationFailure: RelativeTemporalValidationFailure
    ): InitialContextRescheduleRecoveryResult {
        if (validationFailure != RelativeTemporalValidationFailure.CLARIFICATION_REQUIRED) {
            return rejected(InitialContextRescheduleRecoveryReason.WRONG_VALIDATION_FAILURE)
        }
        if (expectedAction != ConversationContextAction.RESCHEDULE ||
            response.action != AiIntent.RESCHEDULE_TASK.name ||
            response.relativeBase != RelativeTemporalBase.AUTHORITATIVE_TASK.name
        ) {
            return rejected(InitialContextRescheduleRecoveryReason.WRONG_ACTION)
        }
        if (!response.needClarification || response.replacementTitle.isNotBlank()) {
            return rejected(InitialContextRescheduleRecoveryReason.MALFORMED_PROPOSAL)
        }
        if (!response.confidence.isFinite() ||
            response.confidence < RelativeTemporalProposal.MIN_CONFIDENCE ||
            response.confidence > 1.0
        ) {
            return rejected(InitialContextRescheduleRecoveryReason.LOW_CONFIDENCE)
        }

        val dateOperation = response.dateOperation.toOperationOrNull()
            ?: return rejected(InitialContextRescheduleRecoveryReason.MALFORMED_PROPOSAL)
        val timeOperation = response.timeOperation.toOperationOrNull()
            ?: return rejected(InitialContextRescheduleRecoveryReason.MALFORMED_PROPOSAL)
        if (dateOperation == RelativeTemporalOperation.OFFSET ||
            timeOperation == RelativeTemporalOperation.OFFSET
        ) {
            return rejected(InitialContextRescheduleRecoveryReason.OFFSET_NOT_RECOVERABLE)
        }

        val candidate = RelativeTemporalProposal(
            dateOperation = dateOperation,
            timeOperation = timeOperation,
            relativeBase = RelativeTemporalBase.AUTHORITATIVE_TASK,
            replacementDateText = response.replacementDateText,
            replacementTimeText = response.replacementTimeText,
            dateOffsetDays = response.dateOffsetDays,
            timeOffsetMinutes = response.timeOffsetMinutes,
            confidence = response.confidence,
            needClarification = false
        )
        try {
            proposalValidator.validate(candidate)
        } catch (_: RelativeTemporalProposalValidationException) {
            return rejected(InitialContextRescheduleRecoveryReason.MALFORMED_PROPOSAL)
        }

        val changesDate = dateOperation == RelativeTemporalOperation.SET
        val changesTime = timeOperation == RelativeTemporalOperation.SET
        val now = nowProvider()
        val originalResolution = resolver.resolve(
            agentDateText = null,
            agentTimeText = null,
            originalText = originalNormalizedRequest,
            baseCalendar = clone(now)
        )
        if (originalResolution.hasDateConstraint != changesDate ||
            originalResolution.hasTimeConstraint != changesTime
        ) {
            return rejected(InitialContextRescheduleRecoveryReason.EXTRA_TEMPORAL_FIELD)
        }
        if ((changesDate && !originalResolution.isExactDate) ||
            (changesTime && !originalResolution.isExactTime)
        ) {
            return rejected(InitialContextRescheduleRecoveryReason.NON_EXACT_ORIGINAL)
        }

        if (changesDate) {
            val modelDate = resolver.resolve(
                agentDateText = response.replacementDateText,
                agentTimeText = null,
                originalText = response.replacementDateText,
                baseCalendar = clone(now)
            )
            if (!modelDate.isExactDate) {
                return rejected(InitialContextRescheduleRecoveryReason.MALFORMED_PROPOSAL)
            }
            if (modelDate.startDateInclusive != originalResolution.startDateInclusive) {
                return rejected(InitialContextRescheduleRecoveryReason.MODEL_ORIGINAL_MISMATCH)
            }
        }
        if (changesTime) {
            val modelTime = resolver.resolve(
                agentDateText = null,
                agentTimeText = response.replacementTimeText,
                originalText = response.replacementTimeText,
                baseCalendar = clone(now)
            )
            if (!modelTime.isExactTime) {
                return rejected(InitialContextRescheduleRecoveryReason.MALFORMED_PROPOSAL)
            }
            if (modelTime.startMinuteInclusive != originalResolution.startMinuteInclusive) {
                return rejected(InitialContextRescheduleRecoveryReason.MODEL_ORIGINAL_MISMATCH)
            }
        }

        return InitialContextRescheduleRecoveryResult.Accepted(
            response.copy(needClarification = false)
        )
    }

    private fun String.toOperationOrNull(): RelativeTemporalOperation? =
        runCatching { RelativeTemporalOperation.valueOf(this) }.getOrNull()

    private fun rejected(reason: InitialContextRescheduleRecoveryReason) =
        InitialContextRescheduleRecoveryResult.Rejected(reason)

    private fun clone(calendar: Calendar): Calendar = calendar.clone() as Calendar
}
