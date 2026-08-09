package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.data.TaskEntity

object ContextActionTargetValidator {
    fun isEligible(
        task: TaskEntity?,
        action: ConversationContextAction = ConversationContextAction.UPDATE
    ): Boolean = task != null &&
        task.parentTaskId == null &&
        (action == ConversationContextAction.DELETE || !task.isDone)
}
