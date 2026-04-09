package com.example.myapplication

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Button
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

import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import com.example.myapplication.ai.AiRouter
import com.example.myapplication.ai.GeminiCloudNlpExtractor


import com.example.myapplication.voice.TextNormalizer

import com.example.myapplication.BuildConfig // for gemini api key
import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.LocalIntentClassifier
import com.example.myapplication.ai.LocalTaskParser
import com.example.myapplication.voice.AssistantResponseManager
import com.example.myapplication.voice.QueryDetailMode
import com.example.myapplication.voice.LocalDialogAct
import com.example.myapplication.voice.LocalDialogActInterpreter
import com.example.myapplication.voice.VoiceSessionController
import com.example.myapplication.voice.VoiceSessionState
import java.text.SimpleDateFormat

class HomeActivity : AppCompatActivity() {
    private lateinit var responseManager: AssistantResponseManager
    private lateinit var aiRouter: AiRouter
    private lateinit var sessionController: VoiceSessionController
    private var speechRecognizer: SpeechRecognizer? = null
    private var assistantBottomSheet: AssistantBottomSheet? = null
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
        AFTER_TASK_DETAILS
    }

    private var homeFollowUpContext = HomeFollowUpContext.NONE
    private var lastQueryWasToday = false

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startVoiceRecognition()
            } else {
                voiceHelper.speak(responseManager.microphonePermissionNeeded())
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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val localIntentClassifier = LocalIntentClassifier(this)
        val localTaskParser = LocalTaskParser()
        Log.d("GEMINI_KEY_CHECK", "key='${BuildConfig.GEMINI_API_KEY}'")
        val cloudExtractor = GeminiCloudNlpExtractor(
            BuildConfig.GEMINI_API_KEY
        )

        aiRouter = AiRouter(
            localIntentClassifier,
            localTaskParser,
            cloudExtractor
        )

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
        btnTodayTasks.setOnClickListener {
            //startActivity(Intent(this, TodayTasksActivity::class.java))
            speakThenOpen("opening today's task.") {
                startActivity(Intent(this, TodayTasksActivity::class.java))
            }
        }
        btnCreateTask.setOnClickListener {
            speakThenOpen("opening task create.") {
                startActivity(Intent(this, CreateTaskActivity::class.java))
            }
        }

        btnScheduledTasks.setOnClickListener {
            speakThenOpen("opening scheduled task.") {
                startActivity(Intent(this, MainActivity::class.java))
            }
        }

        btnSettings.setOnClickListener {
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
        sessionController = VoiceSessionController(voiceHelper) { state ->
            when (state) {
                VoiceSessionState.LISTENING -> assistantBottomSheet?.setListeningState()
                VoiceSessionState.PROCESSING -> assistantBottomSheet?.setProcessingState()
                VoiceSessionState.SPEAKING -> assistantBottomSheet?.setSpeakingState()
                VoiceSessionState.IDLE -> assistantBottomSheet?.setIdleState()
                VoiceSessionState.STOPPED -> assistantBottomSheet?.setIdleState()
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
                    if (!sessionController.canHandleRecognizerCallbacks()) {
                        return
                    }
                    sessionController.onRecognizerError()

                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            handleListenFailure(responseManager.listenFailure())
                        }

                        SpeechRecognizer.ERROR_CLIENT,
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            // ignore these transient recognizer-side interruptions
                        }

                        else -> {
                            handleListenFailure(responseManager.listenFailure())
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    if (!sessionController.canHandleRecognizerCallbacks()) {
                        return
                    }

                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spokenText = matches?.firstOrNull()?.trim()?.lowercase()

                    if (!spokenText.isNullOrEmpty()) {
                        sessionController.onFinalSpeechReceived()
                        assistantBottomSheet?.showUserSpeech(spokenText)
                        assistantBottomSheet?.setProcessingState()
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
    } // end of onCreate

    override fun onResume(){
        super.onResume()

        refreshOverview()

        if(!hasShownPermissionDialog && !allRequiredPermissionsReady()){
            hasShownPermissionDialog = true
            showReminderSetupDialog()
        }
    }
    private fun refreshOverview(){
        val overviewText = findViewById<TextView>(R.id.overviewText)
        val dao = AppDatabase.getInstance(this).taskDao()

        lifecycleScope.launch{
            val tasks = withContext(Dispatchers.IO){dao.getAll()}
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

    private fun handleVoiceCommand(command: String) {
        val normalized = TextNormalizer.normalize(command)
        when (LocalDialogActInterpreter.detect(normalized)) {
            LocalDialogAct.CANCEL,
            LocalDialogAct.STOP,
            LocalDialogAct.END -> {
                endAssistantConversation()
                return
            }
            LocalDialogAct.DELETE,
            LocalDialogAct.DONE,
            LocalDialogAct.UNDO -> {
                speakThenListenAgain("Please say the task title too, for example: mark homework done.")
                return
            }
            else -> {}
        }

        if (isConversationExitCommand(normalized)) {
            endAssistantConversation()
            return
        }
        if (handleHomeFollowUp(normalized)) {
            return
        }
        lifecycleScope.launch {
            try {
                val aiResult = aiRouter.process(normalized)

                Log.d(
                    "AI_ROUTER",
                    "intent=${aiResult.intent}, title=${aiResult.taskTitle}, date=${aiResult.dateText}," +
                            " time=${aiResult.timeText}, source=${aiResult.source}, confidence=${aiResult.confidence}"
                )
                // create task
                when (aiResult.intent) {
                    AiIntent.CREATE_TASK.name -> {
                        val reply = responseManager.openCreateTaskReply(aiResult.source)

                        speakThenNavigate(reply) {
                            val intent = Intent(this@HomeActivity, CreateTaskActivity::class.java).apply {
                                putExtra("prefill_title", aiResult.taskTitle)
                                putExtra("prefill_date_text", aiResult.dateText)
                                putExtra("prefill_time_text", aiResult.timeText)
                            }
                            startActivity(intent)
                        }
                    }
                    // query on task
                    AiIntent.QUERY_TASK.name -> {
                        handleQueryTask(normalized)
                    }
                    // delete task
                    AiIntent.DELETE_TASK.name -> {
                        lifecycleScope.launch {
                            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
                            val tasks = withContext(Dispatchers.IO) { dao.getAll() }
                            val matchedTask = findBestTaskMatch(aiResult.targetTaskTitle ?: aiResult.taskTitle, tasks)

                            if (matchedTask == null) {
                                val reply = "I couldn't find that task. Please say the task title again."
                                /*assistantBottomSheet?.showAssistantReply(reply)
                                assistantBottomSheet?.setSpeakingState()
                                voiceHelper.speak(reply) {
                                    runOnUiThread { assistantBottomSheet?.setIdleState() }
                                }*/
                                speakThenListenAgain(reply)
                            } else {
                                withContext(Dispatchers.IO) {
                                    dao.deleteById(matchedTask.id)
                                }

                                ReminderHelper.cancelReminder(this@HomeActivity, matchedTask.id.toInt())
                                refreshOverview()

                                val reply = "${matchedTask.title} deleted."
                                assistantBottomSheet?.showAssistantReply(reply)
                                assistantBottomSheet?.setSpeakingState()
                                voiceHelper.speak(reply) {
                                    runOnUiThread { assistantBottomSheet?.setIdleState() }
                                }
                            }
                        }
                    }
                    // edit task or update it
                    /*AiIntent.UPDATE_TASK.name -> {
                        val reply = responseManager.updateNotReady()
                        assistantBottomSheet?.showAssistantReply(reply)
                        assistantBottomSheet?.setSpeakingState()
                        voiceHelper.speak(reply) {
                            runOnUiThread {
                                assistantBottomSheet?.setIdleState()
                            }
                        }
                    }*/
                    AiIntent.UPDATE_TASK.name -> {
                        lifecycleScope.launch {
                            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
                            val tasks = withContext(Dispatchers.IO) { dao.getActiveTasks() }
                            val matchedTask = findBestTaskMatch(aiResult.targetTaskTitle ?: aiResult.taskTitle, tasks)

                            if (matchedTask == null) {
                                val reply = "I couldn't find that task. Please try saying the task title again."
                                /*assistantBottomSheet?.showAssistantReply(reply)
                                assistantBottomSheet?.setSpeakingState()
                                voiceHelper.speak(reply) {
                                    runOnUiThread { assistantBottomSheet?.setIdleState() }
                                }*/
                                speakThenListenAgain(reply)
                            } else {
                                val reply = "Okay, opening edit task."
                                speakThenNavigate(reply) {
                                    val intent = Intent(this@HomeActivity, EditTaskActivity::class.java).apply {
                                        putExtra("task_id", matchedTask.id)
                                        putExtra("task_title", matchedTask.title)
                                        putExtra("task_date", matchedTask.dueDate)
                                        putExtra("task_time", matchedTask.dueTime)
                                        putExtra("opened_by_assistant", true)
                                    }
                                    startActivity(intent)
                                }
                            }
                        }
                    }
                    // reschedule task, changing the time and date
                    /*AiIntent.RESCHEDULE_TASK.name -> {
                        val reply = responseManager.rescheduleNotReady()
                        assistantBottomSheet?.showAssistantReply(reply)
                        assistantBottomSheet?.setSpeakingState()
                        voiceHelper.speak(reply) {
                            runOnUiThread {
                                assistantBottomSheet?.setIdleState()
                            }
                        }
                    }*/
                    AiIntent.RESCHEDULE_TASK.name -> {
                        lifecycleScope.launch {
                            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
                            val tasks = withContext(Dispatchers.IO) { dao.getActiveTasks() }
                            val matchedTask = findBestTaskMatch(aiResult.targetTaskTitle ?: aiResult.taskTitle, tasks)

                            if (matchedTask == null) {
                                val reply = "I couldn't find that task. Please try saying the task title again."
                                /*assistantBottomSheet?.showAssistantReply(reply)
                                assistantBottomSheet?.setSpeakingState()
                                voiceHelper.speak(reply) {
                                    runOnUiThread { assistantBottomSheet?.setIdleState() }
                                }*/
                                speakThenListenAgain(reply)
                            } else {
                                val reply = "Okay, opening reschedule."
                                speakThenNavigate(reply) {
                                    val intent = Intent(this@HomeActivity, EditTaskActivity::class.java).apply {
                                        putExtra("task_id", matchedTask.id)
                                        putExtra("task_title", matchedTask.title)
                                        putExtra("task_date", matchedTask.dueDate)
                                        putExtra("task_time", matchedTask.dueTime)
                                        putExtra("opened_by_assistant", true)
                                        putExtra("assistant_mode", "reschedule")
                                        putExtra("prefill_new_date_text", aiResult.dateText)
                                        putExtra("prefill_new_time_text", aiResult.timeText)
                                    }
                                    startActivity(intent)
                                }
                            }
                        }
                    }

                    AiIntent.MARK_DONE.name -> {
                        lifecycleScope.launch {
                            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
                            val tasks = withContext(Dispatchers.IO) { dao.getActiveTasks() }
                            val matchedTask = findBestTaskMatch(aiResult.targetTaskTitle ?: aiResult.taskTitle, tasks)

                            if (matchedTask == null) {
                                val reply = "I couldn't find that task. Please say the task title again."
                                /*assistantBottomSheet?.showAssistantReply(reply)
                                assistantBottomSheet?.setSpeakingState()
                                voiceHelper.speak(reply) {
                                    runOnUiThread { assistantBottomSheet?.setIdleState() }
                                }*/
                                speakThenListenAgain(reply)
                            } else {
                                withContext(Dispatchers.IO) {
                                    dao.updateDoneStatus(matchedTask.id, true)
                                }

                                ReminderHelper.cancelReminder(this@HomeActivity, matchedTask.id.toInt())
                                refreshOverview()

                                val reply = "${matchedTask.title} marked as done."
                                assistantBottomSheet?.showAssistantReply(reply)
                                assistantBottomSheet?.setSpeakingState()
                                voiceHelper.speak(reply) {
                                    runOnUiThread { assistantBottomSheet?.setIdleState() }
                                }
                            }
                        }
                    }

                    AiIntent.MARK_UNDONE.name -> {
                        lifecycleScope.launch {
                            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
                            val tasks = withContext(Dispatchers.IO) { dao.getAll() }
                            val matchedTask = findBestTaskMatch(aiResult.targetTaskTitle ?: aiResult.taskTitle, tasks)

                            if (matchedTask == null) {
                                val reply = "I couldn't find that task. Please say the task title again."
                               /* assistantBottomSheet?.showAssistantReply(reply)
                                assistantBottomSheet?.setSpeakingState()
                                voiceHelper.speak(reply) {
                                    runOnUiThread { assistantBottomSheet?.setIdleState() }
                                }*/
                                speakThenListenAgain(reply)
                            } else {
                                withContext(Dispatchers.IO) {
                                    dao.updateDoneStatus(matchedTask.id, false)
                                }

                                val reopenedTask = matchedTask.copy(isDone = false)
                                ReminderHelper.scheduleReminderFromTask(this@HomeActivity, reopenedTask)
                                refreshOverview()

                                val reply = "${matchedTask.title} marked as not done."
                                assistantBottomSheet?.showAssistantReply(reply)
                                assistantBottomSheet?.setSpeakingState()
                                voiceHelper.speak(reply) {
                                    runOnUiThread { assistantBottomSheet?.setIdleState() }
                                }
                            }
                        }
                    }

                    else -> {
                        val reply = responseManager.unknownCommand()
                        assistantBottomSheet?.showAssistantReply(reply)
                        assistantBottomSheet?.setSpeakingState()
                        voiceHelper.speak(reply) {
                            runOnUiThread {
                                assistantBottomSheet?.setIdleState()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AI_ROUTER", "Crash in handleVoiceCommand", e)
                val reply = responseManager.parserCrash()
                assistantBottomSheet?.showAssistantReply(reply)
                assistantBottomSheet?.setErrorState(reply)
                assistantBottomSheet?.setSpeakingState()
                voiceHelper.speak(reply) {
                    runOnUiThread {
                        assistantBottomSheet?.setIdleState()
                    }
                }
            }
        }
    }


    private fun handleQueryTask(normalized: String) {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
            val allTasks = withContext(Dispatchers.IO) { dao.getAll() }

            val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                .format(Calendar.getInstance().time)

            val queryToday =
                normalized.contains("today") ||
                        normalized.contains("anything today") ||
                        normalized.contains("tasks today")

            lastQueryWasToday = queryToday

            val filteredTasks = if (queryToday) {
                allTasks.filter { !it.isDone && it.dueDate == today }
            } else {
                allTasks.filter { !it.isDone }
            }

            val replyMode = detectQueryReplyMode(normalized)
            val reply = buildTaskQueryReply(filteredTasks, queryToday, replyMode)

            assistantBottomSheet?.showAssistantReply(reply)

            val hint = if (filteredTasks.isEmpty()) {
                responseManager.hintCreateOrRead()
            } else {
                when (replyMode) {
                    QueryReplyMode.SHORT, QueryReplyMode.NORMAL -> responseManager.followUpReadAllTasks()
                    QueryReplyMode.DETAILED -> responseManager.followUpAnythingElse()
                }
            }

            assistantBottomSheet?.showAssistantHint(hint)
            assistantBottomSheet?.setIdleState()

            homeFollowUpContext = when {
                filteredTasks.isEmpty() -> HomeFollowUpContext.AFTER_NO_TASKS
                replyMode == QueryReplyMode.DETAILED -> HomeFollowUpContext.AFTER_TASK_DETAILS
                else -> HomeFollowUpContext.AFTER_TASK_SUMMARY
            }

            val spokenFollowUp = when {
                filteredTasks.isEmpty() -> responseManager.followUpCreateAfterNoTasks()
                replyMode == QueryReplyMode.DETAILED -> responseManager.followUpAnythingElse()
                else -> responseManager.followUpReadAllTasks()
            }

            val spokenReply = responseManager.combineReplyWithFollowUp(reply, spokenFollowUp)

            assistantBottomSheet?.setSpeakingState()
            voiceHelper.speak(spokenReply) {
                runOnUiThread {
                    if (sessionController.isSessionActive()) {
                        sessionController.onPartialSpeech()

                        startVoiceFlow()
                    } else {
                        assistantBottomSheet?.setIdleState()
                    }
                }
            }
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

        /*val taskDetails = tasks.take(responseManager.getMaxTasksForMode(QueryDetailMode.NORMAL)).joinToString(" ") { task ->
            buildCompactTaskSpeech(task)
        }

        *//*val moreText = if (tasks.size > 2) {
            responseManager.queryAndMore(tasks.size - 2)
        } else {
            ""
        }*/
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

        /*val taskDetails = tasks.take(responseManager.getMaxTasksForMode(QueryDetailMode.DETAILED)).joinToString(" ") { task ->
            buildSingleTaskSpeech(task)
        }

        val moreText = if (tasks.size > 5) {
            responseManager.queryAndMore(tasks.size - 5)
        } else {
            ""
        }*/

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

        return if (hasTime) {
            "$title at ${task.dueTime}."
        } else {
            "$title."
        }
    }

    private fun buildSingleTaskSpeech(task: com.example.myapplication.data.TaskEntity): String {
        val title = task.title.ifBlank { "Untitled task" }

        val hasDate = !task.dueDate.isNullOrBlank()
        val hasTime = !task.dueTime.isNullOrBlank()

        return when {
            hasDate && hasTime -> "$title on ${formatDateForSpeech(task.dueDate)} at ${task.dueTime}."
            hasDate -> "$title on ${formatDateForSpeech(task.dueDate)}."
            hasTime -> "$title at ${task.dueTime}."
            else -> "$title."
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
    private fun speakThenNavigate(reply: String, action: () -> Unit) {
        assistantBottomSheet?.showAssistantReply(reply)
        assistantBottomSheet?.setSpeakingState()

        voiceHelper.speak(reply) {
            runOnUiThread {
                assistantBottomSheet?.setIdleState()
                assistantBottomSheet?.dismiss()
                action()
            }
        }
    }

    private fun speakThenListenAgain(reply: String) {
        assistantBottomSheet?.showAssistantReply(reply)
        assistantBottomSheet?.setSpeakingState()

        voiceHelper.speak(reply) {
            runOnUiThread {
                if (sessionController.isSessionActive()) {
                    assistantBottomSheet?.setListeningState()
                    startVoiceFlow()
                } else {
                    assistantBottomSheet?.setIdleState()
                }
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
                    onDone = {
                        runOnUiThread {
                            try { speechRecognizer?.cancel() } catch (_: Exception) {}
                            assistantBottomSheet?.dismiss()
                        }
                    }
                )
            }
        )
    }

    // --> temproray functions before adding this exit intent in Ai router
    private fun isConversationExitCommand(normalized: String): Boolean {
        val exitPhrases = listOf(
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

        return exitPhrases.any { phrase -> normalized.contains(phrase) }
    }
    private fun endAssistantConversation() {
        sessionController.deactivateSession()
        val reply = responseManager.stopListening()

        assistantBottomSheet?.showAssistantReply(reply)
        assistantBottomSheet?.clearHint()
        assistantBottomSheet?.setSpeakingState()

        voiceHelper.speak(reply) {
            runOnUiThread {
                try { speechRecognizer?.cancel() } catch (_: Exception) {}
                assistantBottomSheet?.setIdleState()
                assistantBottomSheet?.dismiss()
            }
        }
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

                    normalized == "no" ||
                            normalized.contains("nothing else") ||
                            normalized.contains("that's all") ||
                            normalized.contains("done") -> {
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

                    normalized == "no" ||
                            normalized.contains("nothing else") ||
                            normalized.contains("that's all") ||
                            normalized.contains("done") -> {
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

                    normalized == "no" ||
                            normalized.contains("nothing else") ||
                            normalized.contains("that's all") ||
                            normalized.contains("done") -> {
                        endAssistantConversation()
                        true
                    }

                    else -> false
                }
            }

            HomeFollowUpContext.NONE -> false
        }
    }
    private fun handleDetailedFollowUpQuery() {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
            val allTasks = withContext(Dispatchers.IO) { dao.getAll() }

            val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                .format(Calendar.getInstance().time)

            val filteredTasks = if (lastQueryWasToday) {
                allTasks.filter { !it.isDone && it.dueDate == today }
            } else {
                allTasks.filter { !it.isDone }
            }

            val reply = buildTaskQueryReply(
                tasks = filteredTasks,
                queryToday = lastQueryWasToday,
                mode = QueryReplyMode.DETAILED
            )

            homeFollowUpContext = HomeFollowUpContext.AFTER_TASK_DETAILS

            assistantBottomSheet?.showAssistantReply(reply)
            assistantBottomSheet?.showAssistantHint(responseManager.followUpAnythingElse())
            assistantBottomSheet?.setIdleState()

            val spokenReply = responseManager.combineReplyWithFollowUp(
                reply,
                responseManager.followUpAnythingElse()
            )

            assistantBottomSheet?.setSpeakingState()
            voiceHelper.speak(spokenReply) {
                runOnUiThread {
                    if (sessionController.isSessionActive()) {
                        sessionController.onPartialSpeech()
                        startVoiceFlow()
                    } else {
                        assistantBottomSheet?.setIdleState()
                    }
                }
            }
        }
    }
    private fun openCreateTaskFromFollowUp() {
        homeFollowUpContext = HomeFollowUpContext.NONE
        val reply = responseManager.followUpCreateAccepted()

        speakThenNavigate(reply) {
            startActivity(Intent(this@HomeActivity, CreateTaskActivity::class.java))
        }
    }
    private fun normalizeTaskMatchText(text: String): String {
        var value = text.lowercase().trim()
        value = value.replace(Regex("[^a-z0-9\\s]"), "")
        value = value.replace(Regex("\\s+"), " ").trim()

        if (value.endsWith("s") && value.length > 3) {
            value = value.dropLast(1)
        }

        return value
    }

    private fun findBestTaskMatch(
        spokenTitle: String?,
        tasks: List<com.example.myapplication.data.TaskEntity>
    ): com.example.myapplication.data.TaskEntity? {
        if (spokenTitle.isNullOrBlank()) return null

        val normalizedSpoken = normalizeTaskMatchText(spokenTitle)

        return tasks.firstOrNull { normalizeTaskMatchText(it.title) == normalizedSpoken }
            ?: tasks.firstOrNull { normalizeTaskMatchText(it.title).contains(normalizedSpoken) }
            ?: tasks.firstOrNull { normalizedSpoken.contains(normalizeTaskMatchText(it.title)) }
    }

    private fun forceStopAssistant() {
        sessionController.hardStop(
            cancelRecognizer = { try { speechRecognizer?.cancel() } catch (_: Exception) {} },
            dismissPanel = {
                assistantBottomSheet?.clearHint()
                assistantBottomSheet?.dismiss()
            },
            clearConversationState = { homeFollowUpContext = HomeFollowUpContext.NONE }
        )
    }

    private fun speakThenOpen(reply: String, action: () -> Unit) {
        voiceHelper.speak(reply) {
            runOnUiThread { action() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        voiceHelper.shutdown()
    }
}
