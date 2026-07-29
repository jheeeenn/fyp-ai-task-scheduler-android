package com.example.myapplication.reminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReminderEscalationSourceContractTest {
    private val mainSourceRoot = File("src/main/java/com/example/myapplication")

    @Test
    fun helperUsesLongFingerprintAndDistinctStageIdentityWithoutAlarmTitle() {
        val helper = source("ReminderHelper.kt")

        assertTrue(helper.contains("fun cancelReminder(context: Context, taskId: Long)"))
        assertTrue(helper.contains("ReminderSequenceCoordinator.scheduleCompleteSequence"))
        assertTrue(helper.contains("ReminderSequenceCoordinator.cancelAllStages"))
        assertTrue(helper.contains("ReminderAlarmIdentity.forTaskStage"))
        assertTrue(helper.contains("action = identity.action"))
        assertTrue(helper.contains("data = Uri.parse(identity.dataUri)"))
        assertTrue(helper.contains("EXTRA_EXPECTED_DUE_EPOCH"))
        assertFalse(helper.contains("task.id.toInt()"))
        assertFalse(helper.contains("\"task_title\""))
    }

    @Test
    fun createTaskUsesOnlyCentralReminderScheduler() {
        val create = source("CreateTaskActivity.kt")
        val save = create
            .substringAfter("private fun saveTask")
            .substringBefore("private fun formatDateForSpeech")

        assertTrue(save.contains("val insertedTask = taskToInsert.copy(id = insertedId)"))
        assertTrue(save.contains("ReminderHelper.scheduleReminderFromTask"))
        assertFalse(create.contains("AlarmManager"))
        assertFalse(create.contains("PendingIntent"))
        assertFalse(create.contains("private fun scheduleReminder("))
    }

    @Test
    fun completionDeletionRescheduleAndReopenUseCentralApis() {
        val main = source("MainActivity.kt")
        val today = source("TodayTasksActivity.kt")
        val edit = source("EditTaskActivity.kt")
        val home = source("HomeActivity.kt")
        val combined = listOf(main, today, edit, home).joinToString("\n")

        assertTrue(main.contains("ReminderHelper.cancelReminder(this@MainActivity, task.id)"))
        assertTrue(today.contains("ReminderHelper.cancelReminder(this@TodayTasksActivity, task.id)"))
        assertTrue(edit.contains("ReminderHelper.cancelReminder(this@EditTaskActivity, taskId)"))
        assertTrue(edit.contains("ReminderHelper.scheduleReminderFromTask"))
        assertTrue(edit.contains("existingTask.copy("))
        assertFalse(edit.contains("isDone = false,\n                parentTaskId"))
        assertTrue(home.contains("ReminderHelper.cancelReminder(this@HomeActivity"))
        assertTrue(home.contains("ReminderHelper.scheduleReminderFromTask"))
        assertFalse(combined.contains("cancelReminder(this@MainActivity, task.id.toInt())"))
        assertFalse(combined.contains("cancelReminder(this@TodayTasksActivity, task.id.toInt())"))
        assertFalse(combined.contains("cancelReminder(this@HomeActivity, taskId.toInt())"))
    }

    @Test
    fun routineAndBreakdownPersistenceStillUseCentralScheduler() {
        val home = source("HomeActivity.kt")
        val routine = home
            .substringAfter("private fun savePendingRoutine")
            .substringBefore("private fun speakRoutineResponse")
        val breakdown = home
            .substringAfter("private fun savePendingBreakdown")
            .substringBefore("private fun handleBreakdownDraftFailure")

        assertTrue(routine.contains("RoutinePersistenceCoordinator"))
        assertTrue(routine.contains("ReminderHelper.scheduleReminderFromTask"))
        assertTrue(breakdown.contains("BreakdownPersistenceCoordinator"))
        assertTrue(breakdown.contains("ReminderHelper.scheduleReminderFromTask"))
    }

    @Test
    fun receiverRevalidatesRoomAsynchronouslyAndAlwaysFinishes() {
        val receiver = source("ReminderReceiver.kt")

        assertTrue(receiver.contains("val pendingResult = goAsync()"))
        assertTrue(receiver.contains("Dispatchers.IO"))
        assertTrue(receiver.contains("AppDatabase.getInstance(context)"))
        assertTrue(receiver.contains(".getById(payload.taskId)"))
        assertTrue(receiver.contains("ReminderEligibilityPolicy.evaluateForDelivery"))
        assertTrue(receiver.contains("finally {"))
        assertTrue(receiver.contains("pendingResult.finish()"))
        assertFalse(receiver.contains("scheduleReminderFromTask"))
        assertFalse(receiver.contains("getStringExtra(\"task_title\")"))
        assertFalse(receiver.contains("getIntExtra"))
    }

    private fun source(name: String): String =
        File(mainSourceRoot, name).readText()
}
