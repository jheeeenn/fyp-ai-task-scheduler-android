package com.example.myapplication.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSpeechQueueTest {
    @Test
    fun simultaneousRemindersSpeakFifoAndStopOnlyAfterQueueIsEmpty() {
        val started = mutableListOf<ReminderSpeechRequest>()
        val completions = mutableListOf<() -> Unit>()
        var stopCount = 0
        val queue = ReminderSpeechQueue(
            speaker = { request, onComplete ->
                started += request
                completions += onComplete
            },
            onQueueEmpty = { stopCount += 1 }
        )

        assertTrue(queue.enqueuePayload("First task", "DUE"))
        assertTrue(queue.enqueuePayload("Second task", "FOLLOW_UP"))

        assertEquals(listOf("First task"), started.map { it.taskTitle })
        assertEquals(1, queue.pendingCount)
        assertEquals(0, stopCount)

        completions[0]()

        assertEquals(
            listOf("First task", "Second task"),
            started.map { it.taskTitle }
        )
        assertEquals(0, queue.pendingCount)
        assertEquals(0, stopCount)

        completions[1]()

        assertTrue(queue.isIdle)
        assertEquals(1, stopCount)
    }

    @Test
    fun invalidPayloadDoesNotInterruptOrClearValidQueuedRequests() {
        val started = mutableListOf<String>()
        val completions = mutableListOf<() -> Unit>()
        var stopCount = 0
        val queue = ReminderSpeechQueue(
            speaker = { request, onComplete ->
                started += request.taskTitle
                completions += onComplete
            },
            onQueueEmpty = { stopCount += 1 }
        )

        assertTrue(queue.enqueuePayload("Current task", "DUE"))
        assertTrue(queue.enqueuePayload("Next task", "FINAL"))
        assertFalse(queue.enqueuePayload("", "DUE"))
        assertFalse(queue.enqueuePayload("Malformed", "SNOOZE"))

        assertEquals(listOf("Current task"), started)
        assertEquals(1, queue.pendingCount)
        assertEquals(0, stopCount)

        completions[0]()
        assertEquals(listOf("Current task", "Next task"), started)
        assertEquals(0, stopCount)

        completions[1]()
        assertEquals(1, stopCount)
    }
}
