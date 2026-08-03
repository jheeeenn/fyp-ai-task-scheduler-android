package com.example.myapplication

object TaskListScreenSpeechRenderer {
    fun orientation(pageTitle: String, count: Int): String =
        "$pageTitle. ${countPhrase(count)} " +
            "Single tap an item to hear it. " +
            "Double tap to open or activate it. " +
            "Home and Assistant are at the bottom."

    fun refreshedCount(pageTitle: String, count: Int): String =
        "$pageTitle updated. ${countPhrase(count)}"

    fun countPhrase(count: Int): String = when (count.coerceAtLeast(0)) {
        0 -> "No tasks."
        1 -> "One task."
        2 -> "Two tasks."
        else -> "${count.coerceAtLeast(0)} tasks."
    }
}

class TaskListScreenSpeechState {
    private var lastSnapshot: Any? = null

    fun onAuthoritativeLoad(pageTitle: String, count: Int, snapshot: Any): String? {
        val previous = lastSnapshot
        lastSnapshot = snapshot
        return when {
            previous == null -> TaskListScreenSpeechRenderer.orientation(pageTitle, count)
            previous != snapshot -> TaskListScreenSpeechRenderer.refreshedCount(pageTitle, count)
            else -> null
        }
    }
}

object TaskDetailScreenSpeechRenderer {
    fun entry(title: String): String =
        "Task details for ${title.ifBlank { "Untitled task" }}. " +
            "Single tap information or buttons to hear them. " +
            "Double tap a button to activate it."

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
    fun homeDescription(): String = "Home. Double tap to return to the Home screen."
    fun returningHome(): String = "Returning Home."
    fun assistantDescription(): String =
        "Assistant. Double tap to open the voice assistant."
    fun openingAssistant(): String = "Opening Assistant."
    fun readAllDescription(): String =
        "Read all. Double tap to hear the complete task details."
    fun toggleDescription(isDone: Boolean): String = if (isDone) {
        "Undo completion. Double tap to mark this task incomplete."
    } else {
        "Mark task complete. Double tap to mark this task complete."
    }
    fun editDescription(): String = "Edit task. Double tap to open the task editor."
    fun openingTaskEditor(): String = "Opening task editor."
    fun deleteDescription(): String =
        "Delete task. Double tap to begin voice confirmation."
    fun openingDeleteConfirmation(): String =
        "Opening the assistant to confirm deletion."
    fun taskAssistantDescription(): String =
        "Assistant for this task. Double tap to ask about this task."
    fun openingTaskAssistant(title: String): String =
        "Opening the assistant for ${title.ifBlank { "this task" }}."
}
