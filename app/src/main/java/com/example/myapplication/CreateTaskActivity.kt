package com.example.myapplication

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.TaskEntity
import com.example.myapplication.voice.PendingTaskState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

import com.example.myapplication.voice.CreateTaskDialogState

import android.util.Log
import com.example.myapplication.ai.TimePreferenceLearner
import com.example.myapplication.voice.AssistantResponseManager


import com.example.myapplication.voice.TextNormalizer
import com.example.myapplication.ai.LocalDateParser

import com.example.myapplication.BuildConfig // for gemini api key
import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.AiRouter
import com.example.myapplication.ai.GeminiCloudNlpExtractor
import com.example.myapplication.ai.LocalIntentClassifier
import com.example.myapplication.ai.LocalTaskParser
import com.example.myapplication.voice.AssistantPromptHelper

import com.example.myapplication.voice.AssistantVoiceHost
import com.example.myapplication.voice.AssistantVoiceSession

import com.example.myapplication.voice.SpokenTimeParser
import com.example.myapplication.ai.temporal.TemporalActionPolicy
import com.example.myapplication.ai.temporal.TemporalExpressionResolver
import com.example.myapplication.ai.temporal.TemporalResolution
import com.example.myapplication.ai.temporal.TemporalResolutionStatus

class CreateTaskActivity : AppCompatActivity(), AssistantVoiceHost {
    private lateinit var promptHelper: AssistantPromptHelper

    private lateinit var assistantSession: AssistantVoiceSession

    private lateinit var aiRouter: AiRouter
    private lateinit var localDateParser: LocalDateParser
    private lateinit var responseManager: AssistantResponseManager

    private var suggestedLearnedTime: String? = null
    private lateinit var timePreferenceLearner: TimePreferenceLearner
    private var pendingSemanticTimePhrase: String? = null
    private val temporalResolver = TemporalExpressionResolver()
    private var pendingTemporalConstraint: TemporalResolution? = null

