package com.example.myapplication

import com.example.myapplication.data.TaskEntity

object TaskListScreenSpeechRenderer {
    fun scheduled(activeCount: Int): String = when (val count = activeCount.coerceAtLeast(0)) {
        0 -> "You have no active scheduled tasks."
        1 -> "You have 1 active scheduled task."
        else -> "You have $count active scheduled tasks."
    }

    fun today(activeCount: Int): String = when (val count = activeCount.coerceAtLeast(0)) {
        0 -> "You have no tasks today."
        1 -> "You have 1 task today."
        else -> "You have $count tasks today."
    }

    fun activeCount(tasks: List<TaskEntity>): Int =
        tasks.count { !it.isDone }
}

object TaskDetailScreenSpeechRenderer {
    fun entry(title: String): String =
        "Task details for ${title.ifBlank { "Untitled task" }}."

    fun updated(): String = "Task details updated."
}

class TaskDetailScreenSpeechState {
    private var lastSnapshot: Any? = null

    fun onAuthoritativeLoad(title: String, snapshot: Any): String? {
        val previous = lastSnapshot
        lastSnapshot = snapshot
        return when {
            previous == null -> TaskDetailScreenSpeechRenderer.entry(title)
            previous != snapshot -> TaskDetailScreenSpeechRenderer.updated()
            else -> null
        }
    }

    fun synchronize(snapshot: Any) {
        lastSnapshot = snapshot
    }
}

object TaskScreenControlSpeechRenderer {
    fun homeDescription(): String = "Home"
    fun returningHome(): String = "Returning home."
    fun goingBack(): String = "Going back."
    fun assistantDescription(): String = "Talk to Assistant"
    fun openingAssistant(): String = "Opening assistant."
    fun readAllDescription(): String = "Read All"
    fun saveDescription(): String = "Save"
    fun toggleDescription(isDone: Boolean): String = if (isDone) {
        "Undo"
    } else {
        "Mark Done"
    }
    fun editDescription(): String = "Edit"
    fun openingTaskEditor(): String = "Opening task editor."
    fun deleteDescription(): String = "Delete"
    fun openingDeleteConfirmation(): String = "Opening assistant to confirm deletion."
    fun taskAssistantDescription(): String = "Talk to Assistant"
    fun openingTaskAssistant(title: String): String =
        "Opening assistant for ${title.ifBlank { "this task" }}."
}

object TaskDetailEditSpeechRenderer {
    fun askTitle(): String = "What title would you like to use?"
    fun askDate(): String = "What date would you like to use?"
    fun askTime(): String = "What time would you like to use?"
    fun titleChanged(title: String): String = "Title changed to $title. Not saved."
    fun dateChanged(date: String?): String =
        "Date changed to ${com.example.myapplication.accessibility.TaskCardAccessibilitySemantics.spokenDate(date)}. Not saved."
    fun timeChanged(time: String?): String =
        "Time changed to ${com.example.myapplication.accessibility.TaskCardAccessibilitySemantics.spokenTime(time)}. Not saved."
    fun scheduleChanged(
        oldDate: String?,
        oldTime: String?,
        newDate: String?,
        newTime: String?
    ): String = when {
        oldDate != newDate && oldTime != newTime ->
            "Date changed to ${com.example.myapplication.accessibility.TaskCardAccessibilitySemantics.spokenDate(newDate)} " +
                "and time changed to ${com.example.myapplication.accessibility.TaskCardAccessibilitySemantics.spokenTime(newTime)}. Not saved."
        oldDate != newDate -> dateChanged(newDate)
        else -> timeChanged(newTime)
    }
    fun pastSameDayQuestion(time: String): String =
        "${com.example.myapplication.accessibility.TaskCardAccessibilitySemantics.spokenTime(time)} today has already passed. " +
            "Did you mean tomorrow at ${com.example.myapplication.accessibility.TaskCardAccessibilitySemantics.spokenTime(time)}?"
    fun askDateAndTime(): String = "What date and time would you like to use?"
    fun confirmSave(title: String): String = "Save changes to ${title.ifBlank { "this task" }}?"
    fun confirmHomeExit(): String =
        "You have unsaved changes. Would you like to save them before returning home?"
    fun confirmBackExit(): String =
        "You have unsaved changes. Would you like to save them before going back?"
    fun confirmAssistantExit(): String =
        "You have unsaved changes. Would you like to save them before opening the assistant?"
    fun confirmDeleteDiscard(): String =
        "Unsaved changes will be discarded if this task is deleted. Continue?"

    fun retryQuestion(interaction: TaskDetailEditInteraction): String = when (interaction) {
        TaskDetailEditInteraction.WAITING_FOR_TITLE ->
            "I couldn't use that title. What title would you like to use?"
        TaskDetailEditInteraction.WAITING_FOR_DATE ->
            "I couldn't understand that date. What date would you like to use?"
        TaskDetailEditInteraction.WAITING_FOR_TIME ->
            "I couldn't understand that time. What time would you like to use?"
        TaskDetailEditInteraction.WAITING_FOR_PAST_TIME_CONFIRMATION ->
            error("Past-time confirmation repeats its guarded pending question")
        TaskDetailEditInteraction.WAITING_FOR_SAVE_CONFIRMATION ->
            "I didn't catch that. Would you like to save the changes?\n" +
                "Please say yes, no, or cancel."
        TaskDetailEditInteraction.WAITING_FOR_HOME_CONFIRMATION ->
            "I didn't catch that. Would you like to save the changes before returning home?\n" +
                "Please say yes, no, or cancel."
        TaskDetailEditInteraction.WAITING_FOR_BACK_CONFIRMATION ->
            "I didn't catch that. Would you like to save the changes before going back?\n" +
                "Please say yes, no, or cancel."
        TaskDetailEditInteraction.WAITING_FOR_ASSISTANT_EXIT_CONFIRMATION ->
            "I didn't catch that. Would you like to save the changes before opening the assistant?\n" +
                "Please say yes, no, or cancel."
        TaskDetailEditInteraction.WAITING_FOR_DELETE_DISCARD_CONFIRMATION ->
            "I didn't catch that. Continue and discard the unsaved changes?\n" +
                "Please say yes, no, or cancel."
        TaskDetailEditInteraction.IDLE,
        TaskDetailEditInteraction.SAVING -> error("No retry question for $interaction")
    }

    fun pastScheduleRetry(interaction: TaskDetailEditInteraction): String = when (interaction) {
        TaskDetailEditInteraction.WAITING_FOR_DATE ->
            "That date and time would be in the past. What date would you like to use instead?"
        TaskDetailEditInteraction.WAITING_FOR_TIME ->
            "That time would be in the past. What time would you like to use instead?"
        else -> error("No past-schedule retry for $interaction")
    }
}
