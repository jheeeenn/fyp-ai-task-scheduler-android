package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeRedesignSourceContractTest {
    private val main = File("src/main/java/com/example/myapplication")
    private val resources = File("src/main/res")
    private val home = main.resolve("HomeActivity.kt").readText()
    private val presenter = main.resolve("HomeOverviewPresentation.kt").readText()
    private val layout = resources.resolve("layout/activity_home.xml").readText()

    @Test
    fun previewUsesTodayDaoSharedOrderingAndOneNullableSelection() {
        assertTrue(home.contains("dao.getRootTasksForDate(today)"))
        assertTrue(presenter.contains("TaskListOrdering.today("))
        assertTrue(presenter.contains(").firstOrNull()"))
        assertFalse(presenter.contains("take(2)"))
    }

    @Test
    fun previewAndViewAllKeepVoiceFirstSingleDoubleTapContracts() {
        val previewBinding = home.substringAfter("view = todayPreviewSurface,")
            .substringBefore("// Notification permission")
        assertTrue(previewBinding.contains("speechProvider = { homePreviewSpeech }"))
        assertTrue(previewBinding.contains("TaskDetailActivity.EXTRA_TASK_ID"))
        assertTrue(previewBinding.contains("task.id"))

        val viewAllBinding = home.substringAfter("view = btnTodayTasks,")
            .substringBefore("VoiceFirstGestureBinder.bindAction(")
        assertTrue(viewAllBinding.contains("HomeControlSpeechRenderer::todayTasks"))
        assertTrue(viewAllBinding.contains("TodayTasksActivity::class.java"))
    }

    @Test
    fun emptyPreviewIsHiddenAndAssistantContractRemainsIntact() {
        assertTrue(home.contains("previewSurface.visibility = View.GONE"))
        assertTrue(home.contains("view = btnTalkAssistant"))
        assertTrue(home.contains("HomeControlSpeechRenderer::assistant"))
        assertTrue(home.contains("btnTalkAssistant.setOnLongClickListener"))
        assertTrue(home.contains("showTypedAssistantInputDialog()"))
        assertTrue(home.contains("assistantSession.bindAssistantControl(btnTalkAssistant)"))
    }

    @Test
    fun homeUsesScrollableContentAndFixedLargestAssistantWithoutPercentageSplit() {
        assertTrue(layout.contains("<ScrollView"))
        assertTrue(layout.contains("app:layout_constraintBottom_toTopOf=\"@id/btnTalkAssistant\""))
        assertTrue(layout.contains("android:layout_height=\"136dp\""))
        assertTrue(layout.contains("@+id/todayPreviewSurface"))
        assertTrue(layout.contains("android:visibility=\"gone\""))
        assertFalse(layout.contains("Guideline"))
        assertFalse(layout.contains("layout_constraintGuide_percent"))
    }

    @Test
    fun homeDrawablesUseOnlySolidOpaqueThemeSurfaces() {
        listOf(
            "bg_home_assistant_action.xml",
            "bg_home_secondary_action.xml",
            "bg_home_view_all_action.xml",
            "bg_home_today_preview.xml"
        ).forEach { fileName ->
            val drawable = resources.resolve("drawable/$fileName").readText()
            assertTrue("$fileName must define a solid surface", drawable.contains("<solid"))
            assertFalse("$fileName must not use a gradient", drawable.contains("<gradient"))
            assertFalse("$fileName must not use alpha", drawable.contains("android:alpha"))
        }
    }
}