    private var hasConsumedPrefill = false



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
                assistantSession.onAudioPermissionGranted()
            } else {
                assistantSession.onAudioPermissionDenied()
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
        responseManager = AssistantResponseManager.fromPreferences(this)
        localDateParser = LocalDateParser()

        assistantSession = AssistantVoiceSession(
            activity = this,
            host = this,
            voiceHelper = voiceHelper,
            responseManager = responseManager,
            audioPermissionLauncher = audioPermissionLauncher
        )

        promptHelper = AssistantPromptHelper(assistantSession, responseManager)

        resetTaskDraftState()

        window.decorView.postDelayed({
            applyIncomingPrefill()
        }, 1500)

        btnPickDate.setOnClickListenerWithHaptic {
            openDatePicker()
        }

        btnPickTime.setOnClickListenerWithHaptic {
            openTimePicker()
        }

        btnSaveTask.setOnClickListenerWithHaptic {
            saveTask()
        }

        btnCancelTask.setOnClickListenerWithHaptic {
            hasConsumedPrefill = false
            assistantSession.speakThenRun(responseManager.cancelCreate()) {
                finish()
            }
        }

        btnGoHome.setOnClickListenerWithHaptic {
            hasConsumedPrefill = false
            assistantSession.speakThenRun(responseManager.returnHome()) {
                finish()
            }
        }

        btnTalkAssistant.setOnClickListenerWithHaptic {
            assistantSession.startSession()
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

        assistantSession.startPassiveSession(clearConversation = true)
        assistantSession.getBottomSheet()?.setProcessingState()

        if (!prefillTitle.isNullOrBlank()) {
            applyTitle(prefillTitle)
        }

        applyTemporalPrefill(prefillDateText, prefillTimeText)

        clearPrefillExtras()
        hasConsumedPrefill = true

        moveToNextMissingStep()
    }


    private fun applyTemporalPrefill(dateText: String?, timeText: String?) {
        val resolution = temporalResolver.resolve(dateText, timeText, listOfNotNull(dateText, timeText).joinToString(" "))
        if (resolution.status == TemporalResolutionStatus.UNRESOLVED) return
        if (resolution.hasDateConstraint || resolution.hasTimeConstraint) pendingTemporalConstraint = resolution
        if (resolution.isExactDate && resolution.startDateInclusive != null) {
            applySpokenDate(resolution.startDateInclusive)
            pendingTaskState.dateText = dateText ?: resolution.startDateInclusive
        } else if (!dateText.isNullOrBlank()) {
            pendingTaskState.dateText = dateText
        }
        if (resolution.isExactTime && resolution.startMinuteInclusive != null) {
            applySpokenTime(timeText ?: resolution.spokenLabel)
            pendingTaskState.timeText = timeText
            pendingSemanticTimePhrase = null
        } else if (!timeText.isNullOrBlank()) {
            pendingTaskState.timeText = timeText
            pendingSemanticTimePhrase = timeText
        }
    }

    private fun invalidTemporalDateMessage(): String {
        val c = pendingTemporalConstraint
        return if (c?.hasDateConstraint == true) {
            "That date is outside ${c.originalDatePhrase.ifBlank { c.spokenLabel }}. Please choose a date from ${c.startDateInclusive ?: "the allowed range"} through ${c.endDateInclusive ?: c.startDateInclusive}."
        } else responseManager.invalidDate()
    }

    private fun invalidTemporalTimeMessage(): String {
        val c = pendingTemporalConstraint
        return if (c?.hasTimeConstraint == true) {
            "That time is outside ${c.originalTimePhrase.ifBlank { c.spokenLabel }}. Please choose an exact time in that window."
        } else responseManager.invalidTime()
    }

    private fun handleVoiceCommand(rawCommand: String) {
        val normalized = TextNormalizer.normalize(rawCommand)
        // log
        Log.d(
            "CREATE_VOICE",
            "raw='$rawCommand' normalized='$normalized' dialogState=$dialogState title='${pendingTaskState.title}' date='${pendingTaskState.dateText}' time='${pendingTaskState.timeText}' selectedDate='$selectedDate' selectedTime='$selectedTime'"
        )

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

                assistantSession.pauseListeningForAssistantSpeech()
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

                assistantSession.pauseListeningForAssistantSpeech()
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
                hasConsumedPrefill = false
                speakThenFinish(responseManager.saveSuccess())

            } else {
                Toast.makeText(
                    this@CreateTaskActivity,
                    "Task saved, but reminder could not be scheduled.",
                    Toast.LENGTH_LONG
                ).show()
                dialogState = CreateTaskDialogState.IDLE
                pendingTaskState.clear()
                hasConsumedPrefill = false
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
        return ScheduleTextParser.formatTime(hour, minute)
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

            assistantSession.pauseListeningForAssistantSpeech()
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
        val resolution = temporalResolver.resolve(dateText, null, dateText)
        if (!resolution.isExactDate || resolution.startDateInclusive == null) return false
        if (!TemporalActionPolicy.validateClarification(pendingTemporalConstraint ?: resolution, resolution.startDateInclusive, null)) return false
        selectedDate = resolution.startDateInclusive
        val parts = selectedDate!!.split("/")
        selectedDay = parts.getOrNull(0)?.toIntOrNull()
        selectedMonth = parts.getOrNull(1)?.toIntOrNull()?.minus(1)
        selectedYear = parts.getOrNull(2)?.toIntOrNull()
        tvSelectedDate.text = "Selected date: $selectedDate"
        return true
    }

    private fun applySpokenTime(timeText: String): Boolean {
        val resolution = temporalResolver.resolve(null, timeText, timeText)
        if (!resolution.isExactTime || resolution.startMinuteInclusive == null) return false
        val minute = resolution.startMinuteInclusive
        if (!TemporalActionPolicy.validateClarification(pendingTemporalConstraint ?: resolution, null, minute)) return false
        selectedHour24 = minute / 60
        selectedMinute = minute % 60
        selectedTime = formatTime(selectedHour24!!, selectedMinute!!)
        tvSelectedTime.text = "Selected time: $selectedTime"
        return true
    }

    private fun handleFollowUpInput(normalized: String): Boolean {
        // log
        Log.d(
            "CREATE_FOLLOWUP",
            "dialogState=$dialogState normalized='$normalized' title='${pendingTaskState.title}' date='${pendingTaskState.dateText}' time='${pendingTaskState.timeText}'"
        )

        return when (dialogState) {
                CreateTaskDialogState.WAITING_FOR_TITLE -> {

                    Log.d("CREATE_FOLLOWUP", "WAITING_FOR_TITLE")

                if (looksLikeReasonableTitle(normalized)) {
                    applyTitle(normalized)
                    moveToNextMissingStep()
                    true
                } else {
                    speakAndContinueListening(responseManager.askTaskTitle())
                    true
                }
            }

            CreateTaskDialogState.WAITING_FOR_DATE -> {

                Log.d("CREATE_FOLLOWUP", "WAITING_FOR_DATE")

                if (applySpokenDate(normalized)) {
                    pendingTaskState.dateText = normalized

                    assistantSession.pauseListeningForAssistantSpeech()
                    voiceHelper.speak(responseManager.dateSet(selectedDate ?: ""))
                    moveToNextMissingStep()
                    true
                } else {
                    speakAndContinueListening(invalidTemporalDateMessage())
                    true
                }
            }

            CreateTaskDialogState.WAITING_FOR_TIME -> {

                Log.d("CREATE_FOLLOWUP", "WAITING_FOR_TIME")

                if (suggestedLearnedTime != null && isYes(normalized)) {
                    if (applySpokenTime(suggestedLearnedTime!!)) {
                        pendingTaskState.timeText = suggestedLearnedTime
                        pendingSemanticTimePhrase = null
                        suggestedLearnedTime = null

                        assistantSession.pauseListeningForAssistantSpeech()
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

                    assistantSession.pauseListeningForAssistantSpeech()
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

                                assistantSession.pauseListeningForAssistantSpeech()
                                voiceHelper.speak(responseManager.timeSet(selectedTime ?: ""))
                                moveToNextMissingStep()
                            } else if (timePreferenceLearner.isSemanticPhrase(candidateTime)) {
                                pendingTaskState.timeText = candidateTime
                                pendingSemanticTimePhrase = candidateTime
                                suggestedLearnedTime = null
                                moveToNextMissingStep()
                            } else {
                                speakAndContinueListening(invalidTemporalTimeMessage())
                            }
                        } catch (_: Exception) {
                            speakAndContinueListening(responseManager.invalidTime())
                        }
                    }
                    true
                }
            }

            CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD -> {
                Log.d("CREATE_FOLLOWUP", "WAITING_FOR_CHANGE_FIELD")

                when {
                    normalized == "title" || normalized.contains("change title") || normalized.contains("edit title") -> {
                        dialogState = CreateTaskDialogState.WAITING_FOR_TITLE
                        promptHelper.speakInfo(responseManager.askChangeTitle(), true, responseManager.hintTitle())
                        true
                    }

                    normalized == "date" || normalized.contains("change date") || normalized.contains("edit date") -> {
                        dialogState = CreateTaskDialogState.WAITING_FOR_DATE
                        promptHelper.speakInfo(responseManager.askChangeDate(), true, responseManager.hintDate())
                        true
                    }

                    normalized == "time" || normalized.contains("change time") || normalized.contains("edit time") -> {
                        dialogState = CreateTaskDialogState.WAITING_FOR_TIME
                        promptHelper.speakInfo(responseManager.askChangeTime(), true, responseManager.hintTime())
                        true
                    }

                    else -> {
                        speakAndContinueListening(responseManager.askWhatToChange())
                        true
                    }
                }
            }

            CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION -> {

                Log.d("CREATE_FOLLOWUP", "WAITING_FOR_SAVE_CONFIRMATION")

                if (isYes(normalized)) {
                    dialogState = CreateTaskDialogState.READY_TO_SAVE
                    saveTask()
                    return true
                }

                if (isNo(normalized)) {
                    dialogState = CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD
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

        val cleaned = value.trim().lowercase()

        if (cleaned.length < 3) return false

        val blocked = listOf(
            "title",
            "date",
            "time",
            "change title",
            "change date",
            "change time",
            "edit title",
            "edit date",
            "edit time",
            "yes",
            "yeah",
            "yep",
            "sure",
            "no",
            "nope",
            "okay",
            "ok",
            "alright",
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
        // log
        Log.d(
            "CREATE_STATE",
            "title='${pendingTaskState.title}' selectedDate='$selectedDate' selectedTime='$selectedTime' semantic='$pendingSemanticTimePhrase'"
        )

        when {
            pendingTaskState.title.isNullOrBlank() -> {
                Log.d("CREATE_STATE", "next=WAITING_FOR_TITLE")
                dialogState = CreateTaskDialogState.WAITING_FOR_TITLE
                promptHelper.askTitle()
            }

            pendingTaskState.dateText.isNullOrBlank() || selectedDate.isNullOrBlank() -> {
                Log.d("CREATE_STATE", "next=WAITING_FOR_DATE")
                dialogState = CreateTaskDialogState.WAITING_FOR_DATE
                promptHelper.askDate()
            }

            pendingTaskState.timeText.isNullOrBlank() || selectedTime.isNullOrBlank() -> {
                Log.d("CREATE_STATE", "next=WAITING_FOR_TIME")
                dialogState = CreateTaskDialogState.WAITING_FOR_TIME

                lifecycleScope.launch {
                    val semanticPhrase = pendingSemanticTimePhrase

                    if (!semanticPhrase.isNullOrBlank()) {
                        val learned = withContext(Dispatchers.IO) {
                            timePreferenceLearner.getLearnedTimeForPhrase(semanticPhrase)
                        }

                        if (learned != null && learned.usageCount >= 2 && isTimeAllowedByPendingConstraint(learned.resolvedTimeMinute())) {
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
                            promptHelper.askTime()
                        }
                    }
                }
            }

            else -> {
                Log.d("CREATE_STATE", "next=WAITING_FOR_SAVE_CONFIRMATION")
                dialogState = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
                promptHelper.askSaveTask(buildTaskSummary())
            }
        }
    }

    private fun String.resolvedTimeMinute(): Int? {
        val r = temporalResolver.resolve(null, this, this)
        return if (r.isExactTime) r.startMinuteInclusive else null
    }

    private fun isTimeAllowedByPendingConstraint(minute: Int?): Boolean {
        if (minute == null) return false
        val constraint = pendingTemporalConstraint ?: return true
        return TemporalActionPolicy.validateClarification(constraint, null, minute)
    }

    private fun shouldContinueConversation(): Boolean {
        return dialogState == CreateTaskDialogState.WAITING_FOR_TITLE ||
                dialogState == CreateTaskDialogState.WAITING_FOR_DATE ||
                dialogState == CreateTaskDialogState.WAITING_FOR_TIME ||
                dialogState == CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD ||
                dialogState == CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
    }

    private fun speakAndContinueListening(text: String) {
        val hint = when (dialogState) {
            CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION -> responseManager.hintYesNo()
            CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD -> responseManager.hintChangeFields()
            CreateTaskDialogState.WAITING_FOR_TITLE -> responseManager.hintTitle()
            CreateTaskDialogState.WAITING_FOR_DATE -> responseManager.hintDate()
            CreateTaskDialogState.WAITING_FOR_TIME -> responseManager.hintTime()
            else -> ""
        }

        assistantSession.getBottomSheet()?.showAssistantHint(hint)
        assistantSession.speak(
            text = text,
            listenAgain = shouldContinueConversation()
        )
    }

    private fun speakWithPanel(text: String) {
        assistantSession.getBottomSheet()?.clearHint()
        assistantSession.speak(text, listenAgain = false)
    }

    private fun speakThenListenAgain(text: String) {
        assistantSession.speakThenListenAgain(text)
    }

    private fun handleListenFailure(reply: String) {
        assistantSession.handleListenFailure(reply)
    }


    private fun speakThenFinish(text: String) {
        assistantSession.getBottomSheet()?.showAssistantHint(responseManager.followUpAnythingElse())
        assistantSession.speakThenRun(text) {
            finish()
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
        pendingTemporalConstraint = null

        dialogState = CreateTaskDialogState.IDLE

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
                value == "sure" ||
                value == "okay" ||
                value == "ok" ||
                value == "alright" ||
                value == "save" ||
                value == "okay yes" ||
                value == "ok yes"
    }

    private fun isNo(normalized: String): Boolean {
        val value = normalized.trim().lowercase()
        return value == "no" ||
                value == "no no" ||
                value == "nope" ||
                value == "no thanks" ||
                value == "no need" ||
                value == "don't save" ||
                value == "do not save" ||
                value == "not now"
    }



    override fun onAssistantFinalText(text: String) {
        handleVoiceCommand(text)
    }

    override fun onAssistantCancelled() {
        dialogState = CreateTaskDialogState.IDLE
        suggestedLearnedTime = null
        pendingSemanticTimePhrase = null
    }

    override fun onAssistantSessionStopped() {
        suggestedLearnedTime = null
        pendingSemanticTimePhrase = null
    }

    override fun onDestroy() {
        assistantSession.destroy()
        voiceHelper.shutdown()
        super.onDestroy()
    }

}