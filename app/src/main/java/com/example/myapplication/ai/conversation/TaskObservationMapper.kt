package com.example.myapplication.ai.conversation

import com.example.myapplication.data.TaskEntity

object TaskObservationMapper {
    fun observedTask(
        task: TaskEntity,
        subtasks: List<TaskEntity> = emptyList()
    ): ObservedTask {
        val unfinished = subtasks.filterNot { it.isDone }
        return ObservedTask(
            title = task.title,
            dueDate = task.dueDate.orEmpty(),
            dueTime = task.dueTime.orEmpty(),
            isDone = task.isDone,
            subtaskCount = subtasks.size,
            unfinishedSubtaskCount = unfinished.size,
            unfinishedSubtaskTitles = unfinished.map { it.title }
        )
    }
}
