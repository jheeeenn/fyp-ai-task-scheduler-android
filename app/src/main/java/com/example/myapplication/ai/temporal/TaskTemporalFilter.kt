package com.example.myapplication.ai.temporal

import com.example.myapplication.data.TaskEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class TaskCompletionFilter { ANY, ACTIVE_ONLY, COMPLETED_ONLY }

object TaskTemporalFilter {
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.UK).apply { isLenient = false }
    private val timeFormats = listOf("h:mm a", "h a", "HH:mm").map { SimpleDateFormat(it, Locale.UK).apply { isLenient = false } }

    fun filterAndSort(tasks: List<TaskEntity>, window: TemporalQueryWindow, completionFilter: TaskCompletionFilter = TaskCompletionFilter.ACTIVE_ONLY): List<TaskEntity> {
        if (window.status == TemporalResolutionStatus.UNRESOLVED) return emptyList()
        return tasks.asSequence()
            .filter { when (completionFilter) { TaskCompletionFilter.ANY -> true; TaskCompletionFilter.ACTIVE_ONLY -> !it.isDone; TaskCompletionFilter.COMPLETED_ONLY -> it.isDone } }
            .filter { matchesDate(it, window) }
            .filter { matchesTime(it, window) }
            .sortedWith(compareBy<TaskEntity> { parseDateMillis(it.dueDate) ?: Long.MAX_VALUE }.thenBy { parseTimeMinute(it.dueTime) ?: Int.MAX_VALUE }.thenBy { it.title.lowercase(Locale.UK) })
            .toList()
    }

    private fun matchesDate(task: TaskEntity, window: TemporalQueryWindow): Boolean {
        if (!window.hasDateConstraint) return true
        val taskDate = parseDateMillis(task.dueDate) ?: return false
        val start = parseDateMillis(window.startDateInclusive)
        val end = parseDateMillis(window.endDateInclusive)
        if (start != null && taskDate < start) return false
        if (end != null && taskDate > end) return false
        return true
    }

    private fun matchesTime(task: TaskEntity, window: TemporalQueryWindow): Boolean {
        if (!window.hasTimeConstraint) return true
        val minute = parseTimeMinute(task.dueTime) ?: return false
        val start = window.startMinuteInclusive ?: 0
        val end = window.endMinuteInclusive ?: 1439
        return if (window.wrapsMidnight) minute >= start || minute <= end else minute in start..end
    }

    private fun parseDateMillis(value: String?): Long? = try {
        if (value.isNullOrBlank()) null else dateFormat.parse(value)?.time
    } catch (_: Exception) { null }

    private fun parseTimeMinute(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        for (format in timeFormats) try {
            val cal = Calendar.getInstance(Locale.UK)
            cal.time = format.parse(value.trim().uppercase(Locale.UK)) ?: continue
            return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        } catch (_: Exception) {}
        return null
    }
}
