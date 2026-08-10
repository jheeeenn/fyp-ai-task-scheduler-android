package com.example.myapplication.ai.conversation

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

enum class ResponseVerbalizationTone { FRIENDLY, NEUTRAL, PROFESSIONAL }
enum class ResponseVerbalizationVerbosity { SHORT, NORMAL, DETAILED }
enum class ResponseVerbalizationContract { AUTHORITATIVE_MESSAGE, TASK_ACTION_RESULT }

/**
 * Presentation-only input for the Conversation Agent.
 *
 * Protected values never leave Android. The model receives only non-factual classifications and
 * placeholder names, then Android performs the one-way substitution after validation.
 */
data class ResponseVerbalizationPlan(
    val operation: ExecutionOperation,
    val outcome: ExecutionOutcome,
    val responseType: ConversationResponseType,
    val tone: ResponseVerbalizationTone,
    val verbosity: ResponseVerbalizationVerbosity,
    val contract: ResponseVerbalizationContract,
    val requiredInput: RequiredInput,
    val allowedUserMoves: List<AllowedUserMove>,
    val continuedInteractionExpected: Boolean,
    val protectedValues: Map<String, String>,
    val requiredPlaceholders: Set<String>,
    val deterministicResponse: ConversationResponse
) {
    init {
        require(protectedValues.keys == requiredPlaceholders)
        require(protectedValues.values.all { it.isNotBlank() })
        require(deterministicResponse.responseType == responseType)
        require(
            when (contract) {
                ResponseVerbalizationContract.AUTHORITATIVE_MESSAGE ->
                    requiredPlaceholders == setOf(AUTHORITATIVE_MESSAGE)
                ResponseVerbalizationContract.TASK_ACTION_RESULT ->
                    requiredPlaceholders == setOf(TASK_TITLE, AUTHORITATIVE_ACTION)
            }
        )
    }

    /** The factual protected values and deterministic speech are intentionally omitted. */
    fun toSafeAgentJson(): String = JSONObject().apply {
        put("operation", operation.name)
        put("outcome", outcome.name)
        put("response_type", responseType.name)
        put("tone", tone.name)
        put("verbosity", verbosity.name)
        put("verbalization_contract", contract.name)
        put("required_input", requiredInput.name)
        put(
            "allowed_user_moves",
            JSONArray().apply { allowedUserMoves.forEach { put(it.name) } }
        )
        put("continued_interaction_expected", continuedInteractionExpected)
        put(
            "required_placeholders",
            JSONArray().apply { requiredPlaceholders.sorted().forEach { put(it) } }
        )
    }.toString()

    companion object {
        const val AUTHORITATIVE_MESSAGE = "authoritative_message"
        const val TASK_TITLE = "task_title"
        const val AUTHORITATIVE_ACTION = "authoritative_action"
    }
}

object ResponseVerbalizationPlanner {
    fun createOrNull(
        observation: ExecutionObservation,
        tone: ResponseVerbalizationTone,
        verbosity: ResponseVerbalizationVerbosity
    ): ResponseVerbalizationPlan? {
        // Query pages retain their existing single-call, fact-preserving safe-style path.
        if (AndroidObservationResponseRenderer.taskQueryPlanOrNull(observation) != null) {
            return null
        }
        val deterministic = AndroidObservationResponseRenderer.render(observation)
        val protectedValues = protectedTaskActionValuesOrNull(observation) ?: mapOf(
            ResponseVerbalizationPlan.AUTHORITATIVE_MESSAGE to deterministic.speech
        )
        val contract = if (
            ResponseVerbalizationPlan.AUTHORITATIVE_MESSAGE in protectedValues
        ) {
            ResponseVerbalizationContract.AUTHORITATIVE_MESSAGE
        } else {
            ResponseVerbalizationContract.TASK_ACTION_RESULT
        }
        return ResponseVerbalizationPlan(
            operation = observation.operation,
            outcome = observation.outcome,
            responseType = observation.outcome.toConversationResponseType(),
            tone = tone,
            verbosity = verbosity,
            contract = contract,
            requiredInput = observation.requiredInput,
            allowedUserMoves = observation.allowedUserMoves.toList(),
            continuedInteractionExpected = observation.listenAgain,
            protectedValues = protectedValues,
            requiredPlaceholders = protectedValues.keys,
            deterministicResponse = deterministic
        )
    }

