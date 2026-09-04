package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationContextFocus
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextActionAuthorityBindingPolicyTest {
    @Test
    fun incorrectModelRefBindsOnlyTheRefToValidSingleDeicticFocus() {
        val binding = reconcile()

        assertEquals(ContextActionAuthorityBindingResult.BOUND_TO_CURRENT_FOCUS, binding.result)
        assertEquals("T1", binding.decision.contextRef)
        assertEquals(ConversationContextAction.RESCHEDULE, binding.decision.contextAction)
        assertEquals(ConversationRoute.CONTEXT_ACTION, binding.decision.route)
    }

    @Test
    fun correctAndBlankModelRefsRemainUnchanged() {
        listOf("T1", "").forEach { ref ->
            val binding = reconcile(decision = defaultDecision.copy(contextRef = ref))

            assertEquals(ref, binding.decision.contextRef)
            assertEquals(ContextActionAuthorityBindingResult.UNCHANGED, binding.result)
        }
    }

    @Test
    fun multipleSuppliedRefsNeverDefaultToFocusedT1() {
        val multiSnapshot = snapshot.copy(items = listOf(item("T1"), item("T2")))
        val binding = reconcile(capturedSnapshot = multiSnapshot)

        assertUnchangedInvalidRef(binding)
    }

    @Test
    fun missingOrUnavailableFocusDoesNotBind() {
        listOf(
            null,
            focus.copy(available = false)
        ).forEach { candidateFocus ->
            assertUnchangedInvalidRef(reconcile(contextFocus = candidateFocus))
        }
    }

    @Test
    fun staleOrMismatchedFocusDoesNotBind() {
        listOf(
            focus.copy(generation = snapshot.generation - 1),
            focus.copy(ref = "T2")
        ).forEach { candidateFocus ->
            assertUnchangedInvalidRef(reconcile(contextFocus = candidateFocus))
        }
    }

    @Test
    fun explicitConflictingTemporaryRefOrOrdinalCannotBeOverridden() {
        listOf(
            "move T2 to friday",
            "move it, the second task, to friday",
            "move it, the latter task, to friday"
        ).forEach { utterance ->
            assertUnchangedInvalidRef(reconcile(normalizedText = utterance))
        }
    }

    @Test
    fun namedMutationDoesNotClaimFocusAuthority() {
        assertUnchangedInvalidRef(reconcile(normalizedText = "move dentist to friday"))
    }

    @Test
    fun nonContextActionAndNoneActionRemainUnchanged() {
        listOf(
            defaultDecision.copy(route = ConversationRoute.TASK_COMMAND),
            defaultDecision.copy(contextAction = ConversationContextAction.NONE)
        ).forEach { candidate ->
            assertUnchangedInvalidRef(reconcile(decision = candidate))
        }
    }

    @Test
    fun truncatedSingleItemSnapshotDoesNotClaimUniqueAuthority() {
        assertUnchangedInvalidRef(reconcile(capturedSnapshot = snapshot.copy(truncated = true)))
    }

    private fun reconcile(
        normalizedText: String = "move it to friday",
        decision: ConversationDecision = defaultDecision,
        capturedSnapshot: ReadOnlyTaskContextSnapshot = snapshot,
        contextFocus: ConversationContextFocus? = focus
    ) = ContextActionAuthorityBindingPolicy.reconcile(
        normalizedText = normalizedText,
        decision = decision,
        capturedSnapshot = capturedSnapshot,
        contextFocus = contextFocus
    )

    private fun assertUnchangedInvalidRef(binding: ContextActionAuthorityBinding) {
        assertEquals(ContextActionAuthorityBindingResult.UNCHANGED, binding.result)
        assertEquals("T7", binding.decision.contextRef)
    }

    private companion object {
        val defaultDecision = ConversationDecision(
            route = ConversationRoute.CONTEXT_ACTION,
            contextRef = "T7",
            contextAction = ConversationContextAction.RESCHEDULE,
            confidence = 0.97
        )
        val snapshot = ReadOnlyTaskContextSnapshot(
            scope = TaskContextScope.RECENT_QUERY_RESULTS,
            generation = 4,
            items = listOf(item("T1")),
            truncated = false
        )
        val focus = ConversationContextFocus(
            available = true,
            ref = "T1",
            generation = snapshot.generation,
            detail = ConversationContextDetail.SUMMARY,
            title = "Dentist"
        )

        fun item(ref: String) = ReadOnlyTaskContextItem(
            ref = ref,
            title = if (ref == "T1") "Dentist" else "Groceries",
            dueDate = "",
            dueTime = "",
            isDone = false,
            subtaskCount = 0,
            unfinishedSubtaskCount = 0
        )
    }
}
