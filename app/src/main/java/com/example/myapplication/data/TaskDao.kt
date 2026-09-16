package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

enum class BreakdownTransactionStatus {
    SUCCESS,
    PARENT_CHANGED,
    ALREADY_HAS_SUBTASKS
}

data class BreakdownTransactionResult(
    val status: BreakdownTransactionStatus,
    val parent: TaskEntity? = null,
    val insertedCount: Int = 0
)

@Dao // data access object
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY id ASC")
    suspend fun getAll(): List<TaskEntity>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>): List<Long>

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks(): Int

    @Transaction
    suspend fun insertRootTasksAtomically(tasks: List<TaskEntity>): List<Long> {
        require(tasks.all { it.parentTaskId == null })
        return insertAll(tasks)
    }

    @Transaction
    suspend fun insertSubtasksIntoExistingRootAtomically(
        parentTaskId: Long,
        subtaskTitles: List<String>
    ): BreakdownTransactionResult {
        require(subtaskTitles.size in 2..5)
        require(subtaskTitles.all { it.isNotBlank() })
        val parent = getById(parentTaskId)
            ?: return BreakdownTransactionResult(BreakdownTransactionStatus.PARENT_CHANGED)
        if (parent.parentTaskId != null || parent.isDone) {
            return BreakdownTransactionResult(BreakdownTransactionStatus.PARENT_CHANGED)
        }
        if (getSubtasks(parentTaskId).isNotEmpty()) {
            return BreakdownTransactionResult(
                BreakdownTransactionStatus.ALREADY_HAS_SUBTASKS
            )
        }

        val children = subtaskTitles.mapIndexed { index, title ->
            TaskEntity(
                title = title,
                dueDate = parent.dueDate,
                dueTime = parent.dueTime,
                parentTaskId = parent.id,
                subtaskOrder = index
            )
        }
        val insertedIds = insertAll(children)
        check(insertedIds.size == children.size)
        return BreakdownTransactionResult(
            status = BreakdownTransactionStatus.SUCCESS,
            parent = parent,
            insertedCount = insertedIds.size
        )
    }

    @Transaction
    suspend fun insertNewRootWithSubtasksAtomically(
        parent: TaskEntity,
        subtaskTitles: List<String>
    ): BreakdownTransactionResult {
        require(parent.id == 0L)
        require(parent.parentTaskId == null)
        require(!parent.isDone)
        require(parent.title.isNotBlank())
        require(subtaskTitles.size in 2..5)
        require(subtaskTitles.all { it.isNotBlank() })

        val parentId = insert(parent)
        val insertedParent = parent.copy(id = parentId)
        val children = subtaskTitles.mapIndexed { index, title ->
            TaskEntity(
                title = title,
                dueDate = parent.dueDate,
                dueTime = parent.dueTime,
                parentTaskId = parentId,
                subtaskOrder = index
            )
        }
        val insertedIds = insertAll(children)
        check(insertedIds.size == children.size)
        return BreakdownTransactionResult(
            status = BreakdownTransactionStatus.SUCCESS,
            parent = insertedParent,
            insertedCount = insertedIds.size
        )
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

    @Query(
        """
        UPDATE tasks
        SET title = :newTitle, dueDate = :newDueDate, dueTime = :newDueTime
        WHERE id = :id
          AND title = :expectedTitle
          AND dueDate IS :expectedDueDate
          AND dueTime IS :expectedDueTime
          AND isDone = :expectedIsDone
        """
    )
    suspend fun updateTaskIfAuthoritativeSnapshotMatches(
        id: Long,
        expectedTitle: String,
        expectedDueDate: String?,
        expectedDueTime: String?,
        expectedIsDone: Boolean,
        newTitle: String,
        newDueDate: String?,
        newDueTime: String?
    ): Int

    @Transaction
    suspend fun updateTaskAndSubtasksIfAuthoritativeSnapshotMatches(
        id: Long,
        expectedTitle: String,
        expectedDueDate: String?,
        expectedDueTime: String?,
        expectedIsDone: Boolean,
        newTitle: String,
        newDueDate: String?,
        newDueTime: String?
    ): Boolean {
        val updatedRows = updateTaskIfAuthoritativeSnapshotMatches(
            id = id,
            expectedTitle = expectedTitle,
            expectedDueDate = expectedDueDate,
            expectedDueTime = expectedDueTime,
            expectedIsDone = expectedIsDone,
            newTitle = newTitle,
            newDueDate = newDueDate,
            newDueTime = newDueTime
        )
        if (updatedRows != 1) return false
        val updatedTask = getById(id) ?: return false
        if (updatedTask.parentTaskId == null) {
            updateSubtasksSchedule(
                parentTaskId = id,
                dueDate = newDueDate,
                dueTime = newDueTime
            )
        }
        return true
    }

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
