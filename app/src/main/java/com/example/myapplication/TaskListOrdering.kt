package com.example.myapplication

import com.example.myapplication.data.TaskEntity
import java.util.TimeZone

object TaskListOrdering {
    fun byUrgency(
        tasks: List<TaskEntity>,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<TaskEntity> = tasks.sortedWith(
        compareBy<TaskEntity> { if (it.isDone) 1 else 0 }
            .thenBy {
                TaskStatusPresenter.dueEpochMillis(it.dueDate, it.dueTime, timeZone)
                    ?: Long.MAX_VALUE
            }
            .thenBy { it.id }
    )
}
