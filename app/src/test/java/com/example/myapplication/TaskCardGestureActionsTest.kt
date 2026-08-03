package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCardGestureActionsTest {
    @Test
    fun confirmedSingleTapMapsOnlyToRead() {
        var reads = 0
        var opens = 0
        val actions = TaskCardGestureActions({ reads++ }, { opens++ })

        assertTrue(actions.onSingleTapConfirmed())
        assertEquals(1, reads)
        assertEquals(0, opens)
    }

    @Test
    fun doubleTapMapsOnlyToOpenWithoutSingleTapSpeech() {
        var reads = 0
        var opens = 0
        val actions = TaskCardGestureActions({ reads++ }, { opens++ })

        assertTrue(actions.onDoubleTap())
        assertEquals(0, reads)
        assertEquals(1, opens)
    }

    @Test
    fun scrollConsumesGestureWithoutInvokingAnAction() {
        var reads = 0
        var opens = 0
        val actions = TaskCardGestureActions({ reads++ }, { opens++ })

        assertTrue(actions.onScroll())
        assertEquals(0, reads)
        assertEquals(0, opens)
    }
}
