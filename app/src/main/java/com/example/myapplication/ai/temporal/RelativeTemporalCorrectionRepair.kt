package com.example.myapplication.ai.temporal

import com.example.myapplication.ai.agent.ContextActionExtractionParseException
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

enum class RelativeTemporalRepairField {
    DATE,
    TIME
}

enum class RelativeTemporalExpectedField {
    DATE,
    TIME,
    SCHEDULE
}

data class RelativeTemporalFieldConstraintCanonicalization(
    val response: RelativeTemporalCorrectionResponse,
    val changedFields: List<String>
)

/**
 * Uses an already validated EditTask field move only to repair operation/payload alignment.
 * It never reads user text, chooses a value, calculates a schedule, or bypasses strict validation.
 */
class RelativeTemporalFieldConstraintCanonicalizer {
    fun conflictingFieldPayloadFailure(
        response: RelativeTemporalCorrectionResponse,
        expectedField: RelativeTemporalExpectedField?
    ): RelativeTemporalValidationFailure? = when (expectedField) {
        RelativeTemporalExpectedField.DATE -> if (
            response.replacementTimeText.isNotBlank() || response.timeOffsetMinutes != 0
        ) {
            RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION
        } else {
            null
        }
        RelativeTemporalExpectedField.TIME -> if (
            response.replacementDateText.isNotBlank() || response.dateOffsetDays != 0
        ) {
            RelativeTemporalValidationFailure.MALFORMED_DATE_COMBINATION
        } else {
            null
        }
        RelativeTemporalExpectedField.SCHEDULE,
        null -> null
    }

    fun hasConflictingFieldPayload(
        response: RelativeTemporalCorrectionResponse,
        expectedField: RelativeTemporalExpectedField?
    ): Boolean = conflictingFieldPayloadFailure(response, expectedField) != null

    fun canonicalize(
        rejected: RelativeTemporalCorrectionResponse,
        failure: RelativeTemporalValidationFailure,
        expectedField: RelativeTemporalExpectedField?
    ): RelativeTemporalFieldConstraintCanonicalization? {
        if (
            expectedField == null ||
            expectedField == RelativeTemporalExpectedField.SCHEDULE ||
            failure !in STRUCTURAL_FAILURES ||
            !rejected.confidence.isFinite() ||
            rejected.confidence !in RelativeTemporalProposal.MIN_CONFIDENCE..1.0 ||
            rejected.needClarification ||
            rejected.move != RelativeTemporalCorrectionMove.APPLY_CHANGE.name ||
            RelativeTemporalCorrectionRelationMapper.map(rejected.correctionRelation) == null ||
            hasConflictingFieldPayload(rejected, expectedField)
        ) {
            return null
        }

        val dateLiteralPresent = rejected.replacementDateText.isNotBlank()
        val timeLiteralPresent = rejected.replacementTimeText.isNotBlank()
        val dateOffsetPresent = rejected.dateOffsetDays != 0
        val timeOffsetPresent = rejected.timeOffsetMinutes != 0

        val canonical = when (expectedField) {
            RelativeTemporalExpectedField.DATE -> {
                if (
                    timeLiteralPresent ||
                    timeOffsetPresent ||
                    dateLiteralPresent == dateOffsetPresent
                ) return null
                rejected.copy(
                    dateOperation = if (dateLiteralPresent) {
                        RelativeTemporalOperation.SET.name
                    } else {
                        RelativeTemporalOperation.OFFSET.name
                    },
                    timeOperation = RelativeTemporalOperation.KEEP.name
                )
            }
            RelativeTemporalExpectedField.TIME -> {
                if (
                    dateLiteralPresent ||
                    dateOffsetPresent ||
                    timeLiteralPresent == timeOffsetPresent
                ) return null
                rejected.copy(
                    dateOperation = RelativeTemporalOperation.KEEP.name,
                    timeOperation = if (timeLiteralPresent) {
                        RelativeTemporalOperation.SET.name
                    } else {
                        RelativeTemporalOperation.OFFSET.name
                    }
                )
            }
            RelativeTemporalExpectedField.SCHEDULE -> return null
        }
        val changedFields = buildList {
            if (canonical.dateOperation != rejected.dateOperation) add(DATE_OPERATION_FIELD)
            if (canonical.timeOperation != rejected.timeOperation) add(TIME_OPERATION_FIELD)
        }
        return changedFields.takeIf { it.isNotEmpty() }?.let {
            RelativeTemporalFieldConstraintCanonicalization(canonical, it)
        }
    }

    private companion object {
        const val DATE_OPERATION_FIELD = "date_operation"
        const val TIME_OPERATION_FIELD = "time_operation"
        val STRUCTURAL_FAILURES = setOf(
            RelativeTemporalValidationFailure.MALFORMED_DATE_COMBINATION,
            RelativeTemporalValidationFailure.ZERO_DATE_OFFSET,
            RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION,
            RelativeTemporalValidationFailure.ZERO_TIME_OFFSET
        )
    }
}

