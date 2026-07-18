package com.example.myapplication.ai.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalObservationInputsTest {
    @Test fun unresolvedTimeProducesExactTime() {
        val input = TemporalObservationInputs.fromUnresolvedComponents(datePhraseUnresolved = false, timePhraseUnresolved = true)
        assertEquals(RequiredInput.EXACT_TIME, input.requiredInput)
        assertTrue(input.allowedUserMoves.contains(AllowedUserMove.PROVIDE_TIME))
    }

    @Test fun unknownOrMultipleTemporalComponentsProduceRetry() {
        assertEquals(RequiredInput.RETRY, TemporalObservationInputs.fromUnresolvedComponents(false, false).requiredInput)
        assertEquals(RequiredInput.RETRY, TemporalObservationInputs.fromUnresolvedComponents(true, true).requiredInput)
    }
}
