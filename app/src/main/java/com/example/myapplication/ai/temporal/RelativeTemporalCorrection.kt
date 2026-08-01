package com.example.myapplication.ai.temporal

import com.example.myapplication.ai.agent.ContextActionExtractionParseException
import org.json.JSONException
import org.json.JSONObject

enum class RelativeTemporalCorrectionMove {
    APPLY_CHANGE,
    RESTORE_ORIGINAL,
    UNKNOWN
}

data class RelativeTemporalCorrectionResponse(
    val move: String,
    val dateOperation: String,
    val timeOperation: String,
    val relativeBase: String,
    val replacementDateText: String,
    val replacementTimeText: String,
    val dateOffsetDays: Int,
    val timeOffsetMinutes: Int,
    val confidence: Double,
    val needClarification: Boolean
)

sealed class ValidatedRelativeTemporalCorrection {
    data class Apply(val proposal: RelativeTemporalProposal) :
        ValidatedRelativeTemporalCorrection()
    data object RestoreOriginal : ValidatedRelativeTemporalCorrection()
}

data class RelativeTemporalCorrectionValidation(
    val correction: ValidatedRelativeTemporalCorrection,
    val canonicalizationReport: RelativeTemporalCanonicalizationReport
)

class RelativeTemporalCorrectionParser {
    fun parse(rawContent: String): RelativeTemporalCorrectionResponse {
        if (rawContent.isBlank()) throw parseFailure("Relative-temporal correction was blank")
        val strippedContent = stripMarkdownFences(rawContent)
        val jsonText = extractFirstJsonObject(strippedContent)
        if (jsonText != strippedContent.trim()) {
            throw parseFailure("Correction must contain only one JSON object")
        }
        val json = try {
            JSONObject(jsonText)
        } catch (exception: JSONException) {
            throw parseFailure("Invalid relative-temporal correction JSON", exception)
        }
        val keys = json.keys().asSequence().toSet()
        val missing = REQUIRED_FIELDS - keys
        val additional = keys - REQUIRED_FIELDS
        if (missing.isNotEmpty()) throw parseFailure("Correction missing fields: ${missing.joinToString()}")
        if (additional.isNotEmpty()) {
            throw parseFailure("Correction contains additional fields: ${additional.joinToString()}")
        }
        return RelativeTemporalCorrectionResponse(
            move = requireString(json, "move"),
            dateOperation = requireString(json, "date_operation"),
            timeOperation = requireString(json, "time_operation"),
            relativeBase = requireString(json, "relative_base"),
            replacementDateText = requireString(json, "replacement_date_text"),
            replacementTimeText = requireString(json, "replacement_time_text"),
            dateOffsetDays = requireInteger(json, "date_offset_days"),
            timeOffsetMinutes = requireInteger(json, "time_offset_minutes"),
            confidence = requireNumber(json, "confidence"),
            needClarification = requireBoolean(json, "need_clarification")
        )
    }

    private fun requireString(json: JSONObject, field: String): String =
        (json.get(field) as? String) ?: throw parseFailure("Field '$field' must be a string")

    private fun requireInteger(json: JSONObject, field: String): Int = when (val value = json.get(field)) {
        is Int -> value
        is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        else -> null
    } ?: throw parseFailure("Field '$field' must be an integer")

    private fun requireNumber(json: JSONObject, field: String): Double =
        (json.get(field) as? Number)?.toDouble()
            ?: throw parseFailure("Field '$field' must be a number")

    private fun requireBoolean(json: JSONObject, field: String): Boolean =
        (json.get(field) as? Boolean) ?: throw parseFailure("Field '$field' must be a boolean")

    private fun stripMarkdownFences(content: String): String {
        var text = content.trim()
        if (text.startsWith("```")) {
            text = text.removePrefix("```").trimStart()
            if (text.startsWith("json", ignoreCase = true)) text = text.drop(4).trimStart()
            val lastFence = text.lastIndexOf("```")
            if (lastFence >= 0) text = text.substring(0, lastFence).trim()
        }
        return text
    }

    private fun extractFirstJsonObject(text: String): String {
        val start = text.indexOf('{')
        if (start < 0) throw parseFailure("Correction did not contain a JSON object")
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val character = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
            } else {
                when (character) {
                    '"' -> inString = true
                    '{' -> depth += 1
                    '}' -> {
                        depth -= 1
                        if (depth == 0) return text.substring(start, index + 1)
                    }
                }
            }
        }
        throw parseFailure("Correction contained an incomplete JSON object")
    }

    private fun parseFailure(message: String, cause: Throwable? = null) =
        ContextActionExtractionParseException(message, cause)

    private companion object {
        val REQUIRED_FIELDS = setOf(
            "move",
            "date_operation",
            "time_operation",
            "relative_base",
            "replacement_date_text",
            "replacement_time_text",
            "date_offset_days",
            "time_offset_minutes",
            "confidence",
            "need_clarification"
        )
    }
}

