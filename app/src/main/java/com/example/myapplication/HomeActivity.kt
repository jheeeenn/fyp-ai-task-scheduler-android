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


import com.example.myapplication.ai.AiRouter
import com.example.myapplication.ai.GeminiCloudNlpExtractor


import com.example.myapplication.voice.TextNormalizer

import com.example.myapplication.BuildConfig // for gemini api key
import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.LocalConversationIntentClassifier
import com.example.myapplication.ai.LocalIntentClassifier
import com.example.myapplication.ai.LocalTaskParser
import com.example.myapplication.ai.agent.ActionValidator
import com.example.myapplication.ai.agent.AgentOrchestrator
import com.example.myapplication.ai.agent.LaptopAgentClient
import com.example.myapplication.ai.agent.TaskActionNormalizer
import com.example.myapplication.ai.agent.TaskAgentResponseParser
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
class HomeActivity : AppCompatActivity(), AssistantVoiceHost{
    private var shouldOpenAssistantOnResume = false
    private lateinit var conversationIntentClassifier: LocalConversationIntentClassifier

    private lateinit var responseManager: AssistantResponseManager
    private lateinit var aiRouter: AiRouter
    private lateinit var agentOrchestrator: AgentOrchestrator


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

    //for delete confirmation when the task intent is 'delete'
    private var pendingDeleteTaskId: Long? = null
    private var pendingDeleteTaskTitle: String? = null

    // for AI breakdown planning
    private var pendingBreakdownTitle: String? = null
    private var pendingBreakdownPlan: List<String> = emptyList()
    private var pendingBreakdownOriginalRequest: String? = null
    private var pendingBreakdownDateText: String? = null
    private var pendingBreakdownTimeText: String? = null
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

        val localIntentClassifier = LocalIntentClassifier(this)
        val localTaskParser = LocalTaskParser()
        //Log.d("GEMINI_KEY_CHECK", "key='${BuildConfig.GEMINI_API_KEY}'")
        val cloudExtractor = GeminiCloudNlpExtractor(
            BuildConfig.GEMINI_API_KEY
        )

