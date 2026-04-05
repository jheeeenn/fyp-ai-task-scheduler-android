package com.example.myapplication.ai

import com.example.myapplication.data.LearnedTimePreferenceDao
import com.example.myapplication.data.LearnedTimePreferenceEntity

class TimePreferenceLearner(
    private val dao: LearnedTimePreferenceDao
) {

    suspend fun getLearnedTimeForPhrase(phrase: String): LearnedTimePreferenceEntity? {
        return dao.getByPhrase(normalizePhrase(phrase))
    }

    suspend fun learnPreference(phrase: String, resolvedTime: String) {
        val normalizedPhrase = normalizePhrase(phrase)
        val existing = dao.getByPhrase(normalizedPhrase)

        if (existing == null) {
            dao.insert(
                LearnedTimePreferenceEntity(
                    phrase = normalizedPhrase,
                    resolvedTime = resolvedTime,
                    usageCount = 1,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        } else {
            val updated = existing.copy(
                resolvedTime = resolvedTime,
                usageCount = existing.usageCount + 1,
                lastUpdated = System.currentTimeMillis()
            )
            dao.update(updated)
        }
    }

    fun isSemanticPhrase(text: String?): Boolean {
        if (text.isNullOrBlank()) return false

        val value = normalizePhrase(text)

        return value.contains("after dinner") ||
                value.contains("before dinner") ||
                value.contains("after lunch") ||
                value.contains("after work") ||
                value.contains("this evening") ||
                value.contains("tonight") ||
                value.contains("when i get home") ||
                value.contains("when i arrive home")
    }

    private fun normalizePhrase(text: String): String {
        return text.lowercase().trim().replace(Regex("\\s+"), " ")
    }
}