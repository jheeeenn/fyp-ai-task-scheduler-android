package com.example.myapplication.reminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReminderMigrationAndSpeechSourceContractTest {
    private val sourceRoot = File("src/main/java/com/example/myapplication")

    @Test
    fun legacyCancellationReconstructsPreviousPendingIntentIdentity() {
        val helper = source("ReminderHelper.kt")
        val legacyCancellation = helper
            .substringAfter("private fun cancelLegacyReminder(")
            .substringBefore("private fun alarmIntent")

        assertTrue(legacyCancellation.contains("Intent(context, ReminderReceiver::class.java)"))
        assertTrue(legacyCancellation.contains("taskId.toInt()"))
        assertTrue(legacyCancellation.contains("PendingIntent.FLAG_NO_CREATE"))
        assertTrue(legacyCancellation.contains("PendingIntent.FLAG_IMMUTABLE"))
        assertFalse(legacyCancellation.contains("action ="))
        assertFalse(legacyCancellation.contains("data ="))
        assertFalse(legacyCancellation.contains("putExtra("))
    }

    @Test
    fun centralCancellationCoversLegacyAndAllEscalationStages() {
        val helper = source("ReminderHelper.kt")
        val centralCancellation = helper
            .substringAfter("fun cancelReminder(context: Context, taskId: Long)")
            .substringBefore("fun cancelLegacyReminder(context: Context")

        assertTrue(centralCancellation.contains("cancelLegacyReminder(context, taskId, alarmManager)"))
        assertTrue(centralCancellation.contains("ReminderSequenceCoordinator.cancelAllStages"))
    }

    @Test
    fun homeRunsVersionedBootstrapOnlyAfterPermissionsAreReady() {
        val home = source("HomeActivity.kt")
        val onResume = home
            .substringAfter("override fun onResume")
            .substringBefore("private fun refreshOverview")
        val bootstrap = home
            .substringAfter("private fun runReminderEscalationBootstrapIfReady")
            .substringBefore("private fun showReminderSetupDialog")

        assertTrue(onResume.contains("if (reminderPermissionsReady)"))
        assertTrue(onResume.contains("runReminderEscalationBootstrapIfReady()"))
        assertTrue(bootstrap.contains("!allRequiredPermissionsReady()"))
        assertTrue(bootstrap.contains("dao.getRootActiveTasks()"))
        assertTrue(bootstrap.contains("ReminderEscalationBootstrapper"))
        assertTrue(bootstrap.contains("SharedPreferencesReminderBootstrapVersionStore"))
        assertTrue(bootstrap.contains("ReminderHelper.cancelLegacyReminder"))
        assertTrue(bootstrap.contains("ReminderHelper.scheduleReminderFromTask"))
        assertTrue(bootstrap.contains("REMINDER_ESCALATION_BOOTSTRAP"))
    }

    @Test
    fun speechServiceSerializesCompletionsOnMainThreadAndKeepsInvalidStartsIsolated() {
        val service = source("ReminderSpeechService.kt")
        val onStart = service
            .substringAfter("override fun onStartCommand")
            .substringBefore("override fun onDestroy")

        assertTrue(service.contains("ReminderSpeechQueue("))
        assertTrue(service.contains("Handler(Looper.getMainLooper())"))
        assertTrue(service.contains("mainHandler.post(onComplete)"))
        assertTrue(onStart.contains("speechQueue.enqueuePayload"))
        assertTrue(onStart.contains("if (speechQueue.isIdle)"))
        assertTrue(service.contains("onQueueEmpty ="))
        assertTrue(service.contains("stopForeground(STOP_FOREGROUND_REMOVE)"))
        assertTrue(service.contains("stopSelf()"))
        assertTrue(onStart.contains("return START_NOT_STICKY"))
        assertFalse(onStart.contains("voiceHelper?.speak"))
    }

    private fun source(name: String): String =
        File(sourceRoot, name).readText()
}
