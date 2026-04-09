package com.example.myapplication

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.TaskEntity
import com.example.myapplication.voice.PendingTaskState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

import com.example.myapplication.voice.CreateTaskDialogState


import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import com.example.myapplication.ai.TimePreferenceLearner
import com.example.myapplication.voice.AssistantResponseManager


import com.example.myapplication.voice.TextNormalizer
import com.example.myapplication.voice.LocalDialogAct
import com.example.myapplication.voice.LocalDialogActInterpreter
import com.example.myapplication.voice.VoiceSessionController
import com.example.myapplication.voice.VoiceSessionState
import com.example.myapplication.ai.LocalDateParser

import com.example.myapplication.BuildConfig // for gemini api key
import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.AiRouter
import com.example.myapplication.ai.GeminiCloudNlpExtractor
import com.example.myapplication.ai.LocalIntentClassifier
import com.example.myapplication.ai.LocalTaskParser

class CreateTaskActivity : AppCompatActivity() {
    private lateinit var aiRouter: AiRouter
    private lateinit var localDateParser: LocalDateParser
    private lateinit var responseManager: AssistantResponseManager

    private var suggestedLearnedTime: String? = null
    private lateinit var timePreferenceLearner: TimePreferenceLearner
    private var pendingSemanticTimePhrase: String? = null

    private var hasConsumedPrefill = false

    private lateinit var sessionController: VoiceSessionController

    private var speechRecognizer: SpeechRecognizer? = null
    private var assistantBottomSheet: AssistantBottomSheet? = null

    private var dialogState = CreateTaskDialogState.IDLE
    private val pendingTaskState = PendingTaskState()

    private lateinit var voiceHelper: VoiceHelper

    private lateinit var etTaskTitle: EditText
    private lateinit var tvSelectedDate: TextView
    private lateinit var tvSelectedTime: TextView

    private lateinit var dao: com.example.myapplication.data.TaskDao