    private fun protectedTaskActionValuesOrNull(
        observation: ExecutionObservation
    ): Map<String, String>? {
        if (observation.outcome != ExecutionOutcome.SUCCESS || observation.taskTitle.isBlank()) {
            return null
        }
        val action = when (observation.operation) {
            ExecutionOperation.DELETE_TASK -> "deleted"
            ExecutionOperation.MARK_DONE -> "marked as complete"
            ExecutionOperation.MARK_UNDONE -> "marked as incomplete"
            else -> return null
        }
        return mapOf(
            ResponseVerbalizationPlan.TASK_TITLE to observation.taskTitle,
            ResponseVerbalizationPlan.AUTHORITATIVE_ACTION to action
        )
    }
}

data class ResponseVerbalizationEnvelope(
    val useVerbalization: Boolean,
    val speechTemplate: String,
    val confidence: Double
)

class ResponseVerbalizationParser {
    fun parse(raw: String): ResponseVerbalizationEnvelope {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || !trimmed.startsWith('{') || !trimmed.endsWith('}')) {
            throw ConversationSchemaException("Response verbalization is not a strict JSON object")
        }
        val json = try {
            val tokener = JSONTokener(trimmed)
            val value = tokener.nextValue()
            if (value !is JSONObject || tokener.nextClean() != '\u0000') {
                throw ConversationSchemaException(
                    "Response verbalization contains content outside its JSON object"
                )
            }
            value
        } catch (e: ConversationSchemaException) {
            throw e
        } catch (e: Exception) {
            throw ConversationSchemaException("Response verbalization is not valid JSON", e)
        }
        val expected = setOf("use_verbalization", "speech_template", "confidence")
        val actual = json.keys().asSequence().toSet()
        if (actual != expected) {
            throw ConversationSchemaException(
                "Response verbalization fields do not match the contract"
            )
        }
        if (
            json.get("use_verbalization") !is Boolean ||
            json.get("speech_template") !is String ||
            json.get("confidence") !is Number
        ) {
            throw ConversationSchemaException(
                "Response verbalization contains an invalid field type"
            )
        }
        return ResponseVerbalizationEnvelope(
            useVerbalization = json.getBoolean("use_verbalization"),
            speechTemplate = json.getString("speech_template"),
            confidence = json.getDouble("confidence")
        )
    }
}

enum class ResponseVerbalizationValidationReason {
    ACCEPTED,
    USE_VERBALIZATION_FALSE,
    LOW_CONFIDENCE,
    CONFIDENCE_OUT_OF_RANGE,
    TEMPLATE_TOO_LONG,
    UNKNOWN_PLACEHOLDER,
    MISSING_REQUIRED_PLACEHOLDER,
    DUPLICATE_REQUIRED_PLACEHOLDER,
    MALFORMED_PLACEHOLDER,
    UNSAFE_PRESENTATION_TEXT,
    UNSUPPORTED_CONTROL_INSTRUCTION,
    INVALID_FORMAT
}

data class ResponseVerbalizationValidationResult(
    val accepted: Boolean,
    val reason: ResponseVerbalizationValidationReason
)

