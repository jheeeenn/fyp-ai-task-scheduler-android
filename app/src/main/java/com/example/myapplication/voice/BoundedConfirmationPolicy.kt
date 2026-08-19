package com.example.myapplication.voice

enum class BoundedConfirmationResult {
    AFFIRM,
    REJECT,
    CANCEL,
    UNKNOWN
}

data class BoundedConfirmationDecision(
    val result: BoundedConfirmationResult,
    val normalizedText: String,
    val source: String = SOURCE,
    val confidence: Double = 1.0
) {
    companion object {
        const val SOURCE = "android_bounded_confirmation"
    }
}

/** Small, state-independent meaning policy. Callers retain authority over pending operations. */
object BoundedConfirmationPolicy {
    fun resolve(value: String): BoundedConfirmationDecision {
        val normalized = normalize(value)
        val result = when {
            normalized in CANCELLATIONS -> BoundedConfirmationResult.CANCEL
            normalized in REJECTIONS ||
                (AFFIRMATIVE_SIGNAL.containsMatchIn(normalized) &&
                    NEGATION.containsMatchIn(normalized)) ->
                BoundedConfirmationResult.REJECT
            normalized in AFFIRMATIONS -> BoundedConfirmationResult.AFFIRM
            else -> BoundedConfirmationResult.UNKNOWN
        }
        return BoundedConfirmationDecision(result, normalized)
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(PUNCTUATION, " ")
        .replace(WHITESPACE, " ")
        .trim()

    private val AFFIRMATIONS = setOf(
        "yes", "yes yes", "yes sure", "yes please", "yes of course", "yeah", "yep",
        "sure", "okay", "ok", "alright", "absolutely", "certainly", "confirm",
        "please do", "go ahead", "do it", "okay yes", "ok yes"
    )
    private val REJECTIONS = setOf(
        "no", "no no", "nope", "don't", "dont", "do not", "keep it", "leave it",
        "don't do that", "dont do that", "do not do that", "don't delete it",
        "dont delete it", "do not delete it", "don't save it", "dont save it",
        "do not save it", "don't save", "dont save", "do not save", "don't create it",
        "dont create it", "do not create it", "not now", "not yet", "no thanks", "no need"
    )
    private val CANCELLATIONS = setOf(
        "cancel", "stop", "never mind", "nevermind"
    )
    private val NEGATION = Regex("(?:^|\\s)(?:no|nope|not|never|don't|dont|do not)(?:\\s|$)")
    private val AFFIRMATIVE_SIGNAL = Regex(
        "(?:^|\\s)(?:yes|yeah|yep|sure|okay|ok|confirm|please do|go ahead|do it)(?:\\s|$)"
    )
    private val PUNCTUATION = Regex("[.,!?;:]+")
    private val WHITESPACE = Regex("\\s+")
}
