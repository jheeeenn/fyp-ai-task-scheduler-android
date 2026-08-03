package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TaskListAndDetailSourceContractTest {
    private val mainRoot = File("src/main/java/com/example/myapplication")
    private val layoutRoot = File("src/main/res/layout")

    @Test
    fun listLayoutContainsOnlyListEmptyStateAndBottomAnchors() {
        val layout = layoutRoot.resolve("activity_task_list.xml").readText()

        listOf("btnTaskDone", "btnTaskEdit", "btnTaskDelete", "btnCreateNewTask", "taskActionBar")
            .forEach { removedId -> assertFalse(layout.contains(removedId)) }
        assertTrue(layout.contains("@+id/titleText"))
        assertTrue(layout.contains("@+id/taskRecyclerView"))
        assertTrue(layout.contains("@+id/emptyTaskText"))
        assertTrue(layout.contains("@+id/btnGoHome"))
        assertTrue(layout.contains("@+id/btnTalkAssistant"))
        assertTrue(layout.contains("app:layout_constraintBottom_toTopOf=\"@id/btnGoHome\""))
    }

    @Test
    fun compactCardContainsOnlyTitleAndRelativeStatusProgress() {
        val item = layoutRoot.resolve("item_task.xml").readText()

        assertTrue(item.contains("@+id/taskText"))
        assertTrue(item.contains("@+id/taskStatusText"))
        assertTrue(item.contains("android:minHeight=\"96dp\""))
        listOf(
            "taskDateText",
            "taskTimeText",
            "taskSubtaskTitlesText",
            "taskSubtaskSummaryText"
        ).forEach { removedId -> assertFalse(item.contains(removedId)) }
    }

    @Test
    fun listsHaveNoSelectionStateAndOpenDetailsWithOnlyTaskId() {
        val adapter = mainRoot.resolve("TaskAdapter.kt").readText()
        val scheduled = mainRoot.resolve("MainActivity.kt").readText()
        val today = mainRoot.resolve("TodayTasksActivity.kt").readText()

        listOf(adapter, scheduled, today).forEach { source ->
            assertFalse(source.contains("selectedTaskId"))
            assertFalse(source.contains("getCurrentlySelectedTask"))
            assertFalse(source.contains("updateActionButtonsState"))
        }
        listOf(scheduled, today).forEach { source ->
            assertTrue(source.contains("TaskDetailActivity.EXTRA_TASK_ID"))
            assertFalse(source.contains("putExtra(\"task_title\""))
            assertFalse(source.contains("putExtra(\"task_date\""))
            assertFalse(source.contains("putExtra(\"task_time\""))
            assertTrue(source.contains("voiceHelper.shutdown()"))
            assertTrue(source.contains("HomeAssistantEntryContract.putGeneric"))
            assertTrue(source.contains("navigationCoordinator.cancelPending()"))
            assertTrue(source.contains("voiceHelper.speakWithResult"))
            assertTrue(source.contains("runOnUiThread"))
        }
        assertTrue(scheduled.contains("dao.getRootTasks()"))
        assertTrue(scheduled.contains("TaskListOrdering.scheduled(roots)"))
        assertTrue(today.contains("dao.getRootTasksForDate(today)"))
        assertTrue(today.contains("TaskListOrdering.today(roots)"))
    }

    @Test
    fun taskCardsUseSharedConfirmedSingleDoubleAndScrollBinding() {
        val adapter = mainRoot.resolve("TaskAdapter.kt").readText()
        val binder = mainRoot.resolve("VoiceFirstGestureBinder.kt").readText()
        val interaction = mainRoot.resolve("VoiceFirstTouchInteraction.kt").readText()

        assertTrue(adapter.contains("VoiceFirstGestureBinder.bindAction"))
        assertTrue(binder.contains("GestureDetector"))
        assertTrue(binder.contains("override fun onSingleTapConfirmed"))
        assertTrue(binder.contains("interaction.onSingleTapConfirmed()"))
        assertTrue(binder.contains("override fun onDoubleTap"))
        assertTrue(binder.contains("interaction.onDoubleTap()"))
        assertTrue(binder.contains("override fun onScroll"))
        assertTrue(binder.contains("interaction.onScroll()"))
        assertTrue(binder.contains("view.performClick()"))
        assertTrue(interaction.contains("fun onScroll(): Boolean = false"))
        assertFalse(binder.contains("System.currentTimeMillis"))
    }

    @Test
    fun detailsAreRegisteredAndLoadOnlyAuthoritativeRoomData() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val detail = mainRoot.resolve("TaskDetailActivity.kt").readText()

        assertTrue(manifest.contains("android:name=\".TaskDetailActivity\""))
        assertTrue(detail.contains("intent.getLongExtra(EXTRA_TASK_ID"))
        assertTrue(detail.contains("dao.getById(taskId)"))
        assertTrue(detail.contains("dao.getSubtasks(taskId)"))
        assertTrue(detail.contains("override fun onResume()"))
        assertFalse(detail.contains("intent.getStringExtra(\"task_title\""))
        assertFalse(detail.contains("intent.getStringExtra(\"task_date\""))
        assertFalse(detail.contains("intent.getStringExtra(\"task_time\""))
    }

    @Test
    fun detailActionsPreserveCompletionReminderSaveAndCentralDeleteContracts() {
        val detail = mainRoot.resolve("TaskDetailActivity.kt").readText()

        assertTrue(detail.contains("TaskDetailSpeechRenderer.readAll"))
        assertTrue(detail.contains("dao.updateDoneStatusForTaskAndSubtasks"))
        assertTrue(detail.contains("ReminderHelper.cancelReminder"))
        assertTrue(detail.contains("ReminderHelper.scheduleReminderFromTask"))
        assertTrue(detail.contains("updateTaskAndSubtasksIfAuthoritativeSnapshotMatches"))
        assertTrue(detail.contains("btnSaveChanges"))
        assertTrue(detail.contains("HomeAssistantEntryMode.TASK_DETAIL_DELETE_CONFIRMATION"))
        assertTrue(detail.contains("HomeAssistantEntryContract.putTaskDetail"))
        assertFalse(detail.contains("dao.deleteTaskAndSubtasks"))
    }

    @Test
    fun detailLayoutHasIndependentInformationSurfacesGridAndAnchors() {
        val layout = layoutRoot.resolve("activity_task_detail.xml").readText()

        listOf(
            "detailTitleSurface",
            "detailStatusSurface",
            "detailDateSurface",
            "detailTimeSurface",
            "detailSubtaskProgressSurface",
            "btnReadAll",
            "btnToggleDone",
            "btnSaveChanges",
            "btnDeleteTask",
            "btnGoHome",
            "btnTalkAssistant"
        ).forEach { id -> assertTrue(layout.contains("@+id/$id")) }
        assertTrue(layout.contains("<ScrollView"))
        assertTrue(layout.contains("app:layout_constraintBottom_toTopOf=\"@id/btnGoHome\""))
        assertTrue(layout.contains("app:layout_constraintBottom_toTopOf=\"@id/btnTalkAssistant\""))
    }

    @Test
    fun statusTreatmentsProvideFiveSoftVisualCategories() {
        val colors = File("src/main/res/values/colors.xml").readText()
        val adapter = mainRoot.resolve("TaskAdapter.kt").readText()

        listOf("overdue", "today", "upcoming", "completed", "unscheduled").forEach { state ->
            assertTrue(colors.contains("task_status_${state}_surface"))
            assertTrue(colors.contains("task_status_${state}_text"))
            assertTrue(adapter.contains("bg_task_status_$state"))
        }
    }

    @Test
    fun daoPreservesSubtaskOrderAndNoSchemaMigrationWasAdded() {
        val dao = mainRoot.resolve("data/TaskDao.kt").readText()
        val database = mainRoot.resolve("data/AppDatabase.kt").readText()

        assertTrue(dao.contains("ORDER BY subtaskOrder ASC, id ASC"))
        assertTrue(database.contains("version = 7"))
        assertFalse(database.contains("MIGRATION_7_8"))
    }
}
