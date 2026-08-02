package com.example.myapplication.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibleAssistantInputDialogCancellationTest {
    @Test
    fun cancelAndDismissDeliverCancellationOnlyOnce() {
        var cancellations = 0
        val dispatcher = OneShotDialogCancellation { cancellations += 1 }

        dispatcher.cancel()
        dispatcher.cancel()

        assertEquals(1, cancellations)
    }

    @Test
    fun successfulSubmissionSuppressesCancellationRecovery() {
        var cancellations = 0
        val dispatcher = OneShotDialogCancellation { cancellations += 1 }

        dispatcher.markSubmitted()
        dispatcher.cancel()

        assertEquals(0, cancellations)
    }
}