        aiRouter = AiRouter(
            localIntentClassifier,
            localTaskParser,
            cloudExtractor
        )
        agentOrchestrator = AgentOrchestrator(
            LaptopAgentClient(),
            TaskAgentResponseParser(),
            TaskActionNormalizer(),
            ActionValidator(),
            aiRouter
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
                // log
                Log.d("HOME_ROUTING", "falling through to AgentOrchestrator with text='$normalized'")
                val aiResult = agentOrchestrator.process(normalized)

                Log.d(
                    "AI_ROUTER",
                    "intent=${aiResult.intent}, title=${aiResult.taskTitle}, date=${aiResult.dateText}," +
                            " time=${aiResult.timeText}, source=${aiResult.source}, confidence=${aiResult.confidence}"
                )

                // branches for actions
                when (aiResult.intent) {
                    // create task
                    AiIntent.CREATE_TASK.name -> {
                        //log
                        Log.d("HOME_ACTION", "CREATE_TASK -> open CreateTaskActivity")

                        val reply = responseManager.openCreateTaskReply(aiResult.source)

                        assistantSession.speakThenRun(reply) {
                            val openCreateIntent = Intent(this@HomeActivity, CreateTaskActivity::class.java).apply {
                                putExtra("prefill_title", aiResult.taskTitle)
                                putExtra("prefill_date_text", aiResult.dateText)
                                putExtra("prefill_time_text", aiResult.timeText)
                            }
                            startActivity(openCreateIntent)
                        }
                    }

                    // query on task
                    AiIntent.QUERY_TASK.name -> {
                        // log
                        Log.d("HOME_ACTION", "QUERY_TASK -> handleQueryTask")

                        handleQueryTask(normalized)
                    }
                    // delete task
                    AiIntent.DELETE_TASK.name -> {
                        Log.d("HOME_ACTION", "DELETE_TASK -> trying task match")

                        lifecycleScope.launch {
                            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()

                            // to prevent user accidently matching a completed task for deletion
                            val tasks = withContext(Dispatchers.IO) { dao.getRootActiveTasks() }

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
                                    assistantSession.speakThenListenAgain(
                                        responseManager.taskMatchNotFound()
                                    )
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
                            val tasks = withContext(Dispatchers.IO) { dao.getRootActiveTasks() }

                            val spokenPhrase = extractSpokenTaskPhrase(aiResult, normalized)
                            val matchResult = findTaskMatchResult(spokenPhrase, tasks)

                            when {
                                matchResult.isAmbiguous &&
                                        matchResult.bestTask != null &&
                                        matchResult.secondTask != null -> {
                                    askTaskMatchClarification(
                                        action = PendingTaskAction.EDIT,
                                        bestTask = matchResult.bestTask,
                                        secondTask = matchResult.secondTask
                                    )
                                }

                                matchResult.bestTask != null -> {
                                    val matchedTask = matchResult.bestTask
                                    val reply = responseManager.openEditTask()
                                    assistantSession.speakThenRun(reply) {
                                        val openEditIntent = Intent(this@HomeActivity, EditTaskActivity::class.java).apply {
                                            putExtra("task_id", matchedTask.id)
                                            putExtra("task_title", matchedTask.title)
                                            putExtra("task_date", matchedTask.dueDate)
                                            putExtra("task_time", matchedTask.dueTime)
                                            putExtra("opened_by_assistant", true)
                                        }
                                        startActivity(openEditIntent)
                                    }
                                }

                                else -> {
                                    val reply = responseManager.taskMatchNotFound()
                                    assistantSession.speakThenListenAgain(reply)
                                }
                            }
                        }
                    }
                    // reschedule task, changing the time and date

                    AiIntent.RESCHEDULE_TASK.name -> {
                        Log.d("HOME_ACTION", "RESCHEDULE_TASK -> trying task match")

                        lifecycleScope.launch {
                            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
                            val tasks = withContext(Dispatchers.IO) { dao.getRootActiveTasks() }

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
                                        rescheduleDateText = aiResult.dateText,
                                        rescheduleTimeText = aiResult.timeText
                                    )
                                }

                                matchResult.bestTask != null -> {
                                    val matchedTask = matchResult.bestTask

                                    assistantSession.speakThenRun(responseManager.openReschedule()) {
                                        val openRescheduleIntent = Intent(this@HomeActivity, EditTaskActivity::class.java).apply {
                                            putExtra("task_id", matchedTask.id)
                                            putExtra("task_title", matchedTask.title)
                                            putExtra("task_date", matchedTask.dueDate)
                                            putExtra("task_time", matchedTask.dueTime)
                                            putExtra("opened_by_assistant", true)
                                            putExtra("assistant_mode", "reschedule")
                                            putExtra("prefill_new_date_text", aiResult.dateText)
                                            putExtra("prefill_new_time_text", aiResult.timeText)
                                        }
                                        startActivity(openRescheduleIntent)
                                    }
                                }

                                else -> {
                                    assistantSession.speakThenListenAgain(
                                        responseManager.taskMatchNotFound()
                                    )
                                }
                            }
                        }
                    }

                    // to mark a task done (completing a task)
                    AiIntent.MARK_DONE.name -> {
                        Log.d("HOME_ACTION", "MARK_DONE -> trying task match")

                        lifecycleScope.launch {
                            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
                            val tasks = withContext(Dispatchers.IO) { dao.getActiveTasks() }

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

                                    assistantSession.speak(responseManager.markDoneSuccess(matchedTask.title), listenAgain = false)
                                }

                                else -> {
                                    assistantSession.speakThenListenAgain(
                                       // "I couldn't find a matching task. Please say the task title again."
                                        responseManager.taskMatchNotFound()

                                    )
                                }
                            }
                        }
                    }

                    // to undo a completed task back to a open state
                    AiIntent.MARK_UNDONE.name -> {
                        Log.d("HOME_ACTION", "MARK_UNDONE -> trying task match")

                        lifecycleScope.launch {
                            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
                            val tasks = withContext(Dispatchers.IO) { dao.getAll() }

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

                                    assistantSession.speak(responseManager.markUndoneSuccess(matchedTask.title), listenAgain = false)
                                }

                                else -> {
                                    assistantSession.speakThenListenAgain(
                                      //  "I couldn't find a matching task. Please say the task title again."
                                        responseManager.taskMatchNotFound()

                                    )
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
                            assistantSession.speakThenListenAgain(
                                "I could not create a clear breakdown yet. Please describe the large task again."
                            )
                        } else {
                            startBreakdownConfirmation(
                                title = title,
                                plan = plan,
                                originalRequest = normalized,
                                naturalResponse = aiResult.naturalResponse,
                                dateText = aiResult.dateText,
                                timeText = aiResult.timeText
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
            } catch (e: Exception) {
                Log.e("AI_ROUTER", "Crash in handleVoiceCommand", e)
                val reply = responseManager.parserCrash()
                assistantSession.speak(reply, listenAgain = false)
            }
        }
    }

    private fun todayDateString(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            .format(Calendar.getInstance().time)
    }

    private fun resolveQueryDate(normalized: String): String? {
        return ScheduleTextParser.parseDateFromSentence(normalized)
    }

    private fun handleQueryTask(normalized: String) {
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
            val queryDate = resolveQueryDate(normalized)

            lastQueryDate = queryDate
            lastQueryWasToday = queryDate == today

            val filteredTasks = if (!queryDate.isNullOrBlank()) {
                allTasks.filter { !it.isDone && it.dueDate == queryDate }
            } else {
                allTasks.filter { !it.isDone }
            }

            val replyMode = detectQueryReplyMode(normalized)
            val reply = buildTaskQueryReply(filteredTasks, lastQueryWasToday, replyMode)

            val hint = if (filteredTasks.isEmpty()) {
                responseManager.hintCreateOrRead()
            } else {
                when (replyMode) {
                    QueryReplyMode.SHORT, QueryReplyMode.NORMAL -> responseManager.hintYesNo()
                    QueryReplyMode.DETAILED -> responseManager.hintCreateOrRead()
                }
            }

            assistantSession.getBottomSheet()?.showAssistantHint(hint)

            homeFollowUpContext = when {
                filteredTasks.isEmpty() -> HomeFollowUpContext.AFTER_NO_TASKS
                replyMode == QueryReplyMode.DETAILED -> HomeFollowUpContext.AFTER_TASK_DETAILS
                else -> HomeFollowUpContext.AFTER_TASK_SUMMARY
            }
            // log
            Log.d(
                "HOME_QUERY",
                "queryDate=$queryDate queryToday=$lastQueryWasToday replyMode=$replyMode taskCount=${filteredTasks.size} nextContext=$homeFollowUpContext"
            )

            val spokenFollowUp = when {
                filteredTasks.isEmpty() -> responseManager.followUpCreateAfterNoTasks()
                replyMode == QueryReplyMode.DETAILED -> responseManager.followUpAnythingElse()
                else -> responseManager.followUpReadAllTasks()
            }

            val spokenReply = responseManager.combineReplyWithFollowUp(reply, spokenFollowUp)

            assistantSession.speak(
                text = spokenReply,
                listenAgain = true
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
        mode: QueryReplyMode
    ): String {
        if (tasks.isEmpty()) {
            return responseManager.queryNoTasks(queryToday)
        }

        return when (mode) {
            QueryReplyMode.SHORT -> buildShortQueryReply(tasks, queryToday)
            QueryReplyMode.NORMAL -> buildNormalQueryReply(tasks, queryToday)
            QueryReplyMode.DETAILED -> buildDetailedQueryReply(tasks, queryToday)
        }
    }

    private fun buildShortQueryReply(
        tasks: List<com.example.myapplication.data.TaskEntity>,
        queryToday: Boolean
    ): String {
        return responseManager.queryShortCount(tasks.size, queryToday)
    }

    private fun buildNormalQueryReply(
        tasks: List<com.example.myapplication.data.TaskEntity>,
        queryToday: Boolean
    ): String {
        val intro = responseManager.queryIntro(tasks.size, queryToday)

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
        queryToday: Boolean
    ): String {
        val intro = responseManager.queryIntro(tasks.size, queryToday)


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
            val queryDate = lastQueryDate
            val queryToday = queryDate == today

            val filteredTasks = if (!queryDate.isNullOrBlank()) {
                allTasks.filter { !it.isDone && it.dueDate == queryDate }
            } else {
                allTasks.filter { !it.isDone }
            }

            val reply = buildTaskQueryReply(
                tasks = filteredTasks,
                queryToday = queryToday,
                mode = QueryReplyMode.DETAILED
            )

            homeFollowUpContext = HomeFollowUpContext.AFTER_TASK_DETAILS

            assistantSession.getBottomSheet()?.showAssistantReply(reply)

            assistantSession.getBottomSheet()?.showAssistantHint(
                responseManager.hintCreateOrRead()
            )

            assistantSession.getBottomSheet()?.setIdleState()

            val spokenReply = responseManager.combineReplyWithFollowUp(
                reply,
                responseManager.followUpAnythingElse()
            )

            assistantSession.speak(
                text = spokenReply,
                listenAgain = true
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


    private fun findTaskMatchResult(
        spokenTitle: String?,
        tasks: List<com.example.myapplication.data.TaskEntity>
    ): com.example.myapplication.ai.TaskMatchResult {
        val result = TaskMatcher.findBestTaskMatch(spokenTitle, tasks)

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

        assistantSession.speakThenListenAgain(
            "Are you sure you want to delete ${task.title}?"
        )
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

            assistantSession.speakThenStop(responseManager.deleteSuccess(title))
        }
    }
    private fun startBreakdownConfirmation(
        title: String,
        plan: List<String>,
        originalRequest: String,
        naturalResponse: String?,
        dateText: String? = null,
        timeText: String? = null
    ) {
        pendingBreakdownTitle = title
        pendingBreakdownPlan = plan.take(4)
        pendingBreakdownOriginalRequest = originalRequest
        pendingBreakdownDateText = dateText
        pendingBreakdownTimeText = timeText
        homeFollowUpContext = HomeFollowUpContext.BREAKDOWN_CONFIRMATION

        assistantSession.getBottomSheet()?.showAssistantHint(
            "Say yes to create these subtasks, no to cancel, or describe how to change the plan."
        )

        assistantSession.speakThenListenAgain(
            buildBreakdownProposalSpeech(
                title = title,
                plan = pendingBreakdownPlan,
                naturalResponse = naturalResponse
            )
        )
    }

    private fun buildBreakdownProposalSpeech(
        title: String,
        plan: List<String>,
        naturalResponse: String?
    ): String {
        val intro = naturalResponse
            ?.takeIf { it.isNotBlank() }
            ?: "I prepared a breakdown for $title."

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
        val parsedSchedule = ScheduleTextParser.parse(pendingBreakdownDateText, pendingBreakdownTimeText)
        pendingBreakdownDateText = parsedSchedule.dueDate ?: pendingBreakdownDateText
        pendingBreakdownTimeText = parsedSchedule.dueTime ?: pendingBreakdownTimeText

        if (parsedSchedule.isComplete) {
            createPendingBreakdown(parsedSchedule.dueDate!!, parsedSchedule.dueTime!!)
            return
        }

        homeFollowUpContext = HomeFollowUpContext.BREAKDOWN_SCHEDULE_COLLECTION
        val prompt = when {
            !parsedSchedule.hasDate && !parsedSchedule.hasTime -> "When should I schedule this task?"
            !parsedSchedule.hasDate -> "What date should I schedule this task for?"
            else -> "What time should I schedule this task for?"
        }
        assistantSession.getBottomSheet()?.showAssistantHint(prompt)
        assistantSession.speakThenListenAgain(prompt)
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

        val parsedSchedule = ScheduleTextParser.merge(
            existingDateText = pendingBreakdownDateText,
            existingTimeText = pendingBreakdownTimeText,
            newText = normalized
        )
        pendingBreakdownDateText = parsedSchedule.dueDate ?: pendingBreakdownDateText
        pendingBreakdownTimeText = parsedSchedule.dueTime ?: pendingBreakdownTimeText

        if (parsedSchedule.isComplete) {
            createPendingBreakdown(parsedSchedule.dueDate!!, parsedSchedule.dueTime!!)
        } else {
            val prompt = when {
                !parsedSchedule.hasDate && !parsedSchedule.hasTime -> "When should I schedule this task?"
                !parsedSchedule.hasDate -> "What date should I schedule this task for?"
                else -> "What time should I schedule this task for?"
            }
            assistantSession.speakThenListenAgain(prompt)
        }
        return true
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

            assistantSession.speakThenStop(
                "I created $title with $count subtasks for $dueDate at $dueTime."
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
                        naturalResponse = result.naturalResponse,
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
        rescheduleDateText: String? = null,
        rescheduleTimeText: String? = null
    ) {

        taskResolutionState = TaskResolutionState(
            action = action,
            candidate1Id = bestTask.id,
            candidate2Id = secondTask.id,
            rescheduleDateText = rescheduleDateText,
            rescheduleTimeText = rescheduleTimeText
        )
        ambiguityRetryCount = 0
        homeFollowUpContext = HomeFollowUpContext.TASK_MATCH_AMBIGUITY

        assistantSession.getBottomSheet()?.showAssistantHint(responseManager.hintAmbiguityChoice())
        assistantSession.speakThenListenAgain(
            responseManager.taskMatchAmbiguous(bestTask.title, secondTask.title)
        )
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
            val rescheduleDate = taskResolutionState.rescheduleDateText
            val rescheduleTime = taskResolutionState.rescheduleTimeText

            clearPendingTaskMatchState()
            homeFollowUpContext = HomeFollowUpContext.NONE

            when (action) {
                PendingTaskAction.EDIT -> {
                    assistantSession.speakThenRun(responseManager.openEditTask()) {
                        val openEditIntent = Intent(this@HomeActivity, EditTaskActivity::class.java).apply {
                            putExtra("task_id", chosenTask.id)
                            putExtra("task_title", chosenTask.title)
                            putExtra("task_date", chosenTask.dueDate)
                            putExtra("task_time", chosenTask.dueTime)
                            putExtra("opened_by_assistant", true)
                        }
                        startActivity(openEditIntent)
                    }
                }

                PendingTaskAction.RESCHEDULE -> {
                    assistantSession.speakThenRun(responseManager.openReschedule()) {
                        val openRescheduleIntent = Intent(this@HomeActivity, EditTaskActivity::class.java).apply {
                            putExtra("task_id", chosenTask.id)
                            putExtra("task_title", chosenTask.title)
                            putExtra("task_date", chosenTask.dueDate)
                            putExtra("task_time", chosenTask.dueTime)
                            putExtra("opened_by_assistant", true)
                            putExtra("assistant_mode", "reschedule")
                            putExtra("prefill_new_date_text", rescheduleDate)
                            putExtra("prefill_new_time_text", rescheduleTime)
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
                    assistantSession.speak(responseManager.markDoneSuccess(chosenTask.title), listenAgain = false)
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
                    assistantSession.speak(responseManager.markUndoneSuccess(chosenTask.title), listenAgain = false)
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

                        assistantSession.speakThenStop(
                            if (title != null) {
                                "Okay, I will not delete $title."
                            } else {
                                "Okay, I will not delete it."
                            }
                        )
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
            ?: normalized
    }



    override fun onDestroy() {
        super.onDestroy()
        assistantSession.destroy()
        voiceHelper.shutdown()
    }
}

// this is a comment for version tally, the current version is 2.4