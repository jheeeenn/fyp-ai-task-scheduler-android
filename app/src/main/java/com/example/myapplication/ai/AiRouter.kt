package com.example.myapplication.ai

@Deprecated("AiRouter is legacy FYP1 routing. FYP2 production uses Conversation Agent -> Task Agent -> Android deterministic execution.")
class AiRouter(
    private val localIntentClassifier: LocalIntentClassifier,
    private val localTaskParser: LocalTaskParser,
    private val cloudExtractor: CloudNlpExtractor
) {
    private fun shouldUseCloudFallback(
        normalizedText: String,
        localResult: LocalIntentResult,
        localParsed: AiParsedCommand
    ): Boolean {
        if (localResult.intent == AiIntent.UNKNOWN) return true

        if (localResult.intent == AiIntent.CREATE_TASK) {
            val complexTemporalPhrases = listOf(
                "after dinner",
                "before dinner",
                "in the evening",
                "in the morning",
                "when i get home",
                "when i arrive",
                "later tonight",
                "after lunch",
                "before my meeting",
                "after work"
            )

            if (complexTemporalPhrases.any { normalizedText.contains(it) }) {
                return true
            }
        }

        return false
    }
    suspend fun process(normalizedText: String): AiParsedCommand {
        val localResult = localIntentClassifier.classify(normalizedText)
        val localParsed = localTaskParser.parse(normalizedText, localResult)

        val shouldUseCloud =
            localResult.confidence < 0.75f ||
                    localResult.intent == AiIntent.UNKNOWN ||
                    shouldUseCloudFallback(normalizedText, localResult, localParsed)

        return if (shouldUseCloud) {
            cloudExtractor.extract(normalizedText)
        } else {
            localParsed
        }
    }

}
