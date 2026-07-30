package com.example.myapplication.ai.conversation.suggestion

import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationRoute
import com.example.myapplication.ai.conversation.ConversationSessionMemory
import com.example.myapplication.ai.conversation.ExecutionObservation
import com.example.myapplication.ai.conversation.ExecutionOperation
import com.example.myapplication.ai.conversation.ExecutionOutcome
import com.example.myapplication.ai.conversation.taskcontext.ContextFocusCarryForwardPolicy
import com.example.myapplication.ai.conversation.taskcontext.ContextItemReadDisposition
import com.example.myapplication.ai.conversation.taskcontext.ContextItemReadPolicy
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextStore
import com.example.myapplication.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ContextSuggestionFocusPolicyTest {
    @Test
    fun singleTaskSuggestionTypesEstablishT1AtPublishedGeneration() {
        listOf(
            ContextSuggestionType.FOCUS_TASK,
            ContextSuggestionType.CONTINUE_SUBTASK,
            ContextSuggestionType.BREAK_DOWN_TASK
        ).forEach { type ->
            val store = singleTaskStore()
            val snapshot = store.snapshot()
            val item = ContextSuggestionFocusPolicy.authoritativeItemOrNull(
                suggestionType = type,
                publishedSnapshot = snapshot,
                capturedGeneration = snapshot.generation,
                currentGeneration = store.currentGeneration()
            )
            val memory = ConversationSessionMemory()
            memory.recordObservation(suggestionObservation())
            memory.setAuthoritativeContextFocus(
                item = requireNotNull(item),
                selectedRef = "T1",
                capturedGeneration = snapshot.generation
            )

            val focus = memory.contextFocusForGeneration(
                currentGeneration = snapshot.generation,
                suppliedRefs = snapshot.items.map { it.ref }.toSet()
            )
            assertEquals(type.name, "T1", focus?.ref)
            assertEquals(type.name, snapshot.generation, focus?.generation)
            assertEquals(type.name, ConversationContextDetail.SUMMARY, focus?.detail)
            assertEquals(type.name, "Final year project", focus?.title)
        }
    }

    @Test
    fun observationMustBeRecordedBeforeFocusAndFocusAddsNoMemoryTurn() {
        val store = singleTaskStore()
        val snapshot = store.snapshot()
        val item = snapshot.items.single()
        val memory = ConversationSessionMemory()

        memory.setAuthoritativeContextFocus(item, "T1", snapshot.generation)
        memory.recordObservation(suggestionObservation())
        assertNull(
            memory.contextFocusForGeneration(snapshot.generation, setOf("T1"))
        )

        memory.recordFinalSpokenResponse("A good next task is Final year project.")
        val promptBeforeFocus = memory.snapshotForPrompt()
        memory.setAuthoritativeContextFocus(item, "T1", snapshot.generation)

        assertNotNull(
            memory.contextFocusForGeneration(snapshot.generation, setOf("T1"))
        )
        assertEquals(promptBeforeFocus, memory.snapshotForPrompt())
    }

    @Test
    fun pairAndNoSuggestionNeverCreateImplicitFocus() {
        val pairStore = ReadOnlyTaskContextStore().apply {
            replaceContextSuggestionResults(
                listOf(
                    task(1, "Dentist appointment"),
                    task(2, "Pick up medicine")
                )
            )
        }
        val pairSnapshot = pairStore.snapshot()

        assertNull(
            ContextSuggestionFocusPolicy.authoritativeItemOrNull(
                ContextSuggestionType.REVIEW_CLOSE_SCHEDULE,
                pairSnapshot,
                pairSnapshot.generation,
                pairStore.currentGeneration()
            )
        )
        assertNull(
            ContextSuggestionFocusPolicy.authoritativeItemOrNull(
                ContextSuggestionType.NO_SUGGESTION,
                pairSnapshot.copy(items = emptyList()),
                pairSnapshot.generation,
                pairStore.currentGeneration()
            )
        )
        assertNull(
            ContextFocusCarryForwardPolicy.resolve(
                normalizedText = "What time is that?",
                focus = null,
                capturedSnapshot = pairSnapshot,
                isResultInteraction = true
            )
        )
        assertEquals(
            ContextItemReadDisposition.RESOLVED,
            ContextItemReadPolicy.resolve(
                normalizedText = "What time is the first one?",
                capturedSnapshot = pairSnapshot,
                currentGeneration = pairStore.currentGeneration()
            ).disposition
        )
    }

    @Test
    fun staleGenerationAndInvalidPublishedShapeRejectFocus() {
        val store = singleTaskStore()
        val snapshot = store.snapshot()

        assertNull(
            ContextSuggestionFocusPolicy.authoritativeItemOrNull(
                ContextSuggestionType.FOCUS_TASK,
                snapshot,
                snapshot.generation,
                snapshot.generation + 1
            )
        )
        assertNull(
            ContextSuggestionFocusPolicy.authoritativeItemOrNull(
                ContextSuggestionType.FOCUS_TASK,
                snapshot.copy(items = snapshot.items + snapshot.items.single().copy(ref = "T2")),
                snapshot.generation,
                snapshot.generation
            )
        )
        assertNull(
            ContextSuggestionFocusPolicy.authoritativeItemOrNull(
                ContextSuggestionType.FOCUS_TASK,
                snapshot.copy(items = listOf(snapshot.items.single().copy(ref = "T2"))),
                snapshot.generation,
                snapshot.generation
            )
        )
    }

    @Test
    fun singleSuggestionFocusCarriesNaturalReadOnlyQuestionsButNotVagueActions() {
        val store = singleTaskStore()
        val snapshot = store.snapshot()
        val memory = ConversationSessionMemory()
        memory.setAuthoritativeContextFocus(
            item = snapshot.items.single(),
            selectedRef = "T1",
            capturedGeneration = snapshot.generation
        )
        val focus = memory.contextFocusForGeneration(snapshot.generation, setOf("T1"))

        mapOf(
            "What time is that?" to ConversationContextDetail.TIME,
            "What date is it?" to ConversationContextDetail.DATE
        ).forEach { (text, expectedDetail) ->
            val decision = ContextFocusCarryForwardPolicy.resolve(
                normalizedText = text,
                focus = focus,
                capturedSnapshot = snapshot,
                isResultInteraction = true
            )
            assertEquals(ConversationRoute.CONTEXT_READ, decision?.route)
            assertEquals("T1", decision?.contextRef)
            assertEquals(expectedDetail, decision?.contextDetail)
        }

        assertNull(
            ContextFocusCarryForwardPolicy.resolve(
                "Do it",
                focus,
                snapshot,
                isResultInteraction = true
            )
        )
        assertNull(
            ContextFocusCarryForwardPolicy.resolve(
                "Break it down",
                focus,
                snapshot,
                isResultInteraction = true
            )
        )
    }

    private fun singleTaskStore() = ReadOnlyTaskContextStore().apply {
        replaceContextSuggestionResults(listOf(task(1, "Final year project")))
    }

    private fun task(id: Long, title: String) = TaskEntity(
        id = id,
        title = title,
        dueDate = "31/07/2026",
        dueTime = "4 PM"
    )

    private fun suggestionObservation() = ExecutionObservation(
        operation = ExecutionOperation.CONTEXT_SUGGESTION,
        outcome = ExecutionOutcome.INFORMATION,
        taskTitle = "Final year project",
        listenAgain = true,
        fallbackSpeech = "A good next task is Final year project."
    )
}