enum class RelativeTemporalRepairRepresentation {
    LITERAL,
    OFFSET,
    KEEP
}

data class RelativeTemporalRepairCandidate(
    val choiceRef: String,
    val field: RelativeTemporalRepairField,
    val representation: RelativeTemporalRepairRepresentation,
    val response: RelativeTemporalCorrectionResponse
) {
    val literalPresent: Boolean
        get() = when (this.field) {
            RelativeTemporalRepairField.DATE -> response.replacementDateText.isNotBlank()
            RelativeTemporalRepairField.TIME -> response.replacementTimeText.isNotBlank()
        }

    val dateOffsetDays: Int
        get() = if (
            this.field == RelativeTemporalRepairField.DATE &&
            representation == RelativeTemporalRepairRepresentation.OFFSET
        ) {
            response.dateOffsetDays
        } else {
            0
        }

    val timeOffsetMinutes: Int
        get() = if (
            this.field == RelativeTemporalRepairField.TIME &&
            representation == RelativeTemporalRepairRepresentation.OFFSET
        ) {
            response.timeOffsetMinutes
        } else {
            0
        }
}

/**
 * Builds only representations already present in the rejected structured response. Candidate
 * generation never inspects user text and every complete response must pass the existing strict
 * correction validator before it can be offered to the semantic choice call.
 */
class RelativeTemporalRepairCandidateBuilder(
    private val correctionValidator: RelativeTemporalCorrectionValidator =
        RelativeTemporalCorrectionValidator()
) {
    fun build(
        rejected: RelativeTemporalCorrectionResponse,
        failure: RelativeTemporalValidationFailure
    ): List<RelativeTemporalRepairCandidate> {
        val field = failure.repairField() ?: return emptyList()
        val variants = when (field) {
            RelativeTemporalRepairField.DATE -> dateVariants(rejected)
            RelativeTemporalRepairField.TIME -> timeVariants(rejected)
        }
        return variants.mapNotNull { (representation, response) ->
            try {
                correctionValidator.validate(response)
                representation to response
            } catch (_: RelativeTemporalProposalValidationException) {
                null
            }
        }.mapIndexed { index, (representation, response) ->
            RelativeTemporalRepairCandidate(
                choiceRef = "R${index + 1}",
                field = field,
                representation = representation,
                response = response
            )
        }.toList()
    }

    fun reconstructResponse(
        candidate: RelativeTemporalRepairCandidate,
        confidence: Double
    ): RelativeTemporalCorrectionResponse = candidate.response.copy(
        confidence = confidence,
        needClarification = false
    )

    private fun dateVariants(
        rejected: RelativeTemporalCorrectionResponse
    ): List<Pair<RelativeTemporalRepairRepresentation, RelativeTemporalCorrectionResponse>> =
        buildList {
            if (rejected.replacementDateText.isNotBlank()) {
                add(
                    RelativeTemporalRepairRepresentation.LITERAL to rejected.copy(
                        dateOperation = RelativeTemporalOperation.SET.name,
                        dateOffsetDays = 0
                    )
                )
            }
            if (
                rejected.dateOffsetDays != 0 &&
                rejected.dateOffsetDays in
                -RelativeTemporalProposal.MAX_ABSOLUTE_DATE_OFFSET_DAYS..
                    RelativeTemporalProposal.MAX_ABSOLUTE_DATE_OFFSET_DAYS
            ) {
                add(
                    RelativeTemporalRepairRepresentation.OFFSET to rejected.copy(
                        dateOperation = RelativeTemporalOperation.OFFSET.name,
                        replacementDateText = ""
                    )
                )
            }
            if (rejected.replacementDateText.isBlank() && rejected.dateOffsetDays == 0) {
                add(
                    RelativeTemporalRepairRepresentation.KEEP to rejected.copy(
                        dateOperation = RelativeTemporalOperation.KEEP.name
                    )
                )
            }
        }

    private fun timeVariants(
        rejected: RelativeTemporalCorrectionResponse
    ): List<Pair<RelativeTemporalRepairRepresentation, RelativeTemporalCorrectionResponse>> =
        buildList {
            if (rejected.replacementTimeText.isNotBlank()) {
                add(
                    RelativeTemporalRepairRepresentation.LITERAL to rejected.copy(
                        timeOperation = RelativeTemporalOperation.SET.name,
                        timeOffsetMinutes = 0
                    )
                )
            }
            if (
                rejected.timeOffsetMinutes != 0 &&
                rejected.timeOffsetMinutes in
                -RelativeTemporalProposal.MAX_ABSOLUTE_TIME_OFFSET_MINUTES..
                    RelativeTemporalProposal.MAX_ABSOLUTE_TIME_OFFSET_MINUTES
            ) {
                add(
                    RelativeTemporalRepairRepresentation.OFFSET to rejected.copy(
                        timeOperation = RelativeTemporalOperation.OFFSET.name,
                        replacementTimeText = ""
                    )
                )
            }
            if (rejected.replacementTimeText.isBlank() && rejected.timeOffsetMinutes == 0) {
                add(
                    RelativeTemporalRepairRepresentation.KEEP to rejected.copy(
                        timeOperation = RelativeTemporalOperation.KEEP.name
                    )
                )
            }
        }

    private fun RelativeTemporalValidationFailure.repairField(): RelativeTemporalRepairField? =
        when (this) {
            RelativeTemporalValidationFailure.MALFORMED_DATE_COMBINATION,
            RelativeTemporalValidationFailure.ZERO_DATE_OFFSET -> RelativeTemporalRepairField.DATE

            RelativeTemporalValidationFailure.MALFORMED_TIME_COMBINATION,
            RelativeTemporalValidationFailure.ZERO_TIME_OFFSET -> RelativeTemporalRepairField.TIME

            else -> null
        }
}

