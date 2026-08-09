package com.example.myapplication.ai

enum class TaskQueryPresentationSource {
    CONVERSATION_AGENT_HINT,
    TASK_AGENT,
    SAFE_DEFAULT,
    NON_QUERY_IGNORED
}

data class TaskQueryPresentationResolution(
    val effective: TaskQueryPresentation,
    val source: TaskQueryPresentationSource
)

object TaskQueryPresentationReconciler {
    fun reconcile(
        taskAgentIntent: String,
        conversationHint: TaskQueryPresentation,
        taskAgentValue: TaskQueryPresentation
    ): TaskQueryPresentationResolution {
        if (taskAgentIntent != AiIntent.QUERY_TASK.name) {
            return TaskQueryPresentationResolution(
                effective = TaskQueryPresentation.NONE,
                source = TaskQueryPresentationSource.NON_QUERY_IGNORED
            )
        }
        if (taskAgentValue != TaskQueryPresentation.NONE) {
            return TaskQueryPresentationResolution(
                effective = taskAgentValue,
                source = TaskQueryPresentationSource.TASK_AGENT
            )
        }
        if (conversationHint != TaskQueryPresentation.NONE) {
            return TaskQueryPresentationResolution(
                effective = conversationHint,
                source = TaskQueryPresentationSource.CONVERSATION_AGENT_HINT
            )
        }
        return TaskQueryPresentationResolution(
            effective = TaskQueryPresentation.OVERVIEW,
            source = TaskQueryPresentationSource.SAFE_DEFAULT
        )
    }
}
