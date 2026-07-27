package com.example.myapplication.ai.routine

import com.example.myapplication.data.TaskEntity
import com.example.myapplication.diagnostics.DebugDiagnosticLog
import kotlinx.coroutines.CancellationException

enum class RoutineCreationResultCategory {
    SUCCESS,
    PARTIAL_REMINDER_FAILURE,
    DATABASE_FAILURE,
    CANCELLED
}

data class RoutineCreationResult(
    val category: RoutineCreationResultCategory,
    val taskCount: Int,
    val insertedCount: Int,
    val reminderSuccessCount: Int
) {
    val reminderFailureCount: Int get() = insertedCount - reminderSuccessCount
}

object RoutineResultSpeechRenderer {
    fun render(result: RoutineCreationResult): String = when (result.category) {
        RoutineCreationResultCategory.SUCCESS ->
            "I created all ${counted(result.insertedCount, "task")} in the routine and scheduled every reminder."
        RoutineCreationResultCategory.PARTIAL_REMINDER_FAILURE ->
            "I created all ${counted(result.insertedCount, "task")}, but " +
                "${counted(result.reminderFailureCount, "reminder")} could not be scheduled."
        RoutineCreationResultCategory.DATABASE_FAILURE ->
            "I could not create the routine tasks. Nothing was saved."
        RoutineCreationResultCategory.CANCELLED ->
            "Okay, I did not create the routine."
    }

    internal fun counted(count: Int, singular: String): String =
        "$count ${if (count == 1) singular else "${singular}s"}"
}

fun interface RoutineTaskStore {
    suspend fun insertRootTasksAtomically(tasks: List<TaskEntity>): List<Long>
}

fun interface RoutineReminderScheduler {
    fun schedule(task: TaskEntity): Boolean
}

class RoutineTaskBatchCreator(
    private val store: RoutineTaskStore,
    private val reminderScheduler: RoutineReminderScheduler
) {
    suspend fun create(
        draft: PendingRoutineDraft,
        saveGeneration: Long = 0L
    ): RoutineCreationResult {
        val tasks = draft.steps.map { step ->
            val date = requireNotNull(step.resolvedDate)
            val time = requireNotNull(step.resolvedTime)
            require(step.title.isNotBlank())
            TaskEntity(
                title = step.title,
                dueDate = date,
                dueTime = time,
                parentTaskId = null
            )
        }
        DebugDiagnosticLog.event(
            "ROUTINE_SAVE_DEBUG",
            "phase=BEGIN\nsaveGeneration=$saveGeneration\ntaskCount=${tasks.size}"
        )
        tasks.forEachIndexed { index, task ->
            DebugDiagnosticLog.event(
                "ROUTINE_SAVE_TASK",
                "index=${index + 1}\n" +
                    "title=${task.title}\n" +
                    "date=${task.dueDate}\n" +
                    "time=${task.dueTime}\n" +
                    "parentTaskId=${task.parentTaskId}"
            )
        }
        val insertedIds = try {
            store.insertRootTasksAtomically(tasks)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            val result = RoutineCreationResult(
                category = RoutineCreationResultCategory.DATABASE_FAILURE,
                taskCount = tasks.size,
                insertedCount = 0,
                reminderSuccessCount = 0
            )
            logComplete(saveGeneration, result)
            return result
        }
        DebugDiagnosticLog.event(
            "ROUTINE_SAVE_DEBUG",
            "phase=DATABASE_COMPLETE\n" +
                "saveGeneration=$saveGeneration\n" +
                "insertedCount=${insertedIds.size}\n" +
                "insertedIds=${insertedIds.joinToString(prefix = "[", postfix = "]")}"
        )
        if (insertedIds.size != tasks.size) {
            val result = RoutineCreationResult(
                category = RoutineCreationResultCategory.DATABASE_FAILURE,
                taskCount = tasks.size,
                insertedCount = insertedIds.size,
                reminderSuccessCount = 0
            )
            logComplete(saveGeneration, result)
            return result
        }

        var reminderSuccessCount = 0
        tasks.zip(insertedIds).forEachIndexed { index, (task, id) ->
            val scheduled = try {
                reminderScheduler.schedule(task.copy(id = id))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (scheduled) reminderSuccessCount += 1
            DebugDiagnosticLog.event(
                "ROUTINE_REMINDER_DEBUG",
                "index=${index + 1}\n" +
                    "taskId=$id\n" +
                    "title=${task.title}\n" +
                    "scheduled=$scheduled"
            )
        }
        val result = RoutineCreationResult(
            category = if (reminderSuccessCount == tasks.size) {
                RoutineCreationResultCategory.SUCCESS
            } else {
                RoutineCreationResultCategory.PARTIAL_REMINDER_FAILURE
            },
            taskCount = tasks.size,
            insertedCount = insertedIds.size,
            reminderSuccessCount = reminderSuccessCount
        )
        logComplete(saveGeneration, result)
        return result
    }

    private fun logComplete(
        saveGeneration: Long,
        result: RoutineCreationResult
    ) {
        DebugDiagnosticLog.event(
            "ROUTINE_SAVE_DEBUG",
            "phase=COMPLETE\n" +
                "saveGeneration=$saveGeneration\n" +
                "result=${result.category.name}\n" +
                "taskCount=${result.taskCount}\n" +
                "insertedCount=${result.insertedCount}\n" +
                "reminderSuccessCount=${result.reminderSuccessCount}"
        )
    }
}
