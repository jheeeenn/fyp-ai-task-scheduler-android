package com.example.myapplication.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class BoundedConfirmationPolicyTest {
    @Test
    fun acceptsClearlyAffirmativeBoundedVariants() {
        listOf("yes", "yes yes", "yeah", "yep", "yes sure", "sure", "okay", "ok",
            "please do", "go ahead", "do it").forEach { text ->
            assertEquals(text, BoundedConfirmationResult.AFFIRM, resolve(text))
        }
    }

    @Test
    fun rejectsOrCancelsWithoutTreatingUnrelatedSpeechAsApproval() {
        listOf("no", "no no", "nope", "don't", "do not", "keep it", "don't do that")
            .forEach { text ->
                assertEquals(text, BoundedConfirmationResult.REJECT, resolve(text))
            }
        assertEquals(BoundedConfirmationResult.CANCEL, resolve("never mind"))
        assertEquals(BoundedConfirmationResult.UNKNOWN, resolve("the weather is nice"))
        assertEquals(BoundedConfirmationResult.UNKNOWN, resolve("save it"))
    }

    @Test
    fun negationWinsOverAffirmativeToken() {
        assertEquals(
            BoundedConfirmationResult.REJECT,
            resolve("yes, don't delete it")
        )
    }

    @Test
    fun naturalNegationWithBoundedPreservationMeaningIsRejected() {
        listOf(
            "no keep it",
            "no, I want to keep it",
            "no leave it",
            "no, don't delete it"
        ).forEach { text ->
            assertEquals(text, BoundedConfirmationResult.REJECT, resolve(text))
        }
    }

    @Test
    fun unrelatedSentenceContainingNoRemainsUnknown() {
        assertEquals(
            BoundedConfirmationResult.UNKNOWN,
            resolve("no tasks are due tomorrow")
        )
    }

    private fun resolve(text: String) = BoundedConfirmationPolicy.resolve(text).result
}
