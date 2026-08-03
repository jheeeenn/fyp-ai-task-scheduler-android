package com.example.myapplication

import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDetailNavigationCoordinatorTest {
    @Test
    fun detailsOpenOnlyAfterSuccessfulSpeechCompletion() {
        var completion: ((Boolean) -> Unit)? = null
        val opened = mutableListOf<TaskEntity>()
        val coordinator = TaskDetailNavigationCoordinator(
            speak = { _, onFinished -> completion = onFinished },
            openDetails = opened::add
        )
        val task = task(4, "Dentist")

        assertTrue(coordinator.request(task))
        assertTrue(opened.isEmpty())

        completion?.invoke(true)

        assertEquals(listOf(task), opened)
        assertFalse(coordinator.isPending())
    }

    @Test
    fun repeatedDoubleTapsSpeakAndOpenOnlyOnce() {
        var speechCount = 0
        var completion: ((Boolean) -> Unit)? = null
        var openCount = 0
        val coordinator = TaskDetailNavigationCoordinator(
            speak = { _, onFinished ->
                speechCount += 1
                completion = onFinished
            },
            openDetails = { openCount += 1 }
        )

        assertTrue(coordinator.request(task(1, "First")))
        assertFalse(coordinator.request(task(2, "Second")))
        assertEquals(1, speechCount)

        completion?.invoke(true)

        assertEquals(1, openCount)
    }

    @Test
    fun speechFailureClearsGuardWithoutOpeningAndAllowsRetry() {
        val completions = mutableListOf<(Boolean) -> Unit>()
        var openCount = 0
        val coordinator = TaskDetailNavigationCoordinator(
            speak = { _, onFinished -> completions += onFinished },
            openDetails = { openCount += 1 }
        )

        assertTrue(coordinator.request(task(1, "First")))
        completions.single().invoke(false)

        assertEquals(0, openCount)
        assertFalse(coordinator.isPending())
        assertTrue(coordinator.request(task(1, "First")))
    }

    @Test
    fun cancellationInvalidatesAStaleSpeechCallback() {
        val completions = mutableListOf<(Boolean) -> Unit>()
        val openedIds = mutableListOf<Long>()
        val coordinator = TaskDetailNavigationCoordinator(
            speak = { _, onFinished -> completions += onFinished },
            openDetails = { openedIds += it.id }
        )

        coordinator.request(task(1, "First"))
        coordinator.cancelPending()
        coordinator.request(task(2, "Second"))
        completions.first().invoke(true)
        completions.last().invoke(true)

        assertEquals(listOf(2L), openedIds)
    }

    private fun task(id: Long, title: String) = TaskEntity(
        id = id,
        title = title,
        dueDate = "03/08/2026",
        dueTime = "10:00 AM"
    )
}
