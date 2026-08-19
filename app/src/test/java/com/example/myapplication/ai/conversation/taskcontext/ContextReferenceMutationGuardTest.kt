package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextReferenceMutationGuardTest {
    private val twoItemSnapshot = ReadOnlyTaskContextSnapshot(
        scope = TaskContextScope.RECENT_QUERY_RESULTS,
        generation = 4,
        items = listOf(item("T1", "Medicine"), item("T2", "Groceries")),
        truncated = false
    )

    @Test
    fun blocksSuppliedResultOrdinalBeforeDelegation() {
        assertTrue(shouldBlock("delete the second one"))
    }

    @Test
    fun blocksCurrentTemporaryRefBeforeDelegation() {
        assertTrue(shouldBlock("mark T2 complete"))
        assertTrue(shouldBlock("mark t2 complete"))
        assertFalse(shouldBlock("mark T3 complete"))
    }

    @Test
    fun blocksDeicticTaskReferenceBeforeDelegation() {
        assertTrue(shouldBlock("move that task to Friday"))
        assertTrue(shouldBlock("move this one to Friday"))
        assertTrue(shouldBlock("delete it"))
        assertTrue(shouldBlock("reopen it"))
        assertTrue(shouldBlock("undo completion for this task"))
    }

    @Test
    fun explicitTaskTitleContinuesNormally() {
        assertFalse(shouldBlock("delete the medicine task"))
        assertFalse(shouldBlock("delete the fix it bug task"))
    }

    @Test
    fun blockedReferenceDoesNotReachDownstreamMutationPipeline() {
        var taskAgentReached = false
        var roomMutationReached = false
        val decision = taskCommand("move the second task to next day")

        if (!ContextReferenceMutationGuard.shouldBlock(
                decision = decision,
                currentUtterance = "move the second task to next day",
                snapshot = twoItemSnapshot
            )
        ) {
            taskAgentReached = true
            roomMutationReached = true
        }

        assertFalse(taskAgentReached)
        assertFalse(roomMutationReached)
    }

    @Test
    fun snapshotWithoutItemsDoesNotClaimAReferenceExists() {
        val emptySnapshot = ReadOnlyTaskContextSnapshot(
            scope = TaskContextScope.RECENT_QUERY_RESULTS,
            generation = 5,
            items = emptyList(),
            truncated = false
        )

        assertFalse(
            ContextReferenceMutationGuard.shouldBlock(
                decision = taskCommand("delete T1"),
                currentUtterance = "delete T1",
                snapshot = emptySnapshot
            )
        )
    }

    @Test
    fun guardOnlyAppliesToTaskCommandRoute() {
        assertFalse(
            ContextReferenceMutationGuard.shouldBlock(
                decision = taskCommand("what was the second one").copy(
                    route = ConversationRoute.DIRECT_REPLY
                ),
                currentUtterance = "what was the second one",
                snapshot = twoItemSnapshot
            )
        )
    }

    @Test
    fun returnedTaskTextIsGuardedEvenWhenOriginalUtteranceIsNot() {
        assertTrue(
            ContextReferenceMutationGuard.shouldBlock(
                decision = taskCommand("delete T2"),
                currentUtterance = "please handle my request",
                snapshot = twoItemSnapshot
            )
        )
    }

    @Test
    fun exposedReferenceDetectionDefersContextualCommandsToCentralPath() {
        assertTrue(
            ContextReferenceMutationGuard.containsContextReference(
                "move the second task to next day",
                twoItemSnapshot
            )
        )
        assertTrue(
            ContextReferenceMutationGuard.isUnsupportedMutationWording(
                "move the second task to next day",
                twoItemSnapshot
            )
        )
        assertFalse(
            ContextReferenceMutationGuard.containsContextReference(
                "delete the medicine task",
                twoItemSnapshot
            )
        )
    }

    private fun shouldBlock(text: String): Boolean =
        ContextReferenceMutationGuard.shouldBlock(
            decision = taskCommand(text),
            currentUtterance = text,
            snapshot = twoItemSnapshot
        )

    private fun taskCommand(taskText: String) = ConversationDecision(
        route = ConversationRoute.TASK_COMMAND,
        taskText = taskText,
        reply = "",
        confidence = 0.95,
        listenAgain = true
    )

    private fun item(ref: String, title: String) = ReadOnlyTaskContextItem(
        ref = ref,
        title = title,
        dueDate = "23/07/2026",
        dueTime = "11:00 AM",
        isDone = false,
        subtaskCount = 0,
        unfinishedSubtaskCount = 0
    )
}
