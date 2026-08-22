package com.example.myapplication.ai.agent

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.temporal.RelativeTemporalOperation
import com.example.myapplication.ai.temporal.RelativeTemporalValidationFailure
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar

class InitialContextRescheduleClarificationRecoveryPolicyTest {
    private val policy = InitialContextRescheduleClarificationRecoveryPolicy(
        nowProvider = { fixedNow() }
    )

    @Test
    fun exactWeekdayRecoversAndPassesExistingValidatorAgain() {
        val result = recover(
            original = "move it to Friday",
            response = response(dateOperation = "SET", replacementDate = "Friday")
        )

        assertTrue(result.toString(), result is InitialContextRescheduleRecoveryResult.Accepted)
        val recovered = (result as InitialContextRescheduleRecoveryResult.Accepted).response
        assertFalse(recovered.needClarification)
        val validation = ContextActionExtractionValidator().validateWithReport(
            recovered,
            ConversationContextAction.RESCHEDULE
        )
        assertEquals(
            RelativeTemporalOperation.SET,
            validation.changeSet.temporalProposal?.dateOperation
        )
        assertEquals(
            RelativeTemporalOperation.KEEP,
            validation.changeSet.temporalProposal?.timeOperation
        )
    }

    @Test
    fun exactDateAndTimeRecoverOnlyWhenBothAndroidResolutionsAgree() {
        val result = recover(
            original = "move it to Friday at 3 PM",
            response = response(
                dateOperation = "SET",
                timeOperation = "SET",
                replacementDate = "Friday",
                replacementTime = "3 PM"
            )
        )

        assertTrue(result is InitialContextRescheduleRecoveryResult.Accepted)
    }

    @Test
    fun exactTomorrowSetOffsetHybridIsCanonicalizedAndRevalidated() {
        val result = policy.recover(
            originalNormalizedRequest = "can you move it to tomorrow 6:00 a.m.",
            response = response(
                dateOperation = "SET",
                timeOperation = "SET",
                replacementDate = "tomorrow",
                replacementTime = "6 AM",
                dateOffsetDays = 1,
                needClarification = false
            ),
            expectedAction = ConversationContextAction.RESCHEDULE,
            validationFailure = RelativeTemporalValidationFailure.MALFORMED_DATE_COMBINATION
        )

        assertTrue(result is InitialContextRescheduleRecoveryResult.Accepted)
        val recovered = (result as InitialContextRescheduleRecoveryResult.Accepted).response
        assertEquals("SET", recovered.dateOperation)
        assertEquals("tomorrow", recovered.replacementDateText)
        assertEquals(0, recovered.dateOffsetDays)
        val validated = ContextActionExtractionValidator().validateWithReport(
            recovered,
            ConversationContextAction.RESCHEDULE
        )
        assertEquals(RelativeTemporalOperation.SET, validated.changeSet.temporalProposal?.dateOperation)
        assertEquals(RelativeTemporalOperation.SET, validated.changeSet.temporalProposal?.timeOperation)
    }

    @Test
    fun setOffsetHybridFailsClosedWhenModelLiteralDisagreesWithOriginal() {
        val result = policy.recover(
            originalNormalizedRequest = "move it to tomorrow at 6 AM",
            response = response(
                dateOperation = "SET",
                timeOperation = "SET",
                replacementDate = "next Tuesday",
                replacementTime = "6 AM",
                dateOffsetDays = 1,
                needClarification = false
            ),
            expectedAction = ConversationContextAction.RESCHEDULE,
            validationFailure = RelativeTemporalValidationFailure.MALFORMED_DATE_COMBINATION
        )

        assertRejected(InitialContextRescheduleRecoveryReason.MODEL_ORIGINAL_MISMATCH, result)
    }

    @Test
    fun exactTimeSetOffsetHybridUsesTheSameMinuteAgreementRule() {
        val result = policy.recover(
            originalNormalizedRequest = "move it to 6 AM",
            response = response(
                timeOperation = "SET",
                replacementTime = "6 AM",
                timeOffsetMinutes = 60,
                needClarification = false
            ),
            expectedAction = ConversationContextAction.RESCHEDULE,
            validationFailure = RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION
        )

        assertTrue(result.toString(), result is InitialContextRescheduleRecoveryResult.Accepted)
        val recovered = (result as InitialContextRescheduleRecoveryResult.Accepted).response
        assertEquals("6 AM", recovered.replacementTimeText)
        assertEquals(0, recovered.timeOffsetMinutes)
        ContextActionExtractionValidator().validateWithReport(
            recovered,
            ConversationContextAction.RESCHEDULE
        )
    }

