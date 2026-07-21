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
import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateDraftMove
import com.example.myapplication.voice.CreateDraftMoveInterpreter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

import com.example.myapplication.voice.CreateTaskDialogState

import android.util.Log
import com.example.myapplication.ai.TimePreferenceLearner
import com.example.myapplication.voice.AssistantResponseManager


import com.example.myapplication.voice.TextNormalizer

import com.example.myapplication.voice.AssistantPromptHelper

import com.example.myapplication.voice.AssistantVoiceHost
import com.example.myapplication.voice.AssistantVoiceSession

import com.example.myapplication.ai.temporal.TemporalActionPolicy
import com.example.myapplication.ai.temporal.TemporalExpressionResolver
import com.example.myapplication.ai.temporal.TemporalResolution
import com.example.myapplication.ai.temporal.TemporalResolutionStatus
import com.example.myapplication.ai.temporal.PendingTemporalClarification
import com.example.myapplication.ai.temporal.TemporalPolicyResult
import com.example.myapplication.ai.temporal.TemporalUseCase
import com.example.myapplication.ai.temporal.TemporalResolutionType
import com.example.myapplication.ai.conversation.ConversationAgentClient
import com.example.myapplication.ai.conversation.createdraft.CreateDraftAgentContext
import com.example.myapplication.ai.conversation.createdraft.CreateDraftFallbackReason
import com.example.myapplication.ai.conversation.createdraft.CreateDraftMoveResolution
import com.example.myapplication.ai.conversation.createdraft.CreateDraftSemanticOrchestrator
import kotlinx.coroutines.CancellationException

internal fun isCreateDraftFieldReplacement(
    field: CreateDraftField,
    state: CreateTaskDialogState,
    pendingReplacementField: CreateDraftField?,
    hasTitle: Boolean,
    hasSelectedDate: Boolean,
    hasSelectedTime: Boolean
): Boolean {
    if (pendingReplacementField == field) return true
    if (
        state == CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION ||
        state == CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD
    ) {
        return true
    }
    return when (field) {
        CreateDraftField.TITLE -> hasTitle
        CreateDraftField.DATE -> hasSelectedDate
        CreateDraftField.TIME -> hasSelectedTime
    }
}

internal enum class CreateDraftCandidateStatus {
    APPLIED,
    UNRESOLVED,
    REJECTED_BY_POLICY
}

class CreateTaskActivity : AppCompatActivity(), AssistantVoiceHost {
    private lateinit var promptHelper: AssistantPromptHelper

    private lateinit var assistantSession: AssistantVoiceSession

    private lateinit var responseManager: AssistantResponseManager

