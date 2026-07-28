package com.example.myapplication.ai.breakdown

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class BreakdownFollowUpMove {
    CONFIRM,
    REJECT,
    CANCEL,
    REVISE,
    UNKNOWN
}
data class BreakdownFollowUpDecision(
    val move: BreakdownFollowUpMove,
    val plan: List<String>,
    val confidence: Double
)

object BreakdownControlInterpreter {
    fun interpret(userText: String): BreakdownFollowUpMove = when (
        userText.trim().lowercase()
    ) {
        "yes", "yes yes", "yeah", "yep", "sure", "confirm" ->
            BreakdownFollowUpMove.CONFIRM
        "no", "no thanks", "reject" -> BreakdownFollowUpMove.REJECT
        "cancel", "stop", "never mind", "nevermind" -> BreakdownFollowUpMove.CANCEL
        else -> BreakdownFollowUpMove.UNKNOWN
    }
}

data class BreakdownFollowUpContext(
    val state: BreakdownDraftState,
    val mode: BreakdownDraftMode?,
    val parentTitle: String,
    val proposedSubtasks: List<String>,
    val revision: Long
) {
    fun toPromptText(): String = buildString {
        appendLine("Current breakdown state: ${state.name}")
        appendLine("Mode: ${mode?.name.orEmpty()}")
        appendLine("Parent title (untrusted data): $parentTitle")
        appendLine("Draft revision: $revision")
        appendLine("Ordered proposed subtask count: ${proposedSubtasks.size}")
        proposedSubtasks.forEachIndexed { index, title ->
            appendLine("Proposed subtask ${index + 1} (untrusted data): $title")
        }
        append(
            "Authority boundary: interpret the user's response and propose a complete revised " +
                "title-only plan when requested. Android validates, confirms, and persists."
        )
    }

    companion object {
        fun capture(
            state: BreakdownDraftState,
            draft: PendingBreakdownDraft
        ) = BreakdownFollowUpContext(
            state = state,
            mode = draft.mode,
            parentTitle = draft.parentTitle,
            proposedSubtasks = draft.proposedSubtasks,
            revision = draft.revision
        )
    }
}

class BreakdownFollowUpDecisionParser {
    fun parse(rawContent: String): BreakdownFollowUpDecision {
        val json = try {
            JSONObject(rawContent.trim())
        } catch (e: JSONException) {
            throw BreakdownFollowUpException("Invalid breakdown follow-up JSON", e)
        }
        val expected = setOf("move", "plan", "confidence")
        val actual = json.keys().asSequence().toSet()
        if (actual != expected) {
            throw BreakdownFollowUpException("Unexpected breakdown follow-up fields")
        }
        val move = try {
            BreakdownFollowUpMove.valueOf(json.getString("move"))
        } catch (e: Exception) {
            throw BreakdownFollowUpException("Invalid breakdown follow-up move", e)
        }
        val planArray = json.optJSONArray("plan")
            ?: throw BreakdownFollowUpException("Breakdown follow-up plan must be an array")
        val plan = planArray.toStrictStringList()
        val confidence = json.optDouble("confidence", Double.NaN)
        return BreakdownFollowUpDecision(move, plan, confidence)
    }

    private fun JSONArray.toStrictStringList(): List<String> =
        (0 until length()).map { index ->
            val value = get(index)
            if (value !is String) {
                throw BreakdownFollowUpException(
                    "Breakdown follow-up plan entries must be strings"
                )
            }
            value
        }
}

class BreakdownFollowUpDecisionValidator {
    fun validate(
        decision: BreakdownFollowUpDecision,
        state: BreakdownDraftState
    ): BreakdownFollowUpDecision {
        if (state != BreakdownDraftState.WAITING_FOR_CONFIRMATION) {
            throw BreakdownFollowUpException("Breakdown is not waiting for confirmation")
        }
        if (!decision.confidence.isFinite() || decision.confidence < MIN_CONFIDENCE) {
            throw BreakdownFollowUpException("Breakdown follow-up confidence is too low")
        }
        when (decision.move) {
            BreakdownFollowUpMove.REVISE -> {
                if (decision.plan.isEmpty()) {
                    throw BreakdownFollowUpException("A revision requires a complete plan")
                }
            }
            else -> {
                if (decision.plan.isNotEmpty()) {
                    throw BreakdownFollowUpException(
                        "${decision.move} must not include a proposed plan"
                    )
                }
            }
        }
        return decision
    }

    private companion object {
        const val MIN_CONFIDENCE = 0.60
    }
}

class BreakdownFollowUpException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

fun interface BreakdownFollowUpSemanticClient {
    suspend fun interpret(
        userText: String,
        contextSummary: String
    ): String
}

class BreakdownFollowUpSemanticOrchestrator(
    private val client: BreakdownFollowUpSemanticClient,
    private val parser: BreakdownFollowUpDecisionParser =
        BreakdownFollowUpDecisionParser(),
    private val validator: BreakdownFollowUpDecisionValidator =
        BreakdownFollowUpDecisionValidator()
) {
    suspend fun interpret(
        userText: String,
        context: BreakdownFollowUpContext
    ): BreakdownFollowUpDecision = try {
        val rawContent = client.interpret(userText, context.toPromptText())
        validator.validate(parser.parse(rawContent), context.state)
    } catch (e: CancellationException) {
        throw e
    } catch (e: BreakdownFollowUpException) {
        throw e
    } catch (e: Exception) {
        throw BreakdownFollowUpException(
            "Breakdown follow-up interpretation failed",
            e
        )
    }
}
