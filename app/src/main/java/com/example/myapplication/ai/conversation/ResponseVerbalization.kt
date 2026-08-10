package com.example.myapplication.ai.conversation

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

enum class ResponseVerbalizationTone { FRIENDLY, NEUTRAL, PROFESSIONAL }
enum class ResponseVerbalizationVerbosity { SHORT, NORMAL, DETAILED }
enum class ResponseVerbalizationContract {
    AUTHORITATIVE_MESSAGE,
    TASK_CONFIRMATION,
    TASK_ACTION_RESULT,
    TASK_TRANSITION
}

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
                ResponseVerbalizationContract.TASK_CONFIRMATION ->
                    operation == ExecutionOperation.DELETE_TASK &&
                        outcome == ExecutionOutcome.NEEDS_CONFIRMATION &&
                        requiredInput == RequiredInput.CONFIRMATION &&
                        requiredPlaceholders == setOf(TASK_TITLE)
                ResponseVerbalizationContract.TASK_ACTION_RESULT ->
                    outcome == ExecutionOutcome.SUCCESS &&
                        operation in SIMPLE_RESULT_OPERATIONS &&
                        requiredPlaceholders == setOf(TASK_TITLE)
                ResponseVerbalizationContract.TASK_TRANSITION ->
                    outcome == ExecutionOutcome.INFORMATION && when (operation) {
                        ExecutionOperation.CREATE_TASK ->
                            requiredPlaceholders == setOf(TRANSITION_TARGET)
                        ExecutionOperation.UPDATE_TASK,
                        ExecutionOperation.RESCHEDULE_TASK ->
                            requiredPlaceholders == setOf(TASK_TITLE)
                        else -> false
                    }
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
        const val TRANSITION_TARGET = "transition_target"

        private val SIMPLE_RESULT_OPERATIONS = setOf(
            ExecutionOperation.DELETE_TASK,
            ExecutionOperation.MARK_DONE,
            ExecutionOperation.MARK_UNDONE
        )
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
        val protectedContract = protectedSimpleContractOrNull(observation)
        val contract = protectedContract?.first
            ?: ResponseVerbalizationContract.AUTHORITATIVE_MESSAGE
        val protectedValues = protectedContract?.second ?: mapOf(
            ResponseVerbalizationPlan.AUTHORITATIVE_MESSAGE to deterministic.speech
        )
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

    private fun protectedSimpleContractOrNull(
        observation: ExecutionObservation
    ): Pair<ResponseVerbalizationContract, Map<String, String>>? {
        if (
            observation.operation == ExecutionOperation.DELETE_TASK &&
            observation.outcome == ExecutionOutcome.NEEDS_CONFIRMATION &&
            observation.requiredInput == RequiredInput.CONFIRMATION &&
            observation.taskTitle.isNotBlank()
        ) {
            return ResponseVerbalizationContract.TASK_CONFIRMATION to mapOf(
                ResponseVerbalizationPlan.TASK_TITLE to observation.taskTitle
            )
        }

        if (
            observation.outcome == ExecutionOutcome.SUCCESS &&
            observation.taskTitle.isNotBlank() &&
            observation.operation in setOf(
                ExecutionOperation.DELETE_TASK,
                ExecutionOperation.MARK_DONE,
                ExecutionOperation.MARK_UNDONE
            )
        ) {
            return ResponseVerbalizationContract.TASK_ACTION_RESULT to mapOf(
                ResponseVerbalizationPlan.TASK_TITLE to observation.taskTitle
            )
        }

        if (observation.outcome != ExecutionOutcome.INFORMATION) return null
        return when (observation.operation) {
            ExecutionOperation.CREATE_TASK ->
                ResponseVerbalizationContract.TASK_TRANSITION to mapOf(
                    ResponseVerbalizationPlan.TRANSITION_TARGET to "task creation"
                )
            ExecutionOperation.UPDATE_TASK,
            ExecutionOperation.RESCHEDULE_TASK -> observation.taskTitle
                .takeIf(String::isNotBlank)
                ?.let {
                    ResponseVerbalizationContract.TASK_TRANSITION to mapOf(
                        ResponseVerbalizationPlan.TASK_TITLE to it
                    )
                }
            else -> null
        }
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
        """\b(room|database|schema|json|android|agent|model|prompt|identifier|uuid|id|task[_ -]?id)\b""",
        RegexOption.IGNORE_CASE
    )
    private val genericFactualOrOperationalTerm = Regex(
        """\b(tasks?|titles?|dates?|times?|counts?|pages?|items?|results?|created?|saved?|deleted?|removed?|completed?|incomplete|marked|updated?|rescheduled?|moved|opened?|closed?|scheduled?|reminders?|found|exists?|available|overdue|remaining|matching|anything|nothing|today|tomorrow|yesterday|weeks?|months?|years?|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""",
        RegexOption.IGNORE_CASE
    )
    private val genericControlInstruction = Regex(
        """\b(confirm|reject|cancel|choose|select|pick|say|answer|respond|reply|continue|repeat|stop|retry|listen|yes|no|next|first|second|third|last|please)\b""",
        RegexOption.IGNORE_CASE
    )
    private val atomicAddedFact = Regex(
        """\b(tasks?|titles?|dates?|times?|counts?|pages?|items?|results?|created?|saved?|updated|rescheduled|moved|opened|closed|scheduled|reminders?|found|exists?|available|overdue|remaining|matching|anything|nothing|today|tomorrow|yesterday|weeks?|months?|years?|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""",
        RegexOption.IGNORE_CASE
    )
    private val successClaim = Regex(
        """\b(success|successful|succeeded|finished|done)\b|\ball\s+set\b""",
        RegexOption.IGNORE_CASE
    )
    private val safePresentationCharacters = Regex("""^[\p{L}\s.,!?\-'’—–]*$""")
    private val wrapperWord = Regex("""\p{L}+(?:['’]\p{L}+)?""")
    private val genericWrapperWords = setOf(
        "okay", "ok", "sure", "certainly", "absolutely", "alright", "right",
        "of", "course", "got", "it", "understood", "well", "here", "there",
        "we", "go", "thanks", "thank", "you", "now", "then", "thing", "i",
        "i've", "we've", "your", "the", "has", "have", "been", "was", "is"
    )
    private val successWrapperWords = setOf("all", "set", "done")
    private val acknowledgementWords = setOf(
        "all", "set", "done", "okay", "ok", "sure", "certainly", "alright",
        "i", "i've", "we", "we've", "have", "has", "been", "was", "is", "now",
        "successfully"
    )
    private val confirmationWords = setOf(
        "would", "you", "like", "me", "to", "delete", "remove", "should", "i",
        "do", "want", "just", "confirm", "can", "shall", "sure", "okay", "ok"
    )
    private val transitionWords = setOf(
        "okay", "ok", "sure", "certainly", "alright", "right", "now", "i", "i'll",
        "will", "let's", "let", "us", "we", "can", "open", "take", "you", "to", "go",
        "for", "so", "make", "that", "the", "a", "change", "edit", "editing", "update",
        "it", "its", "reschedule", "schedule"
    )

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
        val presentationText = placeholder.replace(template, " ").trim()
        if (presentationText.contains('{') || presentationText.contains('}')) {
            return rejected(ResponseVerbalizationValidationReason.MALFORMED_PLACEHOLDER)
        }
        val wrapperWords = wrapperWord.findAll(presentationText)
            .map { it.value.lowercase(Locale.ROOT) }
            .toList()
        val maxWrapperWords = when (plan.contract) {
            ResponseVerbalizationContract.AUTHORITATIVE_MESSAGE -> when (plan.verbosity) {
                ResponseVerbalizationVerbosity.SHORT -> 4
                ResponseVerbalizationVerbosity.NORMAL -> 8
                ResponseVerbalizationVerbosity.DETAILED -> 12
            }
            else -> when (plan.verbosity) {
                ResponseVerbalizationVerbosity.SHORT -> 10
                ResponseVerbalizationVerbosity.NORMAL -> 14
                ResponseVerbalizationVerbosity.DETAILED -> 18
            }
        }
        if (wrapperWords.size > maxWrapperWords) {
            return rejected(ResponseVerbalizationValidationReason.TEMPLATE_TOO_LONG)
        }
        if (
            presentationText.contains('\n') ||
            presentationText.contains('\r') ||
            presentationText.any(Char::isDigit) ||
            url.containsMatchIn(presentationText) ||
            markdown.containsMatchIn(presentationText) ||
            temporaryRef.containsMatchIn(presentationText) ||
            internalIdentifier.containsMatchIn(presentationText) ||
            !safePresentationCharacters.matches(presentationText)
        ) {
            return rejected(ResponseVerbalizationValidationReason.UNSAFE_PRESENTATION_TEXT)
        }

        val contractIsSafe = when (plan.contract) {
            ResponseVerbalizationContract.AUTHORITATIVE_MESSAGE ->
                hasSafeAuthoritativeWrapper(plan, presentationText, wrapperWords)
            ResponseVerbalizationContract.TASK_CONFIRMATION ->
                hasSafeDeleteConfirmation(template, presentationText, wrapperWords)
            ResponseVerbalizationContract.TASK_ACTION_RESULT ->
                hasSafeTaskActionResult(plan.operation, template, presentationText, wrapperWords)
            ResponseVerbalizationContract.TASK_TRANSITION ->
                hasSafeTaskTransition(plan.operation, template, presentationText, wrapperWords)
        }
        if (!contractIsSafe) {
            return rejected(ResponseVerbalizationValidationReason.UNSAFE_PRESENTATION_TEXT)
        }
        return ResponseVerbalizationValidationResult(
            accepted = true,
            reason = ResponseVerbalizationValidationReason.ACCEPTED
        )
    }

    private fun rejected(reason: ResponseVerbalizationValidationReason) =
        ResponseVerbalizationValidationResult(accepted = false, reason = reason)

    private fun hasSafeAuthoritativeWrapper(
        plan: ResponseVerbalizationPlan,
        presentationText: String,
        words: List<String>
    ): Boolean {
        if (
            genericControlInstruction.containsMatchIn(presentationText) ||
            '?' in presentationText ||
            genericFactualOrOperationalTerm.containsMatchIn(presentationText)
        ) {
            return false
        }
        val successLanguageAllowed = plan.outcome == ExecutionOutcome.SUCCESS ||
            plan.outcome == ExecutionOutcome.PARTIAL_SUCCESS
        if (!successLanguageAllowed && successClaim.containsMatchIn(presentationText)) return false
        val allowedWords = if (successLanguageAllowed) {
            genericWrapperWords + successWrapperWords
        } else {
            genericWrapperWords
        }
        return words.all { it in allowedWords }
    }

    private fun hasSafeDeleteConfirmation(
        template: String,
        presentationText: String,
        words: List<String>
    ): Boolean {
        if (!template.trim().endsWith('?') || atomicAddedFact.containsMatchIn(presentationText)) {
            return false
        }
        if (words.any { it !in confirmationWords }) return false
        val title = Regex.escape("{${ResponseVerbalizationPlan.TASK_TITLE}}")
        val naturalQuestion = Regex(
            """(?:(?:sure|okay|ok|just to confirm) )?(?:would you like me to|do you want me to|should i|can i|shall i) (?:delete|remove) $title"""
        )
        return naturalQuestion.matches(canonical(template))
    }

    private fun hasSafeTaskActionResult(
        operation: ExecutionOperation,
        template: String,
        presentationText: String,
        words: List<String>
    ): Boolean {
        if ('?' in template || atomicAddedFact.containsMatchIn(presentationText)) return false
        val title = Regex.escape("{${ResponseVerbalizationPlan.TASK_TITLE}}")
        val canonical = canonical(template)
        val actor = "(?:i['’]ve|i have|we['’]ve|we have|i|we)"
        val state = "(?:has been|was|is now|is)"
        val acknowledgement = "(?:(?:all set|done|okay|ok|sure|certainly|alright) )?"
        return when (operation) {
            ExecutionOperation.DELETE_TASK -> {
                val allowed = acknowledgementWords + setOf("deleted", "removed")
                val result = Regex(
                    """$acknowledgement(?:$actor (?:successfully )?(?:deleted|removed) $title|$title $state (?:successfully )?(?:deleted|removed))"""
                )
                words.all { it in allowed } && result.matches(canonical)
            }
            ExecutionOperation.MARK_DONE -> {
                val allowed = acknowledgementWords + setOf(
                    "marked", "as", "complete", "completed"
                )
                val result = Regex(
                    """$acknowledgement(?:$actor (?:successfully )?marked $title as (?:complete|completed|done)|$title $state (?:successfully )?(?:marked as )?(?:complete|completed|done))"""
                )
                words.all { it in allowed } && result.matches(canonical)
            }
            ExecutionOperation.MARK_UNDONE -> {
                val allowed = acknowledgementWords + setOf(
                    "marked", "as", "incomplete", "active", "again", "not", "complete"
                )
                val result = Regex(
                    """$acknowledgement(?:$actor (?:successfully )?marked $title as (?:incomplete|not complete)|$title $state (?:successfully )?(?:marked as )?(?:incomplete|active again|not complete))"""
                )
                words.all { it in allowed } && result.matches(canonical)
            }
            else -> false
        }
    }

    private fun hasSafeTaskTransition(
        operation: ExecutionOperation,
        template: String,
        presentationText: String,
        words: List<String>
    ): Boolean {
        if (
            '?' in template ||
            atomicAddedFact.containsMatchIn(presentationText) ||
            words.any { it !in transitionWords }
        ) {
            return false
        }
        val canonical = canonical(template)
        val future = "(?:i['’]ll|i will|let['’]s|let us|we can)"
        val acknowledgement = "(?:(?:okay|ok|sure|certainly|alright) )?"
        return when (operation) {
            ExecutionOperation.CREATE_TASK -> {
                val target = Regex.escape("{${ResponseVerbalizationPlan.TRANSITION_TARGET}}")
                Regex(
                    """$acknowledgement(?:$future open $target(?: for you| now)?|(?:i['’]ll|i will) take you to $target|(?:let['’]s|let us) go to $target)"""
                ).matches(canonical)
            }
            ExecutionOperation.UPDATE_TASK -> {
                val title = Regex.escape("{${ResponseVerbalizationPlan.TASK_TITLE}}")
                Regex(
                    """$acknowledgement$future open $title(?: so you can (?:make that change|edit it|update it)| for editing)?"""
                ).matches(canonical)
            }
            ExecutionOperation.RESCHEDULE_TASK -> {
                val title = Regex.escape("{${ResponseVerbalizationPlan.TASK_TITLE}}")
                Regex(
                    """$acknowledgement(?:$future open $title(?: so you can (?:reschedule it|update its schedule)| for rescheduling)?|(?:let['’]s|let us|we can) (?:reschedule $title|update (?:the )?schedule for $title))"""
                ).matches(canonical)
            }
            else -> false
        }
    }

    private fun canonical(template: String): String = template
        .lowercase(Locale.ROOT)
        .replace(Regex("""[.,!?—–-]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

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
