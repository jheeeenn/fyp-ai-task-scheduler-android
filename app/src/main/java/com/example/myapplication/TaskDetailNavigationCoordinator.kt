package com.example.myapplication

import com.example.myapplication.data.TaskEntity

class TaskDetailNavigationCoordinator(
    speak: (String, (Boolean) -> Unit) -> Unit,
    private val openDetails: (TaskEntity) -> Unit
) {
    private val navigation = VoiceFirstNavigationCoordinator(speak)

    fun request(task: TaskEntity): Boolean = navigation.request(
        announcement = TaskNavigationSpeechRenderer.openingDetails(task.title),
        navigate = { openDetails(task) }
    )

    fun cancelPending() = navigation.cancelPending()

    fun isPending(): Boolean = navigation.isPending()
}
