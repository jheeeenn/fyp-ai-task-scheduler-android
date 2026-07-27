package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationRoute
import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextItemRestatementPolicyTest {
    @Test
    fun boundedRestatementPhrasesResolveTheirExplicitSuppliedSelectors() {
        val snapshot = fiveItemSnapshot(TaskContextScope.RECENT_QUERY_RESULTS)
        val examples = linkedMapOf(
            "repeat the fourth one" to "T4",
            "can you repeat the first one" to "T1",
            "say the second task again" to "T2",
            "read t3 again" to "T3",
            "tell me the fifth task again" to "T5",
            "what was the third one" to "T3"
        )

        examples.forEach { (text, expectedRef) ->
            val result = resolve(text, snapshot)

            assertEquals(text, ContextItemRestatementDisposition.RESOLVED, result.disposition)
            assertEquals(expectedRef, result.decision?.contextRef)
            assertEquals(ConversationRoute.CONTEXT_READ, result.decision?.route)
            assertEquals(ConversationContextDetail.SUMMARY, result.decision?.contextDetail)
            assertTrue(result.validation?.isValid == true)
        }
    }

    @Test
    fun restatementWorksForRecentQueryAndDailyBriefingScopes() {
        listOf(
            TaskContextScope.RECENT_QUERY_RESULTS,
            TaskContextScope.DAILY_BRIEFING
        ).forEach { scope ->
            val result = resolve("repeat the fourth one", fiveItemSnapshot(scope))

            assertEquals(ContextItemRestatementDisposition.RESOLVED, result.disposition)
            assertEquals("T4", result.decision?.contextRef)
        }
    }

    @Test
    fun genericAndPageRepeatHaveNoTargetedContextDecision() {
        val snapshot = fiveItemSnapshot(TaskContextScope.RECENT_QUERY_RESULTS)

        listOf(
            "repeat that",
            "can you say that again",
            "repeat your last answer",
            "repeat the group",
            "read this page again",
            "repeat the task list"
        ).forEach { text ->
            val result = resolve(text, snapshot)

            assertEquals(text, ContextItemRestatementDisposition.NOT_APPLICABLE, result.disposition)
            assertNull(result.decision)
        }
    }

    @Test
    fun unavailableSelectorReturnsAndroidClarificationWithoutChoosingAnotherItem() {
        val result = resolve(
            "repeat the sixth one",
            fiveItemSnapshot(TaskContextScope.RECENT_QUERY_RESULTS)
        )

        assertEquals(
            ContextItemRestatementDisposition.UNAVAILABLE_SELECTOR,
            result.disposition
        )
        assertEquals(
            ContextItemRestatementPolicy.UNAVAILABLE_CLARIFICATION,
            result.clarification
        )
        assertNull(result.decision)
        assertNull(result.validation)
    }

    @Test
    fun exactlyOneExplicitSelectorIsRequired() {
        val result = resolve(
            "repeat the first one and the second one",
            fiveItemSnapshot(TaskContextScope.RECENT_QUERY_RESULTS)
        )

        assertEquals(
            ContextItemRestatementDisposition.AMBIGUOUS_SELECTOR,
            result.disposition
        )
        assertEquals(
            ContextItemRestatementPolicy.AMBIGUOUS_CLARIFICATION,
            result.clarification
        )
        assertNull(result.decision)
    }

    @Test
    fun emptyOrUnsupportedContextCannotTriggerTargetedRestatement() {
        val empty = ReadOnlyTaskContextSnapshot(
            scope = TaskContextScope.RECENT_QUERY_RESULTS,
            generation = 3,
            items = emptyList(),
            truncated = false
        )
        val taskChoices = fiveItemSnapshot(TaskContextScope.TASK_MATCH_CHOICES)

        assertEquals(
            ContextItemRestatementDisposition.NOT_APPLICABLE,
            resolve("repeat the first one", empty).disposition
        )
        assertEquals(
            ContextItemRestatementDisposition.NOT_APPLICABLE,
            resolve("repeat the first one", taskChoices).disposition
        )
    }

    @Test
    fun resolutionKeepsReadOnlyContextGenerationUnchanged() {
        val store = ReadOnlyTaskContextStore()
        store.replaceRecentQueryResults(tasks())
        val capture = store.capture()
        val generation = store.currentGeneration()

        val result = ContextItemRestatementPolicy.resolve(
            normalizedText = "say the second task again",
            capturedSnapshot = capture.snapshot,
            currentGeneration = generation
        )

        assertEquals(ContextItemRestatementDisposition.RESOLVED, result.disposition)
        assertEquals(generation, store.currentGeneration())
        assertEquals(capture.snapshot, store.snapshot())
    }

    @Test
    fun staleGenerationFailsClosed() {
        val snapshot = fiveItemSnapshot(TaskContextScope.DAILY_BRIEFING)

        val result = ContextItemRestatementPolicy.resolve(
            normalizedText = "repeat the first one",
            capturedSnapshot = snapshot,
            currentGeneration = snapshot.generation + 1
        )

        assertEquals(
            ContextItemRestatementDisposition.UNAVAILABLE_SELECTOR,
            result.disposition
        )
        assertFalse(result.validation?.isValid == true)
    }

    private fun resolve(
        text: String,
        snapshot: ReadOnlyTaskContextSnapshot
    ): ContextItemRestatementResolution = ContextItemRestatementPolicy.resolve(
        normalizedText = text,
        capturedSnapshot = snapshot,
        currentGeneration = snapshot.generation
    )

    private fun fiveItemSnapshot(scope: TaskContextScope) =
        ReadOnlyTaskContextSnapshot(
            scope = scope,
            generation = 12,
            items = tasks().mapIndexed { index, task ->
                ReadOnlyTaskContextItem(
                    ref = "T${index + 1}",
                    title = task.title,
                    dueDate = task.dueDate.orEmpty(),
                    dueTime = task.dueTime.orEmpty(),
                    isDone = false,
                    subtaskCount = 0,
                    unfinishedSubtaskCount = 0
                )
            },
            truncated = false
        )

    private fun tasks() = (1L..5L).map { id ->
        TaskEntity(
            id = id,
            title = "Task $id",
            dueDate = "27/07/2026",
            dueTime = "${id + 8}:00"
        )
    }
}
