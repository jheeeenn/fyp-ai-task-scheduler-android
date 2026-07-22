package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.data.TaskEntity

object ContextActionTargetValidator {
    fun isEligible(task: TaskEntity?): Boolean =
        task != null && task.parentTaskId == null && !task.isDone
}
