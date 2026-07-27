package com.example.myapplication.voice

import android.Manifest

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.AssistantBottomSheet
import com.example.myapplication.VoiceHelper
import com.example.myapplication.diagnostics.DebugDiagnosticLog

class AssistantVoiceSession(
    private val activity: AppCompatActivity,
    private val host: AssistantVoiceHost,
    private val voiceHelper: VoiceHelper,
    private val responseManager: AssistantResponseManager,
    private val audioPermissionLauncher: ActivityResultLauncher<String>
) {
    private var suppressNextRecognizerError = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var assistantBottomSheet: AssistantBottomSheet? = null

    var assistantSessionActive = false
        private set

    private var isListening = false
    private var retryCount = 0
    private val maxRetryCount = 3
    private var isForceStopping = false

    fun ensureInitialized() {
        if (assistantBottomSheet == null) {
            assistantBottomSheet = AssistantBottomSheet(activity)
            assistantBottomSheet?.setOnDoubleTapCancelListener {
                activity.runOnUiThread {
                    forceStop()
                }
            }
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("VOICE_SESSION", "onReadyForSpeech")
                        suppressNextRecognizerError = false
                        assistantBottomSheet?.setListeningState()
                        isListening = true
                    }

                    override fun onBeginningOfSpeech() {
                        assistantBottomSheet?.setListeningState()
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        assistantBottomSheet?.setProcessingState()
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        Log.d("VOICE_SESSION", "onError code=$error suppress=$suppressNextRecognizerError active=$assistantSessionActive")
                        isListening = false

                        if (suppressNextRecognizerError) {
                            when (error) {
                                SpeechRecognizer.ERROR_CLIENT,
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                                    suppressNextRecognizerError = false
                                    return
                                }
                                else -> {
                                    suppressNextRecognizerError = false
                                }
                            }
                        }
                        if (!assistantSessionActive || isForceStopping) return

                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                handleListenFailure(responseManager.listenFailure())
                            }

                            SpeechRecognizer.ERROR_CLIENT,
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                                activity.window.decorView.postDelayed({
                                    if (assistantSessionActive && !isForceStopping) {
                                        startVoiceRecognition()
                                    }
                                }, 400)
                            }

                            else -> {
                                handleListenFailure(responseManager.listenFailure())
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        if (!assistantSessionActive || isForceStopping) return

                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val finalRecognizedText = matches?.firstOrNull()?.trim()
                        val spokenText = finalRecognizedText?.lowercase()

                        if (!spokenText.isNullOrEmpty()) {
                            retryCount = 0
                            logUserTranscript(
                                requireNotNull(finalRecognizedText),
                                source = "VOICE"
                            )
                            assistantBottomSheet?.showUserSpeech(spokenText)
                            assistantBottomSheet?.setProcessingState()
                            host.onAssistantFinalText(spokenText)
                        } else {
                            handleListenFailure(responseManager.listenFailure())
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        if (!assistantSessionActive || isForceStopping) return

                        val partialMatches =
                            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partialText = partialMatches?.firstOrNull()?.trim()

                        if (!partialText.isNullOrEmpty()) {
                            assistantBottomSheet?.showUserSpeech(partialText)
                            assistantBottomSheet?.setListeningState()
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    fun startPassiveSession(clearConversation: Boolean = true) {
        ensureInitialized()

        retryCount = 0
        isForceStopping = false
        isListening = false
        assistantSessionActive = true

        assistantBottomSheet?.show()
        if (clearConversation) {
            assistantBottomSheet?.clearConversation()
        }
        assistantBottomSheet?.setIdleState()
    }

    fun startSession(clearConversation: Boolean = true) {
        ensureInitialized()

        retryCount = 0
        isForceStopping = false
        assistantSessionActive = true

        assistantBottomSheet?.show()
        if (clearConversation) {
            assistantBottomSheet?.clearConversation()
        }
        assistantBottomSheet?.setListeningState()

        startVoiceFlow()
    }

    fun submitTypedText(text: String, clearConversation: Boolean = true) {
        val typedText = text.trim()
        if (typedText.isEmpty()) return

        ensureInitialized()
        stopListeningBeforeSpeak()

        retryCount = 0
        isForceStopping = false
        assistantSessionActive = true

        assistantBottomSheet?.show()
        if (clearConversation) {
            assistantBottomSheet?.clearConversation()
        }
        assistantBottomSheet?.showUserSpeech(typedText)
        assistantBottomSheet?.setProcessingState()

        logUserTranscript(typedText, source = "TYPED")
        host.onAssistantFinalText(typedText.lowercase())
    }

    fun startVoiceFlow() {
        val hasPermission = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            startVoiceRecognition()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun onAudioPermissionGranted() {
        if (assistantSessionActive) {
            startVoiceRecognition()
        }
    }

    fun onAudioPermissionDenied() {
        speak(responseManager.microphonePermissionNeeded(), listenAgain = false)
    }

    fun startVoiceRecognition() {
        // log
        Log.d("VOICE_SESSION", "startVoiceRecognition active=$assistantSessionActive forceStop=$isForceStopping isListening=$isListening")
        if (!assistantSessionActive || isForceStopping || isListening) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                3500L
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                2500L
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                4000L
            )
        }

        speechRecognizer?.startListening(intent)
    }

    fun speak(text: String, listenAgain: Boolean = true) {

        stopListeningBeforeSpeak()
        // log
        Log.d("VOICE_SESSION", "speak listenAgain=$listenAgain active=$assistantSessionActive forceStop=$isForceStopping")

        assistantBottomSheet?.showAssistantReply(text)
        assistantBottomSheet?.setSpeakingState()

        logAssistantTranscript(text, listenAgain)
        voiceHelper.speak(text) {

            activity.runOnUiThread {
                Log.d("VOICE_SESSION", "tts finished, deciding whether to restart listening")
                if (assistantSessionActive && !isForceStopping && listenAgain) {
                    assistantBottomSheet?.setProcessingState()

                    // log
                    Log.d("VOICE_SESSION", "posting delayed restart")
                    activity.window.decorView.postDelayed({

                        Log.d("VOICE_SESSION", "restarting recognizer now")
                        if (assistantSessionActive && !isForceStopping) {
                            assistantBottomSheet?.setListeningState()
                            startVoiceRecognition()
                        }
                    }, 350)
                } else {
                    assistantBottomSheet?.setIdleState()
                }
            }
        }
    }

    fun speakThenStop(text: String, dismissPanel: Boolean = true) {
        stopListeningBeforeSpeak()


        assistantSessionActive = false
        isForceStopping = true

        assistantBottomSheet?.showAssistantReply(text)
        assistantBottomSheet?.setSpeakingState()

        logAssistantTranscript(text, listenAgain = false)
        voiceHelper.speak(text) {
            activity.runOnUiThread {
                assistantBottomSheet?.setIdleState()
                if (dismissPanel) {
                    assistantBottomSheet?.dismiss()
                }
                isForceStopping = false
                host.onAssistantSessionStopped()
            }
        }
    }

    fun speakThenListenAgain(text: String) {
        speak(text, listenAgain = true)
    }

    fun handleListenFailure(reply: String) {
        if (!assistantSessionActive) {
            stopInternal()
            return
        }

        if (retryCount < maxRetryCount) {
            retryCount++
            speakThenListenAgain(reply)
        } else {
            assistantSessionActive = false
            isForceStopping = true
            retryCount = 0
            isListening = false

            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            }

            val finalReply = responseManager.stopListening()
            assistantBottomSheet?.showAssistantReply(finalReply)
            assistantBottomSheet?.setIdleState()

            logAssistantTranscript(finalReply, listenAgain = false)
            voiceHelper.speak(finalReply) {
                activity.runOnUiThread {
                    assistantBottomSheet?.dismiss()
                    isForceStopping = false
                    host.onAssistantSessionStopped()
                }
            }
        }
    }

    fun forceStop() {
        if (!assistantSessionActive && !isListening) {
            assistantBottomSheet?.dismiss()
            host.onAssistantCancelled()
            return
        }

        assistantSessionActive = false
        isForceStopping = true
        isListening = false
        retryCount = 0

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }

        val reply = responseManager.stopListening()
        assistantBottomSheet?.showAssistantReply(reply)
        assistantBottomSheet?.setIdleState()

        logAssistantTranscript(reply, listenAgain = false)
        voiceHelper.speak(reply) {
            activity.runOnUiThread {
                assistantBottomSheet?.dismiss()
                isForceStopping = false
                host.onAssistantCancelled()
            }
        }
    }

    fun dismissPanel() {
        assistantBottomSheet?.dismiss()
    }

    fun getBottomSheet(): AssistantBottomSheet? = assistantBottomSheet

    fun destroy() {
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        assistantBottomSheet = null
    }

    private fun stopInternal() {
        retryCount = 0
        isListening = false
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
        assistantBottomSheet?.dismiss()
    }

    fun speakThenRun(text: String, action: () -> Unit) {

        stopListeningBeforeSpeak()

        assistantBottomSheet?.showAssistantReply(text)
        assistantBottomSheet?.setSpeakingState()

        logAssistantTranscript(text, listenAgain = false)
        voiceHelper.speak(text) {
            activity.runOnUiThread {
                assistantBottomSheet?.setIdleState()
                assistantBottomSheet?.dismiss()
                assistantSessionActive = false
                isForceStopping = false
                action()
            }
        }
    }

    private fun stopListeningBeforeSpeak() {
        suppressNextRecognizerError = true
        isListening = false
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
    }

    fun pauseListeningForAssistantSpeech() {
        stopListeningBeforeSpeak()}

    private fun logUserTranscript(text: String, source: String) {
        DebugDiagnosticLog.longEvent(
            "ASSISTANT_TRANSCRIPT",
            "role=USER\nsource=$source\ntext=$text"
        )
    }

    private fun logAssistantTranscript(text: String, listenAgain: Boolean) {
        DebugDiagnosticLog.longEvent(
            "ASSISTANT_TRANSCRIPT",
            "role=ASSISTANT\ndelivery=SPEAK\nlistenAgain=$listenAgain\ntext=$text"
        )
    }

}
