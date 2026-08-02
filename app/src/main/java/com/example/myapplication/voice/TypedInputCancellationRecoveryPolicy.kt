package com.example.myapplication.voice

internal class TypedInputCancellationRecoveryPolicy {
    private var recoveryPending = false

    fun onPanelTypedInputRequested(sessionActive: Boolean, forceStopping: Boolean) {
        recoveryPending = sessionActive && !forceStopping
    }

    fun onTypedInputSubmitted() {
        recoveryPending = false
    }

    fun claimRecovery(
        sessionActive: Boolean,
        forceStopping: Boolean,
        listening: Boolean
    ): Boolean {
        val wasPending = recoveryPending
        recoveryPending = false
        return wasPending && sessionActive && !forceStopping && !listening
    }
}