class RelativeTemporalCorrectionValidator(
    private val proposalValidator: RelativeTemporalProposalValidator =
        RelativeTemporalProposalValidator(),
    private val proposalCanonicalizer: RelativeTemporalProposalCanonicalizer =
        RelativeTemporalProposalCanonicalizer()
) {
    fun validate(
        response: RelativeTemporalCorrectionResponse
    ): ValidatedRelativeTemporalCorrection = validateWithReport(response).correction

    fun validateWithReport(
        response: RelativeTemporalCorrectionResponse
    ): RelativeTemporalCorrectionValidation {
        val move = try {
            RelativeTemporalCorrectionMove.valueOf(response.move)
        } catch (_: IllegalArgumentException) {
            RelativeTemporalCorrectionMove.UNKNOWN
        }
        if (!response.confidence.isFinite() ||
            response.confidence !in RelativeTemporalProposal.MIN_CONFIDENCE..1.0
        ) {
            throw RelativeTemporalProposalValidationException(
                RelativeTemporalValidationFailure.LOW_CONFIDENCE,
                "Correction confidence is too low"
            )
        }
        if (response.needClarification || move == RelativeTemporalCorrectionMove.UNKNOWN) {
            throw RelativeTemporalProposalValidationException(
                RelativeTemporalValidationFailure.CLARIFICATION_REQUIRED,
                "Correction requires clarification"
            )
        }
        if (move == RelativeTemporalCorrectionMove.RESTORE_ORIGINAL) {
            validateNeutralRestore(response)
            return RelativeTemporalCorrectionValidation(
                correction = ValidatedRelativeTemporalCorrection.RestoreOriginal,
                canonicalizationReport = RelativeTemporalCanonicalizationReport.NONE
            )
        }
        val dateOperation = response.dateOperation.toOperation(
            RelativeTemporalValidationFailure.UNKNOWN_DATE_OPERATION
        )
        val timeOperation = response.timeOperation.toOperation(
            RelativeTemporalValidationFailure.UNKNOWN_TIME_OPERATION
        )
        val base = try {
            RelativeTemporalBase.valueOf(response.relativeBase)
        } catch (_: IllegalArgumentException) {
            throw validationFailure(
                RelativeTemporalValidationFailure.UNKNOWN_RELATIVE_BASE,
                "Unknown relative base"
            )
        }
        val canonicalized = proposalCanonicalizer.canonicalize(
            RelativeTemporalProposal(
                dateOperation,
                timeOperation,
                base,
                response.replacementDateText,
                response.replacementTimeText,
                response.dateOffsetDays,
                response.timeOffsetMinutes,
                response.confidence,
                response.needClarification
            )
        )
        return RelativeTemporalCorrectionValidation(
            correction = ValidatedRelativeTemporalCorrection.Apply(
                proposalValidator.validate(canonicalized.proposal)
            ),
            canonicalizationReport = canonicalized.report
        )
    }

    private fun validateNeutralRestore(response: RelativeTemporalCorrectionResponse) {
        if (
            response.dateOperation != RelativeTemporalOperation.KEEP.name ||
            response.timeOperation != RelativeTemporalOperation.KEEP.name ||
            response.relativeBase != RelativeTemporalBase.AUTHORITATIVE_TASK.name ||
            response.replacementDateText.isNotBlank() ||
            response.replacementTimeText.isNotBlank() ||
            response.dateOffsetDays != 0 ||
            response.timeOffsetMinutes != 0
        ) {
            throw validationFailure(
                RelativeTemporalValidationFailure.NO_CHANGE,
                "RESTORE_ORIGINAL must use the neutral authoritative representation"
            )
        }
    }

    private fun String.toOperation(
        failure: RelativeTemporalValidationFailure
    ): RelativeTemporalOperation = try {
        RelativeTemporalOperation.valueOf(this)
    } catch (_: IllegalArgumentException) {
        throw validationFailure(failure, "Unknown temporal operation")
    }

    private fun validationFailure(
        failure: RelativeTemporalValidationFailure,
        message: String
    ) = RelativeTemporalProposalValidationException(failure, message)
}
