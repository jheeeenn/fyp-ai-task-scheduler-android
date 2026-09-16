package com.example.myapplication.developer

import androidx.room.withTransaction
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.TaskEntity
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class DemoTaskResetCategory {
    SUCCESS,
    PARTIAL_REMINDER_FAILURE,
    DATABASE_FAILURE
}

data class DemoTaskResetResult(
    val category: DemoTaskResetCategory,
    val insertedTasks: List<TaskEntity>,
    val oldReminderCancellationFailureCount: Int = 0,
    val newReminderScheduleFailureCount: Int = 0
)

data class DemoEnvironmentDatabaseResult(
    val previousTaskIds: List<Long>,
    val insertedTasks: List<TaskEntity>
)

fun interface DemoEnvironmentStore {
    suspend fun resetWithDemoTasks(tasks: List<TaskEntity>): DemoEnvironmentDatabaseResult
}

class RoomDemoEnvironmentStore(
    private val database: AppDatabase
) : DemoEnvironmentStore {
    override suspend fun resetWithDemoTasks(
        tasks: List<TaskEntity>
    ): DemoEnvironmentDatabaseResult = database.withTransaction {
        require(tasks.all { it.id == 0L && it.parentTaskId == null })
        val taskDao = database.taskDao()
        val routineDao = database.routineDao()
        val previousTaskIds = taskDao.getAll().map(TaskEntity::id)
        taskDao.deleteAllTasks()
        routineDao.deleteAllRoutineSteps()
        routineDao.deleteAllRoutines()
        val insertedIds = taskDao.insertAll(tasks)
        check(insertedIds.size == tasks.size)
        check(insertedIds.all { it > 0L })
        DemoEnvironmentDatabaseResult(
            previousTaskIds = previousTaskIds,
            insertedTasks = tasks.zip(insertedIds) { task, id -> task.copy(id = id) }
        )
    }
}

fun interface DemoTaskReminderCanceller {
    fun cancel(taskId: Long)
}

fun interface DemoTaskReminderScheduler {
    fun schedule(task: TaskEntity): Boolean
}

class DemoTaskResetCoordinator(
    private val store: DemoEnvironmentStore,
    private val reminderCanceller: DemoTaskReminderCanceller,
    private val reminderScheduler: DemoTaskReminderScheduler,
    private val currentCalendar: () -> Calendar = Calendar::getInstance
) {
    suspend fun reset(): DemoTaskResetResult {
        val replacement = try {
            store.resetWithDemoTasks(buildDemoTasks())
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            return DemoTaskResetResult(
                category = DemoTaskResetCategory.DATABASE_FAILURE,
                insertedTasks = emptyList()
            )
        }

        var cancellationFailures = 0
        replacement.previousTaskIds.forEach { taskId ->
            try {
                reminderCanceller.cancel(taskId)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                cancellationFailures += 1
            }
        }

        var scheduleFailures = 0
        replacement.insertedTasks.forEach { task ->
            val scheduled = try {
                reminderScheduler.schedule(task)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                false
            }
            if (!scheduled) scheduleFailures += 1
        }

        return DemoTaskResetResult(
            category = if (cancellationFailures == 0 && scheduleFailures == 0) {
                DemoTaskResetCategory.SUCCESS
            } else {
                DemoTaskResetCategory.PARTIAL_REMINDER_FAILURE
            },
            insertedTasks = replacement.insertedTasks,
            oldReminderCancellationFailureCount = cancellationFailures,
            newReminderScheduleFailureCount = scheduleFailures
        )
    }

    private fun buildDemoTasks(): List<TaskEntity> {
        val tomorrow = (currentCalendar().clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val date = SimpleDateFormat(DATE_PATTERN, Locale.UK).apply {
            timeZone = tomorrow.timeZone
        }.format(tomorrow.time)
        return listOf(
            TaskEntity(
                title = "Buy groceries",
                dueDate = date,
                dueTime = "4:00 PM",
                isDone = false,
                parentTaskId = null
            ),
            TaskEntity(
                title = "Prepare presentation slides",
                dueDate = date,
                dueTime = "6:00 PM",
                isDone = false,
                parentTaskId = null
            )
        )
    }

    private companion object {
        const val DATE_PATTERN = "dd/MM/yyyy"
    }
}
