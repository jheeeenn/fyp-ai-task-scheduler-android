package com.example.myapplication


import android.app.DatePickerDialog
import android.app.TimePickerDialog

import android.os.Bundle
import android.util.Log

import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

import androidx.lifecycle.lifecycleScope
import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.AiParsedCommand
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.TaskEntity
import com.example.myapplication.voice.AssistantPromptHelper
import com.example.myapplication.voice.AssistantResponseManager
import com.example.myapplication.voice.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.example.myapplication.voice.AssistantVoiceHost
import com.example.myapplication.voice.AssistantVoiceSession

import com.example.myapplication.ai.temporal.TemporalActionPolicy
import com.example.myapplication.ai.temporal.TemporalExpressionResolver
import com.example.myapplication.ai.temporal.TemporalResolution

private enum class EditFieldTarget {
    NONE, TITLE, DATE, TIME
}


class EditTaskActivity : AppCompatActivity(), AssistantVoiceHost {
    private lateinit var promptHelper: AssistantPromptHelper
    private lateinit var assistantSession: AssistantVoiceSession
    private var isForceStoppingAssistant = false
    private var pendingFieldTarget = EditFieldTarget.NONE
    private var waitingForSaveConfirmation = false
    private var assistantMode: String? = null
    private val temporalResolver = TemporalExpressionResolver()
    private var pendingTemporalConstraint: TemporalResolution? = null

    private lateinit var voiceHelper: VoiceHelper

    private lateinit var responseManager: AssistantResponseManager






    private lateinit var etTaskTitle: EditText
    private lateinit var tvSelectedDate: TextView
    private lateinit var tvSelectedTime: TextView

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

        etTaskTitle = findViewById(R.id.etTaskTitle)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        tvSelectedTime = findViewById(R.id.tvSelectedTime)

        val btnPickDate = findViewById<Button>(R.id.btnPickDate)
        val btnPickTime = findViewById<Button>(R.id.btnPickTime)
        val btnSaveTask = findViewById<Button>(R.id.btnSaveTask)
        val btnDeleteTask = findViewById<Button>(R.id.btnDeleteTask)
        val btnCancelTask = findViewById<Button>(R.id.btnCancelTask)
        val btnGoHome = findViewById<Button>(R.id.btnGoHome)
        val btnTalkAssistant = findViewById<Button>(R.id.btnTalkAssistant)

        voiceHelper = VoiceHelper(this)
        responseManager = AssistantResponseManager.fromPreferences(this)


        assistantSession = AssistantVoiceSession(
            activity = this,
            host = this,
            voiceHelper = voiceHelper,
            responseManager = responseManager,
            audioPermissionLauncher = audioPermissionLauncher
        )

        promptHelper = AssistantPromptHelper(assistantSession, responseManager)

        taskId = intent.getLongExtra("task_id", -1L)
        val originalTitle = intent.getStringExtra("task_title") ?: ""
        val originalDate = intent.getStringExtra("task_date")
        val originalTime = intent.getStringExtra("task_time")

        etTaskTitle.setText(originalTitle)
        selectedDate = originalDate
        selectedTime = originalTime

        tvSelectedDate.text = "Selected date: ${originalDate ?: "No date selected"}"
        tvSelectedTime.text = "Selected time: ${originalTime ?: "No time selected"}"

        parseExistingDate(originalDate)
        parseExistingTime(originalTime)

        assistantMode = intent.getStringExtra("assistant_mode")

        val prefillNewDateText = intent.getStringExtra("prefill_new_date_text")
        val prefillNewTimeText = intent.getStringExtra("prefill_new_time_text")

        applyProposedTemporalChange(prefillNewDateText, prefillNewTimeText, askForMissing = true)

        btnPickDate.setOnClickListenerWithHaptic { openDatePicker() }
        btnPickTime.setOnClickListenerWithHaptic { openTimePicker() }
        btnSaveTask.setOnClickListenerWithHaptic { saveTask() }
        btnDeleteTask.setOnClickListenerWithHaptic { confirmDeleteTask() }


