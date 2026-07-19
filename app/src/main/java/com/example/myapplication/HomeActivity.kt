package com.example.myapplication

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings

import android.text.InputType
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale




import com.example.myapplication.voice.TextNormalizer

import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.LocalConversationIntentClassifier
import com.example.myapplication.ai.agent.ActionValidator
import com.example.myapplication.ai.agent.AgentOrchestrator
import com.example.myapplication.ai.agent.LaptopAgentClient
import com.example.myapplication.ai.agent.TaskActionNormalizer
import com.example.myapplication.ai.agent.TaskAgentResponseParser
import com.example.myapplication.ai.agent.TaskAgentProcessingException
import com.example.myapplication.ai.conversation.ConversationAgentClient
import com.example.myapplication.ai.conversation.ConversationDecisionParser
import com.example.myapplication.ai.conversation.ConversationOrchestrator
import com.example.myapplication.ai.conversation.ConversationOrchestratorException
import com.example.myapplication.ai.conversation.ConversationRoute
import com.example.myapplication.ai.conversation.AllowedUserMove
import com.example.myapplication.ai.conversation.AndroidObservationResponseRenderer
import com.example.myapplication.ai.conversation.ConversationResponse
import com.example.myapplication.ai.conversation.ExecutionObservation
import com.example.myapplication.ai.conversation.ExecutionOperation
import com.example.myapplication.ai.conversation.ExecutionOutcome
import com.example.myapplication.ai.conversation.ObservedTask
import com.example.myapplication.ai.conversation.RequiredInput
import com.example.myapplication.ai.conversation.TaskObservationMapper
import com.example.myapplication.ai.conversation.TemporalObservationInputs
import com.example.myapplication.voice.AssistantResponseManager
import com.example.myapplication.voice.QueryDetailMode
import java.text.SimpleDateFormat

import com.example.myapplication.voice.AssistantVoiceHost
import com.example.myapplication.voice.AssistantVoiceSession

import com.example.myapplication.ai.ConversationIntent
import com.example.myapplication.ai.TaskMatcher

import com.example.myapplication.ai.TaskResolutionState
import com.example.myapplication.ai.PendingTaskAction

import com.example.myapplication.data.TaskEntity
import com.example.myapplication.ai.temporal.TaskTemporalFilter
import com.example.myapplication.ai.temporal.TaskCompletionFilter
import com.example.myapplication.ai.temporal.TemporalQueryLabelFormatter
import com.example.myapplication.ai.temporal.TemporalQueryResolver
import com.example.myapplication.ai.temporal.TemporalQueryWindow
import com.example.myapplication.ai.temporal.TemporalResolutionStatus
import com.example.myapplication.ai.temporal.PendingTemporalClarification
import com.example.myapplication.ai.temporal.TemporalActionPolicy
import com.example.myapplication.ai.temporal.TemporalPolicyResult
import com.example.myapplication.ai.temporal.TemporalUseCase
class HomeActivity : AppCompatActivity(), AssistantVoiceHost{
    private var shouldOpenAssistantOnResume = false
    private lateinit var conversationIntentClassifier: LocalConversationIntentClassifier

    private lateinit var responseManager: AssistantResponseManager
    private lateinit var agentOrchestrator: AgentOrchestrator
    private lateinit var conversationOrchestrator: ConversationOrchestrator


    private lateinit var assistantSession: AssistantVoiceSession
    private lateinit var voiceHelper: VoiceHelper

    private enum class QueryReplyMode {
        SHORT,
        NORMAL,
        DETAILED
    }

    private enum class HomeFollowUpContext {
        NONE,
        AFTER_NO_TASKS,
        AFTER_TASK_SUMMARY,
        AFTER_TASK_DETAILS,
        TASK_MATCH_AMBIGUITY,
        DELETE_CONFIRMATION,
        BREAKDOWN_CONFIRMATION,
        BREAKDOWN_SCHEDULE_COLLECTION
    }
    private var taskResolutionState = TaskResolutionState()
    private var homeFollowUpContext = HomeFollowUpContext.NONE
    private var lastQueryWasToday = false
    private var lastQueryDate: String? = null
    private val temporalQueryResolver = TemporalQueryResolver()
    private var lastQueryWindow: TemporalQueryWindow = TemporalQueryWindow(TemporalResolutionStatus.NONE)

    //for delete confirmation when the task intent is 'delete'
    private var pendingDeleteTaskId: Long? = null
    private var pendingDeleteTaskTitle: String? = null

