package com.example.myapplication.ai.conversation.taskcontext

import java.util.Locale

/** Matches only titles already present in Android's bounded authoritative snapshot. */
object SuppliedTaskTitleMatcher {
    fun matches(
        text: String,
        snapshot: ReadOnlyTaskContextSnapshot
    ): List<ReadOnlyTaskContextItem> {
        val utterance = normalize(text)
        if (utterance.isEmpty()) return emptyList()
        val candidates = snapshot.items.filter { item ->
            val title = normalize(item.title)
            title.isNotEmpty() && containsTitle(utterance, title)
        }
        return candidates.filter { candidate ->
            val candidateTitle = normalize(candidate.title)
            candidates.none { other ->
                val otherTitle = normalize(other.title)
                otherTitle.length > candidateTitle.length &&
                    containsTitle(otherTitle, candidateTitle)
            }
        }
    }

    private fun containsTitle(text: String, title: String): Boolean =
        TITLE_BOUNDARY_TEMPLATE
            .replace("{{TITLE}}", Regex.escape(title))
            .toRegex(RegexOption.IGNORE_CASE)
            .containsMatchIn(text)

    private fun normalize(value: String): String = value
        .trim()
        .replace(WHITESPACE, " ")
        .lowercase(Locale.ROOT)

    private const val TITLE_BOUNDARY_TEMPLATE =
        "(?<![\\p{L}\\p{N}_]){{TITLE}}(?![\\p{L}\\p{N}_])"
    private val WHITESPACE = Regex("\\s+")
}
