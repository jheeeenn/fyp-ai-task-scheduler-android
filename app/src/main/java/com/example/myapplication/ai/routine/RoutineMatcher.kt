package com.example.myapplication.ai.routine

import com.example.myapplication.data.RoutineEntity

sealed class RoutineMatchResult {
    data object NoMatch : RoutineMatchResult()
    data class One(val routine: RoutineEntity) : RoutineMatchResult()
    data class Ambiguous(val routines: List<RoutineEntity>) : RoutineMatchResult()
}

object RoutineMatcher {
    fun match(spokenTitle: String, routines: List<RoutineEntity>): RoutineMatchResult {
        val query = RoutineTitleNormalizer.normalize(spokenTitle)
        if (query.isEmpty()) return RoutineMatchResult.NoMatch

        val exact = routines.filter {
            RoutineTitleNormalizer.normalize(it.title) == query ||
                RoutineTitleNormalizer.normalize(it.normalizedTitle) == query
        }
        val candidates = if (exact.isNotEmpty()) {
            exact
        } else {
            routines.filter {
                val stored = RoutineTitleNormalizer.normalize(it.title)
                stored.containsWholePhrase(query) || query.containsWholePhrase(stored)
            }
        }.sortedWith(compareBy(RoutineEntity::normalizedTitle, RoutineEntity::id))

        return when (candidates.size) {
            0 -> RoutineMatchResult.NoMatch
            1 -> RoutineMatchResult.One(candidates.single())
            else -> RoutineMatchResult.Ambiguous(candidates)
        }
    }

    private fun String.containsWholePhrase(other: String): Boolean =
        this == other || this.startsWith("$other ") || this.endsWith(" $other") ||
            this.contains(" $other ")
}
