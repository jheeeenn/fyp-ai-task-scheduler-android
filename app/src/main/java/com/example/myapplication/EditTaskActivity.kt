package com.example.myapplication


import android.app.DatePickerDialog
import android.app.TimePickerDialog

import android.os.Bundle
import android.util.Log
import android.view.View

import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.example.myapplication.accessibility.AccessibilityActivity

import androidx.lifecycle.lifecycleScope
import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.AiParsedCommand
import com.example.myapplication.ai.agent.ActionValidator
import com.example.myapplication.ai.agent.AgentOrchestrator
import com.example.myapplication.ai.agent.LaptopAgentClient
import com.example.myapplication.ai.agent.TaskActionNormalizer
import com.example.myapplication.ai.agent.TaskAgentProcessingException
import com.example.myapplication.ai.agent.TaskAgentResponseParser
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.TaskEntity
import com.example.myapplication.reminder.ReminderEligibilityPolicy
import com.example.myapplication.reminder.ReminderSchedulingEligibility
import com.example.myapplication.voice.AssistantPromptHelper
import com.example.myapplication.voice.AssistantResponseManager
import com.example.myapplication.voice.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.example.myapplication.voice.AssistantVoiceHost
import com.example.myapplication.voice.AssistantVoiceSession

import com.example.myapplication.ai.temporal.TemporalActionPolicy
import com.example.myapplication.ai.temporal.EditTemporalCommandDisposition
import com.example.myapplication.ai.temporal.EditTemporalCommandPolicy
import com.example.myapplication.ai.temporal.EditTemporalTarget
import com.example.myapplication.ai.temporal.TemporalExpressionResolver
import com.example.myapplication.ai.temporal.TemporalResolution
import com.example.myapplication.ai.temporal.PendingTemporalClarification
import com.example.myapplication.ai.temporal.TemporalPolicyResult
import com.example.myapplication.ai.temporal.TemporalUseCase
import com.example.myapplication.ai.temporal.ExactTemporalSchedule
import com.example.myapplication.ai.temporal.RelativeTemporalCalculationResult
import com.example.myapplication.ai.temporal.RelativeTemporalChangeCalculator
import com.example.myapplication.ai.temporal.RelativeTemporalBase
import com.example.myapplication.ai.temporal.RelativeTemporalOperation
import com.example.myapplication.ai.temporal.RelativeTemporalProposal
import com.example.myapplication.ai.temporal.RelativeTemporalProposalValidator
import com.example.myapplication.ai.temporal.RelativeTemporalProposalSession
import com.example.myapplication.ai.temporal.RelativeTemporalProposalState
import com.example.myapplication.ai.temporal.RelativeTemporalRevisionResult
import com.example.myapplication.ai.temporal.RelativeTemporalSaveClaim
import com.example.myapplication.ai.temporal.RelativeTemporalSaveClaimResult
import com.example.myapplication.ai.temporal.RelativeTemporalSpeechRenderer
import com.example.myapplication.ai.temporal.ValidatedRelativeTemporalCorrection
import com.example.myapplication.accessibility.AccessibleAssistantInputDialog
import com.example.myapplication.accessibility.AccessibilityStateHelper
import com.example.myapplication.accessibility.TaskCardAccessibilitySemantics
import com.example.myapplication.accessibility.AssistantAccessibilityState
import com.example.myapplication.accessibility.AccessibilityAnnouncementHelper

private enum class EditFieldTarget {
    NONE, TITLE, DATE, TIME, DATE_OR_TIME
}


class EditTaskActivity : AccessibilityActivity(), AssistantVoiceHost {
    private lateinit var promptHelper: AssistantPromptHelper
    private lateinit var assistantSession: AssistantVoiceSession
    private var isForceStoppingAssistant = false
    private var pendingFieldTarget = EditFieldTarget.NONE
    private var waitingForSaveConfirmation = false
    private var assistantMode: String? = null
    private val temporalResolver = TemporalExpressionResolver()
    private var pendingTemporalConstraint: TemporalResolution? = null
    private var pendingTemporalClarification: PendingTemporalClarification? = null
    private val relativeTemporalCalculator = RelativeTemporalChangeCalculator()
    private lateinit var relativeTemporalAgent: AgentOrchestrator
    private var relativeTemporalSession: RelativeTemporalProposalSession? = null
    private var relativeTemporalCorrectionInFlight = false
    private var initialProposalCrossedDateBoundary = false

    private var authoritativeOriginalTitle: String = ""
    private var authoritativeOriginalDate: String? = null
    private var authoritativeOriginalTime: String? = null
    private var authoritativeOriginalIsDone: Boolean = false

    private lateinit var voiceHelper: VoiceHelper

    private lateinit var responseManager: AssistantResponseManager






    private lateinit var etTaskTitle: EditText
    private lateinit var tvSelectedDate: TextView
    private lateinit var tvSelectedTime: TextView
    private lateinit var dateInfoGroup: View
    private lateinit var timeInfoGroup: View

    private var taskId: Long = -1L

    private var selectedDate: String? = null
    private var selectedTime: String? = null

    private var selectedYear: Int? = null
    private var selectedMonth: Int? = null
    private var selectedDay: Int? = null
    private var selectedHour24: Int? = null
    private var selectedMinute: Int? = null

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
        setContentView(R.layout.activity_edit_task)
        AccessibilityStateHelper.markHeading(findViewById(R.id.tvEditTitle))

        etTaskTitle = findViewById(R.id.etTaskTitle)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        tvSelectedTime = findViewById(R.id.tvSelectedTime)
        dateInfoGroup = findViewById(R.id.dateInfoGroup)
        timeInfoGroup = findViewById(R.id.timeInfoGroup)

        val btnPickDate = findViewById<Button>(R.id.btnPickDate)
        val btnPickTime = findViewById<Button>(R.id.btnPickTime)
        val btnSaveTask = findViewById<Button>(R.id.btnSaveTask)
        val btnDeleteTask = findViewById<Button>(R.id.btnDeleteTask)
        val btnCancelTask = findViewById<Button>(R.id.btnCancelTask)
        val btnGoHome = findViewById<Button>(R.id.btnGoHome)
        val btnTalkAssistant = findViewById<Button>(R.id.btnTalkAssistant)

        voiceHelper = VoiceHelper(this)
        responseManager = AssistantResponseManager.fromPreferences(this)
        relativeTemporalAgent = AgentOrchestrator(
            LaptopAgentClient(this),
            TaskAgentResponseParser(),
            TaskActionNormalizer(),
            ActionValidator()
        )


        assistantSession = AssistantVoiceSession(
            activity = this,
            host = this,
            voiceHelper = voiceHelper,
            responseManager = responseManager,
            audioPermissionLauncher = audioPermissionLauncher,
            onAccessibilityStateChanged = { state ->
                AccessibilityStateHelper.updateAssistantState(btnTalkAssistant, state, announce = false)
            }
        )
        assistantSession.bindAssistantControl(btnTalkAssistant)
        AccessibilityStateHelper.updateAssistantState(
            btnTalkAssistant,
            AssistantAccessibilityState.READY,
            announce = false
        )

