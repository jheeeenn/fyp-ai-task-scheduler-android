package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoiceFirstGestureSourceContractTest {
    private val root = File("src/main/java/com/example/myapplication")

    @Test
    fun binderUsesGestureDetectorWithoutActionUpActivationOrManualTimestamps() {
        val binder = root.resolve("VoiceFirstGestureBinder.kt").readText()

        assertTrue(binder.contains("GestureDetector"))
        assertTrue(binder.contains("onSingleTapConfirmed"))
        assertTrue(binder.contains("onDoubleTap"))
        assertTrue(binder.contains("onLongPress"))
        assertTrue(binder.contains("onScroll"))
        assertFalse(binder.contains("ACTION_UP"))
        assertFalse(binder.contains("System.currentTimeMillis"))
    }

    @Test
    fun actionBindingPreservesPassiveClickAndHasNoClickHapticDuplication() {
        val binder = root.resolve("VoiceFirstGestureBinder.kt").readText()
        val action = binder.substringAfter("fun bindAction(")
            .substringBefore("fun bindInformation(")
        val passiveClick = action.substringAfter("view.setOnClickListener")
            .substringBefore("bind(")

        assertTrue(passiveClick.contains("activate()"))
        assertFalse(passiveClick.contains("Haptic"))
        assertTrue(action.contains("doubleTapHaptic?.invoke(view)"))
        assertTrue(action.contains("view.performClick()"))
        assertEqualsOnce(action, "doubleTapHaptic?.invoke(view)")
    }

    @Test
    fun longPressForwardsExactlyOnceAndScrollReturnsFalse() {
        val binder = root.resolve("VoiceFirstGestureBinder.kt").readText()
        val interaction = root.resolve("VoiceFirstTouchInteraction.kt").readText()

        assertEqualsOnce(
            binder.substringAfter("fun bindAction(").substringBefore("fun bindInformation("),
            "view.performLongClick()"
        )
        assertTrue(interaction.contains("fun onScroll(): Boolean = false"))
    }

    @Test
    fun taskScreenActionsAllUseSharedBindingAndToggleSkipsPreMutationConfirmationHaptic() {
        val scheduled = root.resolve("MainActivity.kt").readText()
        val today = root.resolve("TodayTasksActivity.kt").readText()
        val detail = root.resolve("TaskDetailActivity.kt").readText()

        assertTrue(scheduled.countOccurrences("VoiceFirstGestureBinder.bindAction") >= 2)
        assertTrue(today.countOccurrences("VoiceFirstGestureBinder.bindAction") >= 2)
        assertTrue(detail.countOccurrences("VoiceFirstGestureBinder.bindAction") >= 6)
        val toggleBinding = detail.substringAfter("toggleDoneButton,")
            .substringBefore("VoiceFirstGestureBinder.bindAction(")
        assertTrue(toggleBinding.contains("doubleTapHaptic = null"))
    }

    private fun assertEqualsOnce(source: String, marker: String) {
        assertTrue(source.indexOf(marker) >= 0)
        assertTrue(source.indexOf(marker) == source.lastIndexOf(marker))
    }

    private fun String.countOccurrences(value: String): Int =
        windowed(value.length).count { it == value }
}
