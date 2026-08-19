package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TaskListRedesignSourceContractTest {
    private val main = File("src/main/java/com/example/myapplication")
    private val resources = File("src/main/res")
    private val adapter = main.resolve("TaskAdapter.kt").readText()
    private val listLayout = resources.resolve("layout/activity_task_list.xml").readText()
    private val cardLayout = resources.resolve("layout/item_task.xml").readText()

    @Test
    fun cardKeepsNeutralSurfaceAndUsesStatusOnlyForStripAndWording() {
        assertTrue(cardLayout.contains("android:background=\"@drawable/bg_task_list_card\""))
        assertTrue(cardLayout.contains("@+id/taskStatusSignifier"))
        assertTrue(cardLayout.contains("@+id/taskStatusText"))
        assertTrue(adapter.contains("TaskStatusPresenter.present("))
        assertTrue(adapter.contains("TaskListCardSpeechRenderer.render("))
        assertTrue(adapter.contains("holder.statusSignifier.setBackgroundColor(statusColor)"))
        assertTrue(adapter.contains("holder.taskStatusText.setTextColor(statusColor)"))
        assertFalse(adapter.contains("holder.itemView.setBackgroundResource"))
    }

    @Test
    fun cardAndDockRetainSharedVoiceFirstInteractionContracts() {
        assertTrue(adapter.contains("VoiceFirstGestureBinder.bindAction("))
        assertTrue(adapter.contains("currentCardPresentation(holder)?.spokenSummary"))
        assertTrue(adapter.contains("onOpenTask(tasks[clickedPosition])"))

        listOf("MainActivity.kt", "TodayTasksActivity.kt").forEach { fileName ->
            val activity = main.resolve(fileName).readText()
            assertTrue(activity.contains("setContentView(R.layout.activity_task_list)"))
            assertTrue(activity.contains("view = btnGoHome"))
            assertTrue(activity.contains("view = btnTalkAssistant"))
            assertTrue(activity.contains("VoiceFirstGestureBinder.bindAction("))
        }
    }

    @Test
    fun orderingAndFilteringCallsRemainAuthoritative() {
        val today = main.resolve("TodayTasksActivity.kt").readText()
        val scheduled = main.resolve("MainActivity.kt").readText()

        assertTrue(today.contains("dao.getRootTasksForDate(today)"))
        assertTrue(today.contains("TaskListOrdering.today(roots)"))
        assertTrue(scheduled.contains("dao.getRootTasks()"))
        assertTrue(scheduled.contains("TaskListOrdering.scheduled(roots)"))
    }

    @Test
    fun redesignedListSurfacesAreSolidOpaqueAndAssistantUsesDedicatedBlue() {
        listOf(
            "bg_task_list_card.xml",
            "bg_task_list_home_action.xml",
            "bg_task_list_assistant_action.xml"
        ).forEach { fileName ->
            val drawable = resources.resolve("drawable/$fileName").readText()
            assertTrue("$fileName needs a solid surface", drawable.contains("<solid"))
            assertFalse("$fileName must not use gradients", drawable.contains("<gradient"))
            assertFalse("$fileName must not use alpha", drawable.contains("android:alpha"))
        }

        val colors = resources.resolve("values/colors.xml").readText()
        assertTrue(colors.contains("name=\"assistant_primary_action\">#FF0B67E8"))
        assertTrue(colors.contains("name=\"hc_assistant_primary_action\">#FF0068FF"))
        listOf(
            "task_list_background",
            "task_list_card_surface",
            "task_list_home_action",
            "task_list_status_overdue",
            "task_list_status_due_today",
            "task_list_status_upcoming",
            "task_list_status_completed",
            "task_list_status_unscheduled"
        ).forEach { name ->
            assertTrue(colors.contains("name=\"$name\">#FF"))
        }
    }

    @Test
    fun dockIsHorizontalWithOneToTwoWeightingAndComfortableGap() {
        val dock = listLayout.substringAfter("android:id=\"@+id/taskListBottomDock\"")
            .substringBefore("</LinearLayout>")
        val home = dock.substringAfter("android:id=\"@+id/btnGoHome\"")
            .substringBefore("/>")
        val assistant = dock.substringAfter("android:id=\"@+id/btnTalkAssistant\"")
            .substringBefore("/>")

        assertTrue(dock.contains("android:orientation=\"horizontal\""))
        assertTrue(dock.contains("android:layout_height=\"108dp\""))
        assertTrue(home.contains("android:layout_weight=\"1\""))
        assertTrue(assistant.contains("android:layout_weight=\"2\""))
        assertTrue(assistant.contains("android:layout_marginStart=\"14dp\""))
    }
}
