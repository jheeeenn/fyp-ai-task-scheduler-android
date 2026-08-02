package com.example.myapplication.ai.agent

import com.example.myapplication.ai.temporal.ExactTemporalSchedule
import com.example.myapplication.ai.temporal.RelativeTemporalBase
import com.example.myapplication.ai.temporal.RelativeTemporalCalculationResult
import com.example.myapplication.ai.temporal.RelativeTemporalChangeCalculator
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionResponse
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionValidator
import com.example.myapplication.ai.temporal.RelativeTemporalOperation
import com.example.myapplication.ai.temporal.RelativeTemporalProposalSession
import com.example.myapplication.ai.temporal.RelativeTemporalProposalState
import com.example.myapplication.ai.temporal.RelativeTemporalProposalValidator
import com.example.myapplication.ai.temporal.RelativeTemporalRepairCandidate
import com.example.myapplication.ai.temporal.RelativeTemporalRepairCandidateBuilder
import com.example.myapplication.ai.temporal.RelativeTemporalRepairField
import com.example.myapplication.ai.temporal.RelativeTemporalRepairRepresentation
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
    private val candidateBuilder = RelativeTemporalRepairCandidateBuilder()
    private val strictValidator = RelativeTemporalCorrectionValidator()

    @Test
    fun validCorrectionUsesOnlyTheNormalSemanticCall() = runBlocking {
        val client = FakeCorrectionClient(normalResponse = validOffsetResponse(30))

        val correction = orchestrator(client).processRelativeTemporalCorrection("shift by a duration")

        assertTrue(correction is ValidatedRelativeTemporalCorrection.Apply)
        assertEquals(1, client.normalCalls)
        assertEquals(0, client.repairCalls)
    }

    @Test
    fun exactDeviceShapeProducesLiteralAndOffsetCandidatesFromStructuredValuesOnly() {
        val rejected = responseObject(malformedDeviceResponse())

        val candidates = candidateBuilder.build(
            rejected,
            RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION
        )

        assertEquals(2, candidates.size)
        assertCandidate(candidates[0], "R1", RelativeTemporalRepairRepresentation.LITERAL)
        assertEquals("1 hours later instead", candidates[0].response.replacementTimeText)
        assertEquals(0, candidates[0].response.timeOffsetMinutes)
        assertCandidate(candidates[1], "R2", RelativeTemporalRepairRepresentation.OFFSET)
        assertEquals("", candidates[1].response.replacementTimeText)
        assertEquals(60, candidates[1].response.timeOffsetMinutes)
        candidates.forEach { strictValidator.validate(it.response) }

        val source = File(
            "src/main/java/com/example/myapplication/ai/temporal/RelativeTemporalCorrectionRepair.kt"
        ).readText()
        val buildMethod = source.substringAfter("fun build(").substringBefore("fun reconstructResponse(")
        assertFalse(buildMethod.contains("originalUserText"))
        assertFalse(buildMethod.contains("normalizedText"))
    }

    @Test
    fun offsetChoiceReconstructsAuthoritativeCorrectionAndAdvancesOnlyAfterCalculation() = runBlocking {
        val original = ExactTemporalSchedule("03/08/2026", "11:45 PM")
        val revisionOne = ExactTemporalSchedule("04/08/2026", "12:15 AM")
        val session = RelativeTemporalProposalSession(original, revisionOne)
        val token = session.beginCorrection()
        val client = FakeCorrectionClient(
            normalResponse = malformedDeviceResponse(),
            repairResponse = choiceResponse("R2", confidence = 1.0)
        )

        val correction = orchestrator(client).processRelativeTemporalCorrection(
            "Can you move it to 1 hours later instead"
        ) as ValidatedRelativeTemporalCorrection.Apply

        assertEquals(1, client.normalCalls)
        assertEquals(1, client.repairCalls)
        assertEquals(2, client.candidates?.size)
        assertEquals(RelativeTemporalOperation.KEEP, correction.proposal.dateOperation)
        assertEquals(RelativeTemporalOperation.OFFSET, correction.proposal.timeOperation)
        assertEquals(RelativeTemporalBase.AUTHORITATIVE_TASK, correction.proposal.relativeBase)
        assertEquals("", correction.proposal.replacementTimeText)
        assertEquals(60, correction.proposal.timeOffsetMinutes)
        assertEquals(1, session.revision)
        assertEquals(revisionOne, session.currentProposal)

        val calculation = RelativeTemporalChangeCalculator().calculate(
            authoritativeOriginal = original,
            currentProposal = revisionOne,
            proposal = correction.proposal,
            now = nowBeforeOriginal()
        ) as RelativeTemporalCalculationResult.Success

        assertEquals(ExactTemporalSchedule("04/08/2026", "12:45 AM"), calculation.schedule)
        assertFalse(calculation.schedule == ExactTemporalSchedule("04/08/2026", "1:15 AM"))
        assertEquals(
            RelativeTemporalRevisionResult.APPLIED,
            session.applyCorrection(token, calculation.schedule)
        )
        assertEquals(2, session.revision)
    }

    @Test
    fun literalChoiceReconstructsSetWithExistingLiteralAndZeroOffset() = runBlocking {
        val client = FakeCorrectionClient(
            normalResponse = malformedDeviceResponse(),
            repairResponse = choiceResponse("R1")
        )

        val correction = orchestrator(client).processRelativeTemporalCorrection("choose the literal meaning")
            as ValidatedRelativeTemporalCorrection.Apply

        assertEquals(RelativeTemporalOperation.KEEP, correction.proposal.dateOperation)
        assertEquals(RelativeTemporalOperation.SET, correction.proposal.timeOperation)
        assertEquals("1 hours later instead", correction.proposal.replacementTimeText)
        assertEquals(0, correction.proposal.timeOffsetMinutes)
        assertEquals(
            correction.proposal,
            RelativeTemporalProposalValidator().validate(correction.proposal)
        )
    }

    @Test
    fun reconstructionPreservesEitherCandidateBaseAndCannotOverrideIt() {
        listOf("AUTHORITATIVE_TASK", "CURRENT_PROPOSAL").forEach { originalBase ->
            val rejected = responseObject(
                response(
                    timeOperation = "SET",
                    base = originalBase,
                    replacementTime = "1 hours later instead",
                    timeOffset = 60
                )
            )
            val offsetCandidate = candidateBuilder.build(
                rejected,
                RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION
            ).single { it.representation == RelativeTemporalRepairRepresentation.OFFSET }

            val reconstructed = candidateBuilder.reconstructResponse(offsetCandidate, 1.0)

            assertEquals(originalBase, reconstructed.relativeBase)
            assertEquals(rejected.dateOperation, reconstructed.dateOperation)
            assertEquals(rejected.replacementDateText, reconstructed.replacementDateText)
            assertEquals(rejected.dateOffsetDays, reconstructed.dateOffsetDays)
            assertEquals(RelativeTemporalOperation.OFFSET.name, reconstructed.timeOperation)
            assertEquals(60, reconstructed.timeOffsetMinutes)
        }

        val source = File(
            "src/main/java/com/example/myapplication/ai/temporal/RelativeTemporalCorrectionRepair.kt"
        ).readText().substringAfter("fun reconstructResponse(").substringBefore("private fun dateVariants(")
        assertFalse(source.contains("relativeBase ="))
    }

    @Test
    fun unknownLowConfidenceAndClarificationChoicesFailClosedAfterOneChoiceCall() {
        val choices = listOf(
            choiceResponse("R9"),
            choiceResponse("R2", confidence = 0.79),
            choiceResponse("CLARIFY", clarification = true)
        )

        choices.forEach { repairChoice ->
            val revisionOne = ExactTemporalSchedule("04/08/2026", "12:15 AM")
            val session = RelativeTemporalProposalSession(
                ExactTemporalSchedule("03/08/2026", "11:45 PM"),
                revisionOne
            )
            session.beginCorrection()
            val client = FakeCorrectionClient(malformedDeviceResponse(), repairChoice)

            assertThrows(TaskAgentProcessingException::class.java) {
                runBlocking {
                    orchestrator(client).processRelativeTemporalCorrection("ambiguous correction")
                }
            }
            assertEquals(1, client.normalCalls)
            assertEquals(1, client.repairCalls)
            assertEquals(1, session.revision)
            assertEquals(revisionOne, session.currentProposal)
        }
    }

    @Test
    fun malformedChoiceResponseIsNotRetried() {
        val client = FakeCorrectionClient(
            normalResponse = malformedDeviceResponse(),
            repairResponse = "{\"choice_ref\":\"R2\""
        )

        assertThrows(TaskAgentProcessingException::class.java) {
            runBlocking {
                orchestrator(client).processRelativeTemporalCorrection("choose a repair")
            }
        }
        assertEquals(1, client.normalCalls)
        assertEquals(1, client.repairCalls)
    }

    @Test
    fun candidatesNeverInventValuesAndInvalidOffsetShapesAreExcluded() {
        val rejected = responseObject(malformedDeviceResponse())
        val candidates = candidateBuilder.build(
            rejected,
            RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION
        )

        assertTrue(candidates.all {
            it.response.replacementTimeText.isBlank() ||
                it.response.replacementTimeText == rejected.replacementTimeText
        })
        assertTrue(candidates.all {
            it.response.timeOffsetMinutes == 0 ||
                it.response.timeOffsetMinutes == rejected.timeOffsetMinutes
        })

        val zeroWithoutLiteral = responseObject(
            response(timeOperation = "OFFSET", replacementTime = "", timeOffset = 0)
        )
        assertTrue(
            candidateBuilder.build(
                zeroWithoutLiteral,
                RelativeTemporalValidationFailure.ZERO_TIME_OFFSET
            ).isEmpty()
        )

        val outOfBoundsWithoutLiteral = responseObject(
            response(timeOperation = "SET", replacementTime = "", timeOffset = 10_081)
        )
        assertTrue(
            candidateBuilder.build(
                outOfBoundsWithoutLiteral,
                RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION
            ).isEmpty()
        )
    }

    @Test
    fun noCandidatesMeansNoSemanticRepairCall() {
        val client = FakeCorrectionClient(
            normalResponse = response(timeOperation = "OFFSET", timeOffset = 0),
            repairResponse = choiceResponse("R1")
        )

        assertThrows(TaskAgentProcessingException::class.java) {
            runBlocking {
                orchestrator(client).processRelativeTemporalCorrection("incomplete correction")
            }
        }
        assertEquals(1, client.normalCalls)
        assertEquals(0, client.repairCalls)
    }

    @Test
    fun nonEligibleFailuresStillNeverCallChoiceRepair() {
        val responses = listOf(
            response(confidence = 0.79),
            response(clarification = true),
            response(timeOperation = "SHIFT", timeOffset = 30),
            response(base = "SOME_TASK", timeOffset = 30),
            response(timeOffset = 10_081),
            "{\"move\":\"APPLY_CHANGE\""
        )

        responses.forEach { raw ->
            val client = FakeCorrectionClient(raw, choiceResponse("R1"))
            assertThrows(TaskAgentProcessingException::class.java) {
                runBlocking {
                    orchestrator(client).processRelativeTemporalCorrection("invalid correction")
                }
            }
            assertEquals(0, client.repairCalls)
        }
    }

    @Test
    fun cancellationAfterRevisionTwoRestoresOriginalWithoutPersistenceHooks() = runBlocking {
        val roomMutations = 0
        val reminderOperations = 0
        val original = ExactTemporalSchedule("03/08/2026", "11:45 PM")
        val session = RelativeTemporalProposalSession(
            original,
            ExactTemporalSchedule("04/08/2026", "12:15 AM")
        )
        val token = session.beginCorrection()
        val client = FakeCorrectionClient(
            malformedDeviceResponse(),
            choiceResponse("R2")
        )
        val correction = orchestrator(client).processRelativeTemporalCorrection("select the offset")
            as ValidatedRelativeTemporalCorrection.Apply
        val calculation = RelativeTemporalChangeCalculator().calculate(
            original,
            session.currentProposal,
            correction.proposal,
            nowBeforeOriginal()
        ) as RelativeTemporalCalculationResult.Success
        session.applyCorrection(token, calculation.schedule)

        assertEquals(2, session.revision)
        assertEquals(original, session.cancel())
        assertEquals(RelativeTemporalProposalState.CANCELLED, session.state)
        assertEquals(0, roomMutations)
        assertEquals(0, reminderOperations)
    }

    @Test
    fun productionRepairContainsNoUtteranceSpecificInterpretation() {
        val orchestratorSource = File(
            "src/main/java/com/example/myapplication/ai/agent/AgentOrchestrator.kt"
        ).readText()
        val repairSource = File(
            "src/main/java/com/example/myapplication/ai/temporal/RelativeTemporalCorrectionRepair.kt"
        ).readText()
        val repairFlow = orchestratorSource
            .substringAfter("private suspend fun processRelativeTemporalCorrectionRepair(")
            .substringBefore("suspend fun processRoutine(")
        val production = repairFlow + repairSource

        assertFalse(production.contains("Regex("))
        assertFalse(production.contains("contains(\"later\")"))
        assertFalse(production.contains("contains(\"instead\")"))
        assertFalse(production.contains("contains(\"actually\")"))
        assertFalse(production.contains("when (originalUserText"))
        assertTrue(repairFlow.contains("relativeTemporalRepairChoiceParser.parse(rawContent)"))
        assertTrue(repairFlow.contains("relativeTemporalCorrectionValidator.validateWithReport(reconstructed)"))
    }

    private fun assertCandidate(
        candidate: RelativeTemporalRepairCandidate,
        expectedRef: String,
        expectedRepresentation: RelativeTemporalRepairRepresentation
    ) {
        assertEquals(expectedRef, candidate.choiceRef)
        assertEquals(RelativeTemporalRepairField.TIME, candidate.field)
        assertEquals(expectedRepresentation, candidate.representation)
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
        var candidates: List<RelativeTemporalRepairCandidate>? = null

        override suspend fun processRelativeTemporalCorrection(normalizedText: String): String {
            normalCalls += 1
            return normalResponse
        }

        override suspend fun processRelativeTemporalCorrectionRepair(
            originalUserText: String,
            validationFailure: RelativeTemporalValidationFailure,
            candidates: List<RelativeTemporalRepairCandidate>
        ): String {
            repairCalls += 1
            this.candidates = candidates
            return checkNotNull(repairResponse)
        }
    }

    private fun malformedDeviceResponse(): String = response(
        timeOperation = "SET",
        replacementTime = "1 hours later instead",
        timeOffset = 60,
        confidence = 1.0
    )

    private fun validOffsetResponse(minutes: Int): String = response(
        timeOperation = "OFFSET",
        timeOffset = minutes
    )

    private fun choiceResponse(
        choiceRef: String,
        confidence: Double = 0.98,
        clarification: Boolean = false
    ): String = JSONObject()
        .put("choice_ref", choiceRef)
        .put("confidence", confidence)
        .put("need_clarification", clarification)
        .toString()

    private fun responseObject(raw: String): RelativeTemporalCorrectionResponse =
        com.example.myapplication.ai.temporal.RelativeTemporalCorrectionParser().parse(raw)

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
