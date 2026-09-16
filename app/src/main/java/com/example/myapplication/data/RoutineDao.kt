package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

data class RoutineOccurrenceInsertResult(
    val routineId: Long?,
    val taskIds: List<Long>
)

@Dao
interface RoutineDao {
    @Transaction
    @Query("SELECT * FROM routines ORDER BY normalizedTitle ASC, id ASC")
    suspend fun listAllUnordered(): List<RoutineWithSteps>

    suspend fun listAll(): List<RoutineWithSteps> =
        listAllUnordered().map(RoutineWithSteps::withOrderedSteps)

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id LIMIT 1")
    suspend fun getByIdUnordered(id: Long): RoutineWithSteps?

    suspend fun getById(id: Long): RoutineWithSteps? =
        getByIdUnordered(id)?.withOrderedSteps()

    @Transaction
    @Query(
        "SELECT * FROM routines WHERE normalizedTitle = :normalizedTitle " +
            "ORDER BY id ASC"
    )
    suspend fun findByNormalizedTitleUnordered(
        normalizedTitle: String
    ): List<RoutineWithSteps>

    suspend fun findByNormalizedTitle(
        normalizedTitle: String
    ): List<RoutineWithSteps> =
        findByNormalizedTitleUnordered(normalizedTitle)
            .map(RoutineWithSteps::withOrderedSteps)

    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Insert
    suspend fun insertSteps(steps: List<RoutineStepEntity>): List<Long>

    @Insert
    suspend fun insertTasks(tasks: List<TaskEntity>): List<Long>

    @Transaction
    suspend fun insertRoutineWithSteps(
        routine: RoutineEntity,
        steps: List<RoutineStepEntity>
    ): Long {
        require(routine.id == 0L)
        require(routine.title.isNotBlank() && routine.normalizedTitle.isNotBlank())
        require(steps.size in 2..5)
        require(steps.map(RoutineStepEntity::stepOrder) == steps.indices.toList())
        require(steps.all { it.routineId == 0L && it.title.isNotBlank() && it.dueTime.isNotBlank() })
        val routineId = insertRoutine(routine)
        insertSteps(steps.map { it.copy(routineId = routineId) })
        return routineId
    }

    @Transaction
    suspend fun insertRoutineWithFirstOccurrence(
        routine: RoutineEntity,
        steps: List<RoutineStepEntity>,
        occurrenceTasks: List<TaskEntity>
    ): RoutineOccurrenceInsertResult {
        require(routine.id == 0L)
        require(routine.title.isNotBlank() && routine.normalizedTitle.isNotBlank())
        require(steps.size in 2..5)
        require(steps.map(RoutineStepEntity::stepOrder) == steps.indices.toList())
        require(steps.all { it.routineId == 0L && it.title.isNotBlank() && it.dueTime.isNotBlank() })
        require(occurrenceTasks.size == steps.size)
        require(occurrenceTasks.all { it.id == 0L && it.parentTaskId == null })

        val routineId = insertRoutine(routine)
        insertSteps(steps.map { it.copy(routineId = routineId) })
        val taskIds = insertTasks(occurrenceTasks)
        check(taskIds.size == occurrenceTasks.size)
        return RoutineOccurrenceInsertResult(routineId, taskIds)
    }

    @Transaction
    suspend fun insertSavedRoutineOccurrence(
        occurrenceTasks: List<TaskEntity>
    ): RoutineOccurrenceInsertResult {
        require(occurrenceTasks.size in 2..5)
        require(occurrenceTasks.all { it.id == 0L && it.parentTaskId == null })
        val taskIds = insertTasks(occurrenceTasks)
        check(taskIds.size == occurrenceTasks.size)
        return RoutineOccurrenceInsertResult(null, taskIds)
    }

    @Query("DELETE FROM routines WHERE id = :routineId")
    suspend fun deleteRoutineRow(routineId: Long): Int

    @Query("DELETE FROM routine_steps")
    suspend fun deleteAllRoutineSteps(): Int

    @Query("DELETE FROM routines")
    suspend fun deleteAllRoutines(): Int

    @Transaction
    suspend fun deleteRoutineAndSteps(routineId: Long): Boolean =
        deleteRoutineRow(routineId) == 1
}
