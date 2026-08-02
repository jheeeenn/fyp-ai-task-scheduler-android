package com.example.myapplication.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypedInputCancellationRecoveryPolicyTest {
    @Test
    fun activePanelCancellationClaimsListeningRecoveryExactlyOnce() {
        val policy = TypedInputCancellationRecoveryPolicy()
        policy.onPanelTypedInputRequested(sessionActive = true, forceStopping = false)

        assertTrue(policy.claimRecovery(sessionActive = true, forceStopping = false, listening = false))
        assertFalse(policy.claimRecovery(sessionActive = true, forceStopping = false, listening = false))
    }

    @Test
    fun inactiveLongPressCancellationDoesNotStartRecovery() {
        val policy = TypedInputCancellationRecoveryPolicy()

        assertFalse(policy.claimRecovery(sessionActive = false, forceStopping = false, listening = false))
    }

    @Test
    fun successfulSubmissionClearsPendingRecovery() {
        val policy = TypedInputCancellationRecoveryPolicy()
        policy.onPanelTypedInputRequested(sessionActive = true, forceStopping = false)
        policy.onTypedInputSubmitted()

        assertFalse(policy.claimRecovery(sessionActive = true, forceStopping = false, listening = false))
    }

    @Test
    fun forceStopAndExistingListeningStateBlockRecovery() {
        val forceStopping = TypedInputCancellationRecoveryPolicy().apply {
            onPanelTypedInputRequested(sessionActive = true, forceStopping = false)
        }
        val alreadyListening = TypedInputCancellationRecoveryPolicy().apply {
            onPanelTypedInputRequested(sessionActive = true, forceStopping = false)
        }

        assertFalse(forceStopping.claimRecovery(sessionActive = true, forceStopping = true, listening = false))
        assertFalse(alreadyListening.claimRecovery(sessionActive = true, forceStopping = false, listening = true))
    }
}
