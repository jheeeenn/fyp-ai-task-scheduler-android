package com.example.myapplication.ai.conversation.suggestion

import kotlinx.coroutines.CancellationException

fun interface ContextSuggestionSemanticClient {
    suspend fun selectContextSuggestion(
        originalRequest: String,
        snapshotJson: String
    ): String
}

class ContextSuggestionSemanticOrchestrator(
    private val client: ContextSuggestionSemanticClient,
    private val parser: ContextSuggestionDecisionParser =
        ContextSuggestionDecisionParser()
) {
    suspend fun select(
        originalRequest: String,
        snapshot: ContextSuggestionSnapshot
    ): ContextSuggestionSelection {
        if (snapshot.candidates.isEmpty()) {
            return ContextSuggestionSelection(
                decision = noSuggestionDecision(),
                source = ContextSuggestionDecisionSource.ANDROID_NO_CANDIDATES,
                validationResult = ContextSuggestionValidationResult.ACCEPTED
            )
        }

        val parsed = try {
            parser.parse(
                client.selectContextSuggestion(
                    originalRequest = originalRequest,
                    snapshotJson = snapshot.toSemanticJson()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: ContextSuggestionSchemaException) {
            return fallback(
                snapshot,
                ContextSuggestionValidationResult.MALFORMED_RESPONSE
            )
        } catch (_: Exception) {
            return fallback(
                snapshot,
                ContextSuggestionValidationResult.REQUEST_FAILED
            )
        }

        val validation = ContextSuggestionDecisionValidator.validate(parsed, snapshot)
        return if (validation == ContextSuggestionValidationResult.ACCEPTED) {
            ContextSuggestionSelection(
                decision = parsed,
                source = ContextSuggestionDecisionSource.SEMANTIC_AGENT,
                validationResult = validation
            )
        } else {
            fallback(snapshot, validation)
        }
    }

    internal fun fallback(
        snapshot: ContextSuggestionSnapshot,
        reason: ContextSuggestionValidationResult
    ): ContextSuggestionSelection {
        val candidate = snapshot.candidates.firstOrNull()
        val decision = when {
            candidate == null -> noSuggestionDecision()
            candidate.unfinishedSubtaskCount > 0 -> ContextSuggestionDecision(
                suggestionType = ContextSuggestionType.CONTINUE_SUBTASK,
                primaryRef = candidate.ref,
                secondaryRef = "",
                confidence = 1.0
            )
            else -> ContextSuggestionDecision(
                suggestionType = ContextSuggestionType.FOCUS_TASK,
                primaryRef = candidate.ref,
                secondaryRef = "",
                confidence = 1.0
            )
        }
        return ContextSuggestionSelection(
            decision = decision,
            source = ContextSuggestionDecisionSource.DETERMINISTIC_FALLBACK,
            validationResult = reason
        )
    }

    private fun noSuggestionDecision() = ContextSuggestionDecision(
        suggestionType = ContextSuggestionType.NO_SUGGESTION,
        primaryRef = "",
        secondaryRef = "",
        confidence = 1.0
    )
}
