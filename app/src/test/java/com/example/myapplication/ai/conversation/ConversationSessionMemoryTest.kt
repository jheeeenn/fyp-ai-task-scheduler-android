package com.example.myapplication.ai.conversation

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
