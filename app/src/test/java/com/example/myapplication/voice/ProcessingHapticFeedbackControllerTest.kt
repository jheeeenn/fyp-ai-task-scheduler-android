package com.example.myapplication.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessingHapticFeedbackControllerTest {
    @Test
    fun startWaitsThenRepeatsFirstAndSecondHeartbeatPulses() {
        val scheduler = FakeScheduler()
        var pulses = 0
        val controller = controller(scheduler, pulse = { pulses += 1; true })

        controller.start()

        assertEquals(0, pulses)
        assertEquals(ProcessingHapticFeedbackController.INITIAL_DELAY_MS, scheduler.nextDelay())

        scheduler.runNext()
        assertEquals(1, pulses)
        assertEquals(ProcessingHapticFeedbackController.SECOND_PULSE_GAP_MS, scheduler.nextDelay())

        scheduler.runNext()
        assertEquals(2, pulses)
        assertEquals(ProcessingHapticFeedbackController.HEARTBEAT_INTERVAL_MS, scheduler.nextDelay())

        scheduler.runNext()
        assertEquals(3, pulses)
        assertEquals(ProcessingHapticFeedbackController.SECOND_PULSE_GAP_MS, scheduler.nextDelay())
    }

    @Test
    fun stopBetweenFirstAndSecondPulseCancelsSecondPulse() {
        val scheduler = FakeScheduler()
        var pulses = 0
        val controller = controller(scheduler, pulse = { pulses += 1; true })

        controller.start()
        scheduler.runNext()
        controller.stop("SPEAKING")

        assertEquals(1, pulses)
        assertEquals(0, scheduler.pendingCount())
        assertFalse(controller.isRunningForTest())
    }

    @Test
    fun fastProcessingStopsBeforeDelayWithoutAnyPulse() {
        val scheduler = FakeScheduler()
        var pulses = 0
        val controller = controller(scheduler, pulse = { pulses += 1; true })

        controller.start()
        controller.stop("SPEAKING")

        assertEquals(0, scheduler.pendingCount())
        assertEquals(0, pulses)
        assertFalse(controller.isRunningForTest())
    }

    @Test
    fun repeatedStartCreatesOnlyOneSequence() {
        val scheduler = FakeScheduler()
        val controller = controller(scheduler)

        controller.start()
        controller.start()
        controller.start()

        assertEquals(1, scheduler.pendingCount())
    }

    @Test
    fun repeatedStopIsSafeAndLeavesNoCallbacks() {
        val scheduler = FakeScheduler()
        val controller = controller(scheduler)

        controller.start()
        controller.stop("LISTENING")
        controller.stop("ERROR")
        controller.stop("STOPPED")

        assertEquals(0, scheduler.pendingCount())
        assertFalse(controller.isRunningForTest())
    }

    @Test
    fun settingOffPreventsEveryPulse() {
        val scheduler = FakeScheduler()
        var pulses = 0
        val controller = controller(
            scheduler = scheduler,
            enabled = { false },
            pulse = { pulses += 1; true }
        )

        controller.start()

        assertEquals(0, scheduler.pendingCount())
        assertEquals(0, pulses)
    }

    @Test
    fun disablingWhileRunningCancelsFuturePulses() {
        val scheduler = FakeScheduler()
        var enabled = true
        var pulses = 0
        val controller = controller(
            scheduler = scheduler,
            enabled = { enabled },
            pulse = { pulses += 1; true }
        )

        controller.start()
        enabled = false
        scheduler.runNext()

        assertEquals(0, pulses)
        assertEquals(0, scheduler.pendingCount())
        assertFalse(controller.isRunningForTest())
    }

    @Test
    fun notDeliveredResultIsReportedAndDoesNotStopSequence() {
        val scheduler = FakeScheduler()
        val messages = mutableListOf<String>()
        val controller = ProcessingHapticFeedbackController(
            scheduler = scheduler,
            isEnabled = { true },
            performPulse = { false },
            log = messages::add
        )

        controller.start()
        scheduler.runNext()

        assertTrue(messages.contains("state=PULSE beat=FIRST result=NOT_DELIVERED"))
        assertEquals(1, scheduler.pendingCount())
        assertTrue(controller.isRunningForTest())
    }

    @Test
    fun deliveredResultIsReported() {
        val scheduler = FakeScheduler()
        val messages = mutableListOf<String>()
        val controller = ProcessingHapticFeedbackController(
            scheduler = scheduler,
            isEnabled = { true },
            performPulse = { true },
            log = messages::add
        )

        controller.start()
        scheduler.runNext()

        assertTrue(messages.contains("state=PULSE beat=FIRST result=DELIVERED"))
    }

    @Test
    fun destroyCancelsPendingPulse() {
        val scheduler = FakeScheduler()
        val controller = controller(scheduler)

        controller.start()
        controller.destroy()

        assertEquals(0, scheduler.pendingCount())
        assertFalse(controller.isRunningForTest())
    }

    @Test
    fun startAfterDestroyCannotCreateNewCallbacks() {
        val scheduler = FakeScheduler()
        val controller = controller(scheduler)

        controller.destroy()
        controller.start()

        assertEquals(0, scheduler.pendingCount())
        assertFalse(controller.isRunningForTest())
    }

    @Test
    fun lifecycleStopCancelsAndBlocksStaleStartsUntilStartedAgain() {
        val scheduler = FakeScheduler()
        val controller = controller(scheduler)

        controller.start()
        controller.onLifecycleStopped()
        controller.start()

        assertEquals(0, scheduler.pendingCount())

        controller.onLifecycleStarted()
        controller.start()

        assertEquals(1, scheduler.pendingCount())
        assertTrue(controller.isRunningForTest())
    }

    private fun controller(
        scheduler: FakeScheduler,
        enabled: () -> Boolean = { true },
        pulse: () -> Boolean = { true }
    ) = ProcessingHapticFeedbackController(
        scheduler = scheduler,
        isEnabled = enabled,
        performPulse = pulse,
        log = {}
    )

    private class FakeScheduler : ProcessingHapticScheduler {
        private val pending = mutableListOf<ScheduledCallback>()

        override fun postDelayed(callback: Runnable, delayMs: Long) {
            pending += ScheduledCallback(callback, delayMs)
        }

        override fun removeCallbacks(callback: Runnable) {
            pending.removeAll { it.callback === callback }
        }

        fun nextDelay(): Long = pending.first().delayMs

        fun pendingCount(): Int = pending.size

        fun runNext() {
            val callback = pending.removeAt(0).callback
            callback.run()
        }

        private data class ScheduledCallback(val callback: Runnable, val delayMs: Long)
    }
}
