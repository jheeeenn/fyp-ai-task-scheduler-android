package com.example.myapplication.ai.breakdown

import com.example.myapplication.data.BreakdownTransactionResult
import com.example.myapplication.data.BreakdownTransactionStatus
import com.example.myapplication.data.TaskEntity
import com.example.myapplication.diagnostics.DebugDiagnosticLog
import kotlinx.coroutines.CancellationException

interface BreakdownPersistenceStore {
    suspend fun insertSubtasksIntoExistingRootAtomically(
        parentTaskId: Long,
        subtaskTitles: List<String>
    ): BreakdownTransactionResult

    suspend fun insertNewRootWithSubtasksAtomically(
        parent: TaskEntity,
        subtaskTitles: List<String>
    ): BreakdownTransactionResult
}

fun interface BreakdownReminderScheduler {
    fun schedule(task: TaskEntity): Boolean
}

enum class BreakdownSaveResultCategory {
    SUCCESS,
    PARENT_CHANGED,
    ALREADY_HAS_SUBTASKS,
    FAILURE
}

data class BreakdownSaveResult(
    val category: BreakdownSaveResultCategory,
    val mode: BreakdownDraftMode,
    val requestedSubtaskCount: Int,
    val insertedCount: Int,
    val reminderScheduled: Boolean = false
)

class BreakdownPersistenceCoordinator(
    private val store: BreakdownPersistenceStore,
    private val reminderScheduler: BreakdownReminderScheduler
) {
    suspend fun persist(pendingSave: PendingBreakdownSave): BreakdownSaveResult {
        val draft = pendingSave.draft
        val mode = requireNotNull(draft.mode)
        val validation = BreakdownPlanValidator.validate(
            draft.parentTitle,
            draft.proposedSubtasks
        )
        require(validation is BreakdownPlanValidationResult.Accepted)
        val titles = validation.titles
        DebugDiagnosticLog.event(
            "BREAKDOWN_TRANSACTION",
            "phase=BEGIN\nmode=${mode.name}\n" +
                "requestedSubtaskCount=${titles.size}\ninsertedCount=0"
        )

        val transaction = try {
            when (mode) {
                BreakdownDraftMode.EXISTING_ROOT ->
                    store.insertSubtasksIntoExistingRootAtomically(
                        parentTaskId = requireNotNull(draft.resolvedParentTaskId()),
                        subtaskTitles = titles
                    )
                BreakdownDraftMode.NEW_ROOT ->
                    store.insertNewRootWithSubtasksAtomically(
                        parent = TaskEntity(
                            title = draft.parentTitle,
                            dueDate = requireNotNull(draft.dateText),
                            dueTime = requireNotNull(draft.timeText)
                        ),
                        subtaskTitles = titles
                    )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return result(
                BreakdownSaveResultCategory.FAILURE,
                mode,
                titles.size,
                insertedCount = 0
            )
        }

        val category = when (transaction.status) {
            BreakdownTransactionStatus.SUCCESS -> BreakdownSaveResultCategory.SUCCESS
            BreakdownTransactionStatus.PARENT_CHANGED ->
                BreakdownSaveResultCategory.PARENT_CHANGED
            BreakdownTransactionStatus.ALREADY_HAS_SUBTASKS ->
                BreakdownSaveResultCategory.ALREADY_HAS_SUBTASKS
        }
        if (category != BreakdownSaveResultCategory.SUCCESS) {
            return result(category, mode, titles.size, insertedCount = 0)
        }
        if (transaction.insertedCount != titles.size) {
            return result(
                BreakdownSaveResultCategory.FAILURE,
                mode,
                titles.size,
                insertedCount = 0
            )
        }

        val reminderScheduled =
            if (mode == BreakdownDraftMode.NEW_ROOT) {
                val insertedRoot = transaction.parent
                    ?: return result(
                        BreakdownSaveResultCategory.FAILURE,
                        mode,
                        titles.size,
                        insertedCount = 0
                    )
                try {
                    reminderScheduler.schedule(insertedRoot)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }
        return result(
            BreakdownSaveResultCategory.SUCCESS,
            mode,
            titles.size,
            transaction.insertedCount,
            reminderScheduled
        )
    }

    private fun result(
        category: BreakdownSaveResultCategory,
        mode: BreakdownDraftMode,
        requestedCount: Int,
        insertedCount: Int,
        reminderScheduled: Boolean = false
    ): BreakdownSaveResult {
        DebugDiagnosticLog.event(
            "BREAKDOWN_TRANSACTION",
            "phase=COMPLETE\nmode=${mode.name}\n" +
                "requestedSubtaskCount=$requestedCount\ninsertedCount=$insertedCount"
        )
        val diagnostic = when (category) {
            BreakdownSaveResultCategory.SUCCESS -> "SUCCESS"
            BreakdownSaveResultCategory.PARENT_CHANGED -> "PARENT_CHANGED"
            BreakdownSaveResultCategory.ALREADY_HAS_SUBTASKS ->
                "ALREADY_HAS_SUBTASKS"
            BreakdownSaveResultCategory.FAILURE -> "FAILURE"
        }
        DebugDiagnosticLog.event("BREAKDOWN_SAVE_RESULT", "result=$diagnostic")
        return BreakdownSaveResult(
            category = category,
            mode = mode,
            requestedSubtaskCount = requestedCount,
            insertedCount = insertedCount,
            reminderScheduled = reminderScheduled
        )
    }
}
