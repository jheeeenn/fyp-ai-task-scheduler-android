package com.example.myapplication.ai.conversation

object AndroidObservationResponseRenderer {
    fun render(observation: ExecutionObservation): ConversationResponse {
        val speech = if (
            observation.operation == ExecutionOperation.QUERY_TASK &&
            observation.queryPage != null
        ) {
            AccessibleTaskQuerySpeechRenderer.render(observation)
        } else {
            observation.fallbackSpeech.trim().ifBlank {
                emergencySpeech(observation.outcome)
            }
        }

        return ConversationResponse(
            speech = speech,
            hint = observation.fallbackHint,
            responseType = observation.outcome.toConversationResponseType(),
            source = "android_deterministic"
        )
    }

    private fun emergencySpeech(outcome: ExecutionOutcome): String = when (outcome) {
        ExecutionOutcome.SUCCESS -> "The task operation was completed."
        ExecutionOutcome.PARTIAL_SUCCESS -> "The task operation was partially completed."
        ExecutionOutcome.INFORMATION -> "Here is the task information."
        ExecutionOutcome.NO_RESULTS -> "No matching tasks were found."
        ExecutionOutcome.NOT_FOUND -> "I could not find a matching task."
        ExecutionOutcome.AMBIGUOUS -> "I found more than one matching task. Please choose one."
        ExecutionOutcome.NEEDS_CONFIRMATION -> "Please confirm the current task operation."
        ExecutionOutcome.NEEDS_CLARIFICATION -> "Please provide the missing task information."
        ExecutionOutcome.CANCELLED -> "The task operation was cancelled."
        ExecutionOutcome.REJECTED,
        ExecutionOutcome.FAILURE -> "The task operation could not be completed."
    }
}
