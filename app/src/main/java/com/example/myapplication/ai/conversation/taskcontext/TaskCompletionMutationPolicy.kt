package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.data.TaskEntity

enum class TaskCompletionReminderDirective {
    NONE,
    CANCEL_ROOT_SEQUENCE,
    RESCHEDULE_ROOT_IF_ELIGIBLE
}

data class TaskCompletionMutationPlan(
    val desiredDone: Boolean,
    val requiresMutation: Boolean,
    val propagateToSubtasks: Boolean,
    val reminderDirective: TaskCompletionReminderDirective
)

/** Android-owned completion plan derived only from the current Room task state. */
object TaskCompletionMutationPolicy {
    fun plan(
        task: TaskEntity,
        action: ConversationContextAction
    ): TaskCompletionMutationPlan {
        val desiredDone = when (action) {
            ConversationContextAction.MARK_DONE -> true
            ConversationContextAction.MARK_UNDONE -> false
            else -> error("Completion policy requires MARK_DONE or MARK_UNDONE")
        }
        val isRoot = task.parentTaskId == null
        return TaskCompletionMutationPlan(
            desiredDone = desiredDone,
            requiresMutation = task.isDone != desiredDone,
            propagateToSubtasks = isRoot,
            reminderDirective = when {
                !isRoot || task.isDone == desiredDone -> TaskCompletionReminderDirective.NONE
                desiredDone -> TaskCompletionReminderDirective.CANCEL_ROOT_SEQUENCE
                else -> TaskCompletionReminderDirective.RESCHEDULE_ROOT_IF_ELIGIBLE
            }
        )
    }
}