    private var selectedTime: String? = null
    private var selectedHour24: Int? = null
    private var selectedMinute: Int? = null
    private var selectedDate: String? = null
    private var selectedMonth: Int? = null
    private var selectedYear: Int? = null
    private var selectedDay: Int? = null

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startVoiceRecognition()
            } else {
                voiceHelper.speak(responseManager.microphonePermissionNeeded())
                //voiceHelper.speak("Microphone permission is needed for voice commands.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_task)

        val localIntentClassifier = LocalIntentClassifier(this)
        val localTaskParser = LocalTaskParser()
        val cloudExtractor = GeminiCloudNlpExtractor(BuildConfig.GEMINI_API_KEY)

        aiRouter = AiRouter(
            localIntentClassifier,
            localTaskParser,
            cloudExtractor
        )

        etTaskTitle = findViewById(R.id.etTaskTitle)


        val btnSaveTask = findViewById<Button>(R.id.btnSaveTask)
        val btnCancelTask = findViewById<Button>(R.id.btnCancelTask)

        val btnPickTime = findViewById<Button>(R.id.btnPickTime)
        tvSelectedTime = findViewById(R.id.tvSelectedTime)

        val btnPickDate = findViewById<Button>(R.id.btnPickDate)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)

        val btnGoHome = findViewById<Button>(R.id.btnGoHome)
        val btnTalkAssistant = findViewById<Button>(R.id.btnTalkAssistant)

        dao = AppDatabase.getInstance(this).taskDao()

        timePreferenceLearner = TimePreferenceLearner(AppDatabase.getInstance(this).learnedTimePreferenceDao())

        voiceHelper = VoiceHelper(this)
        responseManager = AssistantResponseManager()
        sessionController = VoiceSessionController(voiceHelper) { state ->
            when (state) {
                VoiceSessionState.LISTENING -> assistantBottomSheet?.setListeningState()
                VoiceSessionState.PROCESSING -> assistantBottomSheet?.setProcessingState()
                VoiceSessionState.SPEAKING -> assistantBottomSheet?.setSpeakingState()
                VoiceSessionState.IDLE -> assistantBottomSheet?.setIdleState()
                VoiceSessionState.STOPPED -> assistantBottomSheet?.setIdleState()
            }
        }
        localDateParser = LocalDateParser()

        resetTaskDraftState()

        window.decorView.postDelayed({
            applyIncomingPrefill()
        }, 1500)

        btnPickDate.setOnClickListener {
            openDatePicker()
        }

        btnPickTime.setOnClickListener {
            openTimePicker()
        }

        btnSaveTask.setOnClickListener {
            saveTask()
        }

        btnCancelTask.setOnClickListener {
            sessionController.deactivateSession()
            hasConsumedPrefill = false
           voiceHelper.speak(responseManager.cancelCreate()){
                runOnUiThread {
                    assistantBottomSheet?.dismiss()
                    finish()
                }
            }
        }

        btnGoHome.setOnClickListener {
            sessionController.deactivateSession()
            hasConsumedPrefill = false
            voiceHelper.speak(responseManager.returnHome()){
                runOnUiThread {
                    assistantBottomSheet?.dismiss()
                    finish()
                }
            }
        }

        btnTalkAssistant.setOnClickListener {
            if (assistantBottomSheet == null) {
                assistantBottomSheet = AssistantBottomSheet(this)
            }

            assistantBottomSheet?.setOnDoubleTapCancelListener {
                runOnUiThread {
                    forceStopAssistant()
                }
            }

            assistantBottomSheet?.show()
            assistantBottomSheet?.clearConversation()
            window.decorView.post {
                if (assistantBottomSheet?.isShowing == true) {
                    sessionController.beginSession()
                    assistantBottomSheet?.setListeningState()
                    startVoiceFlow()
                }
            }
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    sessionController.onReadyForSpeech()
                }

                override fun onBeginningOfSpeech() {
                    assistantBottomSheet?.setListeningState()
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    sessionController.onEndOfSpeech()
                }

                override fun onError(error: Int) {
                    if (!sessionController.canHandleRecognizerCallbacks()) return
                    handleListenFailure(responseManager.listenFailure())
                }

                override fun onResults(results: Bundle?) {
                    if (!sessionController.canHandleRecognizerCallbacks()) return
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spokenText = matches?.firstOrNull()?.trim()?.lowercase()

                    if (!spokenText.isNullOrEmpty()) {
                        sessionController.onFinalSpeechReceived()
                        assistantBottomSheet?.showUserSpeech(spokenText)
                        handleVoiceCommand(spokenText)
                    } else {
                        handleListenFailure(responseManager.listenFailure())
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (!sessionController.canHandleRecognizerCallbacks()) return

                    val partialMatches =
                        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partialText = partialMatches?.firstOrNull()?.trim()

                    if (!partialText.isNullOrEmpty()) {
                        assistantBottomSheet?.showUserSpeech(partialText)
                        sessionController.onPartialSpeech()
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    } // end of onCreate()

    //function definitions
    private fun applyIncomingPrefill() {
        if (hasConsumedPrefill) return

        val prefillTitle = intent.getStringExtra("prefill_title")
        val prefillDateText = intent.getStringExtra("prefill_date_text")
        val prefillTimeText = intent.getStringExtra("prefill_time_text")

        val hasPrefill =
            !prefillTitle.isNullOrBlank() ||
                    !prefillDateText.isNullOrBlank() ||
                    !prefillTimeText.isNullOrBlank()

        if (!hasPrefill) {
            clearPrefillExtras()
            hasConsumedPrefill = true
            return
        }

        resetTaskDraftState()

        if (assistantBottomSheet == null) {
            assistantBottomSheet = AssistantBottomSheet(this)
        }
        assistantBottomSheet?.setOnDoubleTapCancelListener {
            runOnUiThread {
                forceStopAssistant()
            }
        }
        assistantBottomSheet?.show()
        assistantBottomSheet?.clearConversation()
        assistantBottomSheet?.setProcessingState()
        window.decorView.post {
            if (assistantBottomSheet?.isShowing == true) {
                sessionController.beginSession()
                assistantBottomSheet?.setProcessingState()
            }
        }

        if (!prefillTitle.isNullOrBlank()) {
            applyTitle(prefillTitle)
        }

        if (!prefillDateText.isNullOrBlank() && applySpokenDate(prefillDateText)) {
            pendingTaskState.dateText = prefillDateText
        }

        // improved time
        if (!prefillTimeText.isNullOrBlank()) {
            if (applySpokenTime(prefillTimeText)) {
                pendingTaskState.timeText = prefillTimeText
                pendingSemanticTimePhrase = null
            } else if (timePreferenceLearner.isSemanticPhrase(prefillTimeText)) {
                pendingTaskState.timeText = prefillTimeText
                pendingSemanticTimePhrase = prefillTimeText
            }
        }

        clearPrefillExtras()
        hasConsumedPrefill = true

        moveToNextMissingStep()
    }

    private fun startVoiceFlow() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            startVoiceRecognition()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceRecognition() {
        if (!sessionController.canStartListening()) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                2500L
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                1800L
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                3000L
            )
        }

        speechRecognizer?.startListening(intent)
    }

    private fun handleVoiceCommand(rawCommand: String) {
        val normalized = TextNormalizer.normalize(rawCommand)
        when (LocalDialogActInterpreter.detect(normalized)) {
            LocalDialogAct.CANCEL,
            LocalDialogAct.STOP,
            LocalDialogAct.END -> {
                forceStopAssistant()
                return
            }
            else -> {}
        }

        if (handleFollowUpInput(normalized)) {
            return
        }

        lifecycleScope.launch {
            try {
                val aiResult = aiRouter.process(normalized)

                when (aiResult.intent) {
                    AiIntent.CREATE_TASK.name -> {
                        if (!aiResult.taskTitle.isNullOrBlank()) {
                            applyTitle(aiResult.taskTitle)
                        }

                        if (!aiResult.dateText.isNullOrBlank() && applySpokenDate(aiResult.dateText)) {
                            pendingTaskState.dateText = aiResult.dateText
                        }

                        if (!aiResult.timeText.isNullOrBlank()) {
                            if (applySpokenTime(aiResult.timeText)) {
                                pendingTaskState.timeText = aiResult.timeText
                                pendingSemanticTimePhrase = null
                            } else if (timePreferenceLearner.isSemanticPhrase(aiResult.timeText)) {
                                pendingTaskState.timeText = aiResult.timeText
                                pendingSemanticTimePhrase = aiResult.timeText
                            }
                        }

                        moveToNextMissingStep()
                    }

                    AiIntent.RESCHEDULE_TASK.name,
                    AiIntent.UPDATE_TASK.name -> {
                        tryApplyInlineCorrection(normalized)
                    }

                    AiIntent.UNKNOWN.name -> {
                        speakWithPanel(responseManager.unknownCommand())
                    }

                    else -> {
                        speakWithPanel(responseManager.unknownCommand())
                    }
                }
            } catch (e: Exception) {
                speakWithPanel(responseManager.parserCrash())
            }
        }
    }


    private fun openDatePicker() {
        val calendar = Calendar.getInstance()
        val year = selectedYear ?: calendar.get(Calendar.YEAR)
        val month = selectedMonth ?: calendar.get(Calendar.MONTH)
        val day = selectedDay ?: calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, pickedYear, pickedMonth, pickedDay ->
                selectedYear = pickedYear
                selectedMonth = pickedMonth
                selectedDay = pickedDay
                selectedDate = formatDate(pickedYear, pickedMonth, pickedDay)
                tvSelectedDate.text = "Selected date: $selectedDate"

                voiceHelper.speak(responseManager.dateSelected(selectedDate ?: ""))
                //voiceHelper.speak("Date selected: $selectedDate")
            },
            year,
            month,
            day
        )

        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
        datePickerDialog.show()
    }

    private fun openTimePicker() {
        val calendar = Calendar.getInstance()
        val hour = selectedHour24 ?: calendar.get(Calendar.HOUR_OF_DAY)
        val minute = selectedMinute ?: calendar.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(
            this,
            { _, pickedHour, pickedMinute ->
                selectedHour24 = pickedHour
                selectedMinute = pickedMinute
                selectedTime = formatTime(pickedHour, pickedMinute)
                tvSelectedTime.text = "Selected time: $selectedTime"

                voiceHelper.speak(responseManager.timeSelected(selectedTime ?: ""))
                    //voiceHelper.speak("Time selected: $selectedTime")
            },
            hour,
            minute,
            false
        )
        timePickerDialog.show()
    }

    private fun saveTask() {
        val title = etTaskTitle.text.toString().trim()

        pendingTaskState.title = title
        pendingTaskState.dateText = selectedDate
        pendingTaskState.timeText = selectedTime

        if (title.isEmpty()) {
            etTaskTitle.error = "Task title cannot be empty"
            etTaskTitle.requestFocus()
            //speakWithPanel("Task title cannot be empty.")
            speakWithPanel(responseManager.taskTitleEmpty())
            return
        }

        if (
            selectedYear == null ||
            selectedMonth == null ||
            selectedDay == null ||
            selectedHour24 == null ||
            selectedMinute == null
        ) {
            //speakWithPanel("Please select both date and time before saving.")
            speakWithPanel(responseManager.missingDateTime())
            Toast.makeText(this, "Please select both date and time", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val insertedId = withContext(Dispatchers.IO) {
                dao.insert(
                    TaskEntity(
                        title = title,
                        dueDate = selectedDate,
                        dueTime = selectedTime
                    )
                )
            }

            val scheduled = scheduleReminder(
                taskId = insertedId.toInt(),
                taskTitle = title,
                year = selectedYear!!,
                month = selectedMonth!!,
                day = selectedDay!!,
                hour24 = selectedHour24!!,
                minute = selectedMinute!!
            )

            if (scheduled) {
                Toast.makeText(
                    this@CreateTaskActivity,
                    "Task and reminder saved!",
                    Toast.LENGTH_SHORT
                ).show()
                dialogState = CreateTaskDialogState.IDLE
                pendingTaskState.clear()

                sessionController.deactivateSession()
                hasConsumedPrefill = false
                //speakThenFinish("Task saved successfully.")
                speakThenFinish(responseManager.saveSuccess())
            } else {
                Toast.makeText(
                    this@CreateTaskActivity,
                    "Task saved, but reminder could not be scheduled.",
                    Toast.LENGTH_LONG
                ).show()
                dialogState = CreateTaskDialogState.IDLE
                pendingTaskState.clear()

                sessionController.deactivateSession()
                hasConsumedPrefill = false
                //speakThenFinish("Task saved, but reminder could not be scheduled.")
                speakThenFinish(responseManager.savePartialFailure())
            }
        }
    }
    private fun formatDateForSpeech(date: String?): String {
        if (date.isNullOrBlank()) return "no date"

        return try {
            val inputFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.UK)
            val outputFormat = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.UK)
            val parsedDate = inputFormat.parse(date)
            if (parsedDate != null) {
                outputFormat.format(parsedDate)
            } else {
                date
            }
        } catch (e: Exception) {
            date
        }
    }

    private fun formatDate(year: Int, month: Int, day: Int): String {
        val displayMonth = month + 1
        return "%02d/%02d/%04d".format(day, displayMonth, year)
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val ampm = if (hour < 12) "AM" else "PM"
        val formattedHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val formattedMinute = minute.toString().padStart(2, '0')
        return "$formattedHour:$formattedMinute $ampm"
    }

    private fun scheduleReminder(
        taskId: Int,
        taskTitle: String,
        year: Int,
        month: Int,
        day: Int,
        hour24: Int,
        minute: Int
    ): Boolean {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            return false
        }

        val intent = Intent(this, ReminderReceiver::class.java).apply {
            putExtra("task_title", taskTitle)
            putExtra("task_id", taskId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            taskId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerCalendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour24)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (triggerCalendar.before(Calendar.getInstance())) {
            Toast.makeText(
                this,
                "Selected date and time is already in the past.",
                Toast.LENGTH_LONG
            ).show()
            //voiceHelper.speak("The selected date and time is already in the past.")
            voiceHelper.speak(responseManager.pastDateTime())
            return false
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerCalendar.timeInMillis,
            pendingIntent
        )

        return true
    }

    private fun applySpokenDate(dateText: String): Boolean {
        val result = localDateParser.parse(dateText)

        if (!result.success || result.normalizedDate == null) {
            return false
        }

        selectedYear = result.year
        selectedMonth = result.month
        selectedDay = result.day
        selectedDate = result.normalizedDate

        tvSelectedDate.text = "Selected date: $selectedDate"
        return true
    }

    private fun applySpokenTime(timeText: String): Boolean {
        val cleaned = timeText
            .lowercase()
            .replace("a.m.", "am")
            .replace("p.m.", "pm")
            .replace(Regex("\\ba\\.?\\s*m\\.?\\b"), "am")
            .replace(Regex("\\bp\\.?\\s*m\\.?\\b"), "pm")
            .replace(Regex("\\bat\\b"), " ")
            .replace(Regex("\\s*:\\s*"), ":")
            .replace(Regex("\\s+"), " ")
            .trim()

        val patterns = listOf(
            "h:mm a",
            "hh:mm a",
            "h a",
            "hh a",
            "H:mm",
            "HH:mm"
        )

        for (pattern in patterns) {
            try {
                val formatter = java.text.SimpleDateFormat(pattern, java.util.Locale.UK)
                formatter.isLenient = false
                val parsed = formatter.parse(cleaned) ?: continue

                val calendar = Calendar.getInstance()
                calendar.time = parsed

                val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)

                selectedHour24 = hour24
                selectedMinute = minute
                selectedTime = formatTime(hour24, minute)
                tvSelectedTime.text = "Selected time: $selectedTime"
                return true
            } catch (_: Exception) {
            }
        }

        return false
    }
    private fun handleFollowUpInput(normalized: String): Boolean {
        return when (dialogState) {
            CreateTaskDialogState.WAITING_FOR_TITLE -> {
                if (normalized.isNotBlank()) {
                    applyTitle(normalized)
                    moveToNextMissingStep()
                    true
                } else {
                    false
                }
            }

            CreateTaskDialogState.WAITING_FOR_DATE -> {
                if (applySpokenDate(normalized)) {
                    pendingTaskState.dateText = normalized
                    voiceHelper.speak(responseManager.dateSet(selectedDate ?: ""))
                    moveToNextMissingStep()
                    true
                } else {
                    speakAndContinueListening(responseManager.invalidDate())
                    true
                }
            }

            CreateTaskDialogState.WAITING_FOR_TIME -> {
                if (suggestedLearnedTime != null && isYes(normalized)) {
                    if (applySpokenTime(suggestedLearnedTime!!)) {
                        pendingTaskState.timeText = suggestedLearnedTime
                        pendingSemanticTimePhrase = null
                        suggestedLearnedTime = null
                        voiceHelper.speak(responseManager.timeSet(selectedTime ?: ""))
                        moveToNextMissingStep()
                        return true
                    }
                }

                if (applySpokenTime(normalized)) {
                    pendingTaskState.timeText = normalized
                    val resolvedTime = selectedTime

                    val semanticPhraseToLearn = pendingSemanticTimePhrase
                    if (!semanticPhraseToLearn.isNullOrBlank() && !resolvedTime.isNullOrBlank()) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            timePreferenceLearner.learnPreference(
                                semanticPhraseToLearn,
                                resolvedTime
                            )
                        }
                    }

                    pendingSemanticTimePhrase = null
                    suggestedLearnedTime = null
                    voiceHelper.speak(responseManager.timeSet(selectedTime ?: ""))
                    moveToNextMissingStep()
                    true
                } else {
                    // let centralized AI try to interpret the follow-up utterance
                    lifecycleScope.launch {
                        try {
                            val aiResult = aiRouter.process(normalized)

                            val candidateTime = aiResult.timeText ?: normalized

                            if (applySpokenTime(candidateTime)) {
                                pendingTaskState.timeText = candidateTime
                                pendingSemanticTimePhrase = null
                                suggestedLearnedTime = null
                                voiceHelper.speak(responseManager.timeSet(selectedTime ?: ""))
                                moveToNextMissingStep()
                            } else if (timePreferenceLearner.isSemanticPhrase(candidateTime)) {
                                pendingTaskState.timeText = candidateTime
                                pendingSemanticTimePhrase = candidateTime
                                suggestedLearnedTime = null
                                moveToNextMissingStep()
                            } else {
                                speakAndContinueListening(responseManager.invalidTime())
                            }
                        } catch (_: Exception) {
                            speakAndContinueListening(responseManager.invalidTime())
                        }
                    }
                    true
                }
            }

            CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION -> {
                if (isYes(normalized)) {
                    dialogState = CreateTaskDialogState.READY_TO_SAVE
                    saveTask()
                    return true
                }

                if (isNo(normalized)) {
                    speakAndContinueListening(responseManager.askWhatToChange())
                    return true
                }

                lifecycleScope.launch {
                    try {
                        val aiResult = aiRouter.process(normalized)

                        when (aiResult.intent) {
                            AiIntent.CREATE_TASK.name,
                            AiIntent.UPDATE_TASK.name,
                            AiIntent.RESCHEDULE_TASK.name -> {
                                if (tryApplyInlineCorrection(normalized)) {
                                    return@launch
                                }

                                if (!aiResult.taskTitle.isNullOrBlank()) {
                                    applyTitle(aiResult.taskTitle)
                                    dialogState = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
                                    speakAndContinueListening(
                                        responseManager.inlineTitleUpdated(buildTaskSummary())
                                    )
                                    return@launch
                                }

                                if (!aiResult.dateText.isNullOrBlank() && applySpokenDate(aiResult.dateText)) {
                                    pendingTaskState.dateText = aiResult.dateText
                                    dialogState = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
                                    speakAndContinueListening(
                                        responseManager.inlineDateUpdated(buildTaskSummary())
                                    )
                                    return@launch
                                }

                                if (!aiResult.timeText.isNullOrBlank()) {
                                    if (applySpokenTime(aiResult.timeText)) {
                                        pendingTaskState.timeText = aiResult.timeText
                                        dialogState = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
                                        speakAndContinueListening(
                                            responseManager.inlineTimeUpdated(buildTaskSummary())
                                        )
                                        return@launch
                                    }
                                }

                                speakAndContinueListening(responseManager.saveConfirmationHelp())
                            }

                            else -> {
                                if (tryApplyInlineCorrection(normalized)) {
                                    return@launch
                                }
                                speakAndContinueListening(responseManager.saveConfirmationHelp())
                            }
                        }
                    } catch (_: Exception) {
                        if (tryApplyInlineCorrection(normalized)) {
                            return@launch
                        }
                        speakAndContinueListening(responseManager.saveConfirmationHelp())
                    }
                }
                true
            }

            else -> false
        }
    }

    private fun applyTitle(title: String) {
        val cleanedTitle = title
            .trim()
            .removePrefix(" .")
            .replace(Regex("^by\\s+"), "buy ")
            .trim()

        pendingTaskState.title = cleanedTitle
        etTaskTitle.setText(cleanedTitle)
    }

    private fun buildTaskSummary(): String {
        val title = pendingTaskState.title ?: etTaskTitle.text.toString().trim()
        val date = formatDateForSpeech(selectedDate)
        val time = selectedTime ?: pendingTaskState.timeText ?: "no time"
        return "$title, $date, $time"
    }

    private fun extractInlineCorrectionValue(normalized: String): String {
        val prefixes = listOf(
            "change it to",
            "change to",
            "make it",
            "rename it to"
        )

        for (prefix in prefixes.sortedByDescending { it.length }) {
            if (normalized.startsWith(prefix)) {
                return normalized.removePrefix(prefix).trim()
            }
        }

        return normalized.trim()
    }

    private fun looksLikeReasonableTitle(value: String): Boolean {
        if (value.isBlank()) return false

        val cleaned = value.trim()

        if (cleaned.length < 3) return false

        val blocked = listOf(
            "yes",
            "no",
            "okay",
            "ok",
            "save",
            "confirm",
            "cancel",
            "stop",
            "help"
        )

        if (cleaned in blocked) return false

        return true
    }

    private fun tryApplyInlineCorrection(normalized: String): Boolean {
        val value = extractInlineCorrectionValue(normalized)

        if (value.isBlank()) return false

        // 1. try date first
        if (applySpokenDate(value)) {
            pendingTaskState.dateText = value
            dialogState = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
            //speakAndContinueListening("Okay. I updated the date. I now have ${buildTaskSummary()}. Should I save it?")
            speakAndContinueListening(responseManager.inlineDateUpdated(buildTaskSummary()))
            return true
        }

        // 2. then try time
        if (applySpokenTime(value)) {
            pendingTaskState.timeText = value
            dialogState = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
            //speakAndContinueListening("Okay. I updated the time. I now have ${buildTaskSummary()}. Should I save it?")
            speakAndContinueListening(responseManager.inlineTimeUpdated(buildTaskSummary()))
            return true
        }

        // 3. then try title
        if (looksLikeReasonableTitle(value)) {
            applyTitle(value)
            dialogState = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
            //speakAndContinueListening("Okay. I updated the title. I now have ${buildTaskSummary()}. Should I save it?")
            speakAndContinueListening(responseManager.inlineTitleUpdated(buildTaskSummary()))
            return true
        }

        // 4. otherwise fail safely
        //speakAndContinueListening("I could not understand that change. Please try again.")
        speakAndContinueListening(responseManager.correctionNotUnderstood())
        return true
    }




    private fun moveToNextMissingStep() {
        when {
            pendingTaskState.title.isNullOrBlank() -> {
                dialogState = CreateTaskDialogState.WAITING_FOR_TITLE
                speakAndContinueListening(responseManager.askTaskTitle())
            }

            pendingTaskState.dateText.isNullOrBlank() || selectedDate.isNullOrBlank() -> {
                dialogState = CreateTaskDialogState.WAITING_FOR_DATE
                speakAndContinueListening(responseManager.askTaskDate())
            }

            pendingTaskState.timeText.isNullOrBlank() || selectedTime.isNullOrBlank() -> {
                dialogState = CreateTaskDialogState.WAITING_FOR_TIME

                lifecycleScope.launch {
                    val semanticPhrase = pendingSemanticTimePhrase

                    if (!semanticPhrase.isNullOrBlank()) {
                        val learned = withContext(Dispatchers.IO) {
                            timePreferenceLearner.getLearnedTimeForPhrase(semanticPhrase)
                        }

                        if (learned != null && learned.usageCount >= 2) {
                            suggestedLearnedTime = learned.resolvedTime
                            runOnUiThread {
                                speakAndContinueListening(
                                    //"You usually mean ${learned.resolvedTime} when you say ${semanticPhrase}. Please say yes to use it, or say a different time."
                                    responseManager.learnedTimeSuggestion(semanticPhrase, learned.resolvedTime)
                                )
                            }
                        } else {
                            runOnUiThread {
                                speakAndContinueListening(
                                    //"I understood the time as ${semanticPhrase}. Please tell me an exact clock time, for example 8 PM."
                                    responseManager.semanticTimeNeedsExact(semanticPhrase)
                                )
                            }
                        }
                    } else {
                        runOnUiThread {
                            speakAndContinueListening(responseManager.askTaskTime())
                        }
                    }
                }
            }

            else -> {
                dialogState = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
                speakAndContinueListening(responseManager.confirmTaskSummary(buildTaskSummary()))
            }
        }
    }

    private fun isTaskReadyToSave(): Boolean {
        return !pendingTaskState.title.isNullOrBlank() &&
                !selectedDate.isNullOrBlank() &&
                !selectedTime.isNullOrBlank()
    }
    private fun shouldContinueConversation(): Boolean {
        return dialogState == CreateTaskDialogState.WAITING_FOR_TITLE ||
                dialogState == CreateTaskDialogState.WAITING_FOR_DATE ||
                dialogState == CreateTaskDialogState.WAITING_FOR_TIME ||
                dialogState == CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
    }

    private fun speakAndContinueListening(text: String) {
        assistantBottomSheet?.showAssistantReply(text)
        assistantBottomSheet?.setProcessingState()

        val hint = when (dialogState) {
            CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION -> responseManager.hintYesNo()
            CreateTaskDialogState.WAITING_FOR_TITLE,
            CreateTaskDialogState.WAITING_FOR_DATE,
            CreateTaskDialogState.WAITING_FOR_TIME -> responseManager.hintChangeFields()
            else -> ""
        }

        assistantBottomSheet?.showAssistantHint(hint)

        voiceHelper.speak(text) {
            runOnUiThread {
                if (shouldContinueConversation()) {
                    assistantBottomSheet?.setListeningState()
                    startVoiceFlow()
                } else {
                    assistantBottomSheet?.setIdleState()
                    assistantBottomSheet?.clearHint()
                }
            }
        }
    }

    private fun speakWithPanel(text: String) {
        assistantBottomSheet?.showAssistantReply(text)
        assistantBottomSheet?.clearHint()
        assistantBottomSheet?.setIdleState()
        voiceHelper.speak(text)
    }

    private fun speakThenListenAgain(text: String) {
        assistantBottomSheet?.showAssistantReply(text)
        assistantBottomSheet?.setErrorState(text)

        voiceHelper.speak(text) {
            runOnUiThread {
                assistantBottomSheet?.setListeningState()
                startVoiceFlow()
            }
        }
    }
    private fun handleListenFailure(reply: String) {
        sessionController.handleListenFailure(
            retryReply = reply,
            onContinueListening = { startVoiceFlow() },
            onRetriesExhausted = {
                val finalReply = responseManager.stopListening()
                assistantBottomSheet?.showAssistantReply(finalReply)
                sessionController.speak(
                    text = finalReply,
                    continueListening = false,
                    onContinueListening = { },
                    onDone = { runOnUiThread { assistantBottomSheet?.dismiss() } }
                )
            }
        )
    }
    private fun speakThenFinish(text: String) {
        assistantBottomSheet?.showAssistantReply(text)
        assistantBottomSheet?.showAssistantHint(responseManager.followUpAnythingElse())
        assistantBottomSheet?.setIdleState()

        voiceHelper.speak(text) {
            runOnUiThread {
                assistantBottomSheet?.dismiss()
                finish()
            }
        }
    }

    private fun resetTaskDraftState() {
        pendingTaskState.clear()

        selectedTime = null
        selectedHour24 = null
        selectedMinute = null
        selectedDate = null
        selectedMonth = null
        selectedYear = null
        selectedDay = null

        dialogState = CreateTaskDialogState.IDLE
        sessionController.deactivateSession()

        etTaskTitle.setText("")
        tvSelectedDate.text = "Selected date: No date selected"
        tvSelectedTime.text = "Selected time: No time selected"
    }
    private fun clearPrefillExtras() {
        intent.removeExtra("prefill_title")
        intent.removeExtra("prefill_date_text")
        intent.removeExtra("prefill_time_text")
    }

        private fun isYes(normalized: String): Boolean {
        val value = normalized.trim().lowercase()
        return value == "yes" ||
                value == "yes yes" ||
                value == "yeah" ||
                value == "yep" ||
                value == "save" ||
                value == "okay yes" ||
                value == "ok yes"
    }

    private fun isNo(normalized: String): Boolean {
        val value = normalized.trim().lowercase()
        return value == "no" ||
                value == "nope" ||
                value == "don't save" ||
                value == "do not save" ||
                value == "not now"
    }

    private fun forceStopAssistant() {
        sessionController.hardStop(
            cancelRecognizer = { try { speechRecognizer?.cancel() } catch (_: Exception) {} },
            dismissPanel = {
                assistantBottomSheet?.clearHint()
                assistantBottomSheet?.dismiss()
            },
            clearConversationState = { dialogState = CreateTaskDialogState.IDLE }
        )
    }

    override fun onDestroy() {
        assistantBottomSheet?.dismiss()
        assistantBottomSheet = null
        speechRecognizer?.destroy()
        voiceHelper.shutdown()
        super.onDestroy()
    }
}
