package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationContextFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextFocusCarryForwardPolicyTest {
    @Test
    fun focusedReadOnlyQuestionsCarryOnlyTheRequestedDetail() {
        val expected = mapOf(
            "what time is it" to ConversationContextDetail.TIME,
            "what date is it" to ConversationContextDetail.DATE,
            "is it completed" to ConversationContextDetail.STATUS,
            "how many subtasks does it have" to ConversationContextDetail.SUBTASKS,
            "what was it called" to ConversationContextDetail.TITLE,
            "tell me about it" to ConversationContextDetail.SUMMARY
        )

        expected.forEach { (utterance, detail) ->
            val decision = ContextFocusCarryForwardPolicy.resolve(
                utterance,
                focus(),
                snapshot(),
                isResultInteraction = true
            )
            assertEquals("T2", decision?.contextRef)
            assertEquals(detail, decision?.contextDetail)
            assertEquals(ContextFocusCarryForwardPolicy.SOURCE, decision?.source)
        }
    }

    @Test
    fun mutationAndDifferentExplicitRefNeverCarryFocus() {
        assertNull(resolve("move it to Friday"))
        assertNull(resolve("delete it"))
        assertNull(resolve("mark it complete"))
        assertNull(resolve("what time is the third one"))
        assertNull(resolve("what time is T3"))
    }

    @Test
    fun staleMissingOrNonResultFocusNeverCarries() {
        assertNull(
            ContextFocusCarryForwardPolicy.resolve(
                "what time is it",
                focus().copy(generation = 2),
                snapshot(),
                isResultInteraction = true
            )
        )
        assertNull(
            ContextFocusCarryForwardPolicy.resolve(
                "what time is it",
                focus().copy(ref = "T9"),
                snapshot(),
                isResultInteraction = true
            )
        )
        assertNull(
            ContextFocusCarryForwardPolicy.resolve(
                "what time is it",
                focus(),
                snapshot(),
                isResultInteraction = false
            )
        )
        assertNull(resolve("how are you"))
    }

    @Test
    fun fallbackPassesExistingValidatorAndRenderer() {
        val decision = requireNotNull(resolve("what time is it"))
        val validation = ReadOnlyTaskContextReadValidator.validate(
            decision,
            snapshot(),
            currentGeneration = 1
        )

        assertTrue(validation.isValid)
        assertEquals(
            "Groceries is scheduled at 8:30 PM.",
            ReadOnlyTaskContextResponseRenderer.render(
                requireNotNull(validation.item),
                validation.detail
            )
        )
    }

    @Test
    fun seededTaskDetailFocusResolvesDeviceTestQuestionsToT1() {
        val taskDetailSnapshot = snapshot().copy(
            scope = TaskContextScope.TASK_DETAIL,
            items = listOf(
                item("T1", "Dentist").copy(relativeStatus = "Overdue")
            )
        )
        val taskDetailFocus = focus().copy(ref = "T1", title = "Dentist")
        val expected = mapOf(
            "what is the time" to ConversationContextDetail.TIME,
            "what date is it" to ConversationContextDetail.DATE,
            "is it overdue" to ConversationContextDetail.STATUS,
            "how many subtasks are unfinished" to ConversationContextDetail.SUBTASKS,
            "read the task" to ConversationContextDetail.SUMMARY
        )

        expected.forEach { (utterance, detail) ->
            val decision = requireNotNull(
                ContextFocusCarryForwardPolicy.resolve(
                    utterance,
                    taskDetailFocus,
                    taskDetailSnapshot,
                    isResultInteraction = true
                )
            )
            assertEquals("T1", decision.contextRef)
            assertEquals(detail, decision.contextDetail)
        }
    }

    private fun resolve(text: String) = ContextFocusCarryForwardPolicy.resolve(
        text,
        focus(),
        snapshot(),
        isResultInteraction = true
    )

    private fun focus() = ConversationContextFocus(
        available = true,
        ref = "T2",
        generation = 1,
        detail = ConversationContextDetail.TIME,
        title = "Groceries"
    )

    private fun snapshot() = ReadOnlyTaskContextSnapshot(
        scope = TaskContextScope.RECENT_QUERY_RESULTS,
        generation = 1,
        items = listOf(
            item("T1", "Medicine"),
            item("T2", "Groceries"),
            item("T3", "Podcast")
        ),
        truncated = false
    )

    private fun item(ref: String, title: String) = ReadOnlyTaskContextItem(
        ref = ref,
        title = title,
        dueDate = "23/07/2026",
        dueTime = "8:30 PM",
        isDone = false,
        subtaskCount = 2,
        unfinishedSubtaskCount = 1
    )
}
