package com.example.myapplication.ai.routine

import com.example.myapplication.data.RoutineEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineMatcherTest {
    @Test
    fun exactAndNormalizedTitlesMatchDeterministically() {
        val routines = listOf(routine(1, "Morning Routine"))

        val exact = RoutineMatcher.match("Morning Routine", routines)
        val normalized = RoutineMatcher.match("morning, routine!", routines)

        assertEquals(1L, (exact as RoutineMatchResult.One).routine.id)
        assertEquals(1L, (normalized as RoutineMatchResult.One).routine.id)
    }

    @Test
    fun noMatchDoesNotDefaultToFirstRoutine() {
        val result = RoutineMatcher.match(
            "exercise routine",
            listOf(routine(1, "Morning routine"), routine(2, "Study routine"))
        )

        assertTrue(result is RoutineMatchResult.NoMatch)
    }

    @Test
    fun duplicateTitlesRemainAmbiguous() {
        val result = RoutineMatcher.match(
            "morning routine",
            listOf(routine(2, "Morning routine"), routine(1, "Morning routine"))
        )

        assertEquals(
            listOf(1L, 2L),
            (result as RoutineMatchResult.Ambiguous).routines.map(RoutineEntity::id)
        )
    }

    private fun routine(id: Long, title: String) = RoutineEntity(
        id = id,
        title = title,
        normalizedTitle = RoutineTitleNormalizer.normalize(title),
        createdAtEpochMillis = id,
        updatedAtEpochMillis = id
    )
}
