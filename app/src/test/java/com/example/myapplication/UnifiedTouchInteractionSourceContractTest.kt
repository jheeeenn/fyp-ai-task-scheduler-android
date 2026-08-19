package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UnifiedTouchInteractionSourceContractTest {
    private val mainRoot = File("src/main/java/com/example/myapplication")

    @Test
    fun homeAppOwnedControlsIdentifyBeforeSharedDoubleTapActivation() {
        val home = source("HomeActivity.kt")

        listOf(
            "btnTodayTasks" to "HomeControlSpeechRenderer::todayTasks",
            "btnCreateTask" to "HomeControlSpeechRenderer::createTask",
            "btnScheduledTasks" to "HomeControlSpeechRenderer::scheduledTasks",
            "btnSettings" to "HomeControlSpeechRenderer::settings",
            "btnTalkAssistant" to "HomeControlSpeechRenderer::assistant"
        ).forEach { (view, speech) ->
            assertActionBinding(home, view, speech)
        }
        assertFalse(home.contains("setOnClickListenerWithHaptic"))
    }

    @Test
    fun createTaskActionsUseSharedBindingWhileEditTextAndPickersStayNative() {
        val create = source("CreateTaskActivity.kt")

        listOf(
            "btnPickDate" to "TaskFormControlSpeechRenderer::pickDate",
            "btnPickTime" to "TaskFormControlSpeechRenderer::pickTime",
            "btnSaveTask" to "TaskFormControlSpeechRenderer::saveTask",
            "btnCancelTask" to "TaskFormControlSpeechRenderer::cancel",
            "btnGoHome" to "TaskFormControlSpeechRenderer::home",
            "btnTalkAssistant" to "TaskFormControlSpeechRenderer::assistant"
        ).forEach { (view, speech) ->
            assertActionBinding(create, view, speech)
        }
        assertFalse(create.contains("setOnClickListenerWithHaptic"))
        assertTrue(create.contains("etTaskTitle = findViewById"))
        assertTrue(create.contains("DatePickerDialog("))
        assertTrue(create.contains("TimePickerDialog("))
        assertFalse(create.contains("view = etTaskTitle"))
    }

    @Test
    fun settingsCardsAndNavigationUseSharedBindingWithoutSwitchesOrRadios() {
        val settings = source("SettingsActivity.kt")
        val advanced = source("AdvancedSettingsActivity.kt")

        listOf("cardTone", "cardReplyLength", "cardSpeechSpeed").forEach { card ->
            assertTrue(settings.contains("R.id.$card"))
        }
        assertTrue(settings.contains("bindSelectionCard("))
        assertTrue(settings.contains("bindBooleanCard("))
        listOf(
            "findViewById<LinearLayout>(R.id.cardConversationAgentEndpoint)" to
                "SettingsControlSpeechRenderer::conversationEndpoint",
            "findViewById<LinearLayout>(R.id.cardTaskAgentEndpoint)" to
                "SettingsControlSpeechRenderer::taskEndpoint",
            "findViewById<Button>(R.id.btnBackToSettings)" to
                "getString(R.string.back_to_settings)"
        ).forEach { (view, speech) ->
            assertActionBinding(advanced, view, speech)
        }
        listOf(
            "findViewById<Button>(R.id.btnGoHome)" to "SettingsControlSpeechRenderer::home",
            "assistantButton" to "SettingsControlSpeechRenderer::assistant"
        ).forEach { (view, speech) ->
            assertActionBinding(settings, view, speech)
        }

        assertFalse(settings.contains("SwitchMaterial"))
        assertFalse(settings.contains("RadioButton"))
        assertFalse(settings.contains("showOptionDialog"))
        assertFalse(settings.contains("setOnClickListenerWithHaptic"))
    }

    @Test
    fun reachableEditTaskAndAssistantPanelUseTheSameGestureBinder() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val edit = source("EditTaskActivity.kt")
        val panel = source("AssistantBottomSheet.kt")

        assertTrue(manifest.contains("android:name=\".EditTaskActivity\""))
        listOf(
            "btnPickDate",
            "btnPickTime",
            "btnSaveTask",
            "btnDeleteTask",
            "btnCancelTask",
            "btnGoHome",
            "btnTalkAssistant"
        ).forEach { view ->
            assertActionBinding(edit, view, "TaskFormControlSpeechRenderer")
        }
        assertFalse(edit.contains("setOnClickListenerWithHaptic"))

        assertEquals(1, panel.countOccurrences("VoiceFirstGestureBinder.bindAction"))
        assertTrue(panel.contains("btnStopAssistant).setOnClickListener"))
        assertTrue(panel.contains("btnTypeAssistantInput).setOnClickListener"))
        assertFalse(panel.contains("VoiceFirstGestureBinder.bindInformation"))
        assertTrue(panel.contains("speechProvider = { null }"))
        assertFalse(panel.contains("System.currentTimeMillis"))
        assertFalse(panel.contains("lastTapTime"))
        assertFalse(panel.contains("doubleTapWindowMs"))
    }

    @Test
    fun taskListsAndDetailKeepActionInformationAndAccessibilityClickContracts() {
        val adapter = source("TaskAdapter.kt")
        val detail = source("TaskDetailActivity.kt")
        val binder = source("VoiceFirstGestureBinder.kt")

        assertTrue(adapter.contains("VoiceFirstGestureBinder.bindAction"))
        assertTrue(adapter.contains("view.performClick()"))
        assertTrue(detail.countOccurrences("VoiceFirstGestureBinder.bindAction") >= 9)
        assertTrue(detail.countOccurrences("VoiceFirstGestureBinder.bindInformation") >= 3)
        assertTrue(binder.contains("view.setOnClickListener { activate() }"))
        assertTrue(binder.contains("view.performClick()"))
    }

    @Test
    fun typedAssistantAndManualEditLongPressActionsRemainExposed() {
        val home = source("HomeActivity.kt")
        val create = source("CreateTaskActivity.kt")
        val edit = source("EditTaskActivity.kt")
        val detail = source("TaskDetailActivity.kt")

        listOf(home, create, edit).forEach { activity ->
            assertTrue(activity.contains("btnTalkAssistant.setOnLongClickListener"))
            assertTrue(activity.contains("AccessibilityStateHelper.exposeTypedInputAction"))
        }
        listOf("titleSurface", "dateSurface", "timeSurface").forEach { surface ->
            assertTrue(detail.contains("$surface.setOnLongClickListener"))
        }
    }

    private fun source(filename: String): String = mainRoot.resolve(filename).readText()

    private fun assertActionBinding(source: String, view: String, speech: String) {
        val viewIndex = source.indexOf("view = $view")
        assertTrue("Missing shared action binding for $view", viewIndex >= 0)
        val bindingStart = source.lastIndexOf("VoiceFirstGestureBinder.bindAction(", viewIndex)
        assertTrue("$view is not in a shared action binding", bindingStart >= 0)
        val nextBinding = source.indexOf("VoiceFirstGestureBinder.bindAction(", viewIndex + 1)
            .takeIf { it >= 0 }
            ?: source.length
        val bindingWindow = source.substring(bindingStart, nextBinding)
        assertTrue("Missing identification speech for $view", bindingWindow.contains(speech))
    }

    private fun String.countOccurrences(value: String): Int =
        windowed(value.length).count { it == value }
}
