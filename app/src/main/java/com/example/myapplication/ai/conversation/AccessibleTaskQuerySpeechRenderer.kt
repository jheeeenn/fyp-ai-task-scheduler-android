package com.example.myapplication.ai.conversation

import java.text.SimpleDateFormat
import java.util.Locale

internal object AccessibleTaskQuerySpeechRenderer {
    fun render(observation: ExecutionObservation): String {
        val page = requireNotNull(observation.queryPage)
        if (page.presentation == TaskQueryPresentationLevel.COUNT_ONLY) {
            return renderCountOnly(page)
        }

        val opening = buildOpening(page)
        val grouped = page.totalTaskCount > page.pageSize
        val taskSpeech = observation.tasks.mapIndexed { index, task ->
            val ordinal = ORDINALS.getOrElse(index) { "Next" }
            val prefix = if (grouped) "$ordinal in this group," else "$ordinal,"
            "$prefix ${renderTask(task, page)}"
        }
        val closing = when {
            page.hasNextPage -> "Say continue for the next group, repeat, or stop."
            page.pageCount > 1 ->
                "That was the last group. You can ask about a task in this group, repeat, or stop."
            page.presentation == TaskQueryPresentationLevel.DETAILS ->
                "You can ask about one of these tasks, repeat, or stop."
            else ->
                "You can ask about one of these tasks or ask for full details."
        }
        return (listOf(opening) + taskSpeech + closing)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun renderCountOnly(page: TaskQueryPageObservation): String {
        val count = spokenNumber(page.totalTaskCount)
        val noun = if (page.totalTaskCount == 1) "task" else "tasks"
        val label = page.temporalLabel.trim()
        val schedule = if (label.isBlank()) "" else " $label"
        val countSentence = when (page.tone) {
            TaskQuerySpeechTone.PROFESSIONAL -> "You have $count $noun$schedule."
            TaskQuerySpeechTone.FRIENDLY,
            TaskQuerySpeechTone.NEUTRAL -> "Yes. You have $count $noun$schedule."
        }
        return "$countSentence Would you like me to read them?"
    }

    private fun buildOpening(page: TaskQueryPageObservation): String {
        val total = spokenNumber(page.totalTaskCount)
        val noun = if (page.totalTaskCount == 1) "task" else "tasks"
        val label = page.temporalLabel.trim()
        val schedule = if (label.isBlank()) "" else " $label"
        if (page.pageCount <= 1) {
            return "You have $total $noun$schedule."
        }
        val start = spokenNumber(page.pageStartPosition)
        val end = spokenNumber(page.pageEndPosition)
        return if (page.pageNumber == 1) {
            "You have $total $noun$schedule. I’ll read them in groups of five. Tasks $start through $end."
        } else {
            "Tasks $start through $end of $total."
        }
    }

    private fun renderTask(task: ObservedTask, page: TaskQueryPageObservation): String {
        val title = task.title.ifBlank { "Untitled task" }
        val date = task.dueDate.takeIf { page.includeTaskDates && it.isNotBlank() }
            ?.let(::formatDateForSpeech)
        val time = task.dueTime.takeIf { it.isNotBlank() }
        val schedule = when {
            date != null && time != null -> "$title on $date at $time."
            date != null -> "$title on $date."
            time != null -> "$title at $time."
            else -> "$title."
        }
        return when (page.detailLevel) {
            TaskQuerySpeechDetail.BRIEF -> schedule
            TaskQuerySpeechDetail.BALANCED -> schedule + compactSubtasks(task)
            TaskQuerySpeechDetail.DETAILED -> schedule + detailedSubtasks(task)
        }
    }

    private fun compactSubtasks(task: ObservedTask): String {
        if (task.subtaskCount == 0) {
            return if (task.isDone) " It is completed." else ""
        }
        return " It has ${spokenNumber(task.subtaskCount)} subtasks, " +
            "${spokenNumber(task.unfinishedSubtaskCount)} unfinished."
    }

    private fun detailedSubtasks(task: ObservedTask): String {
        if (task.subtaskCount == 0) {
            return if (task.isDone) " It is completed." else ""
        }
        if (task.unfinishedSubtaskCount == 0) {
            return " It has ${spokenNumber(task.subtaskCount)} subtasks. All are completed."
        }
        val names = task.unfinishedSubtaskTitles.joinToString(", ")
        val unfinishedVerb = if (task.unfinishedSubtaskCount == 1) "is" else "are"
        return " It has ${spokenNumber(task.subtaskCount)} subtasks. " +
            "${spokenNumber(task.unfinishedSubtaskCount)} $unfinishedVerb unfinished: $names."
    }

    private fun formatDateForSpeech(date: String): String = try {
        val parsed = SimpleDateFormat("dd/MM/yyyy", Locale.UK).parse(date)
        if (parsed == null) date else SimpleDateFormat("d MMMM yyyy", Locale.UK).format(parsed)
    } catch (_: Exception) {
        date
    }

    internal fun spokenNumber(value: Int): String {
        if (value !in 0..999) return value.toString()
        if (value < SMALL_NUMBERS.size) return SMALL_NUMBERS[value]
        if (value < 100) {
            val tens = TENS[value / 10]
            val remainder = value % 10
            return if (remainder == 0) tens else "$tens-${SMALL_NUMBERS[remainder]}"
        }
        val hundreds = "${SMALL_NUMBERS[value / 100]} hundred"
        val remainder = value % 100
        return if (remainder == 0) hundreds else "$hundreds ${spokenNumber(remainder)}"
    }

    private val ORDINALS = listOf("First", "Second", "Third", "Fourth", "Fifth")
    private val SMALL_NUMBERS = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight",
        "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen",
        "sixteen", "seventeen", "eighteen", "nineteen"
    )
    private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
}
