package com.example.myapplication.ai.routine

import com.example.myapplication.data.RoutineEntity
import com.example.myapplication.data.RoutineOccurrenceInsertResult
import com.example.myapplication.data.RoutineStepEntity
import com.example.myapplication.data.TaskEntity
import com.example.myapplication.diagnostics.DebugDiagnosticLog
import kotlinx.coroutines.CancellationException

interface RoutinePersistenceStore {
    suspend fun insertNewRoutineWithFirstOccurrence(
        routine: RoutineEntity,
        steps: List<RoutineStepEntity>,
        tasks: List<TaskEntity>
    ): RoutineOccurrenceInsertResult

    suspend fun insertSavedRoutineOccurrence(
        tasks: List<TaskEntity>
    ): RoutineOccurrenceInsertResult
}

class RoutinePersistenceCoordinator(
    private val store: RoutinePersistenceStore,
    private val reminderScheduler: RoutineReminderScheduler,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun persist(
        draft: PendingRoutineDraft,
        saveGeneration: Long = 0L
    ): RoutineCreationResult {
        val tasks = draft.steps.map { step ->
            TaskEntity(
                title = step.title.also { require(it.isNotBlank()) },
                dueDate = requireNotNull(step.resolvedDate),
                dueTime = requireNotNull(step.resolvedTime),
                parentTaskId = null
            )
        }
        require(tasks.size in 2..5)
        DebugDiagnosticLog.event(
            "ROUTINE_TRANSACTION",
            "phase=BEGIN\norigin=${draft.origin.name}\n" +
                "saveGeneration=$saveGeneration\ngeneratedTaskCount=${tasks.size}"
        )

        val inserted = try {
            when (draft.origin) {
                RoutineDraftOrigin.NEW_ROUTINE -> {
                    val timestamp = nowEpochMillis()
                    val routine = RoutineEntity(
                        title = draft.title.trim(),
                        normalizedTitle = RoutineTitleNormalizer.normalize(draft.title),
                        createdAtEpochMillis = timestamp,
                        updatedAtEpochMillis = timestamp
                    )
                    require(routine.title.isNotEmpty() && routine.normalizedTitle.isNotEmpty())
                    val steps = draft.steps.mapIndexed { index, step ->
                        RoutineStepEntity(
                            routineId = 0L,
                            title = step.title.trim(),
                            dueTime = requireNotNull(step.resolvedTime),
                            stepOrder = index
                        )
                    }
                    store.insertNewRoutineWithFirstOccurrence(routine, steps, tasks)
                }
                RoutineDraftOrigin.SAVED_ROUTINE -> {
                    require(draft.savedRoutineId != null)
                    store.insertSavedRoutineOccurrence(tasks)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            DebugDiagnosticLog.event(
                "ROUTINE_TRANSACTION",
                "phase=FAILURE\norigin=${draft.origin.name}\n" +
                    "saveGeneration=$saveGeneration\ngeneratedTaskCount=${tasks.size}"
            )
            return RoutineCreationResult(
                category = RoutineCreationResultCategory.DATABASE_FAILURE,
                taskCount = tasks.size,
                insertedCount = 0,
                reminderSuccessCount = 0,
                origin = draft.origin,
                routineSaved = false
            )
        }

        if (inserted.taskIds.size != tasks.size ||
            (draft.origin == RoutineDraftOrigin.NEW_ROUTINE && inserted.routineId == null)
        ) {
            DebugDiagnosticLog.event(
                "ROUTINE_TRANSACTION",
                "phase=FAILURE\norigin=${draft.origin.name}\nreason=INCOMPLETE_RESULT"
            )
            return RoutineCreationResult(
                category = RoutineCreationResultCategory.DATABASE_FAILURE,
                taskCount = tasks.size,
                insertedCount = 0,
                reminderSuccessCount = 0,
                origin = draft.origin,
                routineSaved = false
            )
        }

        DebugDiagnosticLog.event(
            "ROUTINE_TRANSACTION",
            "phase=COMPLETE\norigin=${draft.origin.name}\n" +
                "generatedTaskCount=${tasks.size}"
        )
        var reminderSuccessCount = 0
        tasks.zip(inserted.taskIds).forEach { (task, id) ->
            val scheduled = try {
                reminderScheduler.schedule(task.copy(id = id))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (scheduled) reminderSuccessCount += 1
        }
        DebugDiagnosticLog.event(
            "ROUTINE_REMINDER_RESULT",
            "generatedTaskCount=${tasks.size}\nreminderCount=$reminderSuccessCount"
        )
        return RoutineCreationResult(
            category = if (reminderSuccessCount == tasks.size) {
                RoutineCreationResultCategory.SUCCESS
            } else {
                RoutineCreationResultCategory.PARTIAL_REMINDER_FAILURE
            },
            taskCount = tasks.size,
            insertedCount = tasks.size,
            reminderSuccessCount = reminderSuccessCount,
            origin = draft.origin,
            routineSaved = draft.origin == RoutineDraftOrigin.NEW_ROUTINE
        )
    }
}
