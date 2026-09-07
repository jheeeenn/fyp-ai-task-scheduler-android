package com.example.myapplication

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.example.myapplication.accessibility.AccessibilityActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.TaskEntity
import com.example.myapplication.voice.PendingTaskState
import com.example.myapplication.voice.CreateDraftField
import com.example.myapplication.voice.CreateDraftMove
import com.example.myapplication.voice.CreateDraftMoveInterpreter
import com.example.myapplication.voice.CreateDraftResumePolicy
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
import com.example.myapplication.ai.conversation.ConversationAgentClient
import com.example.myapplication.ai.conversation.createdraft.CreateDraftAgentContext
import com.example.myapplication.ai.conversation.createdraft.CreateDraftMoveResolution
import com.example.myapplication.ai.conversation.createdraft.CreateDraftReadResponseRenderer
import com.example.myapplication.ai.conversation.createdraft.CreateDraftSemanticOrchestrator
import kotlinx.coroutines.CancellationException
import com.example.myapplication.accessibility.AccessibleAssistantInputDialog
import com.example.myapplication.accessibility.AccessibilityStateHelper
import com.example.myapplication.accessibility.TaskCardAccessibilitySemantics
import com.example.myapplication.accessibility.AssistantAccessibilityState
import com.example.myapplication.accessibility.AccessibilityAnnouncementHelper

internal const val EXTRA_CREATE_TASK_ASSISTANT_HANDOFF = "assistant_handoff"

internal fun shouldContinueIncomingCreateDraft(
    hasPrefill: Boolean,
    assistantHandoff: Boolean
): Boolean = hasPrefill || assistantHandoff

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

internal enum class CreateDraftReplacementPickerRoute {
    NORMAL_DRAFT,
    ACCEPT_PENDING_REPLACEMENT,
    KEEP_PENDING_REPLACEMENT
}

internal fun createDraftReplacementPickerRoute(
    replacementClarificationActive: Boolean,
    pendingReplacementField: CreateDraftField?,
    pickedField: CreateDraftField
): CreateDraftReplacementPickerRoute {
    if (!replacementClarificationActive) {
        return CreateDraftReplacementPickerRoute.NORMAL_DRAFT
    }
    return if (pendingReplacementField == pickedField) {
        CreateDraftReplacementPickerRoute.ACCEPT_PENDING_REPLACEMENT
    } else {
        CreateDraftReplacementPickerRoute.KEEP_PENDING_REPLACEMENT
    }
}

class CreateTaskActivity : AccessibilityActivity(), AssistantVoiceHost {
    private lateinit var promptHelper: AssistantPromptHelper

    private lateinit var assistantSession: AssistantVoiceSession
    private var developerAssistantOverlay: DeveloperAssistantOverlay? = null

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
    private var createDraftRevision = 0L
    private var isSavingTask = false
    private var isCreateTaskExitPending = false

    private var hasConsumedPrefill = false
    private val incomingPrefillRunnable = Runnable {
        if (canRunCreateAssistantCallback()) {
            applyIncomingPrefill()
        }
    }



    private var dialogState = CreateTaskDialogState.IDLE
    private val pendingTaskState = PendingTaskState()

    private lateinit var voiceHelper: VoiceHelper

    private lateinit var etTaskTitle: EditText
    private lateinit var tvSelectedDate: TextView
    private lateinit var tvSelectedTime: TextView
    private lateinit var btnSaveTask: Button
    private lateinit var btnTalkAssistant: Button
    private lateinit var dateInfoGroup: View
    private lateinit var timeInfoGroup: View

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
        AccessibilityStateHelper.markHeading(findViewById(R.id.tvCreateTitle))

        etTaskTitle = findViewById(R.id.etTaskTitle)


        btnSaveTask = findViewById(R.id.btnSaveTask)
        val btnCancelTask = findViewById<Button>(R.id.btnCancelTask)

        tvSelectedTime = findViewById(R.id.tvSelectedTime)
        timeInfoGroup = findViewById(R.id.timeInfoGroup)

        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        dateInfoGroup = findViewById(R.id.dateInfoGroup)