    // for AI breakdown planning
    private var pendingBreakdownTitle: String? = null
    private var pendingBreakdownPlan: List<String> = emptyList()
    private var pendingBreakdownOriginalRequest: String? = null
    private var pendingBreakdownDateText: String? = null
    private var pendingBreakdownTimeText: String? = null
    private var pendingBreakdownTemporalClarification: PendingTemporalClarification? = null
    private var currentSubtasksByParentId: Map<Long, List<TaskEntity>> = emptyMap()

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                assistantSession.onAudioPermissionGranted()
            } else {
                assistantSession.onAudioPermissionDenied()
            }
        }
    private var hasShownPermissionDialog = false
    private var notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()){ granted->
            if(granted){
                checkExactAlarmPermission()
            }else{
                showPermissionDeniedDialog(
                    "Notification permission is needed so task reminders can appear on your device ")
            }
        }

    private val exactAlarmSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            checkExactAlarmPermissionAfterReturn()
        }

    private var ambiguityRetryCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        agentOrchestrator = AgentOrchestrator(
            LaptopAgentClient(this),
            TaskAgentResponseParser(),
            TaskActionNormalizer(),
            ActionValidator()
        )
        conversationOrchestrator = ConversationOrchestrator(
            ConversationAgentClient(this),
            ConversationDecisionParser()
        )
        conversationIntentClassifier = LocalConversationIntentClassifier(this)

        val greetingText = findViewById<TextView>(R.id.greetingText)
        val overviewText = findViewById<TextView>(R.id.overviewText)

        // declaring btns
        val btnTodayTasks = findViewById<Button>(R.id.btnTodayTasks)
        val btnCreateTask = findViewById<Button>(R.id.btnCreateTask)
        val btnScheduledTasks = findViewById<Button>(R.id.btnScheduledTasks)
        val btnTalkAssistant = findViewById<Button>(R.id.btnTalkAssistant)
        val btnSettings = findViewById<Button>(R.id.btnSettings)

        // Greeting
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good Morning ^-^"
            hour < 18 -> "Good Afternoon ;)"
            else -> "Good Evening _zZZ"
        }
        greetingText.text = greeting


        // Navigation buttons
        btnTodayTasks.setOnClickListenerWithHaptic {
            //startActivity(Intent(this, TodayTasksActivity::class.java))
            speakThenOpen("opening today's task.") {
                startActivity(Intent(this, TodayTasksActivity::class.java))
            }
        }
        btnCreateTask.setOnClickListenerWithHaptic {
            speakThenOpen("opening task create.") {
                startActivity(Intent(this, CreateTaskActivity::class.java))
            }
        }

        btnScheduledTasks.setOnClickListenerWithHaptic {
            speakThenOpen("opening scheduled task.") {
                startActivity(Intent(this, MainActivity::class.java))
            }
        }

        btnSettings.setOnClickListenerWithHaptic {
            speakThenOpen("opening settings.") {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }

        // Notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )!= PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        voiceHelper = VoiceHelper(this)
        responseManager= AssistantResponseManager.fromPreferences(this)

        assistantSession = AssistantVoiceSession(
            activity = this,
            host = this,
            voiceHelper = voiceHelper,
            responseManager = responseManager,
            audioPermissionLauncher = audioPermissionLauncher
        )



        btnTalkAssistant.setOnClickListenerWithHaptic {
            assistantSession.startSession()
        }

        btnTalkAssistant.setOnLongClickListener {
            btnTalkAssistant.performLongClickHapticFeedback()
            showTypedAssistantInputDialog()
            true
        }

        // to open the assistant from today task and scheduled task pages
        shouldOpenAssistantOnResume = intent.getBooleanExtra("open_assistant_on_arrival", false)


    } // end of onCreate

    private fun showTypedAssistantInputDialog() {
        val input = EditText(this).apply {
            hint = "Type what you would say to the assistant"
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND
            minLines = 2
            maxLines = 4
            setSingleLine(false)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Type assistant command")
            .setMessage("This uses the same parser and assistant flow as voice input.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val typedText = input.text.toString().trim()
                if (typedText.isNotEmpty()) {
                    dialog.dismiss()
                    assistantSession.submitTypedText(typedText)
                } else {
                    input.error = "Please type a command"
                }
            }

            input.requestFocus()
            dialog.window?.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            )
            input.post {
                val inputMethodManager =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val typedText = input.text.toString().trim()
                if (typedText.isNotEmpty()) {
                    dialog.dismiss()
                    assistantSession.submitTypedText(typedText)
                } else {
                    input.error = "Please type a command"
                }
                true
            } else {
                false
            }
        }

        dialog.show()
    }
    override fun onAssistantFinalText(text: String) {
        handleVoiceCommand(text)
    }

    override fun onAssistantCancelled() {
        homeFollowUpContext = HomeFollowUpContext.NONE
        clearPendingTaskMatchState()
        clearPendingDeleteState()
        clearPendingBreakdownState()
    }

    override fun onAssistantSessionStopped() {
        homeFollowUpContext = HomeFollowUpContext.NONE
        clearPendingTaskMatchState()
        clearPendingDeleteState()
        clearPendingBreakdownState()
    }

    override fun onResume(){
        super.onResume()

        refreshOverview()

        if(!hasShownPermissionDialog && !allRequiredPermissionsReady()){
            hasShownPermissionDialog = true
            showReminderSetupDialog()
        }

        if (shouldOpenAssistantOnResume) {
            shouldOpenAssistantOnResume = false
            window.decorView.post {
                assistantSession.startSession()
            }
        }
    }
    private fun refreshOverview(){
        val overviewText = findViewById<TextView>(R.id.overviewText)
        val dao = AppDatabase.getInstance(this).taskDao()

        lifecycleScope.launch{

            val tasks = withContext(Dispatchers.IO){dao.getRootTasks()}

            val today = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                .format(Calendar.getInstance().time)
            val todayCount = tasks.count { task ->
                !task.isDone && task.dueDate == today
            }
            overviewText.text = "You have $todayCount tasks today"
        }
    }

    private fun allRequiredPermissionsReady(): Boolean {
        val notificationReady =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
        val exactAlarmReady =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    (getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        return notificationReady && exactAlarmReady
    }
    private fun showReminderSetupDialog(){
        AlertDialog.Builder(this)
            .setTitle("Enable Reminder Permissions")
            .setMessage("To make the reminders work properly, please allow Notifications and Alarms & Reminders")
            .setCancelable(false)
            .setPositiveButton("Continue"){_, _ -> startPermissionFlow()}
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun startPermissionFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            checkExactAlarmPermission()
        }
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("Enable Alarms & reminders")
                    .setMessage(
                        "Android requires this setting so your scheduled reminders can ring on time, even when the app is closed."
                    )
                    .setCancelable(false)
                    .setPositiveButton("Open Settings") { _, _ ->
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        exactAlarmSettingsLauncher.launch(intent)
                    }
                    .setNegativeButton("Later", null)
                    .show()
            }
        }
    }

    private fun checkExactAlarmPermissionAfterReturn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                showPermissionDeniedDialog(
                    "Alarms & reminders is still disabled. Task reminders may not work until it is enabled."
                )
            }
        }
    }

    private fun showPermissionDeniedDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Permission not enabled")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }






    private fun buildConversationAppContextSummary(): String {
        return "homeFollowUpContext=$homeFollowUpContext, lastQueryDate=$lastQueryDate, lastQueryWasToday=$lastQueryWasToday, lastQueryWindow=${lastQueryWindow.spokenLabel}"
    }

    private fun handleVoiceCommand(command: String) {
        val normalized = TextNormalizer.normalize(command)

        // log
        Log.d("HOME_VOICE", "raw='$command' normalized='$normalized' context=$homeFollowUpContext")



        if (isConversationExitCommand(normalized)) {
            endAssistantConversation()
            return
        }

        if (homeFollowUpContext == HomeFollowUpContext.TASK_MATCH_AMBIGUITY) {
            handleTaskMatchAmbiguity(normalized)
            return
        }

        if (homeFollowUpContext == HomeFollowUpContext.BREAKDOWN_CONFIRMATION ||
            homeFollowUpContext == HomeFollowUpContext.BREAKDOWN_SCHEDULE_COLLECTION
        ) {
            if (handleBreakdownFollowUp(normalized)) {
                return
            }
        }

        if (homeFollowUpContext != HomeFollowUpContext.NONE) {
            val convoResult = conversationIntentClassifier.classify(normalized)

            // log
            Log.d(
                "HOME_CONVO",
                "text='$normalized' predicted=${convoResult.intent} confidence=${convoResult.confidence} context=$homeFollowUpContext"
            )

            if (convoResult.intent != ConversationIntent.UNKNOWN &&
                convoResult.confidence >= 0.30f) {
                // log
                Log.d("HOME_CONVO", "conversation intent accepted locally")

                if (handleConversationIntent(convoResult.intent)) {
                    return
                }
            } else {
                //log
                Log.d("HOME_CONVO", "conversation intent not accepted, falling through")
            }
        }

        if (handleHomeFollowUp(normalized)) {

            // log
            Log.d("HOME_FOLLOWUP", "handled by old hard-coded follow-up: '$normalized'")
            return
        }
        lifecycleScope.launch {
            try {
                Log.d("CONVO_ORCH", "normalized='$normalized'")
                val conversationDecision = try {
                    conversationOrchestrator.process(
                        normalizedText = normalized,
                        appContextSummary = buildConversationAppContextSummary()
                    )
                } catch (e: ConversationOrchestratorException) {
                    Log.e("CONVO_ORCH", "Conversation Agent failed after schema retry", e)
                    assistantSession.speak(
                        "I could not understand that request correctly. Please try again.",
                        listenAgain = true
                    )
                    return@launch
                }

                Log.d(
                    "CONVO_ORCH",
                    "route=${conversationDecision.route} confidence=${conversationDecision.confidence} " +
                            "source=${conversationDecision.source}"
                )

                val taskAgentInput: String
                when (conversationDecision.route) {
                    ConversationRoute.DIRECT_REPLY -> {
                        Log.d("CONVO_ORCH", "handled directly as DIRECT_REPLY")
                        assistantSession.speak(
                            conversationDecision.reply,
                            listenAgain = conversationDecision.listenAgain
                        )
                        return@launch
                    }
                    ConversationRoute.ASK_CLARIFICATION -> {
                        Log.d("CONVO_ORCH", "handled directly as ASK_CLARIFICATION")
                        assistantSession.speak(conversationDecision.reply, listenAgain = true)
                        return@launch
                    }
                    ConversationRoute.END_SESSION -> {
                        Log.d("CONVO_ORCH", "handled directly as END_SESSION")
                        homeFollowUpContext = HomeFollowUpContext.NONE
                        if (conversationDecision.reply.isNotBlank()) {
                            assistantSession.speak(conversationDecision.reply, listenAgain = false)
                        }
                        return@launch
                    }
                    ConversationRoute.TASK_COMMAND -> {
                        taskAgentInput = conversationDecision.taskText.ifBlank { normalized }
                        Log.d("CONVO_ORCH", "routed to task agent with text='$taskAgentInput'")
                    }
                    ConversationRoute.UNKNOWN -> {
                        Log.d("CONVO_ORCH", "handled directly as UNKNOWN")
                        assistantSession.speak(
                            conversationDecision.reply.ifBlank { "I cannot help with that request yet." },
                            listenAgain = true
                        )
                        return@launch
                    }
                }

                // log
                Log.d("HOME_ROUTING", "falling through to AgentOrchestrator with text='$taskAgentInput'")
                val aiResult = agentOrchestrator.process(taskAgentInput)

                Log.d(
                    "TASK_PIPELINE",
                    "intent=${aiResult.intent}, title=${aiResult.taskTitle}, date=${aiResult.dateText}," +
                            " time=${aiResult.timeText}, targetDate=${aiResult.targetDateText}, targetTime=${aiResult.targetTimeText}," +
                            " newDate=${aiResult.newDateText}, newTime=${aiResult.newTimeText}, source=${aiResult.source}, confidence=${aiResult.confidence}, " +
                            "needsClarification=${aiResult.needsClarification}, missingFields=${aiResult.missingFields}"
                )

                // branches for actions
                when (aiResult.intent) {
                    // create task
                    AiIntent.CREATE_TASK.name -> {
                        //log
                        Log.d("HOME_ACTION", "CREATE_TASK -> open CreateTaskActivity")

                        val reply = responseManager.openCreateTaskReply(aiResult.source)

                        speakObservationThenRun(
                            ExecutionObservation(
                                operation = ExecutionOperation.CREATE_TASK,
                                outcome = ExecutionOutcome.INFORMATION,
                                taskTitle = aiResult.taskTitle.orEmpty(),
                                dateText = aiResult.newDateText ?: aiResult.dateText.orEmpty(),
                                timeText = aiResult.newTimeText ?: aiResult.timeText.orEmpty(),
                                listenAgain = false,
                                fallbackSpeech = reply
                            )
                        ) {
                            val openCreateIntent = Intent(this@HomeActivity, CreateTaskActivity::class.java).apply {
                                putExtra("prefill_title", aiResult.taskTitle)
                                putExtra("prefill_date_text", aiResult.newDateText ?: aiResult.dateText)
                                putExtra("prefill_time_text", aiResult.newTimeText ?: aiResult.timeText)
                            }
                            startActivity(openCreateIntent)
                        }
                    }

                    // query on task
                    AiIntent.QUERY_TASK.name -> {
                        // log
                        Log.d("HOME_ACTION", "QUERY_TASK -> handleQueryTask")

                        if (aiResult.needsClarification) {
                            speakObservation(
                                unresolvedTemporalObservation(
                                    ExecutionOperation.QUERY_TASK,
                                    aiResult.targetDateText ?: aiResult.dateText,
                                    aiResult.targetTimeText ?: aiResult.timeText,
                                    "I could not understand that date or time range. Please try something like tomorrow morning, next week, or from Monday to Friday."
                                )
                            )
                            return@launch
                        }

                        handleQueryTask(
                            normalized = normalized,
                            agentDateText = aiResult.targetDateText ?: aiResult.dateText,
                            agentTimeText = aiResult.targetTimeText ?: aiResult.timeText
                        )
                    }
                    // delete task
                    AiIntent.DELETE_TASK.name -> {
                        Log.d("HOME_ACTION", "DELETE_TASK -> trying task match")

                        lifecycleScope.launch {
                            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()

                            // to prevent user accidently matching a completed task for deletion
                            val rawTasks = withContext(Dispatchers.IO) { dao.getRootActiveTasks() }
                            val tasks = filterMatchCandidates(rawTasks, aiResult.targetDateText ?: aiResult.dateText, aiResult.targetTimeText ?: aiResult.timeText, TaskCompletionFilter.ACTIVE_ONLY)
                            if (tasks == null) {
                                speakObservation(unresolvedTemporalObservation(ExecutionOperation.DELETE_TASK, aiResult.targetDateText ?: aiResult.dateText, aiResult.targetTimeText ?: aiResult.timeText, "I could not understand that date or time. Please say it another way."))
                                return@launch
                            }

                            val spokenPhrase = extractSpokenTaskPhrase(aiResult, normalized)
                            val matchResult = findTaskMatchResult(spokenPhrase, tasks)

                            when {
                                matchResult.isAmbiguous &&
                                        matchResult.bestTask != null &&
                                        matchResult.secondTask != null -> {
                                    askTaskMatchClarification(
                                        action = PendingTaskAction.DELETE,
                                        bestTask = matchResult.bestTask,
                                        secondTask = matchResult.secondTask
                                    )
                                }

                                matchResult.bestTask != null -> {
                                    val matchedTask = matchResult.bestTask
                                    askDeleteConfirmation(matchedTask)
                                }

                                else -> {
                                    speakObservation(ExecutionObservation(ExecutionOperation.DELETE_TASK, ExecutionOutcome.NOT_FOUND, listenAgain = true, fallbackSpeech = responseManager.taskMatchNotFound()))
                                }
                            }
                        }
                    }

                    // edit task or update it

                    AiIntent.UPDATE_TASK.name -> {
                        // log
                        Log.d("HOME_ACTION", "UPDATE_TASK -> trying task match")

                        lifecycleScope.launch {
                            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
                            val rawTasks = withContext(Dispatchers.IO) { dao.getRootActiveTasks() }
                            val tasks = filterMatchCandidates(rawTasks, aiResult.targetDateText, aiResult.targetTimeText, TaskCompletionFilter.ACTIVE_ONLY)
                            if (tasks == null) {
                                speakObservation(unresolvedTemporalObservation(ExecutionOperation.UPDATE_TASK, aiResult.targetDateText, aiResult.targetTimeText, "I could not understand that date or time. Please say it another way."))
                                return@launch
                            }

                            val spokenPhrase = extractSpokenTaskPhrase(aiResult, normalized)
                            val matchResult = findTaskMatchResult(spokenPhrase, tasks)

                            when {
                                matchResult.isAmbiguous &&
                                        matchResult.bestTask != null &&
                                        matchResult.secondTask != null -> {
                                    askTaskMatchClarification(
                                        action = PendingTaskAction.EDIT,
                                        bestTask = matchResult.bestTask,
                                        secondTask = matchResult.secondTask,
                                        proposedTitle = aiResult.taskTitle,
                                        proposedDateText = aiResult.newDateText ?: aiResult.dateText,
                                        proposedTimeText = aiResult.newTimeText ?: aiResult.timeText
                                    )
                                }

                                matchResult.bestTask != null -> {
                                    val matchedTask = matchResult.bestTask
                                    val reply = responseManager.openEditTask()
                                    speakObservationThenRun(ExecutionObservation(ExecutionOperation.UPDATE_TASK, ExecutionOutcome.INFORMATION, taskTitle = matchedTask.title, tasks = listOf(observedTask(matchedTask)), listenAgain = false, fallbackSpeech = reply)) {
                                        val openEditIntent = Intent(this@HomeActivity, EditTaskActivity::class.java).apply {
                                            putExtra("task_id", matchedTask.id)
                                            putExtra("task_title", matchedTask.title)
                                            putExtra("task_date", matchedTask.dueDate)
                                            putExtra("task_time", matchedTask.dueTime)
                                            putExtra("opened_by_assistant", true)
                                            putExtra("prefill_title", aiResult.taskTitle)
                                            putExtra("prefill_new_date_text", aiResult.newDateText ?: aiResult.dateText)
                                            putExtra("prefill_new_time_text", aiResult.newTimeText ?: aiResult.timeText)
                                        }
                                        startActivity(openEditIntent)
                                    }
                                }

                                else -> {
                                    val reply = responseManager.taskMatchNotFound()
                                    speakObservation(ExecutionObservation(ExecutionOperation.UPDATE_TASK, ExecutionOutcome.NOT_FOUND, listenAgain = true, fallbackSpeech = reply))
                                }
                            }
                        }
                    }
                    // reschedule task, changing the time and date

                    AiIntent.RESCHEDULE_TASK.name -> {
                        Log.d("HOME_ACTION", "RESCHEDULE_TASK -> trying task match")

                        lifecycleScope.launch {
                            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
                            val rawTasks = withContext(Dispatchers.IO) { dao.getRootActiveTasks() }
                            val tasks = filterMatchCandidates(rawTasks, aiResult.targetDateText, aiResult.targetTimeText, TaskCompletionFilter.ACTIVE_ONLY)
                            if (tasks == null) {
                                speakObservation(unresolvedTemporalObservation(ExecutionOperation.RESCHEDULE_TASK, aiResult.targetDateText, aiResult.targetTimeText, "I could not understand that date or time. Please say it another way."))
                                return@launch
                            }

                            val spokenPhrase = extractSpokenTaskPhrase(aiResult, normalized)
                            val matchResult = findTaskMatchResult(spokenPhrase, tasks)

                            when {
                                matchResult.isAmbiguous &&
                                        matchResult.bestTask != null &&
                                        matchResult.secondTask != null -> {
                                    askTaskMatchClarification(
                                        action = PendingTaskAction.RESCHEDULE,
                                        bestTask = matchResult.bestTask,
                                        secondTask = matchResult.secondTask,
                                        proposedDateText = aiResult.newDateText ?: aiResult.dateText,
                                        proposedTimeText = aiResult.newTimeText ?: aiResult.timeText
                                    )
                                }

                                matchResult.bestTask != null -> {
                                    val matchedTask = matchResult.bestTask

                                    val reply = responseManager.openReschedule()
                                    speakObservationThenRun(ExecutionObservation(ExecutionOperation.RESCHEDULE_TASK, ExecutionOutcome.INFORMATION, taskTitle = matchedTask.title, tasks = listOf(observedTask(matchedTask)), dateText = aiResult.newDateText ?: aiResult.dateText.orEmpty(), timeText = aiResult.newTimeText ?: aiResult.timeText.orEmpty(), listenAgain = false, fallbackSpeech = reply)) {
                                        val openRescheduleIntent = Intent(this@HomeActivity, EditTaskActivity::class.java).apply {
                                            putExtra("task_id", matchedTask.id)
                                            putExtra("task_title", matchedTask.title)
                                            putExtra("task_date", matchedTask.dueDate)
                                            putExtra("task_time", matchedTask.dueTime)
                                            putExtra("opened_by_assistant", true)
                                            putExtra("assistant_mode", "reschedule")
                                            putExtra("prefill_new_date_text", aiResult.newDateText ?: aiResult.dateText)
                                            putExtra("prefill_new_time_text", aiResult.newTimeText ?: aiResult.timeText)
                                        }
                                        startActivity(openRescheduleIntent)
                                    }
                                }

                                else -> {
                                    speakObservation(ExecutionObservation(ExecutionOperation.RESCHEDULE_TASK, ExecutionOutcome.NOT_FOUND, listenAgain = true, fallbackSpeech = responseManager.taskMatchNotFound()))
                                }
                            }
                        }
                    }

                    // to mark a task done (completing a task)
                    AiIntent.MARK_DONE.name -> {
                        Log.d("HOME_ACTION", "MARK_DONE -> trying task match")

                        lifecycleScope.launch {
                            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
                            val rawTasks = withContext(Dispatchers.IO) { dao.getActiveTasks() }
                            val tasks = filterMatchCandidates(rawTasks, aiResult.targetDateText ?: aiResult.dateText, aiResult.targetTimeText ?: aiResult.timeText, TaskCompletionFilter.ACTIVE_ONLY)
                            if (tasks == null) {
                                speakObservation(unresolvedTemporalObservation(ExecutionOperation.MARK_DONE, aiResult.targetDateText ?: aiResult.dateText, aiResult.targetTimeText ?: aiResult.timeText, "I could not understand that date or time. Please say it another way."))
                                return@launch
                            }

                            val spokenPhrase = extractSpokenTaskPhrase(aiResult, normalized)
                            val matchResult = findTaskMatchResult(spokenPhrase, tasks)

                            when {
                                matchResult.isAmbiguous &&
                                        matchResult.bestTask != null &&
                                        matchResult.secondTask != null -> {
                                    askTaskMatchClarification(
                                        action = PendingTaskAction.MARK_DONE,
                                        bestTask = matchResult.bestTask,
                                        secondTask = matchResult.secondTask
                                    )
                                }

                                matchResult.bestTask != null -> {
                                    val matchedTask = matchResult.bestTask

                                    withContext(Dispatchers.IO) {
                                        if (matchedTask.parentTaskId == null) {
                                            dao.updateDoneStatusForTaskAndSubtasks(matchedTask.id, true)
                                        } else {
                                            dao.updateDoneStatus(matchedTask.id, true)
                                        }
                                    }

                                    if (matchedTask.parentTaskId == null) {
                                        ReminderHelper.cancelReminder(this@HomeActivity, matchedTask.id.toInt())
                                    }

                                    refreshOverview()

                                    speakObservation(ExecutionObservation(ExecutionOperation.MARK_DONE, ExecutionOutcome.SUCCESS, taskTitle = matchedTask.title, tasks = listOf(observedTask(matchedTask.copy(isDone = true))), listenAgain = false, fallbackSpeech = responseManager.markDoneSuccess(matchedTask.title)))
                                }

                                else -> {
                                    speakObservation(ExecutionObservation(ExecutionOperation.MARK_DONE, ExecutionOutcome.NOT_FOUND, listenAgain = true, fallbackSpeech = responseManager.taskMatchNotFound()))
                                }
                            }
                        }
                    }

                    // to undo a completed task back to a open state
                    AiIntent.MARK_UNDONE.name -> {
                        Log.d("HOME_ACTION", "MARK_UNDONE -> trying task match")

                        lifecycleScope.launch {
                            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
                            val rawTasks = withContext(Dispatchers.IO) { dao.getAll() }
                            val tasks = filterMatchCandidates(rawTasks, aiResult.targetDateText ?: aiResult.dateText, aiResult.targetTimeText ?: aiResult.timeText, TaskCompletionFilter.COMPLETED_ONLY)
                            if (tasks == null) {
                                speakObservation(unresolvedTemporalObservation(ExecutionOperation.MARK_UNDONE, aiResult.targetDateText ?: aiResult.dateText, aiResult.targetTimeText ?: aiResult.timeText, "I could not understand that date or time. Please say it another way."))
                                return@launch
                            }

                            val spokenPhrase = extractSpokenTaskPhrase(aiResult, normalized)
                            val matchResult = findTaskMatchResult(spokenPhrase, tasks)

                            when {
                                matchResult.isAmbiguous &&
                                        matchResult.bestTask != null &&
                                        matchResult.secondTask != null -> {
                                    askTaskMatchClarification(
                                        action = PendingTaskAction.MARK_UNDONE,
                                        bestTask = matchResult.bestTask,
                                        secondTask = matchResult.secondTask
                                    )
                                }

                                matchResult.bestTask != null -> {
                                    val matchedTask = matchResult.bestTask

                                    withContext(Dispatchers.IO) {
                                        if (matchedTask.parentTaskId == null) {
                                            dao.updateDoneStatusForTaskAndSubtasks(matchedTask.id, false)
                                        } else {
                                            dao.updateDoneStatus(matchedTask.id, false)
                                        }
                                    }

                                    if (matchedTask.parentTaskId == null) {
                                        val reopenedTask = matchedTask.copy(isDone = false)
                                        ReminderHelper.scheduleReminderFromTask(this@HomeActivity, reopenedTask)
                                    }

                                    refreshOverview()

                                    speakObservation(ExecutionObservation(ExecutionOperation.MARK_UNDONE, ExecutionOutcome.SUCCESS, taskTitle = matchedTask.title, tasks = listOf(observedTask(matchedTask.copy(isDone = false))), listenAgain = false, fallbackSpeech = responseManager.markUndoneSuccess(matchedTask.title)))
                                }

                                else -> {
                                    speakObservation(ExecutionObservation(ExecutionOperation.MARK_UNDONE, ExecutionOutcome.NOT_FOUND, listenAgain = true, fallbackSpeech = responseManager.taskMatchNotFound()))
                                }
                            }
                        }
                    }

                    // for breakdown tasks
                    AiIntent.BREAKDOWN_TASK.name -> {
                        Log.d("HOME_ACTION", "BREAKDOWN_TASK -> start breakdown confirmation")

                        val title = aiResult.taskTitle ?: normalized
                        val plan = aiResult.plan
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .take(4)

                        if (plan.size < 2) {
                            speakObservation(
                                ExecutionObservation(
                                    operation = ExecutionOperation.BREAKDOWN_TASK,
                                    outcome = ExecutionOutcome.FAILURE,
                                    requiredInput = RequiredInput.RETRY,
                                    allowedUserMoves = listOf(AllowedUserMove.RETRY, AllowedUserMove.CANCEL, AllowedUserMove.REQUEST_HELP),
                                    listenAgain = true,
                                    fallbackSpeech = "I could not create a clear breakdown yet. Please describe the large task again."
                                )
                            )
                        } else {
                            startBreakdownConfirmation(
                                title = title,
                                plan = plan,
                                originalRequest = normalized,
                                dateText = aiResult.newDateText ?: aiResult.dateText,
                                timeText = aiResult.newTimeText ?: aiResult.timeText
                            )
                        }
                    }

                    else -> {
                        // log
                        Log.d("HOME_ACTION", "UNKNOWN -> local reply")

                        val reply = responseManager.unknownCommand()
                        assistantSession.speak(reply, listenAgain = false)
                    }
                }
            } catch (e: TaskAgentProcessingException) {
                Log.e("TASK_PIPELINE", "Task agent failed closed", e)
                assistantSession.speak("I could not process that task command safely. Please try again.", listenAgain = true)
            } catch (e: Exception) {
                Log.e("TASK_PIPELINE", "Crash in handleVoiceCommand", e)
                val reply = responseManager.parserCrash()
                assistantSession.speak(reply, listenAgain = false)
            }
        }
    }



    private fun unresolvedTemporalObservation(
        operation: ExecutionOperation,
        dateText: String?,
        timeText: String?,
        fallbackSpeech: String
    ): ExecutionObservation {
        val datePhraseUnresolved = !dateText.isNullOrBlank() &&
                temporalQueryResolver.resolve(dateText, null, dateText).status == TemporalResolutionStatus.UNRESOLVED
        val timePhraseUnresolved = !timeText.isNullOrBlank() &&
                temporalQueryResolver.resolve(null, timeText, timeText).status == TemporalResolutionStatus.UNRESOLVED
        val input = TemporalObservationInputs.fromUnresolvedComponents(
            datePhraseUnresolved = datePhraseUnresolved,
            timePhraseUnresolved = timePhraseUnresolved
        )
        return ExecutionObservation(
            operation = operation,
            outcome = ExecutionOutcome.NEEDS_CLARIFICATION,
            requiredInput = input.requiredInput,
            allowedUserMoves = input.allowedUserMoves,
            listenAgain = true,
            fallbackSpeech = fallbackSpeech
        )
    }

    private fun renderObservationResponse(observation: ExecutionObservation): ConversationResponse {
        val startedAt = System.currentTimeMillis()
        Log.d(
            "CMAS_OBSERVATION",
            "operation=${observation.operation} outcome=${observation.outcome} " +
                    "requiredInput=${observation.requiredInput} taskCount=${observation.taskCount} " +
                    "listenAgain=${observation.listenAgain}"
        )
        val response = AndroidObservationResponseRenderer.render(observation)
        val latencyMs = System.currentTimeMillis() - startedAt
        conversationOrchestrator.recordDeterministicObservation(observation, response)
        Log.d(
            "CMAS_RESPONSE",
            "responseType=${response.responseType} source=${response.source} " +
                    "speechLength=${response.speech.length} latencyMs=$latencyMs"
        )
        return response
    }

    private fun deliverObservationResponse(
        observation: ExecutionObservation,
        response: ConversationResponse,
        afterSpeech: (() -> Unit)? = null
    ) {
        val hint = response.hint.ifBlank { observation.fallbackHint }
        if (hint.isNotBlank()) assistantSession.getBottomSheet()?.showAssistantHint(hint)
        if (afterSpeech != null) {
            assistantSession.speakThenRun(response.speech) { afterSpeech() }
        } else if (observation.listenAgain) {
            assistantSession.speakThenListenAgain(response.speech)
        } else {
            assistantSession.speak(response.speech, listenAgain = false)
        }
    }

    private suspend fun speakObservation(observation: ExecutionObservation) {
        deliverObservationResponse(observation, renderObservationResponse(observation))
    }

    private suspend fun speakObservationThenRun(observation: ExecutionObservation, action: () -> Unit) {
        deliverObservationResponse(observation, renderObservationResponse(observation), action)
    }

    private fun observedTask(task: TaskEntity): ObservedTask = TaskObservationMapper.observedTask(
        task = task,
        subtasks = currentSubtasksByParentId[task.id].orEmpty()
    )

    private fun todayDateString(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            .format(Calendar.getInstance().time)
    }

    private fun resolveQueryDate(normalized: String): String? {
        return ScheduleTextParser.parseDateFromSentence(normalized)
    }

    private fun handleQueryTask(
        normalized: String,
        agentDateText: String?,
        agentTimeText: String?
    ) {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
            val taskData = withContext(Dispatchers.IO) {
                val roots = dao.getRootTasks()
                val subtasks = roots.associate { root -> root.id to dao.getSubtasks(root.id) }
                roots to subtasks
            }
            val allTasks = taskData.first
            currentSubtasksByParentId = taskData.second

            val today = todayDateString()
            val queryWindow = temporalQueryResolver.resolve(
                agentDateText = agentDateText,
                agentTimeText = agentTimeText,
                originalText = normalized
            )

            Log.d(
                "HOME_QUERY_TEMPORAL",
                "dateText=$agentDateText timeText=$agentTimeText status=${queryWindow.status} " +
                        "scope=${queryWindow.dateScope} startDate=${queryWindow.startDateInclusive} " +
                        "endDate=${queryWindow.endDateInclusive} startMinute=${queryWindow.startMinuteInclusive} " +
                        "endMinute=${queryWindow.endMinuteInclusive} wrapsMidnight=${queryWindow.wrapsMidnight} " +
                        "label=${queryWindow.spokenLabel}"
            )

            if (queryWindow.status == TemporalResolutionStatus.UNRESOLVED) {
                speakObservation(
                    unresolvedTemporalObservation(
                        ExecutionOperation.QUERY_TASK,
                        agentDateText,
                        agentTimeText,
                        "I could not understand that date or time range. Please try something like tomorrow morning, next week, or from Monday to Friday."
                    )
                )
                return@launch
            }

            lastQueryWindow = queryWindow
            lastQueryDate = if (queryWindow.isExactDate) queryWindow.startDateInclusive else null
            lastQueryWasToday = queryWindow.isExactDate && queryWindow.startDateInclusive == today

            val filteredTasks = TaskTemporalFilter.filterAndSort(allTasks, queryWindow)

            val replyMode = detectQueryReplyMode(normalized)
            val reply = buildTaskQueryReply(filteredTasks, lastQueryWasToday, replyMode, queryWindow)

            val hint = if (filteredTasks.isEmpty()) {
                responseManager.hintCreateOrRead()
            } else {
                when (replyMode) {
                    QueryReplyMode.SHORT, QueryReplyMode.NORMAL -> responseManager.hintYesNo()
                    QueryReplyMode.DETAILED -> responseManager.hintCreateOrRead()
                }
            }

            homeFollowUpContext = when {
                filteredTasks.isEmpty() -> HomeFollowUpContext.AFTER_NO_TASKS
                replyMode == QueryReplyMode.DETAILED -> HomeFollowUpContext.AFTER_TASK_DETAILS
                else -> HomeFollowUpContext.AFTER_TASK_SUMMARY
            }
            // log
            Log.d(
                "HOME_QUERY",
                "queryWindow=$queryWindow queryToday=$lastQueryWasToday replyMode=$replyMode taskCount=${filteredTasks.size} nextContext=$homeFollowUpContext"
            )

            val spokenFollowUp = when {
                filteredTasks.isEmpty() -> responseManager.followUpCreateAfterNoTasks()
                replyMode == QueryReplyMode.DETAILED -> responseManager.followUpAnythingElse()
                else -> responseManager.followUpReadAllTasks()
            }

            val spokenReply = responseManager.combineReplyWithFollowUp(reply, spokenFollowUp)
            val responseGuidance = when {
                filteredTasks.isEmpty() -> "Offer to create a new task."
                replyMode == QueryReplyMode.DETAILED -> "After giving the task details, ask whether the user needs anything else."
                else -> "After giving the requested summary, offer to read more task details."
            }

            speakObservation(
                ExecutionObservation(
                    operation = ExecutionOperation.QUERY_TASK,
                    outcome = if (filteredTasks.isEmpty()) ExecutionOutcome.NO_RESULTS else ExecutionOutcome.INFORMATION,
                    taskCount = filteredTasks.size,
                    dateText = queryWindow.spokenLabel,
                    detail = responseGuidance,
                    tasks = filteredTasks.take(responseManager.getMaxTasksForMode(if (replyMode == QueryReplyMode.DETAILED) QueryDetailMode.DETAILED else QueryDetailMode.NORMAL)).map { observedTask(it) },
                    listenAgain = true,
                    fallbackSpeech = spokenReply,
                    fallbackHint = hint
                )
            )
        }
    }
    private fun detectQueryReplyMode(normalized: String): QueryReplyMode {
        return when {
            normalized.contains("show all") ||
                    normalized.contains("read all") ||
                    normalized.contains("list all") ||
                    normalized.contains("all my tasks") -> {
                QueryReplyMode.DETAILED
            }

            normalized.contains("do i have") ||
                    normalized.contains("anything today") ||
                    normalized.contains("any task") ||
                    normalized.contains("any tasks") -> {
                QueryReplyMode.SHORT
            }

            else -> {
                QueryReplyMode.NORMAL
            }
        }
    }

    private fun buildTaskQueryReply(
        tasks: List<com.example.myapplication.data.TaskEntity>,
        queryToday: Boolean,
        mode: QueryReplyMode,
        queryWindow: TemporalQueryWindow
    ): String {
        if (tasks.isEmpty()) {
            return buildNoTasksQueryReply(queryToday, queryWindow)
        }

        return when (mode) {
            QueryReplyMode.SHORT -> buildShortQueryReply(tasks, queryToday, queryWindow)
            QueryReplyMode.NORMAL -> buildNormalQueryReply(tasks, queryToday, queryWindow)
            QueryReplyMode.DETAILED -> buildDetailedQueryReply(tasks, queryToday, queryWindow)
        }
    }

    private fun buildNoTasksQueryReply(
        queryToday: Boolean,
        queryWindow: TemporalQueryWindow
    ): String {
        val label = spokenTemporalLabel(queryWindow)
        if (label == null || queryToday) return responseManager.queryNoTasks(queryToday)
        return "You have no tasks $label."
    }

    private fun buildShortQueryReply(
        tasks: List<com.example.myapplication.data.TaskEntity>,
        queryToday: Boolean,
        queryWindow: TemporalQueryWindow
    ): String {
        val label = spokenTemporalLabel(queryWindow)
        if (label != null && !queryToday) {
            return "Yes, you have ${tasks.size} task${if (tasks.size > 1) "s" else ""} $label."
        }
        return responseManager.queryShortCount(tasks.size, queryToday)
    }

    private fun buildNormalQueryReply(
        tasks: List<com.example.myapplication.data.TaskEntity>,
        queryToday: Boolean,
        queryWindow: TemporalQueryWindow
    ): String {
        val intro = buildQueryIntro(tasks.size, queryToday, queryWindow)

        val maxTasks = responseManager.getMaxTasksForMode(QueryDetailMode.NORMAL)

        val taskDetails = tasks.take(maxTasks).joinToString(" ") { task ->
            buildCompactTaskSpeech(task)
        }

        val moreText = if (tasks.size > maxTasks) {
            responseManager.queryAndMore(tasks.size - maxTasks)
        } else {
            ""
        }

        return "$intro $taskDetails$moreText".trim()
    }

    private fun buildDetailedQueryReply(
        tasks: List<com.example.myapplication.data.TaskEntity>,
        queryToday: Boolean,
        queryWindow: TemporalQueryWindow
    ): String {
        val intro = buildQueryIntro(tasks.size, queryToday, queryWindow)


        val maxTasks = responseManager.getMaxTasksForMode(QueryDetailMode.DETAILED)

        val taskDetails = tasks.take(maxTasks).joinToString(" ") { task ->
            buildSingleTaskSpeech(task)
        }

        val moreText = if (tasks.size > maxTasks) {
            responseManager.queryAndMore(tasks.size - maxTasks)
        } else {
            ""
        }

        return "$intro $taskDetails$moreText".trim()
    }


    private fun buildQueryIntro(
        count: Int,
        queryToday: Boolean,
        queryWindow: TemporalQueryWindow
    ): String {
        val label = spokenTemporalLabel(queryWindow)
        if (label != null && !queryToday) {
            return "You have $count task${if (count > 1) "s" else ""} $label."
        }
        return responseManager.queryIntro(count, queryToday)
    }

    private fun spokenTemporalLabel(queryWindow: TemporalQueryWindow): String? {
        if (queryWindow.isExactDate &&
            queryWindow.startDateInclusive == todayDateString() &&
            !queryWindow.hasTimeConstraint
        ) return null
        return TemporalQueryLabelFormatter.spokenLabel(queryWindow)
    }

    private fun buildCompactTaskSpeech(task: com.example.myapplication.data.TaskEntity): String {
        val title = task.title.ifBlank { "Untitled task" }
        val hasTime = !task.dueTime.isNullOrBlank()

        val subtaskSpeech = buildUnfinishedSubtaskSpeech(task.id, compact = true)
        val base = if (hasTime) {
            "$title at ${task.dueTime}."
        } else {
            "$title."
        }
        return "$base$subtaskSpeech"
    }

    private fun buildSingleTaskSpeech(task: com.example.myapplication.data.TaskEntity): String {
        val title = task.title.ifBlank { "Untitled task" }

        val hasDate = !task.dueDate.isNullOrBlank()
        val hasTime = !task.dueTime.isNullOrBlank()

        val base = when {
            hasDate && hasTime -> "$title on ${formatDateForSpeech(task.dueDate)} at ${task.dueTime}."
            hasDate -> "$title on ${formatDateForSpeech(task.dueDate)}."
            hasTime -> "$title at ${task.dueTime}."
            else -> "$title."
        }
        return "$base${buildUnfinishedSubtaskSpeech(task.id, compact = false)}"
    }

    private fun buildUnfinishedSubtaskSpeech(parentTaskId: Long, compact: Boolean): String {
        val subtasks = currentSubtasksByParentId[parentTaskId].orEmpty()
        if (subtasks.isEmpty()) return ""

        val unfinished = subtasks.filter { !it.isDone }
        val intro = " It has ${subtasks.size} subtasks."
        if (unfinished.isEmpty()) return "$intro All are completed."

        val names = unfinished.take(2).joinToString(", ") { it.title }
        val more = if (unfinished.size > 2) ", and ${unfinished.size - 2} more" else ""
        return if (compact) {
            "$intro ${unfinished.size} unfinished."
        } else {
            "$intro ${unfinished.size} are unfinished: $names$more."
        }
    }

    private fun formatDateForSpeech(date: String?): String {
        if (date.isNullOrBlank()) return "unknown date"

        return try {
            val inputFormat = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.UK)
            val outputFormat = java.text.SimpleDateFormat("d MMMM yyyy", Locale.UK)
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



    // puase after speack input





    // --> temproray functions before adding this exit intent in Ai router
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

    private fun isSimpleFollowUpEndCommand(normalized: String): Boolean {
        return normalized == "no" ||
                normalized == "nothing else" ||
                normalized == "that's all" ||
                normalized == "thats all" ||
                normalized == "done" ||
                normalized == "i'm done" ||
                normalized == "im done" ||
                normalized == "all done" ||
                normalized == "finished" ||
                normalized == "that's it" ||
                normalized == "thats it"
    }

    private fun endAssistantConversation() {
        homeFollowUpContext = HomeFollowUpContext.NONE
        assistantSession.getBottomSheet()?.clearHint()
        clearPendingTaskMatchState()
        clearPendingDeleteState()
        clearPendingBreakdownState()
        assistantSession.speakThenStop(responseManager.stopListening())
    }


    private fun handleHomeFollowUp(normalized: String): Boolean {

        return when (homeFollowUpContext) {
            HomeFollowUpContext.AFTER_NO_TASKS -> {
                when {
                    normalized.contains("create one") ||
                            normalized == "create" ||
                            normalized == "yes" -> {
                        openCreateTaskFromFollowUp()
                        true
                    }

                    isSimpleFollowUpEndCommand(normalized) -> {
    endAssistantConversation()
    true
}

                    else -> false
                }
            }

            HomeFollowUpContext.AFTER_TASK_SUMMARY -> {
                when {
                    normalized.contains("read all") ||
                            normalized.contains("show all") ||
                            normalized == "yes" -> {
                        handleDetailedFollowUpQuery()
                        true
                    }

                    normalized.contains("create one") -> {
                        openCreateTaskFromFollowUp()
                        true
                    }

                    isSimpleFollowUpEndCommand(normalized) -> {
    endAssistantConversation()
    true
}

                    else -> false
                }
            }

            HomeFollowUpContext.AFTER_TASK_DETAILS -> {
                when {
                    normalized.contains("create one") -> {
                        openCreateTaskFromFollowUp()
                        true
                    }

                    isSimpleFollowUpEndCommand(normalized) -> {
    endAssistantConversation()
    true
}

                    else -> false
                }
            }

            HomeFollowUpContext.NONE -> false
            HomeFollowUpContext.TASK_MATCH_AMBIGUITY -> false
            HomeFollowUpContext.DELETE_CONFIRMATION -> false
            HomeFollowUpContext.BREAKDOWN_CONFIRMATION -> false
            HomeFollowUpContext.BREAKDOWN_SCHEDULE_COLLECTION -> false
        }

    }
    private fun handleDetailedFollowUpQuery() {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
            val taskData = withContext(Dispatchers.IO) {
                val roots = dao.getRootTasks()
                val subtasks = roots.associate { root -> root.id to dao.getSubtasks(root.id) }
                roots to subtasks
            }
            val allTasks = taskData.first
            currentSubtasksByParentId = taskData.second

            val today = todayDateString()
            val queryToday = lastQueryWindow.isExactDate && lastQueryWindow.startDateInclusive == today

            val filteredTasks = TaskTemporalFilter.filterAndSort(
                tasks = allTasks,
                window = lastQueryWindow
            )

            val reply = buildTaskQueryReply(
                tasks = filteredTasks,
                queryToday = queryToday,
                mode = QueryReplyMode.DETAILED,
                queryWindow = lastQueryWindow
            )

            homeFollowUpContext = HomeFollowUpContext.AFTER_TASK_DETAILS

            val hint = responseManager.hintCreateOrRead()
            val spokenReply = responseManager.combineReplyWithFollowUp(
                reply,
                responseManager.followUpAnythingElse()
            )

            speakObservation(
                ExecutionObservation(
                    operation = ExecutionOperation.QUERY_TASK,
                    outcome = if (filteredTasks.isEmpty()) ExecutionOutcome.NO_RESULTS else ExecutionOutcome.INFORMATION,
                    taskCount = filteredTasks.size,
                    dateText = lastQueryWindow.spokenLabel,
                    detail = if (filteredTasks.isEmpty()) {
                        "Offer to create a new task."
                    } else {
                        "After giving the task details, ask whether the user needs anything else."
                    },
                    tasks = filteredTasks
                        .take(responseManager.getMaxTasksForMode(QueryDetailMode.DETAILED))
                        .map { observedTask(it) },
                    listenAgain = true,
                    fallbackSpeech = spokenReply,
                    fallbackHint = hint
                )
            )
        }
    }
    private fun openCreateTaskFromFollowUp() {
        homeFollowUpContext = HomeFollowUpContext.NONE
        val reply = responseManager.followUpCreateAccepted()

        assistantSession.speakThenRun(reply) {
            startActivity(Intent(this@HomeActivity, CreateTaskActivity::class.java))
        }
    }


    private fun filterMatchCandidates(
        tasks: List<TaskEntity>,
        dateText: String?,
        timeText: String?,
        completionFilter: TaskCompletionFilter
    ): List<TaskEntity>? {
        val hasTemporal = !dateText.isNullOrBlank() || !timeText.isNullOrBlank()
        if (!hasTemporal) return TaskTemporalFilter.filterAndSort(
            tasks,
            TemporalQueryWindow(TemporalResolutionStatus.NONE),
            completionFilter
        )
        val resolution = temporalQueryResolver.resolve(dateText, timeText, "")
        if (resolution.status == TemporalResolutionStatus.UNRESOLVED) return null
        return TaskTemporalFilter.filterAndSort(tasks, resolution, completionFilter)
    }

    private fun findTaskMatchResult(
        spokenTitle: String?,
        tasks: List<com.example.myapplication.data.TaskEntity>
    ): com.example.myapplication.ai.TaskMatchResult {
        val result = if (spokenTitle.isNullOrBlank() && tasks.size == 1) {
            com.example.myapplication.ai.TaskMatchResult(bestTask = tasks.first(), bestScore = 1.0)
        } else if (spokenTitle.isNullOrBlank() && tasks.size > 1) {
            com.example.myapplication.ai.TaskMatchResult(bestTask = tasks[0], bestScore = 1.0, secondTask = tasks[1], secondScore = 1.0, isAmbiguous = true)
        } else {
            TaskMatcher.findBestTaskMatch(spokenTitle, tasks)
        }

        Log.d(
            "TASK_MATCH",
            "spoken='$spokenTitle' best='${result.bestTask?.title}' bestScore=${result.bestScore} second='${result.secondTask?.title}' secondScore=${result.secondScore} ambiguous=${result.isAmbiguous}"
        )

        return result
    }
    private fun clearPendingTaskMatchState() {
        taskResolutionState = taskResolutionState.clear()
        ambiguityRetryCount = 0
    }
    private fun askDeleteConfirmation(task: com.example.myapplication.data.TaskEntity) {
        pendingDeleteTaskId = task.id
        pendingDeleteTaskTitle = task.title
        homeFollowUpContext = HomeFollowUpContext.DELETE_CONFIRMATION

        lifecycleScope.launch {
            speakObservation(
                ExecutionObservation(
                    operation = ExecutionOperation.DELETE_TASK,
                    outcome = ExecutionOutcome.NEEDS_CONFIRMATION,
                    taskTitle = task.title,
                    tasks = listOf(observedTask(task)),
                    requiredInput = RequiredInput.CONFIRMATION,
                    allowedUserMoves = listOf(AllowedUserMove.CONFIRM, AllowedUserMove.REJECT, AllowedUserMove.CANCEL),
                    listenAgain = true,
                    fallbackSpeech = "Are you sure you want to delete ${task.title}?"
                )
            )
        }
    }

    private fun clearPendingDeleteState() {
        pendingDeleteTaskId = null
        pendingDeleteTaskTitle = null
    }

    private fun confirmPendingDelete() {
        val taskId = pendingDeleteTaskId
        val title = pendingDeleteTaskTitle

        if (taskId == null || title == null) {
            clearPendingDeleteState()
            homeFollowUpContext = HomeFollowUpContext.NONE
            assistantSession.speakThenStop(responseManager.unknownCommand())
            return
        }

        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()

            withContext(Dispatchers.IO) {
                dao.deleteTaskAndSubtasks(taskId)
            }

            ReminderHelper.cancelReminder(this@HomeActivity, taskId.toInt())
            refreshOverview()

            clearPendingDeleteState()
            homeFollowUpContext = HomeFollowUpContext.NONE

            speakObservation(ExecutionObservation(ExecutionOperation.DELETE_TASK, ExecutionOutcome.SUCCESS, taskTitle = title, listenAgain = false, fallbackSpeech = responseManager.deleteSuccess(title)))
        }
    }
    private fun startBreakdownConfirmation(
        title: String,
        plan: List<String>,
        originalRequest: String,
        dateText: String? = null,
        timeText: String? = null
    ) {
        pendingBreakdownTitle = title
        pendingBreakdownPlan = plan.take(4)
        pendingBreakdownOriginalRequest = originalRequest
        pendingBreakdownDateText = dateText
        pendingBreakdownTimeText = timeText
        homeFollowUpContext = HomeFollowUpContext.BREAKDOWN_CONFIRMATION

        val fallback = buildBreakdownProposalSpeech(
            title = title,
            plan = pendingBreakdownPlan
        )
        lifecycleScope.launch {
            speakObservation(
                ExecutionObservation(
                    operation = ExecutionOperation.BREAKDOWN_TASK,
                    outcome = ExecutionOutcome.NEEDS_CONFIRMATION,
                    taskTitle = title,
                    planItems = pendingBreakdownPlan,
                    requiredInput = RequiredInput.CONFIRMATION,
                    allowedUserMoves = listOf(AllowedUserMove.CONFIRM, AllowedUserMove.REJECT, AllowedUserMove.CANCEL, AllowedUserMove.CHANGE_FIELD),
                    listenAgain = true,
                    fallbackSpeech = fallback,
                    fallbackHint = "Say yes to create these subtasks, no to cancel, or describe how to change the plan."
                )
            )
        }
    }

    private fun buildBreakdownProposalSpeech(
        title: String,
        plan: List<String>
    ): String {
        // Task Agent natural_response is intentionally not used for final task speech;
        // Android's deterministic observation renderer speaks this authoritative response.
        val intro = "I prepared a breakdown for $title."

        val planSpeech = plan.mapIndexed { index, item ->
            "${index + 1}. $item."
        }.joinToString(" ")

        return "$intro $planSpeech Do you want me to create this scheduled task with these subtasks?"
    }

    private fun clearPendingBreakdownState() {
        pendingBreakdownTitle = null
        pendingBreakdownPlan = emptyList()
        pendingBreakdownOriginalRequest = null
        pendingBreakdownDateText = null
        pendingBreakdownTimeText = null
        pendingBreakdownTemporalClarification = null
    }

    private fun handleBreakdownFollowUp(normalized: String): Boolean {
        return when (homeFollowUpContext) {
            HomeFollowUpContext.BREAKDOWN_CONFIRMATION -> handleBreakdownConfirmationFollowUp(normalized)
            HomeFollowUpContext.BREAKDOWN_SCHEDULE_COLLECTION -> handleBreakdownScheduleFollowUp(normalized)
            else -> false
        }
    }

    private fun handleBreakdownConfirmationFollowUp(normalized: String): Boolean {
        return when {
            isBreakdownAccept(normalized) -> {
                proceedAfterBreakdownApproval()
                true
            }

            isBreakdownCancel(normalized) -> {
                val title = pendingBreakdownTitle
                clearPendingBreakdownState()
                homeFollowUpContext = HomeFollowUpContext.NONE

                assistantSession.speakThenStop(
                    if (title != null) {
                        "Okay, I will not create subtasks for $title."
                    } else {
                        "Okay, I will not create those subtasks."
                    }
                )
                true
            }

            else -> {
                regenerateBreakdownWithFeedback(normalized)
                true
            }
        }
    }

    private fun proceedAfterBreakdownApproval() {
        val resolution = temporalQueryResolver.resolve(pendingBreakdownDateText, pendingBreakdownTimeText, "")
        val policy = TemporalActionPolicy.evaluate(resolution, TemporalUseCase.BREAKDOWN)
        when (policy) {
            is TemporalPolicyResult.Ready -> createPendingBreakdownIfFuture(resolution.startDateInclusive!!, ScheduleTextParser.formatTime(resolution.startMinuteInclusive!! / 60, resolution.startMinuteInclusive!! % 60))
            is TemporalPolicyResult.InvalidPastSchedule -> enterBreakdownFutureCorrection(resolution)
            is TemporalPolicyResult.Unresolved -> assistantSession.speakThenListenAgain("I could not understand that schedule. Please say an exact date and time.")
            else -> {
                val needsDate = policy is TemporalPolicyResult.NeedsExactDate || policy is TemporalPolicyResult.NeedsExactDateAndTime
                val needsTime = policy is TemporalPolicyResult.NeedsExactTime || policy is TemporalPolicyResult.NeedsExactDateAndTime
                pendingBreakdownTemporalClarification = PendingTemporalClarification(resolution, needsExactDate = needsDate, needsExactTime = needsTime)
                homeFollowUpContext = HomeFollowUpContext.BREAKDOWN_SCHEDULE_COLLECTION
                promptNextBreakdownTemporalClarification()
            }
        }
    }

    private fun promptNextBreakdownTemporalClarification() {
        val pending = pendingBreakdownTemporalClarification ?: return
        val prompt = when {
            pending.needsExactDate && pending.exactDate == null -> "Which exact date ${pending.original.originalDatePhrase.ifBlank { pending.original.spokenLabel }}?"
            pending.needsExactTime && pending.exactMinute == null -> "What exact time ${pending.original.originalTimePhrase.ifBlank { pending.original.spokenLabel }}?"
            else -> null
        }
        if (prompt != null) {
            assistantSession.getBottomSheet()?.showAssistantHint(prompt)
            assistantSession.speakThenListenAgain(prompt)
        } else {
            val date = pending.exactDate ?: pending.original.startDateInclusive
            val minute = pending.exactMinute ?: pending.original.startMinuteInclusive
            if (date != null && minute != null) {
                pendingBreakdownTemporalClarification = null
                createPendingBreakdownIfFuture(date, ScheduleTextParser.formatTime(minute / 60, minute % 60))
            }
        }
    }

    private fun handleBreakdownScheduleFollowUp(normalized: String): Boolean {
        if (isBreakdownCancel(normalized)) {
            val title = pendingBreakdownTitle
            clearPendingBreakdownState()
            homeFollowUpContext = HomeFollowUpContext.NONE
            assistantSession.speakThenStop(
                if (title != null) "Okay, I will not create subtasks for $title." else "Okay, I will not create those subtasks."
            )
            return true
        }

        val pending = pendingBreakdownTemporalClarification
        if (pending != null) {
            if (pending.needsExactDate && pending.exactDate == null) {
                val r = temporalQueryResolver.resolve(normalized, null, normalized)
                if (!r.isExactDate || r.startDateInclusive == null || !TemporalActionPolicy.validateClarification(pending.original, r.startDateInclusive, null)) {
                    assistantSession.speakThenListenAgain("That date is outside the requested range. Please choose a valid date.")
                    return true
                }
                pendingBreakdownTemporalClarification = pending.copy(exactDate = r.startDateInclusive)
                promptNextBreakdownTemporalClarification()
                return true
            }
            if (pending.needsExactTime && pending.exactMinute == null) {
                val r = temporalQueryResolver.resolve(null, normalized, normalized)
                val minute = r.startMinuteInclusive
                if (!r.isExactTime || minute == null || !TemporalActionPolicy.validateClarification(pending.original, null, minute)) {
                    assistantSession.speakThenListenAgain("That time is outside the requested range. Please choose a valid time.")
                    return true
                }
                pendingBreakdownTemporalClarification = pending.copy(exactMinute = minute)
                promptNextBreakdownTemporalClarification()
                return true
            }
        }

        val incoming = temporalQueryResolver.resolve(null, null, normalized)
        pendingBreakdownDateText = incoming.takeIf { it.isExactDate }?.startDateInclusive ?: pendingBreakdownDateText
        pendingBreakdownTimeText = incoming.takeIf { it.isExactTime }?.startMinuteInclusive?.let { ScheduleTextParser.formatTime(it / 60, it % 60) } ?: pendingBreakdownTimeText
        proceedAfterBreakdownApproval()
        return true
    }

    private fun createPendingBreakdownIfFuture(dueDate: String, dueTime: String) {
        val finalResolution = temporalQueryResolver.resolve(dueDate, dueTime, listOf(dueDate, dueTime).joinToString(" "))
        if (TemporalActionPolicy.evaluate(finalResolution, TemporalUseCase.BREAKDOWN) is TemporalPolicyResult.InvalidPastSchedule) {
            enterBreakdownFutureCorrection(finalResolution)
            return
        }
        createPendingBreakdown(dueDate, dueTime)
    }

    private fun enterBreakdownFutureCorrection(rejectedResolution: TemporalQueryWindow) {
        val rejectedDate = rejectedResolution.startDateInclusive
        val rejectedMinute = rejectedResolution.startMinuteInclusive
        val replacementOriginal = TemporalQueryWindow(
            TemporalResolutionStatus.NONE,
            spokenLabel = "a future schedule"
        )
        val calendar = Calendar.getInstance()
        val currentMinute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val dateIsPast = isDateBeforeToday(rejectedDate)
        val timeIsPastToday = isToday(rejectedDate) && rejectedMinute != null && rejectedMinute <= currentMinute

        pendingBreakdownTemporalClarification = when {
            dateIsPast -> PendingTemporalClarification(
                original = replacementOriginal,
                exactMinute = rejectedMinute,
                needsExactDate = true,
                needsExactTime = false,
                replacingOriginalConstraint = true
            )
            timeIsPastToday -> PendingTemporalClarification(
                original = replacementOriginal,
                exactDate = rejectedDate,
                needsExactDate = false,
                needsExactTime = true,
                replacingOriginalConstraint = true
            )
            else -> PendingTemporalClarification(
                original = replacementOriginal,
                needsExactDate = true,
                needsExactTime = true,
                replacingOriginalConstraint = true
            )
        }
        homeFollowUpContext = HomeFollowUpContext.BREAKDOWN_SCHEDULE_COLLECTION
        val pending = pendingBreakdownTemporalClarification
        val prompt = when {
            pending?.needsExactDate == true && pending.exactDate == null -> "Please provide a future exact date."
            pending?.needsExactTime == true && pending.exactMinute == null -> "Please provide a later exact time."
            else -> "Please provide a future date and time."
        }
        assistantSession.getBottomSheet()?.showAssistantHint(prompt)
        assistantSession.speakThenListenAgain("${responseManager.pastDateTime()} $prompt")
    }

    private fun isDateBeforeToday(date: String?): Boolean {
        val parsed = parseDateMillis(date) ?: return false
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return parsed < today
    }

    private fun isToday(date: String?): Boolean {
        val parsed = parseDateMillis(date) ?: return false
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return parsed == today
    }

    private fun parseDateMillis(date: String?): Long? = try {
        if (date.isNullOrBlank()) null else SimpleDateFormat("dd/MM/yyyy", Locale.UK).parse(date)?.time
    } catch (_: Exception) {
        null
    }

    private fun createPendingBreakdown(dueDate: String, dueTime: String) {
        val title = pendingBreakdownTitle
        val plan = pendingBreakdownPlan

        if (title.isNullOrBlank() || plan.isEmpty()) {
            clearPendingBreakdownState()
            homeFollowUpContext = HomeFollowUpContext.NONE
            assistantSession.speakThenStop(responseManager.unknownCommand())
            return
        }

        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
            val mainTask = TaskEntity(
                title = title,
                dueDate = dueDate,
                dueTime = dueTime
            )

            val insertedParentId = withContext(Dispatchers.IO) {
                val parentId = dao.insert(mainTask)
                Log.d("HOME_BREAKDOWN", "main task inserted id=$parentId")

                plan.forEachIndexed { index, subtaskTitle ->
                    dao.insert(
                        TaskEntity(
                            title = subtaskTitle,
                            dueDate = dueDate,
                            dueTime = dueTime,
                            parentTaskId = parentId,
                            subtaskOrder = index
                        )
                    )
                    Log.d("HOME_BREAKDOWN", "inserted subtask title=$subtaskTitle parentId=$parentId")
                }
                parentId
            }

            ReminderHelper.scheduleReminderFromTask(
                this@HomeActivity,
                mainTask.copy(id = insertedParentId)
            )
            refreshOverview()

            val count = plan.size
            clearPendingBreakdownState()
            homeFollowUpContext = HomeFollowUpContext.NONE

            speakObservation(
                ExecutionObservation(
                    operation = ExecutionOperation.BREAKDOWN_TASK,
                    outcome = ExecutionOutcome.SUCCESS,
                    taskTitle = title,
                    taskCount = 1,
                    dateText = dueDate,
                    timeText = dueTime,
                    planItems = plan,
                    listenAgain = false,
                    fallbackSpeech = "I created $title with $count subtasks for $dueDate at $dueTime."
                )
            )
        }
    }

    private fun isBreakdownAccept(normalized: String): Boolean {
        return normalized == "yes" ||
                normalized == "yes yes" ||
                normalized == "yeah" ||
                normalized == "yep" ||
                normalized == "sure" ||
                normalized.contains("create them") ||
                normalized.contains("add them") ||
                normalized.contains("save them")
    }

    private fun isBreakdownCancel(normalized: String): Boolean {
        return normalized == "no" ||
                normalized == "no thanks" ||
                normalized == "cancel" ||
                normalized == "stop" ||
                normalized == "nevermind" ||
                normalized == "never mind"
    }

    private fun regenerateBreakdownWithFeedback(feedback: String) {
        val title = pendingBreakdownTitle
        val oldPlan = pendingBreakdownPlan

        if (title.isNullOrBlank()) {
            clearPendingBreakdownState()
            homeFollowUpContext = HomeFollowUpContext.NONE
            assistantSession.speakThenStop(responseManager.unknownCommand())
            return
        }

        lifecycleScope.launch {
            try {
                assistantSession.getBottomSheet()?.showAssistantHint(
                    "Updating the plan..."
                )

                val oldPlanText = oldPlan.joinToString("; ")

                val refinementRequest = """
                Break down this task into 2 to 4 short actionable subtasks.
                Task: $title
                Previous subtasks: $oldPlanText
                User feedback: $feedback
            """.trimIndent()

                Log.d("HOME_BREAKDOWN", "regenerating breakdown with feedback='$feedback'")

                val result = agentOrchestrator.process(refinementRequest)

                if (result.intent == AiIntent.BREAKDOWN_TASK.name && result.plan.size >= 2) {
                    startBreakdownConfirmation(
                        title = result.taskTitle ?: title,
                        plan = result.plan.take(4),
                        originalRequest = pendingBreakdownOriginalRequest ?: refinementRequest,
                        dateText = pendingBreakdownDateText,
                        timeText = pendingBreakdownTimeText
                    )
                } else {
                    assistantSession.speakThenListenAgain(
                        "I could not revise the breakdown clearly. Please describe how you want to change it."
                    )
                }
            } catch (e: Exception) {
                Log.e("HOME_BREAKDOWN", "Failed to regenerate breakdown", e)
                assistantSession.speakThenListenAgain(
                    "I could not revise the breakdown. Please try again."
                )
            }
        }
    }

    private fun findTaskById(
        taskId: Long?,
        tasks: List<com.example.myapplication.data.TaskEntity>
    ): com.example.myapplication.data.TaskEntity? {
        if (taskId == null) return null
        return tasks.firstOrNull { it.id == taskId }
    }
    private fun resolveAmbiguousTaskChoice(
        normalized: String,
        tasks: List<com.example.myapplication.data.TaskEntity>
    ): com.example.myapplication.data.TaskEntity? {
        val firstTask = findTaskById(taskResolutionState.candidate1Id, tasks)
        val secondTask = findTaskById(taskResolutionState.candidate2Id, tasks)

        if (normalized.contains("first")) return firstTask
        if (normalized.contains("second")) return secondTask

        val result = TaskMatcher.findBestTaskMatch(normalized, listOfNotNull(firstTask, secondTask))
        return result.bestTask
    }

    private fun askTaskMatchClarification(

        action: PendingTaskAction,
        bestTask: com.example.myapplication.data.TaskEntity,
        secondTask: com.example.myapplication.data.TaskEntity,
        proposedTitle: String? = null,
        proposedDateText: String? = null,
        proposedTimeText: String? = null
    ) {

        taskResolutionState = TaskResolutionState(
            action = action,
            candidate1Id = bestTask.id,
            candidate2Id = secondTask.id,
            proposedTitle = proposedTitle,
            proposedDateText = proposedDateText,
            proposedTimeText = proposedTimeText
        )
        ambiguityRetryCount = 0
        homeFollowUpContext = HomeFollowUpContext.TASK_MATCH_AMBIGUITY

        lifecycleScope.launch {
            speakObservation(
                ExecutionObservation(
                    operation = when (action) {
                        PendingTaskAction.DELETE -> ExecutionOperation.DELETE_TASK
                        PendingTaskAction.EDIT -> ExecutionOperation.UPDATE_TASK
                        PendingTaskAction.RESCHEDULE -> ExecutionOperation.RESCHEDULE_TASK
                        PendingTaskAction.MARK_DONE -> ExecutionOperation.MARK_DONE
                        PendingTaskAction.MARK_UNDONE -> ExecutionOperation.MARK_UNDONE
                        else -> ExecutionOperation.SYSTEM
                    },
                    outcome = ExecutionOutcome.AMBIGUOUS,
                    taskCount = 2,
                    choices = listOf(bestTask.title, secondTask.title),
                    requiredInput = RequiredInput.TASK_CHOICE,
                    allowedUserMoves = listOf(AllowedUserMove.SELECT_OPTION, AllowedUserMove.CANCEL, AllowedUserMove.REQUEST_HELP),
                    listenAgain = true,
                    fallbackSpeech = responseManager.taskMatchAmbiguous(bestTask.title, secondTask.title),
                    fallbackHint = responseManager.hintAmbiguityChoice()
                )
            )
        }
    }

    private fun handleTaskMatchAmbiguity(normalized: String) {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
            val tasks = withContext(Dispatchers.IO) {
                when (taskResolutionState.action) {
                    PendingTaskAction.MARK_DONE -> dao.getActiveTasks()
                    PendingTaskAction.MARK_UNDONE -> dao.getAll()
                    else -> dao.getRootActiveTasks()
                }
            }

            val chosenTask = resolveAmbiguousTaskChoice(normalized, tasks)

            if (chosenTask == null) {
                ambiguityRetryCount++

                if (ambiguityRetryCount >= 2) {
                    clearPendingTaskMatchState()
                    homeFollowUpContext = HomeFollowUpContext.NONE

                    assistantSession.speakThenListenAgain(
                        responseManager.taskMatchAmbiguityReset()
                    )
                } else {
                    assistantSession.speakThenListenAgain(
                        responseManager.taskMatchAmbiguityRetry()
                    )
                }
                return@launch
            }

            val action = taskResolutionState.action
            val proposedTitle = taskResolutionState.proposedTitle
            val proposedDate = taskResolutionState.proposedDateText
            val proposedTime = taskResolutionState.proposedTimeText

            clearPendingTaskMatchState()
            homeFollowUpContext = HomeFollowUpContext.NONE

            when (action) {
                PendingTaskAction.EDIT -> {
                    val reply = responseManager.openEditTask()
                    speakObservationThenRun(ExecutionObservation(ExecutionOperation.UPDATE_TASK, ExecutionOutcome.INFORMATION, taskTitle = chosenTask.title, tasks = listOf(observedTask(chosenTask)), listenAgain = false, fallbackSpeech = reply)) {
                        val openEditIntent = Intent(this@HomeActivity, EditTaskActivity::class.java).apply {
                            putExtra("task_id", chosenTask.id)
                            putExtra("task_title", chosenTask.title)
                            putExtra("task_date", chosenTask.dueDate)
                            putExtra("task_time", chosenTask.dueTime)
                            putExtra("opened_by_assistant", true)
                            putExtra("prefill_title", proposedTitle)
                            putExtra("prefill_new_date_text", proposedDate)
                            putExtra("prefill_new_time_text", proposedTime)
                        }
                        startActivity(openEditIntent)
                    }
                }

                PendingTaskAction.RESCHEDULE -> {
                    val reply = responseManager.openReschedule()
                    speakObservationThenRun(ExecutionObservation(ExecutionOperation.RESCHEDULE_TASK, ExecutionOutcome.INFORMATION, taskTitle = chosenTask.title, tasks = listOf(observedTask(chosenTask)), dateText = proposedDate.orEmpty(), timeText = proposedTime.orEmpty(), listenAgain = false, fallbackSpeech = reply)) {
                        val openRescheduleIntent = Intent(this@HomeActivity, EditTaskActivity::class.java).apply {
                            putExtra("task_id", chosenTask.id)
                            putExtra("task_title", chosenTask.title)
                            putExtra("task_date", chosenTask.dueDate)
                            putExtra("task_time", chosenTask.dueTime)
                            putExtra("opened_by_assistant", true)
                            putExtra("assistant_mode", "reschedule")
                            putExtra("prefill_new_date_text", proposedDate)
                            putExtra("prefill_new_time_text", proposedTime)
                        }
                        startActivity(openRescheduleIntent)
                    }
                }

                PendingTaskAction.DELETE -> {
                    askDeleteConfirmation(chosenTask)
                }

                PendingTaskAction.MARK_DONE -> {
                    withContext(Dispatchers.IO) {
                        if (chosenTask.parentTaskId == null) {
                            dao.updateDoneStatusForTaskAndSubtasks(chosenTask.id, true)
                        } else {
                            dao.updateDoneStatus(chosenTask.id, true)
                        }
                    }

                    if (chosenTask.parentTaskId == null) {
                        ReminderHelper.cancelReminder(this@HomeActivity, chosenTask.id.toInt())
                    }

                    refreshOverview()
                    speakObservation(ExecutionObservation(ExecutionOperation.MARK_DONE, ExecutionOutcome.SUCCESS, taskTitle = chosenTask.title, tasks = listOf(observedTask(chosenTask.copy(isDone = true))), listenAgain = false, fallbackSpeech = responseManager.markDoneSuccess(chosenTask.title)))
                }

                PendingTaskAction.MARK_UNDONE -> {
                    withContext(Dispatchers.IO) {
                        if (chosenTask.parentTaskId == null) {
                            dao.updateDoneStatusForTaskAndSubtasks(chosenTask.id, false)
                        } else {
                            dao.updateDoneStatus(chosenTask.id, false)
                        }
                    }

                    if (chosenTask.parentTaskId == null) {
                        ReminderHelper.scheduleReminderFromTask(
                            this@HomeActivity,
                            chosenTask.copy(isDone = false)
                        )
                    }

                    refreshOverview()
                    speakObservation(ExecutionObservation(ExecutionOperation.MARK_UNDONE, ExecutionOutcome.SUCCESS, taskTitle = chosenTask.title, tasks = listOf(observedTask(chosenTask.copy(isDone = false))), listenAgain = false, fallbackSpeech = responseManager.markUndoneSuccess(chosenTask.title)))
                }

                PendingTaskAction.NONE -> {
                    assistantSession.speak(responseManager.unknownCommand(), listenAgain = false)
                }
            }
        }
    }


    private fun speakThenOpen(reply: String, action: () -> Unit) {
        assistantSession.speakThenRun(reply) {
            action()
        }
    }

    private fun handleConversationIntent(intent: ConversationIntent): Boolean {
        // log
        Log.d("HOME_CONVO_ACTION", "intent=$intent context=$homeFollowUpContext")

        return when (homeFollowUpContext) {
            HomeFollowUpContext.AFTER_NO_TASKS -> {
                when (intent) {
                    ConversationIntent.CONFIRM_YES,
                    ConversationIntent.CREATE_ONE -> {
                        // log
                        Log.d("HOME_CONVO_ACTION", "opening create from follow-up")
                        openCreateTaskFromFollowUp()
                        true
                    }

                    ConversationIntent.CONFIRM_NO,
                    ConversationIntent.STOP_CONVERSATION -> {
                        // log
                        Log.d("HOME_CONVO_ACTION", "ending conversation from follow-up")
                        endAssistantConversation()
                        true
                    }

                    else -> false
                }
            }

            HomeFollowUpContext.AFTER_TASK_SUMMARY -> {
                when (intent) {
                    ConversationIntent.CONFIRM_YES,
                    ConversationIntent.READ_ALL -> {
                        // log
                        Log.d("HOME_CONVO_ACTION", "reading all from follow-up")
                        handleDetailedFollowUpQuery()
                        true
                    }

                    ConversationIntent.CREATE_ONE -> {
                        // log
                        Log.d("HOME_CONVO_ACTION", "opening create from follow-up")
                        openCreateTaskFromFollowUp()
                        true
                    }

                    ConversationIntent.CONFIRM_NO,
                    ConversationIntent.STOP_CONVERSATION -> {
                        //log
                        Log.d("HOME_CONVO_ACTION", "ending conversation from follow-up")
                        endAssistantConversation()
                        true
                    }

                    else -> false
                }
            }

            HomeFollowUpContext.AFTER_TASK_DETAILS -> {
                when (intent) {
                    ConversationIntent.CREATE_ONE -> {
                        // log
                        Log.d("HOME_CONVO_ACTION", "opening create from follow-up")
                        openCreateTaskFromFollowUp()
                        true
                    }

                    ConversationIntent.CONFIRM_NO,
                    ConversationIntent.STOP_CONVERSATION -> {
                        // log
                        Log.d("HOME_CONVO_ACTION", "ending conversation from follow-up")

                        endAssistantConversation()
                        true
                    }

                    else -> false
                }
            }
            HomeFollowUpContext.DELETE_CONFIRMATION -> {
                when (intent) {
                    ConversationIntent.CONFIRM_YES -> {
                        Log.d("HOME_CONVO_ACTION", "delete confirmed")
                        confirmPendingDelete()
                        true
                    }

                    ConversationIntent.CONFIRM_NO,
                    ConversationIntent.STOP_CONVERSATION -> {
                        val title = pendingDeleteTaskTitle
                        clearPendingDeleteState()
                        homeFollowUpContext = HomeFollowUpContext.NONE

                        lifecycleScope.launch {
                            speakObservation(
                                ExecutionObservation(
                                    operation = ExecutionOperation.DELETE_TASK,
                                    outcome = ExecutionOutcome.CANCELLED,
                                    taskTitle = title.orEmpty(),
                                    listenAgain = false,
                                    fallbackSpeech = if (title != null) {
                                        "Okay, I will not delete $title."
                                    } else {
                                        "Okay, I will not delete it."
                                    }
                                )
                            )
                        }
                        true
                    }

                    else -> false
                }
            }

            HomeFollowUpContext.BREAKDOWN_CONFIRMATION -> false
            HomeFollowUpContext.BREAKDOWN_SCHEDULE_COLLECTION -> false
            HomeFollowUpContext.NONE -> false

            // stopping inside ambiguity flow
            // let the user to say "no" during ambiguity confirmation
            HomeFollowUpContext.TASK_MATCH_AMBIGUITY -> {
                when (intent) {
                    ConversationIntent.CONFIRM_NO,
                    ConversationIntent.STOP_CONVERSATION -> {
                        clearPendingTaskMatchState()
                        endAssistantConversation()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun extractSpokenTaskPhrase(
        aiResult: com.example.myapplication.ai.AiParsedCommand,
        normalized: String
    ): String {
        return aiResult.targetTaskTitle
            ?: aiResult.taskTitle
            ?: if (!aiResult.targetDateText.isNullOrBlank() || !aiResult.targetTimeText.isNullOrBlank() || !aiResult.dateText.isNullOrBlank() || !aiResult.timeText.isNullOrBlank()) "" else normalized
    }



    override fun onDestroy() {
        super.onDestroy()
        assistantSession.destroy()
        voiceHelper.shutdown()
    }
}

// this is a comment for version tally, the current version is 2.4
