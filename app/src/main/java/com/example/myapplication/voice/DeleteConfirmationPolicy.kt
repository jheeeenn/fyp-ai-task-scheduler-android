package com.example.myapplication.voice

/** Delete-only interpretation layered over the state-independent confirmation policy. */
object DeleteConfirmationPolicy {
    fun resolve(value: String): BoundedConfirmationDecision {
        val bounded = BoundedConfirmationPolicy.resolve(value)
        if (bounded.result != BoundedConfirmationResult.UNKNOWN) return bounded
        if (bounded.normalizedText !in PRESERVATION_REJECTIONS) return bounded

        return bounded.copy(
            result = BoundedConfirmationResult.REJECT,
            source = SOURCE
        )
    }

    private val PRESERVATION_REJECTIONS = setOf(
        "i want to keep it",
        "i would like to keep it",
        "i'd like to keep it",
        "i’d like to keep it",
        "id like to keep it"
    )

    private const val SOURCE = "android_delete_confirmation_preservation"
}
