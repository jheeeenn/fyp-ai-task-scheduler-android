package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TaskDetailRedesignSourceContractTest {
    private val main = File("src/main/java/com/example/myapplication/TaskDetailActivity.kt").readText()
    private val layout = File("src/main/res/layout/activity_task_detail.xml").readText()
    private val drawables = File("src/main/res/drawable")
    private val colors = File("src/main/res/values/colors.xml").readText()

    @Test
    fun editableFieldsAndInformationSurfacesKeepVoiceFirstBindingTypes() {
        listOf("titleSurface", "dateSurface", "timeSurface").forEach { surface ->
            assertTrue(bindingFor(surface).startsWith("VoiceFirstGestureBinder.bindAction"))
        }
        listOf("statusSurface", "subtaskProgressSurface").forEach { surface ->
            assertTrue(bindingFor(surface).startsWith("VoiceFirstGestureBinder.bindInformation"))
        }
        val subtaskRenderer = main.substringAfter("private fun renderSubtasks")
            .substringBefore("private fun readAll")
        assertTrue(subtaskRenderer.contains("VoiceFirstGestureBinder.bindInformation("))
        assertTrue(main.contains("titleSurface.setOnLongClickListener"))
        assertTrue(main.contains("dateSurface.setOnLongClickListener"))
        assertTrue(main.contains("timeSurface.setOnLongClickListener"))
    }

    @Test
    fun allFourActionsAndProtectedNavigationPathsKeepVoiceFirstBindings() {
        listOf(
            "readAllButton" to "::readAll",
            "toggleDoneButton" to "::toggleDone",
            "saveButton" to "::requestSave",
            "deleteButton" to "::handleDelete",
            "homeButton" to "::handleHomeExit",
            "assistantButton" to "::handleContextualAssistant"
        ).forEach { (view, activation) ->
            val binding = bindingFor(view)
            assertTrue(binding.startsWith("VoiceFirstGestureBinder.bindAction"))
            assertTrue(binding.contains("activate = $activation"))
        }
        assertTrue(main.contains("WAITING_FOR_HOME_CONFIRMATION"))
        assertTrue(main.contains("WAITING_FOR_ASSISTANT_EXIT_CONFIRMATION"))
    }

    @Test
    fun statusUsesFiveEstablishedFullSurfaceBackgrounds() {
        listOf(
            "OVERDUE" to "bg_task_status_overdue",
            "DUE_TODAY" to "bg_task_status_today",
            "UPCOMING" to "bg_task_status_upcoming",
            "COMPLETED" to "bg_task_status_completed",
            "UNSCHEDULED" to "bg_task_status_unscheduled"
        ).forEach { (status, background) ->
            assertTrue(main.contains("TaskVisualStatus.$status"))
            assertTrue(main.contains("R.drawable.$background"))
        }
        assertTrue(main.contains("statusSurface.setBackgroundResource(background)"))
        assertTrue(layout.contains("android:background=\"@drawable/bg_task_status_unscheduled\""))
        assertTrue(layout.contains("@+id/detailStatusText"))
    }

    @Test
    fun informationAndActionSizingMatchesTheTargetHierarchy() {
        listOf("detailTitleSurface", "detailDateSurface", "detailTimeSurface").forEach { id ->
            val surface = elementFor(id, "</LinearLayout>")
            assertTrue(surface.contains("android:minHeight=\"100dp\""))
            assertTrue(surface.contains("@drawable/bg_task_detail_surface"))
        }
        val date = elementFor("detailDateSurface", "</LinearLayout>")
        val time = elementFor("detailTimeSurface", "</LinearLayout>")
        assertTrue(date.contains("android:layout_marginEnd=\"7dp\""))
        assertTrue(time.contains("android:layout_marginStart=\"7dp\""))
        listOf("btnReadAll", "btnToggleDone", "btnSaveChanges", "btnDeleteTask").forEach { id ->
            val action = elementFor(id, "/>")
            assertTrue(action.contains("android:layout_height=\"wrap_content\""))
            assertTrue(action.contains("android:minHeight=\"80dp\""))
        }
        assertTrue(layout.contains("android:layout_marginTop=\"12dp\""))
        assertFalse(layout.contains("<Space"))
    }

    @Test
    fun bottomDockMatchesSharedOneToTwoNavigationPattern() {
        val dock = elementFor("taskDetailBottomDock", "</LinearLayout>")
        val home = dock.substringAfter("android:id=\"@+id/btnGoHome\"").substringBefore("/>")
        val assistant = dock.substringAfter("android:id=\"@+id/btnTalkAssistant\"").substringBefore("/>")

        assertTrue(dock.contains("android:layout_height=\"108dp\""))
        assertTrue(dock.contains("android:orientation=\"horizontal\""))
        assertTrue(home.contains("android:layout_weight=\"1\""))
        assertTrue(home.contains("@drawable/bg_task_list_home_action"))
        assertTrue(assistant.contains("android:layout_weight=\"2\""))
        assertTrue(assistant.contains("android:layout_marginStart=\"14dp\""))
        assertTrue(assistant.contains("@drawable/bg_task_list_assistant_action"))
        assertTrue(colors.contains("name=\"assistant_primary_action\">#FF0B67E8"))
    }

    @Test
    fun taskDetailVisualResourcesAreOpaqueAndHighContrastMapped() {
        listOf(
            "bg_task_detail_surface.xml",
            "bg_task_detail_action_read.xml",
            "bg_task_detail_action_mark_done.xml",
            "bg_task_detail_action_undo.xml",
            "bg_task_detail_action_save_clean.xml",
            "bg_task_detail_action_save_dirty.xml",
            "bg_task_detail_action_delete.xml",
            "bg_task_detail_unsaved.xml"
        ).forEach { fileName ->
            val drawable = drawables.resolve(fileName).readText()
            assertTrue("$fileName must have a solid fill", drawable.contains("<solid"))
            assertFalse("$fileName must not use alpha", drawable.contains("alpha"))
            assertFalse("$fileName must not use a gradient", drawable.contains("<gradient"))
        }
        listOf(
            "task_detail_surface",
            "task_detail_action_read",
            "task_detail_action_mark_done",
            "task_detail_action_undo",
            "task_detail_action_save_clean",
            "task_detail_action_save_dirty",
            "task_detail_action_delete",
            "task_detail_unsaved_surface",
            "hc_task_detail_surface",
            "hc_task_detail_outline",
            "hc_task_detail_action_text"
        ).forEach { name ->
            assertTrue(colors.contains("name=\"$name\">#FF"))
        }
    }

    @Test
    fun dirtyStateAndDraftEditingAuthorityRemainIntact() {
        assertTrue(layout.contains("@+id/detailUnsavedChanges"))
        assertTrue(layout.contains("@drawable/bg_task_detail_unsaved"))
        assertTrue(main.contains("unsavedChangesText.visibility = if (dirty)"))
        assertTrue(main.contains("R.drawable.bg_task_detail_action_save_dirty"))
        assertTrue(main.contains("R.drawable.bg_task_detail_action_save_clean"))
        listOf(
            "TaskDetailDraftController",
            "TaskDetailEditSemanticOrchestrator",
            "TaskFieldEditResolver",
            "freezeSaveClaim()",
            "updateTaskAndSubtasksIfAuthoritativeSnapshotMatches",
            "synchronizeReminderAfterSave"
        ).forEach { contract ->
            assertTrue(main.contains(contract))
        }
    }

    private fun bindingFor(view: String): String =
        main.split("VoiceFirstGestureBinder.")
            .drop(1)
            .map { "VoiceFirstGestureBinder.${it.substringBefore("\n        )")}" }
            .first { it.contains(view) }

    private fun elementFor(id: String, closing: String): String =
        layout.substringAfter("android:id=\"@+id/$id\"").substringBefore(closing)
}
