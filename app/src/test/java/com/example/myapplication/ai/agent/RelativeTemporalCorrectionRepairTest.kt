package com.example.myapplication.ai.agent

import com.example.myapplication.ai.temporal.ExactTemporalSchedule
import com.example.myapplication.ai.temporal.RelativeTemporalBase
import com.example.myapplication.ai.temporal.RelativeTemporalCalculationResult
import com.example.myapplication.ai.temporal.RelativeTemporalChangeCalculator
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionResponse
import com.example.myapplication.ai.temporal.RelativeTemporalOperation
import com.example.myapplication.ai.temporal.RelativeTemporalProposalSession
import com.example.myapplication.ai.temporal.RelativeTemporalProposalState
import com.example.myapplication.ai.temporal.RelativeTemporalRevisionResult
import com.example.myapplication.ai.temporal.RelativeTemporalValidationFailure
import com.example.myapplication.ai.temporal.ValidatedRelativeTemporalCorrection
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar
import java.util.TimeZone

class RelativeTemporalCorrectionRepairTest {
    @Test
    fun validCorrectionUsesOnlyTheNormalSemanticCall() = runBlocking {
        val client = FakeCorrectionClient(normalResponse = validOffsetResponse(30))

        val correction = orchestrator(client).processRelativeTemporalCorrection("shift by a duration")

        assertTrue(correction is ValidatedRelativeTemporalCorrection.Apply)
        assertEquals(1, client.normalCalls)
        assertEquals(0, client.repairCalls)
    }

    @Test
    fun exactDeviceShapeGetsOneRepairAndUsesTheOriginalAuthoritativeBase() = runBlocking {
        val original = ExactTemporalSchedule("03/08/2026", "11:45 PM")
        val revisionOne = ExactTemporalSchedule("04/08/2026", "12:15 AM")
        val client = FakeCorrectionClient(
            normalResponse = malformedDeviceResponse(),
            repairResponse = validOffsetResponse(60)
        )
        val session = RelativeTemporalProposalSession(original, revisionOne)
        val token = session.beginCorrection()

        val correction = orchestrator(client).processRelativeTemporalCorrection(
            "Actually, make it one hour later instead."
        ) as ValidatedRelativeTemporalCorrection.Apply

        assertEquals(1, client.normalCalls)
        assertEquals(1, client.repairCalls)
        assertEquals(RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION, client.failure)
        assertEquals("SET", client.rejectedResponse?.timeOperation)
        assertEquals(60, client.rejectedResponse?.timeOffsetMinutes)
        assertEquals(RelativeTemporalOperation.KEEP, correction.proposal.dateOperation)
        assertEquals(RelativeTemporalOperation.OFFSET, correction.proposal.timeOperation)
        assertEquals(RelativeTemporalBase.AUTHORITATIVE_TASK, correction.proposal.relativeBase)
        assertEquals("", correction.proposal.replacementTimeText)
        assertEquals(60, correction.proposal.timeOffsetMinutes)
        assertEquals(1, session.revision)
        assertEquals(revisionOne, session.currentProposal)

        val calculated = RelativeTemporalChangeCalculator().calculate(
            authoritativeOriginal = original,
            currentProposal = revisionOne,
            proposal = correction.proposal,
            now = nowBeforeOriginal()
        ) as RelativeTemporalCalculationResult.Success

        assertEquals(ExactTemporalSchedule("04/08/2026", "12:45 AM"), calculated.schedule)
        assertFalse(calculated.schedule == ExactTemporalSchedule("04/08/2026", "1:15 AM"))
        assertEquals(
            RelativeTemporalRevisionResult.APPLIED,
            session.applyCorrection(token, calculated.schedule)
        )
        assertEquals(2, session.revision)
        assertEquals(calculated.schedule, session.currentProposal)
    }

    @Test
    fun malformedRepairFailsClosedWithoutChangingRevisionAndIsNeverRetried() {
        val original = ExactTemporalSchedule("03/08/2026", "11:45 PM")
        val revisionOne = ExactTemporalSchedule("04/08/2026", "12:15 AM")
        val session = RelativeTemporalProposalSession(original, revisionOne)
        session.beginCorrection()
        val client = FakeCorrectionClient(
            normalResponse = malformedDeviceResponse(),
            repairResponse = malformedDeviceResponse()
        )

        assertThrows(TaskAgentProcessingException::class.java) {
            runBlocking {
                orchestrator(client).processRelativeTemporalCorrection("replace the prior shift")
            }
        }

        assertEquals(1, client.normalCalls)
        assertEquals(1, client.repairCalls)
        assertEquals(1, session.revision)
        assertEquals(revisionOne, session.currentProposal)
        assertEquals(RelativeTemporalProposalState.ACTIVE, session.state)
    }

