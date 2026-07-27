package com.example.myapplication.ai.conversation

import org.json.JSONObject
import java.util.Locale

enum class TaskQueryPageRole { COUNT_ONLY, SINGLE, FIRST, MIDDLE, LAST }

enum class TaskQueryControlCategory {
    OFFER_START,
    CONTINUE_REPEAT_STOP,
    REPEAT_OR_STOP,
    ASK_TASK_OR_DETAILS
}

data class SafeStyleTurnAuthorization(
    val requestGeneration: Long,
    val styleCallAllowed: Boolean
)

data class AssistantRequestToken(
    val requestGeneration: Long
)

object AssistantRequestTokenPolicy {
    fun isCurrent(
        token: AssistantRequestToken,
        currentRequestGeneration: Long,
        requestActive: Boolean
    ): Boolean =
        requestActive && token.requestGeneration == currentRequestGeneration
}

enum class SafeStyleAuthorizationStatus {
    ALLOWED,
    DISALLOWED,
    STALE
}

data class SafeStyleAuthorizationEvaluation(
    val styleCallAllowed: Boolean,
    val status: SafeStyleAuthorizationStatus
)

object SafeStyleAuthorizationPolicy {
    fun evaluate(
        requestToken: AssistantRequestToken?,
        authorization: SafeStyleTurnAuthorization?
    ): SafeStyleAuthorizationEvaluation = when {
        requestToken == null ||
            authorization == null ||
            requestToken.requestGeneration != authorization.requestGeneration ->
            SafeStyleAuthorizationEvaluation(
                styleCallAllowed = false,
                status = SafeStyleAuthorizationStatus.STALE
            )
        !authorization.styleCallAllowed ->
            SafeStyleAuthorizationEvaluation(
                styleCallAllowed = false,
                status = SafeStyleAuthorizationStatus.DISALLOWED
            )
        else ->
            SafeStyleAuthorizationEvaluation(
                styleCallAllowed = true,
                status = SafeStyleAuthorizationStatus.ALLOWED
            )
    }
}

enum class SafeObservationInteraction {
    NONE,
    QUERY_COUNT,
    QUERY_PAGE
}

data class SafeObservationDeliveryState(
    val requestGeneration: Long,
    val queryReadingStateGeneration: Long,
    val taskContextGeneration: Long?,
    val pageIndex: Int?,
    val interaction: SafeObservationInteraction,
    val querySessionActive: Boolean,
    val assistantRequestActive: Boolean
)

enum class SafeObservationStaleReason {
    REQUEST_CHANGED,
    QUERY_CHANGED,
    TASK_CONTEXT_CHANGED,
    QUERY_PAGE_CHANGED,
    INTERACTION_CHANGED,
    SESSION_INACTIVE
}

object SafeObservationDeliveryGuard {
    fun staleReason(
        captured: SafeObservationDeliveryState,
        current: SafeObservationDeliveryState
    ): SafeObservationStaleReason? = when {
        captured.requestGeneration != current.requestGeneration ->
            SafeObservationStaleReason.REQUEST_CHANGED
        captured.queryReadingStateGeneration != current.queryReadingStateGeneration ->
            SafeObservationStaleReason.QUERY_CHANGED
        !current.assistantRequestActive || !current.querySessionActive ->
            SafeObservationStaleReason.SESSION_INACTIVE
        captured.interaction != current.interaction ->
            SafeObservationStaleReason.INTERACTION_CHANGED
        captured.taskContextGeneration != current.taskContextGeneration ->
            SafeObservationStaleReason.TASK_CONTEXT_CHANGED
        captured.pageIndex != current.pageIndex ->
            SafeObservationStaleReason.QUERY_PAGE_CHANGED
        else -> null
    }

    fun runIfCurrent(
        captured: SafeObservationDeliveryState,
        current: SafeObservationDeliveryState,
        deliver: () -> Unit
    ): SafeObservationStaleReason? {
        val reason = staleReason(captured, current)
        if (reason == null) deliver()
        return reason
    }
}

