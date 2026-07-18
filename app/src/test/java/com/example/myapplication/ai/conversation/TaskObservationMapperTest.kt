package com.example.myapplication.ai.conversation

import com.example.myapplication.data.TaskEntity
import org.junit.Assert.*
import org.junit.Test

class TaskObservationMapperTest {
    @Test fun nullableTaskDateAndTimeBecomeEmptyStrings() {
        val observed = TaskObservationMapper.observedTask(TaskEntity(title = "No schedule"))
        assertEquals("", observed.dueDate)
        assertEquals("", observed.dueTime)
    }

    @Test fun includesAvailableSubtaskCountsAndUnfinishedTitles() {
        val observed = TaskObservationMapper.observedTask(
            task = TaskEntity(id = 1, title = "Project"),
            subtasks = listOf(
                TaskEntity(id = 2, title = "Draft", parentTaskId = 1, isDone = false),
                TaskEntity(id = 3, title = "Submit", parentTaskId = 1, isDone = true)
            )
        )
        assertEquals(2, observed.subtaskCount)
        assertEquals(1, observed.unfinishedSubtaskCount)
        assertEquals(listOf("Draft"), observed.unfinishedSubtaskTitles)
    }

    @Test fun postMutationCompletionFactsAreRepresentable() {
        assertTrue(TaskObservationMapper.observedTask(TaskEntity(title = "Done", isDone = true)).isDone)
        assertFalse(TaskObservationMapper.observedTask(TaskEntity(title = "Open", isDone = false)).isDone)
    }
}
