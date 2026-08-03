package com.example.myapplication

import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class TaskListOrderingTest {
    @Test
    fun incompleteTasksAreUrgentFirstAndCompletedTasksAreLast() {
        val tasks = listOf(
            task(1, "Completed earlier", "01/08/2026", "08:00 AM", isDone = true),
            task(2, "Upcoming", "04/08/2026", "09:00 AM"),
            task(3, "Overdue", "02/08/2026", "10:00 AM"),
            task(4, "Invalid", "31/02/2026", "09:00 AM"),
            task(5, "Completed later", "05/08/2026", "08:00 AM", isDone = true)
        )

        val ordered = TaskListOrdering.byUrgency(tasks, TimeZone.getTimeZone("UTC"))

        assertEquals(listOf(3L, 2L, 4L, 1L, 5L), ordered.map { it.id })
    }

    private fun task(
        id: Long,
        title: String,
        date: String,
        time: String,
        isDone: Boolean = false
    ) = TaskEntity(id = id, title = title, dueDate = date, dueTime = time, isDone = isDone)
}