data class TaskQueryStyleContext(
    val operation: ExecutionOperation,
    val presentation: TaskQueryPresentationLevel,
    val pageRole: TaskQueryPageRole,
    val tone: TaskQuerySpeechTone,
    val continuedInteractionExpected: Boolean,
    val controlCategory: TaskQueryControlCategory
) {
    fun toSafeJson(): String = JSONObject().apply {
        put("operation", operation.name)
        put("presentation", presentation.name)
        put("page_role", pageRole.name)
        put("tone", tone.name)
        put("continued_interaction_expected", continuedInteractionExpected)
        put("control_category", controlCategory.name)
    }.toString()

    companion object {
        fun from(
            page: TaskQueryPageObservation,
            continuedInteractionExpected: Boolean
        ): TaskQueryStyleContext {
            val pageRole = when {
                page.presentation == TaskQueryPresentationLevel.COUNT_ONLY ->
                    TaskQueryPageRole.COUNT_ONLY
                page.pageCount <= 1 -> TaskQueryPageRole.SINGLE
                page.pageNumber == 1 -> TaskQueryPageRole.FIRST
                page.hasNextPage -> TaskQueryPageRole.MIDDLE
                else -> TaskQueryPageRole.LAST
            }
            val controlCategory = when {
                page.presentation == TaskQueryPresentationLevel.COUNT_ONLY ->
                    TaskQueryControlCategory.OFFER_START
                page.hasNextPage -> TaskQueryControlCategory.CONTINUE_REPEAT_STOP
                page.pageCount > 1 ||
                    page.presentation == TaskQueryPresentationLevel.DETAILS ->
                    TaskQueryControlCategory.REPEAT_OR_STOP
                else -> TaskQueryControlCategory.ASK_TASK_OR_DETAILS
            }
            return TaskQueryStyleContext(
                operation = ExecutionOperation.QUERY_TASK,
                presentation = page.presentation,
                pageRole = pageRole,
                tone = page.tone,
                continuedInteractionExpected = continuedInteractionExpected,
                controlCategory = controlCategory
            )
        }
    }
}

data class TaskQuerySpeechPlan(
    val authoritativeCore: String,
    val authoritativeControl: String,
    val deterministicSpeech: String,
    val styleContext: TaskQueryStyleContext
)

data class SafeObservationStyleEnvelope(
    val useStyle: Boolean,
    val leadIn: String,
    val bridge: String,
    val confidence: Double
)

enum class SafeStyleValidationReason {
    ACCEPTED,
    USE_STYLE_FALSE,
    LOW_CONFIDENCE,
    CONFIDENCE_OUT_OF_RANGE,
    LEAD_IN_TOO_LONG,
    BRIDGE_TOO_LONG,
    LEAD_IN_UNSAFE,
    BRIDGE_UNSAFE,
    INVALID_FORMAT
}

data class SafeStyleValidationResult(
    val accepted: Boolean,
    val reason: SafeStyleValidationReason
)

class SafeObservationStyleParser {
    fun parse(raw: String): SafeObservationStyleEnvelope {
        val json = try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw ConversationSchemaException("Safe style response is not valid JSON", e)
        }
        val expected = setOf("use_style", "lead_in", "bridge", "confidence")
        val actual = json.keys().asSequence().toSet()
        if (actual != expected) {
            throw ConversationSchemaException("Safe style response fields do not match the contract")
        }
        if (
            json.get("use_style") !is Boolean ||
            json.get("lead_in") !is String ||
            json.get("bridge") !is String ||
            json.get("confidence") !is Number
        ) {
            throw ConversationSchemaException("Safe style response contains an invalid field type")
        }
        return SafeObservationStyleEnvelope(
            useStyle = json.getBoolean("use_style"),
            leadIn = json.getString("lead_in"),
            bridge = json.getString("bridge"),
            confidence = json.getDouble("confidence")
        )
    }
}

object SafeObservationStyleValidator {
    private val forbiddenTerms = Regex(
        """\b(created|saved|deleted|completed|incomplete|rescheduled|updated|overdue|remaining|found|continue|repeat|stop|confirm|select|proceed|again|choose|pick|first|second|third|fourth|fifth|next|last|tasks?|pages?|count|ordinal|date|period|items?|results?|lists?|entries|exists?|none|some|several|both|each|anything|nothing)\b|\b(?:go|carry)\s+on\b""",
        RegexOption.IGNORE_CASE
    )
    private val successOrMutationClaims = Regex(
        """\b(done|finished|successful|succeeded|failed|removed|changed|moved|added|handled|processed|confirmed|cancelled)\b|\ball\s+set\b""",
        RegexOption.IGNORE_CASE
    )
    private val url = Regex("""(?i)\b(?:https?://|www\.)\S+""")
    private val clockTime = Regex("""(?i)\b\d{1,2}(?::\d{2})?\s*(?:a\.?m\.?|p\.?m\.?)\b|\b\d{1,2}:\d{2}\b""")
    private val calendarDate = Regex("""\b\d{1,4}[/.-]\d{1,2}[/.-]\d{1,4}\b""")
    private val ordinalMarker = Regex("""(?i)\b\d+(?:st|nd|rd|th)\b""")
    private val numberWord = Regex(
        """\b(zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred|thousand)\b""",
        RegexOption.IGNORE_CASE
    )
    private val temporalWord = Regex(
        """\b(today|tomorrow|yesterday|morning|afternoon|evening|tonight|week|month|year|monday|tuesday|wednesday|thursday|friday|saturday|sunday|january|february|march|april|may|june|july|august|september|october|november|december)\b""",
        RegexOption.IGNORE_CASE
    )
    private val existenceClaim = Regex("""\bthere\s+(?:is|are|was|were)\b""", RegexOption.IGNORE_CASE)
    private val markdown = Regex("""(?:^|\s)(?:#{1,6}|[-+*]\s|>\s|```)|[*_~`]""")
    private val jsonStructure = Regex("""[{}\[\]]""")
    private val quotes = Regex("""["“”]""")