object ResponseVerbalizationValidator {
    private val placeholder = Regex("""\{([a-z][a-z0-9_]*)}""")
    private val url = Regex("""(?i)\b(?:https?://|www\.)\S+""")
    private val markdown = Regex("""(?:^|\s)(?:#{1,6}|[-+*]\s|>\s|```)|[*_~`]""")
    private val temporaryRef = Regex("""\bT\d+\b""", RegexOption.IGNORE_CASE)
    private val internalIdentifier = Regex(
        """\b(room|database|schema|json|android|agent|model|prompt|identifier|uuid|task[_ -]?id)\b""",
        RegexOption.IGNORE_CASE
    )
    private val factualOrOperationalTerm = Regex(
        """\b(tasks?|titles?|dates?|times?|counts?|pages?|items?|results?|created?|saved?|deleted?|removed?|completed?|incomplete|marked|updated?|rescheduled?|moved|opened?|closed?|scheduled?|reminders?|found|exists?|available|overdue|remaining|matching|anything|nothing|today|tomorrow|yesterday|weeks?|months?|years?|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""",
        RegexOption.IGNORE_CASE
    )
    private val controlInstruction = Regex(
        """\b(confirm|reject|cancel|choose|select|pick|say|answer|respond|reply|continue|repeat|stop|retry|listen|yes|no|next|first|second|third|last|please)\b""",
        RegexOption.IGNORE_CASE
    )
    private val successClaim = Regex(
        """\b(success|successful|succeeded|finished|done)\b|\ball\s+set\b""",
        RegexOption.IGNORE_CASE
    )
    private val safeWrapperCharacters = Regex("""^[\p{L}\s.,!\-'’—–]*$""")
    private val wrapperWord = Regex("""\p{L}+(?:['’]\p{L}+)?""")
    private val genericWrapperWords = setOf(
        "okay", "ok", "sure", "certainly", "absolutely", "alright", "right",
        "of", "course", "got", "it", "understood", "well", "here", "there",
        "we", "go", "thanks", "thank", "you", "now", "then", "thing", "i",
        "i've", "we've", "your", "the", "has", "have", "been", "was", "is"
    )
    private val successWrapperWords = setOf("all", "set", "done")

    fun evaluate(
        plan: ResponseVerbalizationPlan,
        envelope: ResponseVerbalizationEnvelope
    ): ResponseVerbalizationValidationResult {
        if (!envelope.useVerbalization) return rejected(
            ResponseVerbalizationValidationReason.USE_VERBALIZATION_FALSE
        )
        if (!envelope.confidence.isFinite() || envelope.confidence !in 0.0..1.0) {
            return rejected(ResponseVerbalizationValidationReason.CONFIDENCE_OUT_OF_RANGE)
        }
        if (envelope.confidence < MIN_CONFIDENCE) {
            return rejected(ResponseVerbalizationValidationReason.LOW_CONFIDENCE)
        }
        val template = envelope.speechTemplate.trim()
        val maxTemplateLength = when (plan.verbosity) {
            ResponseVerbalizationVerbosity.SHORT -> 80
            ResponseVerbalizationVerbosity.NORMAL -> 120
            ResponseVerbalizationVerbosity.DETAILED -> 160
        }
        if (template.isBlank() || template.length > maxTemplateLength) {
            return rejected(ResponseVerbalizationValidationReason.TEMPLATE_TOO_LONG)
        }

        val placeholders = placeholder.findAll(template).map { it.groupValues[1] }.toList()
        if (placeholders.any { it !in plan.protectedValues }) {
            return rejected(ResponseVerbalizationValidationReason.UNKNOWN_PLACEHOLDER)
        }
        if (plan.requiredPlaceholders.any { it !in placeholders }) {
            return rejected(ResponseVerbalizationValidationReason.MISSING_REQUIRED_PLACEHOLDER)
        }
        if (plan.requiredPlaceholders.any { required -> placeholders.count { it == required } != 1 }) {
            return rejected(ResponseVerbalizationValidationReason.DUPLICATE_REQUIRED_PLACEHOLDER)
        }
        if (
            plan.contract == ResponseVerbalizationContract.TASK_ACTION_RESULT &&
            !hasSafeTaskActionStructure(template)
        ) {
            return rejected(ResponseVerbalizationValidationReason.UNSAFE_PRESENTATION_TEXT)
        }

        val presentationText = placeholder.replace(template, " ").trim()
        if (presentationText.contains('{') || presentationText.contains('}')) {
            return rejected(ResponseVerbalizationValidationReason.MALFORMED_PLACEHOLDER)
        }
        val wrapperWords = wrapperWord.findAll(presentationText)
            .map { it.value.lowercase(Locale.ROOT) }
            .toList()
        val maxWrapperWords = when (plan.verbosity) {
            ResponseVerbalizationVerbosity.SHORT -> 4
            ResponseVerbalizationVerbosity.NORMAL -> 8
            ResponseVerbalizationVerbosity.DETAILED -> 12
        }
        if (wrapperWords.size > maxWrapperWords) {
            return rejected(ResponseVerbalizationValidationReason.TEMPLATE_TOO_LONG)
        }
        if (controlInstruction.containsMatchIn(presentationText) || '?' in presentationText) {
            return rejected(
                ResponseVerbalizationValidationReason.UNSUPPORTED_CONTROL_INSTRUCTION
            )
        }
        val successLanguageAllowed = plan.outcome == ExecutionOutcome.SUCCESS ||
            plan.outcome == ExecutionOutcome.PARTIAL_SUCCESS
        val allowedWrapperWords = if (successLanguageAllowed) {
            genericWrapperWords + successWrapperWords
        } else {
            genericWrapperWords
        }
        if (
            presentationText.contains('\n') ||
            presentationText.contains('\r') ||
            presentationText.any(Char::isDigit) ||
            url.containsMatchIn(presentationText) ||
            markdown.containsMatchIn(presentationText) ||
            temporaryRef.containsMatchIn(presentationText) ||
            internalIdentifier.containsMatchIn(presentationText) ||
            factualOrOperationalTerm.containsMatchIn(presentationText) ||
            (!successLanguageAllowed && successClaim.containsMatchIn(presentationText)) ||
            !safeWrapperCharacters.matches(presentationText) ||
            wrapperWords.any { it !in allowedWrapperWords }
        ) {
            return rejected(ResponseVerbalizationValidationReason.UNSAFE_PRESENTATION_TEXT)
        }
        return ResponseVerbalizationValidationResult(
            accepted = true,
            reason = ResponseVerbalizationValidationReason.ACCEPTED
        )
    }

