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

    fun isValid(envelope: SafeObservationStyleEnvelope): Boolean {
        if (
            !envelope.useStyle ||
            !envelope.confidence.isFinite() ||
            envelope.confidence < 0.85 ||
            envelope.confidence > 1.0
        ) {
            return false
        }
        return fragmentIsValid(envelope.leadIn, 12, 100) &&
            fragmentIsValid(envelope.bridge, 8, 70)
    }

    private fun fragmentIsValid(fragment: String, maxWords: Int, maxChars: Int): Boolean {
        if (fragment.length > maxChars || fragment.contains('\n') || fragment.contains('\r')) {
            return false
        }
        if (fragment.isBlank()) return true
        val wordCount = Regex("""\S+""").findAll(fragment.trim()).count()
        if (wordCount > maxWords) return false
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
