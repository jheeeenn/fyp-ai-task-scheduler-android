package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class VoiceHelper(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech = TextToSpeech(context.applicationContext, this)
    private var isReady = false

    private var pendingText: String? = null
    private var pendingOnDone: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {

            android.util.Log.d("REMINDER_DEBUG", "VoiceHelper onInit status=$status ready=$isReady")
            val result = tts.setLanguage(Locale.UK)
            isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED

            if (isReady) {
                val text = pendingText
                val callback = pendingOnDone
                pendingText = null
                pendingOnDone = null

                if (!text.isNullOrBlank()) {
                    speakInternal(text, callback)
                }
            }
        }
    }

    fun speak(text: String) {
        speak(text, null)
    }

    fun speak(text: String, onDone: (() -> Unit)?) {
        if (!isReady) {
            pendingText = text
            pendingOnDone = onDone
            return
        }

        speakInternal(text, onDone)
    }

    private fun speakInternal(text: String, onDone: (() -> Unit)?) {
        val utteranceId = "voice_helper_${System.currentTimeMillis()}"

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                onDone?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onDone?.invoke()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onDone?.invoke()
            }
        })

        val params = Bundle()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun shutdown() {
        pendingText = null
        pendingOnDone = null
        tts.stop()
        tts.shutdown()
    }
}