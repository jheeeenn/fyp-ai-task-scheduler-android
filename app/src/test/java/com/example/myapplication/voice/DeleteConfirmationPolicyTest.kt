package com.example.myapplication.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteConfirmationPolicyTest {
    @Test
    fun clearBarePreservationPhrasesRejectPendingDeletion() {
        listOf(
            "I want to keep it",
            "I would like to keep it",
            "I'd like to keep it"
        ).forEach { text ->
            assertEquals(text, BoundedConfirmationResult.REJECT, resolve(text))
        }
    }

    @Test
    fun existingNegatedPreservationStillRejectsPendingDeletion() {
        assertEquals(
            BoundedConfirmationResult.REJECT,
            resolve("no, I want to keep it")
        )
    }

    @Test
    fun unrelatedSpeechRemainsUnknown() {
        assertEquals(
            BoundedConfirmationResult.UNKNOWN,
            resolve("I want to keep shopping")
        )
    }

    @Test
    fun stateIndependentPolicyDoesNotGainDeleteSpecificMeaning() {
        assertEquals(
            BoundedConfirmationResult.UNKNOWN,
            BoundedConfirmationPolicy.resolve("I want to keep it").result
        )
    }

    private fun resolve(text: String) = DeleteConfirmationPolicy.resolve(text).result
}
