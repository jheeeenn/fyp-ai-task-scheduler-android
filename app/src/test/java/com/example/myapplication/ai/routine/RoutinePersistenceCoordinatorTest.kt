package com.example.myapplication.ai.routine

import com.example.myapplication.data.RoutineEntity
import com.example.myapplication.data.RoutineOccurrenceInsertResult
import com.example.myapplication.data.RoutineStepEntity
import com.example.myapplication.data.TaskEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutinePersistenceCoordinatorTest {
    @Test
    fun newRoutinePersistsTemplateOrderedStepsAndFirstOccurrenceBeforeReminders() {
        val calls = mutableListOf<String>()
        var storedRoutine: RoutineEntity? = null
        var storedSteps: List<RoutineStepEntity> = emptyList()
        var storedTasks: List<TaskEntity> = emptyList()
        val store = fakeStore(
            onNew = { routine, steps, tasks ->
                calls += "database"
                storedRoutine = routine
                storedSteps = steps
                storedTasks = tasks
                RoutineOccurrenceInsertResult(7, listOf(11, 12, 13))
            }
        )
        val coordinator = RoutinePersistenceCoordinator(
            store,
            RoutineReminderScheduler {
                calls += "reminder"
                true
            },
            nowEpochMillis = { 1234L }
        )

        val result = runBlocking { coordinator.persist(newDraft()) }

        assertEquals(RoutineCreationResultCategory.SUCCESS, result.category)
        assertTrue(result.routineSaved)
        assertEquals("morning routine", storedRoutine!!.normalizedTitle)
        assertEquals(listOf(0, 1, 2), storedSteps.map(RoutineStepEntity::stepOrder))
        assertEquals(listOf("medicine", "breakfast", "leave"), storedTasks.map(TaskEntity::title))
        assertEquals("database", calls.first())
        assertEquals(3, calls.count { it == "reminder" })
    }

    @Test
    fun databaseFailureLeavesReportedBatchEmptyAndNeverStartsReminders() {
        var reminderCalls = 0
        val coordinator = RoutinePersistenceCoordinator(
            fakeStore(onNew = { _, _, _ -> throw IllegalStateException("rollback") }),
            RoutineReminderScheduler {
                reminderCalls += 1
                true
            }
        )

        val result = runBlocking { coordinator.persist(newDraft()) }

        assertEquals(RoutineCreationResultCategory.DATABASE_FAILURE, result.category)
        assertFalse(result.routineSaved)
        assertEquals(0, result.insertedCount)
        assertEquals(0, reminderCalls)
        assertTrue(RoutineResultSpeechRenderer.render(result).contains("Nothing was saved"))
    }

    @Test
    fun savedRoutineInvocationInsertsOccurrenceOnlyAndDoesNotDuplicateTemplate() {
        var newCalls = 0
        var occurrenceCalls = 0
        val coordinator = RoutinePersistenceCoordinator(
            fakeStore(
                onNew = { _, _, _ ->
                    newCalls += 1
                    error("unexpected")
                },
                onSaved = {
                    occurrenceCalls += 1
                    RoutineOccurrenceInsertResult(null, listOf(31, 32, 33))
                }
            ),
            RoutineReminderScheduler { true }
        )

        val result = runBlocking { coordinator.persist(savedDraft()) }

        assertEquals(0, newCalls)
        assertEquals(1, occurrenceCalls)
        assertFalse(result.routineSaved)
        assertEquals(3, result.insertedCount)
        assertTrue(RoutineResultSpeechRenderer.render(result).contains("from the saved routine"))
        assertFalse(RoutineResultSpeechRenderer.render(result).contains("saved the routine"))

        val failure = result.copy(
            category = RoutineCreationResultCategory.DATABASE_FAILURE,
            insertedCount = 0,
            reminderSuccessCount = 0
        )
        assertTrue(
            RoutineResultSpeechRenderer.render(failure)
                .contains("saved routine was unchanged")
        )
    }

    @Test
    fun partialReminderFailureAndCancellationAreReportedAccurately() {
        var reminder = 0
        val coordinator = RoutinePersistenceCoordinator(
            fakeStore(
                onSaved = {
                    RoutineOccurrenceInsertResult(null, listOf(1, 2, 3))
                }
            ),
            RoutineReminderScheduler {
                reminder += 1
                reminder != 2
            }
        )
        val result = runBlocking { coordinator.persist(savedDraft()) }

        assertEquals(RoutineCreationResultCategory.PARTIAL_REMINDER_FAILURE, result.category)
        assertEquals(2, result.reminderSuccessCount)
        assertEquals(1, result.reminderFailureCount)

        val cancelledStore = fakeStore(
            onSaved = { throw CancellationException("cancelled") }
        )
        assertThrows(CancellationException::class.java) {
            runBlocking {
                RoutinePersistenceCoordinator(
                    cancelledStore,
                    RoutineReminderScheduler { true }
                ).persist(savedDraft())
            }
        }
    }

    private fun fakeStore(
        onNew: suspend (
            RoutineEntity,
            List<RoutineStepEntity>,
            List<TaskEntity>
        ) -> RoutineOccurrenceInsertResult = { _, _, _ ->
            RoutineOccurrenceInsertResult(1, listOf(1, 2, 3))
        },
        onSaved: suspend (List<TaskEntity>) -> RoutineOccurrenceInsertResult = {
            RoutineOccurrenceInsertResult(null, listOf(1, 2, 3))
        }
    ) = object : RoutinePersistenceStore {
        override suspend fun insertNewRoutineWithFirstOccurrence(
            routine: RoutineEntity,
            steps: List<RoutineStepEntity>,
            tasks: List<TaskEntity>
        ): RoutineOccurrenceInsertResult = onNew(routine, steps, tasks)

        override suspend fun insertSavedRoutineOccurrence(
            tasks: List<TaskEntity>
        ): RoutineOccurrenceInsertResult = onSaved(tasks)
    }

    private fun newDraft() = draft(RoutineDraftOrigin.NEW_ROUTINE, null)

    private fun savedDraft() = draft(RoutineDraftOrigin.SAVED_ROUTINE, 44)

    private fun draft(origin: RoutineDraftOrigin, routineId: Long?) = PendingRoutineDraft(
        title = "Morning routine",
        revision = 1,
        origin = origin,
        savedRoutineId = routineId,
        steps = listOf(
            step("medicine", "8:00 AM"),
            step("breakfast", "8:15 AM"),
            step("leave", "9:00 AM")
        )
    )

    private fun step(title: String, time: String) = PendingRoutineStep(
        title = title,
        originalDateText = "tomorrow",
        originalTimeText = time,
        resolvedDate = "28/07/2026",
        resolvedTime = time
    )
}