        val btnGoHome = findViewById<Button>(R.id.btnGoHome)
        btnTalkAssistant = findViewById(R.id.btnTalkAssistant)

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
            audioPermissionLauncher = audioPermissionLauncher,
            onAccessibilityStateChanged = { state ->
                AccessibilityStateHelper.updateAssistantState(btnTalkAssistant, state, announce = false)
                DeveloperTestSession.updateAssistantState(state)
            },
            interactionMode = DeveloperTestSession.interactionMode(),
            shouldSpeakAudio = DeveloperTestSession::shouldSpeakAudio,
            transcriptObserver = DeveloperTestSession::recordTranscript
        )
        assistantSession.bindAssistantControl(btnTalkAssistant)
        AccessibilityStateHelper.updateAssistantState(
            btnTalkAssistant,
            AssistantAccessibilityState.READY,
            announce = false
        )

        promptHelper = AssistantPromptHelper(assistantSession, responseManager)

        resetTaskDraftState()

        VoiceFirstGestureBinder.bindAction(
            view = dateInfoGroup,
            speechProvider = { TaskFormControlSpeechRenderer.date(selectedDate) },
            speak = ::speakControlIdentification,
            activate = ::openDatePicker
        )

        VoiceFirstGestureBinder.bindAction(
            view = timeInfoGroup,
            speechProvider = { TaskFormControlSpeechRenderer.time(selectedTime) },
            speak = ::speakControlIdentification,
            activate = ::openTimePicker
        )

        VoiceFirstGestureBinder.bindAction(
            view = btnSaveTask,
            speechProvider = TaskFormControlSpeechRenderer::saveTask,
            speak = ::speakControlIdentification,
            activate = ::saveTask
        )

        VoiceFirstGestureBinder.bindAction(
            view = btnCancelTask,
            speechProvider = TaskFormControlSpeechRenderer::cancel,
            speak = ::speakControlIdentification,
            activate = {
                if (!isSavingTask) {
                    invalidateCreateDraftResolution()
                    handleCreateDraftMove(CreateDraftMove.Cancel)
                }
            }
        )

        VoiceFirstGestureBinder.bindAction(
            view = btnGoHome,
            speechProvider = TaskFormControlSpeechRenderer::home,
            speak = ::speakControlIdentification,
            activate = {
                if (!isSavingTask) {
                    invalidateCreateDraftResolution()
                    isCreateTaskExitPending = true
                    setCreateDraftControlsEnabled(false)
                    hasConsumedPrefill = false
                    assistantSession.speakThenRun(responseManager.returnHome()) {
                        finish()
                    }
                }
            }
        )

        VoiceFirstGestureBinder.bindAction(
            view = btnTalkAssistant,
            speechProvider = TaskFormControlSpeechRenderer::assistant,
            speak = ::speakControlIdentification,
            activate = {
                if (dialogState == CreateTaskDialogState.IDLE) {
                    assistantSession.startSession()
                    resumeCreateAssistantFromDraft()
                } else {
                    assistantSession.startSession()
                }
            }
        )
        btnTalkAssistant.setOnLongClickListener {
            btnTalkAssistant.performLongClickHapticFeedback()
            showTypedAssistantInputDialog()
            true
        }
        AccessibilityStateHelper.exposeTypedInputAction(btnTalkAssistant)

        developerAssistantOverlay = DeveloperAssistantOverlay.attach(
            activity = this,
            onSubmit = { text ->
                assistantSession.submitTypedText(text, clearConversation = false)
            }
        )

    } // end of onCreate()

    //function definitions
    private fun resumeCreateAssistantFromDraft() {
        val nativeTitle = etTaskTitle.text.toString().trim()
        val title = nativeTitle.ifBlank { pendingTaskState.title.orEmpty().trim() }
        if (title.isNotBlank()) {
            pendingTaskState.title = title
            if (nativeTitle.isBlank()) etTaskTitle.setText(title)
        }
        if (!selectedDate.isNullOrBlank() && pendingTaskState.dateText.isNullOrBlank()) {
            pendingTaskState.dateText = selectedDate
        }
        if (!selectedTime.isNullOrBlank() && pendingTaskState.timeText.isNullOrBlank()) {
            pendingTaskState.timeText = selectedTime
        }

        val hasTitle = title.isNotBlank()
        val hasDate = !selectedDate.isNullOrBlank()
        val hasTime = !selectedTime.isNullOrBlank()
        dialogState = CreateDraftResumePolicy.nextState(hasTitle, hasDate, hasTime)
        pendingReplacementField = null
        Log.d(
            "CREATE_RESUME",
            "hasTitle=$hasTitle hasDate=$hasDate hasTime=$hasTime next=${dialogState.name}"
        )
        when (dialogState) {
            CreateTaskDialogState.WAITING_FOR_TITLE -> promptHelper.askTitle()
            CreateTaskDialogState.WAITING_FOR_DATE -> promptHelper.askDate()
            CreateTaskDialogState.WAITING_FOR_TIME -> promptHelper.askTime()
            CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION -> {
                assistantSession.expectConfirmation()
                promptHelper.askSaveTask(buildTaskSummary())
            }
            else -> Unit
        }
    }

    private fun applyIncomingPrefill() {
        if (hasConsumedPrefill) return

        val prefillTitle = intent.getStringExtra("prefill_title")
        val prefillDateText = intent.getStringExtra("prefill_date_text")
        val prefillTimeText = intent.getStringExtra("prefill_time_text")
        val assistantHandoff = intent.getBooleanExtra(EXTRA_CREATE_TASK_ASSISTANT_HANDOFF, false)

        val hasPrefill =
            !prefillTitle.isNullOrBlank() ||
                    !prefillDateText.isNullOrBlank() ||
                    !prefillTimeText.isNullOrBlank()

        if (!shouldContinueIncomingCreateDraft(hasPrefill, assistantHandoff)) {
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
        if (BuildConfig.DEBUG) {
            Log.d(
                "CREATE_VOICE",
                "raw='$rawCommand' normalized='$normalized' dialogState=$dialogState title='${pendingTaskState.title}' date='${pendingTaskState.dateText}' time='${pendingTaskState.timeText}' selectedDate='$selectedDate' selectedTime='$selectedTime'"
            )
        }

        val capturedState = dialogState
        val localCandidate = createDraftSemanticOrchestrator.proposeLocal(normalized, capturedState)
        val immediateResult = createDraftSemanticOrchestrator.resolveImmediate(localCandidate, capturedState)
        if (immediateResult != null) {
            logCreateMoveResolution(capturedState, immediateResult)
            handleCreateDraftMove(immediateResult.move)
            return
        }

        requestCreateDraftPrimary(normalized, capturedState, localCandidate)
    }

    private fun requestCreateDraftPrimary(
        userText: String,
        capturedState: CreateTaskDialogState,
        localCandidate: CreateDraftMove
    ) {
        if (isResolvingCreateDraftMove) return
        isResolvingCreateDraftMove = true
        createDraftResolutionGeneration += 1
        val requestGeneration = createDraftResolutionGeneration
        val requestDraftRevision = createDraftRevision
        val context = CreateDraftAgentContext.capture(
            state = capturedState,
            pendingReplacementField = pendingReplacementField,
            hasTitle = !pendingTaskState.title.isNullOrBlank() || etTaskTitle.text.toString().isNotBlank(),
            hasSelectedDate = !selectedDate.isNullOrBlank(),
            hasSelectedTime = !selectedTime.isNullOrBlank(),
            localCandidate = localCandidate
        )

        assistantSession.pauseListeningForAssistantSpeech()
        assistantSession.getBottomSheet()?.setProcessingState()
        setCreateDraftControlsEnabled(false)

        lifecycleScope.launch {
            try {
                val result = createDraftSemanticOrchestrator.resolve(
                    userText = userText,
                    state = capturedState,
                    context = context,
                    localCandidate = localCandidate
                )
                if (requestGeneration != createDraftResolutionGeneration ||
                    dialogState != capturedState ||
                    requestDraftRevision != createDraftRevision
                ) {
                    Log.d(
                        "CREATE_MOVE_PRIMARY",
                        "state=$capturedState category=STALE_DRAFT_RESULT_DISCARDED"
                    )
                    return@launch
                }
                isResolvingCreateDraftMove = false
                logCreateMoveResolution(capturedState, result)
                handleCreateDraftMove(result.move)
            } catch (e: CancellationException) {
                throw e
            } finally {
                if (requestGeneration == createDraftResolutionGeneration) {
                    isResolvingCreateDraftMove = false
                    setCreateDraftControlsEnabled(true)
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
            is CreateDraftMove.ProvideSchedule -> "SCHEDULE"
            is CreateDraftMove.ReadDraft -> move.target.name
            else -> "none"
        }
        Log.d(
            "CREATE_MOVE_RESOLUTION",
            "state=$capturedState source=${result.source.logValue} move=${result.move::class.java.simpleName} " +
                    "field=$field confidence=${result.confidence} agentAttempted=${result.agentAttempted}"
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
                if (handleReplacementSchedulePickerSelection(
                        field = CreateDraftField.DATE,
                        exactDate = pickedDate
                    )
                ) {
                    Unit
                } else if (acceptExactDate(pickedDate, replacingConstraint = false)) {
                    pendingTaskState.dateText = selectedDate
                    if (AccessibilityStateHelper.isScreenReaderActive(dateInfoGroup)) {
                        AccessibilityAnnouncementHelper.announce(
                            dateInfoGroup,
                            screen = "CREATE_TASK",
                            event = "DATE_UPDATED",
                            message = TaskCardAccessibilitySemantics.spokenDate(selectedDate)
                        )
                        dateInfoGroup.postDelayed({ moveToNextMissingStep() }, 1200)
                    } else {
                        voiceHelper.speak(responseManager.dateSelected(selectedDate ?: ""))
                        moveToNextMissingStep()
                    }
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
                if (handleReplacementSchedulePickerSelection(
                        field = CreateDraftField.TIME,
                        exactMinute = pickedMinuteOfDay
                    )
                ) {
                    Unit
                } else if (acceptExactMinute(pickedMinuteOfDay, replacingConstraint = false)) {
                    pendingTaskState.timeText = selectedTime
                    pendingSemanticTimePhrase = null
                    suggestedLearnedTime = null
                    if (AccessibilityStateHelper.isScreenReaderActive(timeInfoGroup)) {
                        AccessibilityAnnouncementHelper.announce(
                            timeInfoGroup,
                            screen = "CREATE_TASK",
                            event = "TIME_UPDATED",
                            message = TaskCardAccessibilitySemantics.spokenTime(selectedTime)
                        )
                        timeInfoGroup.postDelayed({ moveToNextMissingStep() }, 1200)
                    } else {
                        voiceHelper.speak(responseManager.timeSelected(selectedTime ?: ""))
                        moveToNextMissingStep()
                    }
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
        if (isSavingTask || isResolvingCreateDraftMove) return

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

        val finalTitle = title
        val finalDate = selectedDate!!
        val finalTime = selectedTime!!
        val taskToInsert = TaskEntity(
            title = finalTitle,
            dueDate = finalDate,
            dueTime = finalTime
        )

        isSavingTask = true
        setCreateDraftControlsEnabled(false)
        lifecycleScope.launch {
            val insertedId = try {
                withContext(Dispatchers.IO) {
                    dao.insert(taskToInsert)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CREATE_SAVE", "category=INSERT_FAILED", e)
                if (!isFinishing && !isDestroyed) {
                    isSavingTask = false
                    setCreateDraftControlsEnabled(true)
                    Toast.makeText(
                        this@CreateTaskActivity,
                        "Task could not be saved. Please try again.",
                        Toast.LENGTH_LONG
                    ).show()
                    speakWithPanel("The task could not be saved. Please try again.")
                }
                return@launch
            }

            val insertedTask = taskToInsert.copy(id = insertedId)
            val scheduled = ReminderHelper.scheduleReminderFromTask(
                this@CreateTaskActivity,
                insertedTask
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
        renderSelectedDate()
        updateDateAccessibilityState()
        pendingTemporalClarification = pendingTemporalClarification?.copy(exactDate = date)
        advanceTemporalClarification()
        markCreateDraftChanged()
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
        renderSelectedTime()
        updateTimeAccessibilityState()
        pendingTemporalClarification = pendingTemporalClarification?.copy(exactMinute = minute)
        advanceTemporalClarification()
        markCreateDraftChanged()
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

            is CreateDraftMove.ProvideSchedule -> {
                handleProvidedSchedule(move.dateText, move.timeText)
                true
            }

            is CreateDraftMove.ApplyUnspecifiedCorrection -> {
                applyUnspecifiedCorrection(move.value)
                true
            }

            is CreateDraftMove.ReadDraft -> {
                readCurrentDraft(move)
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
                    promptHelper.speakInfo(responseManager.askChangeTitle(), true)
                }

                CreateDraftField.DATE -> {
                    dialogState = CreateTaskDialogState.WAITING_FOR_DATE
                    promptHelper.speakInfo(responseManager.askChangeDate(), true)
                }

                CreateDraftField.TIME -> {
                    dialogState = CreateTaskDialogState.WAITING_FOR_TIME
                    promptHelper.speakInfo(responseManager.askChangeTime(), true)
                }
            }
            return
        }

        handleProvidedField(field, value)
    }

    private fun handleProvidedField(field: CreateDraftField, value: String) {
        if (handlePendingReplacementScheduleClarification(field, value)) return
        val replacingField = pendingReplacementField == field
        when (field) {
            CreateDraftField.TITLE -> applyProvidedTitle(value, replacingField)
            CreateDraftField.DATE -> applyProvidedDate(value, replacingField)
            CreateDraftField.TIME -> applyProvidedTime(value, replacingField)
        }
    }

    private fun handleProvidedSchedule(dateText: String, timeText: String) {
        val priorState = dialogState
        val replacingSchedule =
            priorState == CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION ||
                pendingReplacementField == CreateDraftField.DATE ||
                pendingReplacementField == CreateDraftField.TIME
        val originalText = listOf(dateText, timeText).joinToString(" ")
        val resolution = temporalResolver.resolve(dateText, timeText, originalText)
        when (val policy = TemporalActionPolicy.evaluate(resolution, TemporalUseCase.CREATE)) {
            is TemporalPolicyResult.Ready -> {
                if (!applyExactSchedule(policy.resolution, replacingConstraint = replacingSchedule)) {
                    dialogState = priorState
                    speakAndContinueListening(responseManager.correctionNotUnderstood())
                    return
                }
                pendingTaskState.dateText = dateText
                pendingTaskState.timeText = timeText
                pendingSemanticTimePhrase = null
                suggestedLearnedTime = null
                pendingReplacementField = null
                if (replacingSchedule) {
                    returnToSaveConfirmation(updatedField = null)
                } else {
                    moveToNextMissingStep()
                }
            }
            is TemporalPolicyResult.NeedsExactDate,
            is TemporalPolicyResult.NeedsExactTime,
            is TemporalPolicyResult.NeedsExactDateAndTime -> {
                if (replacingSchedule) {
                    beginReplacementScheduleClarification(resolution, policy)
                } else {
                    retainPartialScheduleForClarification(resolution, dateText, timeText, policy)
                }
            }
            is TemporalPolicyResult.InvalidPastSchedule -> {
                dialogState = priorState
                speakAndContinueListening(responseManager.pastDateTime())
            }
            is TemporalPolicyResult.Unresolved -> {
                dialogState = priorState
                speakAndContinueListening(responseManager.correctionNotUnderstood())
            }
        }
    }

    private fun beginReplacementScheduleClarification(
        resolution: TemporalResolution,
        policy: TemporalPolicyResult
    ) {
        val needsDate = policy is TemporalPolicyResult.NeedsExactDate ||
            policy is TemporalPolicyResult.NeedsExactDateAndTime
        val needsTime = policy is TemporalPolicyResult.NeedsExactTime ||
            policy is TemporalPolicyResult.NeedsExactDateAndTime
        pendingTemporalConstraint = resolution
        pendingTemporalClarification = PendingTemporalClarification(
            original = resolution,
            exactDate = resolution.startDateInclusive.takeIf { resolution.isExactDate },
            exactMinute = resolution.startMinuteInclusive.takeIf { resolution.isExactTime },
            needsExactDate = needsDate,
            needsExactTime = needsTime,
            replacingOriginalConstraint = true
        )
        promptNextReplacementScheduleClarification()
    }

    private fun handlePendingReplacementScheduleClarification(
        field: CreateDraftField,
        value: String
    ): Boolean {
        if (pendingTemporalClarification?.replacingOriginalConstraint != true ||
            field != pendingReplacementField
        ) return false
        return when (field) {
            CreateDraftField.DATE -> {
                val resolution = temporalResolver.resolve(value, null, value)
                val date = resolution.startDateInclusive?.takeIf { resolution.isExactDate }
                if (date == null) {
                    speakAndContinueListening(replacementScheduleRetry(CreateDraftField.DATE))
                    return true
                }
                acceptPendingReplacementScheduleValue(field = field, exactDate = date)
            }
            CreateDraftField.TIME -> {
                val resolution = temporalResolver.resolve(null, value, value)
                val minute = resolution.startMinuteInclusive?.takeIf { resolution.isExactTime }
                if (minute == null) {
                    speakAndContinueListening(replacementScheduleRetry(CreateDraftField.TIME))
                    return true
                }
                acceptPendingReplacementScheduleValue(field = field, exactMinute = minute)
            }
            CreateDraftField.TITLE -> return false
        }
    }

    private fun acceptPendingReplacementScheduleValue(
        field: CreateDraftField,
        exactDate: String? = null,
        exactMinute: Int? = null
    ): Boolean {
        val pending = pendingTemporalClarification
            ?.takeIf { it.replacingOriginalConstraint }
            ?: return false
        if (field != pendingReplacementField) return false
        if (!TemporalActionPolicy.validateClarification(pending.original, exactDate, exactMinute)) {
            speakAndContinueListening(replacementScheduleRetry(field))
            return true
        }
        val updated = when (field) {
            CreateDraftField.DATE -> pending.copy(exactDate = exactDate ?: return true)
            CreateDraftField.TIME -> pending.copy(exactMinute = exactMinute ?: return true)
            CreateDraftField.TITLE -> return false
        }

        if (!updated.isComplete) {
            pendingTemporalClarification = updated
            promptNextReplacementScheduleClarification()
            return true
        }

        val exactDate = updated.exactDate ?: return true
        val exactMinute = updated.exactMinute ?: return true
        val exactTime = formatTime(exactMinute / 60, exactMinute % 60)
        val finalResolution = temporalResolver.resolve(
            exactDate,
            exactTime,
            "$exactDate $exactTime"
        )
        when (TemporalActionPolicy.evaluate(finalResolution, TemporalUseCase.CREATE)) {
            is TemporalPolicyResult.Ready -> Unit
            is TemporalPolicyResult.InvalidPastSchedule -> {
                speakAndContinueListening(
                    "${responseManager.pastDateTime()} ${replacementScheduleRetry(field)}"
                )
                return true
            }
            else -> {
                speakAndContinueListening(replacementScheduleRetry(field))
                return true
            }
        }
        if (!applyExactSchedule(finalResolution, replacingConstraint = true)) {
            speakAndContinueListening(replacementScheduleRetry(field))
            return true
        }

        pendingTaskState.dateText = exactDate
        pendingTaskState.timeText = exactTime
        pendingReplacementField = null
        pendingSemanticTimePhrase = null
        suggestedLearnedTime = null
        returnToSaveConfirmation(updatedField = null)
        return true
    }

    private fun handleReplacementSchedulePickerSelection(
        field: CreateDraftField,
        exactDate: String? = null,
        exactMinute: Int? = null
    ): Boolean = when (
        createDraftReplacementPickerRoute(
            replacementClarificationActive =
                pendingTemporalClarification?.replacingOriginalConstraint == true,
            pendingReplacementField = pendingReplacementField,
            pickedField = field
        )
    ) {
        CreateDraftReplacementPickerRoute.NORMAL_DRAFT -> false
        CreateDraftReplacementPickerRoute.ACCEPT_PENDING_REPLACEMENT ->
            acceptPendingReplacementScheduleValue(field, exactDate, exactMinute)
        CreateDraftReplacementPickerRoute.KEEP_PENDING_REPLACEMENT -> {
            promptNextReplacementScheduleClarification()
            true
        }
    }

    private fun promptNextReplacementScheduleClarification() {
        val pending = pendingTemporalClarification
            ?.takeIf { it.replacingOriginalConstraint }
            ?: return
        val response = when {
            pending.needsExactDate && pending.exactDate == null -> {
                pendingReplacementField = CreateDraftField.DATE
                dialogState = CreateTaskDialogState.WAITING_FOR_DATE
                val range = pending.original.originalDatePhrase.trim()
                if (range.isNotEmpty()) {
                    "What exact date within $range would you like?"
                } else {
                    "What exact date would you like?"
                }
            }
            pending.needsExactTime && pending.exactMinute == null -> {
                pendingReplacementField = CreateDraftField.TIME
                dialogState = CreateTaskDialogState.WAITING_FOR_TIME
                val date = pending.original.originalDatePhrase.trim()
                if (date.isNotEmpty()) {
                    "What exact time on $date would you like?"
                } else {
                    "What exact time would you like?"
                }
            }
            else -> return
        }
        speakAndContinueListening(response)
    }

    private fun replacementScheduleRetry(field: CreateDraftField): String = when (field) {
        CreateDraftField.DATE -> "That date is outside the requested range. What exact date would you like?"
        CreateDraftField.TIME -> "That time is outside the requested range. What exact time would you like?"
        CreateDraftField.TITLE -> responseManager.correctionNotUnderstood()
    }

    private fun applyExactSchedule(
        resolution: TemporalResolution,
        replacingConstraint: Boolean
    ): Boolean {
        val date = resolution.startDateInclusive?.takeIf { resolution.isExactDate } ?: return false
        val minute = resolution.startMinuteInclusive?.takeIf { resolution.isExactTime } ?: return false
        val constraint = if (replacingConstraint) {
            null
        } else {
            pendingTemporalClarification?.original ?: pendingTemporalConstraint
        }
        if (constraint != null && !TemporalActionPolicy.validateClarification(constraint, date, minute)) {
            return false
        }

        selectedDate = date
        val parts = date.split("/")
        selectedDay = parts.getOrNull(0)?.toIntOrNull()
        selectedMonth = parts.getOrNull(1)?.toIntOrNull()?.minus(1)
        selectedYear = parts.getOrNull(2)?.toIntOrNull()
        selectedHour24 = minute / 60
        selectedMinute = minute % 60
        selectedTime = formatTime(selectedHour24!!, selectedMinute!!)
        pendingTemporalConstraint = null
        pendingTemporalClarification = null
        renderSelectedDate()
        renderSelectedTime()
        updateScheduleAccessibilityState()
        markCreateDraftChanged()
        return true
    }

    private fun retainPartialScheduleForClarification(
        resolution: TemporalResolution,
        dateText: String,
        timeText: String,
        policy: TemporalPolicyResult
    ) {
        val needsDate = policy is TemporalPolicyResult.NeedsExactDate ||
            policy is TemporalPolicyResult.NeedsExactDateAndTime
        val needsTime = policy is TemporalPolicyResult.NeedsExactTime ||
            policy is TemporalPolicyResult.NeedsExactDateAndTime
        pendingTemporalConstraint = resolution
        pendingTemporalClarification = PendingTemporalClarification(
            original = resolution,
            exactDate = resolution.startDateInclusive.takeIf { resolution.isExactDate },
            exactMinute = resolution.startMinuteInclusive.takeIf { resolution.isExactTime },
            needsExactDate = needsDate,
            needsExactTime = needsTime
        )
        if (resolution.isExactDate && resolution.startDateInclusive != null) {
            acceptExactDate(resolution.startDateInclusive, replacingConstraint = true)
            pendingTaskState.dateText = dateText
        }
        if (resolution.isExactTime && resolution.startMinuteInclusive != null) {
            acceptExactMinute(resolution.startMinuteInclusive, replacingConstraint = true)
            pendingTaskState.timeText = timeText
        } else if (needsTime) {
            pendingTaskState.timeText = timeText
            pendingSemanticTimePhrase = timeText
        }
        moveToNextMissingStep()
    }

    private fun readCurrentDraft(move: CreateDraftMove.ReadDraft) {
        val response = CreateDraftReadResponseRenderer.render(
            target = move.target,
            title = pendingTaskState.title ?: etTaskTitle.text.toString().trim(),
            date = selectedDate,
            time = selectedTime,
            state = dialogState,
            pendingReplacementField = pendingReplacementField
        )
        if (dialogState == CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION) {
            assistantSession.expectConfirmation()
        }
        speakAndContinueListening(response)
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

        val hasDate = temporalResolver.hasExplicitDateExpression(value)
        val hasTime = temporalResolver.hasExplicitTimeExpression(value)
        if (hasDate && hasTime) {
            val resolution = temporalResolver.resolve(null, null, value)
            val dateText = resolution.originalDatePhrase
            val timeText = resolution.originalTimePhrase
            if (dateText.isBlank() || timeText.isBlank()) {
                dialogState = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
                speakAndContinueListening(responseManager.correctionNotUnderstood())
                return
            }
            handleProvidedSchedule(dateText, timeText)
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

        dialogState = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
        speakAndContinueListening(responseManager.correctionNotUnderstood())
    }

    private fun returnToSaveConfirmation(updatedField: CreateDraftField?) {
        pendingReplacementField = null
        if (!isDraftCompleteForSaveConfirmation()) {
            moveToNextMissingStep()
            return
        }
        dialogState = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
        assistantSession.expectConfirmation()
        val summary = buildTaskSummary()
        val response = when (updatedField) {
            CreateDraftField.TITLE -> responseManager.inlineTitleUpdated(summary)
            CreateDraftField.DATE -> responseManager.inlineDateUpdated(summary)
            CreateDraftField.TIME -> responseManager.inlineTimeUpdated(summary)
            null -> responseManager.inlineScheduleUpdated(summary)
        }
        speakAndContinueListening(response)
    }

    private fun isDraftCompleteForSaveConfirmation(): Boolean {
        val title = pendingTaskState.title ?: etTaskTitle.text.toString().trim()
        return title.isNotBlank() && !selectedDate.isNullOrBlank() && !selectedTime.isNullOrBlank()
    }

    private fun cancelCreateDraft() {
        isCreateTaskExitPending = true
        resetTaskDraftState()
        setCreateDraftControlsEnabled(false)
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
            is CreateDraftMove.ProvideSchedule -> "SCHEDULE"
            is CreateDraftMove.ReadDraft -> move.target.name
            else -> "none"
        }
        val valueSupplied = when (move) {
            is CreateDraftMove.ChangeField -> !move.value.isNullOrBlank()
            is CreateDraftMove.ProvideField -> move.value.isNotBlank()
            is CreateDraftMove.ProvideSchedule ->
                move.dateText.isNotBlank() && move.timeText.isNotBlank()
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
        if (BuildConfig.DEBUG) {
            Log.d(
                "TEMPORAL_FOLLOWUP",
                "raw='$raw' dialogState=$dialogState type=${resolution.type} date=${resolution.startDateInclusive} minute=${resolution.startMinuteInclusive} valid=$validationResult"
            )
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
        markCreateDraftChanged()
    }

    private fun buildTaskSummary(): String {
        val title = pendingTaskState.title ?: etTaskTitle.text.toString().trim()
        val date = formatDateForSpeech(selectedDate)
        val time = selectedTime ?: pendingTaskState.timeText ?: "no time"
        return "$title, $date, $time"
    }

    private fun moveToNextMissingStep() {
        // log
        if (BuildConfig.DEBUG) {
            Log.d(
                "CREATE_STATE",
                "title='${pendingTaskState.title}' selectedDate='$selectedDate' selectedTime='$selectedTime' semantic='$pendingSemanticTimePhrase'"
            )
        }

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
                val requestGeneration = createDraftResolutionGeneration
                val requestDraftRevision = createDraftRevision
                val requestDialogState = dialogState
                val semanticPhrase = pendingSemanticTimePhrase

                lifecycleScope.launch {
                    if (!semanticPhrase.isNullOrBlank()) {
                        val learned = withContext(Dispatchers.IO) {
                            timePreferenceLearner.getLearnedTimeForPhrase(semanticPhrase)
                        }
                        if (!isCurrentLearnedTimeRequest(
                                requestGeneration,
                                requestDraftRevision,
                                requestDialogState,
                                semanticPhrase
                            )
                        ) return@launch

                        if (learned != null && learned.usageCount >= 2 && isTimeAllowedByPendingConstraint(learned.resolvedTime.resolvedTimeMinute())) {
                            suggestedLearnedTime = learned.resolvedTime
                            speakAndContinueListening(
                                //"You usually mean ${learned.resolvedTime} when you say ${semanticPhrase}. Please say yes to use it, or say a different time."
                                responseManager.learnedTimeSuggestion(semanticPhrase, learned.resolvedTime)
                            )
                        } else {
                            speakAndContinueListening(
                                //"I understood the time as ${semanticPhrase}. Please tell me an exact clock time, for example 8 PM."
                                responseManager.semanticTimeNeedsExact(semanticPhrase)
                            )
                        }
                    } else {
                        if (isCurrentLearnedTimeRequest(
                                requestGeneration,
                                requestDraftRevision,
                                requestDialogState,
                                null
                            )
                        ) {
                            promptHelper.askTime()
                        }
                    }
                }
            }

            else -> {
                Log.d("CREATE_STATE", "next=WAITING_FOR_SAVE_CONFIRMATION")
                dialogState = CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION
                assistantSession.expectConfirmation()
                promptHelper.askSaveTask(buildTaskSummary())
            }
        }
    }

    private fun isCurrentLearnedTimeRequest(
        requestGeneration: Long,
        requestDraftRevision: Long,
        requestDialogState: CreateTaskDialogState,
        semanticPhrase: String?
    ): Boolean = canRunCreateAssistantCallback() &&
        requestGeneration == createDraftResolutionGeneration &&
        requestDraftRevision == createDraftRevision &&
        requestDialogState == dialogState &&
        semanticPhrase == pendingSemanticTimePhrase

    private fun canRunCreateAssistantCallback(): Boolean =
        lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
            !isFinishing &&
            !isDestroyed &&
            !isSavingTask &&
            !isCreateTaskExitPending

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
        assistantSession.speak(
            text = text,
            listenAgain = shouldContinueConversation()
        )
    }

    private fun speakWithPanel(text: String) {
        assistantSession.speak(text, listenAgain = false)
    }

    private fun speakControlIdentification(text: String) {
        voiceHelper.speak(text)
    }

    private fun speakThenListenAgain(text: String) {
        assistantSession.speakThenListenAgain(text)
    }

    private fun handleListenFailure(reply: String) {
        assistantSession.handleListenFailure(reply)
    }


    private fun speakThenFinish(text: String) {
        isCreateTaskExitPending = true
        setCreateDraftControlsEnabled(false)
        assistantSession.speakThenRun(text) {
            finish()
        }
    }

    private fun resetTaskDraftState() {
        invalidateCreateDraftResolution()
        markCreateDraftChanged()
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
        renderSelectedDate()
        renderSelectedTime()
        updateScheduleAccessibilityState()
    }

    private fun markCreateDraftChanged() {
        createDraftRevision += 1
    }

    private fun setCreateDraftControlsEnabled(enabled: Boolean) {
        val canEnable = enabled && !isSavingTask && !isCreateTaskExitPending && !isFinishing && !isDestroyed
        etTaskTitle.isEnabled = canEnable
        btnSaveTask.isEnabled = canEnable
        dateInfoGroup.isEnabled = canEnable
        timeInfoGroup.isEnabled = canEnable
        btnTalkAssistant.isEnabled = canEnable
    }

    private fun invalidateCreateDraftResolution() {
        createDraftResolutionGeneration += 1
        isResolvingCreateDraftMove = false
        setCreateDraftControlsEnabled(true)
    }

    private fun clearPrefillExtras() {
        intent.removeExtra("prefill_title")
        intent.removeExtra("prefill_date_text")
        intent.removeExtra("prefill_time_text")
        intent.removeExtra(EXTRA_CREATE_TASK_ASSISTANT_HANDOFF)
    }

    override fun onAssistantFinalText(text: String) {
        handleVoiceCommand(text)
    }

    override fun onAssistantCancelled() {
        invalidateCreateDraftResolution()
        if (dialogState != CreateTaskDialogState.IDLE) {
            markCreateDraftChanged()
        }
        dialogState = CreateTaskDialogState.IDLE
        suggestedLearnedTime = null
        pendingSemanticTimePhrase = null
    }

    override fun onAssistantSessionStopped() {
        invalidateCreateDraftResolution()
        suggestedLearnedTime = null
        pendingSemanticTimePhrase = null
    }

    override fun onStart() {
        super.onStart()
        scheduleIncomingPrefill()
    }

    override fun onStop() {
        window.decorView.removeCallbacks(incomingPrefillRunnable)
        if (!isChangingConfigurations) {
            invalidateCreateDraftResolution()
            assistantSession.stopForLifecycle()
            clearTransientCreateAssistantState()
            Log.d(
                "CREATE_LIFECYCLE",
                "state=STOPPED assistantSessionStopped=true semanticResolutionInvalidated=true"
            )
        }
        super.onStop()
    }

    private fun scheduleIncomingPrefill() {
        if (hasConsumedPrefill || isFinishing || isDestroyed) return
        window.decorView.removeCallbacks(incomingPrefillRunnable)
        window.decorView.postDelayed(incomingPrefillRunnable, 1500L)
    }

    private fun clearTransientCreateAssistantState() {
        if (dialogState != CreateTaskDialogState.IDLE) {
            markCreateDraftChanged()
        }
        dialogState = CreateTaskDialogState.IDLE
        pendingTemporalConstraint = null
        pendingTemporalClarification = null
        pendingReplacementField = null
        suggestedLearnedTime = null
        pendingSemanticTimePhrase = null
    }

    override fun onAssistantTypedInputRequested() {
        showTypedAssistantInputDialog()
    }

    private fun showTypedAssistantInputDialog() {
        AccessibleAssistantInputDialog.show(
            activity = this,
            title = "Type assistant response",
            message = "Typed and voice input use the same assistant flow.",
            emptyError = "Please type a response",
            onCancel = assistantSession::onTypedInputCancelled
        ) { typedText ->
            assistantSession.submitTypedText(typedText, clearConversation = false)
        }
    }

    private fun updateScheduleAccessibilityState() {
        updateDateAccessibilityState()
        updateTimeAccessibilityState()
    }

    private fun renderSelectedDate() {
        tvSelectedDate.text = TaskFormScheduleValueRenderer.date(selectedDate)
    }

    private fun renderSelectedTime() {
        tvSelectedTime.text = TaskFormScheduleValueRenderer.time(selectedTime)
    }

    private fun updateDateAccessibilityState() {
        dateInfoGroup.contentDescription = "Date"
        AccessibilityStateHelper.updateStateDescription(
            dateInfoGroup,
            TaskCardAccessibilitySemantics.spokenDate(selectedDate)
                .replace("No date set", "No date selected")
        )
    }

    private fun updateTimeAccessibilityState() {
        timeInfoGroup.contentDescription = "Time"
        AccessibilityStateHelper.updateStateDescription(
            timeInfoGroup,
            TaskCardAccessibilitySemantics.spokenTime(selectedTime)
                .replace("No time set", "No time selected")
        )
    }

    override fun onDestroy() {
        window.decorView.removeCallbacks(incomingPrefillRunnable)
        invalidateCreateDraftResolution()
        developerAssistantOverlay?.dismiss()
        developerAssistantOverlay = null
        assistantSession.destroy()
        voiceHelper.shutdown()
        super.onDestroy()
    }

}
