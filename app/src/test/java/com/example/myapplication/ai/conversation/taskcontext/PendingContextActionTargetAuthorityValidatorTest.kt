package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingContextActionTargetAuthorityValidatorTest {
    @Test
    fun partialSemanticTitleCanSelectOnlyItsUniquelyCorroboratedRef() {
        assertAccepted("the medicine task", "T2", "T2")
        assertAccepted("medicine", "T2", "T2")
        assertAccepted("the breakfast one", "T1", "T1")

        val noMatch = validate("the dinner", selectedRef = "T2")
        assertFalse(noMatch.isAccepted)
        assertEquals(
            PendingContextActionTargetAuthorityResult.NO_TARGET_CORROBORATION,
            noMatch.result
        )
    }

    @Test
    fun ordinalAndExplicitRefRemainDeterministic() {
        assertAccepted("the second one", "T2", "T2")
        assertAccepted("T2", "T2", "T2")

        val ordinalMismatch = validate("the second one", selectedRef = "T1")
        val refMismatch = validate("T2", selectedRef = "T1")
        assertEquals(
            PendingContextActionTargetAuthorityResult.EXPLICIT_SELECTOR_MISMATCH,
            ordinalMismatch.result
        )
        assertEquals(
            PendingContextActionTargetAuthorityResult.EXPLICIT_SELECTOR_MISMATCH,
            refMismatch.result
        )
    }

    @Test
    fun staleGenerationChangedRefSetAndChangedActionFailClosed() {
        val stale = validate("medicine", "T2", currentGeneration = 9)
        val changedRefs = validate(
            text = "medicine",
            selectedRef = "T2",
            pendingRefs = setOf("T1")
        )
        val changedAction = validate(
            text = "medicine",
            selectedRef = "T2",
            pendingAction = ConversationContextAction.RESCHEDULE,
            candidateAction = ConversationContextAction.UPDATE
        )

        assertEquals(PendingContextActionTargetAuthorityResult.STALE_GENERATION, stale.result)
        assertEquals(
            PendingContextActionTargetAuthorityResult.SUPPLIED_REFS_CHANGED,
            changedRefs.result
        )
        assertEquals(PendingContextActionTargetAuthorityResult.ACTION_CHANGED, changedAction.result)
    }

    @Test
    fun ambiguousSharedPartialTokenCannotAuthorizeEitherRef() {
        val result = validate("take", selectedRef = "T1")
        assertEquals(
            PendingContextActionTargetAuthorityResult.AMBIGUOUS_PARTIAL_TITLE,
            result.result
        )
    }

    @Test
    fun lowConfidenceSelectionCannotCrossMutationAuthorityBoundary() {
        val interpretation = PendingContextActionTargetDecision(
            move = PendingContextActionTargetMove.SELECT_TARGET,
            contextRef = "T2",
            confidence = 0.85
        )
        val candidate = ConversationDecision(
            route = ConversationRoute.CONTEXT_ACTION,
            contextRef = "T2",
            contextAction = ConversationContextAction.RESCHEDULE,
            confidence = interpretation.confidence
        )
        val result = PendingContextActionTargetAuthorityValidator.validate(
            normalizedText = "medicine",
            interpretation = interpretation,
            candidate = candidate,
            pendingAction = ConversationContextAction.RESCHEDULE,
            pendingGeneration = 8,
            pendingSuppliedRefs = setOf("T1", "T2"),
            capturedSnapshot = snapshot(),
            currentGeneration = 8,
            currentFocus = null
        )

        assertEquals(PendingContextActionTargetAuthorityResult.LOW_CONFIDENCE, result.result)
    }

    private fun assertAccepted(text: String, selectedRef: String, expectedRef: String) {
        val result = validate(text, selectedRef)
        assertTrue(result.isAccepted)
        assertEquals(expectedRef, result.ref)
    }

    private fun validate(
        text: String,
        selectedRef: String,
        pendingRefs: Set<String> = setOf("T1", "T2"),
        pendingAction: ConversationContextAction = ConversationContextAction.RESCHEDULE,
        candidateAction: ConversationContextAction = pendingAction,
        currentGeneration: Long = 8
    ): PendingContextActionTargetAuthority {
        val interpretation = PendingContextActionTargetDecision(
            move = PendingContextActionTargetMove.SELECT_TARGET,
            contextRef = selectedRef,
            confidence = 0.97
        )
        val candidate = ConversationDecision(
            route = ConversationRoute.CONTEXT_ACTION,
            contextRef = selectedRef,
            contextAction = candidateAction,
            confidence = 0.97,
            source = "conversation_agent_pending_context_target"
        )
        return PendingContextActionTargetAuthorityValidator.validate(
            normalizedText = text,
            interpretation = interpretation,
            candidate = candidate,
            pendingAction = pendingAction,
            pendingGeneration = 8,
            pendingSuppliedRefs = pendingRefs,
            capturedSnapshot = snapshot(),
            currentGeneration = currentGeneration,
            currentFocus = null
        )
    }

    private fun snapshot() = ReadOnlyTaskContextSnapshot(
        scope = TaskContextScope.RECENT_QUERY_RESULTS,
        generation = 8,
        items = listOf(
            item("T1", "take breakfast"),
            item("T2", "take medicine")
        ),
        truncated = false
    )

    private fun item(ref: String, title: String) = ReadOnlyTaskContextItem(
        ref = ref,
        title = title,
        dueDate = "11/08/2026",
        dueTime = "6:00 PM",
        isDone = false,
        subtaskCount = 0,
        unfinishedSubtaskCount = 0
    )
}
