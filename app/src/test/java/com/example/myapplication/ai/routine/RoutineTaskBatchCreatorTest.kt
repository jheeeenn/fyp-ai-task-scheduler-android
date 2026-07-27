package com.example.myapplication.ai.routine

import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineTaskBatchCreatorTest {
    @Test
    fun atomicInsertionReceivesRootTasksInRequestedOrderAndSchedulesEveryReminder() {
        var stored: List<TaskEntity> = emptyList()
        val scheduled = mutableListOf<TaskEntity>()
        val creator = RoutineTaskBatchCreator(
            store = RoutineTaskStore { tasks ->
                stored = tasks
                listOf(11L, 12L, 13L)
            },
            reminderScheduler = RoutineReminderScheduler { task ->
                scheduled += task
                true
            }
        )

        val result = kotlinx.coroutines.runBlocking { creator.create(draft()) }

        assertEquals(RoutineCreationResultCategory.SUCCESS, result.category)
        assertEquals(listOf("medicine", "breakfast", "leave"), stored.map(TaskEntity::title))
        assertTrue(stored.all { it.parentTaskId == null })
        assertEquals(listOf(11L, 12L, 13L), scheduled.map(TaskEntity::id))
        assertEquals(3, result.reminderSuccessCount)
    }

    @Test
    fun databaseFailureCreatesNoTasksAndSchedulesNoReminders() {
        var reminderCalls = 0
        val creator = RoutineTaskBatchCreator(
            store = RoutineTaskStore { throw IllegalStateException("database failure") },
            reminderScheduler = RoutineReminderScheduler {
                reminderCalls += 1
                true
            }
        )

        val result = kotlinx.coroutines.runBlocking { creator.create(draft()) }

        assertEquals(RoutineCreationResultCategory.DATABASE_FAILURE, result.category)
        assertEquals(0, result.insertedCount)
        assertEquals(0, reminderCalls)
    }

    @Test
    fun reminderPartialFailureReportsExactCountsWithoutDeletingInsertedTasks() {
        var call = 0
        val creator = RoutineTaskBatchCreator(
            store = RoutineTaskStore { listOf(1L, 2L, 3L) },
            reminderScheduler = RoutineReminderScheduler {
                call += 1
                call != 2
            }
        )

        val result = kotlinx.coroutines.runBlocking { creator.create(draft()) }

        assertEquals(RoutineCreationResultCategory.PARTIAL_REMINDER_FAILURE, result.category)
        assertEquals(3, result.insertedCount)
        assertEquals(2, result.reminderSuccessCount)
        assertEquals(1, result.reminderFailureCount)
        assertEquals(3, call)
    }

    @Test
    fun authoritativeResultSpeechUsesCorrectSingularAndPluralGrammar() {
        val oneFailure = RoutineCreationResult(
            RoutineCreationResultCategory.PARTIAL_REMINDER_FAILURE,
            taskCount = 2,
            insertedCount = 2,
            reminderSuccessCount = 1
        )
        val twoFailures = oneFailure.copy(
            taskCount = 3,
            insertedCount = 3,
            reminderSuccessCount = 1
        )

        assertTrue(RoutineResultSpeechRenderer.render(oneFailure).contains("1 reminder "))
        assertTrue(RoutineResultSpeechRenderer.render(twoFailures).contains("2 reminders "))
        assertEquals("1 task", RoutineResultSpeechRenderer.counted(1, "task"))
        assertEquals("2 tasks", RoutineResultSpeechRenderer.counted(2, "task"))
    }

    private fun draft() = PendingRoutineDraft(
        title = "Morning routine",
        revision = 1,
        steps = listOf(
            pending("medicine", "8:00 AM"),
            pending("breakfast", "8:15 AM"),
            pending("leave", "9:00 AM")
        )
    )

    private fun pending(title: String, time: String) = PendingRoutineStep(
        title = title,
        originalDateText = "tomorrow",
        originalTimeText = time,
        resolvedDate = "28/07/2026",
        resolvedTime = time
    )
}