        promptHelper = AssistantPromptHelper(assistantSession, responseManager)

        taskId = intent.getLongExtra("task_id", -1L)
        authoritativeOriginalTitle = intent.getStringExtra("task_title") ?: ""
        authoritativeOriginalDate = intent.getStringExtra("task_date")
        authoritativeOriginalTime = intent.getStringExtra("task_time")
        authoritativeOriginalIsDone = intent.getBooleanExtra("task_is_done", false)

        etTaskTitle.setText(authoritativeOriginalTitle)
        selectedDate = authoritativeOriginalDate
        selectedTime = authoritativeOriginalTime

        tvSelectedDate.text = "Selected date: ${authoritativeOriginalDate ?: "No date selected"}"
        tvSelectedTime.text = "Selected time: ${authoritativeOriginalTime ?: "No time selected"}"
        updateScheduleAccessibilityState()

        parseExistingDate(authoritativeOriginalDate)
        parseExistingTime(authoritativeOriginalTime)

        assistantMode = intent.getStringExtra("assistant_mode")

        val prefillTitle = intent.getStringExtra("prefill_title")
        val prefillNewDateText = intent.getStringExtra("prefill_new_date_text")
        val prefillNewTimeText = intent.getStringExtra("prefill_new_time_text")
        val rescheduleCollectionRequired =
            intent.getBooleanExtra("reschedule_collection_required", false)
        val relativeTemporalProposal =
            intent.getBooleanExtra("relative_temporal_proposal", false)
        val initialRelativeTemporalSemanticProposal =
            readInitialRelativeTemporalSemanticProposal()
        initialProposalCrossedDateBoundary = intent.getBooleanExtra(
            "relative_temporal_crossed_date_boundary",
            false
        )

        if (!prefillTitle.isNullOrBlank()) {
            etTaskTitle.setText(prefillTitle)
        }


        btnPickDate.setOnClickListenerWithHaptic { openDatePicker() }
        btnPickTime.setOnClickListenerWithHaptic { openTimePicker() }
        btnSaveTask.setOnClickListenerWithHaptic {
            saveTask(relativeTemporalSession?.revision)
        }
        btnDeleteTask.setOnClickListenerWithHaptic { confirmDeleteTask() }


        btnCancelTask.setOnClickListenerWithHaptic {
            if (ignoreInputWhileRelativeTemporalSaveIsInFlight()) return@setOnClickListenerWithHaptic
            relativeTemporalSession?.cancel()
            assistantSession.speakThenRun(responseManager.cancelEdit()) {
                finish()
            }
        }

        btnGoHome.setOnClickListenerWithHaptic {
            if (ignoreInputWhileRelativeTemporalSaveIsInFlight()) return@setOnClickListenerWithHaptic
            relativeTemporalSession?.cancel()
            assistantSession.speakThenRun(responseManager.returnHomeFromEdit()) {
                finish()
            }
        }

        btnTalkAssistant.setOnClickListenerWithHaptic {
            if (ignoreInputWhileRelativeTemporalSaveIsInFlight()) return@setOnClickListenerWithHaptic
            assistantSession.startSession()
        }
        btnTalkAssistant.setOnLongClickListener {
            btnTalkAssistant.performLongClickHapticFeedback()
            if (ignoreInputWhileRelativeTemporalSaveIsInFlight()) return@setOnLongClickListener true
            showTypedAssistantInputDialog()
            true
        }
        AccessibilityStateHelper.exposeTypedInputAction(btnTalkAssistant)



        // Check if the activity was started by the assistant (from home)
        val openedByAssistant = intent.getBooleanExtra("opened_by_assistant", false)

