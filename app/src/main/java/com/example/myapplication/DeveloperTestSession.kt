package com.example.myapplication

import com.example.myapplication.accessibility.AssistantAccessibilityState
import com.example.myapplication.voice.AssistantInteractionMode
import com.example.myapplication.voice.AssistantTranscriptEvent

/** Process-local presentation/transport state for an explicitly active developer test. */
object DeveloperTestSession {
    private val transcript = mutableListOf<AssistantTranscriptEvent>()
    private val observers = linkedSetOf<() -> Unit>()

    var isActive: Boolean = false
        private set

    var ttsEnabled: Boolean = false
        private set

    var assistantState: AssistantAccessibilityState = AssistantAccessibilityState.READY
        private set

    fun activate() {
        isActive = true
        reset()
    }

    fun deactivate() {
        isActive = false
        transcript.clear()
        ttsEnabled = false
        assistantState = AssistantAccessibilityState.READY
        notifyObservers()
    }

    fun reset() {
        if (!isActive) return
        transcript.clear()
        ttsEnabled = false
        assistantState = AssistantAccessibilityState.READY
        notifyObservers()
    }

    fun interactionMode(): AssistantInteractionMode =
        if (isActive) {
            AssistantInteractionMode.DEVELOPER_TEXT
        } else {
            AssistantInteractionMode.NORMAL_VOICE
        }

    fun shouldSpeakAudio(): Boolean = !isActive || ttsEnabled

    fun setTtsEnabled(enabled: Boolean) {
        if (!isActive || ttsEnabled == enabled) return
        ttsEnabled = enabled
        notifyObservers()
    }

    fun recordTranscript(event: AssistantTranscriptEvent) {
        if (!isActive) return
        transcript += event
        notifyObservers()
    }

    fun updateAssistantState(state: AssistantAccessibilityState) {
        if (!isActive) return
        assistantState = state
        notifyObservers()
    }

    fun transcriptSnapshot(): List<AssistantTranscriptEvent> = transcript.toList()

    fun addObserver(observer: () -> Unit) {
        observers += observer
        observer()
    }

    fun removeObserver(observer: () -> Unit) {
        observers -= observer
    }

    private fun notifyObservers() {
        observers.toList().forEach { it() }
    }
}
