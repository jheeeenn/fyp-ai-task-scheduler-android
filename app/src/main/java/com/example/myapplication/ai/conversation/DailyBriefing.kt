package com.example.myapplication.ai.conversation

import com.example.myapplication.data.TaskEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class DailyBriefingSnapshot(
    val localDate: String,
    val overdueCount: Int,
    val todayActiveCount: Int,
    val highlightedTasks: List<ObservedTask>,
    val additionalTodayCount: Int,
    internal val highlightedRoomTasks: List<TaskEntity>
)

object DailyBriefingSnapshotBuilder {
    const val MAX_HIGHLIGHTED_TASKS = 5

    fun build(
        localDate: String,
        rootTasks: List<TaskEntity>,
        subtasksByParentId: Map<Long, List<TaskEntity>>
    ): DailyBriefingSnapshot {
        val todayMillis = parseDateMillis(localDate)
            ?: throw IllegalArgumentException("localDate must use dd/MM/yyyy")
        val activeRoots = rootTasks.filter { it.parentTaskId == null && !it.isDone }
        val overdueCount = activeRoots.count { task ->
            val dueMillis = parseDateMillis(task.dueDate)
            dueMillis != null && dueMillis < todayMillis
        }
        val todayTasks = activeRoots
            .filter { parseDateMillis(it.dueDate) == todayMillis }
            .sortedWith(
                compareBy<TaskEntity> { parseTimeMinute(it.dueTime) ?: Int.MAX_VALUE }
                    .thenBy { it.title.lowercase(Locale.UK) }
                    .thenBy { it.id }
            )
        val highlightedRoomTasks = todayTasks.take(MAX_HIGHLIGHTED_TASKS)
        val highlightedTasks = highlightedRoomTasks.map { task ->
            TaskObservationMapper.observedTask(
                task = task,
                subtasks = subtasksByParentId[task.id].orEmpty()
            )
        }
        return DailyBriefingSnapshot(
            localDate = localDate,
            overdueCount = overdueCount,
            todayActiveCount = todayTasks.size,
            highlightedTasks = highlightedTasks,
            additionalTodayCount = todayTasks.size - highlightedTasks.size,
            highlightedRoomTasks = highlightedRoomTasks
        )
    }

    internal fun parseTimeMinute(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        TIME_PATTERNS.forEach { pattern ->
            try {
                val parsed = SimpleDateFormat(pattern, Locale.UK).apply {
                    isLenient = false
                }.parse(value.trim().uppercase(Locale.UK)) ?: return@forEach
                val calendar = Calendar.getInstance(Locale.UK).apply { time = parsed }
                return calendar.get(Calendar.HOUR_OF_DAY) * 60 +
                    calendar.get(Calendar.MINUTE)
            } catch (_: Exception) {
                // A malformed time is authoritative as an untimed task for briefing ordering.
            }
        }
        return null
    }

    private fun parseDateMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            SimpleDateFormat(DATE_PATTERN, Locale.UK).apply {
                isLenient = false
            }.parse(value.trim())?.time
        } catch (_: Exception) {
            null
        }
    }

    private const val DATE_PATTERN = "dd/MM/yyyy"
    private val TIME_PATTERNS = listOf("h:mm a", "h a", "HH:mm")
}

object DailyBriefingSpeechRenderer {
    fun render(snapshot: DailyBriefingSnapshot): String {
        val sentences = mutableListOf<String>()
        sentences += "Here is your briefing for ${spokenDate(snapshot.localDate)}."

        val todaySummary = when (snapshot.todayActiveCount) {
            0 -> "no active tasks due today"
            1 -> "one active task today"
            else -> "${spokenNumber(snapshot.todayActiveCount)} active tasks today"
        }
        sentences += if (snapshot.overdueCount == 0) {
            "You have $todaySummary."
        } else {
            val overdueSummary = if (snapshot.overdueCount == 1) {
                "one overdue task"
            } else {
                "${spokenNumber(snapshot.overdueCount)} overdue tasks"
            }
            "You have $overdueSummary and $todaySummary."
        }

        snapshot.highlightedTasks.forEachIndexed { index, task ->
            sentences += renderTask(index, task)
        }

        if (snapshot.additionalTodayCount > 0) {
            sentences += if (snapshot.additionalTodayCount == 1) {
                "There is one more active task due today."
            } else {
                "There are ${spokenNumber(snapshot.additionalTodayCount)} more active tasks due today."
            }
        }

        sentences += if (snapshot.highlightedTasks.isEmpty()) {
            "You can ask to review your upcoming tasks or create a new task."
        } else {
            "You can ask about one of these tasks or request the full task list."
        }
        return sentences.joinToString(" ")
    }

    private fun renderTask(index: Int, task: ObservedTask): String {
        val ordinal = ORDINALS.getOrElse(index) { "Next" }
        val title = task.title.ifBlank { "Untitled task" }
        val time = DailyBriefingSnapshotBuilder.parseTimeMinute(task.dueTime)
            ?.let(::spokenTime)
        val schedule = if (time == null) {
            "$ordinal, $title, with no set time."
        } else {
            "$ordinal, $title at $time."
        }
        return schedule + subtaskDescription(task)
    }

    private fun subtaskDescription(task: ObservedTask): String {
        if (task.subtaskCount == 0) return ""
        if (task.unfinishedSubtaskCount == 0) {
            val noun = if (task.subtaskCount == 1) "subtask" else "subtasks"
            return " It has ${spokenNumber(task.subtaskCount)} $noun, all completed."
        }
        val totalNoun = if (task.subtaskCount == 1) "subtask" else "subtasks"
        val unfinishedNoun =
            if (task.unfinishedSubtaskCount == 1) "is" else "are"
        val titles = task.unfinishedSubtaskTitles
            .filter { it.isNotBlank() }
            .joinToString(", ")
        val titleDetail = if (titles.isBlank()) "." else ": $titles."
        return " It has ${spokenNumber(task.subtaskCount)} $totalNoun; " +
            "${spokenNumber(task.unfinishedSubtaskCount)} $unfinishedNoun unfinished$titleDetail"
    }

    private fun spokenDate(value: String): String = try {
        val parsed = SimpleDateFormat("dd/MM/yyyy", Locale.UK).apply {
            isLenient = false
        }.parse(value)
        if (parsed == null) "today" else {
            SimpleDateFormat("EEEE, d MMMM", Locale.UK).format(parsed)
        }
    } catch (_: Exception) {
        "today"
    }

    private fun spokenTime(minuteOfDay: Int): String {
        val hour24 = minuteOfDay / 60
        val minute = minuteOfDay % 60
        val suffix = if (hour24 < 12) "AM" else "PM"
        val hour12 = when (val value = hour24 % 12) {
            0 -> 12
            else -> value
        }
        return if (minute == 0) {
            "$hour12 $suffix"
        } else {
            "$hour12:${minute.toString().padStart(2, '0')} $suffix"
        }
    }

    private fun spokenNumber(value: Int): String =
        AccessibleTaskQuerySpeechRenderer.spokenNumber(value)

    private val ORDINALS = listOf("First", "Second", "Third", "Fourth", "Fifth")
}
