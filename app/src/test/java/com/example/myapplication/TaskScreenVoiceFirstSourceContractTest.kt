package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TaskScreenVoiceFirstSourceContractTest {
    private val root = File("src/main/java/com/example/myapplication")

    @Test
    fun taskListsSpeakAuthoritativeEntryAndChangedCounts() {
        listOf("MainActivity.kt", "TodayTasksActivity.kt").forEach { filename ->
            val source = root.resolve(filename).readText()
            val load = source.substringAfter("private fun loadTasks()")
                .substringBefore("private fun navigateToTaskDetails")

            assertOrdered(
                load,
                "withContext(Dispatchers.IO)",
                "adapter.setTasksWithSubtasks",
                "accessibilityController.render",
                "screenSpeechState.onAuthoritativeLoad",
                "?.let(::speakIdentification)"
            )
        }
    }

    @Test
    fun listHomeAndAssistantIdentifyThenUseSpeechGatedNavigation() {
        listOf("MainActivity.kt", "TodayTasksActivity.kt").forEach { filename ->
            val source = root.resolve(filename).readText()

            assertTrue(source.contains("speechProvider = TaskScreenControlSpeechRenderer::homeDescription"))
            assertTrue(source.contains("TaskScreenControlSpeechRenderer.returningHome()"))
            assertTrue(source.contains("speechProvider = TaskScreenControlSpeechRenderer::assistantDescription"))
            assertTrue(source.contains("TaskScreenControlSpeechRenderer.openingAssistant()"))
            assertTrue(source.contains("HomeAssistantEntryContract.putGeneric"))
            assertTrue(source.contains("Intent.FLAG_ACTIVITY_CLEAR_TOP"))
            assertTrue(source.contains("Intent.FLAG_ACTIVITY_SINGLE_TOP"))
            assertTrue(source.contains("navigationCoordinator.cancelPending()"))
            val identification = source.substringAfter("private fun speakIdentification")
                .substringBefore("private fun navigateToTaskDetails")
            assertOrdered(
                identification,
                "navigationCoordinator.cancelPending()",
                "voiceHelper.speak(text)"
            )
        }
    }

    @Test
    fun taskCardsKeepDynamicSingleReadAndAuthoritativeDoubleOpen() {
        val adapter = root.resolve("TaskAdapter.kt").readText()
        val scheduled = root.resolve("MainActivity.kt").readText()
        val today = root.resolve("TodayTasksActivity.kt").readText()

        assertTrue(adapter.contains("speechProvider = { currentCardPresentation(holder)?.spokenSummary }"))
        assertTrue(adapter.contains("onOpenTask(tasks[clickedPosition])"))
        assertTrue(adapter.contains("VoiceFirstGestureBinder.bindAction"))
        listOf(scheduled, today).forEach { source ->
            assertTrue(source.contains("TaskNavigationSpeechRenderer.openingDetails(task.title)"))
            assertTrue(source.contains("TaskDetailActivity.EXTRA_TASK_ID"))
        }
    }

    @Test
    fun taskDetailEntryAndInformationUseAuthoritativeCurrentState() {
        val detail = root.resolve("TaskDetailActivity.kt").readText()

        assertTrue(detail.contains("screenSpeechState.onAuthoritativeLoad(snapshot.first.title, snapshot)"))
        assertTrue(detail.contains("screenSpeechState.synchronize(refreshed)"))
        listOf(
            "titleSurface",
            "statusSurface",
            "dateSurface",
            "timeSurface",
            "subtaskProgressSurface",
            "subtaskView"
        ).forEach { view ->
            assertTrue(
                Regex("VoiceFirstGestureBinder\\.bindInformation\\(\\s*$view,")
                    .containsMatchIn(detail)
            )
        }
        assertTrue(detail.contains("currentTask?.let { TaskDetailSpeechRenderer.title(it.title) }"))
        assertTrue(detail.contains("TaskDetailSpeechRenderer.subtask(subtask.title, subtask.isDone)"))
        assertFalse(detail.contains("contentDescription = subtask.id"))
    }

    @Test
    fun readAllToggleEditDeleteHomeAndAssistantUseExpectedContracts() {
        val detail = root.resolve("TaskDetailActivity.kt").readText()

        assertTrue(detail.contains("speechProvider = TaskScreenControlSpeechRenderer::readAllDescription"))
        assertTrue(detail.contains("activate = ::readAll"))
        assertTrue(detail.contains("TaskScreenControlSpeechRenderer.toggleDescription(it.isDone)"))
        assertTrue(detail.contains("activate = ::toggleDone"))
        assertTrue(detail.contains("TaskScreenControlSpeechRenderer.openingTaskEditor()"))
        assertTrue(detail.contains("::editTask"))
        assertTrue(detail.contains("TaskScreenControlSpeechRenderer.openingDeleteConfirmation()"))
        assertTrue(detail.contains("HomeAssistantEntryMode.TASK_DETAIL_DELETE_CONFIRMATION"))
        assertTrue(detail.contains("TaskScreenControlSpeechRenderer.returningHome()"))
        assertTrue(detail.contains("TaskScreenControlSpeechRenderer.openingTaskAssistant(title)"))
        assertTrue(detail.contains("HomeAssistantEntryMode.TASK_DETAIL_CONTEXT"))
        assertFalse(detail.contains("AlertDialog"))
    }

    @Test
    fun unavailableTaskIsSpokenBeforeThePageFinishes() {
        val detail = root.resolve("TaskDetailActivity.kt").readText()
        val unavailable = detail.substringAfter("private fun showMissingTaskAndFinish")
            .substringBefore("private fun dp")

        assertOrdered(
            unavailable,
            "voiceHelper.speakWithResult",
            "runOnUiThread",
            "finish()"
        )
        assertTrue(unavailable.contains("task_no_longer_available_spoken"))
    }

    @Test
    fun newerIdentificationSpeechCancelsStaleDetailNavigation() {
        val detail = root.resolve("TaskDetailActivity.kt").readText()
        val identification = detail.substringAfter("private fun speakIdentification")
            .substringBefore("private fun toggleDone")

        assertOrdered(
            identification,
            "navigationCoordinator.cancelPending()",
            "voiceHelper.speak(text)"
        )
    }

    private fun assertOrdered(source: String, vararg markers: String) {
        var previous = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker)
            assertTrue("Missing or out-of-order marker: $marker", index > previous)
            previous = index
        }
    }
}