        btnCancelTask.setOnClickListenerWithHaptic {
            assistantSession.speakThenRun(responseManager.cancelEdit()) {
                finish()
            }
        }

        btnGoHome.setOnClickListenerWithHaptic {
            assistantSession.speakThenRun(responseManager.returnHomeFromEdit()) {
                finish()
            }
        }

        btnTalkAssistant.setOnClickListenerWithHaptic {
            assistantSession.startSession()
        }



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
            val introReply = if (assistantMode == "reschedule") {
                waitingForSaveConfirmation = true
                responseManager.rescheduleIntro(
                    title = etTaskTitle.text.toString(),
                    changedDate = !prefillNewDateText.isNullOrBlank(),
                    changedTime = !prefillNewTimeText.isNullOrBlank()
                )
            } else {
                responseManager.editIntro(etTaskTitle.text.toString())
            }

            window.decorView.postDelayed({
                assistantSession.speak(
                    text = introReply,
                    listenAgain = true
                )
            }, 350)
        }
    }

    private fun openDatePicker() {
        val calendar = Calendar.getInstance()
        val year = selectedYear ?: calendar.get(Calendar.YEAR)
        val month = selectedMonth ?: calendar.get(Calendar.MONTH)
        val day = selectedDay ?: calendar.get(Calendar.DAY_OF_MONTH)

        val dialog = DatePickerDialog(
            this,
            { _, pickedYear, pickedMonth, pickedDay ->
                selectedYear = pickedYear
                selectedMonth = pickedMonth
                selectedDay = pickedDay
                selectedDate = formatDate(pickedYear, pickedMonth, pickedDay)
                tvSelectedDate.text = "Selected date: $selectedDate"
            },
            year,
            month,
            day
        )
        dialog.datePicker.minDate = System.currentTimeMillis() - 1000
        dialog.show()
    }

    private fun openTimePicker() {
        val calendar = Calendar.getInstance()
        val hour = selectedHour24 ?: calendar.get(Calendar.HOUR_OF_DAY)
        val minute = selectedMinute ?: calendar.get(Calendar.MINUTE)

        val dialog = TimePickerDialog(
            this,
            { _, pickedHour, pickedMinute ->
                selectedHour24 = pickedHour
                selectedMinute = pickedMinute
                selectedTime = formatTime(pickedHour, pickedMinute)
                tvSelectedTime.text = "Selected time: $selectedTime"
            },
            hour,
            minute,
            false
        )
        dialog.show()
    }

    private fun saveTask() {
        val newTitle = etTaskTitle.text.toString().trim()

        if (newTitle.isEmpty()) {
            etTaskTitle.error = "Task title cannot be empty"
            etTaskTitle.requestFocus()
            return
        }

        val dao = AppDatabase.getInstance(this).taskDao()

        lifecycleScope.launch {
            val existingTask = withContext(Dispatchers.IO) {
                dao.getById(taskId)
            }

            ReminderHelper.cancelReminder(this@EditTaskActivity, taskId.toInt())

            withContext(Dispatchers.IO) {
                dao.updateTask(taskId, newTitle, selectedDate, selectedTime)

                if (existingTask?.parentTaskId == null) {
                    dao.updateSubtasksSchedule(
                        parentTaskId = taskId,
                        dueDate = selectedDate,
                        dueTime = selectedTime
                    )
                }
            }

            val updatedTask = TaskEntity(
                id = taskId,
                title = newTitle,
                dueDate = selectedDate,
                dueTime = selectedTime,
                isDone = false,
                parentTaskId = existingTask?.parentTaskId,
                subtaskOrder = existingTask?.subtaskOrder ?: 0
            )

            val scheduled = if (updatedTask.parentTaskId == null) {
                ReminderHelper.scheduleReminderFromTask(
                    this@EditTaskActivity,
                    updatedTask
                )
            } else {
                true
            }

            if (selectedDate != null && selectedTime != null) {
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

    private fun confirmDeleteTask() {
        val dao = AppDatabase.getInstance(this).taskDao()

        AlertDialog.Builder(this)
            .setTitle("Delete task?")
            .setMessage("Are you sure you want to delete this task?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        dao.deleteTaskAndSubtasks(taskId)
                    }
                    ReminderHelper.cancelReminder(this@EditTaskActivity, taskId.toInt())
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
        val normalized = TextNormalizer.normalize(text)

        if (isSaveCommand(normalized)) {
            waitingForSaveConfirmation = false
            assistantSession.dismissPanel()
            saveTask()
            return
        }

        if (isConversationExitCommand(normalized)) {
            endAssistantConversation()
            return
        }

        if (waitingForSaveConfirmation) {
            when {
                isYes(normalized) -> {
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

                applySpokenDate(normalized) -> {
                    waitingForSaveConfirmation = false
                    askToSaveChanges()
                    return
                }

                applySpokenTime(normalized) -> {
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
                    ReminderHelper.cancelReminder(this@EditTaskActivity, taskId.toInt())
                    assistantSession.dismissPanel()
                    finish()
                }
                return
            }
        }


        speak("Please return to the main assistant for a new command, or choose title, date, time, or delete for this task.")
    }

    private fun applyProposedTemporalChange(dateText: String?, timeText: String?, askForMissing: Boolean): Boolean {
        if (dateText.isNullOrBlank() && timeText.isNullOrBlank()) return false
        val resolution = temporalResolver.resolve(dateText, timeText, listOfNotNull(dateText, timeText).joinToString(" "))
        pendingTemporalConstraint = resolution
        var updated = false
        if (resolution.isExactDate && resolution.startDateInclusive != null) {
            applySpokenDate(resolution.startDateInclusive)
            updated = true
        } else if (!dateText.isNullOrBlank() && askForMissing) {
            pendingFieldTarget = EditFieldTarget.DATE
            speak("Which exact date ${resolution.originalDatePhrase.ifBlank { dateText }}?")
        }
        if (resolution.isExactTime && resolution.startMinuteInclusive != null) {
            applySpokenTime(timeText ?: resolution.spokenLabel)
            updated = true
        } else if (!timeText.isNullOrBlank() && askForMissing && pendingFieldTarget == EditFieldTarget.NONE) {
            pendingFieldTarget = EditFieldTarget.TIME
            speak("What exact time ${resolution.originalTimePhrase.ifBlank { timeText }}?")
        }
        return updated
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

                if (updated && pendingFieldTarget == EditFieldTarget.NONE) {
                    askToSaveChanges()
                } else if (!updated && pendingFieldTarget == EditFieldTarget.NONE) {
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
                    ReminderHelper.cancelReminder(this@EditTaskActivity, taskId.toInt())
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
        selectedTime = formatTime(minute / 60, minute % 60)
        tvSelectedTime.text = "Selected time: $selectedTime"
        return true
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
                if (applySpokenDate(normalized)) {
                    pendingFieldTarget = EditFieldTarget.NONE
                    askToSaveChanges()
                } else {
                    speak(responseManager.invalidEditDate())
                }
                true
            }

            EditFieldTarget.TIME -> {
                val parsed = applySpokenTime(normalized)
                Log.d("EDIT_TIME", "raw='$normalized' parsed=$parsed")
                if (parsed) {
                    pendingFieldTarget = EditFieldTarget.NONE
                    askToSaveChanges()
                } else {
                    speak(responseManager.invalidEditTime())
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
        waitingForSaveConfirmation = true
        promptHelper.askSaveChanges(buildEditSummary())
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
        isForceStoppingAssistant = false
    }

    override fun onAssistantSessionStopped() {
        waitingForSaveConfirmation = false
        pendingFieldTarget = EditFieldTarget.NONE
        isForceStoppingAssistant = false
    }

    override fun onDestroy() {
        assistantSession.destroy()
        voiceHelper.shutdown()
        super.onDestroy()
    }
}