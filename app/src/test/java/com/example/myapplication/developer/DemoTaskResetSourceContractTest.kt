package com.example.myapplication.developer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DemoTaskResetSourceContractTest {
    private val mainRoot = File("src/main")
    private val activity = source("AdvancedSettingsActivity.kt")
    private val coordinator = source("developer/DemoTaskResetCoordinator.kt")
    private val dao = source("data/TaskDao.kt")
    private val layout = mainRoot.resolve("res/layout/activity_advanced_settings.xml").readText()
    private val strings = mainRoot.resolve("res/values/strings.xml").readText()

    @Test
    fun advancedSettingsUsesTechnicalCardVoiceFirstActionAndConfirmation() {
        val card = layout.substringAfter("android:id=\"@+id/cardResetDemoTasks\"")
            .substringBefore("</LinearLayout>")
        val confirmation = activity.substringAfter("private fun showResetDemoTasksConfirmation")
            .substringBefore("private fun resetDemoTasks")

        assertTrue(card.contains("@style/SettingsTechnicalCard"))
        assertTrue(card.contains("@string/reset_demo_tasks"))
        assertTrue(card.contains("@string/reset_demo_tasks_description"))
        assertTrue(activity.contains("VoiceFirstGestureBinder.bindAction("))
        assertTrue(activity.contains("view = findViewById<LinearLayout>(R.id.cardResetDemoTasks)"))
        assertTrue(activity.contains("activate = ::showResetDemoTasksConfirmation"))
        assertTrue(confirmation.contains("AlertDialog.Builder(this)"))
        assertTrue(confirmation.contains("setPositiveButton(R.string.reset_demo_tasks_action)"))
        assertTrue(confirmation.contains("setNegativeButton(R.string.cancel"))
        assertTrue(strings.contains("Saved routines and settings will not be changed."))
    }

    @Test
    fun taskOnlyReplacementIsTransactionalAndLeavesOtherStoresOutsideItsApi() {
        val replacement = dao.substringAfter("suspend fun replaceTaskTableWithRootTasksAtomically")
            .substringBefore("suspend fun insertRootTasksAtomically")
        val resetSources = coordinator + replacement
        val methodIndex = dao.indexOf("suspend fun replaceTaskTableWithRootTasksAtomically")

        assertTrue(dao.lastIndexOf("@Transaction", methodIndex) >= 0)
        assertTrue(dao.contains("@Query(\"DELETE FROM tasks\")"))
        assertTrue(replacement.contains("val previousTaskIds = getAll().map(TaskEntity::id)"))
        assertTrue(replacement.indexOf("deleteAllTasks()") < replacement.indexOf("insertAll(tasks)"))
        listOf(
            "RoutineDao",
            "RoutineStep",
            "LearnedTimePreference",
            "AppPreferences",
            "SharedPreferences"
        ).forEach { forbidden -> assertFalse(resetSources.contains(forbidden)) }
    }

    @Test
    fun activityGuardsDuplicatesAndRunsCoordinatorOffMainThread() {
        val reset = activity.substringAfter("private fun resetDemoTasks()")
            .substringBefore("private fun presentDemoTaskResetResult")

        assertTrue(reset.contains("if (demoTaskResetInProgress) return"))
        assertTrue(reset.contains("demoTaskResetInProgress = true"))
        assertTrue(reset.contains("lifecycleScope.launch"))
        assertTrue(reset.contains("withContext(Dispatchers.IO)"))
        assertTrue(reset.contains("demoTaskResetCoordinator.reset()"))
        assertTrue(reset.contains("finally"))
        assertTrue(reset.contains("demoTaskResetInProgress = false"))
        assertTrue(activity.contains("ReminderHelper.cancelReminder(appContext, taskId)"))
        assertTrue(activity.contains("ReminderHelper.scheduleReminderFromTask(appContext, task)"))
    }

    private fun source(relativePath: String): String =
        mainRoot.resolve("java/com/example/myapplication/$relativePath").readText()
}
