package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.breakdown.BreakdownDraftMode
import com.example.myapplication.ai.breakdown.BreakdownSaveResult
import com.example.myapplication.ai.breakdown.BreakdownSaveResultCategory
import com.example.myapplication.ai.conversation.ConversationSessionMemory
import com.example.myapplication.ai.conversation.ExecutionObservation
import com.example.myapplication.ai.conversation.ExecutionOperation
import com.example.myapplication.ai.conversation.ExecutionOutcome
import com.example.myapplication.data.TaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BreakdownPostSaveContextFocusPolicyTest {
    @Test
    fun acceptsOnlyTheCurrentPublishedTaskDetailGeneration() {
        val store = ReadOnlyTaskContextStore().apply {
            replaceTaskDetailResult(TaskEntity(id = 41, title = "Prepare presentation"))
        }
        val snapshot = store.snapshot()

        assertNotNull(
            BreakdownPostSaveContextFocusPolicy.authoritativeItemOrNull(
                snapshot,
                store.currentGeneration()
            )
        )
        store.clear()
        assertNull(
            BreakdownPostSaveContextFocusPolicy.authoritativeItemOrNull(
                snapshot,
                store.currentGeneration()
            )
        )
        assertNull(
            BreakdownPostSaveContextFocusPolicy.authoritativeItemOrNull(
                snapshot.copy(scope = TaskContextScope.SUBTASK_LIST),
                snapshot.generation
            )
        )
    }

    @Test
    fun partialSuccessObservationIsRecordedBeforeCurrentParentFocus() = runBlocking {
        val parent = TaskEntity(id = 41, title = "Prepare presentation")
        val children = listOf(
            TaskEntity(id = 42, title = "Draft slides", parentTaskId = parent.id)
        )
        val store = ReadOnlyTaskContextStore()
        val capture = requireNotNull(
            BreakdownTaskContextPublisher(store, { parent }, { children }).publish(
                BreakdownSaveResult(
                    category = BreakdownSaveResultCategory.PARTIAL_REMINDER_FAILURE,
                    mode = BreakdownDraftMode.NEW_ROOT,
                    requestedSubtaskCount = 1,
                    insertedCount = 1,
                    reminderScheduled = false,
                    parentTaskId = parent.id
                )
            )
        )
        val memory = ConversationSessionMemory()
        memory.recordObservation(
            ExecutionObservation(
                operation = ExecutionOperation.BREAKDOWN_TASK,
                outcome = ExecutionOutcome.PARTIAL_SUCCESS,
                taskTitle = parent.title,
                listenAgain = true,
                fallbackSpeech = "I created the breakdown, but could not schedule its reminder."
            )
        )
        val item = requireNotNull(
            BreakdownPostSaveContextFocusPolicy.authoritativeItemOrNull(
                capture.snapshot,
                store.currentGeneration()
            )
        )
        memory.setAuthoritativeContextFocus(item, item.ref, capture.snapshot.generation)

        assertEquals(
            "T1",
            memory.contextFocusForGeneration(
                store.currentGeneration(),
                capture.snapshot.items.map { it.ref }.toSet()
            )?.ref
        )
    }
}
