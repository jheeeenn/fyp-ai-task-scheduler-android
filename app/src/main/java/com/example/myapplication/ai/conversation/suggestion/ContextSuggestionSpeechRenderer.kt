package com.example.myapplication.ai.conversation.suggestion

import com.example.myapplication.data.TaskEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object ContextSuggestionSpeechRenderer {
    fun render(
        decision: ContextSuggestionDecision,
        snapshot: ContextSuggestionSnapshot,
        primaryTask: TaskEntity? = null,
        secondaryTask: TaskEntity? = null,
        firstUnfinishedSubtask: TaskEntity? = null,
        now: Calendar = Calendar.getInstance()
    ): String = when (decision.suggestionType) {
        ContextSuggestionType.FOCUS_TASK -> {
            val task = requireNotNull(primaryTask)
            "A good next task is ${spokenTitle(task.title)}. " +
                scheduleReason(task, decision.primaryRef, snapshot, now)
        }
        ContextSuggestionType.CONTINUE_SUBTASK -> {
            val task = requireNotNull(primaryTask)
            val subtask = requireNotNull(firstUnfinishedSubtask)
            "To make progress on ${spokenTitle(task.title)}, continue with " +
                "${spokenTitle(subtask.title)}. " +
                scheduleReason(task, decision.primaryRef, snapshot, now)
        }
        ContextSuggestionType.BREAK_DOWN_TASK -> {
            val task = requireNotNull(primaryTask)
            val title = spokenTitle(task.title)
            "You may want to break down $title into smaller steps. " +
                "Say, 'Break down $title,' when you are ready."
        }
        ContextSuggestionType.REVIEW_CLOSE_SCHEDULE -> {
            val first = requireNotNull(primaryTask)
            val second = requireNotNull(secondaryTask)
            val pair = requireNotNull(
                snapshot.closePair(decision.primaryRef, decision.secondaryRef)
            )
            val gapWording = if (pair.gapMinutes == 0) {
                "at the same time"
            } else {
                "${pair.gapMinutes} minutes apart"
            }
            "Review ${spokenTitle(first.title)} and ${spokenTitle(second.title)}. " +
                "They are scheduled $gapWording on ${spokenDate(pair.dueDate)}."
        }
        ContextSuggestionType.NO_SUGGESTION ->
            "You do not have an active task for me to suggest right now."
    }

    private fun scheduleReason(
        task: TaskEntity,
        selectedRef: String,
        snapshot: ContextSuggestionSnapshot,
        now: Calendar
    ): String {
        val category = ContextSuggestionSnapshotBuilder.attentionFor(task, now)
        val date = spokenDate(task.dueDate.orEmpty())
        val time = ContextSuggestionSnapshotBuilder.parseTimeMinute(task.dueTime)
            ?.let(::spokenTime)
        return when (category) {
            ContextSuggestionAttentionCategory.OVERDUE ->
                "It is overdue since $date${time?.let { " at $it" }.orEmpty()}."
            ContextSuggestionAttentionCategory.DUE_TODAY ->
                "It is due today${time?.let { " at $it" }.orEmpty()}."
            ContextSuggestionAttentionCategory.UPCOMING -> {
                val firstUpcomingRef = snapshot.candidates.firstOrNull {
                    it.attentionCategory == ContextSuggestionAttentionCategory.UPCOMING
                }?.ref
                if (selectedRef == firstUpcomingRef) {
                    "It is your next upcoming task on $date" +
                        "${time?.let { " at $it" }.orEmpty()}."
                } else {
                    "It is scheduled for $date${time?.let { " at $it" }.orEmpty()}."
                }
            }
            ContextSuggestionAttentionCategory.LATER ->
                "It is scheduled for $date${time?.let { " at $it" }.orEmpty()}."
            ContextSuggestionAttentionCategory.UNSCHEDULED, null ->
                "It does not currently have a scheduled time."
        }
    }

    private fun spokenTitle(value: String): String =
        ContextSuggestionSnapshotBuilder.sanitizeTitle(value).ifBlank { "Untitled task" }

    private fun spokenDate(value: String): String = try {
        val parsed = SimpleDateFormat("dd/MM/yyyy", Locale.UK).apply {
            isLenient = false
        }.parse(value)
        if (parsed == null) "the scheduled date" else {
            SimpleDateFormat("d MMMM", Locale.UK).format(parsed)
        }
    } catch (_: Exception) {
        "the scheduled date"
    }

    private fun spokenTime(minuteOfDay: Int): String {
        val hour24 = minuteOfDay / 60
        val minute = minuteOfDay % 60
        val suffix = if (hour24 < 12) "AM" else "PM"
        val hour12 = (hour24 % 12).takeIf { it != 0 } ?: 12
        return if (minute == 0) {
            "$hour12 $suffix"
        } else {
            "$hour12:${minute.toString().padStart(2, '0')} $suffix"
        }
    }
}
