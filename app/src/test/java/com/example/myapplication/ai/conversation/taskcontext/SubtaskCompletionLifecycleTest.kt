package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute
import com.example.myapplication.ai.conversation.ConversationSessionMemory
import com.example.myapplication.ai.conversation.ExecutionObservation
import com.example.myapplication.ai.conversation.ExecutionOperation
import com.example.myapplication.ai.conversation.ExecutionOutcome
import com.example.myapplication.data.TaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtaskCompletionLifecycleTest {
    private val parentId = 41L
    private val children = listOf(
        child(101, "Create outline", 0),
        child(102, "Prepare slides", 1),
        child(103, "Rehearse demo", 2)
    )

    @Test
    fun completeReadReopenReadKeepsStableOrdinalsAndChildFocus() = runBlocking {
        val store = ReadOnlyTaskContextStore().apply { replaceSubtaskList(children) }
        val initial = store.capture()
        val complete = actionDecision("T3", ConversationContextAction.MARK_DONE)

        assertEquals(
            ContextActionReferenceGroundingResult.VALID_ORDINAL,
            ground("Mark the third subtasks as done", complete, initial.snapshot).result
        )
        assertTrue(validate(complete, initial, store).isValid)
        assertEquals(children[2].id, store.resolveRef("T3", initial.snapshot.generation))
        assertTrue(store.matchesResolvedTask("T3", initial.snapshot.generation, children[2]))

        val completePlan = TaskCompletionMutationPolicy.plan(
            children[2],
            ConversationContextAction.MARK_DONE
        )
        assertTrue(completePlan.requiresMutation)
        assertFalse(completePlan.propagateToSubtasks)
        assertEquals(TaskCompletionReminderDirective.NONE, completePlan.reminderDirective)

        val completedChildren = children.toMutableList().apply {
            this[2] = this[2].copy(isDone = true)
        }
        val completed = requireNotNull(
            SubtaskCompletionContextRefresher(store, { completedChildren }, { true }).refresh(
                parentId,
                children[2].id,
                initial.snapshot.generation
            )
        )
        assertEquals(listOf("T1", "T2", "T3"), completed.capture.snapshot.items.map { it.ref })
        assertEquals(listOf(false, false, true), completed.capture.snapshot.items.map { it.isDone })
        assertEquals("T3", completed.affectedItem.ref)

        val memory = ConversationSessionMemory()
        recordCompletionThenFocus(memory, completed, completedChildren[2], true)
        assertEquals(
            "T3",
            memory.contextFocusForGeneration(
                completed.capture.snapshot.generation,
                completed.capture.snapshot.items.map { it.ref }.toSet()
            )?.ref
        )
        assertStatusRead("Is the third one completed?", completed.capture, store, expectedDone = true)

        val reopen = actionDecision("T3", ConversationContextAction.MARK_UNDONE)
        assertEquals(
            ContextActionReferenceGroundingResult.VALID_ORDINAL,
            ground("Reopen the third subtask", reopen, completed.capture.snapshot).result
        )
        val reopenPlan = TaskCompletionMutationPolicy.plan(
            completedChildren[2],
            ConversationContextAction.MARK_UNDONE
        )
        assertFalse(reopenPlan.propagateToSubtasks)
        assertEquals(TaskCompletionReminderDirective.NONE, reopenPlan.reminderDirective)

        val reopenedChildren = completedChildren.toMutableList().apply {
            this[2] = this[2].copy(isDone = false)
        }
        val reopened = requireNotNull(
            SubtaskCompletionContextRefresher(store, { reopenedChildren }, { true }).refresh(
                parentId,
                children[2].id,
                completed.capture.snapshot.generation
            )
        )
        recordCompletionThenFocus(memory, reopened, reopenedChildren[2], false)
        assertStatusRead("Is the third one completed?", reopened.capture, store, expectedDone = false)
    }

    @Test
    fun alreadyInStateStillRefreshesCompletedSiblingsWithoutReminderWork() = runBlocking {
        val completedChildren = children.mapIndexed { index, task ->
            if (index == 2) task.copy(isDone = true) else task
        }
        val store = ReadOnlyTaskContextStore().apply { replaceSubtaskList(completedChildren) }
        val before = store.capture()
        val plan = TaskCompletionMutationPolicy.plan(
            completedChildren[2],
            ConversationContextAction.MARK_DONE
        )

        assertFalse(plan.requiresMutation)
        assertFalse(plan.propagateToSubtasks)
        assertEquals(TaskCompletionReminderDirective.NONE, plan.reminderDirective)
        val refreshed = requireNotNull(
            SubtaskCompletionContextRefresher(store, { completedChildren }, { true }).refresh(
                parentId,
                completedChildren[2].id,
                before.snapshot.generation
            )
        )
        assertTrue(refreshed.affectedItem.isDone)
        assertEquals(3, refreshed.capture.snapshot.items.size)
        assertTrue(refreshed.capture.snapshot.generation > before.snapshot.generation)
    }

    @Test
    fun staleContextNewerQueryAndWrongParentFailWithoutReplacingContext() = runBlocking {
        val store = ReadOnlyTaskContextStore().apply { replaceSubtaskList(children) }
        val staleGeneration = store.currentGeneration()
        store.clear()
        assertNull(
            SubtaskCompletionContextRefresher(store, { error("must not fetch") }, { true }).refresh(
                parentId,
                children[2].id,
                staleGeneration
            )
        )

        store.replaceSubtaskList(children)
        val capturedGeneration = store.currentGeneration()
        var current = true
        val newerQuery = SubtaskCompletionContextRefresher(
            store,
            getSubtasks = {
                current = false
                children
            },
            isCurrent = { current }
        ).refresh(parentId, children[2].id, capturedGeneration)
        assertNull(newerQuery)
        assertEquals(capturedGeneration, store.currentGeneration())

        current = true
        val wrongParent = children.toMutableList().apply {
            this[1] = this[1].copy(parentTaskId = 999)
        }
        assertNull(
            SubtaskCompletionContextRefresher(store, { wrongParent }, { current }).refresh(
                parentId,
                children[2].id,
                capturedGeneration
            )
        )
        assertEquals(capturedGeneration, store.currentGeneration())
    }

    @Test
    fun mismatchedModelRefAndChangedRoomRecordAreRejected() {
        val store = ReadOnlyTaskContextStore().apply { replaceSubtaskList(children) }
        val capture = store.capture()
        val mismatched = actionDecision("T2", ConversationContextAction.MARK_DONE)

        assertEquals(
            ContextActionReferenceGroundingResult.SELECTED_REF_MISMATCH,
            ground("Mark the third subtask as done", mismatched, capture.snapshot).result
        )
        assertFalse(
            store.matchesResolvedTask(
                "T3",
                capture.snapshot.generation,
                children[2].copy(title = "Changed elsewhere")
            )
        )
    }

    private fun recordCompletionThenFocus(
        memory: ConversationSessionMemory,
        refreshed: RefreshedSubtaskCompletionContext,
        task: TaskEntity,
        done: Boolean
    ) {
        memory.recordObservation(
            ExecutionObservation(
                operation = if (done) ExecutionOperation.MARK_DONE else ExecutionOperation.MARK_UNDONE,
                outcome = ExecutionOutcome.SUCCESS,
                taskTitle = task.title,
                listenAgain = true,
                fallbackSpeech = "Done"
            )
        )
        memory.setAuthoritativeContextFocus(
            refreshed.affectedItem,
            refreshed.affectedItem.ref,
            refreshed.capture.snapshot.generation
        )
    }

    private fun assertStatusRead(
        text: String,
        capture: ReadOnlyTaskContextCapture,
        store: ReadOnlyTaskContextStore,
        expectedDone: Boolean
    ) {
        val decision = ConversationDecision(
            route = ConversationRoute.CONTEXT_READ,
            contextRef = "T3",
            contextDetail = ConversationContextDetail.STATUS,
            confidence = 0.98,
            listenAgain = true
        )
        val validated = ReadOnlyTaskContextReadValidator.validate(
            decision,
            capture.snapshot,
            store.currentGeneration(),
            text
        )
        assertTrue(validated.isValid)
        assertEquals(expectedDone, requireNotNull(validated.item).isDone)
        assertEquals(
            if (expectedDone) "Rehearse demo is completed." else "Rehearse demo is active.",
            ReadOnlyTaskContextResponseRenderer.render(
                requireNotNull(validated.item),
                ConversationContextDetail.STATUS
            )
        )
    }

    private fun validate(
        decision: ConversationDecision,
        capture: ReadOnlyTaskContextCapture,
        store: ReadOnlyTaskContextStore
    ) = ContextActionDecisionValidator.validate(
        decision,
        capture.snapshot,
        store.currentGeneration()
    )

    private fun ground(
        text: String,
        decision: ConversationDecision,
        snapshot: ReadOnlyTaskContextSnapshot
    ) = ContextActionReferenceGroundingValidator.validate(text, decision, snapshot, null)

    private fun actionDecision(ref: String, action: ConversationContextAction) = ConversationDecision(
        route = ConversationRoute.CONTEXT_ACTION,
        contextRef = ref,
        contextAction = action,
        confidence = 0.98,
        listenAgain = true
    )

    private fun child(id: Long, title: String, order: Int) = TaskEntity(
        id = id,
        title = title,
        parentTaskId = parentId,
        subtaskOrder = order
    )
}