data class RelativeTemporalRepairChoiceResponse(
    val choiceRef: String,
    val confidence: Double,
    val needClarification: Boolean
)

class RelativeTemporalRepairChoiceParser {
    fun parse(rawContent: String): RelativeTemporalRepairChoiceResponse {
        if (rawContent.isBlank()) throw parseFailure("Repair choice was blank")
        val json = try {
            val tokener = JSONTokener(rawContent.trim())
            val value = tokener.nextValue()
            if (value !is JSONObject || tokener.nextClean().code != 0) {
                throw parseFailure("Repair choice must contain only one JSON object")
            }
            value
        } catch (exception: JSONException) {
            throw parseFailure("Invalid repair-choice JSON", exception)
        }
        val keys = json.keys().asSequence().toSet()
        val missing = REQUIRED_FIELDS - keys
        val additional = keys - REQUIRED_FIELDS
        if (missing.isNotEmpty()) {
            throw parseFailure("Repair choice missing fields: ${missing.joinToString()}")
        }
        if (additional.isNotEmpty()) {
            throw parseFailure("Repair choice contains additional fields: ${additional.joinToString()}")
        }
        return RelativeTemporalRepairChoiceResponse(
            choiceRef = (json.get("choice_ref") as? String)
                ?: throw parseFailure("choice_ref must be a string"),
            confidence = (json.get("confidence") as? Number)?.toDouble()
                ?: throw parseFailure("confidence must be a number"),
            needClarification = (json.get("need_clarification") as? Boolean)
                ?: throw parseFailure("need_clarification must be a boolean")
        )
    }

    private fun parseFailure(message: String, cause: Throwable? = null) =
        ContextActionExtractionParseException(message, cause)

    private companion object {
        val REQUIRED_FIELDS = setOf(
            "choice_ref",
            "confidence",
            "need_clarification"
        )
    }
}

enum class RelativeTemporalRepairChoiceFailure {
    UNKNOWN_CHOICE_REF,
    NON_FINITE_CONFIDENCE,
    LOW_CONFIDENCE,
    CLARIFICATION_REQUIRED
}

class RelativeTemporalRepairChoiceValidationException(
    val failure: RelativeTemporalRepairChoiceFailure,
    message: String
) : IllegalArgumentException(message)

data class ValidatedRelativeTemporalRepairChoice(
    val candidate: RelativeTemporalRepairCandidate,
    val confidence: Double
)

class RelativeTemporalRepairChoiceValidator {
    fun validate(
        response: RelativeTemporalRepairChoiceResponse,
        candidates: List<RelativeTemporalRepairCandidate>
    ): ValidatedRelativeTemporalRepairChoice {
        if (!response.confidence.isFinite()) {
            fail(
                RelativeTemporalRepairChoiceFailure.NON_FINITE_CONFIDENCE,
                "Repair-choice confidence must be finite"
            )
        }
        if (response.confidence !in RelativeTemporalProposal.MIN_CONFIDENCE..1.0) {
            fail(
                RelativeTemporalRepairChoiceFailure.LOW_CONFIDENCE,
                "Repair-choice confidence is too low"
            )
        }
        if (response.needClarification || response.choiceRef == CLARIFY_REF) {
            fail(
                RelativeTemporalRepairChoiceFailure.CLARIFICATION_REQUIRED,
                "Repair choice requires clarification"
            )
        }
        val candidate = candidates.singleOrNull { it.choiceRef == response.choiceRef }
            ?: fail(
                RelativeTemporalRepairChoiceFailure.UNKNOWN_CHOICE_REF,
                "Repair choice is not available"
            )
        return ValidatedRelativeTemporalRepairChoice(candidate, response.confidence)
    }

    private fun fail(
        failure: RelativeTemporalRepairChoiceFailure,
        message: String
    ): Nothing = throw RelativeTemporalRepairChoiceValidationException(failure, message)

    companion object {
        const val CLARIFY_REF = "CLARIFY"
    }
}