    private var suggestedLearnedTime: String? = null
    private lateinit var timePreferenceLearner: TimePreferenceLearner
    private var pendingSemanticTimePhrase: String? = null
    private val temporalResolver = TemporalExpressionResolver()
    private val createDraftMoveInterpreter = CreateDraftMoveInterpreter()
    private lateinit var createDraftSemanticOrchestrator: CreateDraftSemanticOrchestrator
    private var pendingTemporalConstraint: TemporalResolution? = null
    private var pendingTemporalClarification: PendingTemporalClarification? = null
    private var pendingReplacementField: CreateDraftField? = null
    private var isResolvingCreateDraftMove = false
    private var createDraftResolutionGeneration = 0L

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
        createDraftSemanticOrchestrator = CreateDraftSemanticOrchestrator(
            localInterpreter = createDraftMoveInterpreter,
            semanticClient = ConversationAgentClient(this)
        )

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
            handleCreateDraftMove(CreateDraftMove.Cancel)
        }

        btnGoHome.setOnClickListenerWithHaptic {
            hasConsumedPrefill = false
            assistantSession.speakThenRun(responseManager.returnHome()) {
                finish()
            }
        }

        btnTalkAssistant.setOnClickListenerWithHaptic {
            if (dialogState == CreateTaskDialogState.IDLE) {
                dialogState = CreateTaskDialogState.WAITING_FOR_TITLE
                promptHelper.askTitle()
            }
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
        val policy = TemporalActionPolicy.evaluate(resolution, TemporalUseCase.CREATE)
        if (policy is TemporalPolicyResult.Unresolved || policy is TemporalPolicyResult.InvalidPastSchedule) return
        val needsDate = policy is TemporalPolicyResult.NeedsExactDate || policy is TemporalPolicyResult.NeedsExactDateAndTime
        val needsTime = policy is TemporalPolicyResult.NeedsExactTime || policy is TemporalPolicyResult.NeedsExactDateAndTime
        if (needsDate || needsTime) {
            pendingTemporalConstraint = resolution
            pendingTemporalClarification = PendingTemporalClarification(resolution, needsExactDate = needsDate, needsExactTime = needsTime)
        }
        if (resolution.isExactDate && resolution.startDateInclusive != null) {
            applySpokenDate(resolution.startDateInclusive, replacingConstraint = true)
            pendingTaskState.dateText = dateText ?: resolution.startDateInclusive
        } else if (!dateText.isNullOrBlank()) {
            pendingTaskState.dateText = dateText
        }
        if (resolution.isExactTime && resolution.startMinuteInclusive != null) {
            applySpokenTime(timeText ?: resolution.spokenLabel, replacingConstraint = true)
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
        if (isResolvingCreateDraftMove) {
            Log.d("CREATE_MOVE_RESOLUTION", "ignored=true reason=request_in_progress")
            return
        }
        val normalized = TextNormalizer.normalize(rawCommand)
        // log
        Log.d(
            "CREATE_VOICE",
            "raw='$rawCommand' normalized='$normalized' dialogState=$dialogState title='${pendingTaskState.title}' date='${pendingTaskState.dateText}' time='${pendingTaskState.timeText}' selectedDate='$selectedDate' selectedTime='$selectedTime'"
        )

        val capturedState = dialogState
        val localResult = createDraftSemanticOrchestrator.resolveLocal(normalized, capturedState)
        val fallbackReason = fallbackReasonFor(localResult.move, capturedState)
        if (fallbackReason == null) {
            logCreateMoveResolution(capturedState, localResult)
            handleCreateDraftMove(localResult.move)
            return
        }

        requestCreateDraftFallback(normalized, capturedState, fallbackReason)
    }

    private fun fallbackReasonFor(
        move: CreateDraftMove,
        capturedState: CreateTaskDialogState
    ): CreateDraftFallbackReason? {
        if (capturedState == CreateTaskDialogState.READY_TO_SAVE) return null
        if (move == CreateDraftMove.Unknown) return CreateDraftFallbackReason.LOCAL_UNKNOWN
        if (move is CreateDraftMove.ProvideField &&
            (move.field == CreateDraftField.DATE || move.field == CreateDraftField.TIME)
        ) {
            val replacingField = pendingReplacementField == move.field
            if (classifyTemporalCandidate(move.field, move.value, replacingField) == CreateDraftCandidateStatus.UNRESOLVED) {
                return CreateDraftFallbackReason.TEMPORAL_UNRESOLVED
            }
        }
        if (move is CreateDraftMove.ApplyUnspecifiedCorrection &&
            isUnresolvedUnspecifiedCorrection(move.value)
        ) {
            return CreateDraftFallbackReason.UNSPECIFIED_CORRECTION_UNRESOLVED
        }
        return null
    }

    private fun classifyTemporalCandidate(
        field: CreateDraftField,
        value: String,
        replacingConstraint: Boolean
    ): CreateDraftCandidateStatus {
        val resolution = when (field) {
            CreateDraftField.DATE -> temporalResolver.resolve(value, null, value)
            CreateDraftField.TIME -> temporalResolver.resolve(null, value, value)
            CreateDraftField.TITLE -> return CreateDraftCandidateStatus.REJECTED_BY_POLICY
        }
        if (resolution.type == TemporalResolutionType.UNRESOLVED || resolution.type == TemporalResolutionType.NONE) {
            return CreateDraftCandidateStatus.UNRESOLVED
        }

        val exactDate = resolution.startDateInclusive.takeIf { resolution.isExactDate }
        val exactMinute = resolution.startMinuteInclusive.takeIf { resolution.isExactTime }
        if (exactDate == null && exactMinute == null) return CreateDraftCandidateStatus.REJECTED_BY_POLICY

        val constraint = if (replacingConstraint) {
            null
        } else {
            pendingTemporalClarification?.original ?: pendingTemporalConstraint
        }
        if (constraint != null && !TemporalActionPolicy.validateClarification(constraint, exactDate, exactMinute)) {
            return CreateDraftCandidateStatus.REJECTED_BY_POLICY
        }
        return CreateDraftCandidateStatus.APPLIED
    }

    private fun isUnresolvedUnspecifiedCorrection(value: String): Boolean {
        val dateStatus = classifyTemporalCandidate(CreateDraftField.DATE, value, replacingConstraint = true)
        val timeStatus = classifyTemporalCandidate(CreateDraftField.TIME, value, replacingConstraint = true)
        return dateStatus == CreateDraftCandidateStatus.UNRESOLVED &&
                timeStatus == CreateDraftCandidateStatus.UNRESOLVED &&
                !createDraftMoveInterpreter.isReasonableTitleCandidate(value)
    }

    private fun requestCreateDraftFallback(
        normalized: String,
        capturedState: CreateTaskDialogState,
        reason: CreateDraftFallbackReason
    ) {
        if (isResolvingCreateDraftMove) return
        isResolvingCreateDraftMove = true
        createDraftResolutionGeneration += 1
        val requestGeneration = createDraftResolutionGeneration
        val context = CreateDraftAgentContext.capture(
            state = capturedState,
            pendingReplacementField = pendingReplacementField,
            hasTitle = !pendingTaskState.title.isNullOrBlank() || etTaskTitle.text.toString().isNotBlank(),
            hasSelectedDate = !selectedDate.isNullOrBlank(),
            hasSelectedTime = !selectedTime.isNullOrBlank()
        )

        assistantSession.pauseListeningForAssistantSpeech()
        assistantSession.getBottomSheet()?.setProcessingState()

        lifecycleScope.launch {
            try {
                val result = createDraftSemanticOrchestrator.resolve(
                    userText = normalized,
                    state = capturedState,
                    context = context,
                    fallbackReason = reason
                )
                if (requestGeneration != createDraftResolutionGeneration || dialogState != capturedState) {
                    Log.d(
                        "CREATE_MOVE_FALLBACK",
                        "reason=$reason state=$capturedState category=STALE_RESULT_DISCARDED"
                    )
                    return@launch
                }
                logCreateMoveResolution(capturedState, result)
                handleCreateDraftMove(result.move)
            } catch (e: CancellationException) {
                throw e
            } finally {
                if (requestGeneration == createDraftResolutionGeneration) {
                    isResolvingCreateDraftMove = false
                }
            }
        }
    }

    private fun logCreateMoveResolution(
        capturedState: CreateTaskDialogState,
        result: CreateDraftMoveResolution
    ) {
        val field = when (val move = result.move) {
            is CreateDraftMove.ChangeField -> move.field.name
            is CreateDraftMove.ProvideField -> move.field.name
            else -> "none"
        }
        Log.d(
            "CREATE_MOVE_RESOLUTION",
            "state=$capturedState source=${result.source.logValue} move=${result.move::class.java.simpleName} " +
                    "field=$field confidence=${result.confidence} fallbackAttempted=${result.fallbackAttempted}"
        )
    }

    private fun openDatePicker() {
        val calendar = Calendar.getInstance()
        val year = selectedYear ?: calendar.get(Calendar.YEAR)
        val month = selectedMonth ?: calendar.get(Calendar.MONTH)
        val day = selectedDay ?: calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, pickedYear, pickedMonth, pickedDay ->
                val pickedDate = formatDate(pickedYear, pickedMonth, pickedDay)
                assistantSession.pauseListeningForAssistantSpeech()
                if (acceptExactDate(pickedDate, replacingConstraint = false)) {
                    pendingTaskState.dateText = selectedDate
                    voiceHelper.speak(responseManager.dateSelected(selectedDate ?: ""))
                    moveToNextMissingStep()
                } else {
                    voiceHelper.speak(invalidTemporalDateMessage())
                }
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
                val pickedMinuteOfDay = pickedHour * 60 + pickedMinute
                assistantSession.pauseListeningForAssistantSpeech()
                if (acceptExactMinute(pickedMinuteOfDay, replacingConstraint = false)) {
                    pendingTaskState.timeText = selectedTime
                    pendingSemanticTimePhrase = null
                    suggestedLearnedTime = null
                    voiceHelper.speak(responseManager.timeSelected(selectedTime ?: ""))
                    moveToNextMissingStep()
                } else {
                    voiceHelper.speak(invalidTemporalTimeMessage())
                }
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

        val finalResolution = temporalResolver.resolve(selectedDate, selectedTime, listOfNotNull(selectedDate, selectedTime).joinToString(" "))
        if (TemporalActionPolicy.evaluate(finalResolution, TemporalUseCase.CREATE) is TemporalPolicyResult.InvalidPastSchedule) {
            speakWithPanel(responseManager.pastDateTime())
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

    private fun applySpokenDate(dateText: String, replacingConstraint: Boolean = false): Boolean {
        val resolution = temporalResolver.resolve(dateText, null, dateText)
        if (!resolution.isExactDate || resolution.startDateInclusive == null) return false
        return acceptExactDate(resolution.startDateInclusive, replacingConstraint)
    }

    private fun acceptExactDate(date: String, replacingConstraint: Boolean): Boolean {
        val constraint = if (replacingConstraint) null else pendingTemporalClarification?.original ?: pendingTemporalConstraint
        if (constraint != null && !TemporalActionPolicy.validateClarification(constraint, date, null)) return false
        selectedDate = date
        val parts = selectedDate!!.split("/")
        selectedDay = parts.getOrNull(0)?.toIntOrNull()
        selectedMonth = parts.getOrNull(1)?.toIntOrNull()?.minus(1)
        selectedYear = parts.getOrNull(2)?.toIntOrNull()
        tvSelectedDate.text = "Selected date: $selectedDate"
        pendingTemporalClarification = pendingTemporalClarification?.copy(exactDate = date)
        advanceTemporalClarification()
        return true
    }

    private fun applySpokenTime(timeText: String, replacingConstraint: Boolean = false): Boolean {
        val resolution = temporalResolver.resolve(null, timeText, timeText)
        if (!resolution.isExactTime || resolution.startMinuteInclusive == null) return false
        return acceptExactMinute(resolution.startMinuteInclusive, replacingConstraint)
    }

    private fun acceptExactMinute(minute: Int, replacingConstraint: Boolean): Boolean {
        val constraint = if (replacingConstraint) null else pendingTemporalClarification?.original ?: pendingTemporalConstraint
        if (constraint != null && !TemporalActionPolicy.validateClarification(constraint, null, minute)) return false
        selectedHour24 = minute / 60
        selectedMinute = minute % 60
        selectedTime = formatTime(selectedHour24!!, selectedMinute!!)
        tvSelectedTime.text = "Selected time: $selectedTime"
        pendingTemporalClarification = pendingTemporalClarification?.copy(exactMinute = minute)
        advanceTemporalClarification()
        return true
    }

    private fun advanceTemporalClarification() {
        val pending = pendingTemporalClarification ?: return
        if (pending.isComplete) {
            pendingTemporalClarification = null
            pendingTemporalConstraint = null
        }
    }

    private fun handleCreateDraftMove(move: CreateDraftMove): Boolean {
        logCreateDraftMove(move)
        return when (move) {
            CreateDraftMove.ConfirmSave -> {
                dialogState = CreateTaskDialogState.READY_TO_SAVE
                saveTask()
                true
            }

            CreateDraftMove.RejectSave -> {
                pendingReplacementField = null
                dialogState = CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD
                speakAndContinueListening(responseManager.askWhatToChange())
                true
            }

            is CreateDraftMove.ChangeField -> {
                handleFieldChange(move.field, move.value)
                true
            }

            is CreateDraftMove.ProvideField -> {
                handleProvidedField(move.field, move.value)
                true
            }

            is CreateDraftMove.ApplyUnspecifiedCorrection -> {
                applyUnspecifiedCorrection(move.value)
                true
            }

            CreateDraftMove.Cancel -> {
                cancelCreateDraft()
                true
            }

            CreateDraftMove.RequestHelp -> {
                provideCreateDraftHelp()
                true
            }

            CreateDraftMove.Unknown -> {
                recoverFromUnknownMove()
                true
            }
        }
    }

    private fun handleFieldChange(field: CreateDraftField, value: String?) {
        val replacingField = isCreateDraftFieldReplacement(
            field = field,
            state = dialogState,
            pendingReplacementField = pendingReplacementField,
            hasTitle = !pendingTaskState.title.isNullOrBlank() || etTaskTitle.text.toString().isNotBlank(),
            hasSelectedDate = !selectedDate.isNullOrBlank(),
            hasSelectedTime = !selectedTime.isNullOrBlank()
        )
        pendingReplacementField = if (replacingField) field else null

        if (value.isNullOrBlank()) {
            when (field) {
                CreateDraftField.TITLE -> {
                    dialogState = CreateTaskDialogState.WAITING_FOR_TITLE
                    promptHelper.speakInfo(responseManager.askChangeTitle(), true, responseManager.hintTitle())
                }

                CreateDraftField.DATE -> {
                    dialogState = CreateTaskDialogState.WAITING_FOR_DATE
                    promptHelper.speakInfo(responseManager.askChangeDate(), true, responseManager.hintDate())
                }

                CreateDraftField.TIME -> {
                    dialogState = CreateTaskDialogState.WAITING_FOR_TIME
                    promptHelper.speakInfo(responseManager.askChangeTime(), true, responseManager.hintTime())
                }
            }
            return
        }

        handleProvidedField(field, value)
    }

    private fun handleProvidedField(field: CreateDraftField, value: String) {
        val replacingField = pendingReplacementField == field
        when (field) {
            CreateDraftField.TITLE -> applyProvidedTitle(value, replacingField)
            CreateDraftField.DATE -> applyProvidedDate(value, replacingField)
            CreateDraftField.TIME -> applyProvidedTime(value, replacingField)
        }
    }

    private fun applyProvidedTitle(value: String, replacingField: Boolean) {
        if (!createDraftMoveInterpreter.isReasonableTitleCandidate(value)) {
            dialogState = CreateTaskDialogState.WAITING_FOR_TITLE
            val response = if (replacingField) responseManager.askChangeTitle() else responseManager.askTaskTitle()
            speakAndContinueListening(response)
            return
        }

        applyTitle(value)
        if (replacingField) {
            returnToSaveConfirmation(CreateDraftField.TITLE)
        } else {
            moveToNextMissingStep()
        }
    }

    private fun applyProvidedDate(value: String, replacingField: Boolean) {
        dialogState = CreateTaskDialogState.WAITING_FOR_DATE
        val applied = applySpokenDate(value, replacingConstraint = replacingField)
        logTemporalFollowUp(value, applied)
        if (!applied) {
            speakAndContinueListening(invalidTemporalDateMessage())
            return
        }

        pendingTaskState.dateText = value
        if (replacingField) {
            returnToSaveConfirmation(CreateDraftField.DATE)
        } else {
            assistantSession.pauseListeningForAssistantSpeech()
            voiceHelper.speak(responseManager.dateSet(selectedDate ?: ""))
            moveToNextMissingStep()
        }
    }

    private fun applyProvidedTime(value: String, replacingField: Boolean) {
        dialogState = CreateTaskDialogState.WAITING_FOR_TIME
        val acceptedLearnedSuggestion =
            !replacingField &&
                    !suggestedLearnedTime.isNullOrBlank() &&
                    createDraftMoveInterpreter.isConfirmationUtterance(value)
        val timeCandidate = if (acceptedLearnedSuggestion) suggestedLearnedTime!! else value
        val applied = applySpokenTime(timeCandidate, replacingConstraint = replacingField)
        logTemporalFollowUp(value, applied)
        if (!applied) {
            speakAndContinueListening(invalidTemporalTimeMessage())
            return
        }

        pendingTaskState.timeText = timeCandidate
        if (replacingField) {
            pendingSemanticTimePhrase = null
            suggestedLearnedTime = null
            returnToSaveConfirmation(CreateDraftField.TIME)
            return
        }

        val resolvedTime = selectedTime
        val semanticPhraseToLearn = pendingSemanticTimePhrase
        if (!acceptedLearnedSuggestion && !semanticPhraseToLearn.isNullOrBlank() && !resolvedTime.isNullOrBlank()) {
            lifecycleScope.launch(Dispatchers.IO) {
                timePreferenceLearner.learnPreference(semanticPhraseToLearn, resolvedTime)
            }
        }

        pendingSemanticTimePhrase = null
        suggestedLearnedTime = null
        assistantSession.pauseListeningForAssistantSpeech()
        voiceHelper.speak(responseManager.timeSet(selectedTime ?: ""))
        moveToNextMissingStep()
    }

    private fun applyUnspecifiedCorrection(value: String) {
        if (value.isBlank()) {
            speakAndContinueListening(responseManager.correctionNotUnderstood())
            return
        }

        if (applySpokenDate(value, replacingConstraint = true)) {
            pendingTaskState.dateText = value
            returnToSaveConfirmation(CreateDraftField.DATE)
            return
        }

        if (applySpokenTime(value, replacingConstraint = true)) {
            pendingTaskState.timeText = value
            returnToSaveConfirmation(CreateDraftField.TIME)
            return
        }

        if (createDraftMoveInterpreter.isReasonableTitleCandidate(value)) {
            applyTitle(value)
            returnToSaveConfirmation(CreateDraftField.TITLE)
            return
        }

        dialogState = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
        speakAndContinueListening(responseManager.correctionNotUnderstood())
    }

    private fun returnToSaveConfirmation(field: CreateDraftField) {
        pendingReplacementField = null
        if (!isDraftCompleteForSaveConfirmation()) {
            moveToNextMissingStep()
            return
        }
        dialogState = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
        val summary = buildTaskSummary()
        val response = when (field) {
            CreateDraftField.TITLE -> responseManager.inlineTitleUpdated(summary)
            CreateDraftField.DATE -> responseManager.inlineDateUpdated(summary)
            CreateDraftField.TIME -> responseManager.inlineTimeUpdated(summary)
        }
        speakAndContinueListening(response)
    }

    private fun isDraftCompleteForSaveConfirmation(): Boolean {
        val title = pendingTaskState.title ?: etTaskTitle.text.toString().trim()
        return title.isNotBlank() && !selectedDate.isNullOrBlank() && !selectedTime.isNullOrBlank()
    }

    private fun cancelCreateDraft() {
        resetTaskDraftState()
        hasConsumedPrefill = false
        assistantSession.speakThenRun(responseManager.cancelCreate()) {
            finish()
        }
    }

    private fun provideCreateDraftHelp() {
        val response = when (dialogState) {
            CreateTaskDialogState.IDLE -> responseManager.askTaskTitle()
            CreateTaskDialogState.WAITING_FOR_TITLE -> if (pendingReplacementField == CreateDraftField.TITLE) {
                responseManager.askChangeTitle()
            } else {
                responseManager.askTaskTitle()
            }
            CreateTaskDialogState.WAITING_FOR_DATE -> if (pendingReplacementField == CreateDraftField.DATE) {
                responseManager.askChangeDate()
            } else {
                responseManager.askTaskDate()
            }
            CreateTaskDialogState.WAITING_FOR_TIME -> if (pendingReplacementField == CreateDraftField.TIME) {
                responseManager.askChangeTime()
            } else {
                responseManager.askTaskTime()
            }
            CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD -> responseManager.askWhatToChange()
            CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION -> responseManager.saveConfirmationHelp()
            CreateTaskDialogState.READY_TO_SAVE -> responseManager.correctionNotUnderstood()
        }
        speakAndContinueListening(response)
    }

    private fun recoverFromUnknownMove() {
        val response = when (dialogState) {
            CreateTaskDialogState.IDLE -> responseManager.askTaskTitle()
            CreateTaskDialogState.WAITING_FOR_TITLE -> if (pendingReplacementField == CreateDraftField.TITLE) {
                responseManager.askChangeTitle()
            } else {
                responseManager.askTaskTitle()
            }
            CreateTaskDialogState.WAITING_FOR_DATE -> responseManager.invalidDate()
            CreateTaskDialogState.WAITING_FOR_TIME -> responseManager.invalidTime()
            CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD -> responseManager.askWhatToChange()
            CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION -> responseManager.saveConfirmationHelp()
            CreateTaskDialogState.READY_TO_SAVE -> responseManager.correctionNotUnderstood()
        }
        if (shouldContinueConversation()) {
            speakAndContinueListening(response)
        } else {
            speakWithPanel(response)
        }
    }

    private fun logCreateDraftMove(move: CreateDraftMove) {
        val field = when (move) {
            is CreateDraftMove.ChangeField -> move.field.name
            is CreateDraftMove.ProvideField -> move.field.name
            else -> "none"
        }
        val valueSupplied = when (move) {
            is CreateDraftMove.ChangeField -> !move.value.isNullOrBlank()
            is CreateDraftMove.ProvideField -> move.value.isNotBlank()
            is CreateDraftMove.ApplyUnspecifiedCorrection -> move.value.isNotBlank()
            else -> false
        }
        val moveType = move::class.java.simpleName
        Log.d(
            "CREATE_MOVE",
            "dialogState=$dialogState move=$moveType field=$field valueSupplied=$valueSupplied"
        )
    }

    private fun logTemporalFollowUp(raw: String, validationResult: Boolean) {
        val resolution = when (dialogState) {
            CreateTaskDialogState.WAITING_FOR_DATE -> temporalResolver.resolve(raw, null, raw)
            CreateTaskDialogState.WAITING_FOR_TIME -> temporalResolver.resolve(null, raw, raw)
            else -> temporalResolver.resolve(null, null, raw)
        }
        Log.d(
            "TEMPORAL_FOLLOWUP",
            "raw='$raw' dialogState=$dialogState type=${resolution.type} date=${resolution.startDateInclusive} minute=${resolution.startMinuteInclusive} valid=$validationResult"
        )
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

                        if (learned != null && learned.usageCount >= 2 && isTimeAllowedByPendingConstraint(learned.resolvedTime.resolvedTimeMinute())) {
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
        invalidateCreateDraftResolution()
        pendingTaskState.clear()

        selectedTime = null
        selectedHour24 = null
        selectedMinute = null
        selectedDate = null
        selectedMonth = null
        selectedYear = null
        selectedDay = null
        pendingTemporalConstraint = null
        pendingTemporalClarification = null
        pendingReplacementField = null

        dialogState = CreateTaskDialogState.IDLE

        etTaskTitle.setText("")
        tvSelectedDate.text = "Selected date: No date selected"
        tvSelectedTime.text = "Selected time: No time selected"
    }

    private fun invalidateCreateDraftResolution() {
        createDraftResolutionGeneration += 1
        isResolvingCreateDraftMove = false
    }

    private fun clearPrefillExtras() {
        intent.removeExtra("prefill_title")
        intent.removeExtra("prefill_date_text")
        intent.removeExtra("prefill_time_text")
    }

    override fun onAssistantFinalText(text: String) {
        handleVoiceCommand(text)
    }

    override fun onAssistantCancelled() {
        invalidateCreateDraftResolution()
        dialogState = CreateTaskDialogState.IDLE
        suggestedLearnedTime = null
        pendingSemanticTimePhrase = null
    }

    override fun onAssistantSessionStopped() {
        invalidateCreateDraftResolution()
        suggestedLearnedTime = null
        pendingSemanticTimePhrase = null
    }

    override fun onDestroy() {
        invalidateCreateDraftResolution()
        assistantSession.destroy()
        voiceHelper.shutdown()
        super.onDestroy()
    }

}
