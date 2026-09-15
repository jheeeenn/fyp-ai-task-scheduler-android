package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.ai.conversation.ConversationalScheduleValueRenderer
import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateDraftReadTarget
import com.example.myapplication.voice.CreateTaskDialogState
import java.util.Calendar

/** Renders Android-owned Create Task draft facts without sending those facts to an LLM. */
object CreateDraftReadResponseRenderer {
    fun render(
        target: CreateDraftReadTarget,
        title: String?,
        date: String?,
        time: String?,
        state: CreateTaskDialogState,
        pendingReplacementField: CreateDraftField?,
        baseCalendar: Calendar = Calendar.getInstance()
    ): String {
        val fact = when (target) {
            CreateDraftReadTarget.TITLE -> titleFact(title)
            CreateDraftReadTarget.DATE -> dateFact(date, baseCalendar)
            CreateDraftReadTarget.TIME -> timeFact(time)
            CreateDraftReadTarget.SCHEDULE -> scheduleFact(date, time, baseCalendar)
            CreateDraftReadTarget.SUMMARY -> summaryFact(title, date, time, baseCalendar)
        }
        return listOfNotNull(fact, continuation(state, pendingReplacementField)).joinToString(" ")
    }

    private fun titleFact(title: String?): String = title?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { "The current title is $it." }
        ?: "This task doesn't have a title yet."

    private fun dateFact(date: String?, baseCalendar: Calendar): String =
        spokenDate(date, baseCalendar)
            ?.let { "It's currently set for $it." }
            ?: "This task doesn't have a date set yet."

    private fun timeFact(time: String?): String = spokenTime(time)
        ?.let { "It's currently set for $it." }
        ?: "This task doesn't have a time set yet."

    private fun scheduleFact(date: String?, time: String?, baseCalendar: Calendar): String {
        val spokenDate = spokenDate(date, baseCalendar)
        val spokenTime = spokenTime(time)
        return when {
            spokenDate != null && spokenTime != null ->
                "It's currently scheduled for $spokenDate at $spokenTime."
            spokenDate != null -> "It's currently scheduled for $spokenDate, with no time set."
            spokenTime != null -> "It doesn't have a date set yet. Its current time is $spokenTime."
            else -> "This task doesn't have a date or time set yet."
        }
    }

    private fun summaryFact(
        title: String?,
        date: String?,
        time: String?,
        baseCalendar: Calendar
    ): String {
        val spokenTitle = title?.trim()?.takeIf(String::isNotEmpty) ?: "no title"
        val spokenDate = spokenDate(date, baseCalendar) ?: "no date"
        val spokenTime = spokenTime(time) ?: "no time"
        return "The current draft is $spokenTitle, $spokenDate, $spokenTime."
    }

    private fun spokenDate(date: String?, baseCalendar: Calendar): String? = date?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { ConversationalScheduleValueRenderer.date(it, baseCalendar) }

    private fun spokenTime(time: String?): String? = time?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { ConversationalScheduleValueRenderer.time(it) }

    private fun continuation(
        state: CreateTaskDialogState,
        pendingReplacementField: CreateDraftField?
    ): String? = when (state) {
        CreateTaskDialogState.IDLE,
        CreateTaskDialogState.WAITING_FOR_TITLE ->
            if (pendingReplacementField == CreateDraftField.TITLE) {
                "What should I change the title to?"
            } else {
                "What should I call this task?"
            }
        CreateTaskDialogState.WAITING_FOR_DATE ->
            if (pendingReplacementField == CreateDraftField.DATE) {
                "What date should I use instead?"
            } else {
                "What date would you like?"
            }
        CreateTaskDialogState.WAITING_FOR_TIME ->
            if (pendingReplacementField == CreateDraftField.TIME) {
                "What time should I use instead?"
            } else {
                "What time would you like?"
            }
        CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD -> "What would you like to change?"
        CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION -> "Would you like to save this task?"
        CreateTaskDialogState.READY_TO_SAVE -> null
    }
}
