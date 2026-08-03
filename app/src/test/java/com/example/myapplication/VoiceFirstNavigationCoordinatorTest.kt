package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceFirstNavigationCoordinatorTest {
    @Test
    fun navigationRunsOnlyAfterSuccessfulSpeech() {
        var completion: ((Boolean) -> Unit)? = null
        var navigationCount = 0
        val coordinator = VoiceFirstNavigationCoordinator { _, onFinished ->
            completion = onFinished
        }

        assertTrue(coordinator.request("Opening.") { navigationCount += 1 })
        assertEquals(0, navigationCount)

        completion?.invoke(true)

        assertEquals(1, navigationCount)
        assertFalse(coordinator.isPending())
    }

    @Test
    fun duplicateRequestsAreRejectedWhileSpeechIsPending() {
        var speechCount = 0
        var completion: ((Boolean) -> Unit)? = null
        var navigationCount = 0
        val coordinator = VoiceFirstNavigationCoordinator { _, onFinished ->
            speechCount += 1
            completion = onFinished
        }

        assertTrue(coordinator.request("First") { navigationCount += 1 })
        assertFalse(coordinator.request("Second") { navigationCount += 1 })
        completion?.invoke(true)

        assertEquals(1, speechCount)
        assertEquals(1, navigationCount)
    }

    @Test
    fun failureClearsTheGuardWithoutUnsafeNavigation() {
        var completion: ((Boolean) -> Unit)? = null
        var navigationCount = 0
        val coordinator = VoiceFirstNavigationCoordinator { _, onFinished ->
            completion = onFinished
        }

        coordinator.request("Opening") { navigationCount += 1 }
        completion?.invoke(false)

        assertEquals(0, navigationCount)
        assertFalse(coordinator.isPending())
        assertTrue(coordinator.request("Retry") {})
    }

    @Test
    fun cancellationInvalidatesStaleCompletionCallbacks() {
        val completions = mutableListOf<(Boolean) -> Unit>()
        val navigations = mutableListOf<String>()
        val coordinator = VoiceFirstNavigationCoordinator { _, onFinished ->
            completions += onFinished
        }

        coordinator.request("First") { navigations += "first" }
        coordinator.cancelPending()
        coordinator.request("Second") { navigations += "second" }
        completions.first().invoke(true)
        completions.last().invoke(true)

        assertEquals(listOf("second"), navigations)
    }
}
