package com.example.myapplication.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DebugDiagnosticLogTest {
    @Test
    fun shortMessagesRemainOneUnchangedPayload() {
        val message = "short structured event"

        val chunks = DebugDiagnosticLog.chunk(message)

        assertEquals(1, chunks.size)
        assertSame(message, chunks.single())
    }

    @Test
    fun longMessagesAreChunkedWithoutLossOrReordering() {
        val message = buildString {
            repeat(7_777) { index ->
                append(('a'.code + (index % 26)).toChar())
            }
        }

        val chunks = DebugDiagnosticLog.chunk(message)

        assertEquals(listOf(3_000, 3_000, 1_777), chunks.map(String::length))
        assertEquals(message, chunks.joinToString(separator = ""))
    }

    @Test
    fun customChunkBoundaryPreservesExactText() {
        val message = "0123456789"

        val chunks = DebugDiagnosticLog.chunk(message, maximumPayloadChars = 3)

        assertEquals(listOf("012", "345", "678", "9"), chunks)
        assertEquals(message, chunks.joinToString(separator = ""))
    }
}
