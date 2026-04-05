package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class VoiceHelper(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech = TextToSpeech(context, this)
    private var isReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            //val result = tts.setLanguage(Locale.US)
            val result = tts.setLanguage(Locale.UK)
            isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    fun speak(text: String) {
        if (isReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_helper")
        }
    }

    fun speak(text: String, onDone: (() -> Unit)?) {
        if (!isReady) return

        val utteranceId = "voice_helper_${System.currentTimeMillis()}"

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                onDone?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}

            override fun onError(utteranceId: String?, errorCode: Int) {}
        })

        val params = Bundle()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}