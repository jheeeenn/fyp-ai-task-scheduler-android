package com.example.myapplication.ai

import android.content.Context
import org.json.JSONObject
import kotlin.collections.iterator
import kotlin.math.exp

@Deprecated("LocalIntentClassifier is legacy FYP1 semantic routing. FYP2 production uses Conversation Agent -> Task Agent.")
class LocalIntentClassifier(context: Context) {

    private val vocabulary: Map<String, Int>
    private val idf: DoubleArray
    private val classes: List<String>
    private val coefficients: Array<DoubleArray>
    private val intercept: DoubleArray

    init {

        val jsonText = context.assets
            .open("intent_model.json")
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

    fun classify(normalizedText: String): LocalIntentResult {
        // temproray fix before retraining regression classifier
        val text = normalizedText.trim().lowercase()

        if (
            text.contains("undo") ||
            text.contains("undone") ||
            text.contains("not done") ||
            text.contains("reopen") ||
            text.contains("unfinished")
        ) {
            return LocalIntentResult(AiIntent.MARK_UNDONE, 0.95f)
        }

        if (
            (text.contains("mark") && text.contains("done")) ||
            text.contains("complete") ||
            text.contains("completed") ||
            text.contains("finish") ||
            text.contains("finished")
        ) {
            return LocalIntentResult(AiIntent.MARK_DONE, 0.95f)
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

        return LocalIntentResult(
            mapLabelToAiIntent(classes[bestIndex]),
            probabilities[bestIndex].toFloat()
        )
    }
    private fun mapLabelToAiIntent(label: String): AiIntent {
        return when (label.trim().lowercase()) {
            "create_task" -> AiIntent.CREATE_TASK
            "delete_task" -> AiIntent.DELETE_TASK
            "query_task" -> AiIntent.QUERY_TASK
            "reschedule_task" -> AiIntent.RESCHEDULE_TASK
            "update_task" -> AiIntent.UPDATE_TASK
            "unknown" -> AiIntent.UNKNOWN

            "mark_done" -> AiIntent.MARK_DONE
            "mark_undone" -> AiIntent.MARK_UNDONE

            else -> AiIntent.UNKNOWN
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
}
