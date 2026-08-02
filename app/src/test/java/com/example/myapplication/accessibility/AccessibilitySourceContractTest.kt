package com.example.myapplication.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AccessibilitySourceContractTest {
    private val layoutRoot = File("src/main/res/layout")
    private val mainRoot = File("src/main/java/com/example/myapplication")

    @Test
    fun taskCardIsOneFocusableClickableNodeWithProgrammaticSelection() {
        val layout = layoutRoot.resolve("item_task.xml").readText()
        val adapter = mainRoot.resolve("TaskAdapter.kt").readText()

        assertTrue(layout.contains("android:focusable=\"true\""))
        assertTrue(layout.contains("android:clickable=\"true\""))
        assertTrue(layout.contains("android:importantForAccessibility=\"yes\""))
        assertTrue(layout.countOccurrences("android:importantForAccessibility=\"no\"") >= 6)
        assertTrue(adapter.contains("holder.itemView.isSelected = isSelected"))
        assertTrue(adapter.contains("TaskCardAccessibilitySemantics.summary"))
        assertTrue(adapter.contains("onTaskSelected(tasks.find"))
        assertFalse(adapter.contains("contentDescription = task.id"))
    }

    @Test
    fun dateAndTimeStateDescriptionsUpdateAfterSelection() {
        val create = mainRoot.resolve("CreateTaskActivity.kt").readText()
        val edit = mainRoot.resolve("EditTaskActivity.kt").readText()

        assertTrue(create.substringAfter("private fun acceptExactDate").substringBefore("private fun applySpokenTime").contains("updateDateAccessibilityState()"))
        assertTrue(create.substringAfter("private fun acceptExactMinute").substringBefore("private fun advanceTemporalClarification").contains("updateTimeAccessibilityState()"))
        assertTrue(edit.substringAfter("private fun setExactDate").substringBefore("private fun applySpokenTime").contains("updateDateAccessibilityState()"))
        assertTrue(edit.substringAfter("private fun setExactMinute").substringBefore("private fun setSelectedSchedule").contains("updateTimeAccessibilityState()"))
        assertTrue(create.contains("AccessibilityStateHelper.updateStateDescription"))
        assertTrue(edit.contains("AccessibilityStateHelper.updateStateDescription"))
    }

    @Test
    fun headingsLabelsEmptyStateAndTouchTargetsAreDeclared() {
        val home = layoutRoot.resolve("activity_home.xml").readText()
        val create = layoutRoot.resolve("activity_create_task.xml").readText()
        val edit = layoutRoot.resolve("activity_edit_task.xml").readText()
        val list = layoutRoot.resolve("activity_task_list.xml").readText()
        val assistant = layoutRoot.resolve("bottomsheet_assistant.xml").readText()
        val strings = File("src/main/res/values/strings.xml").readText()

        assertTrue(home.contains("android:accessibilityHeading=\"true\""))
        assertTrue(create.contains("android:accessibilityHeading=\"true\""))
        assertTrue(edit.contains("android:accessibilityHeading=\"true\""))
        assertTrue(list.contains("android:accessibilityHeading=\"true\""))
        assertTrue(assistant.contains("android:accessibilityHeading=\"true\""))
        assertTrue(
            mainRoot.resolve("accessibility/AccessibilityStateHelper.kt").readText()
                .contains("ViewCompat.setAccessibilityHeading(view, true)")
        )
        assertTrue(create.contains("android:labelFor=\"@id/etTaskTitle\""))
        assertTrue(edit.contains("android:labelFor=\"@id/etTaskTitle\""))
        assertTrue(list.contains("@+id/emptyTaskText"))
        assertTrue(list.contains("@string/no_scheduled_tasks"))
        assertTrue(strings.contains("No scheduled tasks"))

        listOf(home, create, edit, list, assistant).forEach { xml ->
            assertTrue(xml.contains("android:minHeight=\""))
        }
    }

    @Test
    fun primaryButtonsCanGrowInsteadOfClippingAtFixedHeights() {
        val layouts = listOf(
            "activity_home.xml",
            "activity_create_task.xml",
            "activity_edit_task.xml",
            "activity_task_list.xml"
        ).map { layoutRoot.resolve(it).readText() }

        layouts.forEach { xml ->
            assertFalse(xml.contains("android:layout_height=\"104dp\""))
            assertFalse(xml.contains("android:layout_height=\"72dp\""))
            assertFalse(xml.contains("android:layout_height=\"70dp\""))
            assertFalse(xml.contains("android:layout_height=\"112dp\""))
            Regex("<Button[\\s\\S]*?/>").findAll(xml).forEach { button ->
                val minimum = Regex("android:minHeight=\"(\\d+)dp\"")
                    .find(button.value)
                    ?.groupValues
                    ?.get(1)
                    ?.toInt()
                assertTrue("Every primary button needs a declared touch target", minimum != null)
                assertTrue("Primary button touch targets must be at least 48dp", minimum!! >= 48)
            }
        }
    }

    @Test
    fun voiceTouchTalkBackAndTypedPathsRemainAvailableWithoutAccessibilityAi() {
        val home = mainRoot.resolve("HomeActivity.kt").readText()
        val create = mainRoot.resolve("CreateTaskActivity.kt").readText()
        val edit = mainRoot.resolve("EditTaskActivity.kt").readText()
        val panel = layoutRoot.resolve("bottomsheet_assistant.xml").readText()
        val accessibilityProduction = mainRoot.resolve("accessibility").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n", transform = File::readText)

        listOf(home, create, edit).forEach { activity ->
            assertTrue(activity.contains("btnTalkAssistant.setOnClickListenerWithHaptic"))
            assertTrue(activity.contains("btnTalkAssistant.setOnLongClickListener"))
            assertTrue(activity.contains("showTypedAssistantInputDialog"))
        }
        assertTrue(panel.contains("@+id/btnTypeAssistantInput"))
        assertTrue(panel.contains("@+id/btnStopAssistant"))
        assertFalse(accessibilityProduction.contains("Gemini"))
        assertFalse(accessibilityProduction.contains("AgentOrchestrator"))
        assertFalse(accessibilityProduction.contains("Regex("))
    }

    private fun String.countOccurrences(needle: String): Int =
        windowed(needle.length).count { it == needle }
}
