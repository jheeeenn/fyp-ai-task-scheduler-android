package com.example.myapplication.voice

interface AssistantVoiceHost {
    fun onAssistantFinalText(text: String)
    fun onAssistantCancelled()
    fun onAssistantSessionStopped()
}