    fun evaluate(envelope: SafeObservationStyleEnvelope): SafeStyleValidationResult {
        if (!envelope.useStyle) {
            return rejected(SafeStyleValidationReason.USE_STYLE_FALSE)
        }
        if (!envelope.confidence.isFinite() || envelope.confidence !in 0.0..1.0) {
            return rejected(SafeStyleValidationReason.CONFIDENCE_OUT_OF_RANGE)
        }
        if (envelope.confidence < 0.85) {
            return rejected(SafeStyleValidationReason.LOW_CONFIDENCE)
        }
        if (fragmentIsTooLong(envelope.leadIn, 12, 100)) {
            return rejected(SafeStyleValidationReason.LEAD_IN_TOO_LONG)
        }
        if (fragmentIsTooLong(envelope.bridge, 8, 70)) {
            return rejected(SafeStyleValidationReason.BRIDGE_TOO_LONG)
        }
        if (!fragmentIsSafe(envelope.leadIn)) {
            return rejected(SafeStyleValidationReason.LEAD_IN_UNSAFE)
        }
        if (!fragmentIsSafe(envelope.bridge)) {
            return rejected(SafeStyleValidationReason.BRIDGE_UNSAFE)
        }
        return SafeStyleValidationResult(
            accepted = true,
            reason = SafeStyleValidationReason.ACCEPTED
        )
    }

    fun isValid(envelope: SafeObservationStyleEnvelope): Boolean =
        evaluate(envelope).accepted

    private fun fragmentIsTooLong(fragment: String, maxWords: Int, maxChars: Int): Boolean {
        val wordCount = Regex("""\S+""").findAll(fragment.trim()).count()
        return fragment.length > maxChars || wordCount > maxWords
    }

    private fun fragmentIsSafe(fragment: String): Boolean {
        if (fragment.contains('\n') || fragment.contains('\r')) return false
        if (fragment.isBlank()) return true
        return !fragment.any(Char::isDigit) &&
            !url.containsMatchIn(fragment) &&
            !clockTime.containsMatchIn(fragment) &&
            !calendarDate.containsMatchIn(fragment) &&
            !ordinalMarker.containsMatchIn(fragment) &&
            !numberWord.containsMatchIn(fragment) &&
            !temporalWord.containsMatchIn(fragment) &&
            !existenceClaim.containsMatchIn(fragment) &&
            !markdown.containsMatchIn(fragment) &&
            !jsonStructure.containsMatchIn(fragment) &&
            !quotes.containsMatchIn(fragment) &&
            !forbiddenTerms.containsMatchIn(fragment.lowercase(Locale.ROOT)) &&
            !successOrMutationClaims.containsMatchIn(fragment)
    }

    private fun rejected(reason: SafeStyleValidationReason) =
        SafeStyleValidationResult(accepted = false, reason = reason)
}

object SafeTaskQuerySpeechComposer {
    fun compose(
        plan: TaskQuerySpeechPlan,
        envelope: SafeObservationStyleEnvelope
    ): String {
        require(SafeObservationStyleValidator.isValid(envelope))
        val speech = listOf(
            envelope.leadIn.trim(),
            plan.authoritativeCore,
            envelope.bridge.trim(),
            plan.authoritativeControl
        ).filter { it.isNotBlank() }.joinToString(" ")
        require(speech.occurrencesOf(plan.authoritativeCore) == 1)
        require(speech.occurrencesOf(plan.authoritativeControl) == 1)
        require(speech.indexOf(plan.authoritativeCore) < speech.indexOf(plan.authoritativeControl))
        return speech
    }

    private fun String.occurrencesOf(value: String): Int {
        if (value.isEmpty()) return 0
        var count = 0
        var start = 0
        while (true) {
            val match = indexOf(value, start)
            if (match < 0) return count
            count += 1
            start = match + value.length
        }
    }
}
