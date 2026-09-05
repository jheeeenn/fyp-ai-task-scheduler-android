package com.example.myapplication.ai

enum class TaskQueryPresentationSource {
    BOUNDED_QUERY_PRESENTATION,
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
        taskAgentValue: TaskQueryPresentation,
        boundedSemantic: TaskQueryPresentation = TaskQueryPresentation.NONE
    ): TaskQueryPresentationResolution {
        if (taskAgentIntent != AiIntent.QUERY_TASK.name) {
            return TaskQueryPresentationResolution(
                effective = TaskQueryPresentation.NONE,
                source = TaskQueryPresentationSource.NON_QUERY_IGNORED
            )
        }
        if (boundedSemantic != TaskQueryPresentation.NONE) {
            return TaskQueryPresentationResolution(
                effective = boundedSemantic,
                source = TaskQueryPresentationSource.BOUNDED_QUERY_PRESENTATION
            )
        }
        if (conversationHint != TaskQueryPresentation.NONE) {
            return TaskQueryPresentationResolution(
                effective = conversationHint,
                source = TaskQueryPresentationSource.CONVERSATION_AGENT_HINT
            )
        }
        if (taskAgentValue != TaskQueryPresentation.NONE) {
            return TaskQueryPresentationResolution(
                effective = taskAgentValue,
                source = TaskQueryPresentationSource.TASK_AGENT
            )
        }
        return TaskQueryPresentationResolution(
            effective = TaskQueryPresentation.OVERVIEW,
            source = TaskQueryPresentationSource.SAFE_DEFAULT
        )
    }
}