    @Test
    fun validTrueOffsetStillUsesTheNormalStrictValidatorWithoutRecovery() {
        val validation = ContextActionExtractionValidator().validateWithReport(
            response(
                dateOperation = "OFFSET",
                dateOffsetDays = 1,
                needClarification = false
            ),
            ConversationContextAction.RESCHEDULE
        )

        assertEquals(
            RelativeTemporalOperation.OFFSET,
            validation.changeSet.temporalProposal?.dateOperation
        )
        assertEquals(1, validation.changeSet.temporalProposal?.dateOffsetDays)
    }

    @Test
    fun omittedUserTimeAndInventedDateAreRejected() {
        assertRejected(
            expected = InitialContextRescheduleRecoveryReason.EXTRA_TEMPORAL_FIELD,
            result = recover(
                original = "move it to Friday at 3 PM",
                response = response(dateOperation = "SET", replacementDate = "Friday")
            )
        )
        assertRejected(
            expected = InitialContextRescheduleRecoveryReason.MODEL_ORIGINAL_MISMATCH,
            result = recover(
                original = "move it to Friday",
                response = response(dateOperation = "SET", replacementDate = "Monday")
            )
        )
        assertRejected(
            expected = InitialContextRescheduleRecoveryReason.EXTRA_TEMPORAL_FIELD,
            result = recover(
                original = "move it to Friday",
                response = response(
                    dateOperation = "SET",
                    timeOperation = "SET",
                    replacementDate = "Friday",
                    replacementTime = "3 PM"
                )
            )
        )
    }

    @Test
    fun broadTimeCannotBeUpgradedToExact() {
        assertRejected(
            expected = InitialContextRescheduleRecoveryReason.NON_EXACT_ORIGINAL,
            result = recover(
                original = "move it to morning",
                response = response(timeOperation = "SET", replacementTime = "morning")
            )
        )
    }

    @Test
    fun ambiguousAndExplicitOffsetsNeverUseSetRecovery() {
        listOf(
            "move it forward" to response(
                dateOperation = "OFFSET",
                dateOffsetDays = 1
            ),
            "move it thirty minutes later" to response(
                timeOperation = "OFFSET",
                timeOffsetMinutes = 30
            )
        ).forEach { (original, candidate) ->
            assertRejected(
                expected = InitialContextRescheduleRecoveryReason.OFFSET_NOT_RECOVERABLE,
                result = recover(original, candidate)
            )
        }
    }

    @Test
    fun lowConfidenceAndMalformedSetFailClosed() {
        assertRejected(
            expected = InitialContextRescheduleRecoveryReason.LOW_CONFIDENCE,
            result = recover(
                original = "move it to Friday",
                response = response(
                    dateOperation = "SET",
                    replacementDate = "Friday",
                    confidence = 0.79
                )
            )
        )
        assertRejected(
            expected = InitialContextRescheduleRecoveryReason.MALFORMED_PROPOSAL,
            result = recover(
                original = "move it to Friday",
                response = response(dateOperation = "SET", replacementDate = "")
            )
        )
    }

    @Test
    fun updateAndNonClarificationFailureAreNeverEligible() {
        assertRejected(
            expected = InitialContextRescheduleRecoveryReason.WRONG_ACTION,
            result = policy.recover(
                originalNormalizedRequest = "rename it",
                response = response(action = "UPDATE_TASK"),
                expectedAction = ConversationContextAction.UPDATE,
                validationFailure = RelativeTemporalValidationFailure.CLARIFICATION_REQUIRED
            )
        )
        assertRejected(
            expected = InitialContextRescheduleRecoveryReason.WRONG_VALIDATION_FAILURE,
            result = policy.recover(
                originalNormalizedRequest = "move it to Friday",
                response = response(dateOperation = "SET", replacementDate = "Friday"),
                expectedAction = ConversationContextAction.RESCHEDULE,
                validationFailure = RelativeTemporalValidationFailure.LOW_CONFIDENCE
            )
        )
    }

    @Test
    fun orchestratorRecoversInitialExactRescheduleButLeavesNormalPathIntact() = runBlocking {
        val recovered = orchestrator(
            responseJson(response(dateOperation = "SET", replacementDate = "Friday"))
        ).processContextAction(
            "move it to Friday",
            ConversationContextAction.RESCHEDULE
        )
        val normal = orchestrator(
            responseJson(
                response(
                    dateOperation = "SET",
                    replacementDate = "Friday",
                    needClarification = false
                )
            )
        ).processContextAction(
            "move it to Friday",
            ConversationContextAction.RESCHEDULE
        )

        assertFalse(requireNotNull(recovered.temporalProposal).needClarification)
        assertFalse(requireNotNull(normal.temporalProposal).needClarification)
        assertEquals(recovered.temporalProposal, normal.temporalProposal)
    }

