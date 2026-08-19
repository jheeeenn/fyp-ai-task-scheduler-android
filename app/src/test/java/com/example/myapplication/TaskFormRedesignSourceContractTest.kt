package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TaskFormRedesignSourceContractTest {
    private val mainRoot = File("src/main/java/com/example/myapplication")
    private val layoutRoot = File("src/main/res/layout")
    private val drawableRoot = File("src/main/res/drawable")
    private val valuesRoot = File("src/main/res/values")
    private val createSource = mainRoot.resolve("CreateTaskActivity.kt").readText()
    private val editSource = mainRoot.resolve("EditTaskActivity.kt").readText()
    private val createLayout = layoutRoot.resolve("activity_create_task.xml").readText()
    private val editLayout = layoutRoot.resolve("activity_edit_task.xml").readText()

    @Test
    fun combinedScheduleSurfacesReplaceSeparatePickerButtonsAndKeepNativeDialogs() {
        listOf(createLayout, editLayout).forEach { layout ->
            assertFalse(layout.contains("btnPickDate"))
            assertFalse(layout.contains("btnPickTime"))
            assertTrue(layout.contains("@+id/dateInfoGroup"))
            assertTrue(layout.contains("@+id/timeInfoGroup"))
            assertTrue(layout.contains("android:minHeight=\"100dp\""))
            assertTrue(layout.contains("@drawable/bg_task_form_surface"))
        }
        listOf(createSource, editSource).forEach { source ->
            assertActionBinding(source, "dateInfoGroup", "TaskFormControlSpeechRenderer.date(selectedDate)", "::openDatePicker")
            assertActionBinding(source, "timeInfoGroup", "TaskFormControlSpeechRenderer.time(selectedTime)", "::openTimePicker")
            assertTrue(source.contains("DatePickerDialog("))
            assertTrue(source.contains("TimePickerDialog("))
        }
    }

    @Test
    fun titleRemainsAResizableNativeEditTextOutsideTheGestureBinder() {
        listOf(createLayout, editLayout).forEach { layout ->
            val title = layout.substringAfter("<EditText").substringBefore("/>")
            assertTrue(title.contains("@+id/etTaskTitle"))
            assertTrue(title.contains("android:inputType=\"textCapSentences\""))
            assertTrue(title.contains("android:minHeight=\"88dp\""))
            assertTrue(title.contains("android:layout_height=\"wrap_content\""))
        }
        listOf(createSource, editSource).forEach { source ->
            assertFalse(source.contains("view = etTaskTitle"))
            assertTrue(source.contains("etTaskTitle.setText("))
        }
    }

    @Test
    fun visualScheduleValuesUseSharedValueOnlyRendering() {
        listOf(createSource, editSource).forEach { source ->
            assertTrue(source.contains("renderSelectedDate()"))
            assertTrue(source.contains("renderSelectedTime()"))
            assertTrue(source.contains("TaskFormScheduleValueRenderer.date(selectedDate)"))
            assertTrue(source.contains("TaskFormScheduleValueRenderer.time(selectedTime)"))
            assertFalse(source.contains("tvSelectedDate.text = \"Selected date:"))
            assertFalse(source.contains("tvSelectedTime.text = \"Selected time:"))
        }
    }

    @Test
    fun formActionsRemainVoiceFirstAndUseSharedSemanticStyling() {
        assertActionBinding(createSource, "btnSaveTask", "TaskFormControlSpeechRenderer::saveTask")
        assertActionBinding(editSource, "btnSaveTask", "TaskFormControlSpeechRenderer::saveChanges")
        listOf(createSource, editSource).forEach { source ->
            assertActionBinding(source, "btnCancelTask", "TaskFormControlSpeechRenderer::cancel")
        }
        assertActionBinding(editSource, "btnDeleteTask", "TaskFormControlSpeechRenderer::deleteTask", "::confirmDeleteTask")

        listOf(createLayout, editLayout).forEach { layout ->
            assertTrue(layout.contains("@drawable/bg_task_form_save"))
            assertTrue(layout.contains("@drawable/bg_task_form_cancel"))
            assertTrue(layout.contains("android:minHeight=\"82dp\""))
        }
        assertTrue(editLayout.contains("@drawable/bg_task_form_delete"))
        assertTrue(editLayout.contains("android:minHeight=\"76dp\""))
    }

    @Test
    fun sharedDockIsFixedAt108DpWithOneToTwoActionRatio() {
        listOf(createLayout, editLayout).forEach { layout ->
            val dock = layout.substringAfter("android:id=\"@+id/taskFormBottomDock\"")
                .substringBefore("</LinearLayout>")
            assertTrue(dock.contains("android:layout_height=\"108dp\""))
            assertTrue(dock.contains("android:orientation=\"horizontal\""))
            assertTrue(dock.contains("android:layout_weight=\"1\""))
            assertTrue(dock.contains("android:layout_weight=\"2\""))
            assertTrue(dock.contains("android:layout_marginStart=\"14dp\""))
            assertTrue(dock.contains("@drawable/bg_task_list_home_action"))
            assertTrue(dock.contains("@drawable/bg_task_list_assistant_action"))
            assertTrue(layout.contains("app:layout_constraintBottom_toTopOf=\"@id/taskFormBottomDock\""))
        }
        val assistantDrawable = drawableRoot.resolve("bg_task_list_assistant_action.xml").readText()
        assertTrue(assistantDrawable.contains("?attr/appColorAssistantPrimaryAction"))
    }

    @Test
    fun assistantTypedFallbackAndProtectedCreateEditMachineryRemainPresent() {
        listOf(createSource, editSource).forEach { source ->
            assertTrue(source.contains("btnTalkAssistant.setOnLongClickListener"))
            assertTrue(source.contains("AccessibilityStateHelper.exposeTypedInputAction(btnTalkAssistant)"))
        }
        listOf("PendingTaskState", "CreateDraftMoveInterpreter", "CreateDraftSemanticOrchestrator", "createDraftResolutionGeneration")
            .forEach { assertTrue(createSource.contains(it)) }
        listOf("authoritativeOriginalTitle", "RelativeTemporalProposalSession", "RelativeTemporalSaveClaim", "claimSave(")
            .forEach { assertTrue(editSource.contains(it)) }
    }

    @Test
    fun scheduleSurfacesRemainAnnouncementAnchors() {
        listOf(createSource, editSource).forEach { source ->
            assertTrue(source.contains("AccessibilityAnnouncementHelper.announce("))
            assertTrue(source.contains("TaskCardAccessibilitySemantics.spokenDate(selectedDate)"))
            assertTrue(source.contains("TaskCardAccessibilitySemantics.spokenTime(selectedTime)"))
        }
    }

    @Test
    fun taskFormPaletteIsOpaqueAndHasExplicitHighContrastMappings() {
        val colors = valuesRoot.resolve("colors.xml").readText()
        val themes = valuesRoot.resolve("themes.xml").readText()
        val nightThemes = File("src/main/res/values-night/themes.xml").readText()
        val roles = listOf("Surface", "Outline", "Save", "Cancel", "Delete")

        listOf("surface", "outline", "save", "cancel", "delete").forEach { role ->
            assertTrue(Regex("<color name=\"task_form_$role\">#FF[0-9A-F]{6}</color>").containsMatchIn(colors))
            assertTrue(Regex("<color name=\"hc_task_form_$role\">#FF[0-9A-F]{6}</color>").containsMatchIn(colors))
        }
        roles.forEach { role ->
            assertTrue(themes.contains("appColorTaskForm$role\">@color/task_form_${role.lowercase()}"))
            assertTrue(themes.contains("appColorTaskForm$role\">@color/hc_task_form_${role.lowercase()}"))
            assertTrue(nightThemes.contains("appColorTaskForm$role\">@color/task_form_${role.lowercase()}"))
            assertTrue(nightThemes.contains("appColorTaskForm$role\">@color/hc_task_form_${role.lowercase()}"))
        }
    }

    private fun assertActionBinding(
        source: String,
        view: String,
        speech: String,
        activation: String? = null
    ) {
        val viewIndex = source.indexOf("view = $view")
        assertTrue("Missing action binding for $view", viewIndex >= 0)
        val bindingStart = source.lastIndexOf("VoiceFirstGestureBinder.bindAction(", viewIndex)
        val nextBinding = source.indexOf("VoiceFirstGestureBinder.bindAction(", viewIndex + 1)
            .takeIf { it >= 0 } ?: source.length
        val binding = source.substring(bindingStart, nextBinding)
        assertTrue(binding.contains(speech))
        activation?.let { assertTrue(binding.contains(it)) }
    }
}
