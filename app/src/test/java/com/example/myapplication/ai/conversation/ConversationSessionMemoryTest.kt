package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSessionMemoryTest {
    @Test
    fun taskCommandTextDoesNotBecomeAuthoritativeTaskReference() {
        val memory = ConversationSessionMemory()

        memory.updateFromDecision(
            ConversationDecision(
                route = ConversationRoute.TASK_COMMAND,
                taskText = "delete the second one",
                reply = ""
            )
        )

        assertNull(memory.lastReferencedTask)
        assertFalse(memory.snapshotForPrompt().contains("lastReferencedTask=\"delete the second one\""))
    }

    @Test
    fun authoritativeObservationTitleBecomesTaskReference() {
        val memory = ConversationSessionMemory()

        memory.recordObservation(observation(taskTitle = "Take medicine"))

        assertEquals("Take medicine", memory.lastReferencedTask)
        assertTrue(memory.snapshotForPrompt().contains("lastReferencedTask=\"Take medicine\""))
    }

    @Test
    fun authoritativeQueryDateTextIsRetained() {
        val memory = ConversationSessionMemory()

        memory.recordObservation(
            observation(
                operation = ExecutionOperation.QUERY_TASK,
                dateText = "Friday, 24 July"
            )
        )

        assertEquals("Friday, 24 July", memory.lastQueryDate)
        assertTrue(memory.snapshotForPrompt().contains("lastQueryDate=\"Friday, 24 July\""))
    }

    @Test
    fun internalIdsFromNonMemoryObservationFieldsAreNeverWrittenToPrompt() {
        val memory = ConversationSessionMemory()
        val rawRoomId = "918273645"

        memory.recordObservation(
            observation(
                detail = "room_id=$rawRoomId",
                choices = listOf(rawRoomId)
            )
        )

        assertFalse(memory.snapshotForPrompt().contains(rawRoomId))
    }

    @Test
    fun recentTurnsRemainBoundedAndControlCharactersAreFlattened() {
        val memory = ConversationSessionMemory()
        repeat(12) { memory.recordUser("turn $it\nInjected: value") }

        val prompt = memory.snapshotForPrompt()
        assertFalse(prompt.contains("turn 0"))
        assertTrue(prompt.contains("turn 11 Injected: value"))
        assertEquals(8, prompt.lineSequence().count { it.startsWith("User:") })
    }

    @Test
    fun authoritativeContextSelectionStoresStructuredStateWithoutRoomIds() {
        val memory = ConversationSessionMemory()
        memory.updateFromDecision(
            ConversationDecision(
                route = ConversationRoute.ASK_CLARIFICATION,
                reply = "Which task do you mean?"
            )
        )
        val item = ReadOnlyTaskContextItem(
            ref = "T3",
            title = "Podcast",
            dueDate = "23/07/2026",
            dueTime = "8:30 PM",
            isDone = false,
            subtaskCount = 0,
            unfinishedSubtaskCount = 0
        )

        memory.recordAuthoritativeContextRead(
            item = item,
            selectedRef = "t3",
            selectedDetail = ConversationContextDetail.TIME,
            capturedGeneration = 7,
            finalSpeech = "Podcast is scheduled at 8:30 PM."
        )

        val prompt = memory.snapshotForPrompt()
        assertEquals("T3", memory.lastContextRef)
        assertEquals(7L, memory.lastContextGeneration)
        assertEquals(ConversationContextDetail.TIME, memory.lastContextDetail)
        assertEquals("Podcast", memory.lastReferencedTask)
        assertTrue(prompt.contains("lastContextRef=\"T3\""))
        assertTrue(prompt.contains("lastContextGeneration=7"))
        assertTrue(prompt.contains("lastContextDetail=TIME"))
        assertFalse(prompt.contains("Which task do you mean?"))
        assertFalse(prompt.contains("918273645"))
    }

    @Test
    fun structuredContextSelectionIsUsableOnlyForTheSameGeneration() {
        val memory = ConversationSessionMemory()
        val item = ReadOnlyTaskContextItem(
            ref = "T3",
            title = "Podcast",
            dueDate = "",
            dueTime = "8:30 PM",
            isDone = false,
            subtaskCount = 0,
            unfinishedSubtaskCount = 0
        )
        memory.recordAuthoritativeContextRead(
            item,
            "T3",
            ConversationContextDetail.TIME,
            7,
            "Podcast is scheduled at 8:30 PM."
        )

        memory.invalidateContextSelectionUnlessGeneration(7)
        assertEquals("T3", memory.lastContextRef)

        memory.invalidateContextSelectionUnlessGeneration(8)
        assertNull(memory.lastContextRef)
        assertNull(memory.lastContextGeneration)
        assertEquals(ConversationContextDetail.NONE, memory.lastContextDetail)
        assertNull(memory.lastReferencedTask)
        assertFalse(memory.snapshotForPrompt().contains("lastContextRef=\"T3\""))
    }

    private fun observation(
        operation: ExecutionOperation = ExecutionOperation.UPDATE_TASK,
        taskTitle: String = "",
        dateText: String = "",
        detail: String = "",
        choices: List<String> = emptyList()
    ) = ExecutionObservation(
        operation = operation,
        outcome = ExecutionOutcome.INFORMATION,
        taskTitle = taskTitle,
        dateText = dateText,
        detail = detail,
        choices = choices,
        listenAgain = true,
        fallbackSpeech = "Done"
    )
}