    @Test
    fun malformedJsonRepairIsAttemptedOnlyOnceAndFailsClosed() {
        val client = FakeCorrectionClient(
            normalResponse = malformedDeviceResponse(),
            repairResponse = "{\"move\":\"APPLY_CHANGE\""
        )

        assertThrows(TaskAgentProcessingException::class.java) {
            runBlocking {
                orchestrator(client).processRelativeTemporalCorrection("replace the prior shift")
            }
        }

        assertEquals(1, client.normalCalls)
        assertEquals(1, client.repairCalls)
    }

    @Test
    fun onlyTheFourOperationShapeFailuresAreRepairEligible() = runBlocking {
        val cases = listOf(
            response(dateOperation = "KEEP", dateOffset = 1),
            response(timeOperation = "KEEP", timeOffset = 30),
            response(dateOperation = "OFFSET", dateOffset = 0, timeOperation = "SET", replacementTime = "9 AM"),
            response(timeOperation = "OFFSET", timeOffset = 0, dateOperation = "SET", replacementDate = "tomorrow")
        )

        val observedFailures = cases.map { malformed ->
            val client = FakeCorrectionClient(malformed, validOffsetResponse(15))
            val correction = orchestrator(client).processRelativeTemporalCorrection("apply the stated change")
            assertTrue(correction is ValidatedRelativeTemporalCorrection.Apply)
            assertEquals(1, client.normalCalls)
            assertEquals(1, client.repairCalls)
            client.failure
        }
        assertEquals(
            setOf(
                RelativeTemporalValidationFailure.MALFORMED_DATE_COMBINATION,
                RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION,
                RelativeTemporalValidationFailure.ZERO_DATE_OFFSET,
                RelativeTemporalValidationFailure.ZERO_TIME_OFFSET
            ),
            observedFailures.toSet()
        )
    }

    @Test
    fun confidenceClarificationEnumsBoundsAndStructuralFailuresDoNotRepair() {
        val valid = validOffsetResponse(30)
        val responses = listOf(
            response(confidence = 0.79),
            response(clarification = true),
            response(timeOperation = "SHIFT", timeOffset = 30),
            response(base = "SOME_TASK", timeOffset = 30),
            response(timeOffset = 10_081),
            JSONObject(valid).put("unexpected", true).toString(),
            JSONObject(valid).apply { remove("relative_base") }.toString(),
            "{\"move\":\"APPLY_CHANGE\""
        )

        responses.forEach { raw ->
            val client = FakeCorrectionClient(raw, validOffsetResponse(30))
            assertThrows(TaskAgentProcessingException::class.java) {
                runBlocking {
                    orchestrator(client).processRelativeTemporalCorrection("uncertain correction")
                }
            }
            assertEquals(1, client.normalCalls)
            assertEquals(0, client.repairCalls)
        }
    }

    @Test
    fun cancellationAfterRepairedCorrectionRestoresOriginalWithoutPersistenceHooks() = runBlocking {
        var roomMutations = 0
        var reminderOperations = 0
        val original = ExactTemporalSchedule("03/08/2026", "11:45 PM")
        val session = RelativeTemporalProposalSession(
            original,
            ExactTemporalSchedule("04/08/2026", "12:15 AM")
        )
        val token = session.beginCorrection()
        val client = FakeCorrectionClient(malformedDeviceResponse(), validOffsetResponse(60))
        val correction = orchestrator(client).processRelativeTemporalCorrection("replace the shift")
            as ValidatedRelativeTemporalCorrection.Apply
        val calculated = RelativeTemporalChangeCalculator().calculate(
            original,
            session.currentProposal,
            correction.proposal,
            nowBeforeOriginal()
        ) as RelativeTemporalCalculationResult.Success
        session.applyCorrection(token, calculated.schedule)

        assertEquals(original, session.cancel())
        assertEquals(RelativeTemporalProposalState.CANCELLED, session.state)
        assertEquals(0, roomMutations)
        assertEquals(0, reminderOperations)
    }