    @Test
    fun orchestratorRecoversRedundantTomorrowOffsetBeforeReturningChangeSet() = runBlocking {
        val recovered = orchestrator(
            responseJson(
                response(
                    dateOperation = "SET",
                    timeOperation = "SET",
                    replacementDate = "tomorrow",
                    replacementTime = "6 AM",
                    dateOffsetDays = 1,
                    needClarification = false
                )
            )
        ).processContextAction(
            "move it to tomorrow at 6 AM",
            ConversationContextAction.RESCHEDULE
        )

        val proposal = requireNotNull(recovered.temporalProposal)
        assertEquals(RelativeTemporalOperation.SET, proposal.dateOperation)
        assertEquals("tomorrow", proposal.replacementDateText)
        assertEquals(0, proposal.dateOffsetDays)
        assertEquals(RelativeTemporalOperation.SET, proposal.timeOperation)
        assertEquals(0, proposal.timeOffsetMinutes)
    }

    @Test
    fun diagnosticReportsEnumFailureAndRecoveryIsInitialExtractionOnly() {
        val source = File(
            "src/main/java/com/example/myapplication/ai/agent/AgentOrchestrator.kt"
        ).readText()
        val initial = source
            .substringAfter("suspend fun processContextAction(")
            .substringBefore("suspend fun processRelativeTemporalCorrection(")
        val correction = source.substringAfter("suspend fun processRelativeTemporalCorrection(")

        assertTrue(initial.contains("INITIAL_CONTEXT_RESCHEDULE_RECOVERY"))
        assertTrue(initial.contains("exception.failure !in INITIAL_CONTEXT_RECOVERABLE_FAILURES"))
        assertTrue(initial.contains("val recoveredValidation ="))
        assertTrue(initial.contains("recovery.response"))
        assertTrue(initial.contains("relativeTemporalFailureReason(e)"))
        assertTrue(source.contains("RelativeTemporalProposalValidationException)?.failure?.name"))
        assertFalse(correction.contains("INITIAL_CONTEXT_RESCHEDULE_RECOVERY"))
    }

    private fun recover(
        original: String,
        response: ContextActionExtractionResponse
    ): InitialContextRescheduleRecoveryResult = policy.recover(
        originalNormalizedRequest = original,
        response = response,
        expectedAction = ConversationContextAction.RESCHEDULE,
        validationFailure = RelativeTemporalValidationFailure.CLARIFICATION_REQUIRED
    )

    private fun assertRejected(
        expected: InitialContextRescheduleRecoveryReason,
        result: InitialContextRescheduleRecoveryResult
    ) {
        assertTrue(result is InitialContextRescheduleRecoveryResult.Rejected)
        assertEquals(
            expected,
            (result as InitialContextRescheduleRecoveryResult.Rejected).reason
        )
    }

    private fun response(
        action: String = "RESCHEDULE_TASK",
        dateOperation: String = "KEEP",
        timeOperation: String = "KEEP",
        replacementDate: String = "",
        replacementTime: String = "",
        dateOffsetDays: Int = 0,
        timeOffsetMinutes: Int = 0,
        confidence: Double = 0.80,
        needClarification: Boolean = true
    ) = ContextActionExtractionResponse(
        action = action,
        replacementTitle = "",
        dateOperation = dateOperation,
        timeOperation = timeOperation,
        relativeBase = "AUTHORITATIVE_TASK",
        replacementDateText = replacementDate,
        replacementTimeText = replacementTime,
        dateOffsetDays = dateOffsetDays,
        timeOffsetMinutes = timeOffsetMinutes,
        confidence = confidence,
        needClarification = needClarification
    )

    private fun responseJson(response: ContextActionExtractionResponse): String = JSONObject()
        .put("action", response.action)
        .put("replacement_title", response.replacementTitle)
        .put("date_operation", response.dateOperation)
        .put("time_operation", response.timeOperation)
        .put("relative_base", response.relativeBase)
        .put("replacement_date_text", response.replacementDateText)
        .put("replacement_time_text", response.replacementTimeText)
        .put("date_offset_days", response.dateOffsetDays)
        .put("time_offset_minutes", response.timeOffsetMinutes)
        .put("confidence", response.confidence)
        .put("need_clarification", response.needClarification)
        .toString()

    private fun orchestrator(raw: String) = AgentOrchestrator(
        laptopAgentClient = object : LaptopAgentClient(null) {
            override suspend fun processContextAction(
                normalizedText: String,
                expectedAction: ConversationContextAction
            ): String = raw
        },
        taskAgentResponseParser = TaskAgentResponseParser(),
        taskActionNormalizer = TaskActionNormalizer(),
        actionValidator = ActionValidator(),
        initialContextRescheduleRecoveryPolicy =
            InitialContextRescheduleClarificationRecoveryPolicy(
                nowProvider = { fixedNow() }
            )
    )

    private fun fixedNow(): Calendar = Calendar.getInstance().apply {
        set(2026, Calendar.JULY, 16, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
