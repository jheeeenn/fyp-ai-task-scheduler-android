package com.example.myapplication

import com.example.myapplication.data.TaskEntity

class TaskDetailNavigationCoordinator(
    private val speak: (String, (Boolean) -> Unit) -> Unit,
    private val openDetails: (TaskEntity) -> Unit
) {
    private var navigationPending = false
    private var requestGeneration = 0L

    fun request(task: TaskEntity): Boolean {
        if (navigationPending) return false
        navigationPending = true
        requestGeneration += 1
        val capturedGeneration = requestGeneration
        try {
            speak(TaskNavigationSpeechRenderer.openingDetails(task.title)) { speechSucceeded ->
                if (!navigationPending || capturedGeneration != requestGeneration) return@speak
                navigationPending = false
                if (speechSucceeded) openDetails(task)
            }
        } catch (_: RuntimeException) {
            navigationPending = false
            return false
        }
        return true
    }

    fun cancelPending() {
        requestGeneration += 1
        navigationPending = false
    }

    fun isPending(): Boolean = navigationPending
}
