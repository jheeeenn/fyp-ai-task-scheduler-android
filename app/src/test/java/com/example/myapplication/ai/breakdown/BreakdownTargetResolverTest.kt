package com.example.myapplication.ai.breakdown

import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakdownTargetResolverTest {
    @Test
    fun existingActiveRootIsMatchedAndSelected() {
        val root = task(10, "Final year project")

        val result = BreakdownTargetResolver.resolve(
            BreakdownTargetPreference.AUTO,
            "final year project",
            listOf(root)
        )

        assertEquals(root, (result as BreakdownTargetResolution.ExistingRoot).task)
    }

    @Test
    fun childAndCompletedTasksCannotBecomeBreakdownParents() {
        val child = task(2, "Final year project", parentId = 1)
        val completedRoot = task(3, "Final year project", done = true)

        assertEquals(
            BreakdownTargetResolution.NewRoot,
            BreakdownTargetResolver.resolve(
                BreakdownTargetPreference.AUTO,
                "final year project",
                listOf(child, completedRoot)
            )
        )
    }

    @Test
    fun ambiguousRootMatchesRequireOrdinalOrUniqueTitleSelection() {
        val report = task(1, "Final year project report")
        val presentation = task(2, "Final year project presentation")
        val result = BreakdownTargetResolver.resolve(
            BreakdownTargetPreference.AUTO,
            "final year project",
            listOf(report, presentation)
        )

        assertTrue(result is BreakdownTargetResolution.Ambiguous)
        assertEquals(
            presentation,
            BreakdownTargetResolver.selectCandidate("second", listOf(report, presentation))
        )
        assertEquals(
            report,
            BreakdownTargetResolver.selectCandidate(
                "Final year project report",
                listOf(report, presentation)
            )
        )
        assertNull(
            BreakdownTargetResolver.selectCandidate(
                "final year project",
                listOf(report, presentation)
            )
        )
    }

    @Test
    fun explicitNewRootPreferenceBypassesAnExactExistingTitleMatch() {
        val existing = task(10, "Presentation")

        assertEquals(
            BreakdownTargetResolution.NewRoot,
            BreakdownTargetResolver.resolve(
                BreakdownTargetPreference.NEW_ROOT,
                "Presentation",
                listOf(existing)
            )
        )
        assertEquals(
            existing,
            (
                BreakdownTargetResolver.resolve(
                    BreakdownTargetPreference.AUTO,
                    "Presentation",
                    listOf(existing)
                ) as BreakdownTargetResolution.ExistingRoot
                ).task
        )
    }

    private fun task(
        id: Long,
        title: String,
        parentId: Long? = null,
        done: Boolean = false
    ) = TaskEntity(
        id = id,
        title = title,
        isDone = done,
        parentTaskId = parentId
    )
}