    private fun rejected(reason: ResponseVerbalizationValidationReason) =
        ResponseVerbalizationValidationResult(accepted = false, reason = reason)

    private fun hasSafeTaskActionStructure(template: String): Boolean {
        val titleToken = "{${ResponseVerbalizationPlan.TASK_TITLE}}"
        val actionToken = "{${ResponseVerbalizationPlan.AUTHORITATIVE_ACTION}}"
        val titleIndex = template.indexOf(titleToken)
        val actionIndex = template.indexOf(actionToken)
        return if (actionIndex < titleIndex) {
            val beforeAction = template.substring(0, actionIndex)
            val between = template.substring(actionIndex + actionToken.length, titleIndex)
            Regex(
                """(?i)\b(?:i['’]ve|i\s+have|we['’]ve|we\s+have)\s*$"""
            ).containsMatchIn(beforeAction) && between.isBlank()
        } else {
            val between = template.substring(titleIndex + titleToken.length, actionIndex)
            Regex("""(?i)\b(?:is|was)\b|\bhas\s+been\b""").containsMatchIn(between)
        }
    }

    private const val MIN_CONFIDENCE = 0.85
}

object ResponseVerbalizationComposer {
    fun compose(
        plan: ResponseVerbalizationPlan,
        envelope: ResponseVerbalizationEnvelope
    ): String {
        require(ResponseVerbalizationValidator.evaluate(plan, envelope).accepted)
        var speech = envelope.speechTemplate.trim()
        plan.protectedValues.forEach { (name, value) ->
            speech = speech.replace("{$name}", value)
        }
        require(!speech.contains('{') && !speech.contains('}'))
        require(speech.length <= plan.deterministicResponse.speech.length + 160)
        return speech
    }
}

data class ResponseVerbalizationDeliveryState(
    val requestGeneration: Long,
    val assistantRequestActive: Boolean,
    val activityActive: Boolean
)

enum class ResponseVerbalizationStaleReason {
    REQUEST_CHANGED,
    SESSION_ENDED,
    ACTIVITY_STOPPED
}

object ResponseVerbalizationDeliveryGuard {
    fun staleReason(
        captured: ResponseVerbalizationDeliveryState,
        current: ResponseVerbalizationDeliveryState
    ): ResponseVerbalizationStaleReason? = when {
        captured.requestGeneration != current.requestGeneration ->
            ResponseVerbalizationStaleReason.REQUEST_CHANGED
        !current.activityActive -> ResponseVerbalizationStaleReason.ACTIVITY_STOPPED
        captured.assistantRequestActive && !current.assistantRequestActive ->
            ResponseVerbalizationStaleReason.SESSION_ENDED
        else -> null
    }

    fun runIfCurrent(
        captured: ResponseVerbalizationDeliveryState,
        current: ResponseVerbalizationDeliveryState,
        deliver: () -> Unit
    ): ResponseVerbalizationStaleReason? {
        val reason = staleReason(captured, current)
        if (reason == null) deliver()
        return reason
    }
}
