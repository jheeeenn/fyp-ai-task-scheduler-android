package com.example.myapplication.ai.conversation.taskedit

import com.example.myapplication.TaskFormScheduleValueRenderer

/** Renders Android-owned Edit Task draft facts without delegating factual wording to an LLM. */
object EditTaskDraftReadResponseRenderer {
    fun render(
        move: EditTaskSemanticMove,
        title: String,
        date: String?,
        time: String?,
        interactionState: EditTaskInteractionState,
        pendingFieldTarget: EditFieldTarget
    ): String {
        val fact = when (move) {
            EditTaskSemanticMove.READ_TITLE -> renderTitle(title)
            EditTaskSemanticMove.READ_DATE -> renderDate(date)
            EditTaskSemanticMove.READ_TIME -> renderTime(time)
            EditTaskSemanticMove.READ_SCHEDULE -> renderSchedule(date, time)
            else -> throw IllegalArgumentException("A read-only Edit Task move is required")
        }
        return listOfNotNull(fact, continuation(interactionState, pendingFieldTarget))
            .joinToString(" ")
    }

    private fun renderTitle(title: String): String = title.trim()
        .takeIf(String::isNotEmpty)
        ?.let { "The current title is $it." }
        ?: "This task doesn't have a title yet."

    private fun renderDate(date: String?): String = date?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let {
            "It's currently set for ${TaskFormScheduleValueRenderer.date(it)}."
        }
        ?: "This task doesn't have a date set yet."

    private fun renderTime(time: String?): String = time?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let {
            "It's currently set for ${TaskFormScheduleValueRenderer.time(it)}."
        }
        ?: "This task doesn't have a time set yet."

    private fun renderSchedule(date: String?, time: String?): String {
        val spokenDate = date?.trim()?.takeIf(String::isNotEmpty)
            ?.let { TaskFormScheduleValueRenderer.date(it) }
        val spokenTime = time?.trim()?.takeIf(String::isNotEmpty)
            ?.let { TaskFormScheduleValueRenderer.time(it) }
        return when {
            spokenDate != null && spokenTime != null ->
                "It's currently scheduled for $spokenDate at $spokenTime."
            spokenDate != null ->
                "It's currently scheduled for $spokenDate, with no time set."
            spokenTime != null ->
                "It doesn't have a date set yet. Its current time is $spokenTime."
            else -> "This task doesn't have a date or time set yet."
        }
    }

    private fun continuation(
        interactionState: EditTaskInteractionState,
        pendingFieldTarget: EditFieldTarget
    ): String? = when (interactionState) {
        EditTaskInteractionState.WAITING_FOR_TITLE ->
            "What would you like the new title to be?"
        EditTaskInteractionState.WAITING_FOR_DATE ->
            "What date would you like instead?"
        EditTaskInteractionState.WAITING_FOR_TIME ->
            "What time would you like instead?"
        EditTaskInteractionState.WAITING_FOR_DATE_OR_TIME ->
            "What date or time would you like instead?"
        EditTaskInteractionState.WAITING_FOR_TEMPORAL_CLARIFICATION -> when (pendingFieldTarget) {
            EditFieldTarget.DATE -> "What exact date should I use for the change?"
            EditFieldTarget.TIME -> "What exact time should I use for the change?"
            else -> "What exact date or time should I use for the change?"
        }
        EditTaskInteractionState.WAITING_FOR_SAVE_CONFIRMATION ->
            "Would you like to save these changes?"
        EditTaskInteractionState.WAITING_FOR_RELATIVE_TEMPORAL_CONFIRMATION ->
            "Would you like to save this schedule?"
        EditTaskInteractionState.READY_FOR_EDIT,
        EditTaskInteractionState.WAITING_FOR_DELETE_CONFIRMATION,
        EditTaskInteractionState.OPERATION_IN_FLIGHT -> null
    }
}
