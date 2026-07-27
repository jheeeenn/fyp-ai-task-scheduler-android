package com.example.myapplication.ai.conversation

import com.example.myapplication.data.TaskEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class DailyBriefingItemCategory {
    OVERDUE,
    TODAY,
    UPCOMING
}

enum class DailyBriefingFocusReason {
    RECENTLY_OVERDUE,
    EARLIEST_TODAY,
    NEXT_UPCOMING
}

data class DailyBriefingItem(
    val category: DailyBriefingItemCategory,
    val task: ObservedTask,
    val isSuggestedFocus: Boolean
)

data class DailyBriefingSnapshot(
    val localDate: String,
    val overdueCount: Int,
    val todayActiveCount: Int,
    val upcomingActiveCount: Int,
    val spokenItems: List<DailyBriefingItem>,
    val additionalTodayCount: Int,
    val additionalUpcomingCount: Int,
    val suggestedFocus: ObservedTask?,
    val suggestedFocusReason: DailyBriefingFocusReason?,
    internal val spokenRoomTasks: List<TaskEntity>
)

object DailyBriefingSnapshotBuilder {
    const val UPCOMING_WINDOW_DAYS = 7
    const val MAX_SPOKEN_TASKS = 5

    fun build(
        localDate: String,
        rootTasks: List<TaskEntity>,
        subtasksByParentId: Map<Long, List<TaskEntity>>
    ): DailyBriefingSnapshot {
        val todayMillis = parseDateMillis(localDate)
            ?: throw IllegalArgumentException("localDate must use dd/MM/yyyy")
        val upcomingBoundaryMillis = Calendar.getInstance().run {
            timeInMillis = todayMillis
            add(Calendar.DAY_OF_MONTH, UPCOMING_WINDOW_DAYS)
            timeInMillis
        }
        val activeRoots = rootTasks.filter { it.parentTaskId == null && !it.isDone }
        val datedRoots = activeRoots.mapNotNull { task ->
            parseDateMillis(task.dueDate)?.let { dueDateMillis ->
                DatedRoomTask(task, dueDateMillis)
            }
        }

        val overdueTasks = datedRoots
            .filter { it.dueDateMillis < todayMillis }
        val todayTasks = datedRoots
            .filter { it.dueDateMillis == todayMillis }
            .sortedWith(todayComparator)
        val upcomingTasks = datedRoots
            .filter {
                it.dueDateMillis > todayMillis &&
                    it.dueDateMillis <= upcomingBoundaryMillis
            }
            .sortedWith(upcomingComparator)

        val focusSelection = when {
            overdueTasks.isNotEmpty() -> FocusSelection(
                candidate = overdueTasks.sortedWith(overdueFocusComparator).first(),
                reason = DailyBriefingFocusReason.RECENTLY_OVERDUE
            )
            todayTasks.isNotEmpty() -> FocusSelection(
                candidate = todayTasks.first(),
                reason = DailyBriefingFocusReason.EARLIEST_TODAY
            )
            upcomingTasks.isNotEmpty() -> FocusSelection(
                candidate = upcomingTasks.first(),
                reason = DailyBriefingFocusReason.NEXT_UPCOMING
            )
            else -> null
        }

        val selected = buildList {
            focusSelection?.let { focus ->
                add(CategorizedRoomTask(focus.candidate, focus.reason.category))
            }
            addAll(
                todayTasks
                    .filterNot { it.task.id == focusSelection?.candidate?.task?.id }
                    .map { CategorizedRoomTask(it, DailyBriefingItemCategory.TODAY) }
            )
            addAll(
                upcomingTasks
                    .filterNot { it.task.id == focusSelection?.candidate?.task?.id }
                    .map { CategorizedRoomTask(it, DailyBriefingItemCategory.UPCOMING) }
            )
        }.take(MAX_SPOKEN_TASKS)

        val spokenItems = selected.map { selectedTask ->
            DailyBriefingItem(
                category = selectedTask.category,
                task = observedTask(selectedTask.candidate.task, subtasksByParentId),
                isSuggestedFocus =
                    selectedTask.candidate.task.id == focusSelection?.candidate?.task?.id
            )
        }
        val suggestedFocus = focusSelection?.candidate?.task?.let { task ->
            observedTask(task, subtasksByParentId)
        }

        return DailyBriefingSnapshot(
            localDate = localDate,
            overdueCount = overdueTasks.size,
            todayActiveCount = todayTasks.size,
            upcomingActiveCount = upcomingTasks.size,
            spokenItems = spokenItems,
            additionalTodayCount = todayTasks.size -
                spokenItems.count { it.category == DailyBriefingItemCategory.TODAY },
            additionalUpcomingCount = upcomingTasks.size -
                spokenItems.count { it.category == DailyBriefingItemCategory.UPCOMING },
            suggestedFocus = suggestedFocus,
            suggestedFocusReason = focusSelection?.reason,
            spokenRoomTasks = selected.map { it.candidate.task }
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

    private fun observedTask(
        task: TaskEntity,
        subtasksByParentId: Map<Long, List<TaskEntity>>
    ): ObservedTask = TaskObservationMapper.observedTask(
        task = task,
        subtasks = subtasksByParentId[task.id].orEmpty()
    )

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

    private fun timeSortValue(task: TaskEntity): Int =
        parseTimeMinute(task.dueTime) ?: Int.MAX_VALUE

    private val todayComparator =
        compareBy<DatedRoomTask> { timeSortValue(it.task) }
            .thenBy { it.task.title.lowercase(Locale.UK) }
            .thenBy { it.task.id }

    private val upcomingComparator =
        compareBy<DatedRoomTask> { it.dueDateMillis }
            .thenBy { timeSortValue(it.task) }
            .thenBy { it.task.title.lowercase(Locale.UK) }
            .thenBy { it.task.id }

    private val overdueFocusComparator =
        compareByDescending<DatedRoomTask> { it.dueDateMillis }
            .thenBy { timeSortValue(it.task) }
            .thenBy { it.task.title.lowercase(Locale.UK) }
            .thenBy { it.task.id }

    private val DailyBriefingFocusReason.category: DailyBriefingItemCategory
        get() = when (this) {
            DailyBriefingFocusReason.RECENTLY_OVERDUE ->
                DailyBriefingItemCategory.OVERDUE
            DailyBriefingFocusReason.EARLIEST_TODAY ->
                DailyBriefingItemCategory.TODAY
            DailyBriefingFocusReason.NEXT_UPCOMING ->
                DailyBriefingItemCategory.UPCOMING
        }

    private data class DatedRoomTask(
        val task: TaskEntity,
        val dueDateMillis: Long
    )

    private data class CategorizedRoomTask(
        val candidate: DatedRoomTask,
        val category: DailyBriefingItemCategory
    )

    private data class FocusSelection(
        val candidate: DatedRoomTask,
        val reason: DailyBriefingFocusReason
    )

    private const val DATE_PATTERN = "dd/MM/yyyy"
    private val TIME_PATTERNS = listOf("h:mm a", "h a", "HH:mm")
}

object DailyBriefingSpeechRenderer {
    fun render(snapshot: DailyBriefingSnapshot): String {
        val sentences = mutableListOf<String>()
        sentences += "Here is your briefing for ${spokenDate(snapshot.localDate)}."
        sentences += "You have ${countSummary(snapshot.overdueCount, "overdue task")}, " +
            "${todaySummary(snapshot.todayActiveCount)}, and " +
            "${countSummary(snapshot.upcomingActiveCount, "upcoming task")} " +
            "within the next seven days."

        snapshot.spokenItems.forEachIndexed { index, item ->
            sentences += renderItem(index, item)
        }

        if (snapshot.additionalTodayCount > 0) {
            sentences += remainingSummary(
                count = snapshot.additionalTodayCount,
                singular = "active task due today",
                plural = "active tasks due today"
            )
        }
        if (snapshot.additionalUpcomingCount > 0) {
            sentences += remainingSummary(
                count = snapshot.additionalUpcomingCount,
                singular = "upcoming task within the next seven days",
                plural = "upcoming tasks within the next seven days"
            )
        }

        sentences += if (snapshot.spokenItems.isEmpty()) {
            "You can request the full task list or create a new task."
        } else {
            "You can ask about one of these tasks or request the full task list."
        }
        return sentences.joinToString(" ")
    }

    private fun renderItem(index: Int, item: DailyBriefingItem): String {
        val ordinal = ORDINALS.getOrElse(index) { "Next" }
        val introduction = if (item.isSuggestedFocus) {
            "Suggested focus, ${ordinal.lowercase(Locale.UK)}"
        } else {
            ordinal
        }
        val title = item.task.title.ifBlank { "Untitled task" }
        val time = DailyBriefingSnapshotBuilder.parseTimeMinute(item.task.dueTime)
            ?.let(::spokenTime)
        val schedule = when (item.category) {
            DailyBriefingItemCategory.OVERDUE ->
                "$introduction, $title, overdue since ${spokenDate(item.task.dueDate)}" +
                    spokenTimeSuffix(time)
            DailyBriefingItemCategory.TODAY ->
                "$introduction, $title, due today" + spokenTimeSuffix(time)
            DailyBriefingItemCategory.UPCOMING ->
                "$introduction, $title, upcoming on ${spokenDate(item.task.dueDate)}" +
                    spokenTimeSuffix(time)
        }
        return "$schedule." + subtaskDescription(item.task)
    }

    private fun spokenTimeSuffix(time: String?): String =
        if (time == null) ", with no set time" else " at $time"

    private fun countSummary(count: Int, singular: String): String = when (count) {
        0 -> "no ${singular}s"
        1 -> "one $singular"
        else -> "${spokenNumber(count)} ${singular}s"
    }

    private fun todaySummary(count: Int): String = when (count) {
        0 -> "no active tasks due today"
        1 -> "one active task due today"
        else -> "${spokenNumber(count)} active tasks due today"
    }

    private fun remainingSummary(
        count: Int,
        singular: String,
        plural: String
    ): String = if (count == 1) {
        "There is one more $singular."
    } else {
        "There are ${spokenNumber(count)} more $plural."
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
