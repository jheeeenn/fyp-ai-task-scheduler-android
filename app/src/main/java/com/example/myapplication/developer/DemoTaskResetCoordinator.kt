package com.example.myapplication.developer

import com.example.myapplication.data.TaskEntity
import com.example.myapplication.data.TaskTableReplacementResult
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

fun interface DemoTaskStore {
    suspend fun replaceAllTasks(tasks: List<TaskEntity>): TaskTableReplacementResult
}

fun interface DemoTaskReminderCanceller {
    fun cancel(taskId: Long)
}

fun interface DemoTaskReminderScheduler {
    fun schedule(task: TaskEntity): Boolean
}

class DemoTaskResetCoordinator(
    private val store: DemoTaskStore,
    private val reminderCanceller: DemoTaskReminderCanceller,
    private val reminderScheduler: DemoTaskReminderScheduler,
    private val currentCalendar: () -> Calendar = Calendar::getInstance
) {
    suspend fun reset(): DemoTaskResetResult {
        val replacement = try {
            store.replaceAllTasks(buildDemoTasks())
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