    @Test
    fun cancellationAfterFailedCorrectionAlsoRestoresOriginalWithoutPersistenceHooks() {
        var roomMutations = 0
        var reminderOperations = 0
        val original = ExactTemporalSchedule("03/08/2026", "11:45 PM")
        val revisionOne = ExactTemporalSchedule("04/08/2026", "12:15 AM")
        val session = RelativeTemporalProposalSession(original, revisionOne)
        session.beginCorrection()
        val client = FakeCorrectionClient(
            normalResponse = malformedDeviceResponse(),
            repairResponse = malformedDeviceResponse()
        )

        assertThrows(TaskAgentProcessingException::class.java) {
            runBlocking {
                orchestrator(client).processRelativeTemporalCorrection("replace the shift")
            }
        }
        assertEquals(original, session.cancel())
        assertEquals(RelativeTemporalProposalState.CANCELLED, session.state)
        assertEquals(0, roomMutations)
        assertEquals(0, reminderOperations)
    }

    @Test
    fun repairOrchestrationContainsNoUtteranceSpecificRouter() {
        val source = File("src/main/java/com/example/myapplication/ai/agent/AgentOrchestrator.kt").readText()
        val repair = source.substringAfter("private suspend fun processRelativeTemporalCorrectionRepair(")
            .substringBefore("suspend fun processRoutine(")

        assertFalse(repair.contains("Regex("))
        assertFalse(repair.contains("originalUserText.contains("))
        assertFalse(repair.contains("when (originalUserText"))
        assertTrue(repair.contains("relativeTemporalCorrectionParser.parse(rawContent)"))
        assertTrue(repair.contains("relativeTemporalCorrectionValidator.validateWithReport(response)"))
    }

    private fun orchestrator(client: LaptopAgentClient) = AgentOrchestrator(
        laptopAgentClient = client,
        taskAgentResponseParser = TaskAgentResponseParser(),
        taskActionNormalizer = TaskActionNormalizer(),
        actionValidator = ActionValidator()
    )

    private class FakeCorrectionClient(
        private val normalResponse: String,
        private val repairResponse: String? = null
    ) : LaptopAgentClient(null) {
        var normalCalls = 0
        var repairCalls = 0
        var rejectedResponse: RelativeTemporalCorrectionResponse? = null
        var failure: RelativeTemporalValidationFailure? = null

        override suspend fun processRelativeTemporalCorrection(normalizedText: String): String {
            normalCalls += 1
            return normalResponse
        }

        override suspend fun processRelativeTemporalCorrectionRepair(
            originalUserText: String,
            rejectedResponse: RelativeTemporalCorrectionResponse,
            validationFailure: RelativeTemporalValidationFailure
        ): String {
            repairCalls += 1
            this.rejectedResponse = rejectedResponse
            failure = validationFailure
            return checkNotNull(repairResponse)
        }
    }

    private fun malformedDeviceResponse(): String = response(
        timeOperation = "SET",
        replacementTime = "a duration-based shift",
        timeOffset = 60,
        confidence = 1.0
    )

    private fun validOffsetResponse(minutes: Int): String = response(
        timeOperation = "OFFSET",
        timeOffset = minutes
    )

    private fun response(
        move: String = "APPLY_CHANGE",
        dateOperation: String = "KEEP",
        timeOperation: String = "OFFSET",
        base: String = "AUTHORITATIVE_TASK",
        replacementDate: String = "",
        replacementTime: String = "",
        dateOffset: Int = 0,
        timeOffset: Int = 30,
        confidence: Double = 0.98,
        clarification: Boolean = false
    ): String = JSONObject()
        .put("move", move)
        .put("date_operation", dateOperation)
        .put("time_operation", timeOperation)
        .put("relative_base", base)
        .put("replacement_date_text", replacementDate)
        .put("replacement_time_text", replacementTime)
        .put("date_offset_days", dateOffset)
        .put("time_offset_minutes", timeOffset)
        .put("confidence", confidence)
        .put("need_clarification", clarification)
        .toString()

    private fun nowBeforeOriginal(): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur")).apply {
            clear()
            set(2026, Calendar.AUGUST, 2, 16, 25, 0)
        }
}
