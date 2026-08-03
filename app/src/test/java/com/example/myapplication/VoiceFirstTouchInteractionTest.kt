package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceFirstTouchInteractionTest {
    @Test
    fun actionSingleTapSpeaksWithoutActivating() {
        var speechCount = 0
        var activationCount = 0
        val interaction = interaction(
            singleTap = { speechCount += 1 },
            doubleTap = { activationCount += 1 }
        )

        assertTrue(interaction.onSingleTapConfirmed())
        assertEquals(1, speechCount)
        assertEquals(0, activationCount)
    }

    @Test
    fun actionDoubleTapActivatesExactlyOnceWithoutSingleTapSpeech() {
        var speechCount = 0
        var activationCount = 0
        val interaction = interaction(
            singleTap = { speechCount += 1 },
            doubleTap = { activationCount += 1 }
        )

        assertTrue(interaction.onDoubleTap())
        assertEquals(0, speechCount)
        assertEquals(1, activationCount)
    }

    @Test
    fun informationSingleAndDoubleTapEachReadExactlyOnce() {
        var readCount = 0
        val interaction = interaction(
            singleTap = { readCount += 1 },
            doubleTap = { readCount += 1 }
        )

        interaction.onSingleTapConfirmed()
        assertEquals(1, readCount)

        readCount = 0
        interaction.onDoubleTap()
        assertEquals(1, readCount)
    }

    @Test
    fun scrollDoesNotReadActivateOrConsumeParentScrolling() {
        var userActionCount = 0
        val interaction = interaction(
            singleTap = { userActionCount += 1 },
            doubleTap = { userActionCount += 1 }
        )

        assertFalse(interaction.onScroll())
        assertEquals(0, userActionCount)
    }

    @Test
    fun longPressUsesOnlyReservedLongClickPath() {
        var userActionCount = 0
        var longClickCount = 0
        val interaction = VoiceFirstTouchInteraction(
            singleTap = { userActionCount += 1 },
            doubleTap = { userActionCount += 1 },
            longPress = { longClickCount += 1 }
        )

        interaction.onLongPress()

        assertEquals(0, userActionCount)
        assertEquals(1, longClickCount)
    }

    private fun interaction(
        singleTap: () -> Unit,
        doubleTap: () -> Unit
    ) = VoiceFirstTouchInteraction(singleTap, doubleTap, longPress = {})
}