        if (openedByAssistant) {
            assistantSession.startPassiveSession(clearConversation = true)
            isForceStoppingAssistant = false

            /*val introReply = if (assistantMode == "reschedule") {
                waitingForSaveConfirmation = true
                buildString {
                    append("You are rescheduling ${etTaskTitle.text}.")
                    if (!prefillNewDateText.isNullOrBlank() || !prefillNewTimeText.isNullOrBlank()) {
                        append(" I updated")
                        if (!prefillNewDateText.isNullOrBlank() && !prefillNewTimeText.isNullOrBlank()) {
                            append(" the date and time")
                        } else if (!prefillNewDateText.isNullOrBlank()) {
                            append(" the date")
                        } else if (!prefillNewTimeText.isNullOrBlank()) {
                            append(" the time")
                        }
                        append(".")
                    }
                    append(" Would you like me to save the changes?")
                }
            } else {
                "You are editing ${etTaskTitle.text}. What would you like to change?"
            }*/
            val changed = applyProposedTemporalChange(prefillNewDateText, prefillNewTimeText, askForMissing = false)
            if (relativeTemporalProposal && changed && initialRelativeTemporalSemanticProposal != null) {
                relativeTemporalSession = RelativeTemporalProposalSession(
                    authoritativeOriginal = ExactTemporalSchedule(
                        authoritativeOriginalDate,
                        authoritativeOriginalTime
                    ),
                    initialProposal = ExactTemporalSchedule(selectedDate, selectedTime),
                    initialSemanticProposal = initialRelativeTemporalSemanticProposal,
                    initialRevision = intent.getIntExtra("relative_temporal_revision", 1)
                )
                logRelativeTemporalProposal("WAITING_CONFIRMATION")
            }
            window.decorView.postDelayed({
                when {
                    pendingTemporalClarification != null -> advanceTemporalClarification()
                    changed -> askToSaveChanges()
                    assistantMode == "reschedule" || rescheduleCollectionRequired -> {
                        waitingForSaveConfirmation = false
                        enterTemporalCollection(EditTemporalTarget.DATE_OR_TIME)
                    }
                    else -> assistantSession.speak(
                        text = responseManager.editIntro(etTaskTitle.text.toString()),
                        listenAgain = true
                    )
                }
            }, 350)
        }
    }

    private fun openDatePicker() {
        if (ignoreInputWhileRelativeTemporalSaveIsInFlight()) return
        val calendar = Calendar.getInstance()
        val year = selectedYear ?: calendar.get(Calendar.YEAR)
        val month = selectedMonth ?: calendar.get(Calendar.MONTH)
        val day = selectedDay ?: calendar.get(Calendar.DAY_OF_MONTH)

        val dialog = DatePickerDialog(
            this,
            { _, pickedYear, pickedMonth, pickedDay ->
                if (ignoreInputWhileRelativeTemporalSaveIsInFlight()) return@DatePickerDialog
                val pickedDate = formatDate(pickedYear, pickedMonth, pickedDay)
                val wasTemporalClarification = pendingTemporalClarification != null
                if (acceptExactDate(pickedDate, replacingConstraint = false)) {
                    if (!wasTemporalClarification) {
                        pendingFieldTarget = EditFieldTarget.NONE
                        announceManualScheduleChange(
                            view = dateInfoGroup,
                            event = "DATE_UPDATED",
                            value = TaskCardAccessibilitySemantics.spokenDate(selectedDate)
                        )
                    }
                } else {
                    speak("That date is outside the requested date range. Please choose a valid date.")
                }
            },
            year,
            month,
            day
        )
        dialog.datePicker.minDate = System.currentTimeMillis() - 1000
        dialog.show()
    }

    private fun openTimePicker() {
        if (ignoreInputWhileRelativeTemporalSaveIsInFlight()) return
        val calendar = Calendar.getInstance()
        val hour = selectedHour24 ?: calendar.get(Calendar.HOUR_OF_DAY)
        val minute = selectedMinute ?: calendar.get(Calendar.MINUTE)

        val dialog = TimePickerDialog(
            this,
            { _, pickedHour, pickedMinute ->
                if (ignoreInputWhileRelativeTemporalSaveIsInFlight()) return@TimePickerDialog
                val pickedMinuteOfDay = pickedHour * 60 + pickedMinute
                val wasTemporalClarification = pendingTemporalClarification != null
                if (acceptExactMinute(pickedMinuteOfDay, replacingConstraint = false)) {
                    if (!wasTemporalClarification) {
                        pendingFieldTarget = EditFieldTarget.NONE
                        announceManualScheduleChange(
                            view = timeInfoGroup,
                            event = "TIME_UPDATED",
                            value = TaskCardAccessibilitySemantics.spokenTime(selectedTime)
                        )
                    }
                } else {
                    speak("That time is outside the requested time range. Please choose a valid time.")
                }
            },
            hour,
            minute,
            false
        )
        dialog.show()
    }

    private fun saveTask(expectedProposalRevision: Int? = null) {
        val proposedTitle = etTaskTitle.text.toString().trim()

        if (proposedTitle.isEmpty()) {
            etTaskTitle.error = "Task title cannot be empty"
            etTaskTitle.requestFocus()
            return
        }

        // Capture the ordinary edit values before a relative save claim can enter SAVING.
        val ordinaryScheduleSnapshot = ExactTemporalSchedule(selectedDate, selectedTime)
        val claimedSession = relativeTemporalSession
        val saveClaim = claimedSession?.let { session ->
            when (val result = session.claimSave(expectedProposalRevision ?: -1, proposedTitle)) {
                is RelativeTemporalSaveClaimResult.Claimed -> result.claim
                RelativeTemporalSaveClaimResult.Inactive,
                RelativeTemporalSaveClaimResult.StaleRevision -> {
                    Log.d(
                        "RELATIVE_TEMPORAL_PROPOSAL",
                        "revision=${session.revision} state=${session.state} saveClaim=REJECTED"
                    )
                    speak("That confirmation is no longer current. Please review the latest proposal.")
                    return
                }
            }
        }

        // Everything below this point uses immutable values frozen by the confirmation claim.
        val claimedTitle = saveClaim?.title ?: proposedTitle
        val claimedSchedule = saveClaim?.schedule ?: ordinaryScheduleSnapshot
        val claimedDate = claimedSchedule.date
        val claimedTime = claimedSchedule.time
        val claimedTaskId = taskId
        val claimedOriginalTitle = authoritativeOriginalTitle
        val claimedOriginalDate = authoritativeOriginalDate
        val claimedOriginalTime = authoritativeOriginalTime
        val claimedOriginalIsDone = authoritativeOriginalIsDone

        saveClaim?.let {
            Log.d(
                "RELATIVE_TEMPORAL_PROPOSAL",
                "revision=${it.revision} state=SAVING saveClaim=ACQUIRED"
            )
        }

        val dao = AppDatabase.getInstance(this).taskDao()

        lifecycleScope.launch {
            val existingTask = withContext(Dispatchers.IO) {
                dao.getById(claimedTaskId)
            }
            if (existingTask == null) {
                failRelativeTemporalSaveClaim(claimedSession, saveClaim, retryable = false)
                Toast.makeText(
                    this@EditTaskActivity,
                    "Task no longer exists.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
                return@launch
            }
            if (saveClaim != null && !authoritativeSnapshotMatches(
                    task = existingTask,
                    expectedTaskId = claimedTaskId,
                    expectedTitle = claimedOriginalTitle,
                    expectedDate = claimedOriginalDate,
                    expectedTime = claimedOriginalTime,
                    expectedIsDone = claimedOriginalIsDone
                )
            ) {
                failRelativeTemporalSaveClaim(claimedSession, saveClaim, retryable = false)
                speak("That task changed since this proposal was created. I did not save anything.")
                return@launch
            }
            if (saveClaim != null && claimedSession?.isCurrentSaveClaim(saveClaim) != true) {
                Log.d(
                    "RELATIVE_TEMPORAL_PROPOSAL",
                    "revision=${saveClaim.revision} state=STALE_SAVE_CLAIM mutation=SKIPPED"
                )
                speak("That confirmation is no longer current. I did not save anything.")
                return@launch
            }

            val finalResolution = temporalResolver.resolve(
                claimedDate,
                claimedTime,
                listOfNotNull(claimedDate, claimedTime).joinToString(" ")
            )
            if (TemporalActionPolicy.evaluate(finalResolution, TemporalUseCase.UPDATE) is TemporalPolicyResult.InvalidPastSchedule) {
                failRelativeTemporalSaveClaim(claimedSession, saveClaim, retryable = true)
                speak(responseManager.pastDateTime())
                return@launch
            }

            val updated = withContext(Dispatchers.IO) {
                if (saveClaim != null) {
                    dao.updateTaskAndSubtasksIfAuthoritativeSnapshotMatches(
                        id = claimedTaskId,
                        expectedTitle = claimedOriginalTitle,
                        expectedDueDate = claimedOriginalDate,
                        expectedDueTime = claimedOriginalTime,
                        expectedIsDone = claimedOriginalIsDone,
                        newTitle = claimedTitle,
                        newDueDate = claimedDate,
                        newDueTime = claimedTime
                    )
                } else {
                    dao.updateTask(claimedTaskId, claimedTitle, claimedDate, claimedTime)
                    if (existingTask.parentTaskId == null) {
                        dao.updateSubtasksSchedule(
                            parentTaskId = claimedTaskId,
                            dueDate = claimedDate,
                            dueTime = claimedTime
                        )
                    }
                    true
                }
            }
            if (!updated) {
                failRelativeTemporalSaveClaim(claimedSession, saveClaim, retryable = false)
                speak("That task changed before I could save it. I did not apply the proposal.")
                return@launch
            }

            val saveStateCompleted = if (saveClaim != null) {
                claimedSession?.completeSave(saveClaim) == true
            } else {
                true
            }
            if (!saveStateCompleted) {
                Log.e(
                    "RELATIVE_TEMPORAL_PROPOSAL",
                    "revision=${saveClaim?.revision ?: -1} state=SAVE_COMPLETION_REJECTED"
                )
                speak("The save could not be completed safely. Please review the task before trying again.")
                return@launch
            }

            val updatedTask = existingTask.copy(
                title = claimedTitle,
                dueDate = claimedDate,
                dueTime = claimedTime
            )

            saveClaim?.let {
                Log.d(
                    "RELATIVE_TEMPORAL_PROPOSAL",
                    "revision=${it.revision} state=SAVED " +
                        "hasDateChange=${it.schedule.date != claimedOriginalDate} " +
                        "hasTimeChange=${it.schedule.time != claimedOriginalTime}"
                )
            }

            // Reminder work is authorized only after Room mutation and SAVING -> SAVED.
            ReminderHelper.cancelReminder(this@EditTaskActivity, claimedTaskId)

            val reminderExpected = updatedTask.parentTaskId == null &&
                !updatedTask.isDone &&
                !updatedTask.dueDate.isNullOrBlank() &&
                !updatedTask.dueTime.isNullOrBlank()
            val schedulingEligibility = ReminderEligibilityPolicy.evaluateForScheduling(
                updatedTask,
                System.currentTimeMillis()
            )
            val scheduled = if (
                reminderExpected &&
                schedulingEligibility is ReminderSchedulingEligibility.Eligible
            ) {
                ReminderHelper.scheduleReminderFromTask(
                    this@EditTaskActivity,
                    updatedTask
                )
            } else {
                !reminderExpected
            }

            if (reminderExpected) {
                if (scheduled) {
                    Toast.makeText(
                        this@EditTaskActivity,
                        "Task updated and reminder rescheduled",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@EditTaskActivity,
                        "Task updated, but reminder could not be scheduled",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(
                    this@EditTaskActivity,
                    "Task updated. Reminder removed.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            waitingForSaveConfirmation = false
            assistantSession.dismissPanel()
            finish()
        }
    }

    private fun failRelativeTemporalSaveClaim(
        session: RelativeTemporalProposalSession?,
        claim: RelativeTemporalSaveClaim?,
        retryable: Boolean
    ) {
        if (session == null || claim == null) return
        val released = session.failSave(claim, retryable)
        Log.d(
            "RELATIVE_TEMPORAL_PROPOSAL",
            "revision=${claim.revision} state=${session.state} saveClaimReleased=$released"
        )
    }

    private fun ignoreInputWhileRelativeTemporalSaveIsInFlight(): Boolean {
        val session = relativeTemporalSession ?: return false
        if (session.state != RelativeTemporalProposalState.SAVING) return false
        Log.d(
            "RELATIVE_TEMPORAL_PROPOSAL",
            "revision=${session.revision} state=SAVING action=IGNORED"
        )
        speak("I am saving the confirmed proposal. Please wait.")
        return true
    }

    private fun confirmDeleteTask() {
        if (ignoreInputWhileRelativeTemporalSaveIsInFlight()) return
        val dao = AppDatabase.getInstance(this).taskDao()

        AlertDialog.Builder(this)
            .setTitle("Delete task?")
            .setMessage("Are you sure you want to delete this task?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        dao.deleteTaskAndSubtasks(taskId)
                    }
                    ReminderHelper.cancelReminder(this@EditTaskActivity, taskId)
                    Toast.makeText(
                        this@EditTaskActivity,
                        "Task deleted",
                        Toast.LENGTH_SHORT
                    ).show()
                    waitingForSaveConfirmation = false
                    assistantSession.dismissPanel()
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleVoiceInput(text: String) {
        if (ignoreInputWhileRelativeTemporalSaveIsInFlight()) return
        val normalized = TextNormalizer.normalize(text)
        if (BuildConfig.DEBUG) {
            Log.d(
                "EDIT_VOICE",
                "pendingFieldTarget=$pendingFieldTarget " +
                    "waitingForSaveConfirmation=$waitingForSaveConfirmation " +
                    "hasPendingTemporal=${pendingTemporalClarification != null} " +
                    "relativeProposalActive=${relativeTemporalSession?.state == RelativeTemporalProposalState.ACTIVE}"
            )
        }

        if (handleRelativeTemporalProposalInput(normalized)) return

        if (isSaveCommand(normalized)) {
            if (pendingFieldTarget != EditFieldTarget.NONE || pendingTemporalClarification != null) {
                repeatPendingTemporalPrompt()
                return
            }
            waitingForSaveConfirmation = false
            assistantSession.dismissPanel()
            saveTask()
            return
        }

        if (isConversationExitCommand(normalized)) {
            endAssistantConversation()
            return
        }

        if (handleOneSentenceTemporalCommand(normalized)) {
            return
        }

        if (waitingForSaveConfirmation) {
            when {
                isYes(normalized) -> {
                    if (pendingFieldTarget != EditFieldTarget.NONE || pendingTemporalClarification != null) {
                        repeatPendingTemporalPrompt()
                        return
                    }
                    waitingForSaveConfirmation = false
                    assistantSession.dismissPanel()
                    saveTask()
                    return
                }

                isNo(normalized) -> {
                    waitingForSaveConfirmation = false
                    promptHelper.askWhatToChange()
                    return
                }

                isDateFieldCommand(normalized) -> {
                    waitingForSaveConfirmation = false
                    pendingFieldTarget = EditFieldTarget.DATE
                    promptHelper.speakInfo(responseManager.askChangeDate(), true, responseManager.hintDate())
                    return
                }

                isTimeFieldCommand(normalized) -> {
                    waitingForSaveConfirmation = false
                    pendingFieldTarget = EditFieldTarget.TIME
                    promptHelper.speakInfo(responseManager.askChangeTime(), true, responseManager.hintTime())
                    return
                }

                isTitleFieldCommand(normalized) -> {
                    waitingForSaveConfirmation = false
                    pendingFieldTarget = EditFieldTarget.TITLE
                    promptHelper.speakInfo(responseManager.askChangeTitle(), true, responseManager.hintTitle())
                    return
                }

                applySpokenDate(normalized, replacingConstraint = true) -> {
                    waitingForSaveConfirmation = false
                    askToSaveChanges()
                    return
                }

                applySpokenTime(normalized, replacingConstraint = true) -> {
                    waitingForSaveConfirmation = false
                    askToSaveChanges()
                    return
                }
            }
        }

        // If we already asked what field value to set, handle it locally first
        if (handlePendingFieldValue(normalized)) {
            return
        }

        // Local short edit commands
        when {
            isDateFieldCommand(normalized) -> {
                pendingFieldTarget = EditFieldTarget.DATE
                promptHelper.speakInfo(responseManager.askChangeDate(), true, responseManager.hintDate())
                return
            }

            isTimeFieldCommand(normalized) -> {
                pendingFieldTarget = EditFieldTarget.TIME
                promptHelper.speakInfo(responseManager.askChangeTime(), true, responseManager.hintTime())
                return
            }

            isTitleFieldCommand(normalized) -> {
                pendingFieldTarget = EditFieldTarget.TITLE
                promptHelper.speakInfo(responseManager.askChangeTitle(), true, responseManager.hintTitle())
                return
            }

            normalized == "delete" ||
                    normalized == "delete task" ||
                    normalized == "delete this" -> {
                speak(responseManager.editDeleteCurrent())
                lifecycleScope.launch {
                    val dao = AppDatabase.getInstance(this@EditTaskActivity).taskDao()
                    withContext(Dispatchers.IO) {
                        dao.deleteTaskAndSubtasks(taskId)
                    }
                    ReminderHelper.cancelReminder(this@EditTaskActivity, taskId)
                    assistantSession.dismissPanel()
                    finish()
                }
                return
            }
        }


        speak("Please return to the main assistant for a new command, or choose title, date, time, or delete for this task.")
    }

    private fun handleRelativeTemporalProposalInput(normalized: String): Boolean {
        val session = relativeTemporalSession ?: return false
        if (session.state == RelativeTemporalProposalState.SAVING) {
            ignoreInputWhileRelativeTemporalSaveIsInFlight()
            return true
        }
        if (session.state != RelativeTemporalProposalState.ACTIVE) return false
        if (isConversationExitCommand(normalized)) {
            session.cancel()
            logRelativeTemporalProposal("CANCELLED")
            waitingForSaveConfirmation = false
            endAssistantConversation()
            return true
        }
        if (relativeTemporalCorrectionInFlight) {
            speak("I am still checking the latest correction. Please wait.")
            return true
        }
        when {
            isRelativeCancellationCommand(normalized) -> {
                session.cancel()
                logRelativeTemporalProposal("CANCELLED")
                waitingForSaveConfirmation = false
                assistantSession.speakThenRun(
                    "Okay, I cancelled that change. The task was not updated."
                ) {
                    finish()
                }
            }
            isRelativeRepeatCommand(normalized) -> {
                speakCurrentRelativeTemporalProposal()
            }
            isSaveCommand(normalized) || isYes(normalized) -> {
                waitingForSaveConfirmation = false
                assistantSession.dismissPanel()
                saveTask(session.revision)
            }
            isNo(normalized) -> {
                waitingForSaveConfirmation = true
                speak("Okay, I have not saved it. Tell me the schedule correction you want.")
            }
            else -> processRelativeTemporalCorrection(normalized)
        }
        return true
    }

    private fun processRelativeTemporalCorrection(normalized: String) {
        val session = relativeTemporalSession ?: return
        val correctionContext = session.correctionContext()
        if (correctionContext == null) {
            waitingForSaveConfirmation = true
            speak("Please describe the complete schedule change you want from the original task.")
            return
        }
        val token = session.beginCorrection()
        relativeTemporalCorrectionInFlight = true
        waitingForSaveConfirmation = false
        lifecycleScope.launch {
            try {
                if (!authoritativeTaskStillMatches()) {
                    session.cancel()
                    speak("That task changed since this proposal was created. I did not save anything.")
                    return@launch
                }
                val correction = relativeTemporalAgent.processRelativeTemporalCorrection(
                    normalized,
                    correctionContext
                )
                if (!session.isCurrent(token)) return@launch
                if (!authoritativeTaskStillMatches()) {
                    session.cancel()
                    speak("That task changed while I was checking the correction. I did not save anything.")
                    return@launch
                }

                val revisionResult = when (correction) {
                    ValidatedRelativeTemporalCorrection.RestoreOriginal ->
                        session.restoreOriginal(token)
                    is ValidatedRelativeTemporalCorrection.Apply -> {
                        when (
                            val calculation = relativeTemporalCalculator.calculate(
                                authoritativeOriginal = session.authoritativeOriginal,
                                currentProposal = session.currentProposal,
                                proposal = correction.proposal,
                                now = Calendar.getInstance()
                            )
                        ) {
                            is RelativeTemporalCalculationResult.Success -> {
                                Log.d(
                                    "RELATIVE_TEMPORAL_CALCULATION",
                                    "result=SUCCESS " +
                                        "crossedDateBoundary=${calculation.crossedDateBoundary} " +
                                        "source=${calculation.source}"
                                )
                                initialProposalCrossedDateBoundary =
                                    calculation.crossedDateBoundary
                                session.applyCorrection(
                                    token,
                                    calculation.schedule,
                                    correction.proposal
                                )
                            }
                            is RelativeTemporalCalculationResult.PastSchedule -> {
                                Log.d(
                                    "RELATIVE_TEMPORAL_CALCULATION",
                                    "result=PAST " +
                                        "crossedDateBoundary=${calculation.crossedDateBoundary} " +
                                        "source=${calculation.source}"
                                )
                                waitingForSaveConfirmation = true
                                speak(RelativeTemporalSpeechRenderer.pastSchedule(calculation.schedule))
                                return@launch
                            }
                            is RelativeTemporalCalculationResult.Failure -> {
                                Log.d(
                                    "RELATIVE_TEMPORAL_CALCULATION",
                                    "result=REJECTED crossedDateBoundary=false " +
                                        "source=${calculation.source}"
                                )
                                waitingForSaveConfirmation = true
                                speak(
                                    RelativeTemporalSpeechRenderer.calculationClarification(
                                        calculation.reason
                                    )
                                )
                                return@launch
                            }
                        }
                    }
                }
                if (revisionResult != RelativeTemporalRevisionResult.APPLIED) return@launch
                setSelectedSchedule(session.currentProposal, synchronizeSession = false)
                initialProposalCrossedDateBoundary =
                    session.currentProposal.date != session.authoritativeOriginal.date
                logRelativeTemporalProposal("WAITING_CONFIRMATION")
                askToSaveChanges()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: TaskAgentProcessingException) {
                if (session.isCurrent(token)) {
                    waitingForSaveConfirmation = true
                    speak(RelativeTemporalSpeechRenderer.semanticClarification())
                }
            } finally {
                relativeTemporalCorrectionInFlight = false
            }
        }
    }

    private suspend fun authoritativeTaskStillMatches(): Boolean {
        val task = withContext(Dispatchers.IO) {
            AppDatabase.getInstance(this@EditTaskActivity).taskDao().getById(taskId)
        }
        return authoritativeSnapshotMatches(task)
    }

    private fun isRelativeCancellationCommand(normalized: String): Boolean =
        normalized in setOf(
            "cancel",
            "cancel that change",
            "cancel the change",
            "discard that change",
            "discard the change"
        )

    private fun isRelativeRepeatCommand(normalized: String): Boolean =
        normalized in setOf(
            "repeat",
            "repeat the proposal",
            "say the proposed time again",
            "say the proposal again",
            "what is the proposed time"
        )

    private fun speakCurrentRelativeTemporalProposal() {
        val session = relativeTemporalSession ?: return
        speak(
            RelativeTemporalSpeechRenderer.repeatedProposal(
                taskTitle = authoritativeOriginalTitle,
                schedule = session.currentProposal,
                crossedDateBoundary = initialProposalCrossedDateBoundary
            )
        )
    }

    private fun repeatPendingTemporalPrompt() {
        pendingTemporalClarification?.let {
            advanceTemporalClarification()
            return
        }
        when (pendingFieldTarget) {
            EditFieldTarget.DATE -> speak("Please provide the exact date first.")
            EditFieldTarget.TIME -> speak("Please provide the exact time first.")
            EditFieldTarget.TITLE -> speak(responseManager.askChangeTitle())
            EditFieldTarget.DATE_OR_TIME -> speak("What date or time would you like to use?")
            EditFieldTarget.NONE -> Unit
        }
    }

    private fun applyProposedTemporalChange(dateText: String?, timeText: String?, askForMissing: Boolean): Boolean {
        if (dateText.isNullOrBlank() && timeText.isNullOrBlank()) return false
        val resolution = temporalResolver.resolve(dateText, timeText, listOfNotNull(dateText, timeText).joinToString(" "))
        val policy = TemporalActionPolicy.evaluate(resolution, TemporalUseCase.RESCHEDULE)
        if (policy is TemporalPolicyResult.Unresolved || policy is TemporalPolicyResult.InvalidPastSchedule) return false
        return applyTemporalResolution(resolution, policy, askForMissing)
    }

    private fun applyTemporalResolution(
        resolution: TemporalResolution,
        policy: TemporalPolicyResult,
        askForMissing: Boolean
    ): Boolean {
        val needsDate = policy is TemporalPolicyResult.NeedsExactDate || policy is TemporalPolicyResult.NeedsExactDateAndTime
        val needsTime = policy is TemporalPolicyResult.NeedsExactTime || policy is TemporalPolicyResult.NeedsExactDateAndTime
        var updated = false
        if (resolution.isExactDate && resolution.startDateInclusive != null) {
            setExactDate(resolution.startDateInclusive)
            updated = true
        }
        if (resolution.isExactTime && resolution.startMinuteInclusive != null) {
            setExactMinute(resolution.startMinuteInclusive)
            updated = true
        }
        pendingTemporalConstraint = if (needsDate || needsTime) resolution else null
        pendingTemporalClarification = if (needsDate || needsTime) {
            PendingTemporalClarification(
                original = resolution,
                exactDate = if (resolution.isExactDate) resolution.startDateInclusive else null,
                exactMinute = if (resolution.isExactTime) resolution.startMinuteInclusive else null,
                needsExactDate = needsDate,
                needsExactTime = needsTime
            )
        } else null
        if (askForMissing && pendingTemporalClarification != null) {
            advanceTemporalClarification()
        }
        return updated || needsDate || needsTime
    }

    private fun handleOneSentenceTemporalCommand(normalized: String): Boolean {
        if (
            pendingFieldTarget != EditFieldTarget.NONE ||
            pendingTemporalClarification != null
        ) {
            return false
        }
        val command = EditTemporalCommandPolicy.resolve(
            normalizedText = normalized,
            resolver = temporalResolver
        )
        return when (command.disposition) {
            EditTemporalCommandDisposition.NOT_APPLICABLE -> false
            EditTemporalCommandDisposition.READY -> {
                waitingForSaveConfirmation = false
                val changed = applyTemporalResolution(
                    resolution = command.temporal,
                    policy = command.policy,
                    askForMissing = false
                )
                if (changed) {
                    pendingFieldTarget = EditFieldTarget.NONE
                    askToSaveChanges()
                } else {
                    enterTemporalCollection(command.target)
                }
                true
            }
            EditTemporalCommandDisposition.NEEDS_CLARIFICATION -> {
                waitingForSaveConfirmation = false
                applyTemporalResolution(
                    resolution = command.temporal,
                    policy = command.policy,
                    askForMissing = true
                )
                true
            }
            EditTemporalCommandDisposition.UNRESOLVED -> {
                waitingForSaveConfirmation = false
                enterTemporalCollection(command.target)
                true
            }
        }
    }

    private fun enterTemporalCollection(target: EditTemporalTarget) {
        pendingFieldTarget = when (target) {
            EditTemporalTarget.DATE -> EditFieldTarget.DATE
            EditTemporalTarget.TIME -> EditFieldTarget.TIME
            EditTemporalTarget.DATE_OR_TIME -> EditFieldTarget.DATE_OR_TIME
        }
        val prompt = when (target) {
            EditTemporalTarget.DATE -> "What exact date would you like to use?"
            EditTemporalTarget.TIME -> "What exact time would you like to use?"
            EditTemporalTarget.DATE_OR_TIME -> "What date or time would you like to use?"
        }
        speak(prompt)
    }

    private fun processEditCommand(cmd: AiParsedCommand) {
        when (cmd.intent) {
            AiIntent.UPDATE_TASK.name,
            AiIntent.RESCHEDULE_TASK.name -> {
                var updated = false

                if (!cmd.taskTitle.isNullOrBlank()) {
                    etTaskTitle.setText(cmd.taskTitle)
                    updated = true
                }

                if (applyProposedTemporalChange(cmd.newDateText ?: cmd.dateText, cmd.newTimeText ?: cmd.timeText, askForMissing = true)) {
                    updated = true
                }

                if (updated && pendingFieldTarget == EditFieldTarget.NONE && pendingTemporalClarification == null) {
                    askToSaveChanges()
                } else if (!updated && pendingFieldTarget == EditFieldTarget.NONE && pendingTemporalClarification == null) {
                    speak(responseManager.editHelp())
                }
            }

            AiIntent.DELETE_TASK.name -> {
                speak(responseManager.editDeleteCurrent())
                lifecycleScope.launch {
                    val dao = AppDatabase.getInstance(this@EditTaskActivity).taskDao()
                    withContext(Dispatchers.IO) {
                        dao.deleteTaskAndSubtasks(taskId)
                    }
                    ReminderHelper.cancelReminder(this@EditTaskActivity, taskId)
                    assistantSession.dismissPanel()
                    finish()
                }
            }

            AiIntent.QUERY_TASK.name -> {
                speak(responseManager.editHelp())
            }

            AiIntent.CREATE_TASK.name -> {
                speak(responseManager.editContextReminder())
            }

            else -> {
                speak(responseManager.editHelp())
            }
        }
    }

    private fun speak(text: String) {
        assistantSession.speak(text, listenAgain = true)
    }

    private fun readInitialRelativeTemporalSemanticProposal(): RelativeTemporalProposal? {
        if (!intent.hasExtra("relative_temporal_date_operation") ||
            !intent.hasExtra("relative_temporal_time_operation") ||
            !intent.hasExtra("relative_temporal_base")
        ) return null
        return runCatching {
            RelativeTemporalProposalValidator().validate(
                RelativeTemporalProposal(
                    dateOperation = RelativeTemporalOperation.valueOf(
                        requireNotNull(intent.getStringExtra("relative_temporal_date_operation"))
                    ),
                    timeOperation = RelativeTemporalOperation.valueOf(
                        requireNotNull(intent.getStringExtra("relative_temporal_time_operation"))
                    ),
                    relativeBase = RelativeTemporalBase.valueOf(
                        requireNotNull(intent.getStringExtra("relative_temporal_base"))
                    ),
                    replacementDateText =
                        intent.getStringExtra("relative_temporal_replacement_date").orEmpty(),
                    replacementTimeText =
                        intent.getStringExtra("relative_temporal_replacement_time").orEmpty(),
                    dateOffsetDays = intent.getIntExtra("relative_temporal_date_offset_days", 0),
                    timeOffsetMinutes =
                        intent.getIntExtra("relative_temporal_time_offset_minutes", 0),
                    confidence = intent.getDoubleExtra("relative_temporal_confidence", 0.0),
                    needClarification = false
                )
            )
        }.getOrNull()
    }

    private fun handleListenFailure(reply: String) {
        assistantSession.handleListenFailure(reply)
    }

    private fun endAssistantConversation() {
        waitingForSaveConfirmation = false
        pendingFieldTarget = EditFieldTarget.NONE
        assistantSession.getBottomSheet()?.clearHint()
        assistantSession.speakThenStop(responseManager.stopListening())
    }

    private fun isConversationExitCommand(normalized: String): Boolean {
        val taskActionHints = listOf(
            "mark ",
            " as done",
            "complete ",
            "completed",
            "delete ",
            "remove ",
            "edit ",
            "update ",
            "reschedule",
            "break down",
            "remind me",
            "create ",
            "what task",
            "what tasks",
            "show task",
            "show tasks"
        )

        if (taskActionHints.any { normalized.contains(it) }) {
            return false
        }

        val exactExitCommands = setOf(
            "nothing else",
            "that's all",
            "thats all",
            "goodbye",
            "bye",
            "stop",
            "cancel",
            "no thanks",
            "thank you",
            "thanks",
            "exit",
            "quit",
            "close",
            "end",
            "stop listening",
            "done",
            "i'm done",
            "im done",
            "all done",
            "finished",
            "that's it",
            "thats it"
        )

        return normalized in exactExitCommands
    }

    private fun applySpokenDate(dateText: String, replacingConstraint: Boolean = false): Boolean {
        val resolution = temporalResolver.resolve(dateText, null, dateText)
        if (!resolution.isExactDate || resolution.startDateInclusive == null) return false
        return acceptExactDate(resolution.startDateInclusive, replacingConstraint)
    }

    private fun acceptExactDate(date: String, replacingConstraint: Boolean): Boolean {
        val constraint = if (replacingConstraint) null else pendingTemporalClarification?.original ?: pendingTemporalConstraint
        if (constraint != null && !TemporalActionPolicy.validateClarification(constraint, date, null)) return false
        setExactDate(date)
        pendingTemporalClarification = pendingTemporalClarification?.copy(exactDate = date)
        advanceTemporalClarification()
        return true
    }

    private fun setExactDate(date: String) {
        selectedDate = date
        val parts = selectedDate!!.split("/")
        selectedDay = parts.getOrNull(0)?.toIntOrNull()
        selectedMonth = parts.getOrNull(1)?.toIntOrNull()?.minus(1)
        selectedYear = parts.getOrNull(2)?.toIntOrNull()
        tvSelectedDate.text = "Selected date: $selectedDate"
        updateDateAccessibilityState()
        synchronizeRelativeProposalFromUi()
    }

    private fun applySpokenTime(timeText: String, replacingConstraint: Boolean = false): Boolean {
        val resolution = temporalResolver.resolve(null, timeText, timeText)
        if (!resolution.isExactTime || resolution.startMinuteInclusive == null) return false
        return acceptExactMinute(resolution.startMinuteInclusive, replacingConstraint)
    }

    private fun acceptExactMinute(minute: Int, replacingConstraint: Boolean): Boolean {
        val constraint = if (replacingConstraint) null else pendingTemporalClarification?.original ?: pendingTemporalConstraint
        if (constraint != null && !TemporalActionPolicy.validateClarification(constraint, null, minute)) return false
        setExactMinute(minute)
        pendingTemporalClarification = pendingTemporalClarification?.copy(exactMinute = minute)
        advanceTemporalClarification()
        return true
    }

    private fun setExactMinute(minute: Int) {
        selectedHour24 = minute / 60
        selectedMinute = minute % 60
        selectedTime = formatTime(selectedHour24!!, selectedMinute!!)
        tvSelectedTime.text = "Selected time: $selectedTime"
        updateTimeAccessibilityState()
        synchronizeRelativeProposalFromUi()
    }

    private fun setSelectedSchedule(
        schedule: ExactTemporalSchedule,
        synchronizeSession: Boolean
    ) {
        selectedDate = schedule.date
        selectedTime = schedule.time
        selectedYear = null
        selectedMonth = null
        selectedDay = null
        selectedHour24 = null
        selectedMinute = null
        parseExistingDate(selectedDate)
        parseExistingTime(selectedTime)
        tvSelectedDate.text = "Selected date: ${selectedDate ?: "No date selected"}"
        tvSelectedTime.text = "Selected time: ${selectedTime ?: "No time selected"}"
        updateScheduleAccessibilityState()
        if (synchronizeSession) synchronizeRelativeProposalFromUi()
    }

    private fun synchronizeRelativeProposalFromUi() {
        val session = relativeTemporalSession ?: return
        if (session.state != RelativeTemporalProposalState.ACTIVE) return
        session.replaceFromManualEdit(
            ExactTemporalSchedule(selectedDate, selectedTime)
        )
        initialProposalCrossedDateBoundary = selectedDate != authoritativeOriginalDate
        logRelativeTemporalProposal("WAITING_CONFIRMATION")
    }

    private fun advanceTemporalClarification() {
        val pending = pendingTemporalClarification ?: return
        if (pending.needsExactDate && pending.exactDate == null) {
            pendingFieldTarget = EditFieldTarget.DATE
            waitingForSaveConfirmation = false
            speak("Which exact date ${pending.original.originalDatePhrase.ifBlank { pending.original.spokenLabel }}?")
            return
        }
        if (pending.needsExactTime && pending.exactMinute == null) {
            pendingFieldTarget = EditFieldTarget.TIME
            waitingForSaveConfirmation = false
            speak("What exact time ${pending.original.originalTimePhrase.ifBlank { pending.original.spokenLabel }}?")
            return
        }
        pendingFieldTarget = EditFieldTarget.NONE
        pendingTemporalClarification = null
        pendingTemporalConstraint = null
        askToSaveChanges()
    }

    private fun parseExistingDate(date: String?) {
        if (date.isNullOrBlank()) return

        try {
            val parts = date.split("/")
            if (parts.size == 3) {
                selectedDay = parts[0].toInt()
                selectedMonth = parts[1].toInt() - 1
                selectedYear = parts[2].toInt()
            }
        } catch (_: Exception) {
        }
    }

    private fun parseExistingTime(time: String?) {
        if (time.isNullOrBlank()) return

        try {
            val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val parsed = formatter.parse(time) ?: return

            val calendar = Calendar.getInstance()
            calendar.time = parsed

            selectedHour24 = calendar.get(Calendar.HOUR_OF_DAY)
            selectedMinute = calendar.get(Calendar.MINUTE)
        } catch (_: Exception) {
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

    private fun handlePendingFieldValue(normalized: String): Boolean {
        return when (pendingFieldTarget) {
            EditFieldTarget.TITLE -> {
                if (normalized.isBlank()) {
                    speak(responseManager.invalidEditTitle())
                } else {
                    etTaskTitle.setText(normalized)
                    pendingFieldTarget = EditFieldTarget.NONE
                    askToSaveChanges()
                }
                true
            }

            EditFieldTarget.DATE -> {
                val wasTemporalClarification = pendingTemporalClarification != null
                if (applySpokenDate(normalized)) {
                    if (!wasTemporalClarification) {
                        pendingFieldTarget = EditFieldTarget.NONE
                        askToSaveChanges()
                    }
                } else {
                    speak(responseManager.invalidEditDate())
                }
                true
            }

            EditFieldTarget.TIME -> {
                val wasTemporalClarification = pendingTemporalClarification != null
                val parsed = applySpokenTime(normalized)
                if (BuildConfig.DEBUG) {
                    Log.d("EDIT_TIME", "raw='$normalized' parsed=$parsed")
                }
                if (parsed) {
                    if (!wasTemporalClarification) {
                        pendingFieldTarget = EditFieldTarget.NONE
                        askToSaveChanges()
                    }
                } else {
                    speak(responseManager.invalidEditTime())
                }
                true
            }

            EditFieldTarget.DATE_OR_TIME -> {
                val changed = applyProposedTemporalChange(
                    dateText = normalized,
                    timeText = null,
                    askForMissing = true
                )
                if (changed && pendingTemporalClarification == null) {
                    pendingFieldTarget = EditFieldTarget.NONE
                    askToSaveChanges()
                } else if (!changed) {
                    speak("Please provide an exact date, an exact time, or both.")
                }
                true
            }

            EditFieldTarget.NONE -> false
        }
    }

    private fun isDateFieldCommand(normalized: String): Boolean {
        return normalized == "date" ||
                normalized == "the date" ||
                normalized == "change date" ||
                normalized == "edit date"
    }

    private fun isTimeFieldCommand(normalized: String): Boolean {
        return normalized == "time" ||
                normalized == "the time" ||
                normalized == "change time" ||
                normalized == "edit time"
    }

    private fun isTitleFieldCommand(normalized: String): Boolean {
        return normalized == "title" ||
                normalized == "the title" ||
                normalized == "change title" ||
                normalized == "edit title" ||
                normalized == "rename"
    }

    private fun isYes(normalized: String): Boolean {
        val value = normalized.trim().lowercase()
        return value == "yes" ||
                value == "yes yes" ||
                value == "yeah" ||
                value == "yep" ||
                value == "save" ||
                value == "save it" ||
                value == "safe" ||
                value == "okay" ||
                value == "ok"
    }

    private fun isNo(normalized: String): Boolean {
        val value = normalized.trim().lowercase()
        return value == "no" ||
                value == "no no" ||
                value == "nope" ||
                value == "not yet" ||
                value == "don't save" ||
                value == "do not save"
    }
    private fun buildEditSummary(): String {
        val title = etTaskTitle.text.toString().trim().ifBlank { "Untitled task" }
        val date = selectedDate ?: "no date"
        val time = selectedTime ?: "no time"
        return "$title, $date, $time"
    }

    private fun askToSaveChanges() {
        if (ignoreInputWhileRelativeTemporalSaveIsInFlight()) return
        if (pendingFieldTarget != EditFieldTarget.NONE || pendingTemporalClarification != null) {
            repeatPendingTemporalPrompt()
            return
        }
        waitingForSaveConfirmation = true
        assistantSession.expectConfirmation()
        val session = relativeTemporalSession
        if (session != null && session.state == RelativeTemporalProposalState.ACTIVE) {
            promptHelper.speakInfo(
                RelativeTemporalSpeechRenderer.proposal(
                    taskTitle = authoritativeOriginalTitle,
                    schedule = session.currentProposal,
                    crossedDateBoundary = initialProposalCrossedDateBoundary
                ),
                listenAgain = true,
                hint = responseManager.hintYesNo()
            )
        } else {
            promptHelper.askSaveChanges(buildEditSummary())
        }
    }

    private fun authoritativeSnapshotMatches(task: TaskEntity?): Boolean =
        task != null &&
            task.id == taskId &&
            task.title == authoritativeOriginalTitle &&
            task.dueDate == authoritativeOriginalDate &&
            task.dueTime == authoritativeOriginalTime &&
            task.isDone == authoritativeOriginalIsDone

    private fun authoritativeSnapshotMatches(
        task: TaskEntity?,
        expectedTaskId: Long,
        expectedTitle: String,
        expectedDate: String?,
        expectedTime: String?,
        expectedIsDone: Boolean
    ): Boolean = task != null &&
        task.id == expectedTaskId &&
        task.title == expectedTitle &&
        task.dueDate == expectedDate &&
        task.dueTime == expectedTime &&
        task.isDone == expectedIsDone

    private fun logRelativeTemporalProposal(state: String) {
        val session = relativeTemporalSession ?: return
        Log.d(
            "RELATIVE_TEMPORAL_PROPOSAL",
            "revision=${session.revision} state=$state " +
                "hasDateChange=${session.currentProposal.date != authoritativeOriginalDate} " +
                "hasTimeChange=${session.currentProposal.time != authoritativeOriginalTime}"
        )
    }

    private fun showTypedAssistantInputDialog() {
        if (ignoreInputWhileRelativeTemporalSaveIsInFlight()) return
        AccessibleAssistantInputDialog.show(
            activity = this,
            title = "Type assistant response",
            message = "Typed and voice corrections use the same assistant flow.",
            emptyError = "Please type a response",
            onCancel = assistantSession::onTypedInputCancelled
        ) { typedText ->
            assistantSession.submitTypedText(typedText, clearConversation = false)
        }
    }

    override fun onAssistantTypedInputRequested() {
        showTypedAssistantInputDialog()
    }

    private fun updateScheduleAccessibilityState() {
        updateDateAccessibilityState()
        updateTimeAccessibilityState()
    }

    private fun updateDateAccessibilityState() {
        dateInfoGroup.contentDescription = "Selected date"
        AccessibilityStateHelper.updateStateDescription(
            dateInfoGroup,
            TaskCardAccessibilitySemantics.spokenDate(selectedDate)
                .replace("No date set", "No date selected")
        )
    }

    private fun updateTimeAccessibilityState() {
        timeInfoGroup.contentDescription = "Selected time"
        AccessibilityStateHelper.updateStateDescription(
            timeInfoGroup,
            TaskCardAccessibilitySemantics.spokenTime(selectedTime)
                .replace("No time set", "No time selected")
        )
    }

    private fun announceManualScheduleChange(view: View, event: String, value: String) {
        if (AccessibilityStateHelper.isScreenReaderActive(view)) {
            AccessibilityAnnouncementHelper.announce(
                view,
                screen = "EDIT_TASK",
                event = event,
                message = value
            )
            view.postDelayed({ askToSaveChanges() }, 1200)
        } else {
            askToSaveChanges()
        }
    }

    private fun isSaveCommand(normalized: String): Boolean {
        val value = normalized.trim().lowercase()
        return value == "save" ||
                value == "save changes" ||
                value == "save it" ||
                value == "safe" ||
                value == "okay save" ||
                value == "ok save"
    }

    override fun onAssistantFinalText(text: String) {
        handleVoiceInput(text)
    }

    override fun onAssistantCancelled() {
        waitingForSaveConfirmation = false
        pendingFieldTarget = EditFieldTarget.NONE
        relativeTemporalSession?.invalidatePendingCorrection()
        isForceStoppingAssistant = false
    }

    override fun onAssistantSessionStopped() {
        waitingForSaveConfirmation = false
        pendingFieldTarget = EditFieldTarget.NONE
        relativeTemporalSession?.invalidatePendingCorrection()
        isForceStoppingAssistant = false
    }

    override fun onDestroy() {
        assistantSession.destroy()
        voiceHelper.shutdown()
        super.onDestroy()
    }
}
