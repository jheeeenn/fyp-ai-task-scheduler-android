package com.example.myapplication.ai.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidObservationResponseRendererTest {
    private fun observation(
        outcome: ExecutionOutcome,
        fallbackSpeech: String = "Android speech",
        fallbackHint: String = "Android hint"
    ) = ExecutionObservation(
        operation = ExecutionOperation.SYSTEM,
        outcome = outcome,
        listenAgain = true,
        fallbackSpeech = fallbackSpeech,
        fallbackHint = fallbackHint
    )

    @Test fun nonblankFallbackSpeechAndHintArePreserved() {
        val response = AndroidObservationResponseRenderer.render(
            observation(ExecutionOutcome.SUCCESS, "  Exact Android speech.  ", "Keep this hint.")
        )

        assertEquals("Exact Android speech.", response.speech)
        assertEquals("Keep this hint.", response.hint)
        assertEquals("android_deterministic", response.source)
    }

    @Test fun authoritativeOutcomesMapToResponseTypes() {
        assertEquals(ConversationResponseType.SUCCESS, renderType(ExecutionOutcome.SUCCESS))
        assertEquals(ConversationResponseType.INFORMATION, renderType(ExecutionOutcome.INFORMATION))
        assertEquals(ConversationResponseType.INFORMATION, renderType(ExecutionOutcome.NOT_FOUND))
        assertEquals(ConversationResponseType.REQUEST_CONFIRMATION, renderType(ExecutionOutcome.NEEDS_CONFIRMATION))
        assertEquals(ConversationResponseType.REQUEST_CLARIFICATION, renderType(ExecutionOutcome.NEEDS_CLARIFICATION))
        assertEquals(ConversationResponseType.ACKNOWLEDGEMENT, renderType(ExecutionOutcome.CANCELLED))
        assertEquals(ConversationResponseType.ERROR, renderType(ExecutionOutcome.FAILURE))
    }

    @Test fun blankFallbackUsesSafeSpeechWithoutInventingObservationFacts() {
        val suppliedFacts = listOf("Secret Project", "31 December", "11:45 PM", "47 tasks")
        val response = AndroidObservationResponseRenderer.render(
            ExecutionObservation(
                operation = ExecutionOperation.DELETE_TASK,
                outcome = ExecutionOutcome.FAILURE,
                taskTitle = suppliedFacts[0],
                dateText = suppliedFacts[1],
                timeText = suppliedFacts[2],
                detail = suppliedFacts[3],
                listenAgain = false,
                fallbackSpeech = "   "
            )
        )

        assertTrue(response.speech.isNotBlank())
        suppliedFacts.forEach { assertFalse(response.speech.contains(it, ignoreCase = true)) }
        assertEquals("The task operation could not be completed.", response.speech)
    }

    private fun renderType(outcome: ExecutionOutcome): ConversationResponseType =
        AndroidObservationResponseRenderer.render(observation(outcome)).responseType
}
