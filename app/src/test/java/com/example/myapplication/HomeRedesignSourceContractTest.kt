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
    private val colors = resources.resolve("values/colors.xml").readText()
    private val attrs = resources.resolve("values/attrs.xml").readText()
    private val themes = resources.resolve("values/themes.xml").readText()

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
    fun targetDeviceActionsUseThePhysicallyTestedDimensionsAndSpacing() {
        val createButton = layout.substringAfter("android:id=\"@+id/btnCreateTask\"")
            .substringBefore("/>")
        val scheduledButton = layout.substringAfter("android:id=\"@+id/btnScheduledTasks\"")
            .substringBefore("/>")
        val settingsButton = layout.substringAfter("android:id=\"@+id/btnSettings\"")
            .substringBefore("/>")
        val assistantButton = layout.substringAfter("android:id=\"@+id/btnTalkAssistant\"")
            .substringBefore("/>")

        assertTrue(createButton.contains("android:minHeight=\"104dp\""))
        assertTrue(scheduledButton.contains("android:minHeight=\"104dp\""))
        assertTrue(scheduledButton.contains("android:layout_marginStart=\"20dp\""))
        assertTrue(settingsButton.contains("android:minHeight=\"96dp\""))
        assertTrue(settingsButton.contains("android:gravity=\"center\""))
        assertTrue(assistantButton.contains("android:layout_height=\"220dp\""))
    }

    @Test
    fun homeKeepsScrollableContentAndFixedLargestAssistantWithoutPercentageSplit() {
        assertTrue(layout.contains("<ScrollView"))
        assertTrue(layout.contains("app:layout_constraintBottom_toTopOf=\"@id/btnTalkAssistant\""))
        assertTrue(layout.contains("android:layout_height=\"220dp\""))
        assertTrue(layout.contains("@+id/todayPreviewSurface"))
        assertTrue(layout.contains("android:visibility=\"gone\""))
        assertFalse(layout.contains("Guideline"))
        assertFalse(layout.contains("layout_constraintGuide_percent"))
    }

    @Test
    fun homeNavigationUsesDistinctNormalAndHighContrastSemanticColours() {
        val actions = listOf(
            "Today" to "today",
            "Create" to "create",
            "Scheduled" to "scheduled",
            "Settings" to "settings"
        )
        actions.forEach { (attributeSuffix, colorName) ->
            assertTrue(attrs.contains("appColorHome${attributeSuffix}Action"))
            assertTrue(colors.contains("name=\"home_${colorName}_action\">#FF"))
            assertTrue(colors.contains("name=\"hc_home_${colorName}_action\">#FF"))
            assertTrue(
                themes.contains(
                    "appColorHome${attributeSuffix}Action\">@color/home_${colorName}_action"
                )
            )
            assertTrue(
                themes.contains(
                    "appColorHome${attributeSuffix}Action\">@color/hc_home_${colorName}_action"
                )
            )
        }
        assertTrue(colors.contains("name=\"home_assistant_primary\">#FF0B67E8"))
        assertTrue(colors.contains("name=\"hc_home_assistant_primary\">#FF0068FF"))
        assertFalse(attrs.contains("appColorHomeSecondarySurface"))
    }

    @Test
    fun homeDrawablesUseOnlySolidOpaqueThemeSurfaces() {
        listOf(
            "bg_home_assistant_action.xml",
            "bg_home_create_action.xml",
            "bg_home_scheduled_action.xml",
            "bg_home_settings_action.xml",
            "bg_home_view_all_action.xml",
            "bg_home_today_preview.xml"
        ).forEach { fileName ->
            val drawable = resources.resolve("drawable/$fileName").readText()
            assertTrue("$fileName must define a solid surface", drawable.contains("<solid"))
            assertFalse("$fileName must not use a gradient", drawable.contains("<gradient"))
            assertFalse("$fileName must not use alpha", drawable.contains("android:alpha"))
        }
    }

    @Test
    fun everyHomeDestinationStillUsesTheSharedVoiceFirstBinding() {
        listOf(
            "btnTodayTasks",
            "btnCreateTask",
            "btnScheduledTasks",
            "btnSettings",
            "btnTalkAssistant",
            "todayPreviewSurface"
        ).forEach { view ->
            val viewIndex = home.indexOf("view = $view")
            assertTrue("Missing voice-first binding for $view", viewIndex >= 0)
            assertTrue(
                home.lastIndexOf("VoiceFirstGestureBinder.bindAction(", viewIndex) >= 0
            )
        }
        assertTrue(home.contains("btnTalkAssistant.setOnLongClickListener"))
    }
}
