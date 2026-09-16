package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationalScheduleValueRenderer
import com.example.myapplication.ai.TaskQueryDetail

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
            ConversationContextDetail.DATE ->
                renderSchedule(title, item.dueDate, item.dueTime, TaskQueryDetail.DATE)
            ConversationContextDetail.TIME ->
                renderSchedule(title, item.dueDate, item.dueTime, TaskQueryDetail.TIME)
            ConversationContextDetail.DATE_TIME ->
                renderSchedule(title, item.dueDate, item.dueTime, TaskQueryDetail.DATE_TIME)
            ConversationContextDetail.STATUS -> {
                val status = if (item.isDone) "Completed" else item.relativeStatus
                "$title is ${status.replaceFirstChar { it.lowercase() }}."
            }
            ConversationContextDetail.SUBTASKS -> renderSubtasks(item, title)
            ConversationContextDetail.NONE ->
                throw IllegalArgumentException("A validated context detail is required")
        }
    }

    /** Shared factual speech from Android-owned values; requires no temporary ref. */
    fun renderSchedule(
        taskTitle: String,
        dueDate: String?,
        dueTime: String?,
        detail: TaskQueryDetail
    ): String {
        val title = taskTitle.ifBlank { "This task" }
        val date = dueDate.orEmpty()
        val time = spokenTime(dueTime)
        return when (detail) {
            TaskQueryDetail.DATE -> if (date.isBlank()) "It does not have a date."
                else "$title is scheduled for ${spokenDate(date)}."
            TaskQueryDetail.TIME -> if (time.isBlank()) "It does not have a time."
                else "$title is scheduled at $time."
            TaskQueryDetail.DATE_TIME -> when {
                date.isNotBlank() && time.isNotBlank() ->
                    "$title is scheduled for ${spokenDate(date)} at $time."
                date.isBlank() && time.isBlank() ->
                    "$title does not have a date or time set."
                date.isBlank() -> "$title has no date set. Its time is $time."
                else -> "$title is scheduled for ${spokenDate(date)}, with no time set."
            }
            TaskQueryDetail.NONE -> throw IllegalArgumentException("A schedule detail is required")
        }
    }

    private fun renderSummary(item: ReadOnlyTaskContextItem, title: String): String {
        val prefix = "The ${ordinalLabel(item.ref)} task was $title"
        return when {
            item.dueDate.isNotBlank() && item.dueTime.isNotBlank() ->
                "$prefix, scheduled for ${spokenDate(item.dueDate)} at ${spokenTime(item.dueTime)}."
            item.dueDate.isNotBlank() ->
                "$prefix, scheduled for ${spokenDate(item.dueDate)}. It does not have a time."
            item.dueTime.isNotBlank() ->
                "$prefix, scheduled at ${spokenTime(item.dueTime)}. It does not have a date."
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

    private fun spokenTime(time: String?): String =
        time?.takeIf { it.isNotBlank() }
            ?.let(ConversationalScheduleValueRenderer::time)
            .orEmpty()

    private val DATE_PATTERN = Regex("([0-9]{1,2})/([0-9]{1,2})/[0-9]{4}")
    private val MONTH_NAMES = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
}
