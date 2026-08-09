package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationContextFocus
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextDeleteFailureFallbackPolicyTest {
    @Test
    fun failedPrimaryAndRepairMayFallBackToFocusedContextualDelete() {
        val result = resolve(
            text = "delete this task",
            decision = ConversationDecision(
                route = ConversationRoute.ASK_CLARIFICATION,
                reply = "Which task?",
                source = "conversation_agent_repair"
            )
        )

        requireNotNull(result)
        assertEquals(ConversationRoute.CONTEXT_ACTION, result.route)
        assertEquals(ConversationContextAction.DELETE, result.contextAction)
        assertEquals("T1", result.contextRef)
        assertEquals(ContextDeleteFailureFallbackPolicy.SOURCE, result.source)
        assertTrue(
            ContextActionDecisionValidator.validate(result, snapshot, snapshot.generation).isValid
        )
        assertTrue(
            ContextActionReferenceGroundingValidator.validate(
                normalizedText = "delete this task",
                decision = result,
                capturedSnapshot = snapshot,
                currentFocus = focus
            ).isValid
        )
    }

    @Test
    fun fallbackRequiresPriorAgentAttemptDeleteWordingAndFocusExpression() {
        assertNull(resolve("delete this task", agentAttempted = false))
        assertNull(resolve("open this task"))
        assertNull(resolve("delete groceries"))
    }

    @Test
    fun staleOrMissingFocusRejectsFallback() {
        assertNull(resolve("remove it", currentFocus = focus.copy(generation = 8)))
        assertNull(resolve("remove it", currentFocus = null))
        assertNull(resolve("remove it", currentGeneration = 8))
    }

    @Test
    fun usableModelDeleteDoesNotGetReplacedByFallback() {
        val modelDelete = ConversationDecision(
            route = ConversationRoute.CONTEXT_ACTION,
            contextRef = "T1",
            contextAction = ConversationContextAction.DELETE,
            confidence = 0.97,
            source = "conversation_agent"
        )

        assertNull(resolve("delete this task", decision = modelDelete))
    }

    private fun resolve(
        text: String,
        decision: ConversationDecision = ConversationDecision(
            route = ConversationRoute.ASK_CLARIFICATION,
            source = "conversation_agent"
        ),
        currentFocus: ConversationContextFocus? = focus,
        currentGeneration: Long = snapshot.generation,
        agentAttempted: Boolean = true
    ) = ContextDeleteFailureFallbackPolicy.resolve(
        normalizedText = text,
        currentDecision = decision,
        capturedSnapshot = snapshot,
        currentGeneration = currentGeneration,
        currentFocus = currentFocus,
        agentAttempted = agentAttempted
    )

    private companion object {
        val snapshot = ReadOnlyTaskContextSnapshot(
            scope = TaskContextScope.TASK_DETAIL,
            generation = 7,
            items = listOf(
                ReadOnlyTaskContextItem(
                    ref = "T1",
                    title = "Groceries",
                    dueDate = "",
                    dueTime = "",
                    isDone = false,
                    subtaskCount = 0,
                    unfinishedSubtaskCount = 0
                )
            ),
            truncated = false
        )
        val focus = ConversationContextFocus(
            available = true,
            ref = "T1",
            generation = snapshot.generation,
            detail = ConversationContextDetail.SUMMARY,
            title = "Groceries"
        )
    }
}
