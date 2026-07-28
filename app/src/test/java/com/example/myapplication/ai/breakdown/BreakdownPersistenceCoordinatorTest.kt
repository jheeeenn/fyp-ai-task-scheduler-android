package com.example.myapplication.ai.breakdown

import com.example.myapplication.data.BreakdownTransactionResult
import com.example.myapplication.data.BreakdownTransactionStatus
import com.example.myapplication.data.TaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakdownPersistenceCoordinatorTest {
    @Test
    fun existingRootConfirmationInsertsOnlyChildrenAndSchedulesNoReminder() =
        runBlocking {
            var existingCalls = 0
            var newCalls = 0
            var reminderCalls = 0
            var receivedTitles: List<String> = emptyList()
            val parent = TaskEntity(
                id = 41,
                title = "Final year project",
                dueDate = "30/07/2026",
                dueTime = "4:00 PM"
            )
            val coordinator = BreakdownPersistenceCoordinator(
                store = fakeStore(
                    existing = { parentId, titles ->
                        existingCalls += 1
                        assertEquals(41L, parentId)
                        receivedTitles = titles
                        BreakdownTransactionResult(
                            BreakdownTransactionStatus.SUCCESS,
                            parent,
                            titles.size
                        )
                    },
                    newRoot = { _, _ ->
                        newCalls += 1
                        error("unexpected new root")
                    }
                ),
                reminderScheduler = BreakdownReminderScheduler {
                    reminderCalls += 1
                    true
                }
            )

            val result = coordinator.persist(existingSave(parent))

            assertEquals(BreakdownSaveResultCategory.SUCCESS, result.category)
            assertEquals(1, existingCalls)
            assertEquals(0, newCalls)
            assertEquals(0, reminderCalls)
            assertEquals(steps(), receivedTitles)
            assertEquals(2, result.insertedCount)
        }

    @Test
    fun newRootConfirmationPersistsOneAtomicBatchThenOnlyRootReminder() =
        runBlocking {
            val calls = mutableListOf<String>()
            var storedParent: TaskEntity? = null
            var storedTitles: List<String> = emptyList()
            val insertedParent = TaskEntity(
                id = 88,
                title = "Prepare presentation",
                dueDate = "31/07/2026",
                dueTime = "3:00 PM"
            )
            val coordinator = BreakdownPersistenceCoordinator(
                store = fakeStore(
                    newRoot = { parent, titles ->
                        calls += "transaction"
                        storedParent = parent
                        storedTitles = titles
                        BreakdownTransactionResult(
                            BreakdownTransactionStatus.SUCCESS,
                            insertedParent,
                            titles.size
                        )
                    }
                ),
                reminderScheduler = BreakdownReminderScheduler { task ->
                    calls += "reminder"
                    assertEquals(insertedParent, task)
                    true
                }
            )

            val result = coordinator.persist(newSave())

            assertEquals(BreakdownSaveResultCategory.SUCCESS, result.category)
            assertEquals(listOf("transaction", "reminder"), calls)
            assertEquals("Prepare presentation", storedParent!!.title)
            assertEquals("31/07/2026", storedParent!!.dueDate)
            assertEquals("3:00 PM", storedParent!!.dueTime)
            assertEquals(steps(), storedTitles)
            assertTrue(result.reminderScheduled)
        }

    @Test
    fun committedNewRootWithFailedReminderReturnsPartialSuccess() =
        runBlocking {
            val insertedParent = TaskEntity(
                id = 88,
                title = "Prepare presentation",
                dueDate = "31/07/2026",
                dueTime = "3:00 PM"
            )
            val coordinator = BreakdownPersistenceCoordinator(
                store = fakeStore(
                    newRoot = { _, titles ->
                        BreakdownTransactionResult(
                            BreakdownTransactionStatus.SUCCESS,
                            insertedParent,
                            titles.size
                        )
                    }
                ),
                reminderScheduler = BreakdownReminderScheduler { false }
            )

            val result = coordinator.persist(newSave())

            assertEquals(
                BreakdownSaveResultCategory.PARTIAL_REMINDER_FAILURE,
                result.category
            )
            assertEquals(2, result.insertedCount)
            assertFalse(result.reminderScheduled)
        }

    @Test
    fun parentChangeAndExistingChildrenReportZeroInsertionsAndNoReminder() =
        runBlocking {
            listOf(
                BreakdownTransactionStatus.PARENT_CHANGED to
                    BreakdownSaveResultCategory.PARENT_CHANGED,
                BreakdownTransactionStatus.ALREADY_HAS_SUBTASKS to
                    BreakdownSaveResultCategory.ALREADY_HAS_SUBTASKS
            ).forEach { (transactionStatus, expected) ->
                var reminderCalls = 0
                val coordinator = BreakdownPersistenceCoordinator(
                    store = fakeStore(
                        existing = { _, _ ->
                            BreakdownTransactionResult(transactionStatus)
                        }
                    ),
                    reminderScheduler = BreakdownReminderScheduler {
                        reminderCalls += 1
                        true
                    }
                )

                val result = coordinator.persist(
                    existingSave(
                        TaskEntity(id = 41, title = "Final year project")
                    )
                )

                assertEquals(expected, result.category)
                assertEquals(0, result.insertedCount)
                assertEquals(0, reminderCalls)
                assertFalse(result.reminderScheduled)
            }
        }

    private fun fakeStore(
        existing: suspend (Long, List<String>) -> BreakdownTransactionResult =
            { _, _ -> error("unexpected existing root") },
        newRoot: suspend (TaskEntity, List<String>) -> BreakdownTransactionResult =
            { _, _ -> error("unexpected new root") }
    ) = object : BreakdownPersistenceStore {
        override suspend fun insertSubtasksIntoExistingRootAtomically(
            parentTaskId: Long,
            subtaskTitles: List<String>
        ): BreakdownTransactionResult = existing(parentTaskId, subtaskTitles)

        override suspend fun insertNewRootWithSubtasksAtomically(
            parent: TaskEntity,
            subtaskTitles: List<String>
        ): BreakdownTransactionResult = newRoot(parent, subtaskTitles)
    }

    private fun existingSave(parent: TaskEntity): PendingBreakdownSave {
        val controller = BreakdownDraftController()
        val resolving = begin(controller, parent.title)
        controller.applyExistingRoot(
            resolving.generation,
            parent,
            hasSubtasks = false
        )
        return requireNotNull(controller.markSaving())
    }

    private fun newSave(): PendingBreakdownSave {
        val controller = BreakdownDraftController()
        val resolving = begin(controller, "Prepare presentation")
        controller.applyNewRoot(resolving.generation)
        return requireNotNull(
            controller.markSaving("31/07/2026", "3:00 PM")
        )
    }

    private fun begin(
        controller: BreakdownDraftController,
        title: String
    ): PendingBreakdownDraft =
        (controller.beginDraft(
            parentTitle = title,
            proposedSubtasks = steps(),
            originalRequest = "break it down",
            dateText = null,
            timeText = null
        ) as BreakdownDraftUpdate.Resolving).draft

    private fun steps() = listOf("Draft slides", "Practise delivery")
}
