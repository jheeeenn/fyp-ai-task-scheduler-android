package com.example.myapplication.voice

import com.example.myapplication.VoiceHelper

enum class VoiceSessionState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    STOPPED
}

class VoiceSessionController(
    private val voiceHelper: VoiceHelper,
    private val maxRetryCount: Int = 3,
    private val onStateChanged: (VoiceSessionState) -> Unit
) {
    private var assistantSessionActive = false
    private var isListening = false
    private var isForceStopping = false
    private var retryCount = 0

    fun beginSession() {
        assistantSessionActive = true
        isForceStopping = false
        isListening = false
        retryCount = 0
        onStateChanged(VoiceSessionState.LISTENING)
    }

    fun isSessionActive(): Boolean = assistantSessionActive

    fun canHandleRecognizerCallbacks(): Boolean {
        return assistantSessionActive && !isForceStopping
    }

    fun canStartListening(): Boolean {
        return assistantSessionActive && !isForceStopping && !isListening
    }

    fun onReadyForSpeech() {
        if (!canHandleRecognizerCallbacks()) return
        isListening = true
        onStateChanged(VoiceSessionState.LISTENING)
    }

    fun onEndOfSpeech() {
        if (!canHandleRecognizerCallbacks()) return
        isListening = false
        onStateChanged(VoiceSessionState.PROCESSING)
    }

    fun onRecognizerError() {
        if (!canHandleRecognizerCallbacks()) return
        isListening = false
        onStateChanged(VoiceSessionState.PROCESSING)
    }

    fun onPartialSpeech() {
        if (!canHandleRecognizerCallbacks()) return
        onStateChanged(VoiceSessionState.LISTENING)
    }

    fun onFinalSpeechReceived() {
        retryCount = 0
        isListening = false
        onStateChanged(VoiceSessionState.PROCESSING)
    }

    fun speak(
        text: String,
        continueListening: Boolean,
        onContinueListening: () -> Unit,
        onDone: (() -> Unit)? = null
    ) {
        if (!assistantSessionActive && continueListening) return
        onStateChanged(VoiceSessionState.SPEAKING)

        voiceHelper.speak(text) {
            if (assistantSessionActive && continueListening && !isForceStopping) {
                onStateChanged(VoiceSessionState.LISTENING)
                onContinueListening()
            } else if (assistantSessionActive) {
                onStateChanged(VoiceSessionState.IDLE)
            } else {
                onStateChanged(VoiceSessionState.STOPPED)
            }
            onDone?.invoke()
        }
    }

    fun handleListenFailure(
        retryReply: String,
        onContinueListening: () -> Unit,
        onRetriesExhausted: () -> Unit
    ) {
        if (!assistantSessionActive || isForceStopping) return

        if (retryCount < maxRetryCount) {
            retryCount++
            speak(
                text = retryReply,
                continueListening = true,
                onContinueListening = onContinueListening
            )
            return
        }

        assistantSessionActive = false
        isListening = false
        retryCount = 0
        onRetriesExhausted()
    }

    fun hardStop(
        cancelRecognizer: () -> Unit,
        dismissPanel: () -> Unit,
        clearConversationState: (() -> Unit)? = null
    ) {
        assistantSessionActive = false
        isForceStopping = true
        isListening = false
        retryCount = 0
        clearConversationState?.invoke()
        cancelRecognizer()
        dismissPanel()
        onStateChanged(VoiceSessionState.STOPPED)
        isForceStopping = false
    }

    fun deactivateSession() {
        assistantSessionActive = false
        isListening = false
        retryCount = 0
    }
}
