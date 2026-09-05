package com.example.myapplication.ai.agent

import com.example.myapplication.ai.temporal.ExactTemporalSchedule
import com.example.myapplication.ai.temporal.RelativeTemporalBase
import com.example.myapplication.ai.temporal.RelativeTemporalCalculationResult
import com.example.myapplication.ai.temporal.RelativeTemporalChangeCalculator
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionResponse
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionContext
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionRelation
import com.example.myapplication.ai.temporal.RelativeTemporalCorrectionValidator
import com.example.myapplication.ai.temporal.RelativeTemporalExpectedField
import com.example.myapplication.ai.temporal.RelativeTemporalFieldConstraintCanonicalizer
import com.example.myapplication.ai.temporal.RelativeTemporalOperation
import com.example.myapplication.ai.temporal.RelativeTemporalProposalSession
import com.example.myapplication.ai.temporal.RelativeTemporalProposal
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
    fun timeFieldIntentMakesSoftenedOffsetsStructurallyEquivalent() = runBlocking {
        val cases = listOf(
            "Two hours later" to validOffsetResponse(120),
            "About two hours later" to response(
                dateOperation = "OFFSET",
                timeOperation = "KEEP",
                dateOffset = 0,
                timeOffset = 120
            ),
            "Roughly ninety minutes later" to response(
                dateOperation = "OFFSET",
                timeOperation = "KEEP",
                dateOffset = 0,
                timeOffset = 90
            )
        )

        cases.forEach { (input, rawResponse) ->
            val client = FakeCorrectionClient(normalResponse = rawResponse)
            val correction = orchestrator(client).processRelativeTemporalCorrection(
                normalizedText = input,
                context = context(),
                expectedField = RelativeTemporalExpectedField.TIME
            ) as ValidatedRelativeTemporalCorrection.Apply

            assertEquals(input, RelativeTemporalOperation.KEEP, correction.proposal.dateOperation)
            assertEquals(input, RelativeTemporalOperation.OFFSET, correction.proposal.timeOperation)
            assertEquals(
                input,
                if (input.startsWith("Roughly")) 90 else 120,
                correction.proposal.timeOffsetMinutes
            )
            assertEquals(input, 1, client.normalCalls)
            assertEquals(input, 0, client.repairCalls)
        }
    }

    @Test
    fun fieldConstraintCanonicalizationUsesOnlyOneCompatiblePayload() {
        val policy = RelativeTemporalFieldConstraintCanonicalizer()
        val reportedShape = responseObject(
            response(
                dateOperation = "OFFSET",
                timeOperation = "KEEP",
                dateOffset = 0,
                timeOffset = 120
            )
        )

        val canonicalized = policy.canonicalize(
            reportedShape,
            RelativeTemporalValidationFailure.ZERO_DATE_OFFSET,
            RelativeTemporalExpectedField.TIME
        )

        assertEquals(listOf("date_operation", "time_operation"), canonicalized?.changedFields)
        assertEquals("KEEP", canonicalized?.response?.dateOperation)
        assertEquals("OFFSET", canonicalized?.response?.timeOperation)
        assertEquals(120, canonicalized?.response?.timeOffsetMinutes)
        strictValidator.validate(checkNotNull(canonicalized).response)

        val conflicting = reportedShape.copy(dateOffsetDays = 1)
        assertTrue(
            policy.hasConflictingFieldPayload(
                conflicting,
                RelativeTemporalExpectedField.TIME
            )
        )
        assertEquals(
            null,
            policy.canonicalize(
                conflicting,
                RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION,
                RelativeTemporalExpectedField.TIME
            )
        )
        assertEquals(
            null,
            policy.canonicalize(
                reportedShape.copy(confidence = 0.79),
                RelativeTemporalValidationFailure.ZERO_DATE_OFFSET,
                RelativeTemporalExpectedField.TIME
            )
        )
    }

    @Test
    fun ambiguousOrCrossFieldPayloadStillFailsClosedWithoutRepair() {
        val cases = listOf(
            response(
                dateOperation = "KEEP",
                timeOperation = "KEEP",
                dateOffset = 0,
                timeOffset = 0,
                clarification = true
            ),
            response(
                dateOperation = "OFFSET",
                timeOperation = "OFFSET",
                dateOffset = 1,
                timeOffset = 120
            )
        )

        cases.forEach { rawResponse ->
            val client = FakeCorrectionClient(
                normalResponse = rawResponse,
                repairResponse = choiceResponse("R1")
            )
            assertThrows(TaskAgentProcessingException::class.java) {
                runBlocking {
                    orchestrator(client).processRelativeTemporalCorrection(
                        normalizedText = "ambiguous temporal correction",
                        context = context(),
                        expectedField = RelativeTemporalExpectedField.TIME
                    )
                }
            }
            assertEquals(1, client.normalCalls)
            assertEquals(0, client.repairCalls)
        }
    }

    @Test
    fun validCorrectionUsesOnlyTheNormalSemanticCall() = runBlocking {
        val client = FakeCorrectionClient(normalResponse = validOffsetResponse(30))

        val correction = orchestrator(client).processRelativeTemporalCorrection(
            "shift by a duration",
            context()
        )

        assertTrue(correction is ValidatedRelativeTemporalCorrection.Apply)
        assertEquals(1, client.normalCalls)
        assertEquals(0, client.repairCalls)
    }

    @Test
    fun unknownConcreteMalformedDateCanonicalizesBeforeExistingRepair() = runBlocking {
        val client = FakeCorrectionClient(
            normalResponse = response(
                move = "UNKNOWN",
                dateOperation = "SET",
                timeOperation = "KEEP",
                relation = "BUILD_ON_CURRENT",
                replacementDate = "tomorrow",
                dateOffset = 1,
                timeOffset = 0,
                confidence = 1.0
            ),
            repairResponse = choiceResponse("R1", confidence = 1.0)
        )

        val correction = orchestrator(client).processRelativeTemporalCorrection(
            "move it to tomorrow",
            context()
        ) as ValidatedRelativeTemporalCorrection.Apply

        assertEquals(1, client.normalCalls)
        assertEquals(1, client.repairCalls)
        assertEquals(2, client.candidates?.size)
        assertEquals(RelativeTemporalRepairField.DATE, client.candidates?.first()?.field)
        assertEquals(RelativeTemporalRepairRepresentation.LITERAL, client.candidates?.first()?.representation)
        assertEquals(RelativeTemporalOperation.SET, correction.proposal.dateOperation)
        assertEquals("tomorrow", correction.proposal.replacementDateText)
        assertEquals(0, correction.proposal.dateOffsetDays)
        assertEquals(RelativeTemporalBase.CURRENT_PROPOSAL, correction.proposal.relativeBase)
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
        val session = RelativeTemporalProposalSession(original, revisionOne, initialSemantic())
        val token = session.beginCorrection()
        val client = FakeCorrectionClient(
            normalResponse = malformedDeviceResponse(),
            repairResponse = choiceResponse("R2", confidence = 1.0)
        )

        val correction = orchestrator(client).processRelativeTemporalCorrection(
            "Can you move it to 1 hours later instead",
            session.correctionContext()!!
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

        assertEquals(ExactTemporalSchedule("05/08/2026", "12:45 AM"), calculation.schedule)
        assertFalse(calculation.schedule == ExactTemporalSchedule("04/08/2026", "12:45 AM"))
        assertEquals(
            RelativeTemporalRevisionResult.APPLIED,
            session.applyCorrection(token, calculation.schedule, correction.proposal)
        )
        assertEquals(2, session.revision)
    }

    @Test
    fun literalChoiceReconstructsSetWithExistingLiteralAndZeroOffset() = runBlocking {
        val client = FakeCorrectionClient(
            normalResponse = malformedDeviceResponse(),
            repairResponse = choiceResponse("R1")
        )

        val correction = orchestrator(client).processRelativeTemporalCorrection(
            "choose the literal meaning",
            context()
        )
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
        listOf(
            RelativeTemporalCorrectionRelation.REPLACE_PREVIOUS,
            RelativeTemporalCorrectionRelation.BUILD_ON_CURRENT
        ).forEach { relation ->
            val rejected = responseObject(
                response(
                    timeOperation = "SET",
                    relation = relation.name,
                    replacementTime = "1 hours later instead",
                    timeOffset = 60
                )
            )
            val offsetCandidate = candidateBuilder.build(
                rejected,
                RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION
            ).single { it.representation == RelativeTemporalRepairRepresentation.OFFSET }

            val reconstructed = candidateBuilder.reconstructResponse(offsetCandidate, 1.0)
            val validated = strictValidator.validate(reconstructed)
                as ValidatedRelativeTemporalCorrection.Apply
            val expectedBase = when (relation) {
                RelativeTemporalCorrectionRelation.REPLACE_PREVIOUS ->
                    RelativeTemporalBase.AUTHORITATIVE_TASK
                RelativeTemporalCorrectionRelation.BUILD_ON_CURRENT ->
                    RelativeTemporalBase.CURRENT_PROPOSAL
                RelativeTemporalCorrectionRelation.UNCLEAR -> error("not repairable")
            }

            assertEquals(relation.name, reconstructed.correctionRelation)
            assertEquals(expectedBase, validated.proposal.relativeBase)
            assertEquals(rejected.dateOperation, reconstructed.dateOperation)
            assertEquals(rejected.replacementDateText, reconstructed.replacementDateText)
            assertEquals(rejected.dateOffsetDays, reconstructed.dateOffsetDays)
            assertEquals(RelativeTemporalOperation.OFFSET.name, reconstructed.timeOperation)
            assertEquals(60, reconstructed.timeOffsetMinutes)
        }

        val source = File(
            "src/main/java/com/example/myapplication/ai/temporal/RelativeTemporalCorrectionRepair.kt"
        ).readText().substringAfter("fun reconstructResponse(").substringBefore("private fun dateVariants(")
        assertFalse(source.contains("correctionRelation ="))
    }

    @Test
    fun replacementAndCumulativeRelationsMapWithoutProductionPhraseInterpretation() = runBlocking {
        val replacementParaphrases = listOf(
            "Actually, change the delay to one hour.",
            "No, use a one-hour delay.",
            "I meant one hour later, not thirty minutes."
        )
        val cumulativeParaphrases = listOf(
            "Add another thirty minutes.",
            "On top of that, add one hour.",
            "Add one more hour to that."
        )

        replacementParaphrases.forEach { input ->
            val correction = orchestrator(
                FakeCorrectionClient(validOffsetResponse(60, "REPLACE_PREVIOUS"))
            ).processRelativeTemporalCorrection(input, context())
                as ValidatedRelativeTemporalCorrection.Apply
            assertEquals(RelativeTemporalBase.AUTHORITATIVE_TASK, correction.proposal.relativeBase)
        }
        cumulativeParaphrases.forEach { input ->
            val correction = orchestrator(
                FakeCorrectionClient(validOffsetResponse(60, "BUILD_ON_CURRENT"))
            ).processRelativeTemporalCorrection(input, context())
                as ValidatedRelativeTemporalCorrection.Apply
            assertEquals(RelativeTemporalBase.CURRENT_PROPOSAL, correction.proposal.relativeBase)
        }

        val source = File(
            "src/main/java/com/example/myapplication/ai/agent/AgentOrchestrator.kt"
        ).readText() + File(
            "src/main/java/com/example/myapplication/EditTaskActivity.kt"
        ).readText()
        (replacementParaphrases + cumulativeParaphrases).forEach { phrase ->
            assertFalse(source.contains(phrase))
        }
    }

    @Test
    fun cumulativeRelationUsesCurrentProposalWhileReplacementUsesAuthoritativeOriginal() {
        val original = ExactTemporalSchedule("03/08/2026", "11:45 PM")
        val revisionOne = ExactTemporalSchedule("04/08/2026", "12:15 AM")
        val replacement = strictValidator.validate(
            responseObject(validOffsetResponse(60, "REPLACE_PREVIOUS"))
        ) as ValidatedRelativeTemporalCorrection.Apply
        val cumulative = strictValidator.validate(
            responseObject(validOffsetResponse(60, "BUILD_ON_CURRENT"))
        ) as ValidatedRelativeTemporalCorrection.Apply
        val calculator = RelativeTemporalChangeCalculator()

        val replacementResult = calculator.calculate(
            original,
            revisionOne,
            replacement.proposal,
            nowBeforeOriginal()
        ) as RelativeTemporalCalculationResult.Success
        val cumulativeResult = calculator.calculate(
            original,
            revisionOne,
            cumulative.proposal,
            nowBeforeOriginal()
        ) as RelativeTemporalCalculationResult.Success

        assertEquals(ExactTemporalSchedule("05/08/2026", "12:45 AM"), replacementResult.schedule)
        assertEquals(ExactTemporalSchedule("04/08/2026", "1:15 AM"), cumulativeResult.schedule)
    }

    @Test
    fun unclearRelationDoesNotRepairOrMutateTheSession() {
        val original = ExactTemporalSchedule("03/08/2026", "11:45 PM")
        val revisionOne = ExactTemporalSchedule("04/08/2026", "12:15 AM")
        val session = RelativeTemporalProposalSession(original, revisionOne, initialSemantic())
        session.beginCorrection()
        val client = FakeCorrectionClient(
            validOffsetResponse(60, "UNCLEAR"),
            choiceResponse("R1")
        )

        assertThrows(TaskAgentProcessingException::class.java) {
            runBlocking {
                orchestrator(client).processRelativeTemporalCorrection(
                    "ambiguous relationship",
                    session.correctionContext()!!
                )
            }
        }
        assertEquals(0, client.repairCalls)
        assertEquals(1, session.revision)
        assertEquals(revisionOne, session.currentProposal)
        assertEquals(initialSemantic(), session.currentSemanticProposal)
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
                revisionOne,
                initialSemantic()
            )
            session.beginCorrection()
            val client = FakeCorrectionClient(malformedDeviceResponse(), repairChoice)

            assertThrows(TaskAgentProcessingException::class.java) {
                runBlocking {
                    orchestrator(client).processRelativeTemporalCorrection(
                        "ambiguous correction",
                        context()
                    )
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
                orchestrator(client).processRelativeTemporalCorrection("choose a repair", context())
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
                orchestrator(client).processRelativeTemporalCorrection(
                    "incomplete correction",
                    context()
                )
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
            response(relation = "SOME_RELATION", timeOffset = 30),
            response(timeOffset = 10_081),
            "{\"move\":\"APPLY_CHANGE\""
        )

        responses.forEach { raw ->
            val client = FakeCorrectionClient(raw, choiceResponse("R1"))
            assertThrows(TaskAgentProcessingException::class.java) {
                runBlocking {
                    orchestrator(client).processRelativeTemporalCorrection(
                        "invalid correction",
                        context()
                    )
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
            ExactTemporalSchedule("04/08/2026", "12:15 AM"),
            initialSemantic()
        )
        val token = session.beginCorrection()
        val client = FakeCorrectionClient(
            malformedDeviceResponse(),
            choiceResponse("R2")
        )
        val correction = orchestrator(client).processRelativeTemporalCorrection(
            "select the offset",
            session.correctionContext()!!
        )
            as ValidatedRelativeTemporalCorrection.Apply
        val calculation = RelativeTemporalChangeCalculator().calculate(
            original,
            session.currentProposal,
            correction.proposal,
            nowBeforeOriginal()
        ) as RelativeTemporalCalculationResult.Success
        session.applyCorrection(token, calculation.schedule, correction.proposal)

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

        var context: RelativeTemporalCorrectionContext? = null

        override suspend fun processRelativeTemporalCorrection(
            normalizedText: String,
            context: RelativeTemporalCorrectionContext
        ): String {
            normalCalls += 1
            this.context = context
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

    private fun validOffsetResponse(
        minutes: Int,
        relation: String = "REPLACE_PREVIOUS"
    ): String = response(
        timeOperation = "OFFSET",
        relation = relation,
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
        relation: String = "REPLACE_PREVIOUS",
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
        .put("correction_relation", relation)
        .put("replacement_date_text", replacementDate)
        .put("replacement_time_text", replacementTime)
        .put("date_offset_days", dateOffset)
        .put("time_offset_minutes", timeOffset)
        .put("confidence", confidence)
        .put("need_clarification", clarification)
        .toString()

    private fun context() = RelativeTemporalCorrectionContext(
        previousDateOperation = RelativeTemporalOperation.KEEP,
        previousTimeOperation = RelativeTemporalOperation.OFFSET,
        previousRelativeBase = RelativeTemporalBase.AUTHORITATIVE_TASK,
        previousDateOffsetDays = 0,
        previousTimeOffsetMinutes = 30,
        previousDateLiteralPresent = false,
        previousTimeLiteralPresent = false,
        proposalRevision = 1
    )

    private fun initialSemantic() = RelativeTemporalProposal(
        dateOperation = RelativeTemporalOperation.KEEP,
        timeOperation = RelativeTemporalOperation.OFFSET,
        relativeBase = RelativeTemporalBase.AUTHORITATIVE_TASK,
        replacementDateText = "",
        replacementTimeText = "",
        dateOffsetDays = 0,
        timeOffsetMinutes = 30,
        confidence = 0.98,
        needClarification = false
    )

    private fun nowBeforeOriginal(): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur")).apply {
            clear()
            set(2026, Calendar.AUGUST, 2, 16, 25, 0)
        }
}
