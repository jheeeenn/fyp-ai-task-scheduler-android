package com.example.myapplication.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskQueryPresentationReconcilerTest {
    @Test
    fun taskAgentPresentationOverridesConversationHintForQueryTask() {
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

        assertEquals(TaskQueryPresentation.COUNT_ONLY, taskCount.effective)
        assertEquals(TaskQueryPresentationSource.TASK_AGENT, taskCount.source)
        assertEquals(TaskQueryPresentation.OVERVIEW, taskOverview.effective)
        assertEquals(TaskQueryPresentationSource.TASK_AGENT, taskOverview.source)
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
    fun conversationHintIsUsedOnlyWhenTaskAgentValueIsNone() {
        val result = TaskQueryPresentationReconciler.reconcile(
            taskAgentIntent = AiIntent.QUERY_TASK.name,
            conversationHint = TaskQueryPresentation.OVERVIEW,
            taskAgentValue = TaskQueryPresentation.NONE
        )

        assertEquals(TaskQueryPresentation.OVERVIEW, result.effective)
        assertEquals(TaskQueryPresentationSource.CONVERSATION_AGENT_HINT, result.source)
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
