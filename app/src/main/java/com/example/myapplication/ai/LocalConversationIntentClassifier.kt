package com.example.myapplication.ai

import android.content.Context
import org.json.JSONObject
import kotlin.math.exp

class LocalConversationIntentClassifier(context: Context) {

    private val vocabulary: Map<String, Int>
    private val idf: DoubleArray
    private val classes: List<String>
    private val coefficients: Array<DoubleArray>
    private val intercept: DoubleArray

    init {
        val jsonText = context.assets
            .open("conversation_intent_model.json")
            .bufferedReader()
            .use { it.readText() }

        val json = JSONObject(jsonText)

        val vocabJson = json.getJSONObject("vocabulary")
        val vocabMap = mutableMapOf<String, Int>()
        for (key in vocabJson.keys()) {
            vocabMap[key] = vocabJson.getInt(key)
        }
        vocabulary = vocabMap

        val idfArray = json.getJSONArray("idf")
        idf = DoubleArray(idfArray.length()) { i ->
            idfArray.getDouble(i)
        }

        val classArray = json.getJSONArray("classes")
        classes = List(classArray.length()) {
            classArray.getString(it)
        }

        val coefArray = json.getJSONArray("coefficients")
        coefficients = Array(coefArray.length()) { i ->
            val row = coefArray.getJSONArray(i)
            DoubleArray(row.length()) { j ->
                row.getDouble(j)
            }
        }

        val interceptArray = json.getJSONArray("intercept")
        intercept = DoubleArray(interceptArray.length()) {
            interceptArray.getDouble(it)
        }
    }

    fun classify(normalizedText: String): ConversationIntentResult {
        if (shouldDeferToSemanticRouting(normalizedText)) {
            return ConversationIntentResult(
                intent = ConversationIntent.UNKNOWN,
                confidence = 1.0f
            )
        }
        val vector = buildTfidfVector(normalizedText)
        val scores = DoubleArray(classes.size)

        for (c in classes.indices) {
            var score = intercept[c]
            for (i in vector.indices) {
                score += vector[i] * coefficients[c][i]
            }
            scores[c] = score
        }

        val probabilities = softmax(scores)
        val bestIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0

        return ConversationIntentResult(
            intent = mapLabelToIntent(classes[bestIndex]),
            confidence = probabilities[bestIndex].toFloat()
        )
    }

    private fun mapLabelToIntent(label: String): ConversationIntent {
        return when (label.trim().lowercase()) {
            "confirm_yes" -> ConversationIntent.CONFIRM_YES
            "confirm_no" -> ConversationIntent.CONFIRM_NO
            "read_all" -> ConversationIntent.READ_ALL
            "create_one" -> ConversationIntent.CREATE_ONE
            "stop_conversation" -> ConversationIntent.STOP_CONVERSATION
            else -> ConversationIntent.UNKNOWN
        }
    }

    private fun buildTfidfVector(text: String): DoubleArray {
        val tokens = text.split(" ")
        val vector = DoubleArray(vocabulary.size)
        val tf = mutableMapOf<Int, Int>()

        for (token in tokens) {
            val index = vocabulary[token] ?: continue
            tf[index] = (tf[index] ?: 0) + 1
        }

        for ((index, count) in tf) {
            vector[index] = count * idf[index]
        }

        return vector
    }

    private fun softmax(scores: DoubleArray): DoubleArray {
        val max = scores.maxOrNull() ?: 0.0
        val expScores = scores.map { exp(it - max) }
        val sum = expScores.sum()
        return expScores.map { it / sum }.toDoubleArray()
    }

    companion object {
        /** Combined schedule questions need the bounded context-detail contract, not READ_ALL. */
        internal fun shouldDeferToSemanticRouting(normalizedText: String): Boolean =
            COMBINED_DATE_TIME_QUESTION.containsMatchIn(normalizedText)

        private val COMBINED_DATE_TIME_QUESTION = Regex(
            "(?i)\\b(?:date|day|when)\\b[^.!?]*\\btime\\b|" +
                "\\btime\\b[^.!?]*\\b(?:date|day)\\b|" +
                "\\bwhen\\s+(?:is|was)\\s+(?:it|this\\s+task|the\\s+task|task)\\b"
        )
    }
}
