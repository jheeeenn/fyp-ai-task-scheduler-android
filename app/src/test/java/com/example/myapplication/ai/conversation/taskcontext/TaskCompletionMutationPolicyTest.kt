package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCompletionMutationPolicyTest {
    @Test
    fun activeRootCompletionPropagatesAndCancelsReminderSequence() {
        val plan = TaskCompletionMutationPolicy.plan(task(isDone = false), ConversationContextAction.MARK_DONE)

        assertTrue(plan.requiresMutation)
        assertTrue(plan.desiredDone)
        assertTrue(plan.propagateToSubtasks)
        assertEquals(TaskCompletionReminderDirective.CANCEL_ROOT_SEQUENCE, plan.reminderDirective)
    }

    @Test
    fun completedRootReopenPropagatesAndReschedulesIfEligible() {
        val plan = TaskCompletionMutationPolicy.plan(task(isDone = true), ConversationContextAction.MARK_UNDONE)

        assertTrue(plan.requiresMutation)
        assertFalse(plan.desiredDone)
        assertTrue(plan.propagateToSubtasks)
        assertEquals(TaskCompletionReminderDirective.RESCHEDULE_ROOT_IF_ELIGIBLE, plan.reminderDirective)
    }

    @Test
    fun alreadyMatchingStateIsANoOp() {
        val alreadyDone = TaskCompletionMutationPolicy.plan(
            task(isDone = true), ConversationContextAction.MARK_DONE
        )
        val alreadyActive = TaskCompletionMutationPolicy.plan(
            task(isDone = false), ConversationContextAction.MARK_UNDONE
        )

        assertFalse(alreadyDone.requiresMutation)
        assertFalse(alreadyActive.requiresMutation)
        assertEquals(TaskCompletionReminderDirective.NONE, alreadyDone.reminderDirective)
        assertEquals(TaskCompletionReminderDirective.NONE, alreadyActive.reminderDirective)
    }

    @Test
    fun subtaskMutationDoesNotPropagateOrChangeRootReminder() {
        val plan = TaskCompletionMutationPolicy.plan(
            task(isDone = false, parentTaskId = 7), ConversationContextAction.MARK_DONE
        )

        assertTrue(plan.requiresMutation)
        assertFalse(plan.propagateToSubtasks)
        assertEquals(TaskCompletionReminderDirective.NONE, plan.reminderDirective)
    }

    private fun task(isDone: Boolean, parentTaskId: Long? = null) = TaskEntity(
        id = 9,
        title = "Software Revision",
        dueDate = "20/08/2026",
        dueTime = "05:00 PM",
        isDone = isDone,
        parentTaskId = parentTaskId
    )
}
