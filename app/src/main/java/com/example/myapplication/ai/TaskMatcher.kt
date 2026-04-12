package com.example.myapplication.ai

import com.example.myapplication.data.TaskEntity
import kotlin.math.max
import kotlin.math.min

data class TaskMatchResult(
    val bestTask: TaskEntity?,
    val bestScore: Double,
    val secondTask: TaskEntity? = null,
    val secondScore: Double = 0.0,
    val isAmbiguous: Boolean = false
)

object TaskMatcher {

    private val removableWords = setOf(
        "task", "tasks", "reminder", "reminders", "the", "a", "an",
        "my", "this", "that", "for", "to", "please"
    )

    private val actionPrefixes = listOf(
        "edit ", "delete ", "remove ", "mark ", "complete ", "finish ",
        "reschedule ", "change ", "update "
    )

    private val synonyms = mapOf(
        "medication" to "medicine",
        "meds" to "medicine",
        "phone" to "call",
        "ring" to "call",
        "purchase" to "buy",
        "shopping" to "buy",
        "shop" to "buy",
        "groceries" to "grocery",
        "bills" to "bill",
        "meeting" to "meet",
        "homework" to "assignment"

    )

    fun findBestTaskMatch(
        spokenTitle: String?,
        tasks: List<TaskEntity>
    ): TaskMatchResult {
        if (spokenTitle.isNullOrBlank() || tasks.isEmpty()) {
            return TaskMatchResult(
                bestTask = null,
                bestScore = 0.0
            )
        }

        val spokenNorm = normalizeForTaskMatch(spokenTitle)
        if (spokenNorm.isBlank()) {
            return TaskMatchResult(
                bestTask = null,
                bestScore = 0.0
            )
        }

        val scored = tasks.map { task ->
            val titleNorm = normalizeForTaskMatch(task.title)
            val score = scoreTaskMatch(spokenNorm, titleNorm)
            task to score
        }.sortedByDescending { it.second }

        val best = scored.getOrNull(0)
        val second = scored.getOrNull(1)

        val bestTask = best?.first
        val bestScore = best?.second ?: 0.0
        val secondTask = second?.first
        val secondScore = second?.second ?: 0.0

        val ambiguous = bestTask != null &&
                secondTask != null &&
                bestScore >= 0.40 &&
                secondScore >= 0.35 &&
                (bestScore - secondScore) <= 0.08

        // acceptance threshold
        val accepted = bestScore >= 0.40

        return TaskMatchResult(
            bestTask = if (accepted) bestTask else null,
            bestScore = bestScore,
            secondTask = if (ambiguous) secondTask else null,
            secondScore = if (ambiguous) secondScore else 0.0,
            isAmbiguous = ambiguous
        )
    }

    fun normalizeForTaskMatch(text: String): String {
        var value = text.lowercase().trim()

        actionPrefixes.forEach { prefix ->
            if (value.startsWith(prefix)) {
                value = value.removePrefix(prefix).trim()
            }
        }

        value = value
            .replace(Regex("\\ba\\.?\\s*m\\.?\\b"), "am")
            .replace(Regex("\\bp\\.?\\s*m\\.?\\b"), "pm")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val filtered = value.split(" ")
            .filter { it.isNotBlank() }
            .filterNot { it in removableWords }
            .map { singularize(it) }
            .map { synonyms[it] ?: it }


        return filtered.joinToString(" ").trim()
    }
    //
    private fun spokenCoverageScore(spoken: Set<String>, task: Set<String>): Double {
        if (spoken.isEmpty()) return 0.0
        val matched = spoken.count { it in task }
        return matched.toDouble() / spoken.size.toDouble()
    }

    private fun scoreTaskMatch(spoken: String, task: String): Double {
        if (spoken.isBlank() || task.isBlank()) return 0.0
        if (spoken == task) return 1.0

        val spokenTokens = spoken.split(" ").filter { it.isNotBlank() }.toSet()
        val taskTokens = task.split(" ").filter { it.isNotBlank() }.toSet()

        val tokenOverlap = jaccard(spokenTokens, taskTokens)
        val containsScore = when {
            task.contains(spoken) || spoken.contains(task) -> 1.0
            else -> 0.0
        }
        val editScore = normalizedEditSimilarity(spoken, task)
        val prefixScore = prefixTokenScore(spokenTokens, taskTokens)
        val coverageScore = spokenCoverageScore(spokenTokens, taskTokens)

        return (
                tokenOverlap * 0.35 +
                        containsScore * 0.20 +
                        editScore * 0.15 +
                        prefixScore * 0.15 +
                        coverageScore * 0.15
                )
    }

    // Jaccard index to measure string similarity
    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size.toDouble()
        val union = a.union(b).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }

    private fun prefixTokenScore(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0

        var matched = 0
        for (x in a) {
            if (b.any { y -> x.startsWith(y) || y.startsWith(x) }) {
                matched++
            }
        }
        return matched.toDouble() / max(a.size, b.size)
    }

    private fun normalizedEditSimilarity(a: String, b: String): Double {
        val distance = levenshtein(a, b)
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1.0
        return 1.0 - (distance.toDouble() / maxLen.toDouble())
    }

    // Levenshtein distance to measure string similarity
    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[a.length][b.length]
    }

    private fun singularize(word: String): String {
        return if (word.length > 3 && word.endsWith("s")) {
            word.dropLast(1)
        } else {
            word
        }
    }
}