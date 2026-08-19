package com.example.myapplication.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AccessibilityIntegrationSourceTest {
    private val sourceRoot = File("src/main/java/com/example/myapplication")
    private val layoutRoot = File("src/main/res/layout")

    @Test
    fun everyUserFacingActivityUsesReusableAccessibilityActivity() {
        listOf(
            "HomeActivity.kt",
            "TodayTasksActivity.kt",
            "MainActivity.kt",
            "CreateTaskActivity.kt",
            "EditTaskActivity.kt",
            "TaskDetailActivity.kt",
            "SettingsActivity.kt",
            "AdvancedSettingsActivity.kt"
        ).forEach { fileName ->
            assertTrue(
                "$fileName must inherit AccessibilityActivity",
                File(sourceRoot, fileName).readText().contains(": AccessibilityActivity()")
            )
        }
    }

    @Test
    fun layoutsUseThemeSemanticColorsAndKeepTextWrappable() {
        val majorLayouts = listOf(
            "activity_home.xml",
            "activity_task_list.xml",
            "activity_create_task.xml",
            "activity_edit_task.xml",
            "activity_task_detail.xml",
            "activity_settings.xml",
            "bottomsheet_assistant.xml",
            "item_task.xml"
        ).map { File(layoutRoot, it).readText() }

        majorLayouts.forEach { layout ->
            assertTrue(layout.contains("?attr/appColor"))
            assertFalse(layout.contains("android:singleLine=\"true\""))
        }
    }

    @Test
    fun binarySettingsUseWholeCardVoiceFirstActionsWithoutSwitches() {
        val settingsSource = File(sourceRoot, "SettingsActivity.kt").readText()
        val settingsLayout = File(layoutRoot, "activity_settings.xml").readText()

        listOf("LargeText", "HighContrast", "ProcessingHaptic", "SessionEndHaptic").forEach { name ->
            assertTrue(settingsLayout.contains("@+id/card$name"))
            assertTrue(settingsLayout.contains("@+id/tv${name}Value"))
        }
        assertTrue(settingsSource.contains("bindBooleanCard("))
        assertTrue(settingsSource.contains("VoiceFirstGestureBinder.bindAction("))
        assertFalse(settingsLayout.contains("SwitchMaterial"))
        assertFalse(settingsSource.contains("setOnCheckedChangeListener"))
    }

    @Test
    fun assistantStatesKeepExplicitTextAndACompactColourIndicator() {
        val sheetSource = File(sourceRoot, "AssistantBottomSheet.kt").readText()
        val layout = File(layoutRoot, "bottomsheet_assistant.xml").readText()

        listOf("LISTENING", "PROCESSING", "SPEAKING", "WAITING_FOR_CONFIRMATION", "ERROR", "STOPPED")
            .forEach { assertTrue(sheetSource.contains("AssistantAccessibilityState.$it")) }
        assertTrue(layout.contains("@+id/assistantStateIndicator"))
        assertTrue(layout.contains("@drawable/bg_assistant_state_indicator"))
        assertTrue(layout.contains("@+id/tvAssistantState"))
        assertFalse(layout.contains("@drawable/bg_assistant_state_label"))
    }
}
