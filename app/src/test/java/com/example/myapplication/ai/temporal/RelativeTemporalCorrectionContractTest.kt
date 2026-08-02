package com.example.myapplication.ai.temporal

import com.example.myapplication.ai.agent.ContextActionExtractionParseException
import com.example.myapplication.ai.agent.LaptopAgentClient
import com.example.myapplication.ai.schema.AgentResponseSchemas
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RelativeTemporalCorrectionContractTest {
    private val parser = RelativeTemporalCorrectionParser()
    private val validator = RelativeTemporalCorrectionValidator()

    @Test
    fun correctionSchemaIsExactAndExcludesTaskFacts() {
        val schema = AgentResponseSchemas.relativeTemporalCorrectionResponseFormat()
            .getJSONObject("json_schema")
            .getJSONObject("schema")
        val properties = schema.getJSONObject("properties").keys().asSequence().toSet()
        assertEquals(
            setOf(
                "move", "date_operation", "time_operation", "relative_base",
                "replacement_date_text", "replacement_time_text", "date_offset_days",
                "time_offset_minutes", "confidence", "need_clarification"
            ),
            properties
        )
        assertFalse(schema.getBoolean("additionalProperties"))
        listOf("task_id", "room_id", "task_title", "final_date", "final_time").forEach {
            assertFalse(properties.contains(it))
        }
    }

    @Test
    fun parserRejectsAdditionalFieldsAndUnknownMoveNeedsClarification() {
        assertThrows(ContextActionExtractionParseException::class.java) {
            parser.parse(JSONObject(applyResponse()).put("room_id", 42).toString())
        }
        assertThrows(RelativeTemporalProposalValidationException::class.java) {
            validator.validate(parser.parse(JSONObject(applyResponse()).put("move", "OTHER").toString()))
        }
    }

    @Test
    fun currentProposalAndAuthoritativeBaseAreBothRepresentable() {
        val current = validator.validate(
            parser.parse(applyResponse(base = "CURRENT_PROPOSAL", minutes = 30))
        ) as ValidatedRelativeTemporalCorrection.Apply
        val original = validator.validate(
            parser.parse(applyResponse(base = "AUTHORITATIVE_TASK", minutes = 60))
        ) as ValidatedRelativeTemporalCorrection.Apply
        assertEquals(RelativeTemporalBase.CURRENT_PROPOSAL, current.proposal.relativeBase)
        assertEquals(RelativeTemporalBase.AUTHORITATIVE_TASK, original.proposal.relativeBase)
    }

    @Test
    fun correctionCanonicalizesInactiveDatePollutionAndReportsOnlyDateOperation() {
        val validation = validator.validateWithReport(
            parser.parse(
                response(
                    move = "APPLY_CHANGE",
                    dateOperation = "SET",
                    timeOperation = "OFFSET",
                    base = "AUTHORITATIVE_TASK",
                    timeOffset = 30
                )
            )
        )
        val proposal = (validation.correction as ValidatedRelativeTemporalCorrection.Apply).proposal

        assertEquals(RelativeTemporalOperation.KEEP, proposal.dateOperation)
        assertEquals(RelativeTemporalOperation.OFFSET, proposal.timeOperation)
        assertEquals(30, proposal.timeOffsetMinutes)
        assertEquals(listOf("date_operation"), validation.canonicalizationReport.changedFields)
    }

    @Test
    fun correctionConfidenceBelowPointEightFailsClosed() {
        assertThrows(RelativeTemporalProposalValidationException::class.java) {
            validator.validate(parser.parse(applyResponse().replace("0.98", "0.79")))
        }
        val accepted = validator.validate(parser.parse(applyResponse().replace("0.98", "0.80")))
        assertTrue(accepted is ValidatedRelativeTemporalCorrection.Apply)
    }

    @Test
    fun nonFiniteCorrectionConfidenceRetainsItsStrictFailureCategory() {
        val response = parser.parse(applyResponse()).copy(confidence = Double.NaN)
        val exception = assertThrows(RelativeTemporalProposalValidationException::class.java) {
            validator.validate(response)
        }

        assertEquals(RelativeTemporalValidationFailure.NON_FINITE_CONFIDENCE, exception.failure)
    }

    @Test
    fun restoreOriginalRequiresNeutralFields() {
        val restored = validator.validate(
            parser.parse(
                response(
                    move = "RESTORE_ORIGINAL",
                    dateOperation = "KEEP",
                    timeOperation = "KEEP",
                    base = "AUTHORITATIVE_TASK"
                )
            )
        )
        assertTrue(restored is ValidatedRelativeTemporalCorrection.RestoreOriginal)
    }

    @Test
    fun promptUsesMeaningNotAnExhaustiveUtteranceDictionary() {
        val prompt = LaptopAgentClient.RELATIVE_TEMPORAL_CORRECTION_SYSTEM_PROMPT
        assertTrue(prompt.contains("semantically"))
        assertTrue(prompt.contains("not an exhaustive vocabulary or phrase dictionary"))
        assertTrue(prompt.contains("AUTHORITATIVE_TASK means"))
        assertTrue(prompt.contains("CURRENT_PROPOSAL means"))
        assertTrue(prompt.contains("requires clarification rather than guessing"))
        assertTrue(prompt.contains("An unchanged field must use KEEP."))
        assertTrue(prompt.contains("A pure time offset must use date_operation=KEEP."))
        assertTrue(prompt.contains("SET must never be returned without non-empty literal replacement text."))
        assertTrue(prompt.contains("Verify that each operation matches"))

        val editSource = File("src/main/java/com/example/myapplication/EditTaskActivity.kt").readText()
        val correction = editSource
            .substringAfter("private fun processRelativeTemporalCorrection(")
            .substringBefore("private suspend fun authoritativeTaskStillMatches")
        assertTrue(correction.contains("processRelativeTemporalCorrection(normalized)"))
        assertFalse(correction.contains("Regex("))
        assertFalse(correction.contains("contains(\"later\")"))
        assertFalse(correction.contains("contains(\"earlier\")"))
    }

    @Test
    fun repairChoiceSchemaIsExactDynamicAndCannotReturnOperationFields() {
        val schema = AgentResponseSchemas.relativeTemporalRepairChoiceResponseFormat(
            listOf("R1", "R2")
        ).getJSONObject("json_schema").getJSONObject("schema")
        val properties = schema.getJSONObject("properties").keys().asSequence().toSet()
        val choiceRefs = schema.getJSONObject("properties")
            .getJSONObject("choice_ref")
            .getJSONArray("enum")

        assertEquals(
            setOf("choice_ref", "relative_base", "confidence", "need_clarification"),
            properties
        )
        assertEquals(listOf("R1", "R2", "CLARIFY"), (0 until choiceRefs.length()).map {
            choiceRefs.getString(it)
        })
        assertFalse(schema.getBoolean("additionalProperties"))
        listOf(
            "move", "date_operation", "time_operation", "replacement_date_text",
            "replacement_time_text", "date_offset_days", "time_offset_minutes"
        ).forEach { assertFalse(properties.contains(it)) }
    }

    @Test
    fun repairChoiceParserRejectsMissingAdditionalAndUnknownRefsFailValidation() {
        val parser = RelativeTemporalRepairChoiceParser()
        val validator = RelativeTemporalRepairChoiceValidator()
        val candidate = RelativeTemporalRepairCandidateBuilder().build(
            RelativeTemporalCorrectionParser().parse(
                response(
                    move = "APPLY_CHANGE",
                    dateOperation = "KEEP",
                    timeOperation = "SET",
                    base = "AUTHORITATIVE_TASK",
                    replacementTime = "duration literal",
                    timeOffset = 60
                )
            ),
            RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION
        ).first()
        val validChoice = JSONObject()
            .put("choice_ref", candidate.choiceRef)
            .put("relative_base", "AUTHORITATIVE_TASK")
            .put("confidence", 0.98)
            .put("need_clarification", false)

        assertThrows(ContextActionExtractionParseException::class.java) {
            parser.parse(JSONObject(validChoice.toString()).put("time_operation", "OFFSET").toString())
        }
        assertThrows(ContextActionExtractionParseException::class.java) {
            parser.parse(JSONObject(validChoice.toString()).apply { remove("confidence") }.toString())
        }
        assertThrows(ContextActionExtractionParseException::class.java) {
            parser.parse(validChoice.toString() + validChoice.toString())
        }
        assertThrows(RelativeTemporalRepairChoiceValidationException::class.java) {
            validator.validate(
                parser.parse(JSONObject(validChoice.toString()).put("choice_ref", "R9").toString()),
                listOf(candidate)
            )
        }
    }

    @Test
    fun repairPromptUsesCandidateChoiceAndPreservesAndroidAuthority() {
        val prompt = LaptopAgentClient.RELATIVE_TEMPORAL_CORRECTION_REPAIR_SYSTEM_PROMPT

        assertTrue(prompt.contains("choose among bounded Android-constructed interpretations"))
        assertTrue(prompt.contains("Return exactly: choice_ref, relative_base, confidence"))
        assertTrue(prompt.contains("Never return temporal"))
        assertTrue(prompt.contains("never invent a candidate"))
        assertTrue(prompt.contains("AUTHORITATIVE_TASK means"))
        assertTrue(prompt.contains("CURRENT_PROPOSAL means"))
        listOf("task IDs", "Room IDs", "task titles", "final dates", "saved").forEach {
            assertTrue(prompt.contains(it))
        }

        val source = File(
            "src/main/java/com/example/myapplication/ai/agent/LaptopAgentClient.kt"
        ).readText()
        val method = source
            .substringAfter("open suspend fun processRelativeTemporalCorrectionRepair(")
            .substringBefore("open suspend fun processBreakdownFollowUp(")
        assertTrue(method.contains("relativeTemporalRepairChoiceResponseFormat("))
        assertTrue(method.contains("RELATIVE_TEMPORAL_MAX_TOKENS"))
        assertTrue(method.contains("boundedTemporalClient"))
        assertTrue(method.contains("original_user_correction"))
        assertTrue(method.contains("validation_failure"))
        assertTrue(method.contains("candidates"))
        assertFalse(method.contains("rejected_candidate"))
        assertFalse(method.contains("replacement_date_text"))
        assertFalse(method.contains("replacement_time_text"))
        listOf("task_id", "room_id", "task_title", "final_date", "final_time").forEach {
            assertFalse(method.contains("put(\"$it\""))
        }
    }

    private fun applyResponse(
        base: String = "AUTHORITATIVE_TASK",
        minutes: Int = 30
    ): String = response(
        move = "APPLY_CHANGE",
        dateOperation = "KEEP",
        timeOperation = "OFFSET",
        base = base,
        timeOffset = minutes
    )

    private fun response(
        move: String,
        dateOperation: String,
        timeOperation: String,
        base: String,
        replacementDate: String = "",
        replacementTime: String = "",
        dateOffset: Int = 0,
        timeOffset: Int = 0,
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

}
