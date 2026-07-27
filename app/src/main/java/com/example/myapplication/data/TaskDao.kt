package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao // data access object
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY id ASC")
    suspend fun getAll(): List<TaskEntity>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>): List<Long>

    @Transaction
    suspend fun insertRootTasksAtomically(tasks: List<TaskEntity>): List<Long> {
        require(tasks.all { it.parentTaskId == null })
        return insertAll(tasks)
    }

    @Query("UPDATE tasks SET isDone = :isDone WHERE id = :id")
    suspend fun updateDoneStatus(id: Long, isDone: Boolean)

    @Query("UPDATE tasks SET isDone = :isDone WHERE id = :taskId OR parentTaskId = :taskId")
    suspend fun updateDoneStatusForTaskAndSubtasks(taskId: Long, isDone: Boolean)

    @Query("UPDATE tasks SET title = :newTitle WHERE id = :id")
    suspend fun updateTitle(id: Long, newTitle: String)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM tasks WHERE id = :id OR parentTaskId = :id")
    suspend fun deleteTaskAndSubtasks(id: Long)

    @Query("DELETE FROM tasks WHERE parentTaskId = :parentTaskId")
    suspend fun deleteSubtasks(parentTaskId: Long)

    @Query("UPDATE tasks SET title = :title, dueDate = :dueDate, dueTime = :dueTime WHERE id = :id")
    suspend fun updateTask(id: Long, title: String, dueDate: String?, dueTime: String?)

    @Query("""
    UPDATE tasks 
    SET dueDate = :dueDate, dueTime = :dueTime 
    WHERE parentTaskId = :parentTaskId
""")
    suspend fun updateSubtasksSchedule(
        parentTaskId: Long,
        dueDate: String?,
        dueTime: String?
    )

    @Query("SELECT * FROM tasks WHERE isDone = 0 ORDER BY id DESC")
    suspend fun getActiveTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE parentTaskId IS NULL ORDER BY id ASC")
    suspend fun getRootTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE parentTaskId IS NULL AND isDone = 0 ORDER BY id DESC")
    suspend fun getRootActiveTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE parentTaskId IS NULL AND dueDate = :date ORDER BY id ASC")
    suspend fun getRootTasksForDate(date: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentTaskId ORDER BY subtaskOrder ASC, id ASC")
    suspend fun getSubtasks(parentTaskId: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE LOWER(title) LIKE '%' || LOWER(:query) || '%' ORDER BY id DESC")
    suspend fun searchTasksByTitle(query: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TaskEntity?

    @Query("UPDATE tasks SET isDone = 1 WHERE id = :id")
    suspend fun markTaskDone(id: Long)

    @Query("UPDATE tasks SET isDone = 0 WHERE id = :id")
    suspend fun markTaskUndone(id: Long)
}
