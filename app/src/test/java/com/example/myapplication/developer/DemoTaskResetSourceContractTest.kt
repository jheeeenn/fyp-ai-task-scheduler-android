package com.example.myapplication.developer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DemoTaskResetSourceContractTest {
    private val mainRoot = File("src/main")
    private val activity = source("AdvancedSettingsActivity.kt")
    private val coordinator = source("developer/DemoTaskResetCoordinator.kt")
    private val taskDao = source("data/TaskDao.kt")
    private val routineDao = source("data/RoutineDao.kt")
    private val layout = mainRoot.resolve("res/layout/activity_advanced_settings.xml").readText()
    private val strings = mainRoot.resolve("res/values/strings.xml").readText()

    @Test
    fun developerCardsStayTitleOnlyWithNonEmptyVoiceFirstIdentificationAndConfirmation() {
        val developerCard = layout.substringAfter("android:id=\"@+id/cardDeveloperTesting\"")
            .substringBefore("</LinearLayout>")
        val resetCard = layout.substringAfter("android:id=\"@+id/cardResetDemoEnvironment\"")
            .substringBefore("</LinearLayout>")
        val confirmation = activity.substringAfter("private fun showResetDemoEnvironmentConfirmation")
            .substringBefore("private fun resetDemoEnvironment")

        listOf(developerCard, resetCard).forEach { card ->
            assertTrue(card.contains("@style/SettingsTechnicalCard"))
            assertFalse(card.contains("SettingsDescriptionText"))
            assertEquals(1, card.countOccurrences("<TextView"))
        }
        assertTrue(developerCard.contains("@string/developer_testing"))
        assertTrue(resetCard.contains("@string/reset_demo_environment"))
        assertFalse(layout.contains("developer_testing_description"))
        assertFalse(layout.contains("reset_demo_tasks_description"))
        assertTrue(activity.contains("VoiceFirstGestureBinder.bindAction("))
        assertTrue(activity.contains("speechProvider = { getString(R.string.developer_testing) }"))
        assertTrue(activity.contains("speechProvider = { getString(R.string.reset_demo_environment) }"))
        assertTrue(strings.contains(">Developer Testing</string>"))
        assertTrue(strings.contains(">Reset Demo Environment</string>"))
        assertTrue(activity.contains("view = findViewById<LinearLayout>(R.id.cardResetDemoEnvironment)"))
        assertTrue(activity.contains("activate = ::showResetDemoEnvironmentConfirmation"))
        assertTrue(confirmation.contains("AlertDialog.Builder(this)"))
        assertTrue(confirmation.contains("setPositiveButton(R.string.reset_demo_environment_action)"))
        assertTrue(confirmation.contains("setNegativeButton(R.string.cancel"))
        assertTrue(strings.contains("delete current tasks and saved routines"))
        assertTrue(strings.contains("Settings and learned preferences will not be changed."))
    }

    @Test
    fun databaseResetIsOneExplicitOrderedTransactionWithPreferencesOutsideItsApi() {
        val resetStore = coordinator.substringAfter("class RoomDemoEnvironmentStore")
            .substringBefore("fun interface DemoTaskReminderCanceller")

        assertTrue(resetStore.contains("database.withTransaction"))
        assertTrue(taskDao.contains("@Query(\"DELETE FROM tasks\")"))
        assertTrue(routineDao.contains("@Query(\"DELETE FROM routine_steps\")"))
        assertTrue(routineDao.contains("@Query(\"DELETE FROM routines\")"))
        val capture = resetStore.indexOf("val previousTaskIds = taskDao.getAll()")
        val clearTasks = resetStore.indexOf("taskDao.deleteAllTasks()")
        val clearSteps = resetStore.indexOf("routineDao.deleteAllRoutineSteps()")
        val clearRoutines = resetStore.indexOf("routineDao.deleteAllRoutines()")
        val insertSeeds = resetStore.indexOf("taskDao.insertAll(tasks)")
        assertTrue(capture in 0 until clearTasks)
        assertTrue(clearTasks < clearSteps)
        assertTrue(clearSteps < clearRoutines)
        assertTrue(clearRoutines < insertSeeds)
        listOf(
            "LearnedTimePreference",
            "AppPreferences",
            "SharedPreferences"
        ).forEach { forbidden -> assertFalse(resetStore.contains(forbidden)) }
    }

    @Test
    fun activityGuardsDuplicatesAndRunsCoordinatorOffMainThread() {
        val reset = activity.substringAfter("private fun resetDemoEnvironment()")
            .substringBefore("private fun presentDemoEnvironmentResetResult")

        assertTrue(reset.contains("if (demoEnvironmentResetInProgress) return"))
        assertTrue(reset.contains("demoEnvironmentResetInProgress = true"))
        assertTrue(reset.contains("lifecycleScope.launch"))
        assertTrue(reset.contains("withContext(Dispatchers.IO)"))
        assertTrue(reset.contains("demoEnvironmentResetCoordinator.reset()"))
        assertTrue(reset.contains("finally"))
        assertTrue(reset.contains("demoEnvironmentResetInProgress = false"))
        assertTrue(activity.contains("ReminderHelper.cancelReminder(appContext, taskId)"))
        assertTrue(activity.contains("ReminderHelper.scheduleReminderFromTask(appContext, task)"))
    }

    private fun source(relativePath: String): String =
        mainRoot.resolve("java/com/example/myapplication/$relativePath").readText()

    private fun String.countOccurrences(value: String): Int = split(value).size - 1
}
