package com.example.myapplication.ai.temporal

import com.example.myapplication.ai.conversation.ConversationalScheduleValueRenderer
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

object RelativeTemporalSpeechRenderer {
    fun proposal(
        taskTitle: String,
        schedule: ExactTemporalSchedule,
        crossedDateBoundary: Boolean
    ): String {
        val destination = destination(schedule)
        val boundary = if (crossedDateBoundary) {
            " This crosses into another day."
        } else {
            ""
        }
        return "I will move $taskTitle $destination.$boundary Should I save the change?"
    }

    fun repeatedProposal(
        taskTitle: String,
        schedule: ExactTemporalSchedule,
        crossedDateBoundary: Boolean
    ): String = "The current proposal is to move $taskTitle ${destination(schedule)}." +
        if (crossedDateBoundary) " This crosses into another day." else ""

    fun pastSchedule(schedule: ExactTemporalSchedule): String =
        "That would move the task ${destination(schedule)}, which is in the past. " +
            "Please provide another change."

    fun calculationClarification(failure: RelativeTemporalCalculationFailure): String =
        when (failure) {
            RelativeTemporalCalculationFailure.MISSING_ORIGINAL_TIME,
            RelativeTemporalCalculationFailure.INVALID_STORED_TIME ->
                "That task does not have a valid time yet. What time should I use?"
            RelativeTemporalCalculationFailure.MISSING_ORIGINAL_DATE,
            RelativeTemporalCalculationFailure.INVALID_STORED_DATE ->
                "That task does not have a valid date yet. What date should I use?"
            RelativeTemporalCalculationFailure.REPLACEMENT_DATE_UNRESOLVED,
            RelativeTemporalCalculationFailure.REPLACEMENT_DATE_NEEDS_CLARIFICATION ->
                "What exact date would you like me to use?"
            RelativeTemporalCalculationFailure.REPLACEMENT_TIME_UNRESOLVED,
            RelativeTemporalCalculationFailure.REPLACEMENT_TIME_NEEDS_CLARIFICATION ->
                "What exact time would you like me to use?"
            RelativeTemporalCalculationFailure.CURRENT_PROPOSAL_UNAVAILABLE ->
                "There is no current schedule proposal to build on. Please state the change again."
            RelativeTemporalCalculationFailure.UNMENTIONED_FIELD_CHANGED ->
                "Please restate the schedule change so I can preserve the other proposed field."
            RelativeTemporalCalculationFailure.NO_EFFECTIVE_CHANGE ->
                "That would keep the same schedule. What different date, time, or offset should I use?"
        }

    fun semanticClarification(): String =
        "Please state one schedule change and specify the exact date, time, or amount earlier or later."

    private fun destination(schedule: ExactTemporalSchedule): String = when {
        schedule.date != null && schedule.time != null ->
            "to ${spokenDate(schedule.date)} at ${spokenTime(schedule.time)}"
        schedule.date != null -> "to ${spokenDate(schedule.date)}, keeping no task time"
        schedule.time != null -> "to ${spokenTime(schedule.time)}, keeping no task date"
        else -> "with no date or time"
    }

    private fun spokenTime(value: String): String =
        ConversationalScheduleValueRenderer.time(value)

    private fun spokenDate(value: String): String {
        val input = SimpleDateFormat("dd/MM/yyyy", Locale.UK).apply { isLenient = false }
        val position = ParsePosition(0)
        val parsed = input.parse(value, position)
        if (parsed == null || position.index != value.length) return value
        return SimpleDateFormat("d MMMM yyyy", Locale.UK).format(parsed)
    }
}
