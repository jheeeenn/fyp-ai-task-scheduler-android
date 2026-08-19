package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute
import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextActionDecisionValidatorTest {
    @Test
    fun validatesCurrentKnownRefAndRejectsStaleOrUnknownRefs() {
        val store = ReadOnlyTaskContextStore()
        store.replaceRecentQueryResults(listOf(task(1), task(2)))
        val capture = store.capture()

        assertTrue(validate(capture, store, "T2").isValid)
        assertEquals(2L, store.resolveRef("T2", capture.snapshot.generation))
        assertEquals(null, store.resolveRef("T9", capture.snapshot.generation))

        store.replaceRecentQueryResults(listOf(task(3), task(4)))
        assertEquals(null, store.resolveRef("T2", capture.snapshot.generation))
        assertEquals(
            ContextActionValidationResult.STALE_GENERATION,
            validate(capture, store, "T2").result
        )
    }

    @Test
    fun validatorFailsClosedForUnknownLowConfidenceAndInvalidFields() {
        val store = ReadOnlyTaskContextStore()
        store.replaceRecentQueryResults(listOf(task(1)))
        val capture = store.capture()
        assertEquals(
            ContextActionValidationResult.UNKNOWN_REF,
            validate(capture, store, "T9").result
        )
        assertEquals(
            ContextActionValidationResult.LOW_CONFIDENCE,
            validate(capture, store, "T1", confidence = 0.89).result
        )
        val invalid = decision("T1").copy(taskText = "edit it")
        assertEquals(
            ContextActionValidationResult.INVALID_ROUTE_FIELDS,
            ContextActionDecisionValidator.validate(invalid, capture.snapshot, store.currentGeneration()).result
        )
    }

    @Test
    fun contextualDeleteIsValidOnlyForCurrentKnownRef() {
        val store = ReadOnlyTaskContextStore()
        store.replaceTaskDetailResult(task(1))
        val capture = store.capture()

        assertTrue(
            validate(
                capture,
                store,
                "T1",
                action = ConversationContextAction.DELETE
            ).isValid
        )
        assertEquals(
            ContextActionValidationResult.UNKNOWN_REF,
            validate(
                capture,
                store,
                "T9",
                action = ConversationContextAction.DELETE
            ).result
        )
        store.clear()
        assertEquals(
            ContextActionValidationResult.STALE_GENERATION,
            validate(
                capture,
                store,
                "T1",
                action = ConversationContextAction.DELETE
            ).result
        )
    }

    @Test
    fun contextualCompletionActionsValidateOnlyAfterGroundedKnownRefIsPresent() {
        val store = ReadOnlyTaskContextStore()
        store.replaceTaskDetailResult(task(1))
        val capture = store.capture()

        assertTrue(validate(capture, store, "T1", action = ConversationContextAction.MARK_DONE).isValid)
        assertTrue(validate(capture, store, "T1", action = ConversationContextAction.MARK_UNDONE).isValid)
        assertEquals(
            ContextActionValidationResult.UNKNOWN_REF,
            validate(capture, store, "", action = ConversationContextAction.MARK_DONE).result
        )
    }

    @Test
    fun privateRefSnapshotMustStillMatchTheRefetchedRoomTask() {
        val store = ReadOnlyTaskContextStore()
        val original = task(42).copy(title = "Private title", dueDate = "09/08/2026")
        store.replaceTaskDetailResult(original)
        val generation = store.currentGeneration()

        assertTrue(store.matchesResolvedTask("T1", generation, original))
        assertFalse(
            store.matchesResolvedTask(
                "T1",
                generation,
                original.copy(title = "Changed elsewhere")
            )
        )
        assertFalse(store.snapshotForPrompt().contains("42"))
    }

    @Test
    fun targetEligibilityIsActionSpecific() {
        assertFalse(ContextActionTargetValidator.isEligible(null))
        assertFalse(ContextActionTargetValidator.isEligible(task(1).copy(isDone = true)))
        assertFalse(ContextActionTargetValidator.isEligible(task(2).copy(parentTaskId = 1)))
        assertTrue(ContextActionTargetValidator.isEligible(task(3)))
        assertTrue(
            ContextActionTargetValidator.isEligible(
                task(4).copy(isDone = true),
                ConversationContextAction.DELETE
            )
        )
        assertTrue(
            ContextActionTargetValidator.isEligible(
                task(5).copy(isDone = true, parentTaskId = 1),
                ConversationContextAction.MARK_UNDONE
            )
        )
    }

    private fun validate(
        capture: ReadOnlyTaskContextCapture,
        store: ReadOnlyTaskContextStore,
        ref: String,
        confidence: Double = 0.97,
        action: ConversationContextAction = ConversationContextAction.RESCHEDULE
    ) = ContextActionDecisionValidator.validate(
        decision(ref).copy(confidence = confidence, contextAction = action),
        capture.snapshot,
        store.currentGeneration()
    )

    private fun decision(ref: String) = ConversationDecision(
        route = ConversationRoute.CONTEXT_ACTION,
        contextRef = ref,
        contextDetail = ConversationContextDetail.NONE,
        contextAction = ConversationContextAction.RESCHEDULE,
        confidence = 0.97,
        listenAgain = false
    )

    private fun task(id: Long) = TaskEntity(id = id, title = "Task $id")
}
