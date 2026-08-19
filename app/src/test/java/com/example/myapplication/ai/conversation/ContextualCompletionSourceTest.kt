package com.example.myapplication.ai.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContextualCompletionSourceTest {
    private val source = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()

    @Test
    fun completionRevalidatesGroundedTargetAndBypassesChangeExtraction() {
        val branch = source
            .substringAfter("ConversationRoute.CONTEXT_ACTION -> {")
            .substringBefore("ConversationRoute.QUERY_READING_CONTROL ->")
        val completion = branch.indexOf(
            "validation.action == ConversationContextAction.MARK_DONE"
        )
        val extraction = branch.indexOf("agentOrchestrator.processContextAction(")

        assertTrue(completion >= 0)
        assertTrue(completion < extraction)
        assertTrue(branch.contains("readOnlyTaskContextStore.currentGeneration() != capturedGeneration"))
        assertTrue(branch.contains("completionTaskId != privateTaskId"))
        assertTrue(branch.contains("sameContextActionTaskSnapshot(initiallyFetchedTask, completionTask)"))
        assertTrue(branch.contains("readOnlyTaskContextStore.matchesResolvedTask("))
        assertTrue(branch.contains("executeDeterministicTaskCompletion("))
    }

    @Test
    fun sharedExecutorOwnsNoOpPropagationAndReminderBehavior() {
        val executor = source
            .substringAfter("private suspend fun executeDeterministicTaskCompletion")
            .substringBefore("private fun rejectUnavailableContextAction")

        assertTrue(executor.contains("TaskCompletionMutationPolicy.plan(task, action)"))
        assertTrue(executor.contains("if (!plan.requiresMutation)"))
        assertTrue(executor.contains("result=ALREADY_IN_STATE"))
        assertTrue(executor.contains("dao.updateDoneStatusForTaskAndSubtasks"))
        assertTrue(executor.contains("dao.updateDoneStatus(task.id"))
        assertTrue(executor.contains("ReminderHelper.cancelReminder"))
        assertTrue(executor.contains("ReminderHelper.scheduleReminderFromTask"))
        assertTrue(executor.contains("refreshOverview()"))
        assertFalse(executor.contains("ConversationAgentClient"))
    }

    @Test
    fun namedCompletionPathsStillUseNormalTaskMatchingThenSharedExecution() {
        val normalActions = source
            .substringAfter("// branches for actions")
            .substringBefore("private fun askTaskMatchClarification")

        assertTrue(normalActions.contains("AiIntent.MARK_DONE.name"))
        assertTrue(normalActions.contains("AiIntent.MARK_UNDONE.name"))
        assertTrue(normalActions.contains("findTaskMatchResult(spokenPhrase, tasks)"))
        assertTrue(normalActions.contains("grounding = \"NAMED_TASK\""))
    }
}
