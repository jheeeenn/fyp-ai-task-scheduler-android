package com.example.myapplication.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantSessionEndHapticFeedbackTest {
    @Test
    fun enabledDeliveryUsesNamedTwoSecondDuration() {
        val durations = mutableListOf<Long>()
        val messages = mutableListOf<String>()
        val feedback = feedback(
            perform = { duration -> durations += duration; true },
            log = messages::add
        )

        feedback.deliverOnce(sessionGeneration = 7L)

        assertEquals(listOf(AssistantSessionEndHapticFeedback.DURATION_MS), durations)
        assertTrue(messages.contains("state=DELIVERED durationMs=2000"))
    }

    @Test
    fun sameSessionGenerationCannotDeliverTwice() {
        var deliveries = 0
        val feedback = feedback(perform = { deliveries += 1; true })

        feedback.deliverOnce(sessionGeneration = 9L)
        feedback.deliverOnce(sessionGeneration = 9L)

        assertEquals(1, deliveries)
    }

    @Test
    fun newSessionGenerationCanDeliverAgain() {
        var deliveries = 0
        val feedback = feedback(perform = { deliveries += 1; true })

        feedback.deliverOnce(sessionGeneration = 10L)
        feedback.deliverOnce(sessionGeneration = 11L)

        assertEquals(2, deliveries)
    }

    @Test
    fun disabledSettingPreventsVibrationAndLogsDisabled() {
        var deliveries = 0
        val messages = mutableListOf<String>()
        val feedback = feedback(
            enabled = { false },
            perform = { deliveries += 1; true },
            log = messages::add
        )

        feedback.deliverOnce(sessionGeneration = 12L)

        assertEquals(0, deliveries)
        assertTrue(messages.contains("state=DISABLED"))
    }

    @Test
    fun unavailableVibratorIsReported() {
        val messages = mutableListOf<String>()
        val feedback = feedback(perform = { false }, log = messages::add)

        feedback.deliverOnce(sessionGeneration = 13L)

        assertTrue(messages.contains("state=UNAVAILABLE durationMs=2000"))
    }

    @Test
    fun lifecycleCancellationDoesNotCreateHapticAndDestroyBlocksDelivery() {
        var deliveries = 0
        var cancellations = 0
        val feedback = feedback(
            perform = { deliveries += 1; true },
            cancel = { cancellations += 1 }
        )

        feedback.cancelActive()
        feedback.destroy()
        feedback.deliverOnce(sessionGeneration = 14L)

        assertEquals(0, deliveries)
        assertEquals(2, cancellations)
    }

    private fun feedback(
        enabled: () -> Boolean = { true },
        perform: (Long) -> Boolean = { true },
        cancel: () -> Unit = {},
        log: (String) -> Unit = {}
    ) = AssistantSessionEndHapticFeedback(
        isEnabled = enabled,
        performVibration = perform,
        cancelVibration = cancel,
        log = log
    )
}
