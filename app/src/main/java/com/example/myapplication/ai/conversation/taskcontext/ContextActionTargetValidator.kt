package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.data.TaskEntity

object ContextActionTargetValidator {
    fun isEligible(
        task: TaskEntity?,
        action: ConversationContextAction = ConversationContextAction.UPDATE
    ): Boolean = when (action) {
        ConversationContextAction.MARK_DONE,
        ConversationContextAction.MARK_UNDONE -> task != null
        ConversationContextAction.DELETE -> task != null && task.parentTaskId == null
        ConversationContextAction.UPDATE,
        ConversationContextAction.RESCHEDULE ->
            task != null && task.parentTaskId == null && !task.isDone
        ConversationContextAction.NONE -> false
    }
}
