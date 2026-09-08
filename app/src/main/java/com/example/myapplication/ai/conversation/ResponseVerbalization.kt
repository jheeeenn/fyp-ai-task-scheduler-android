package com.example.myapplication.ai.conversation

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

enum class ResponseVerbalizationTone { FRIENDLY, NEUTRAL, PROFESSIONAL }
enum class ResponseVerbalizationVerbosity { SHORT, NORMAL, DETAILED }
enum class ResponseAct {
    ACKNOWLEDGE,
    REPORT_INFORMATION,
    REPORT_AND_REQUEST_INPUT,
    REPORT_RESULT,
    ASK_CONFIRMATION,
    ASK_CLARIFICATION,
    TRANSITION
}
enum class ResponseVerbalizationContract {
    AUTHORITATIVE_MESSAGE,
    TASK_CONFIRMATION,
    TASK_ACTION_RESULT,
    TASK_TRANSITION,
    CREATE_DRAFT_PRESENTATION
}
enum class ResponseMeaningDetail {
    GENERAL,
    CREATE_DRAFT_READ_TITLE,
    CREATE_DRAFT_READ_DATE,
    CREATE_DRAFT_READ_TIME,
    CREATE_DRAFT_READ_SCHEDULE,
    CREATE_DRAFT_READ_SUMMARY,
    CREATE_DRAFT_UPDATE_TITLE,
    CREATE_DRAFT_UPDATE_DATE,
    CREATE_DRAFT_UPDATE_TIME,
    CREATE_DRAFT_UPDATE_SCHEDULE,
    CREATE_DRAFT_SAVE_CONFIRMATION,
    CREATE_DRAFT_SAVE_SUCCESS,
    CREATE_DRAFT_SAVE_PARTIAL_REMINDER_FAILURE
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
    val responseAct: ResponseAct,
    val contract: ResponseVerbalizationContract,
    val requiredInput: RequiredInput,
    val allowedUserMoves: List<AllowedUserMove>,
    val continuedInteractionExpected: Boolean,
    val protectedValues: Map<String, String>,
    val requiredPlaceholders: Set<String>,
    val optionalPlaceholders: Set<String>,
    val deterministicResponse: ConversationResponse,
    val meaningDetail: ResponseMeaningDetail = ResponseMeaningDetail.GENERAL
) {
    init {
        require(requiredPlaceholders.intersect(optionalPlaceholders).isEmpty())
        require(protectedValues.keys == requiredPlaceholders + optionalPlaceholders)
        require(protectedValues.values.all { it.isNotBlank() })
        require(deterministicResponse.responseType == responseType)
        require(
            when (contract) {
                ResponseVerbalizationContract.AUTHORITATIVE_MESSAGE ->
                    requiredPlaceholders == setOf(AUTHORITATIVE_MESSAGE) &&
                        optionalPlaceholders.isEmpty()
                ResponseVerbalizationContract.TASK_CONFIRMATION ->
                    operation == ExecutionOperation.DELETE_TASK &&
                        outcome == ExecutionOutcome.NEEDS_CONFIRMATION &&
                        responseAct == ResponseAct.ASK_CONFIRMATION &&
                        requiredInput == RequiredInput.CONFIRMATION &&
                        requiredPlaceholders == setOf(TASK_TITLE) &&
                        optionalPlaceholders.isEmpty()
                ResponseVerbalizationContract.TASK_ACTION_RESULT ->
                    outcome == ExecutionOutcome.SUCCESS &&
                        responseAct == ResponseAct.REPORT_RESULT &&
                        operation in SIMPLE_RESULT_OPERATIONS &&
                        requiredPlaceholders.isEmpty() &&
                        optionalPlaceholders == setOf(TASK_TITLE)
                ResponseVerbalizationContract.TASK_TRANSITION ->
                    outcome == ExecutionOutcome.INFORMATION &&
                        responseAct == ResponseAct.TRANSITION &&
                        when (operation) {
                            ExecutionOperation.CREATE_TASK ->
                                requiredPlaceholders == setOf(TRANSITION_TARGET) &&
                                    optionalPlaceholders.isEmpty()
                            ExecutionOperation.UPDATE_TASK,
                            ExecutionOperation.RESCHEDULE_TASK ->
                                requiredPlaceholders.isEmpty() &&
                                    TASK_TITLE in optionalPlaceholders &&
                                    optionalPlaceholders.all {
                                        it == TASK_TITLE || it == DATE_TEXT || it == TIME_TEXT
                                    }
                            else -> false
                        }
                ResponseVerbalizationContract.CREATE_DRAFT_PRESENTATION ->
                    operation == ExecutionOperation.CREATE_TASK &&
                        meaningDetail != ResponseMeaningDetail.GENERAL &&
                        protectedValues.keys.all { it in CREATE_DRAFT_FACTS } &&
                        hasValidCreateDraftContract()
            }
        )
    }

    /** The factual protected values and deterministic speech are intentionally omitted. */
    fun toSafeAgentJson(): String = JSONObject().apply {
        put("operation", operation.name)
        put("outcome", outcome.name)
        put("response_type", responseType.name)
        put("response_act", responseAct.name)
        put("meaning_detail", meaningDetail.name)
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
            "available_placeholders",
            JSONArray().apply { protectedValues.keys.sorted().forEach { put(it) } }
        )
        put(
            "required_placeholders",
            JSONArray().apply { requiredPlaceholders.sorted().forEach { put(it) } }
        )
        put(
            "optional_placeholders",
            JSONArray().apply { optionalPlaceholders.sorted().forEach { put(it) } }
        )
    }.toString()

    companion object {
        const val AUTHORITATIVE_MESSAGE = "authoritative_message"
        const val TASK_TITLE = "task_title"
        const val DATE_TEXT = "date_text"
        const val TIME_TEXT = "time_text"
        const val TASK_COUNT = "task_count"
        const val TRANSITION_TARGET = "transition_target"

        private val SIMPLE_RESULT_OPERATIONS = setOf(
            ExecutionOperation.DELETE_TASK,
            ExecutionOperation.MARK_DONE,
            ExecutionOperation.MARK_UNDONE
        )

        private val CREATE_DRAFT_FACTS = setOf(TASK_TITLE, DATE_TEXT, TIME_TEXT)
    }

    private fun hasValidCreateDraftContract(): Boolean = when (meaningDetail) {
        ResponseMeaningDetail.CREATE_DRAFT_READ_TITLE ->
            requiredPlaceholders == setOf(TASK_TITLE) && optionalPlaceholders.isEmpty() &&
                hasValidCreateDraftReadAct()
        ResponseMeaningDetail.CREATE_DRAFT_READ_DATE ->
            requiredPlaceholders == setOf(DATE_TEXT) && optionalPlaceholders.isEmpty() &&
                hasValidCreateDraftReadAct()
        ResponseMeaningDetail.CREATE_DRAFT_READ_TIME ->
            requiredPlaceholders == setOf(TIME_TEXT) && optionalPlaceholders.isEmpty() &&
                hasValidCreateDraftReadAct()
        ResponseMeaningDetail.CREATE_DRAFT_READ_SCHEDULE ->
            requiredPlaceholders == setOf(DATE_TEXT, TIME_TEXT) &&
                optionalPlaceholders.isEmpty() && hasValidCreateDraftReadAct()
        ResponseMeaningDetail.CREATE_DRAFT_READ_SUMMARY ->
            requiredPlaceholders == setOf(TASK_TITLE, DATE_TEXT, TIME_TEXT) &&
                optionalPlaceholders.isEmpty() && hasValidCreateDraftReadAct()
        ResponseMeaningDetail.CREATE_DRAFT_UPDATE_TITLE,
        ResponseMeaningDetail.CREATE_DRAFT_UPDATE_DATE,
        ResponseMeaningDetail.CREATE_DRAFT_UPDATE_TIME,
        ResponseMeaningDetail.CREATE_DRAFT_UPDATE_SCHEDULE,
        ResponseMeaningDetail.CREATE_DRAFT_SAVE_CONFIRMATION ->
            outcome == ExecutionOutcome.NEEDS_CONFIRMATION &&
                responseAct == ResponseAct.ASK_CONFIRMATION &&
                responseType == ConversationResponseType.REQUEST_CONFIRMATION &&
                requiredInput == RequiredInput.CONFIRMATION &&
                continuedInteractionExpected &&
                requiredPlaceholders == CREATE_DRAFT_FACTS && optionalPlaceholders.isEmpty()
        ResponseMeaningDetail.CREATE_DRAFT_SAVE_SUCCESS ->
            outcome == ExecutionOutcome.SUCCESS && responseAct == ResponseAct.REPORT_RESULT &&
                responseType == ConversationResponseType.SUCCESS &&
                requiredInput == RequiredInput.NONE && !continuedInteractionExpected &&
                requiredPlaceholders.isEmpty() && optionalPlaceholders == CREATE_DRAFT_FACTS
        ResponseMeaningDetail.CREATE_DRAFT_SAVE_PARTIAL_REMINDER_FAILURE ->
            outcome == ExecutionOutcome.PARTIAL_SUCCESS &&
                responseAct == ResponseAct.REPORT_RESULT &&
                responseType == ConversationResponseType.PARTIAL_SUCCESS &&
                requiredInput == RequiredInput.NONE && !continuedInteractionExpected &&
                requiredPlaceholders.isEmpty() && optionalPlaceholders == CREATE_DRAFT_FACTS
        ResponseMeaningDetail.GENERAL -> false
    }

    private fun hasValidCreateDraftReadAct(): Boolean = when (responseAct) {
        ResponseAct.REPORT_INFORMATION ->
            outcome == ExecutionOutcome.INFORMATION && requiredInput == RequiredInput.NONE
        ResponseAct.REPORT_AND_REQUEST_INPUT ->
            outcome == ExecutionOutcome.NEEDS_CLARIFICATION && requiredInput != RequiredInput.NONE &&
                requiredInput != RequiredInput.CONFIRMATION && continuedInteractionExpected
        ResponseAct.ASK_CONFIRMATION ->
            outcome == ExecutionOutcome.NEEDS_CONFIRMATION &&
                requiredInput == RequiredInput.CONFIRMATION && continuedInteractionExpected
        else -> false
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
        val responseAct = deriveResponseAct(observation)
        val protectedContract = protectedSimpleContractOrNull(observation, responseAct)
        val contract = protectedContract?.contract
            ?: ResponseVerbalizationContract.AUTHORITATIVE_MESSAGE
        val protectedValues = protectedContract?.protectedValues ?: mapOf(
            ResponseVerbalizationPlan.AUTHORITATIVE_MESSAGE to deterministic.speech
        )
        val requiredPlaceholders = protectedContract?.requiredPlaceholders ?: setOf(
            ResponseVerbalizationPlan.AUTHORITATIVE_MESSAGE
        )
        val optionalPlaceholders = protectedContract?.optionalPlaceholders.orEmpty()
        return ResponseVerbalizationPlan(
            operation = observation.operation,
            outcome = observation.outcome,
            responseType = observation.outcome.toConversationResponseType(),
            tone = tone,
            verbosity = verbosity,
            responseAct = responseAct,
            contract = contract,
            requiredInput = observation.requiredInput,
            allowedUserMoves = observation.allowedUserMoves.toList(),
            continuedInteractionExpected = observation.listenAgain,
            protectedValues = protectedValues,
            requiredPlaceholders = requiredPlaceholders,
            optionalPlaceholders = optionalPlaceholders,
            deterministicResponse = deterministic
        )
    }

    fun deriveResponseAct(observation: ExecutionObservation): ResponseAct = when {
        observation.outcome == ExecutionOutcome.NEEDS_CONFIRMATION ||
            observation.requiredInput == RequiredInput.CONFIRMATION -> ResponseAct.ASK_CONFIRMATION
        observation.outcome in setOf(
            ExecutionOutcome.AMBIGUOUS,
            ExecutionOutcome.NEEDS_CLARIFICATION
        ) || observation.requiredInput != RequiredInput.NONE -> ResponseAct.ASK_CLARIFICATION
        observation.outcome == ExecutionOutcome.INFORMATION &&
            !observation.listenAgain &&
            observation.operation in TRANSITION_OPERATIONS -> ResponseAct.TRANSITION
        observation.outcome in setOf(
            ExecutionOutcome.SUCCESS,
            ExecutionOutcome.PARTIAL_SUCCESS,
            ExecutionOutcome.REJECTED,
            ExecutionOutcome.FAILURE
        ) -> ResponseAct.REPORT_RESULT
        observation.outcome == ExecutionOutcome.CANCELLED -> ResponseAct.ACKNOWLEDGE
        else -> ResponseAct.REPORT_INFORMATION
    }

    private fun protectedSimpleContractOrNull(
        observation: ExecutionObservation,
        responseAct: ResponseAct
    ): ProtectedContract? {
        if (
            observation.operation == ExecutionOperation.DELETE_TASK &&
            observation.outcome == ExecutionOutcome.NEEDS_CONFIRMATION &&
            observation.requiredInput == RequiredInput.CONFIRMATION &&
            observation.taskTitle.isNotBlank()
        ) {
            return ProtectedContract(
                contract = ResponseVerbalizationContract.TASK_CONFIRMATION,
                protectedValues = mapOf(
                    ResponseVerbalizationPlan.TASK_TITLE to observation.taskTitle
                ),
                requiredPlaceholders = setOf(ResponseVerbalizationPlan.TASK_TITLE)
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
            return ProtectedContract(
                contract = ResponseVerbalizationContract.TASK_ACTION_RESULT,
                protectedValues = mapOf(
                    ResponseVerbalizationPlan.TASK_TITLE to observation.taskTitle
                ),
                requiredPlaceholders = emptySet(),
                optionalPlaceholders = setOf(ResponseVerbalizationPlan.TASK_TITLE)
            )
        }

        if (
            observation.outcome != ExecutionOutcome.INFORMATION ||
            responseAct != ResponseAct.TRANSITION
        ) return null
        return when (observation.operation) {
            ExecutionOperation.CREATE_TASK -> ProtectedContract(
                contract = ResponseVerbalizationContract.TASK_TRANSITION,
                protectedValues = mapOf(
                    ResponseVerbalizationPlan.TRANSITION_TARGET to "task creation"
                ),
                requiredPlaceholders = setOf(ResponseVerbalizationPlan.TRANSITION_TARGET)
            )
            ExecutionOperation.UPDATE_TASK,
            ExecutionOperation.RESCHEDULE_TASK -> observation.taskTitle
                .takeIf(String::isNotBlank)
                ?.let { title ->
                    val values = buildMap {
                        put(ResponseVerbalizationPlan.TASK_TITLE, title)
                        observation.dateText.takeIf(String::isNotBlank)?.let {
                            put(ResponseVerbalizationPlan.DATE_TEXT, it)
                        }
                        observation.timeText.takeIf(String::isNotBlank)?.let {
                            put(ResponseVerbalizationPlan.TIME_TEXT, it)
                        }
                    }
                    ProtectedContract(
                        contract = ResponseVerbalizationContract.TASK_TRANSITION,
                        protectedValues = values,
                        requiredPlaceholders = emptySet(),
                        optionalPlaceholders = values.keys
                    )
                }
            else -> null
        }
    }

    private data class ProtectedContract(
        val contract: ResponseVerbalizationContract,
        val protectedValues: Map<String, String>,
        val requiredPlaceholders: Set<String>,
        val optionalPlaceholders: Set<String> = emptySet()
    )

    private val TRANSITION_OPERATIONS = setOf(
        ExecutionOperation.CREATE_TASK,
        ExecutionOperation.UPDATE_TASK,
        ExecutionOperation.RESCHEDULE_TASK
    )
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
    DUPLICATE_OPTIONAL_PLACEHOLDER,
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
    private val placeholder = Regex("""\{([a-z][a-z0-9_]*)\}""")
    private val url = Regex("""(?i)\b(?:https?://|www\.)\S+""")
    private val markdown = Regex("""(?:^|\s)(?:#{1,6}|[-+*]\s|>\s|```)|[*_~`]""")
    private val temporaryRef = Regex("""\bT\d+\b""", RegexOption.IGNORE_CASE)
    private val internalIdentifier = Regex(
        """\b(room|database|schema|json|android|agent|model|prompt|identifier|uuid|id|task[_ -]?id)\b""",
        RegexOption.IGNORE_CASE
    )
    private val unauthorizedFactLanguage = Regex(
        """\b(dates?|times?|counts?|pages?|reminders?|overdue|remaining|today|tomorrow|yesterday|weeks?|months?|years?|monday|tuesday|wednesday|thursday|friday|saturday|sunday|january|february|march|april|may|june|july|august|september|october|november|december|noon|midnight|morning|afternoon|evening|am|pm)\b""",
        RegexOption.IGNORE_CASE
    )
    private val authoritativeFactClaim = Regex(
        """\b(tasks?|titles?|items?|results?|found|exists?|available|matching|anything|nothing)\b""",
        RegexOption.IGNORE_CASE
    )
    private val controlInstruction = Regex(
        """\b(confirm|reject|cancel|choose|select|pick|say|answer|respond|reply|continue|repeat|stop|retry)\b""",
        RegexOption.IGNORE_CASE
    )
    private val operationClaim = Regex(
        """\b(created?|saved?|added?|deleted?|removed?|completed?|marked|updated?|edited?|changed?|rescheduled?|scheduled?|moved|opened?|opening|reopened?|worked)\b""",
        RegexOption.IGNORE_CASE
    )
    private val successClaim = Regex(
        """\b(success|successful|succeeded|finished|completed|accomplished|done)\b|\ball\s+set\b|\btaken\s+care\s+of\b""",
        RegexOption.IGNORE_CASE
    )
    private val completedOperationClaim = Regex(
        """\b(created|saved|added|deleted|removed|marked|updated|edited|changed|rescheduled|scheduled|moved)\b""",
        RegexOption.IGNORE_CASE
    )
    private val failureClaim = Regex(
        """\b(failed|failure|unsuccessful|unable)\b|\bcould\s+not\b|\bcouldn't\b""",
        RegexOption.IGNORE_CASE
    )
    private val deletionClaim = Regex("""\b(delet(?:e|ed)|remov(?:e|ed))\b""", RegexOption.IGNORE_CASE)
    private val completionClaim = Regex(
        """\b(complete|completed|done|finished)\b""",
        RegexOption.IGNORE_CASE
    )
    private val undoneClaim = Regex(
        """\b(incomplete|reopened|active)\b|\bnot\s+(?:complete|done)\b""",
        RegexOption.IGNORE_CASE
    )
    private val transitionClaim = Regex(
        """\b(open|opening|head|go|take|bring|pull|switch|start|begin)\b|\blet['’]s\b""",
        RegexOption.IGNORE_CASE
    )
    private val safePresentationCharacters = Regex("""^[\p{L}\s.,!?:;\-'’—–]*$""")
    private val wrapperWord = Regex("""\p{L}+(?:['’]\p{L}+)?""")

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
            ResponseVerbalizationVerbosity.SHORT -> 120
            ResponseVerbalizationVerbosity.NORMAL -> 200
            ResponseVerbalizationVerbosity.DETAILED -> 280
        }
        if (template.isBlank() || template.length > maxTemplateLength) {
            return rejected(ResponseVerbalizationValidationReason.TEMPLATE_TOO_LONG)
        }

        val placeholders = placeholder.findAll(template).map { it.groupValues[1] }.toList()
        if (placeholders.any { it !in plan.protectedValues }) {
            return rejected(ResponseVerbalizationValidationReason.UNKNOWN_PLACEHOLDER)
        }
        val presentationText = placeholder.replace(template, " ").trim()
        if (presentationText.contains('{') || presentationText.contains('}')) {
            return rejected(ResponseVerbalizationValidationReason.MALFORMED_PLACEHOLDER)
        }
        if (plan.requiredPlaceholders.any { it !in placeholders }) {
            return rejected(ResponseVerbalizationValidationReason.MISSING_REQUIRED_PLACEHOLDER)
        }
        if (plan.requiredPlaceholders.any { required -> placeholders.count { it == required } != 1 }) {
            return rejected(ResponseVerbalizationValidationReason.DUPLICATE_REQUIRED_PLACEHOLDER)
        }
        if (plan.optionalPlaceholders.any { optional -> placeholders.count { it == optional } > 1 }) {
            return rejected(ResponseVerbalizationValidationReason.DUPLICATE_OPTIONAL_PLACEHOLDER)
        }
        val wrapperWords = wrapperWord.findAll(presentationText)
            .map { it.value.lowercase(Locale.ROOT) }
            .toList()
        val maxWrapperWords = when (plan.contract) {
            ResponseVerbalizationContract.AUTHORITATIVE_MESSAGE -> when (plan.verbosity) {
                ResponseVerbalizationVerbosity.SHORT -> 10
                ResponseVerbalizationVerbosity.NORMAL -> 18
                ResponseVerbalizationVerbosity.DETAILED -> 26
            }
            else -> when (plan.verbosity) {
                ResponseVerbalizationVerbosity.SHORT -> 18
                ResponseVerbalizationVerbosity.NORMAL -> 30
                ResponseVerbalizationVerbosity.DETAILED -> 45
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
            containsLiteralProtectedValue(plan, presentationText) ||
            !safePresentationCharacters.matches(presentationText)
        ) {
            return rejected(ResponseVerbalizationValidationReason.UNSAFE_PRESENTATION_TEXT)
        }
        if (!permitsControlLanguage(plan.responseAct) && (
                '?' in presentationText || controlInstruction.containsMatchIn(presentationText)
            )
        ) {
            return rejected(ResponseVerbalizationValidationReason.UNSUPPORTED_CONTROL_INSTRUCTION)
        }
        if (
            plan.contract != ResponseVerbalizationContract.CREATE_DRAFT_PRESENTATION &&
            plan.outcome !in setOf(ExecutionOutcome.SUCCESS, ExecutionOutcome.PARTIAL_SUCCESS) &&
            (
                successClaim.containsMatchIn(presentationText) ||
                    completedOperationClaim.containsMatchIn(presentationText)
                )
        ) {
            return rejected(ResponseVerbalizationValidationReason.UNSAFE_PRESENTATION_TEXT)
        }
        if (
            plan.contract != ResponseVerbalizationContract.CREATE_DRAFT_PRESENTATION &&
            plan.outcome in setOf(ExecutionOutcome.SUCCESS, ExecutionOutcome.PARTIAL_SUCCESS) &&
            failureClaim.containsMatchIn(presentationText)
        ) {
            return rejected(ResponseVerbalizationValidationReason.UNSAFE_PRESENTATION_TEXT)
        }

        val contractIsSafe = when (plan.contract) {
            ResponseVerbalizationContract.AUTHORITATIVE_MESSAGE ->
                hasSafeAuthoritativeWrapper(plan, presentationText)
            ResponseVerbalizationContract.TASK_CONFIRMATION ->
                hasSafeDeleteConfirmation(template, presentationText)
            ResponseVerbalizationContract.TASK_ACTION_RESULT ->
                hasSafeTaskActionResult(plan.operation, template, presentationText)
            ResponseVerbalizationContract.TASK_TRANSITION ->
                hasSafeTaskTransition(plan.operation, template, presentationText)
            ResponseVerbalizationContract.CREATE_DRAFT_PRESENTATION ->
                hasSafeCreateDraftPresentation(plan, template, presentationText)
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
        presentationText: String
    ): Boolean {
        if (
            unauthorizedFactLanguage.containsMatchIn(presentationText) ||
            authoritativeFactClaim.containsMatchIn(presentationText) ||
            operationClaim.containsMatchIn(presentationText)
        ) {
            return false
        }
        if ('?' in presentationText) {
            if (plan.responseAct !in setOf(ResponseAct.ASK_CONFIRMATION, ResponseAct.ASK_CLARIFICATION)) {
                return false
            }
            val boundedQuestion = Regex(
                """\b(confirm|clarify|provide|tell|which|what|when|where|ready|right)\b""",
                RegexOption.IGNORE_CASE
            )
            if (!boundedQuestion.containsMatchIn(presentationText)) return false
        }
        return true
    }

    private fun hasSafeDeleteConfirmation(
        template: String,
        presentationText: String
    ): Boolean {
        if (
            !template.trim().endsWith('?') ||
            unauthorizedFactLanguage.containsMatchIn(presentationText) ||
            !deletionClaim.containsMatchIn(presentationText)
        ) {
            return false
        }
        val nonIdiomaticText = GO_AHEAD_IDIOM.replace(presentationText, " ")
        return !containsContradictoryOperation(
            nonIdiomaticText,
            allowedClaims = setOf(OperationClaim.DELETE)
        )
    }

    private fun hasSafeTaskActionResult(
        operation: ExecutionOperation,
        template: String,
        presentationText: String
    ): Boolean {
        if ('?' in template || unauthorizedFactLanguage.containsMatchIn(presentationText)) return false
        return when (operation) {
            ExecutionOperation.DELETE_TASK ->
                deletionClaim.containsMatchIn(presentationText) &&
                    !containsContradictoryOperation(
                        presentationText,
                        allowedClaims = setOf(OperationClaim.DELETE)
                    )
            ExecutionOperation.MARK_DONE ->
                completionClaim.containsMatchIn(presentationText) &&
                    !undoneClaim.containsMatchIn(presentationText) &&
                    !containsContradictoryOperation(
                        presentationText,
                        allowedClaims = setOf(OperationClaim.COMPLETE, OperationClaim.MARK)
                    )
            ExecutionOperation.MARK_UNDONE ->
                undoneClaim.containsMatchIn(presentationText) &&
                    !containsContradictoryOperation(
                        presentationText,
                        allowedClaims = setOf(OperationClaim.MARK, OperationClaim.UNDO)
                    )
            else -> false
        }
    }

    private fun hasSafeTaskTransition(
        operation: ExecutionOperation,
        template: String,
        presentationText: String
    ): Boolean {
        if (
            '?' in template ||
            unauthorizedFactLanguage.containsMatchIn(presentationText) ||
            successClaim.containsMatchIn(presentationText) ||
            !transitionClaim.containsMatchIn(presentationText)
        ) {
            return false
        }
        if (operation !in TRANSITION_OPERATIONS) return false
        val nonPurposeText = authorizedTransitionPurpose(operation).fold(
            presentationText
        ) { remaining, purpose ->
            purpose.replace(remaining, " ")
        }
        return !containsContradictoryOperation(
            nonPurposeText,
            allowedClaims = setOf(OperationClaim.TRANSITION)
        )
    }

    private fun hasSafeCreateDraftPresentation(
        plan: ResponseVerbalizationPlan,
        template: String,
        presentationText: String
    ): Boolean = when (plan.meaningDetail) {
        ResponseMeaningDetail.CREATE_DRAFT_READ_TITLE,
        ResponseMeaningDetail.CREATE_DRAFT_READ_DATE,
        ResponseMeaningDetail.CREATE_DRAFT_READ_TIME,
        ResponseMeaningDetail.CREATE_DRAFT_READ_SCHEDULE,
        ResponseMeaningDetail.CREATE_DRAFT_READ_SUMMARY ->
            hasSafeCreateDraftRead(plan, template, presentationText)
        ResponseMeaningDetail.CREATE_DRAFT_UPDATE_TITLE,
        ResponseMeaningDetail.CREATE_DRAFT_UPDATE_DATE,
        ResponseMeaningDetail.CREATE_DRAFT_UPDATE_TIME,
        ResponseMeaningDetail.CREATE_DRAFT_UPDATE_SCHEDULE ->
            hasSafeCreateDraftUpdate(plan.meaningDetail, template, presentationText)
        ResponseMeaningDetail.CREATE_DRAFT_SAVE_CONFIRMATION ->
            hasSafeCreateDraftSaveConfirmation(template, presentationText)
        ResponseMeaningDetail.CREATE_DRAFT_SAVE_SUCCESS,
        ResponseMeaningDetail.CREATE_DRAFT_SAVE_PARTIAL_REMINDER_FAILURE ->
            hasSafeCreateDraftSaveResult(plan.meaningDetail, template, presentationText)
        ResponseMeaningDetail.GENERAL -> false
    }

    private fun hasSafeCreateDraftRead(
        plan: ResponseVerbalizationPlan,
        template: String,
        presentationText: String
    ): Boolean {
        val questionIsSafe = when (plan.responseAct) {
            ResponseAct.REPORT_INFORMATION -> '?' !in template
            ResponseAct.REPORT_AND_REQUEST_INPUT ->
                hasExactlyOneTrailingQuestion(template) &&
                    questionMatches(plan.requiredInput, presentationText)
            ResponseAct.ASK_CONFIRMATION ->
                hasExactlyOneTrailingQuestion(template) &&
                    confirmationQuestion.containsMatchIn(presentationText)
            else -> false
        }
        if (
            !questionIsSafe ||
            successClaim.containsMatchIn(presentationText) ||
            futureCreateMutationPromise.containsMatchIn(presentationText) ||
            userMutationInstruction.containsMatchIn(presentationText)
        ) {
            return false
        }
        val factDescriptionRemoved = if (
            plan.meaningDetail == ResponseMeaningDetail.CREATE_DRAFT_READ_SCHEDULE
        ) {
            descriptiveScheduleClaim.replace(presentationText, " ")
        } else {
            presentationText
        }
        return !createDraftCompletedMutation.containsMatchIn(factDescriptionRemoved) &&
            !unrelatedCreateDraftMutation.containsMatchIn(factDescriptionRemoved)
    }

    private fun hasSafeCreateDraftUpdate(
        meaningDetail: ResponseMeaningDetail,
        template: String,
        presentationText: String
    ): Boolean {
        if (
            !hasExactlyOneTrailingQuestion(template) ||
            !confirmationQuestion.containsMatchIn(presentationText) ||
            prematureCreateSaveClaim.containsMatchIn(presentationText) ||
            futureCreateMutationPromise.containsMatchIn(presentationText) ||
            userMutationInstruction.containsMatchIn(presentationText) ||
            unrelatedCreateDraftMutation.containsMatchIn(presentationText)
        ) {
            return false
        }
        val expectedField = when (meaningDetail) {
            ResponseMeaningDetail.CREATE_DRAFT_UPDATE_TITLE -> "title"
            ResponseMeaningDetail.CREATE_DRAFT_UPDATE_DATE -> "date"
            ResponseMeaningDetail.CREATE_DRAFT_UPDATE_TIME -> "time"
            ResponseMeaningDetail.CREATE_DRAFT_UPDATE_SCHEDULE -> "schedule"
            else -> return false
        }
        val acknowledgedFields = CREATE_DRAFT_FIELD_NAMES.filterTo(mutableSetOf()) { field ->
            Regex(
                """\b(?:(?:updated|changed|set|revised|adjusted)\s+(?:the\s+)?$field|(?:the\s+)?$field\s+(?:has\s+been|is|was)\s+(?:updated|changed|set|revised|adjusted)|new\s+$field)\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(presentationText)
        }
        return acknowledgedFields == setOf(expectedField)
    }

    private fun hasSafeCreateDraftSaveConfirmation(
        template: String,
        presentationText: String
    ): Boolean = hasExactlyOneTrailingQuestion(template) &&
        confirmationQuestion.containsMatchIn(presentationText) &&
        !successClaim.containsMatchIn(presentationText) &&
        !prematureCreateSaveClaim.containsMatchIn(presentationText) &&
        !futureCreateMutationPromise.containsMatchIn(presentationText) &&
        !createDraftUpdateMutation.containsMatchIn(presentationText) &&
        !userMutationInstruction.containsMatchIn(presentationText) &&
        !unrelatedCreateDraftMutation.containsMatchIn(presentationText)

    private fun hasSafeCreateDraftSaveResult(
        meaningDetail: ResponseMeaningDetail,
        template: String,
        presentationText: String
    ): Boolean {
        if (
            '?' in template ||
            futureCreateMutationPromise.containsMatchIn(presentationText) ||
            createDraftUpdateMutation.containsMatchIn(presentationText) ||
            userMutationInstruction.containsMatchIn(presentationText) ||
            unrelatedCreateDraftMutation.containsMatchIn(presentationText) ||
            !authoritativeCreateSaveClaim.containsMatchIn(presentationText)
        ) {
            return false
        }
        return when (meaningDetail) {
            ResponseMeaningDetail.CREATE_DRAFT_SAVE_SUCCESS ->
                !failureClaim.containsMatchIn(presentationText)
            ResponseMeaningDetail.CREATE_DRAFT_SAVE_PARTIAL_REMINDER_FAILURE ->
                reminderFailureClaim.containsMatchIn(presentationText)
            else -> false
        }
    }

    private fun questionMatches(requiredInput: RequiredInput, text: String): Boolean = when (
        requiredInput
    ) {
        RequiredInput.TITLE -> Regex(
            """\b(title|name|call)\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)
        RequiredInput.EXACT_DATE -> Regex(
            """\b(date|day|when)\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)
        RequiredInput.EXACT_TIME -> Regex(
            """\b(time|when)\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)
        RequiredInput.CHANGE_FIELD -> Regex(
            """\b(change|title|date|time)\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)
        else -> false
    }

    private fun hasExactlyOneTrailingQuestion(template: String): Boolean =
        template.trim().endsWith('?') && template.count { it == '?' } == 1

    private fun authorizedTransitionPurpose(operation: ExecutionOperation): List<Regex> =
        when (operation) {
            ExecutionOperation.CREATE_TASK -> listOf(
                purpose("""so\s+you\s+can\s+(?:create|add|save)(?:\s+it)?"""),
                purpose("""to\s+(?:create|add)(?:\s+it)?""")
            )
            ExecutionOperation.UPDATE_TASK -> listOf(
                purpose(
                    """so\s+you\s+can\s+(?:make\s+(?:(?:that|the|your)\s+)?changes?|(?:change|edit|update)(?:\s+it)?)"""
                ),
                purpose("""to\s+(?:change|edit|update)(?:\s+it)?"""),
                purpose("""for\s+editing""")
            )
            ExecutionOperation.RESCHEDULE_TASK -> listOf(
                purpose(
                    """so\s+you\s+can\s+(?:reschedule(?:\s+it)?|(?:change|update)\s+(?:(?:its|the)\s+)?schedule)"""
                ),
                purpose(
                    """to\s+(?:reschedule(?:\s+it)?|(?:change|update)\s+(?:(?:its|the)\s+)?schedule)"""
                ),
                purpose("""for\s+rescheduling""")
            )
            else -> emptyList()
        }

    private fun purpose(expression: String) = Regex(
        """\b(?:$expression)\b(?!\s+(?:it\s+)?for\s+you\b)""",
        RegexOption.IGNORE_CASE
    )

    private fun permitsControlLanguage(responseAct: ResponseAct): Boolean = responseAct in setOf(
        ResponseAct.ASK_CONFIRMATION,
        ResponseAct.ASK_CLARIFICATION,
        ResponseAct.REPORT_AND_REQUEST_INPUT
    )

    private fun containsLiteralProtectedValue(
        plan: ResponseVerbalizationPlan,
        presentationText: String
    ): Boolean {
        val normalizedPresentation = presentationText.lowercase(Locale.ROOT)
        return plan.protectedValues.values.any { value ->
            val normalizedValue = value.trim().lowercase(Locale.ROOT)
            normalizedValue.length >= 2 && Regex(
                """(?<![\p{L}\p{N}])${Regex.escape(normalizedValue)}(?![\p{L}\p{N}])"""
            ).containsMatchIn(normalizedPresentation)
        }
    }

    private fun containsContradictoryOperation(
        text: String,
        allowedClaims: Set<OperationClaim>
    ): Boolean = OperationClaim.values().any { claim ->
        claim !in allowedClaims && claim.pattern.containsMatchIn(text)
    }

    private enum class OperationClaim(val pattern: Regex) {
        CREATE(Regex("""\b(create|creating|created|save|saving|saved|add|adding|added)\b""", RegexOption.IGNORE_CASE)),
        UPDATE(Regex("""\b(update|updating|updated|edit|editing|edited|change|changing|changed)\b""", RegexOption.IGNORE_CASE)),
        RESCHEDULE(Regex("""\b(reschedule|rescheduling|rescheduled|schedule|scheduling|scheduled|move|moving|moved)\b""", RegexOption.IGNORE_CASE)),
        DELETE(Regex("""\b(delete|deleting|deleted|remove|removing|removed)\b""", RegexOption.IGNORE_CASE)),
        COMPLETE(Regex("""\b(complete|completing|completed)\b""", RegexOption.IGNORE_CASE)),
        MARK(Regex("""\b(mark|marking|marked)\b""", RegexOption.IGNORE_CASE)),
        UNDO(Regex("""\b(incomplete|reopen|reopening|reopened|active)\b|\bnot\s+(?:complete|done)\b""", RegexOption.IGNORE_CASE)),
        TRANSITION(Regex("""\b(open|opening|head|go|take|bring|pull|switch|start|begin)\b""", RegexOption.IGNORE_CASE))
    }

    private val TRANSITION_OPERATIONS = setOf(
        ExecutionOperation.CREATE_TASK,
        ExecutionOperation.UPDATE_TASK,
        ExecutionOperation.RESCHEDULE_TASK
    )
    private val GO_AHEAD_IDIOM = Regex(
        """\bgo\s+ahead(?:\s+(?:and|to))?\b""",
        RegexOption.IGNORE_CASE
    )
    private val confirmationQuestion = Regex(
        """\b(save|confirm)\b""",
        RegexOption.IGNORE_CASE
    )
    private val descriptiveScheduleClaim = Regex(
        """\b(?:is|it['’]s|currently)\s+(?:currently\s+)?(?:set|scheduled)\b""",
        RegexOption.IGNORE_CASE
    )
    private val futureCreateMutationPromise = Regex(
        """(?:\b(?:i|we)(?:['’]ll|\s+will|['’]m\s+going\s+to|\s+am\s+going\s+to|\s+can)\s+(?:create|save|add|update|edit|change|reschedule|schedule|delete|remove|complete|mark)\b|\b(?:it|the\s+task|this\s+task|your\s+task)\s+will\s+be\s+(?:created|saved|added|updated|changed|scheduled|deleted|completed)\b)""",
        RegexOption.IGNORE_CASE
    )
    private val createDraftCompletedMutation = Regex(
        """\b(created|saved|added|deleted|removed|completed|marked|updated|edited|changed|rescheduled|scheduled|moved)\b""",
        RegexOption.IGNORE_CASE
    )
    private val prematureCreateSaveClaim = Regex(
        """\b(created|saved|added)\b""",
        RegexOption.IGNORE_CASE
    )
    private val authoritativeCreateSaveClaim = Regex(
        """\b(created|saved|added)\b""",
        RegexOption.IGNORE_CASE
    )
    private val unrelatedCreateDraftMutation = Regex(
        """\b(delet(?:e|ing|ed)|remov(?:e|ing|ed)|complet(?:e|ing|ed)|mark(?:ing|ed)?|reopen(?:ing|ed)?|reschedul(?:e|ing|ed)|mov(?:e|ing|ed))\b""",
        RegexOption.IGNORE_CASE
    )
    private val createDraftUpdateMutation = Regex(
        """\b(updat(?:e|ing|ed)|edit(?:ing|ed)?|chang(?:e|ing|ed)|revis(?:e|ing|ed)|adjust(?:ing|ed)?)\b""",
        RegexOption.IGNORE_CASE
    )
    private val userMutationInstruction = Regex(
        """\byou\s+(?:can|should|need\s+to|must)\s+(?:create|save|add|update|edit|change|reschedule|schedule|delete|remove|complete|mark)\b""",
        RegexOption.IGNORE_CASE
    )
    private val reminderFailureClaim = Regex(
        """(?:\b(?:could\s+not|couldn['’]t|was\s+not|unable\s+to|failed\s+to)\s+(?:set|schedule)\s+(?:the\s+)?reminder\b|\breminder\b.{0,24}\b(?:failed|could\s+not|couldn['’]t|was\s+not|unable)\b)""",
        RegexOption.IGNORE_CASE
    )
    private val CREATE_DRAFT_FIELD_NAMES = setOf("title", "date", "time", "schedule")

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
