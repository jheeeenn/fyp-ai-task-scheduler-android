package com.example.myapplication.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskQueryPresentationReconcilerTest {
    @Test
    fun conversationHintOverridesTaskAgentPresentationForQueryTask() {
        val taskCount = TaskQueryPresentationReconciler.reconcile(
            taskAgentIntent = AiIntent.QUERY_TASK.name,
            conversationHint = TaskQueryPresentation.OVERVIEW,
            taskAgentValue = TaskQueryPresentation.COUNT_ONLY
        )
        val taskOverview = TaskQueryPresentationReconciler.reconcile(
            taskAgentIntent = AiIntent.QUERY_TASK.name,
            conversationHint = TaskQueryPresentation.COUNT_ONLY,
            taskAgentValue = TaskQueryPresentation.OVERVIEW
        )

        assertEquals(TaskQueryPresentation.OVERVIEW, taskCount.effective)
        assertEquals(TaskQueryPresentationSource.CONVERSATION_AGENT_HINT, taskCount.source)
        assertEquals(TaskQueryPresentation.COUNT_ONLY, taskOverview.effective)
        assertEquals(TaskQueryPresentationSource.CONVERSATION_AGENT_HINT, taskOverview.source)
    }

    @Test
    fun nonQueryPresentationIsAlwaysIgnored() {
        val nonQuery = TaskQueryPresentationReconciler.reconcile(
            taskAgentIntent = AiIntent.UPDATE_TASK.name,
            conversationHint = TaskQueryPresentation.DETAILS,
            taskAgentValue = TaskQueryPresentation.OVERVIEW
        )

        assertEquals(TaskQueryPresentation.NONE, nonQuery.effective)
        assertEquals(TaskQueryPresentationSource.NON_QUERY_IGNORED, nonQuery.source)
    }

    @Test
    fun taskAgentPresentationIsFallbackWhenConversationHintIsNone() {
        val result = TaskQueryPresentationReconciler.reconcile(
            taskAgentIntent = AiIntent.QUERY_TASK.name,
            conversationHint = TaskQueryPresentation.NONE,
            taskAgentValue = TaskQueryPresentation.DETAILS
        )

        assertEquals(TaskQueryPresentation.DETAILS, result.effective)
        assertEquals(TaskQueryPresentationSource.TASK_AGENT, result.source)
    }

    @Test
    fun missingQueryPresentationsSafelyFallBackToOverview() {
        val result = TaskQueryPresentationReconciler.reconcile(
            taskAgentIntent = AiIntent.QUERY_TASK.name,
            conversationHint = TaskQueryPresentation.NONE,
            taskAgentValue = TaskQueryPresentation.NONE
        )

        assertEquals(TaskQueryPresentation.OVERVIEW, result.effective)
        assertEquals(TaskQueryPresentationSource.SAFE_DEFAULT, result.source)
    }
}
