package com.example.myapplication

import android.content.Intent

enum class HomeAssistantEntryMode {
    GENERIC,
    TASK_DETAIL_CONTEXT,
    TASK_DETAIL_DELETE_CONFIRMATION
}

data class HomeAssistantEntry(
    val openAssistant: Boolean,
    val entryMode: HomeAssistantEntryMode,
    val contextTaskId: Long?
)

object HomeAssistantEntryContract {
    const val EXTRA_OPEN_ASSISTANT = "open_assistant_on_arrival"
    const val EXTRA_CONTEXT_TASK_ID = "assistant_context_task_id"
    const val EXTRA_ENTRY_MODE = "assistant_entry_mode"

    fun putGeneric(intent: Intent, openAssistant: Boolean = true): Intent = intent.apply {
        putExtra(EXTRA_OPEN_ASSISTANT, openAssistant)
        putExtra(EXTRA_ENTRY_MODE, HomeAssistantEntryMode.GENERIC.name)
        removeExtra(EXTRA_CONTEXT_TASK_ID)
    }

    fun putTaskDetail(
        intent: Intent,
        taskId: Long,
        entryMode: HomeAssistantEntryMode
    ): Intent = intent.apply {
        require(
            entryMode == HomeAssistantEntryMode.TASK_DETAIL_CONTEXT ||
                entryMode == HomeAssistantEntryMode.TASK_DETAIL_DELETE_CONFIRMATION
        )
        putExtra(EXTRA_OPEN_ASSISTANT, true)
        putExtra(EXTRA_CONTEXT_TASK_ID, taskId)
        putExtra(EXTRA_ENTRY_MODE, entryMode.name)
    }

    fun read(intent: Intent): HomeAssistantEntry? {
        if (!intent.hasExtra(EXTRA_OPEN_ASSISTANT) &&
            !intent.hasExtra(EXTRA_CONTEXT_TASK_ID) &&
            !intent.hasExtra(EXTRA_ENTRY_MODE)
        ) {
            return null
        }
        val mode = intent.getStringExtra(EXTRA_ENTRY_MODE)
            ?.let { stored ->
                HomeAssistantEntryMode.entries.firstOrNull { it.name == stored }
            }
            ?: HomeAssistantEntryMode.GENERIC
        val taskId = intent.takeIf { it.hasExtra(EXTRA_CONTEXT_TASK_ID) }
            ?.getLongExtra(EXTRA_CONTEXT_TASK_ID, -1L)
            ?.takeIf { it > 0L }
        return HomeAssistantEntry(
            openAssistant = intent.getBooleanExtra(EXTRA_OPEN_ASSISTANT, false),
            entryMode = mode,
            contextTaskId = taskId
        )
    }

    fun consume(intent: Intent) {
        intent.removeExtra(EXTRA_OPEN_ASSISTANT)
        intent.removeExtra(EXTRA_CONTEXT_TASK_ID)
        intent.removeExtra(EXTRA_ENTRY_MODE)
    }
}
