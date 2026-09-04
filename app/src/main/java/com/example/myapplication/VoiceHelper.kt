package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.myapplication.preferences.AppPreferences
import com.example.myapplication.preferences.SpeechRatePreset
import java.util.Locale

class VoiceHelper(context: Context) : TextToSpeech.OnInitListener {

    private val appPreferences = AppPreferences(context.applicationContext)
    private var tts: TextToSpeech = TextToSpeech(context.applicationContext, this)
    private var isReady = false
    private var speechRatePreset = appPreferences.speechRatePreset

    private var pendingText: String? = null
    private var pendingOnFinished: ((Boolean) -> Unit)? = null
    private var utteranceSequence = 0L
    private var currentUtteranceId: String? = null
    private var shouldSpeakAudio: () -> Boolean = { true }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {

            android.util.Log.d("REMINDER_DEBUG", "VoiceHelper onInit status=$status ready=$isReady")
            val result = tts.setLanguage(Locale.UK)
            isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED

            if (isReady) {
                speechRatePreset = appPreferences.speechRatePreset
                applySpeechRate(speechRatePreset)
                val text = pendingText
                val callback = pendingOnFinished
                pendingText = null
                pendingOnFinished = null

                if (!text.isNullOrBlank()) {
                    if (shouldSpeakAudio()) {
                        speakInternal(text, callback)
                    } else {
                        callback?.invoke(true)
                    }
                }
            } else {
                failPendingSpeech()
            }
        } else {
            failPendingSpeech()
        }
    }

    private fun failPendingSpeech() {
        val callback = pendingOnFinished
        pendingText = null
        pendingOnFinished = null
        callback?.invoke(false)
    }

    fun speak(text: String) {
        speak(text, null)
    }

    fun speak(text: String, onDone: (() -> Unit)?) {
        speakWithResult(text) { onDone?.invoke() }
    }

    fun speakWithResult(text: String, onFinished: (Boolean) -> Unit) {
        if (!shouldSpeakAudio()) {
            onFinished(true)
            return
        }
        refreshSpeechRateFromPreferences()
        if (!isReady) {
            pendingText = text
            pendingOnFinished = onFinished
            return
        }

        speakInternal(text, onFinished)
    }

    fun setShouldSpeakAudio(provider: () -> Boolean) {
        shouldSpeakAudio = provider
    }

    fun applySpeechRate(preset: SpeechRatePreset) {
        speechRatePreset = preset
        if (isReady) {
            tts.setSpeechRate(preset.rate)
        }
    }

    private fun refreshSpeechRateFromPreferences() {
        val storedPreset = appPreferences.speechRatePreset
        if (storedPreset != speechRatePreset) {
            applySpeechRate(storedPreset)
        }
    }

    private fun speakInternal(text: String, onFinished: ((Boolean) -> Unit)?) {
        val utteranceId = "voice_helper_${++utteranceSequence}"

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                if (utteranceId != this@VoiceHelper.currentUtteranceId) return
                onFinished?.invoke(true)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId != this@VoiceHelper.currentUtteranceId) return
                onFinished?.invoke(false)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId != this@VoiceHelper.currentUtteranceId) return
                onFinished?.invoke(false)
            }
        })

        val params = Bundle()
        currentUtteranceId = utteranceId
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun shutdown() {
        currentUtteranceId = null
        pendingText = null
        pendingOnFinished = null
        tts.stop()
        tts.shutdown()
    }
}
