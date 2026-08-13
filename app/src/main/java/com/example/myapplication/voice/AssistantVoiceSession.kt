package com.example.myapplication.voice

import android.Manifest

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.example.myapplication.AssistantBottomSheet
import com.example.myapplication.VoiceHelper
import com.example.myapplication.preferences.AppPreferences
import com.example.myapplication.diagnostics.AssistantTranscriptDiagnosticLogger
import com.example.myapplication.accessibility.AssistantAccessibilityState

class AssistantVoiceSession(
    private val activity: AppCompatActivity,
    private val host: AssistantVoiceHost,
    private val voiceHelper: VoiceHelper,
    private val responseManager: AssistantResponseManager,
    private val audioPermissionLauncher: ActivityResultLauncher<String>,
    private val normalizeFinalTextForHost: Boolean = true,
    private val onAccessibilityStateChanged: (AssistantAccessibilityState) -> Unit = {}
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
    private var waitingForConfirmation = false
    private var assistantControl: View? = null
    private var recognitionRequestActive = false
    private var sessionGeneration = 0L
    private var pendingRecognitionRestart: Runnable? = null
    private var terminalDeliveryActive = false
    private val typedInputCancellationRecovery = TypedInputCancellationRecoveryPolicy()
    private val processingHapticFeedback = ProcessingHapticFeedbackController(
        scheduler = MainLooperProcessingHapticScheduler(),
        isEnabled = {
            AppPreferences(activity).processingHapticEnabled
        },
        performPulse = {
            !activity.isFinishing &&
                !activity.isDestroyed &&
                assistantSessionActive &&
                !isForceStopping &&
                assistantBottomSheet?.performProcessingHapticPulse() == true
        }
    )
    private val sessionEndVibrationPerformer = AndroidSessionEndVibrationPerformer(activity)
    private val sessionEndHapticFeedback = AssistantSessionEndHapticFeedback(
        isEnabled = {
            AppPreferences(activity).sessionEndHapticEnabled
        },
        performVibration = { durationMs ->
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                !activity.isFinishing &&
                !activity.isDestroyed &&
                sessionEndVibrationPerformer.vibrate(durationMs)
        },
        cancelVibration = sessionEndVibrationPerformer::cancel
    )
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            processingHapticFeedback.onLifecycleStarted()
        }

        override fun onStop(owner: LifecycleOwner) {
            processingHapticFeedback.onLifecycleStopped()
            sessionEndHapticFeedback.cancelActive()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            processingHapticFeedback.destroy()
            sessionEndHapticFeedback.destroy()
        }
    }

    init {
        activity.lifecycle.addObserver(lifecycleObserver)
    }

    fun ensureInitialized() {
        if (assistantBottomSheet == null) {
            assistantBottomSheet = AssistantBottomSheet(
                activity = activity,
                onStateChanged = onAccessibilityStateChanged,
                onPanelDismissed = { processingHapticFeedback.stop("PANEL_DISMISSED") }
            )
            assistantBottomSheet?.setOnDoubleTapCancelListener {
                activity.runOnUiThread {
                    forceStop()
                }
            }
            assistantBottomSheet?.setOnTypedInputRequestedListener {
                activity.runOnUiThread {
                    typedInputCancellationRecovery.onPanelTypedInputRequested(
                        sessionActive = assistantSessionActive,
                        forceStopping = isForceStopping
                    )
                    stopListeningBeforeSpeak()
                    showAssistantState(AssistantAccessibilityState.READY)
                    host.onAssistantTypedInputRequested()
                }
            }
            assistantBottomSheet?.setFocusReturnView(assistantControl)
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("VOICE_SESSION", "onReadyForSpeech")
                        if (!assistantSessionActive || isForceStopping) {
                            recognitionRequestActive = false
                            isListening = false
                            return
                        }
                        suppressNextRecognizerError = false
                        updateListeningAccessibilityState()
                        isListening = true
                        recognitionRequestActive = true
                    }

                    override fun onBeginningOfSpeech() {
                        if (!assistantSessionActive || isForceStopping) return
                        updateListeningAccessibilityState()
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                        recognitionRequestActive = false
                        if (!assistantSessionActive || isForceStopping) return
                        showAssistantState(AssistantAccessibilityState.PROCESSING)
                    }

                    override fun onError(error: Int) {
                        Log.d("VOICE_SESSION", "onError code=$error suppress=$suppressNextRecognizerError active=$assistantSessionActive")
                        isListening = false
                        recognitionRequestActive = false

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
                                postRecognitionRestart(400L)
                            }

                            else -> {
                                handleListenFailure(responseManager.listenFailure())
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        recognitionRequestActive = false
                        if (!assistantSessionActive || isForceStopping) return

                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val finalRecognizedText = matches?.firstOrNull()?.trim()
                        val spokenText = finalRecognizedText?.lowercase()

                        if (!spokenText.isNullOrEmpty()) {
                            waitingForConfirmation = false
                            retryCount = 0
                            logUserTranscript(
                                requireNotNull(finalRecognizedText),
                                source = "VOICE"
                            )
                            assistantBottomSheet?.showUserSpeech(spokenText)
                            showAssistantState(
                                AssistantAccessibilityState.PROCESSING,
                                submittedCommand = true
                            )
                            host.onAssistantFinalText(
                                if (normalizeFinalTextForHost) spokenText else finalRecognizedText
                            )
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
                            updateListeningAccessibilityState()
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    fun startPassiveSession(clearConversation: Boolean = true) {
        ensureInitialized()
        beginSessionGeneration()

        retryCount = 0
        isForceStopping = false
        isListening = false
        waitingForConfirmation = false
        assistantSessionActive = true

        assistantBottomSheet?.show()
        if (clearConversation) {
            assistantBottomSheet?.clearConversation()
        }
        showAssistantState(AssistantAccessibilityState.READY)
    }

    fun prepareForContextEntry() {
        processingHapticFeedback.stop(ProcessingHapticFeedbackController.REASON_SESSION_STOPPED)
        invalidateSessionCallbacks()
        isListening = false
        terminalDeliveryActive = false
        waitingForConfirmation = false
        assistantSessionActive = false
        isForceStopping = false
        cancelRecognitionIfActive()
    }

    /**
     * Stops only resources that already exist. This is safe from onStop even when the
     * assistant has never been opened and must never initialize session UI or recognition.
     */
    fun stopForLifecycle() {
        processingHapticFeedback.onLifecycleStopped()
        sessionEndHapticFeedback.cancelActive()
        invalidateSessionCallbacks()
        assistantSessionActive = false
        waitingForConfirmation = false
        isListening = false
        retryCount = 0
        isForceStopping = false
        terminalDeliveryActive = false

        cancelRecognitionIfActive()

        assistantBottomSheet?.let { panel ->
            if (panel.isShowing) {
                if (panel.isContentReady) {
                    showAssistantState(AssistantAccessibilityState.STOPPED)
                }
                panel.dismissWithoutFocusReturn()
            }
        }
    }

    fun startSession(clearConversation: Boolean = true) {
        ensureInitialized()
        beginSessionGeneration()

        retryCount = 0
        isForceStopping = false
        waitingForConfirmation = false
        assistantSessionActive = true

        assistantBottomSheet?.show()
        if (clearConversation) {
            assistantBottomSheet?.clearConversation()
        }
        showAssistantState(AssistantAccessibilityState.LISTENING)

        startVoiceFlow()
    }

    fun submitTypedText(text: String, clearConversation: Boolean = true) {
        val typedText = text.trim()
        if (typedText.isEmpty()) return
        typedInputCancellationRecovery.onTypedInputSubmitted()

        ensureInitialized()
        stopListeningBeforeSpeak()
        beginSessionGeneration()

        retryCount = 0
        isForceStopping = false
        assistantSessionActive = true
        waitingForConfirmation = false

        assistantBottomSheet?.show()
        if (clearConversation) {
            assistantBottomSheet?.clearConversation()
        }
        assistantBottomSheet?.showUserSpeech(typedText)
        showAssistantState(
            AssistantAccessibilityState.PROCESSING,
            submittedCommand = true
        )

        logUserTranscript(typedText, source = "TYPED")
        host.onAssistantFinalText(
            if (normalizeFinalTextForHost) typedText.lowercase() else typedText
        )
    }

    fun onTypedInputCancelled() {
        val shouldRecover = typedInputCancellationRecovery.claimRecovery(
            sessionActive = assistantSessionActive,
            forceStopping = isForceStopping,
            listening = isListening
        )
        if (!shouldRecover) return

        postRecognitionRestart(TYPED_INPUT_RECOVERY_DELAY_MS, useVoiceFlow = true)
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
        if (assistantSessionActive && !isForceStopping) {
            startVoiceRecognition()
        }
    }

    fun onAudioPermissionDenied() {
        showAssistantState(
            AssistantAccessibilityState.ERROR,
            errorText = "Microphone permission required"
        )
        speak(responseManager.microphonePermissionNeeded(), listenAgain = false)
    }

    fun startVoiceRecognition() {
        // log
        Log.d("VOICE_SESSION", "startVoiceRecognition active=$assistantSessionActive forceStop=$isForceStopping isListening=$isListening recognitionActive=$recognitionRequestActive")
        if (!assistantSessionActive || isForceStopping || isListening || recognitionRequestActive) return

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

        val recognizer = speechRecognizer ?: return
        recognitionRequestActive = true
        try {
            recognizer.startListening(intent)
        } catch (error: RuntimeException) {
            recognitionRequestActive = false
            throw error
        }
    }

    fun speak(text: String, listenAgain: Boolean = true) {

        stopListeningBeforeSpeak()
        val callbackGeneration = sessionGeneration
        // log
        Log.d("VOICE_SESSION", "speak listenAgain=$listenAgain active=$assistantSessionActive forceStop=$isForceStopping")

        assistantBottomSheet?.showAssistantReply(text)
        showAssistantState(AssistantAccessibilityState.SPEAKING)

        logAssistantTranscript(text, listenAgain)
        voiceHelper.speak(text) {

            activity.runOnUiThread {
                if (callbackGeneration != sessionGeneration) return@runOnUiThread
                Log.d("VOICE_SESSION", "tts finished, deciding whether to restart listening")
                if (assistantSessionActive && !isForceStopping && listenAgain) {
                    // log
                    Log.d("VOICE_SESSION", "posting delayed restart")
                    postRecognitionRestart(350L)
                } else {
                    showAssistantState(AssistantAccessibilityState.READY)
                }
            }
        }
    }

    fun speakThenStop(text: String, dismissPanel: Boolean = true) {
        stopListeningBeforeSpeak()
        val callbackGeneration = sessionGeneration

        assistantSessionActive = false
        isForceStopping = true
        terminalDeliveryActive = true

        assistantBottomSheet?.showAssistantReply(text)
        showAssistantState(AssistantAccessibilityState.SPEAKING)

        logAssistantTranscript(text, listenAgain = false)
        voiceHelper.speak(text) {
            activity.runOnUiThread {
                if (callbackGeneration != sessionGeneration || !terminalDeliveryActive) {
                    return@runOnUiThread
                }
                showAssistantState(AssistantAccessibilityState.STOPPED)
                sessionEndHapticFeedback.deliverOnce(callbackGeneration)
                if (dismissPanel) {
                    assistantBottomSheet?.dismiss()
                }
                terminalDeliveryActive = false
                isForceStopping = false
                host.onAssistantSessionStopped()
            }
        }
    }

    /** Completes a model-routed END_SESSION without entering the user-cancel path. */
    fun endConversation(reply: String) {
        if (terminalDeliveryActive || (!assistantSessionActive && isForceStopping)) return

        val closingReply = reply.trim().ifBlank { "Okay, stopping the assistant." }
        invalidateSessionCallbacks()
        val callbackGeneration = sessionGeneration
        cancelRecognitionIfActive()
        waitingForConfirmation = false
        retryCount = 0
        isListening = false
        assistantSessionActive = false
        isForceStopping = true
        terminalDeliveryActive = true

        assistantBottomSheet?.showAssistantReply(closingReply)
        showAssistantState(AssistantAccessibilityState.SPEAKING)
        logAssistantTranscript(closingReply, listenAgain = false)
        voiceHelper.speak(closingReply) {
            activity.runOnUiThread {
                if (callbackGeneration != sessionGeneration || !terminalDeliveryActive) {
                    return@runOnUiThread
                }
                showAssistantState(AssistantAccessibilityState.STOPPED)
                sessionEndHapticFeedback.deliverOnce(callbackGeneration)
                assistantBottomSheet?.dismiss()
                terminalDeliveryActive = false
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
            invalidateSessionCallbacks()
            val callbackGeneration = sessionGeneration
            assistantSessionActive = false
            isForceStopping = true
            terminalDeliveryActive = true
            retryCount = 0
            isListening = false

            cancelRecognitionIfActive()

            val finalReply = responseManager.stopListening()
            assistantBottomSheet?.showAssistantReply(finalReply)
            showAssistantState(
                AssistantAccessibilityState.ERROR,
                errorText = "Assistant could not hear a response"
            )

            logAssistantTranscript(finalReply, listenAgain = false)
            voiceHelper.speak(finalReply) {
                activity.runOnUiThread {
                    if (callbackGeneration != sessionGeneration || !terminalDeliveryActive) {
                        return@runOnUiThread
                    }
                    showAssistantState(AssistantAccessibilityState.STOPPED)
                    sessionEndHapticFeedback.deliverOnce(callbackGeneration)
                    assistantBottomSheet?.dismiss()
                    terminalDeliveryActive = false
                    isForceStopping = false
                    host.onAssistantSessionStopped()
                }
            }
        }
    }

    fun forceStop() {
        if (terminalDeliveryActive) return
        invalidateSessionCallbacks()
        val callbackGeneration = sessionGeneration
        if (!assistantSessionActive && !isListening) {
            showAssistantState(AssistantAccessibilityState.STOPPED)
            assistantBottomSheet?.dismiss()
            host.onAssistantCancelled()
            return
        }

        assistantSessionActive = false
        isForceStopping = true
        terminalDeliveryActive = true
        isListening = false
        retryCount = 0

        cancelRecognitionIfActive()

        val reply = responseManager.stopListening()
        assistantBottomSheet?.showAssistantReply(reply)
        showAssistantState(AssistantAccessibilityState.SPEAKING)

        logAssistantTranscript(reply, listenAgain = false)
        voiceHelper.speak(reply) {
            activity.runOnUiThread {
                if (callbackGeneration != sessionGeneration || !terminalDeliveryActive) {
                    return@runOnUiThread
                }
                showAssistantState(AssistantAccessibilityState.STOPPED)
                sessionEndHapticFeedback.deliverOnce(callbackGeneration)
                assistantBottomSheet?.dismiss()
                terminalDeliveryActive = false
                isForceStopping = false
                host.onAssistantCancelled()
            }
        }
    }

    fun dismissPanel() {
        processingHapticFeedback.stop("PANEL_DISMISSED")
        assistantBottomSheet?.let { panel ->
            if (!panel.isShowing) return@let
            if (panel.isContentReady) {
                showAssistantState(AssistantAccessibilityState.STOPPED)
            }
            panel.dismissWithoutFocusReturn()
        }
    }

    fun bindAssistantControl(view: View) {
        assistantControl = view
        assistantBottomSheet?.setFocusReturnView(view)
    }

    fun expectConfirmation() {
        waitingForConfirmation = true
        processingHapticFeedback.stop(AssistantAccessibilityState.WAITING_FOR_CONFIRMATION.name)
    }

    fun getBottomSheet(): AssistantBottomSheet? = assistantBottomSheet

    fun destroy() {
        processingHapticFeedback.destroy()
        sessionEndHapticFeedback.destroy()
        activity.lifecycle.removeObserver(lifecycleObserver)
        invalidateSessionCallbacks()
        assistantSessionActive = false
        waitingForConfirmation = false
        isListening = false
        terminalDeliveryActive = false
        cancelRecognitionIfActive()
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        assistantBottomSheet = null
    }

    private fun stopInternal() {
        invalidateSessionCallbacks()
        retryCount = 0
        isListening = false
        cancelRecognitionIfActive()
        showAssistantState(AssistantAccessibilityState.STOPPED)
        assistantBottomSheet?.dismiss()
    }

    fun speakThenRun(text: String, action: () -> Unit) {

        stopListeningBeforeSpeak()
        val callbackGeneration = sessionGeneration

        assistantBottomSheet?.showAssistantReply(text)
        showAssistantState(AssistantAccessibilityState.SPEAKING)

        logAssistantTranscript(text, listenAgain = false)
        voiceHelper.speak(text) {
            activity.runOnUiThread {
                if (callbackGeneration != sessionGeneration) return@runOnUiThread
                showAssistantState(AssistantAccessibilityState.STOPPED)
                assistantBottomSheet?.dismissWithoutFocusReturn()
                assistantSessionActive = false
                isForceStopping = false
                action()
            }
        }
    }

    private fun stopListeningBeforeSpeak() {
        invalidateSessionCallbacks()
        isListening = false
        cancelRecognitionIfActive()
    }

    fun pauseListeningForAssistantSpeech() {
        stopListeningBeforeSpeak()
    }

    private fun logUserTranscript(text: String, source: String) {
        AssistantTranscriptDiagnosticLogger.user(text, source)
    }

    private fun logAssistantTranscript(text: String, listenAgain: Boolean) {
        AssistantTranscriptDiagnosticLogger.assistant(text, listenAgain)
    }

    private fun updateListeningAccessibilityState() {
        if (waitingForConfirmation) {
            showAssistantState(AssistantAccessibilityState.WAITING_FOR_CONFIRMATION)
        } else {
            showAssistantState(AssistantAccessibilityState.LISTENING)
        }
    }

    private fun showAssistantState(
        state: AssistantAccessibilityState,
        submittedCommand: Boolean = false,
        errorText: String = ""
    ) {
        if (state == AssistantAccessibilityState.PROCESSING && submittedCommand) {
            processingHapticFeedback.start()
        } else {
            processingHapticFeedback.stop(state.name)
        }

        when (state) {
            AssistantAccessibilityState.READY -> assistantBottomSheet?.setIdleState()
            AssistantAccessibilityState.LISTENING -> assistantBottomSheet?.setListeningState()
            AssistantAccessibilityState.PROCESSING -> assistantBottomSheet?.setProcessingState()
            AssistantAccessibilityState.SPEAKING -> assistantBottomSheet?.setSpeakingState()
            AssistantAccessibilityState.WAITING_FOR_CONFIRMATION ->
                assistantBottomSheet?.setWaitingForConfirmationState()
            AssistantAccessibilityState.STOPPED -> assistantBottomSheet?.setStoppedState()
            AssistantAccessibilityState.ERROR -> assistantBottomSheet?.setErrorState(errorText)
        }
    }

    private fun beginSessionGeneration() {
        invalidateSessionCallbacks()
        terminalDeliveryActive = false
    }

    private fun invalidateSessionCallbacks() {
        sessionGeneration += 1L
        pendingRecognitionRestart?.let(activity.window.decorView::removeCallbacks)
        pendingRecognitionRestart = null
    }

    private fun postRecognitionRestart(
        delayMs: Long,
        useVoiceFlow: Boolean = false
    ) {
        pendingRecognitionRestart?.let(activity.window.decorView::removeCallbacks)
        val callbackGeneration = sessionGeneration
        lateinit var restart: Runnable
        restart = Runnable {
            if (pendingRecognitionRestart === restart) pendingRecognitionRestart = null
            if (
                callbackGeneration != sessionGeneration ||
                !assistantSessionActive ||
                isForceStopping ||
                isListening ||
                recognitionRequestActive
            ) {
                return@Runnable
            }
            Log.d("VOICE_SESSION", "restarting recognizer now generation=$callbackGeneration")
            updateListeningAccessibilityState()
            if (useVoiceFlow) startVoiceFlow() else startVoiceRecognition()
        }
        pendingRecognitionRestart = restart
        activity.window.decorView.postDelayed(restart, delayMs)
    }

    private fun cancelRecognitionIfActive() {
        val recognizer = speechRecognizer ?: return
        if (!recognitionRequestActive && !isListening) return
        suppressNextRecognizerError = true
        recognitionRequestActive = false
        isListening = false
        try {
            recognizer.cancel()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val TYPED_INPUT_RECOVERY_DELAY_MS = 200L
    }

}
