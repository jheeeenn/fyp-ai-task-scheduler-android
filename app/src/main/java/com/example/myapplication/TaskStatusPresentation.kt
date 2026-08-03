package com.example.myapplication

import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

enum class TaskVisualStatus {
    OVERDUE,
    DUE_TODAY,
    UPCOMING,
    COMPLETED,
    UNSCHEDULED
}

data class TaskStatusPresentation(
    val visualStatus: TaskVisualStatus,
    val visibleText: String,
    val spokenText: String
)

object TaskStatusPresenter {
    private const val STORED_DATE_TIME_PATTERN = "dd/MM/yyyy hh:mm a"

    fun present(
        isDone: Boolean,
        dueDate: String?,
        dueTime: String?,
        now: Date = Date(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): TaskStatusPresentation {
        if (isDone) {
            return presentation(TaskVisualStatus.COMPLETED, "Completed")
        }
        val due = parseDueDateTime(dueDate, dueTime, timeZone)
            ?: return presentation(TaskVisualStatus.UNSCHEDULED, "Unscheduled")

        val dueDay = localDayNumber(due, timeZone)
        val today = localDayNumber(now, timeZone)
        val dayDifference = dueDay - today

        if (dayDifference < 0) {
            val days = -dayDifference
            return presentation(
                TaskVisualStatus.OVERDUE,
                "Overdue by $days ${if (days == 1L) "day" else "days"}"
            )
        }

        if (dayDifference == 0L) {
            if (due.before(now)) {
                val overdueHours = TimeUnit.MILLISECONDS.toHours(now.time - due.time)
                val wording = when {
                    overdueHours < 1L -> "Overdue by less than 1 hour"
                    overdueHours == 1L -> "Overdue by 1 hour"
                    else -> "Overdue by $overdueHours hours"
                }
                return presentation(TaskVisualStatus.OVERDUE, wording)
            }
            return presentation(
                TaskVisualStatus.DUE_TODAY,
                "Due today at ${formatTime(due, timeZone)}"
            )
        }

        if (dayDifference == 1L) {
            return presentation(
                TaskVisualStatus.UPCOMING,
                "Due tomorrow at ${formatTime(due, timeZone)}"
            )
        }

        return presentation(TaskVisualStatus.UPCOMING, "Due in $dayDifference days")
    }

    fun dueEpochMillis(
        dueDate: String?,
        dueTime: String?,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long? = parseDueDateTime(dueDate, dueTime, timeZone)?.time

    private fun presentation(
        visualStatus: TaskVisualStatus,
        text: String
    ) = TaskStatusPresentation(visualStatus, text, text)

    private fun parseDueDateTime(
        dueDate: String?,
        dueTime: String?,
        timeZone: TimeZone
    ): Date? {
        if (dueDate.isNullOrBlank() || dueTime.isNullOrBlank()) return null
        return runCatching {
            val storedValue = "$dueDate $dueTime"
            val formatter = SimpleDateFormat(STORED_DATE_TIME_PATTERN, Locale.ENGLISH).apply {
                isLenient = false
                this.timeZone = timeZone
            }
            val position = ParsePosition(0)
            formatter.parse(storedValue, position)?.takeIf {
                position.errorIndex < 0 && position.index == storedValue.length
            }
        }.getOrNull()
    }

    private fun formatTime(date: Date, timeZone: TimeZone): String =
        SimpleDateFormat("h:mm a", Locale.ENGLISH).apply {
            this.timeZone = timeZone
        }.format(date)

    private fun localDayNumber(date: Date, timeZone: TimeZone): Long {
        val local = Calendar.getInstance(timeZone).apply { time = date }
        val utcDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                local.get(Calendar.YEAR),
                local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0
            )
        }
        return TimeUnit.MILLISECONDS.toDays(utcDay.timeInMillis)
    }
}
