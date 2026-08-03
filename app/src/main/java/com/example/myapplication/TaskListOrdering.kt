package com.example.myapplication

import com.example.myapplication.data.TaskEntity
import java.util.Date
import java.util.TimeZone

enum class TaskListSortGroup {
    DUE_TODAY,
    UPCOMING,
    OVERDUE,
    UNSCHEDULED,
    COMPLETED
}

object TaskListOrdering {
    fun scheduled(
        tasks: List<TaskEntity>,
        now: Date = Date(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<TaskEntity> = ordered(
        tasks = tasks,
        now = now,
        timeZone = timeZone,
        rank = SCHEDULED_RANK
    )

    fun today(
        tasks: List<TaskEntity>,
        now: Date = Date(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<TaskEntity> = ordered(
        tasks = tasks,
        now = now,
        timeZone = timeZone,
        rank = TODAY_RANK
    )

    fun groupFor(
        task: TaskEntity,
        now: Date,
        timeZone: TimeZone = TimeZone.getDefault()
    ): TaskListSortGroup {
        if (task.isDone) return TaskListSortGroup.COMPLETED
        return when (
            TaskStatusPresenter.present(
                isDone = false,
                dueDate = task.dueDate,
                dueTime = task.dueTime,
                now = now,
                timeZone = timeZone
            ).visualStatus
        ) {
            TaskVisualStatus.DUE_TODAY -> TaskListSortGroup.DUE_TODAY
            TaskVisualStatus.UPCOMING -> TaskListSortGroup.UPCOMING
            TaskVisualStatus.OVERDUE -> TaskListSortGroup.OVERDUE
            TaskVisualStatus.UNSCHEDULED -> TaskListSortGroup.UNSCHEDULED
            TaskVisualStatus.COMPLETED -> TaskListSortGroup.COMPLETED
        }
    }

    private fun ordered(
        tasks: List<TaskEntity>,
        now: Date,
        timeZone: TimeZone,
        rank: Map<TaskListSortGroup, Int>
    ): List<TaskEntity> = tasks.map { task ->
        SortCandidate(
            task = task,
            group = groupFor(task, now, timeZone),
            dueEpochMillis = TaskStatusPresenter.dueEpochMillis(
                task.dueDate,
                task.dueTime,
                timeZone
            )
        )
    }.sortedWith { first, second ->
        val groupComparison = requireNotNull(rank[first.group])
            .compareTo(requireNotNull(rank[second.group]))
        if (groupComparison != 0) {
            groupComparison
        } else {
            val dueComparison = when (first.group) {
                TaskListSortGroup.DUE_TODAY,
                TaskListSortGroup.UPCOMING -> compareDueAscending(
                    first.dueEpochMillis,
                    second.dueEpochMillis
                )
                TaskListSortGroup.OVERDUE,
                TaskListSortGroup.COMPLETED -> compareDueDescending(
                    first.dueEpochMillis,
                    second.dueEpochMillis
                )
                TaskListSortGroup.UNSCHEDULED -> 0
            }
            if (dueComparison != 0) dueComparison else first.task.id.compareTo(second.task.id)
        }
    }.map { it.task }

    private fun compareDueAscending(first: Long?, second: Long?): Int = when {
        first == null && second == null -> 0
        first == null -> 1
        second == null -> -1
        else -> first.compareTo(second)
    }

    private fun compareDueDescending(first: Long?, second: Long?): Int = when {
        first == null && second == null -> 0
        first == null -> 1
        second == null -> -1
        else -> second.compareTo(first)
    }

    private data class SortCandidate(
        val task: TaskEntity,
        val group: TaskListSortGroup,
        val dueEpochMillis: Long?
    )

    private val SCHEDULED_RANK = mapOf(
        TaskListSortGroup.DUE_TODAY to 0,
        TaskListSortGroup.UPCOMING to 1,
        TaskListSortGroup.OVERDUE to 2,
        TaskListSortGroup.UNSCHEDULED to 3,
        TaskListSortGroup.COMPLETED to 4
    )

    private val TODAY_RANK = mapOf(
        TaskListSortGroup.DUE_TODAY to 0,
        TaskListSortGroup.OVERDUE to 1,
        TaskListSortGroup.UNSCHEDULED to 2,
        TaskListSortGroup.UPCOMING to 3,
        TaskListSortGroup.COMPLETED to 4
    )
}
