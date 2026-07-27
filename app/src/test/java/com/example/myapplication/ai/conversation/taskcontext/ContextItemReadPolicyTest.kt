package com.example.myapplication.ai.conversation.taskcontext

import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationRoute
import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextItemReadPolicyTest {
    @Test
    fun explicitSelectorsAndDetailsResolveDeterministically() {
        val examples = linkedMapOf(
            "what time is the fourth one" to Pair("T4", ConversationContextDetail.TIME),
            "what time is t2" to Pair("T2", ConversationContextDetail.TIME),
            "what date is the third task" to Pair("T3", ConversationContextDetail.DATE),
            "is the second one completed" to Pair("T2", ConversationContextDetail.STATUS),
            "what subtasks does the first one have" to
                Pair("T1", ConversationContextDetail.SUBTASKS),
            "what is the fourth task" to Pair("T4", ConversationContextDetail.SUMMARY),
            "what was the third one" to Pair("T3", ConversationContextDetail.SUMMARY)
        )

        examples.forEach { (text, expected) ->
            val result = resolve(text)

            assertEquals(text, ContextItemReadDisposition.RESOLVED, result.disposition)
            assertEquals(ConversationRoute.CONTEXT_READ, result.decision?.route)
            assertEquals(expected.first, result.decision?.contextRef)
            assertEquals(expected.second, result.decision?.contextDetail)
            assertTrue(result.validation?.isValid == true)
        }
    }

    @Test
    fun ambiguousSelectorOrDetailIsNotGuessed() {
        listOf(
            "what time and date is the second one",
            "what is the first one and the second one",
            "tell me about the fourth one",
            "what time is it",
            "what is the fourth task's time"
        ).forEach { text ->
            val result = resolve(text)

            assertEquals(text, ContextItemReadDisposition.NOT_APPLICABLE, result.disposition)
            assertNull(result.decision)
        }
    }

    @Test
    fun unavailableSelectorAndStaleGenerationFailClosed() {
        val snapshot = snapshot()

        val unavailable = ContextItemReadPolicy.resolve(
            normalizedText = "what time is t9",
            capturedSnapshot = snapshot,
            currentGeneration = snapshot.generation
        )
        val stale = ContextItemReadPolicy.resolve(
            normalizedText = "what time is t2",
            capturedSnapshot = snapshot,
            currentGeneration = snapshot.generation + 1
        )

        assertEquals(ContextItemReadDisposition.NOT_APPLICABLE, unavailable.disposition)
        assertEquals(ContextItemReadDisposition.NOT_APPLICABLE, stale.disposition)
    }

    @Test
    fun policyKeepsContextGenerationAndSnapshotUnchanged() {
        val store = ReadOnlyTaskContextStore()
        store.replaceDailyBriefingResults(tasks())
        val capture = store.capture()
        val generation = store.currentGeneration()

        val result = ContextItemReadPolicy.resolve(
            normalizedText = "what date is the third task",
            capturedSnapshot = capture.snapshot,
            currentGeneration = generation
        )

        assertEquals(ContextItemReadDisposition.RESOLVED, result.disposition)
        assertEquals(generation, store.currentGeneration())
        assertEquals(capture.snapshot, store.snapshot())
    }

    @Test
    fun emptyAndUnsupportedScopesAreNotApplicable() {
        val base = snapshot()
        val empty = base.copy(items = emptyList())
        val choices = base.copy(scope = TaskContextScope.TASK_MATCH_CHOICES)

        assertEquals(
            ContextItemReadDisposition.NOT_APPLICABLE,
            ContextItemReadPolicy.resolve("what time is t2", empty, empty.generation).disposition
        )
        assertEquals(
            ContextItemReadDisposition.NOT_APPLICABLE,
            ContextItemReadPolicy.resolve(
                "what time is t2",
                choices,
                choices.generation
            ).disposition
        )
    }

    private fun resolve(text: String): ContextItemReadResolution {
        val snapshot = snapshot()
        return ContextItemReadPolicy.resolve(text, snapshot, snapshot.generation)
    }

    private fun snapshot() = ReadOnlyTaskContextSnapshot(
        scope = TaskContextScope.DAILY_BRIEFING,
        generation = 19,
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
