package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextDetail

object ReadOnlyTaskContextResponseRenderer {
    fun render(
        item: ReadOnlyTaskContextItem,
        detail: ConversationContextDetail
    ): String {
        val title = item.title.ifBlank { "This task" }
        return when (detail) {
            ConversationContextDetail.SUMMARY -> renderSummary(item, title)
            ConversationContextDetail.TITLE ->
                "The ${ordinalLabel(item.ref)} task was $title."
            ConversationContextDetail.DATE -> if (item.dueDate.isBlank()) {
                "It does not have a date."
            } else {
                "$title is scheduled for ${spokenDate(item.dueDate)}."
            }
            ConversationContextDetail.TIME -> if (item.dueTime.isBlank()) {
                "It does not have a time."
            } else {
                "$title is scheduled at ${item.dueTime}."
            }
            ConversationContextDetail.STATUS -> {
                val status = if (item.isDone) "Completed" else item.relativeStatus
                "$title is ${status.replaceFirstChar { it.lowercase() }}."
            }
            ConversationContextDetail.SUBTASKS -> renderSubtasks(item, title)
            ConversationContextDetail.NONE ->
                throw IllegalArgumentException("A validated context detail is required")
        }
    }

    private fun renderSummary(item: ReadOnlyTaskContextItem, title: String): String {
        val prefix = "The ${ordinalLabel(item.ref)} task was $title"
        return when {
            item.dueDate.isNotBlank() && item.dueTime.isNotBlank() ->
                "$prefix, scheduled for ${spokenDate(item.dueDate)} at ${item.dueTime}."
            item.dueDate.isNotBlank() ->
                "$prefix, scheduled for ${spokenDate(item.dueDate)}. It does not have a time."
            item.dueTime.isNotBlank() ->
                "$prefix, scheduled at ${item.dueTime}. It does not have a date."
            else -> "$prefix. It does not have a date or time."
        }
    }

    private fun renderSubtasks(item: ReadOnlyTaskContextItem, title: String): String {
        if (item.subtaskCount == 0) return "It has no subtasks."
        return "$title has ${countLabel(item.subtaskCount)} " +
            "${if (item.subtaskCount == 1) "subtask" else "subtasks"}, with " +
            "${countLabel(item.unfinishedSubtaskCount)} unfinished."
    }

    private fun ordinalLabel(ref: String): String = when (ref.drop(1).toIntOrNull()) {
        1 -> "first"
        2 -> "second"
        3 -> "third"
        4 -> "fourth"
        5 -> "fifth"
        6 -> "sixth"
        7 -> "seventh"
        8 -> "eighth"
        else -> "selected"
    }

    private fun countLabel(count: Int): String = when (count) {
        0 -> "none"
        1 -> "one"
        2 -> "two"
        3 -> "three"
        4 -> "four"
        5 -> "five"
        6 -> "six"
        7 -> "seven"
        8 -> "eight"
        else -> count.toString()
    }

    private fun spokenDate(date: String): String {
        val match = DATE_PATTERN.matchEntire(date) ?: return date
        val day = match.groupValues[1].toIntOrNull() ?: return date
        val month = match.groupValues[2].toIntOrNull() ?: return date
        val monthName = MONTH_NAMES.getOrNull(month - 1) ?: return date
        return "$day $monthName"
    }

    private val DATE_PATTERN = Regex("([0-9]{1,2})/([0-9]{1,2})/[0-9]{4}")
    private val MONTH_NAMES = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
}
