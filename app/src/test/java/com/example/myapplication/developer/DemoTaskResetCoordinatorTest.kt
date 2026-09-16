package com.example.myapplication.developer

import com.example.myapplication.data.TaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DemoTaskResetCoordinatorTest {
    @Test
    fun resetAtomicallyReplacesExistingTasksAndHandsOffEveryReminderInOrder() = runBlocking {
        val taskTable = mutableListOf(
            task(id = 41L, title = "Old root"),
            task(id = 42L, title = "Old child", parentTaskId = 41L)
        )
        val routines = mutableListOf("Morning routine", "Evening routine")
        val routineSteps = mutableListOf("Wake up", "Prepare breakfast")
        val learnedPreferences = mutableListOf("morning=08:00")
        val settings = mutableMapOf("tone" to "friendly")
        val reminderEvents = mutableListOf<String>()
        val coordinator = coordinator(
            replace = { replacements ->
                val previousIds = taskTable.map(TaskEntity::id)
                taskTable.clear()
                routineSteps.clear()
                routines.clear()
                val inserted = replacements.mapIndexed { index, task ->
                    task.copy(id = 101L + index)
                }
                taskTable += inserted
                DemoEnvironmentDatabaseResult(previousIds, inserted)
            },
            cancel = { reminderEvents += "cancel:$it" },
            schedule = {
                reminderEvents += "schedule:${it.id}"
                true
            }
        )

        val result = coordinator.reset()

        assertEquals(DemoTaskResetCategory.SUCCESS, result.category)
        assertEquals(
            listOf("Buy groceries", "Prepare presentation slides"),
            taskTable.map(TaskEntity::title)
        )
        assertEquals(listOf("17/09/2026", "17/09/2026"), taskTable.map(TaskEntity::dueDate))
        assertEquals(listOf("4:00 PM", "6:00 PM"), taskTable.map(TaskEntity::dueTime))
        assertTrue(taskTable.all { !it.isDone && it.parentTaskId == null })
        assertFalse(taskTable.any { it.title == "Doctor appointment" })
        assertFalse(taskTable.any { it.id == 41L || it.id == 42L })
        assertTrue(routines.isEmpty())
        assertTrue(routineSteps.isEmpty())
        assertEquals(listOf("morning=08:00"), learnedPreferences)
        assertEquals(mapOf("tone" to "friendly"), settings)
        assertEquals(
            listOf("cancel:41", "cancel:42", "schedule:101", "schedule:102"),
            reminderEvents
        )
    }

    @Test
    fun reminderFailureKeepsSeededTasksAndReportsPartialSuccess() = runBlocking {
        var stored = emptyList<TaskEntity>()
        var scheduleCall = 0
        val coordinator = coordinator(
            replace = { replacements ->
                stored = replacements.mapIndexed { index, task -> task.copy(id = index + 1L) }
                DemoEnvironmentDatabaseResult(listOf(7L), stored)
            },
            schedule = {
                scheduleCall += 1
                scheduleCall != 2
            }
        )

        val result = coordinator.reset()

        assertEquals(DemoTaskResetCategory.PARTIAL_REMINDER_FAILURE, result.category)
        assertEquals(1, result.newReminderScheduleFailureCount)
        assertEquals(2, stored.size)
        assertEquals(2, scheduleCall)
    }

    @Test
    fun databaseFailureDoesNotCancelOrScheduleRemindersOrClaimSuccess() = runBlocking {
        var cancellationCalls = 0
        var scheduleCalls = 0
        val coordinator = coordinator(
            replace = { throw IllegalStateException("database unavailable") },
            cancel = { cancellationCalls += 1 },
            schedule = {
                scheduleCalls += 1
                true
            }
        )

        val result = coordinator.reset()

        assertEquals(DemoTaskResetCategory.DATABASE_FAILURE, result.category)
        assertTrue(result.insertedTasks.isEmpty())
        assertEquals(0, cancellationCalls)
        assertEquals(0, scheduleCalls)
    }

    private fun coordinator(
        replace: suspend (List<TaskEntity>) -> DemoEnvironmentDatabaseResult,
        cancel: (Long) -> Unit = {},
        schedule: (TaskEntity) -> Boolean = { true }
    ) = DemoTaskResetCoordinator(
        store = DemoEnvironmentStore(replace),
        reminderCanceller = DemoTaskReminderCanceller(cancel),
        reminderScheduler = DemoTaskReminderScheduler(schedule),
        currentCalendar = ::fixedCalendar
    )

    private fun fixedCalendar(): Calendar = Calendar.getInstance(
        TimeZone.getTimeZone("Asia/Kuala_Lumpur")
    ).apply {
        set(2026, Calendar.SEPTEMBER, 16, 10, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun task(
        id: Long,
        title: String,
        parentTaskId: Long? = null
    ) = TaskEntity(
        id = id,
        title = title,
        dueDate = "16/09/2026",
        dueTime = "10:00 AM",
        parentTaskId = parentTaskId
    )
}
