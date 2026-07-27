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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale




import com.example.myapplication.voice.TextNormalizer

import com.example.myapplication.ai.AiIntent
import com.example.myapplication.ai.TaskQueryPresentation
import com.example.myapplication.ai.TaskQueryPresentationReconciler
import com.example.myapplication.ai.LocalConversationIntentClassifier
import com.example.myapplication.ai.agent.ActionValidator
import com.example.myapplication.ai.agent.AgentOrchestrator
import com.example.myapplication.ai.agent.LaptopAgentClient
import com.example.myapplication.ai.agent.TaskActionNormalizer
import com.example.myapplication.ai.agent.TaskAgentResponseParser
import com.example.myapplication.ai.agent.TaskAgentProcessingException
import com.example.myapplication.ai.agent.ContextActionChangeSet
import com.example.myapplication.ai.conversation.ConversationAgentClient
import com.example.myapplication.ai.conversation.AppGuidanceContext
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationDecisionParser
import com.example.myapplication.ai.conversation.ConversationOrchestrator
import com.example.myapplication.ai.conversation.ConversationOrchestratorException
import com.example.myapplication.ai.conversation.ConversationRoute
import com.example.myapplication.ai.conversation.ConversationQueryReadingMove
import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.conversation.DailyBriefingSnapshotBuilder
import com.example.myapplication.ai.conversation.DailyBriefingSpeechRenderer
import com.example.myapplication.ai.conversation.AllowedUserMove
import com.example.myapplication.ai.conversation.AndroidObservationResponseRenderer
import com.example.myapplication.ai.conversation.ConversationResponse
import com.example.myapplication.ai.conversation.ExecutionObservation
import com.example.myapplication.ai.conversation.ExecutionOperation
import com.example.myapplication.ai.conversation.ExecutionOutcome
import com.example.myapplication.ai.conversation.ObservedTask
import com.example.myapplication.ai.conversation.RequiredInput
import com.example.myapplication.ai.conversation.TaskObservationMapper
import com.example.myapplication.ai.conversation.TaskQueryPageObservation
import com.example.myapplication.ai.conversation.TaskQueryPresentationLevel
import com.example.myapplication.ai.conversation.TaskQuerySpeechDetail
import com.example.myapplication.ai.conversation.TaskQuerySpeechTone
import com.example.myapplication.ai.conversation.TemporalObservationInputs
import com.example.myapplication.ai.conversation.SafeObservationDeliveryGuard
import com.example.myapplication.ai.conversation.SafeObservationDeliveryState
import com.example.myapplication.ai.conversation.SafeObservationInteraction
import com.example.myapplication.ai.conversation.SafeStyleTurnAuthorization
import com.example.myapplication.ai.conversation.AssistantRequestToken
import com.example.myapplication.ai.conversation.AssistantRequestTokenPolicy
import com.example.myapplication.ai.conversation.SafeStyleAuthorizationPolicy
import com.example.myapplication.ai.conversation.SafeStyleAuthorizationStatus
import com.example.myapplication.ai.conversation.taskcontext.ContextReferenceMutationGuard
import com.example.myapplication.ai.conversation.taskcontext.ContextItemRestatementDisposition
import com.example.myapplication.ai.conversation.taskcontext.ContextItemRestatementPolicy
import com.example.myapplication.ai.conversation.taskcontext.ContextItemReadDisposition
import com.example.myapplication.ai.conversation.taskcontext.ContextItemReadPolicy
import com.example.myapplication.ai.conversation.taskcontext.ContextActionDecisionValidator
import com.example.myapplication.ai.conversation.taskcontext.ContextActionValidationResult
import com.example.myapplication.ai.conversation.taskcontext.ContextActionRepairPolicy
import com.example.myapplication.ai.conversation.taskcontext.ContextActionTargetValidator
import com.example.myapplication.ai.conversation.taskcontext.ContextActionReferenceGroundingValidator
import com.example.myapplication.ai.conversation.taskcontext.ContextReadValidationResult
import com.example.myapplication.ai.conversation.taskcontext.ContextReadRepairDisposition
import com.example.myapplication.ai.conversation.taskcontext.ContextReadRepairPolicy
import com.example.myapplication.ai.conversation.taskcontext.ContextFocusCarryForwardPolicy
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextStore
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextCapture
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextReadValidator
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextResponseRenderer
import com.example.myapplication.ai.conversation.taskcontext.ValidatedContextRead
import com.example.myapplication.ai.conversation.query.AccessibleTaskQuerySession
import com.example.myapplication.ai.conversation.query.AuthoritativeRepeatState
import com.example.myapplication.ai.conversation.query.QueryReadingControlPolicy
import com.example.myapplication.ai.conversation.query.QueryReadingInteractionState
import com.example.myapplication.ai.conversation.query.RepeatableSpeechKind
import com.example.myapplication.voice.AssistantResponseManager
import com.example.myapplication.voice.AssistantTone
import com.example.myapplication.voice.AssistantVerbosity
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
import com.example.myapplication.ai.temporal.TemporalExpressionResolver
import com.example.myapplication.ai.temporal.TemporalPolicyResult
import com.example.myapplication.ai.temporal.TemporalUseCase
import com.example.myapplication.ai.routine.RoutineDraftController
import com.example.myapplication.ai.routine.RoutineDraftIssue
import com.example.myapplication.ai.routine.RoutineDraftState
import com.example.myapplication.ai.routine.RoutineDraftUpdate
import com.example.myapplication.ai.routine.RoutineFollowUpInterpreter
import com.example.myapplication.ai.routine.RoutineFollowUpMove
import com.example.myapplication.ai.routine.RoutineReminderScheduler
import com.example.myapplication.ai.routine.RoutineResultSpeechRenderer
import com.example.myapplication.ai.routine.RoutineTaskBatchCreator
import com.example.myapplication.ai.routine.RoutineTaskStore
import com.example.myapplication.diagnostics.DebugDiagnosticLog
class HomeActivity : AppCompatActivity(), AssistantVoiceHost{
    private var shouldOpenAssistantOnResume = false
    private lateinit var conversationIntentClassifier: LocalConversationIntentClassifier

    private lateinit var responseManager: AssistantResponseManager
    private lateinit var agentOrchestrator: AgentOrchestrator
    private lateinit var conversationOrchestrator: ConversationOrchestrator
    private val readOnlyTaskContextStore = ReadOnlyTaskContextStore()


    private lateinit var assistantSession: AssistantVoiceSession
    private lateinit var voiceHelper: VoiceHelper

    private enum class HomeFollowUpContext {
        NONE,
        AFTER_NO_TASKS,
        AFTER_TASK_SUMMARY,
        AFTER_TASK_DETAILS,
        AFTER_DAILY_BRIEFING,
        QUERY_COUNT,
        QUERY_PAGE,
        TASK_MATCH_AMBIGUITY,
        DELETE_CONFIRMATION,
        BREAKDOWN_CONFIRMATION,
        BREAKDOWN_SCHEDULE_COLLECTION
    }
    private enum class AssistantRequestInvalidationReason {
        NEW_COMMAND,
        USER_CANCELLED,
        SESSION_STOPPED,
        CONVERSATION_ENDED
    }
    private enum class RoutineResponseKind {
        EXTRACTION_FAILURE,
        ASK_SHARED_DATE,
        ASK_STEP_TIME,
        PROPOSAL,
        INVALID_DATE,
        INVALID_TIME,
        REVISION_HELP,
        ALREADY_SAVING,
        CANCELLED,
        SAVE_RESULT
    }
    private data class RoutineFollowUpOutcome(
        val result: String,
        val issue: RoutineDraftIssue? = null
    )
    private var taskResolutionState = TaskResolutionState()
    private var homeFollowUpContext = HomeFollowUpContext.NONE
    private val temporalQueryResolver = TemporalQueryResolver()
    private var accessibleTaskQuerySession: AccessibleTaskQuerySession? = null
    private var authoritativeRepeatState: AuthoritativeRepeatState? = null
    private var currentQueryPageRepeatState: AuthoritativeRepeatState? = null
    private var queryReadingStateGeneration: Long = 0
    private var assistantRequestGeneration: Long = 0
    private var assistantRequestActive: Boolean = false
    private val routineDraftController = RoutineDraftController()

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
        logRoutineExternalCancellation("PANEL_CANCEL")
        invalidateAssistantRequest(AssistantRequestInvalidationReason.USER_CANCELLED)
        clearConversationSessionContext()
        homeFollowUpContext = HomeFollowUpContext.NONE
        clearPendingTaskMatchState()
        clearPendingDeleteState()
        clearPendingBreakdownState()
        if (routineDraftController.state != RoutineDraftState.SAVING) {
            routineDraftController.clear()
        }
    }

    override fun onAssistantSessionStopped() {
        logRoutineExternalCancellation("SESSION_STOP")
        invalidateAssistantRequest(AssistantRequestInvalidationReason.SESSION_STOPPED)
        clearConversationSessionContext()
        homeFollowUpContext = HomeFollowUpContext.NONE
        clearPendingTaskMatchState()
        clearPendingDeleteState()
        clearPendingBreakdownState()
        if (routineDraftController.state != RoutineDraftState.SAVING) {
            routineDraftController.clear()
        }
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
        val interaction = when (homeFollowUpContext) {
            HomeFollowUpContext.NONE -> Pair(
                "No task follow-up is currently pending.",
                listOf(
                    "Ask for any supported task action.",
                    "Ask for app guidance or a capability explanation."
                )
            )

            HomeFollowUpContext.AFTER_NO_TASKS -> Pair(
                "The previous query found no matching tasks, and the assistant offered to open task creation.",
                listOf(
                    "Accept or decline the offer.",
                    "Ask what to say next.",
                    "Give another task command."
                )
            )

            HomeFollowUpContext.AFTER_TASK_SUMMARY -> Pair(
                "A task summary was read, and the assistant offered to read more details.",
                listOf(
                    "Ask what the first, second, or another supplied result was.",
                    "Ask for a supplied task's date, time, status, or subtask summary.",
                    "Give another task command using the task name, or end the assistant session."
                )
            )

            HomeFollowUpContext.AFTER_TASK_DETAILS -> Pair(
                "Detailed task results were already read.",
                listOf(
                    "Ask what the first, second, or another supplied result was.",
                    "Ask for a supplied task's date, time, status, or subtask summary.",
                    "Give another task command using the task name, or end the assistant session."
                )
            )

            HomeFollowUpContext.AFTER_DAILY_BRIEFING -> Pair(
                "An authoritative on-demand daily briefing was read.",
                listOf(
                    "Ask what the first, second, or another spoken task was.",
                    "Ask for a spoken task's date, time, status, or subtask summary.",
                    "Give a contextual update or reschedule request for a spoken task.",
                    "Ask to repeat the exact briefing, request the full task list, or end the session."
                )
            )

            HomeFollowUpContext.QUERY_COUNT -> Pair(
                "A task query count was read, but no task item has been exposed yet.",
                listOf(
                    "Accept the offer to hear the first group.",
                    "Decline the offer or end the assistant session.",
                    "Give another task command."
                )
            )

            HomeFollowUpContext.QUERY_PAGE -> Pair(
                "The current group of authoritative task-query results was read.",
                listOf(
                    "Say continue for the next group, repeat the current group, or stop.",
                    "Ask what the first, second, or another task in the current group was.",
                    "Ask for a current task's date, time, status, or subtask summary.",
                    "Give a contextual update or reschedule request for a task in the current group."
                )
            )

            HomeFollowUpContext.TASK_MATCH_AMBIGUITY -> Pair(
                "More than one task matched the request.",
                listOf(
                    "Choose one of the task names that was already read.",
                    "Cancel the task operation."
                )
            )

            HomeFollowUpContext.DELETE_CONFIRMATION -> Pair(
                "A deletion is waiting for confirmation and has not happened yet.",
                listOf(
                    "Confirm or decline the deletion.",
                    "Ask what the confirmation means."
                )
            )

            HomeFollowUpContext.BREAKDOWN_CONFIRMATION -> Pair(
                "A generated subtask plan is waiting for approval.",
                listOf(
                    "Approve or reject the plan.",
                    "Describe how the plan should change."
                )
            )

            HomeFollowUpContext.BREAKDOWN_SCHEDULE_COLLECTION -> Pair(
                "The assistant is collecting missing schedule information for an approved task breakdown.",
                listOf(
                    "Provide the requested date or time.",
                    "Cancel the task breakdown."
                )
            )
        }
        val routineInteraction = when (routineDraftController.state) {
            RoutineDraftState.NONE -> null
            RoutineDraftState.EXTRACTING -> Pair(
                "The bounded Task Agent is extracting a one-time routine proposal.",
                listOf("Wait for the proposal or cancel the routine request.")
            )
            RoutineDraftState.COLLECTING_SHARED_DATE -> Pair(
                "A one-time routine draft needs one exact shared date.",
                listOf("Provide an exact date or cancel the routine.")
            )
            RoutineDraftState.COLLECTING_STEP_TIME -> Pair(
                "A one-time routine draft needs an exact time for the next missing step.",
                listOf("Provide one exact time or cancel the routine.")
            )
            RoutineDraftState.WAITING_FOR_CONFIRMATION -> Pair(
                "An Android-authored routine proposal is waiting for explicit confirmation.",
                listOf(
                    "Confirm or reject the complete proposal.",
                    "Change one selected step's time or title, or change the shared date.",
                    "Repeat the stored authoritative proposal."
                )
            )
            RoutineDraftState.SAVING -> Pair(
                "Android is saving a confirmed one-time routine batch.",
                listOf("Wait for the authoritative save and reminder result.")
            )
        }

        val baseGuidanceContext = AppGuidanceContext(
            currentScreen = "Home",
            assistantPurpose = "Help a visually impaired user manage scheduled tasks through voice or typed assistant input.",
            supportedCapabilities = listOf(
                "Create a task.",
                "Query tasks by date, time, or date range.",
                "Provide an on-demand daily briefing covering overdue tasks, today's tasks, upcoming tasks within seven days, and one suggested focus.",
                "Update a task.",
                "Reschedule a task.",
                "Delete a task after confirmation.",
                "Mark a task complete.",
                "Mark a completed task incomplete.",
                "Break a large task into subtasks.",
                "Build a one-time routine containing 2 to 5 scheduled tasks, review every exact date and time, and save only after explicit confirmation."
            ),
            screenActions = listOf(
                "Open today's tasks.",
                "Open the create-task screen.",
                "Open scheduled tasks.",
                "Open settings.",
                "Start the voice assistant."
            ),
            inputMethods = listOf(
                "Activate the Talk Assistant button to speak.",
                "Long-press the Talk Assistant button to type an assistant request.",
                "Voice and typed inputs use the same assistant pipeline."
            ),
            interactionState = homeFollowUpContext.name,
            currentInteraction = interaction.first,
            currentInteractionGuidance = interaction.second,
            usageExamples = listOf(
                "Say, 'Show my tasks tomorrow.'",
                "Say, 'Give me my daily briefing.'",
                "Say, 'Create a task called revision tomorrow at 4 PM.'",
                "Say, 'How do I reschedule a task?' for app guidance."
            ),
            limitations = listOf(
                "A voice create request opens the create-task screen with recognised fields prefilled for review.",
                "Update and reschedule requests open the edit screen for the matched task.",
                "Delete requires confirmation before the app deletes the task.",
                "The app marks a matched task complete or incomplete.",
                "The app reads verified task-query results.",
                "Daily briefings are available on demand and are not delivered automatically on a schedule.",
                "The daily briefing's suggested focus uses deterministic due-date and time ordering, not behavioural learning, habit-based recommendations, priority fields, or calendar integration.",
                "After a daily briefing, the user may ask about one of the spoken tasks.",
                "Task breakdown requires plan approval and any missing scheduling information.",
                "A Smart Routine Builder routine is one confirmed batch of 2 to 5 independent root tasks, not a recurring template.",
                "Every routine task requires an exact future date and time, and Android reviews the complete routine before creation.",
                "The app does not permanently recur routines, automatically generate future routine instances, learn routine behaviour, or integrate routine creation with calendars.",
                "App guidance must not claim that an operation occurred unless the app successfully completed it."
            )
        )
        val guidanceContext = if (routineInteraction == null) {
            baseGuidanceContext
        } else {
            baseGuidanceContext.copy(
                interactionState = "SMART_ROUTINE_BUILDER_${routineDraftController.state.name}",
                currentInteraction = routineInteraction.first,
                currentInteractionGuidance = routineInteraction.second
            )
        }

        Log.d(
            "CONVO_APP_CONTEXT",
            "screen=${guidanceContext.currentScreen} interactionState=${guidanceContext.interactionState} " +
                    "capabilityCount=${guidanceContext.supportedCapabilities.size} " +
                    "inputMethodCount=${guidanceContext.inputMethods.size}"
        )
        return guidanceContext.toPromptText()
    }

    private fun handleVoiceCommand(command: String) {
        val normalized = TextNormalizer.normalize(command)
        val requestToken = beginAssistantRequest()
        val localStyleAuthorization = SafeStyleTurnAuthorization(
            requestGeneration = requestToken.requestGeneration,
            styleCallAllowed = true
        )

        // log
        Log.d(
            "HOME_VOICE",
            "inputChars=${command.length} normalizedChars=${normalized.length} " +
                "context=$homeFollowUpContext"
        )



        if (isConversationExitCommand(normalized)) {
            endAssistantConversation()
            return
        }

        if (routineDraftController.state != RoutineDraftState.NONE &&
            handleRoutineFollowUp(normalized, requestToken)
        ) {
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

        if (handleContextItemRestatement(normalized)) {
            return
        }

        if (handleContextItemRead(normalized)) {
            return
        }

        if (handleQueryReadingFollowUp(
                normalized,
                requestToken,
                localStyleAuthorization
            )
        ) {
            return
        }

        if (homeFollowUpContext != HomeFollowUpContext.NONE) {
            val shouldDeferContextReference =
                (homeFollowUpContext == HomeFollowUpContext.AFTER_TASK_SUMMARY ||
                    homeFollowUpContext == HomeFollowUpContext.AFTER_TASK_DETAILS ||
                    homeFollowUpContext == HomeFollowUpContext.AFTER_DAILY_BRIEFING ||
                    homeFollowUpContext == HomeFollowUpContext.QUERY_PAGE) &&
                    (ContextReferenceMutationGuard.containsContextReference(
                            normalized,
                            readOnlyTaskContextStore.snapshot()
                        ))

            if (!shouldDeferContextReference) {
                val convoResult = conversationIntentClassifier.classify(normalized)

                // log
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "HOME_CONVO",
                        "text='$normalized' predicted=${convoResult.intent} confidence=${convoResult.confidence} context=$homeFollowUpContext"
                    )
                }

                if (convoResult.intent != ConversationIntent.UNKNOWN &&
                    convoResult.confidence >= 0.30f) {
                    // log
                    Log.d("HOME_CONVO", "conversation intent accepted locally")

                    if (handleConversationIntent(convoResult.intent, normalized)) {
                        return
                    }
                } else {
                    //log
                    Log.d("HOME_CONVO", "conversation intent not accepted, falling through")
                }
            } else {
                Log.d("HOME_CONVO", "contextual reference deferred to Conversation Agent")
            }
        }

        if (handleHomeFollowUp(normalized)) {

            // log
            if (BuildConfig.DEBUG) {
                Log.d("HOME_FOLLOWUP", "handled by old hard-coded follow-up: '$normalized'")
            }
            return
        }
        lifecycleScope.launch {
            try {
                Log.d("CONVO_ORCH", "normalizedChars=${normalized.length}")
                val taskContextCapture = readOnlyTaskContextStore.capture()
                val isResultInteraction =
                    homeFollowUpContext == HomeFollowUpContext.AFTER_TASK_SUMMARY ||
                        homeFollowUpContext == HomeFollowUpContext.AFTER_TASK_DETAILS ||
                        homeFollowUpContext == HomeFollowUpContext.AFTER_DAILY_BRIEFING ||
                        homeFollowUpContext == HomeFollowUpContext.QUERY_PAGE
                val contextFocus = conversationOrchestrator.contextFocusForSnapshot(
                    taskContextCapture.snapshot
                )
                if (contextFocus != null) {
                    Log.d(
                        "HOME_CONTEXT_FOCUS",
                        "CONTEXT_FOCUS_AVAILABLE ref=${contextFocus.ref} " +
                            "generation=${contextFocus.generation} detail=${contextFocus.detail}"
                    )
                } else if (conversationOrchestrator.clearInvalidContextFocus(taskContextCapture.snapshot)) {
                    Log.d(
                        "HOME_CONTEXT_FOCUS",
                        "CONTEXT_FOCUS_STALE generation=${taskContextCapture.snapshot.generation}"
                    )
                }
                Log.d(
                    "HOME_CONTEXT_CAPTURE",
                    "scope=${taskContextCapture.snapshot.scope} " +
                        "generation=${taskContextCapture.snapshot.generation} " +
                        "itemCount=${taskContextCapture.snapshot.items.size} " +
                        "truncated=${taskContextCapture.snapshot.truncated}"
                )
                var conversationDecision = try {
                    conversationOrchestrator.process(
                        normalizedText = normalized,
                        appContextSummary = buildConversationAppContextSummary(),
                        readOnlyTaskContextSnapshot = taskContextCapture.promptText,
                        contextFocus = contextFocus
                    )
                } catch (e: ConversationOrchestratorException) {
                    Log.e("CONVO_ORCH", "Conversation Agent failed after schema retry", e)
                    val fallbackReply = "I could not understand that request correctly. Please try again."
                    conversationOrchestrator.commitFinalDecision(
                        ConversationDecision(
                            route = ConversationRoute.UNKNOWN,
                            reply = fallbackReply,
                            listenAgain = true,
                            source = "android_conversation_failure"
                        )
                    )
                    assistantSession.speak(
                        fallbackReply,
                        listenAgain = true
                    )
                    return@launch
                }
                val contextRepairEligible = ContextReadRepairPolicy.shouldAttempt(
                    primaryDecision = conversationDecision,
                    capturedSnapshot = taskContextCapture.snapshot,
                    isResultInteraction = isResultInteraction,
                    normalizedText = normalized
                )

                if (contextRepairEligible) {
                    Log.d("HOME_CONTEXT_REPAIR", "CONTEXT_REPAIR_ATTEMPTED")
                    try {
                        val repairedDecision = conversationOrchestrator.processContextReadRepair(
                            normalizedText = normalized,
                            readOnlyTaskContextSnapshot = taskContextCapture.promptText,
                            primaryRoute = conversationDecision.route,
                            currentInteraction = homeFollowUpContext.name,
                            contextFocus = contextFocus
                        )
                        val repairEvaluation = ContextReadRepairPolicy.evaluate(
                            normalizedText = normalized,
                            repairedDecision = repairedDecision,
                            capturedSnapshot = taskContextCapture.snapshot,
                            currentGeneration = readOnlyTaskContextStore.currentGeneration()
                        )
                        when (repairEvaluation.disposition) {
                            ContextReadRepairDisposition.ACCEPTED -> {
                                conversationDecision = repairedDecision
                                Log.d("HOME_CONTEXT_REPAIR", "CONTEXT_REPAIR_ACCEPTED")
                            }
                            ContextReadRepairDisposition.ABSTAINED ->
                                Log.d("HOME_CONTEXT_REPAIR", "CONTEXT_REPAIR_ABSTAINED")
                            ContextReadRepairDisposition.REJECTED ->
                                Log.d("HOME_CONTEXT_REPAIR", "CONTEXT_REPAIR_REJECTED")
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        Log.e("HOME_CONTEXT_REPAIR", "CONTEXT_REPAIR_FAILED")
                    }
                }

                val contextActionRepairEligible = !contextRepairEligible &&
                    ContextActionRepairPolicy.shouldAttempt(
                        normalizedText = normalized,
                        primaryDecision = conversationDecision,
                        capturedSnapshot = taskContextCapture.snapshot,
                        isResultInteraction = isResultInteraction,
                        contextFocus = contextFocus
                    )
                if (contextActionRepairEligible) {
                    Log.d("HOME_CONTEXT_ACTION_REPAIR", "CONTEXT_ACTION_REPAIR_ATTEMPTED")
                    try {
                        val repairedDecision = conversationOrchestrator.processContextActionRepair(
                            normalizedText = normalized,
                            readOnlyTaskContextSnapshot = taskContextCapture.promptText,
                            primaryRoute = conversationDecision.route,
                            currentInteraction = homeFollowUpContext.name,
                            contextFocus = contextFocus
                        )
                        when (repairedDecision.route) {
                            ConversationRoute.CONTEXT_ACTION -> {
                                val repairValidation = ContextActionDecisionValidator.validate(
                                    decision = repairedDecision,
                                    capturedSnapshot = taskContextCapture.snapshot,
                                    currentGeneration = readOnlyTaskContextStore.currentGeneration()
                                )
                                val repairGrounding = if (repairValidation.isValid) {
                                    ContextActionReferenceGroundingValidator.validate(
                                        normalizedText = normalized,
                                        decision = repairedDecision,
                                        capturedSnapshot = taskContextCapture.snapshot,
                                        currentFocus = contextFocus
                                    )
                                } else {
                                    null
                                }
                                if (repairValidation.isValid && repairGrounding?.isValid == true) {
                                    conversationDecision = repairedDecision
                                    Log.d("HOME_CONTEXT_ACTION_REPAIR", "CONTEXT_ACTION_REPAIR_ACCEPTED")
                                } else {
                                    if (repairValidation.isValid && repairGrounding != null) {
                                        conversationDecision = ConversationDecision(
                                            route = ConversationRoute.ASK_CLARIFICATION,
                                            reply = if (
                                                repairedDecision.contextAction ==
                                                ConversationContextAction.RESCHEDULE
                                            ) {
                                                "Which task do you want to reschedule?"
                                            } else {
                                                "Which task do you want to edit?"
                                            },
                                            listenAgain = true,
                                            source = "android_context_action_reference_grounding"
                                        )
                                    }
                                    Log.d("HOME_CONTEXT_ACTION_REPAIR", "CONTEXT_ACTION_REPAIR_REJECTED")
                                }
                            }
                            ConversationRoute.ASK_CLARIFICATION -> {
                                conversationDecision = repairedDecision.copy(
                                    reply = "Please say the explicit task name for that change."
                                )
                                Log.d("HOME_CONTEXT_ACTION_REPAIR", "CONTEXT_ACTION_REPAIR_ACCEPTED")
                            }
                            else -> Log.d(
                                "HOME_CONTEXT_ACTION_REPAIR",
                                "CONTEXT_ACTION_REPAIR_REJECTED"
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        Log.e("HOME_CONTEXT_ACTION_REPAIR", "CONTEXT_ACTION_REPAIR_FAILED")
                    }
                }
                if (contextActionRepairEligible &&
                    conversationDecision.source == "conversation_agent" &&
                    conversationDecision.route in setOf(
                        ConversationRoute.ASK_CLARIFICATION,
                        ConversationRoute.UNKNOWN
                    )
                ) {
                    conversationDecision = ConversationDecision(
                        route = ConversationRoute.ASK_CLARIFICATION,
                        reply = "Please say the explicit task name for that change.",
                        listenAgain = true,
                        source = "android_context_action_fail_closed"
                    )
                }

                if (
                    (conversationDecision.route == ConversationRoute.ASK_CLARIFICATION ||
                        conversationDecision.route == ConversationRoute.UNKNOWN) &&
                    isResultInteraction
                ) {
                    val focusFallback = ContextFocusCarryForwardPolicy.resolve(
                        normalizedText = normalized,
                        focus = contextFocus,
                        capturedSnapshot = taskContextCapture.snapshot,
                        isResultInteraction = true
                    )
                    val fallbackValidation = focusFallback?.let { candidate ->
                        ReadOnlyTaskContextReadValidator.validate(
                            decision = candidate,
                            capturedSnapshot = taskContextCapture.snapshot,
                            currentGeneration = readOnlyTaskContextStore.currentGeneration()
                        )
                    }
                    if (focusFallback != null && fallbackValidation?.isValid == true) {
                        conversationDecision = focusFallback
                        Log.d(
                            "HOME_CONTEXT_FOCUS",
                            "CONTEXT_FOCUS_FALLBACK_ACCEPTED ref=${focusFallback.contextRef} " +
                                "generation=${taskContextCapture.snapshot.generation} " +
                                "detail=${focusFallback.contextDetail}"
                        )
                    } else {
                        Log.d(
                            "HOME_CONTEXT_FOCUS",
                            "CONTEXT_FOCUS_FALLBACK_REJECTED generation=${taskContextCapture.snapshot.generation}"
                        )
                    }
                }

                if (conversationDecision.route != ConversationRoute.CONTEXT_READ) {
                    Log.d(
                        "CONVO_ORCH",
                        "route=${conversationDecision.route} confidence=${conversationDecision.confidence} " +
                                "source=${conversationDecision.source}"
                    )
                }

                val routedStyleAuthorization = SafeStyleTurnAuthorization(
                    requestGeneration = requestToken.requestGeneration,
                    styleCallAllowed =
                        conversationDecision.source == "conversation_agent" &&
                            !contextRepairEligible &&
                            !contextActionRepairEligible
                )
                val taskAgentInput: String
                when (conversationDecision.route) {
                    ConversationRoute.SMART_ROUTINE_BUILDER -> {
                        if (!isAssistantRequestCurrent(requestToken)) return@launch
                        conversationOrchestrator.commitFinalDecision(conversationDecision)
                        handleSmartRoutineBuilder(normalized, requestToken)
                        return@launch
                    }
                    ConversationRoute.DAILY_BRIEFING -> {
                        executeDailyBriefing(
                            requestToken = requestToken,
                            decision = conversationDecision
                        )
                        return@launch
                    }
                    ConversationRoute.CONTEXT_READ -> {
                        val validation = ReadOnlyTaskContextReadValidator.validate(
                            decision = conversationDecision,
                            capturedSnapshot = taskContextCapture.snapshot,
                            currentGeneration = readOnlyTaskContextStore.currentGeneration()
                        )
                        executeContextRead(
                            decision = conversationDecision,
                            taskContextCapture = taskContextCapture,
                            validation = validation
                        )
                        return@launch
                    }
                    ConversationRoute.CONTEXT_ACTION -> {
                        val validation = ContextActionDecisionValidator.validate(
                            decision = conversationDecision,
                            capturedSnapshot = taskContextCapture.snapshot,
                            currentGeneration = readOnlyTaskContextStore.currentGeneration()
                        )
                        Log.d(
                            "HOME_CONTEXT_ACTION",
                            "route=${conversationDecision.route} " +
                                "ref=${conversationDecision.contextRef} " +
                                "action=${conversationDecision.contextAction} " +
                                "capturedGeneration=${taskContextCapture.snapshot.generation} " +
                                "validation=${validation.result}"
                        )
                        if (!validation.isValid) {
                            val clarification = if (
                                validation.result == ContextActionValidationResult.STALE_GENERATION
                            ) {
                                "Those task results changed. Please repeat your task query."
                            } else {
                                "Please repeat the requested change."
                            }
                            rejectContextAction(clarification, "android_context_action_validation")
                            return@launch
                        }

                        val grounding = ContextActionReferenceGroundingValidator.validate(
                            normalizedText = normalized,
                            decision = conversationDecision,
                            capturedSnapshot = taskContextCapture.snapshot,
                            currentFocus = contextFocus
                        )
                        Log.d(
                            "HOME_CONTEXT_ACTION_GROUNDING",
                            "ref=${conversationDecision.contextRef} result=${grounding.result}"
                        )
                        if (!grounding.isValid) {
                            val clarification = if (
                                conversationDecision.contextAction == ConversationContextAction.RESCHEDULE
                            ) {
                                "Which task do you want to reschedule?"
                            } else {
                                "Which task do you want to edit?"
                            }
                            rejectContextAction(
                                clarification,
                                "android_context_action_reference_grounding"
                            )
                            return@launch
                        }

                        val capturedGeneration = taskContextCapture.snapshot.generation
                        val privateTaskId = readOnlyTaskContextStore.resolveRef(
                            ref = grounding.ref,
                            expectedGeneration = capturedGeneration
                        )
                        if (privateTaskId == null) {
                            rejectUnavailableContextAction()
                            return@launch
                        }
                        val taskDao = AppDatabase.getInstance(this@HomeActivity).taskDao()
                        val initiallyFetchedTask = withContext(Dispatchers.IO) {
                            taskDao.getById(privateTaskId)
                        }
                        val initiallyEligible = isEligibleContextActionTarget(initiallyFetchedTask)
                        Log.d(
                            "CONTEXT_ACTION_TARGET_RESOLVED",
                            "generation=$capturedGeneration eligible=$initiallyEligible"
                        )
                        if (!initiallyEligible) {
                            rejectUnavailableContextAction()
                            return@launch
                        }

                        val extractedChange = try {
                            agentOrchestrator.processContextAction(
                                normalizedText = normalized,
                                expectedAction = validation.action
                            )
                        } catch (_: TaskAgentProcessingException) {
                            rejectContextAction(
                                "Please repeat the requested change.",
                                "android_context_action_extraction"
                            )
                            return@launch
                        }
                        val hasDateChange = !extractedChange.newDateText.isNullOrBlank()
                        val hasTimeChange = !extractedChange.newTimeText.isNullOrBlank()
                        val extractedTemporalResolution =
                            if (
                                validation.action == ConversationContextAction.RESCHEDULE &&
                                (hasDateChange || hasTimeChange)
                            ) {
                                TemporalExpressionResolver().resolve(
                                    agentDateText = extractedChange.newDateText,
                                    agentTimeText = extractedChange.newTimeText,
                                    originalText = listOfNotNull(
                                        extractedChange.newDateText,
                                        extractedChange.newTimeText
                                    ).joinToString(" ")
                                )
                            } else {
                                null
                            }
                        val extractedTemporalPolicy = extractedTemporalResolution?.let {
                            TemporalActionPolicy.evaluate(it, TemporalUseCase.RESCHEDULE)
                        }
                        val clarificationRequired =
                            validation.action == ConversationContextAction.RESCHEDULE &&
                                (
                                    !hasDateChange && !hasTimeChange ||
                                        extractedTemporalPolicy is TemporalPolicyResult.Unresolved ||
                                        extractedTemporalPolicy is TemporalPolicyResult.InvalidPastSchedule ||
                                        extractedTemporalPolicy is TemporalPolicyResult.NeedsExactDate ||
                                        extractedTemporalPolicy is TemporalPolicyResult.NeedsExactTime ||
                                        extractedTemporalPolicy is TemporalPolicyResult.NeedsExactDateAndTime
                                    )
                        Log.d(
                            "HOME_CONTEXT_RESCHEDULE_EXTRACTION",
                            "hasDateChange=$hasDateChange " +
                                "hasTimeChange=$hasTimeChange " +
                                "clarificationRequired=$clarificationRequired"
                        )

                        if (readOnlyTaskContextStore.currentGeneration() != capturedGeneration) {
                            rejectUnavailableContextAction()
                            return@launch
                        }
                        val reResolvedTaskId = readOnlyTaskContextStore.resolveRef(
                            ref = grounding.ref,
                            expectedGeneration = capturedGeneration
                        )
                        if (reResolvedTaskId == null || reResolvedTaskId != privateTaskId) {
                            rejectUnavailableContextAction()
                            return@launch
                        }
                        val authoritativeTask = withContext(Dispatchers.IO) {
                            taskDao.getById(reResolvedTaskId)
                        }
                        if (!isEligibleContextActionTarget(authoritativeTask)) {
                            rejectUnavailableContextAction()
                            return@launch
                        }

                        conversationOrchestrator.commitFinalDecision(conversationDecision)
                        openContextActionEditScreen(
                            task = requireNotNull(authoritativeTask),
                            action = validation.action,
                            extractedChange = extractedChange,
                            requiresTemporalCollection = clarificationRequired
                        )
                        return@launch
                    }
                    ConversationRoute.QUERY_READING_CONTROL -> {
                        val validation = validateQueryReadingControl(
                            conversationDecision.queryReadingMove
                        )
                        if (!validation.isValid) {
                            val clarificationDecision = ConversationDecision(
                                route = ConversationRoute.ASK_CLARIFICATION,
                                reply = validation.clarification,
                                listenAgain = true,
                                source = "android_query_reading_control_validation"
                            )
                            conversationOrchestrator.commitFinalDecision(clarificationDecision)
                            assistantSession.speak(validation.clarification, listenAgain = true)
                            return@launch
                        }
                        conversationOrchestrator.commitFinalDecision(conversationDecision)
                        executeQueryReadingControl(
                            conversationDecision.queryReadingMove,
                            requestToken,
                            routedStyleAuthorization
                        )
                        return@launch
                    }
                    ConversationRoute.DIRECT_REPLY -> {
                        Log.d("CONVO_ORCH", "handled directly as DIRECT_REPLY")
                        conversationOrchestrator.commitFinalDecision(conversationDecision)
                        assistantSession.speak(
                            conversationDecision.reply,
                            listenAgain = conversationDecision.listenAgain
                        )
                        return@launch
                    }
                    ConversationRoute.ASK_CLARIFICATION -> {
                        Log.d("CONVO_ORCH", "handled directly as ASK_CLARIFICATION")
                        conversationOrchestrator.commitFinalDecision(conversationDecision)
                        assistantSession.speak(conversationDecision.reply, listenAgain = true)
                        return@launch
                    }
                    ConversationRoute.END_SESSION -> {
                        Log.d("CONVO_ORCH", "handled directly as END_SESSION")
                        conversationOrchestrator.commitFinalDecision(conversationDecision)
                        if (conversationDecision.reply.isNotBlank()) {
                            assistantSession.speak(conversationDecision.reply, listenAgain = false)
                        }
                        logQueryPageEndIfActive("USER_STOPPED")
                        invalidateAssistantRequest(
                            AssistantRequestInvalidationReason.CONVERSATION_ENDED
                        )
                        clearConversationSessionContext()
                        routineDraftController.clear()
                        homeFollowUpContext = HomeFollowUpContext.NONE
                        return@launch
                    }
                    ConversationRoute.TASK_COMMAND -> {
                        val taskContextSnapshot = readOnlyTaskContextStore.snapshot()
                        if (ContextReferenceMutationGuard.shouldBlock(
                                decision = conversationDecision,
                                currentUtterance = normalized,
                                snapshot = taskContextSnapshot
                            )
                        ) {
                            Log.w(
                                "HOME_CONTEXT_GUARD",
                                ContextReferenceMutationGuard.BLOCK_REASON
                            )
                            val clarification = "Please say the task name for that change."
                            conversationOrchestrator.commitFinalDecision(
                                ConversationDecision(
                                    route = ConversationRoute.ASK_CLARIFICATION,
                                    reply = clarification,
                                    listenAgain = true,
                                    source = ContextReferenceMutationGuard.BLOCK_REASON
                                )
                            )
                            assistantSession.speak(
                                clarification,
                                listenAgain = true
                            )
                            return@launch
                        }
                        conversationOrchestrator.commitFinalDecision(conversationDecision)
                        taskAgentInput = conversationDecision.taskText.ifBlank { normalized }
                        if (BuildConfig.DEBUG) {
                            Log.d("CONVO_ORCH", "routed to task agent with text='$taskAgentInput'")
                        }
                    }
                    ConversationRoute.UNKNOWN -> {
                        Log.d("CONVO_ORCH", "handled directly as UNKNOWN")
                        val fallbackReply = conversationDecision.reply.ifBlank {
                            "I cannot help with that request yet."
                        }
                        conversationOrchestrator.commitFinalDecision(
                            conversationDecision.copy(reply = fallbackReply)
                        )
                        assistantSession.speak(
                            fallbackReply,
                            listenAgain = true
                        )
                        return@launch
                    }
                }

                // log
                if (BuildConfig.DEBUG) {
                    Log.d("HOME_ROUTING", "falling through to AgentOrchestrator with text='$taskAgentInput'")
                }
                val aiResult = agentOrchestrator.process(taskAgentInput)
                val presentationResolution = TaskQueryPresentationReconciler.reconcile(
                    taskAgentIntent = aiResult.intent,
                    conversationHint = conversationDecision.queryPresentationHint,
                    taskAgentValue = aiResult.queryPresentation
                )
                if (
                    aiResult.intent == AiIntent.QUERY_TASK.name ||
                    conversationDecision.queryPresentationHint != TaskQueryPresentation.NONE
                ) {
                    Log.d(
                        "HOME_QUERY_PRESENTATION",
                        "conversationHint=${conversationDecision.queryPresentationHint} " +
                            "taskAgentValue=${aiResult.queryPresentation} " +
                            "effective=${presentationResolution.effective} " +
                            "source=${presentationResolution.source}"
                    )
                }

                if (BuildConfig.DEBUG) {
                    Log.d(
                        "TASK_PIPELINE",
                        "intent=${aiResult.intent}, title=${aiResult.taskTitle}, date=${aiResult.dateText}," +
                                " time=${aiResult.timeText}, targetDate=${aiResult.targetDateText}, targetTime=${aiResult.targetTimeText}," +
                                " newDate=${aiResult.newDateText}, newTime=${aiResult.newTimeText}, source=${aiResult.source}, confidence=${aiResult.confidence}, " +
                                "needsClarification=${aiResult.needsClarification}, missingFields=${aiResult.missingFields}"
                    )
                }

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
                            agentTimeText = aiResult.targetTimeText ?: aiResult.timeText,
                            presentation = presentationResolution.effective,
                            requestToken = requestToken,
                            authorization = routedStyleAuthorization
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
            } catch (e: CancellationException) {
                throw e
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

    private suspend fun renderObservationResponse(
        observation: ExecutionObservation,
        requestToken: AssistantRequestToken? = null,
        authorization: SafeStyleTurnAuthorization? = null
    ): ConversationResponse {
        val startedAt = System.currentTimeMillis()
        Log.d(
            "CMAS_OBSERVATION",
            "operation=${observation.operation} outcome=${observation.outcome} " +
                    "requiredInput=${observation.requiredInput} taskCount=${observation.taskCount} " +
                    "listenAgain=${observation.listenAgain}"
        )
        val plan = AndroidObservationResponseRenderer.taskQueryPlanOrNull(observation)
        val response = if (plan != null) {
            val styleAuthorization = SafeStyleAuthorizationPolicy.evaluate(
                requestToken,
                authorization
            )
            if (styleAuthorization.status == SafeStyleAuthorizationStatus.STALE) {
                Log.d(
                    "SAFE_OBSERVATION_STYLE_FALLBACK",
                    "reason=STYLE_AUTHORIZATION_STALE"
                )
            }
            val budgetAvailable = styleAuthorization.styleCallAllowed
            Log.d(
                "SAFE_OBSERVATION_STYLE",
                "eligible=true pageRole=${plan.styleContext.pageRole} " +
                    "tone=${plan.styleContext.tone} budgetAvailable=$budgetAvailable"
            )
            if (styleAuthorization.status == SafeStyleAuthorizationStatus.STALE) {
                AndroidObservationResponseRenderer.render(observation)
            } else {
                conversationOrchestrator.styleTaskQuerySpeech(plan, budgetAvailable)
                    .copy(hint = observation.fallbackHint)
            }
        } else {
            AndroidObservationResponseRenderer.render(observation)
        }
        val latencyMs = System.currentTimeMillis() - startedAt
        Log.d(
            "CMAS_RESPONSE",
            "responseType=${response.responseType} source=${response.source} " +
                    "speechLength=${response.speech.length} latencyMs=$latencyMs"
        )
        return response
    }

    private fun recordObservationResponse(
        observation: ExecutionObservation,
        response: ConversationResponse
    ) {
        conversationOrchestrator.recordDeterministicObservation(observation, response)
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
        val response = renderObservationResponse(observation)
        recordObservationResponse(observation, response)
        deliverObservationResponse(observation, response)
    }

    private suspend fun speakRepeatableObservation(
        observation: ExecutionObservation,
        kind: RepeatableSpeechKind,
        contextGeneration: Long?,
        pageIndex: Int?,
        requestToken: AssistantRequestToken,
        authorization: SafeStyleTurnAuthorization
    ) {
        val capturedState = captureSafeObservationDeliveryState(
            kind = kind,
            contextGeneration = contextGeneration,
            pageIndex = pageIndex,
            requestToken = requestToken
        )
        if (!isSafeObservationDeliveryCurrent(capturedState)) return

        val response = renderObservationResponse(
            observation,
            requestToken,
            authorization
        )
        val staleReason = SafeObservationDeliveryGuard.runIfCurrent(
            captured = capturedState,
            current = currentSafeObservationDeliveryState(capturedState)
        ) {
            recordObservationResponse(observation, response)
            authoritativeRepeatState = AuthoritativeRepeatState(
                speech = response.speech,
                kind = kind,
                contextGeneration = contextGeneration
            )
            if (kind == RepeatableSpeechKind.QUERY_PAGE) {
                currentQueryPageRepeatState = authoritativeRepeatState
            }
            deliverObservationResponse(observation, response)
        }
        if (staleReason != null) {
            Log.d("SAFE_OBSERVATION_STYLE_STALE", "reason=$staleReason")
        }
    }

    private suspend fun speakObservationThenRun(observation: ExecutionObservation, action: () -> Unit) {
        val response = renderObservationResponse(observation)
        recordObservationResponse(observation, response)
        deliverObservationResponse(observation, response, action)
    }

    private fun captureSafeObservationDeliveryState(
        kind: RepeatableSpeechKind,
        contextGeneration: Long?,
        pageIndex: Int?,
        requestToken: AssistantRequestToken
    ): SafeObservationDeliveryState = SafeObservationDeliveryState(
        requestGeneration = requestToken.requestGeneration,
        queryReadingStateGeneration = queryReadingStateGeneration,
        taskContextGeneration = contextGeneration,
        pageIndex = pageIndex,
        interaction = when (kind) {
            RepeatableSpeechKind.QUERY_COUNT -> SafeObservationInteraction.QUERY_COUNT
            RepeatableSpeechKind.QUERY_PAGE -> SafeObservationInteraction.QUERY_PAGE
            RepeatableSpeechKind.DAILY_BRIEFING -> SafeObservationInteraction.NONE
            RepeatableSpeechKind.CONTEXT_READ -> SafeObservationInteraction.NONE
        },
        querySessionActive = accessibleTaskQuerySession != null,
        assistantRequestActive = assistantRequestActive
    )

    private fun currentSafeObservationDeliveryState(
        captured: SafeObservationDeliveryState
    ): SafeObservationDeliveryState = SafeObservationDeliveryState(
        requestGeneration = assistantRequestGeneration,
        queryReadingStateGeneration = queryReadingStateGeneration,
        taskContextGeneration = if (captured.taskContextGeneration == null) {
            null
        } else {
            readOnlyTaskContextStore.currentGeneration()
        },
        pageIndex = if (captured.pageIndex == null) {
            null
        } else {
            accessibleTaskQuerySession?.currentPageIndex
        },
        interaction = when (homeFollowUpContext) {
            HomeFollowUpContext.QUERY_COUNT -> SafeObservationInteraction.QUERY_COUNT
            HomeFollowUpContext.QUERY_PAGE -> SafeObservationInteraction.QUERY_PAGE
            else -> SafeObservationInteraction.NONE
        },
        querySessionActive = accessibleTaskQuerySession != null,
        assistantRequestActive = assistantRequestActive
    )

    private fun isSafeObservationDeliveryCurrent(
        captured: SafeObservationDeliveryState
    ): Boolean {
        val reason = SafeObservationDeliveryGuard.staleReason(
            captured = captured,
            current = currentSafeObservationDeliveryState(captured)
        )
        if (reason != null) {
            Log.d("SAFE_OBSERVATION_STYLE_STALE", "reason=$reason")
            return false
        }
        return true
    }

    private fun observedTask(task: TaskEntity): ObservedTask = TaskObservationMapper.observedTask(
        task = task,
        subtasks = currentSubtasksByParentId[task.id].orEmpty()
    )

    private suspend fun executeDailyBriefing(
        requestToken: AssistantRequestToken,
        decision: ConversationDecision
    ) {
        if (!isDailyBriefingRequestCurrent(requestToken)) return
        Log.d(
            "DAILY_BRIEFING_REQUEST",
            "requestGeneration=${requestToken.requestGeneration}"
        )

        val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
        val roomData = withContext(Dispatchers.IO) {
            val roots = dao.getRootTasks()
            val subtasks = roots.associate { root ->
                root.id to dao.getSubtasks(root.id)
            }
            roots to subtasks
        }
        if (!isDailyBriefingRequestCurrent(requestToken)) return

        val snapshot = DailyBriefingSnapshotBuilder.build(
            localDate = todayDateString(),
            rootTasks = roomData.first,
            subtasksByParentId = roomData.second
        )
        Log.d(
            "DAILY_BRIEFING_SNAPSHOT",
            "overdueCount=${snapshot.overdueCount} " +
                "todayCount=${snapshot.todayActiveCount} " +
                "upcomingCount=${snapshot.upcomingActiveCount} " +
                "spokenCount=${snapshot.spokenItems.size} " +
                "additionalTodayCount=${snapshot.additionalTodayCount} " +
                "additionalUpcomingCount=${snapshot.additionalUpcomingCount} " +
                "focusCategory=${snapshot.spokenItems.firstOrNull { it.isSuggestedFocus }?.category?.name ?: "NONE"}"
        )

        if (!isDailyBriefingRequestCurrent(requestToken)) return
        clearAccessibleTaskQuerySession(clearTaskContext = true)
        if (!isDailyBriefingRequestCurrent(requestToken)) return
        readOnlyTaskContextStore.replaceDailyBriefingResults(
            tasks = snapshot.spokenRoomTasks,
            subtasksByParentId = roomData.second
        )
        currentSubtasksByParentId = roomData.second
        if (::conversationOrchestrator.isInitialized) {
            conversationOrchestrator.clearInvalidContextFocus(
                readOnlyTaskContextStore.snapshot()
            )
        }
        val contextGeneration = readOnlyTaskContextStore.currentGeneration()
        Log.d(
            "DAILY_BRIEFING_CONTEXT",
            "generation=$contextGeneration itemCount=${snapshot.spokenItems.size}"
        )

        val speech = DailyBriefingSpeechRenderer.render(snapshot)
        val observation = ExecutionObservation(
            operation = ExecutionOperation.DAILY_BRIEFING,
            outcome = ExecutionOutcome.INFORMATION,
            taskCount = snapshot.todayActiveCount,
            overdueTaskCount = snapshot.overdueCount,
            todayActiveTaskCount = snapshot.todayActiveCount,
            upcomingActiveTaskCount = snapshot.upcomingActiveCount,
            additionalTodayTaskCount = snapshot.additionalTodayCount,
            additionalUpcomingTaskCount = snapshot.additionalUpcomingCount,
            dailyBriefingItems = snapshot.spokenItems,
            dailyBriefingFocusReason = snapshot.suggestedFocusReason,
            dateText = snapshot.localDate,
            detail = "Authoritative on-demand daily briefing.",
            tasks = snapshot.spokenItems.map { it.task },
            listenAgain = true,
            fallbackSpeech = speech
        )
        val response = AndroidObservationResponseRenderer.render(observation)

        if (!isDailyBriefingRequestCurrent(requestToken)) return
        conversationOrchestrator.commitFinalDecision(decision)
        if (!isDailyBriefingRequestCurrent(requestToken)) return
        recordObservationResponse(observation, response)
        if (!isDailyBriefingRequestCurrent(requestToken)) return
        authoritativeRepeatState = AuthoritativeRepeatState(
            speech = response.speech,
            kind = RepeatableSpeechKind.DAILY_BRIEFING,
            contextGeneration = contextGeneration
        )
        homeFollowUpContext = HomeFollowUpContext.AFTER_DAILY_BRIEFING
        if (!isDailyBriefingRequestCurrent(requestToken)) return
        Log.d(
            "DAILY_BRIEFING_RESPONSE",
            "source=android_deterministic speechLength=${response.speech.length}"
        )
        deliverObservationResponse(observation, response)
    }

    private fun isDailyBriefingRequestCurrent(
        requestToken: AssistantRequestToken
    ): Boolean {
        val current = isAssistantRequestCurrent(requestToken)
        if (!current) {
            Log.d("DAILY_BRIEFING_STALE", "reason=REQUEST_CHANGED")
        }
        return current
    }

    private fun isEligibleContextActionTarget(task: TaskEntity?): Boolean =
        ContextActionTargetValidator.isEligible(task)

    private fun rejectUnavailableContextAction() {
        rejectContextAction(
            reply = "That task is no longer available. Please repeat your task query.",
            source = "android_context_action_target_unavailable"
        )
    }

    private fun rejectContextAction(reply: String, source: String) {
        conversationOrchestrator.commitFinalDecision(
            ConversationDecision(
                route = ConversationRoute.ASK_CLARIFICATION,
                reply = reply,
                listenAgain = true,
                source = source
            )
        )
        assistantSession.speak(reply, listenAgain = true)
    }

    private suspend fun openContextActionEditScreen(
        task: TaskEntity,
        action: ConversationContextAction,
        extractedChange: ContextActionChangeSet,
        requiresTemporalCollection: Boolean
    ) {
        val operation = if (action == ConversationContextAction.RESCHEDULE) {
            ExecutionOperation.RESCHEDULE_TASK
        } else {
            ExecutionOperation.UPDATE_TASK
        }
        val reply = if (
            action == ConversationContextAction.RESCHEDULE &&
            requiresTemporalCollection
        ) {
            "Opening the task so you can choose a new date or time."
        } else if (action == ConversationContextAction.RESCHEDULE) {
            responseManager.openReschedule()
        } else {
            responseManager.openEditTask()
        }
        speakObservationThenRun(
            ExecutionObservation(
                operation = operation,
                outcome = ExecutionOutcome.INFORMATION,
                taskTitle = task.title,
                tasks = listOf(observedTask(task)),
                dateText = extractedChange.newDateText.orEmpty(),
                timeText = extractedChange.newTimeText.orEmpty(),
                listenAgain = false,
                fallbackSpeech = reply
            )
        ) {
            val editIntent = Intent(this@HomeActivity, EditTaskActivity::class.java).apply {
                putExtra("task_id", task.id)
                putExtra("task_title", task.title)
                putExtra("task_date", task.dueDate)
                putExtra("task_time", task.dueTime)
                putExtra("opened_by_assistant", true)
                putExtra("prefill_new_date_text", extractedChange.newDateText)
                putExtra("prefill_new_time_text", extractedChange.newTimeText)
                if (action == ConversationContextAction.UPDATE &&
                    !extractedChange.replacementTitle.isNullOrBlank()
                ) {
                    putExtra("prefill_title", extractedChange.replacementTitle)
                }
                if (action == ConversationContextAction.RESCHEDULE) {
                    putExtra("assistant_mode", "reschedule")
                    putExtra(
                        "reschedule_collection_required",
                        requiresTemporalCollection
                    )
                }
            }
            startActivity(editIntent)
        }
    }

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
        agentTimeText: String?,
        presentation: TaskQueryPresentation,
        requestToken: AssistantRequestToken,
        authorization: SafeStyleTurnAuthorization
    ) {
        if (!isAssistantRequestCurrent(requestToken)) {
            return
        }
        val queryRequestGeneration =
            clearAccessibleTaskQuerySession(clearTaskContext = true)
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
            val taskData = withContext(Dispatchers.IO) {
                val roots = dao.getRootTasks()
                val subtasks = roots.associate { root -> root.id to dao.getSubtasks(root.id) }
                roots to subtasks
            }
            val allTasks = taskData.first
            if (
                queryRequestGeneration != queryReadingStateGeneration ||
                !isAssistantRequestCurrent(requestToken)
            ) {
                return@launch
            }
            currentSubtasksByParentId = taskData.second

            val today = todayDateString()
            val queryWindow = temporalQueryResolver.resolve(
                agentDateText = agentDateText,
                agentTimeText = agentTimeText,
                originalText = normalized
            )

            if (BuildConfig.DEBUG) {
                Log.d(
                    "HOME_QUERY_TEMPORAL",
                    "dateText=$agentDateText timeText=$agentTimeText status=${queryWindow.status} " +
                            "scope=${queryWindow.dateScope} startDate=${queryWindow.startDateInclusive} " +
                            "endDate=${queryWindow.endDateInclusive} startMinute=${queryWindow.startMinuteInclusive} " +
                            "endMinute=${queryWindow.endMinuteInclusive} wrapsMidnight=${queryWindow.wrapsMidnight} " +
                            "label=${queryWindow.spokenLabel}"
                )
            }

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

            val queryWasToday =
                queryWindow.isExactDate && queryWindow.startDateInclusive == today

            val filteredTasks = TaskTemporalFilter.filterAndSort(allTasks, queryWindow)
            if (filteredTasks.isEmpty()) {
                accessibleTaskQuerySession = null
                currentSubtasksByParentId = emptyMap()
                homeFollowUpContext = HomeFollowUpContext.AFTER_NO_TASKS
                val noTasks = buildNoTasksQueryReply(queryWasToday, queryWindow)
                speakObservation(
                    ExecutionObservation(
                        operation = ExecutionOperation.QUERY_TASK,
                        outcome = ExecutionOutcome.NO_RESULTS,
                        taskCount = 0,
                        dateText = queryWindow.spokenLabel,
                        detail = "Offer to create a new task.",
                        tasks = emptyList(),
                        listenAgain = true,
                        fallbackSpeech = responseManager.combineReplyWithFollowUp(
                            noTasks,
                            responseManager.followUpCreateAfterNoTasks()
                        ),
                        fallbackHint = responseManager.hintCreateOrRead()
                    )
                )
                return@launch
            }

            val session = AccessibleTaskQuerySession(
                orderedTasks = filteredTasks,
                subtasksByParentId = currentSubtasksByParentId,
                queryWindow = queryWindow,
                presentation = presentation
            )
            accessibleTaskQuerySession = session
            Log.d(
                "HOME_QUERY_READING_SESSION",
                "totalCount=${session.orderedTasks.size} presentation=${session.presentation} " +
                    "pageSize=${session.pageSize}"
            )

            if (presentation == TaskQueryPresentation.COUNT_ONLY) {
                homeFollowUpContext = HomeFollowUpContext.QUERY_COUNT
                speakRepeatableObservation(
                    observation = buildCountOnlyQueryObservation(session),
                    kind = RepeatableSpeechKind.QUERY_COUNT,
                    contextGeneration = null,
                    pageIndex = null,
                    requestToken = requestToken,
                    authorization = authorization
                )
            } else {
                homeFollowUpContext = HomeFollowUpContext.QUERY_PAGE
                publishAndSpeakCurrentQueryPage(
                    session,
                    requestToken,
                    authorization
                )
            }
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

    private fun buildCountOnlyQueryObservation(
        session: AccessibleTaskQuerySession
    ): ExecutionObservation = ExecutionObservation(
        operation = ExecutionOperation.QUERY_TASK,
        outcome = ExecutionOutcome.INFORMATION,
        taskCount = session.orderedTasks.size,
        dateText = session.queryWindow.spokenLabel,
        tasks = emptyList(),
        queryPage = TaskQueryPageObservation(
            totalTaskCount = session.orderedTasks.size,
            pageStartPosition = 0,
            pageEndPosition = 0,
            pageNumber = 0,
            pageCount = session.pageCount,
            pageSize = session.pageSize,
            hasNextPage = session.hasNextPage,
            presentation = TaskQueryPresentationLevel.COUNT_ONLY,
            detailLevel = TaskQuerySpeechDetail.BRIEF,
            tone = taskQuerySpeechTone(),
            includeTaskDates = false,
            temporalLabel = taskQueryTemporalLabel(session.queryWindow)
        ),
        listenAgain = true,
        fallbackSpeech = "",
        fallbackHint = responseManager.hintYesNo()
    )

    private suspend fun publishAndSpeakCurrentQueryPage(
        session: AccessibleTaskQuerySession,
        requestToken: AssistantRequestToken,
        authorization: SafeStyleTurnAuthorization
    ) {
        if (!isAssistantRequestCurrent(requestToken)) return
        if (accessibleTaskQuerySession !== session) {
            Log.d("SAFE_OBSERVATION_STYLE_STALE", "reason=QUERY_PAGE_CHANGED")
            return
        }
        val pageTasks = session.currentPageTasks
        readOnlyTaskContextStore.replaceRecentQueryResults(
            tasks = pageTasks,
            subtasksByParentId = session.subtasksByParentId
        )
        if (::conversationOrchestrator.isInitialized) {
            conversationOrchestrator.clearInvalidContextFocus(readOnlyTaskContextStore.snapshot())
        }
        val contextGeneration = readOnlyTaskContextStore.currentGeneration()
        Log.d(
            "HOME_QUERY_PAGE",
            "pageNumber=${session.currentPageIndex + 1} pageCount=${session.pageCount} " +
                "pageItemCount=${pageTasks.size} hasNext=${session.hasNextPage} " +
                "contextGeneration=$contextGeneration"
        )
        speakRepeatableObservation(
            observation = buildQueryPageObservation(session),
            kind = RepeatableSpeechKind.QUERY_PAGE,
            contextGeneration = contextGeneration,
            pageIndex = session.currentPageIndex,
            requestToken = requestToken,
            authorization = authorization
        )
    }

    private fun buildQueryPageObservation(
        session: AccessibleTaskQuerySession
    ): ExecutionObservation {
        val presentationLevel = when (session.presentation) {
            TaskQueryPresentation.DETAILS -> TaskQueryPresentationLevel.DETAILS
            TaskQueryPresentation.COUNT_ONLY -> TaskQueryPresentationLevel.COUNT_ONLY
            TaskQueryPresentation.NONE,
            TaskQueryPresentation.OVERVIEW -> TaskQueryPresentationLevel.OVERVIEW
        }
        return ExecutionObservation(
            operation = ExecutionOperation.QUERY_TASK,
            outcome = ExecutionOutcome.INFORMATION,
            taskCount = session.orderedTasks.size,
            dateText = session.queryWindow.spokenLabel,
            tasks = session.currentPageTasks.map { task ->
                TaskObservationMapper.observedTask(
                    task = task,
                    subtasks = session.subtasksByParentId[task.id].orEmpty()
                )
            },
            queryPage = TaskQueryPageObservation(
                totalTaskCount = session.orderedTasks.size,
                pageStartPosition = session.currentPageStartPosition,
                pageEndPosition = session.currentPageEndPosition,
                pageNumber = session.currentPageIndex + 1,
                pageCount = session.pageCount,
                pageSize = session.pageSize,
                hasNextPage = session.hasNextPage,
                presentation = presentationLevel,
                detailLevel = taskQuerySpeechDetail(session.presentation),
                tone = taskQuerySpeechTone(),
                includeTaskDates = !session.queryWindow.isExactDate ||
                    session.presentation == TaskQueryPresentation.DETAILS,
                temporalLabel = taskQueryTemporalLabel(session.queryWindow)
            ),
            listenAgain = true,
            fallbackSpeech = "",
            fallbackHint = if (session.hasNextPage) {
                "Say continue, repeat, or stop."
            } else {
                "Ask about a task in this group, repeat, or stop."
            }
        )
    }

    private fun taskQuerySpeechDetail(
        presentation: TaskQueryPresentation
    ): TaskQuerySpeechDetail {
        if (presentation == TaskQueryPresentation.DETAILS) {
            return TaskQuerySpeechDetail.DETAILED
        }
        return when (responseManager.verbosity) {
            AssistantVerbosity.BRIEF -> TaskQuerySpeechDetail.BRIEF
            AssistantVerbosity.BALANCED -> TaskQuerySpeechDetail.BALANCED
            AssistantVerbosity.DETAILED -> TaskQuerySpeechDetail.DETAILED
        }
    }

    private fun taskQuerySpeechTone(): TaskQuerySpeechTone = when (responseManager.tone) {
        AssistantTone.FRIENDLY -> TaskQuerySpeechTone.FRIENDLY
        AssistantTone.NEUTRAL -> TaskQuerySpeechTone.NEUTRAL
        AssistantTone.PROFESSIONAL -> TaskQuerySpeechTone.PROFESSIONAL
    }

    private fun taskQueryTemporalLabel(queryWindow: TemporalQueryWindow): String =
        spokenTemporalLabel(queryWindow)
            ?: if (
                queryWindow.isExactDate &&
                queryWindow.startDateInclusive == todayDateString()
            ) {
                "today"
            } else {
                ""
            }

    private fun spokenTemporalLabel(queryWindow: TemporalQueryWindow): String? {
        if (queryWindow.isExactDate &&
            queryWindow.startDateInclusive == todayDateString() &&
            !queryWindow.hasTimeConstraint
        ) return null
        return TemporalQueryLabelFormatter.spokenLabel(queryWindow)
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

    private fun isSimpleFollowUpAgreement(normalized: String): Boolean = normalized in setOf(
        "yes",
        "yeah",
        "sure",
        "okay",
        "ok",
        "please do",
        "read them"
    )

    private fun handleContextItemRestatement(normalized: String): Boolean {
        val isResultInteraction = homeFollowUpContext in setOf(
            HomeFollowUpContext.AFTER_TASK_SUMMARY,
            HomeFollowUpContext.AFTER_TASK_DETAILS,
            HomeFollowUpContext.QUERY_PAGE,
            HomeFollowUpContext.AFTER_DAILY_BRIEFING
        )
        if (!isResultInteraction) return false

        val taskContextCapture = readOnlyTaskContextStore.capture()
        val resolution = ContextItemRestatementPolicy.resolve(
            normalizedText = normalized,
            capturedSnapshot = taskContextCapture.snapshot,
            currentGeneration = readOnlyTaskContextStore.currentGeneration()
        )
        Log.d(
            "HOME_CONTEXT_RESTATEMENT",
            "disposition=${resolution.disposition} " +
                "scope=${taskContextCapture.snapshot.scope} " +
                "generation=${taskContextCapture.snapshot.generation}"
        )
        return when (resolution.disposition) {
            ContextItemRestatementDisposition.NOT_APPLICABLE -> false
            ContextItemRestatementDisposition.RESOLVED -> {
                executeContextRead(
                    decision = requireNotNull(resolution.decision),
                    taskContextCapture = taskContextCapture,
                    validation = requireNotNull(resolution.validation)
                )
                true
            }
            ContextItemRestatementDisposition.UNAVAILABLE_SELECTOR,
            ContextItemRestatementDisposition.AMBIGUOUS_SELECTOR -> {
                val clarificationDecision = ConversationDecision(
                    route = ConversationRoute.ASK_CLARIFICATION,
                    reply = resolution.clarification,
                    listenAgain = true,
                    source = "android_context_item_restatement"
                )
                conversationOrchestrator.commitFinalDecision(clarificationDecision)
                assistantSession.speak(
                    resolution.clarification,
                    listenAgain = true
                )
                true
            }
        }
    }

    private fun handleContextItemRead(normalized: String): Boolean {
        val isResultInteraction = homeFollowUpContext in setOf(
            HomeFollowUpContext.AFTER_TASK_SUMMARY,
            HomeFollowUpContext.AFTER_TASK_DETAILS,
            HomeFollowUpContext.QUERY_PAGE,
            HomeFollowUpContext.AFTER_DAILY_BRIEFING
        )
        if (!isResultInteraction) return false

        val taskContextCapture = readOnlyTaskContextStore.capture()
        val resolution = ContextItemReadPolicy.resolve(
            normalizedText = normalized,
            capturedSnapshot = taskContextCapture.snapshot,
            currentGeneration = readOnlyTaskContextStore.currentGeneration()
        )
        Log.d(
            "HOME_CONTEXT_ITEM_READ",
            "disposition=${resolution.disposition} " +
                "scope=${taskContextCapture.snapshot.scope} " +
                "generation=${taskContextCapture.snapshot.generation}"
        )
        if (resolution.disposition != ContextItemReadDisposition.RESOLVED) return false

        executeContextRead(
            decision = requireNotNull(resolution.decision),
            taskContextCapture = taskContextCapture,
            validation = requireNotNull(resolution.validation)
        )
        return true
    }

    private fun executeContextRead(
        decision: ConversationDecision,
        taskContextCapture: ReadOnlyTaskContextCapture,
        validation: ValidatedContextRead
    ) {
        Log.d(
            "HOME_CONTEXT_READ",
            "route=${decision.route} " +
                "ref=${decision.contextRef} " +
                "detail=${decision.contextDetail} " +
                "capturedGeneration=${taskContextCapture.snapshot.generation} " +
                "validation=${validation.result}"
        )
        if (!validation.isValid) {
            val clarification = if (
                validation.result == ContextReadValidationResult.STALE_GENERATION
            ) {
                "Those task results changed. Please repeat your task query."
            } else {
                "Please ask again using one of the supplied task results."
            }
            conversationOrchestrator.commitFinalDecision(
                ConversationDecision(
                    route = ConversationRoute.ASK_CLARIFICATION,
                    reply = clarification,
                    listenAgain = true,
                    source = "android_context_validation"
                )
            )
            assistantSession.speak(clarification, listenAgain = true)
            return
        }

        val item = requireNotNull(validation.item)
        val speech = ReadOnlyTaskContextResponseRenderer.render(
            item = item,
            detail = validation.detail
        )
        conversationOrchestrator.recordAuthoritativeContextRead(
            item = item,
            selectedRef = decision.contextRef,
            selectedDetail = validation.detail,
            capturedGeneration = taskContextCapture.snapshot.generation,
            finalSpeech = speech
        )
        authoritativeRepeatState = AuthoritativeRepeatState(
            speech = speech,
            kind = RepeatableSpeechKind.CONTEXT_READ,
            contextGeneration = taskContextCapture.snapshot.generation
        )
        assistantSession.speak(
            speech,
            listenAgain = decision.listenAgain
        )
    }

    private fun handleQueryReadingFollowUp(
        normalized: String,
        requestToken: AssistantRequestToken,
        authorization: SafeStyleTurnAuthorization
    ): Boolean {
        val session = accessibleTaskQuerySession
        return when (homeFollowUpContext) {
            HomeFollowUpContext.QUERY_COUNT -> when {
                session != null && isSimpleFollowUpAgreement(normalized) -> {
                    startQueryOverviewFromCount(requestToken, authorization)
                    true
                }

                isSimpleFollowUpEndCommand(normalized) -> {
                    endAssistantConversation()
                    true
                }

                else -> false
            }

            HomeFollowUpContext.QUERY_PAGE -> when {
                normalized in setOf(
                    "continue",
                    "next",
                    "next group",
                    "read more",
                    "keep going"
                ) || (
                    normalized == "yes" &&
                        session?.hasNextPage == true
                    ) -> {
                    continueTaskQueryPage(requestToken, authorization)
                    true
                }

                normalized in setOf(
                    "repeat",
                    "repeat that",
                    "read that again",
                    "again"
                ) -> {
                    repeatLastAuthoritativeSpeech()
                    true
                }

                isSimpleFollowUpEndCommand(normalized) -> {
                    endAssistantConversation()
                    true
                }

                else -> false
            }

            HomeFollowUpContext.AFTER_DAILY_BRIEFING -> false
            else -> false
        }
    }

    private fun validateQueryReadingControl(move: ConversationQueryReadingMove) =
        QueryReadingControlPolicy.validate(
            move = move,
            interactionState = currentQueryReadingInteractionState(),
            hasActiveSession = accessibleTaskQuerySession?.orderedTasks?.isNotEmpty() == true,
            hasAuthoritativeRepeat = currentAuthoritativeRepeatState() != null
        )

    private fun executeQueryReadingControl(
        move: ConversationQueryReadingMove,
        requestToken: AssistantRequestToken,
        authorization: SafeStyleTurnAuthorization
    ) {
        when (move) {
            ConversationQueryReadingMove.START_OVERVIEW ->
                startQueryOverviewFromCount(requestToken, authorization)
            ConversationQueryReadingMove.CONTINUE ->
                continueTaskQueryPage(requestToken, authorization)
            ConversationQueryReadingMove.REPEAT_LAST -> repeatLastAuthoritativeSpeech()
            ConversationQueryReadingMove.REPEAT_PAGE -> repeatCurrentTaskQueryPage()
            ConversationQueryReadingMove.STOP -> endAssistantConversation()
            ConversationQueryReadingMove.NONE -> Unit
        }
    }

    private fun currentQueryReadingInteractionState(): QueryReadingInteractionState =
        when (homeFollowUpContext) {
            HomeFollowUpContext.QUERY_COUNT -> QueryReadingInteractionState.QUERY_COUNT
            HomeFollowUpContext.QUERY_PAGE -> QueryReadingInteractionState.QUERY_PAGE
            HomeFollowUpContext.AFTER_DAILY_BRIEFING ->
                QueryReadingInteractionState.DAILY_BRIEFING
            else -> QueryReadingInteractionState.NONE
        }

    private fun startQueryOverviewFromCount(
        requestToken: AssistantRequestToken,
        authorization: SafeStyleTurnAuthorization
    ) {
        if (!isAssistantRequestCurrent(requestToken)) return
        val session = accessibleTaskQuerySession ?: return
        val overviewSession = session.beginOverview()
        accessibleTaskQuerySession = overviewSession
        homeFollowUpContext = HomeFollowUpContext.QUERY_PAGE
        lifecycleScope.launch {
            publishAndSpeakCurrentQueryPage(
                overviewSession,
                requestToken,
                authorization
            )
        }
    }

    private fun continueTaskQueryPage(
        requestToken: AssistantRequestToken,
        authorization: SafeStyleTurnAuthorization
    ) {
        if (!isAssistantRequestCurrent(requestToken)) return
        val currentSession = accessibleTaskQuerySession ?: return
        val nextSession = currentSession.advanceOnePage()
        if (nextSession == null) {
            Log.d("HOME_QUERY_PAGE_END", "reason=LAST_PAGE")
            assistantSession.speak("That was the last group.", listenAgain = true)
            return
        }
        Log.d(
            "HOME_QUERY_PAGE_CONTINUE",
            "fromPage=${currentSession.currentPageIndex + 1} " +
                "toPage=${nextSession.currentPageIndex + 1}"
        )
        accessibleTaskQuerySession = nextSession
        homeFollowUpContext = HomeFollowUpContext.QUERY_PAGE
        lifecycleScope.launch {
            publishAndSpeakCurrentQueryPage(
                nextSession,
                requestToken,
                authorization
            )
        }
    }

    private fun repeatCurrentTaskQueryPage() {
        if (accessibleTaskQuerySession == null) return
        val contextGeneration = readOnlyTaskContextStore.currentGeneration()
        Log.d(
            "HOME_QUERY_PAGE_REPEAT",
            "contextGenerationUnchanged=true exactStoredSpeech=true"
        )
        val pageState = currentQueryPageRepeatState
        if (
            pageState == null ||
            pageState.speech.isBlank() ||
            pageState.contextGeneration != contextGeneration
        ) {
            assistantSession.speak(
                "There is no current page available to repeat.",
                listenAgain = true
            )
            return
        }
        authoritativeRepeatState = pageState
        assistantSession.speak(pageState.speech, listenAgain = true)
        check(readOnlyTaskContextStore.currentGeneration() == contextGeneration)
    }

    private fun repeatLastAuthoritativeSpeech() {
        val repeatState = currentAuthoritativeRepeatState()
        if (repeatState == null) {
            assistantSession.speak(
                "There is no current response available to repeat.",
                listenAgain = true
            )
            return
        }
        val contextGeneration = readOnlyTaskContextStore.currentGeneration()
        assistantSession.speak(repeatState.speech, listenAgain = true)
        check(readOnlyTaskContextStore.currentGeneration() == contextGeneration)
    }

    private fun currentAuthoritativeRepeatState(): AuthoritativeRepeatState? {
        val repeatState = authoritativeRepeatState ?: return null
        if (repeatState.speech.isBlank()) return null
        val requiredGeneration = repeatState.contextGeneration
        return if (
            requiredGeneration == null ||
            requiredGeneration == readOnlyTaskContextStore.currentGeneration()
        ) {
            repeatState
        } else {
            null
        }
    }

    private fun endAssistantConversation() {
        invalidateAssistantRequest(
            AssistantRequestInvalidationReason.CONVERSATION_ENDED
        )
        logQueryPageEndIfActive("USER_STOPPED")
        clearConversationSessionContext()
        homeFollowUpContext = HomeFollowUpContext.NONE
        assistantSession.getBottomSheet()?.clearHint()
        clearPendingTaskMatchState()
        clearPendingDeleteState()
        clearPendingBreakdownState()
        if (routineDraftController.state != RoutineDraftState.SAVING) {
            routineDraftController.clear()
        }
        assistantSession.speakThenStop(responseManager.stopListening())
    }

    private fun clearConversationSessionContext() {
        clearAccessibleTaskQuerySession(clearTaskContext = true)
        if (::conversationOrchestrator.isInitialized) {
            conversationOrchestrator.clearSessionMemory()
        }
    }

    private fun clearAccessibleTaskQuerySession(
        clearTaskContext: Boolean
    ): Long {
        queryReadingStateGeneration += 1
        accessibleTaskQuerySession = null
        authoritativeRepeatState = null
        currentQueryPageRepeatState = null
        currentSubtasksByParentId = emptyMap()
        if (clearTaskContext) {
            readOnlyTaskContextStore.clear()
        }
        return queryReadingStateGeneration
    }

    private suspend fun handleSmartRoutineBuilder(
        normalizedRequest: String,
        requestToken: AssistantRequestToken
    ) {
        if (!isAssistantRequestCurrent(requestToken)) return
        homeFollowUpContext = HomeFollowUpContext.NONE
        if (!isAssistantRequestCurrent(requestToken)) return
        val generation = routineDraftController.beginExtraction()
        logRoutineDraftState()
        val extraction = try {
            agentOrchestrator.processRoutine(normalizedRequest)
        } catch (_: TaskAgentProcessingException) {
            val discarded = routineDraftController.discardExtraction(generation)
            if (discarded && isAssistantRequestCurrent(requestToken)) {
                conversationOrchestrator.clearSessionMemory()
                speakRoutineResponse(
                    RoutineResponseKind.EXTRACTION_FAILURE,
                    "I could not extract that routine safely. Please describe 2 to 5 ordered tasks again.",
                    listenAgain = true
                )
            }
            return
        }
        if (!isAssistantRequestCurrent(requestToken)) {
            routineDraftController.discardExtraction(generation)
            return
        }
        val extractionGeneration = generation
        if (!isAssistantRequestCurrent(requestToken)) {
            routineDraftController.discardExtraction(extractionGeneration)
            return
        }
        val update = routineDraftController.applyExtraction(
            extractionGeneration,
            extraction
        )
        handleRoutineDraftUpdate(
            update,
            invalidSpeech = "I could not extract that routine safely. Please describe 2 to 5 ordered tasks again.",
            requestToken = requestToken
        )
    }

    private fun handleRoutineFollowUp(
        normalized: String,
        requestToken: AssistantRequestToken
    ): Boolean {
        val stateBefore = routineDraftController.state
        if (routineDraftController.state == RoutineDraftState.SAVING) {
            val savingMove = RoutineFollowUpInterpreter.interpret(normalized)
            logRoutineFollowUpDebug(stateBefore, normalized, savingMove)
            if (isAssistantRequestCurrent(requestToken)) {
                speakRoutineResponse(
                    RoutineResponseKind.ALREADY_SAVING,
                    "The confirmed routine is already being saved.",
                    listenAgain = false
                )
            }
            logRoutineFollowUpResult("SAVING")
            return true
        }
        val move = RoutineFollowUpInterpreter.interpret(normalized)
        logRoutineFollowUpDebug(stateBefore, normalized, move)
        if (move is RoutineFollowUpMove.Cancel || move is RoutineFollowUpMove.Reject) {
            cancelPendingRoutine()
            logRoutineFollowUpResult("CANCELLED")
            return true
        }
        return when (routineDraftController.state) {
            RoutineDraftState.NONE -> false
            RoutineDraftState.EXTRACTING -> {
                speakRoutineResponse(
                    RoutineResponseKind.REVISION_HELP,
                    "I am still preparing that routine. You can cancel it if needed.",
                    listenAgain = true
                )
                logRoutineFollowUpResult("UNKNOWN")
                true
            }
            RoutineDraftState.COLLECTING_SHARED_DATE -> {
                val outcome = handleRoutineDraftUpdate(
                    routineDraftController.provideSharedDate(normalized),
                    invalidSpeech = "Please provide one exact date that satisfies the routine's date constraints.",
                    requestToken = requestToken
                )
                logRoutineFollowUpResult(outcome.result, outcome.issue)
                true
            }
            RoutineDraftState.COLLECTING_STEP_TIME -> {
                val outcome = handleRoutineDraftUpdate(
                    routineDraftController.provideNextStepTime(normalized),
                    invalidSpeech = "Please provide one exact clock time for that step.",
                    requestToken = requestToken
                )
                logRoutineFollowUpResult(outcome.result, outcome.issue)
                true
            }
            RoutineDraftState.WAITING_FOR_CONFIRMATION -> {
                val outcome = when (move) {
                    RoutineFollowUpMove.Confirm -> savePendingRoutine().let {
                        RoutineFollowUpOutcome("SAVING")
                    }
                    RoutineFollowUpMove.Repeat -> {
                        val proposal = routineDraftController.authoritativeProposal
                        if (proposal != null) {
                            speakRoutineResponse(
                                RoutineResponseKind.PROPOSAL,
                                proposal,
                                listenAgain = true
                            )
                        }
                        RoutineFollowUpOutcome("ACCEPTED")
                    }
                    is RoutineFollowUpMove.ChangeStepTime -> {
                        handleRoutineDraftUpdate(
                            routineDraftController.changeStepTime(
                                move.stepIndex,
                                move.value
                            ),
                            invalidSpeech = "Please select a valid routine step and give one exact time.",
                            requestToken = requestToken
                        )
                    }
                    is RoutineFollowUpMove.ChangeStepTitle -> {
                        handleRoutineDraftUpdate(
                            routineDraftController.changeStepTitle(
                                move.stepIndex,
                                move.value
                            ),
                            invalidSpeech = "Please select a valid routine step and give a non-empty title.",
                            requestToken = requestToken
                        )
                    }
                    is RoutineFollowUpMove.ChangeSharedDate -> {
                        handleRoutineDraftUpdate(
                            routineDraftController.changeSharedDate(move.value),
                            invalidSpeech = "Please provide one exact future date for the routine.",
                            requestToken = requestToken
                        )
                    }
                    RoutineFollowUpMove.StructuralChange -> {
                        speakRoutineResponse(
                            RoutineResponseKind.REVISION_HELP,
                            "To add or remove routine steps, cancel this draft and start a new routine request.",
                            listenAgain = true
                        )
                        RoutineFollowUpOutcome("REJECTED")
                    }
                    else -> {
                        speakRoutineResponse(
                            RoutineResponseKind.REVISION_HELP,
                            "Say yes to create these tasks, no to reject them, repeat the routine, or change one step's time or title.",
                            listenAgain = true
                        )
                        RoutineFollowUpOutcome("UNKNOWN")
                    }
                }
                logRoutineFollowUpResult(outcome.result, outcome.issue)
                true
            }
            RoutineDraftState.SAVING -> true
        }
    }

    private fun handleRoutineDraftUpdate(
        update: RoutineDraftUpdate,
        invalidSpeech: String,
        requestToken: AssistantRequestToken
    ): RoutineFollowUpOutcome {
        if (!isAssistantRequestCurrent(requestToken)) return RoutineFollowUpOutcome("UNKNOWN")
        return when (update) {
            is RoutineDraftUpdate.Ask -> {
                logRoutineDraftState()
                assistantSession.getBottomSheet()?.showAssistantHint(update.prompt)
                val kind = if (
                    routineDraftController.state == RoutineDraftState.COLLECTING_SHARED_DATE
                ) {
                    RoutineResponseKind.ASK_SHARED_DATE
                } else {
                    RoutineResponseKind.ASK_STEP_TIME
                }
                speakRoutineResponse(kind, update.prompt, listenAgain = true)
                RoutineFollowUpOutcome("ACCEPTED")
            }
            is RoutineDraftUpdate.Review -> {
                logRoutineDraftState()
                assistantSession.getBottomSheet()?.showAssistantHint(
                    "Confirm, reject, repeat, or change one selected step."
                )
                speakRoutineResponse(
                    RoutineResponseKind.PROPOSAL,
                    update.proposal,
                    listenAgain = true
                )
                RoutineFollowUpOutcome("ACCEPTED")
            }
            is RoutineDraftUpdate.Rejected -> {
                Log.d("ROUTINE_DRAFT", "rejected=${update.reason.name}")
                if (routineDraftController.state == RoutineDraftState.NONE &&
                    ::conversationOrchestrator.isInitialized
                ) {
                    conversationOrchestrator.clearSessionMemory()
                }
                val speech = when (update.reason) {
                    RoutineDraftIssue.LOW_CONFIDENCE,
                    RoutineDraftIssue.EXTRACTION_NEEDS_CLARIFICATION,
                    RoutineDraftIssue.INVALID_STEP_COUNT,
                    RoutineDraftIssue.EMPTY_STEP_TITLE -> invalidSpeech
                    RoutineDraftIssue.PAST_SCHEDULE ->
                        "Please provide a future exact date and time."
                    else -> invalidSpeech
                }
                val kind = when (update.reason) {
                    RoutineDraftIssue.LOW_CONFIDENCE,
                    RoutineDraftIssue.EXTRACTION_NEEDS_CLARIFICATION,
                    RoutineDraftIssue.INVALID_STEP_COUNT,
                    RoutineDraftIssue.EMPTY_STEP_TITLE ->
                        RoutineResponseKind.EXTRACTION_FAILURE
                    RoutineDraftIssue.INVALID_DATE,
                    RoutineDraftIssue.PAST_SCHEDULE -> RoutineResponseKind.INVALID_DATE
                    RoutineDraftIssue.INVALID_TIME -> RoutineResponseKind.INVALID_TIME
                    RoutineDraftIssue.INVALID_STATE -> RoutineResponseKind.REVISION_HELP
                }
                speakRoutineResponse(kind, speech, listenAgain = true)
                RoutineFollowUpOutcome("REJECTED", update.reason)
            }
            RoutineDraftUpdate.Stale -> {
                Log.d("ROUTINE_DRAFT", "staleExtractionIgnored=true")
                RoutineFollowUpOutcome("UNKNOWN")
            }
        }
    }

    private fun logRoutineDraftState() {
        val current = routineDraftController.draft
        val steps = current?.steps.orEmpty()
        Log.d(
            "ROUTINE_DRAFT",
            "state=${routineDraftController.state.name} " +
                "stepCount=${steps.size} revision=${current?.revision ?: 0} " +
                "missingDateCount=${steps.count { it.resolvedDate == null }} " +
                "missingTimeCount=${steps.count { it.resolvedTime == null }}"
        )
    }

    private fun cancelPendingRoutine() {
        if (routineDraftController.state == RoutineDraftState.SAVING) return
        val count = routineDraftController.draft?.steps?.size ?: 0
        routineDraftController.clear()
        if (::conversationOrchestrator.isInitialized) {
            conversationOrchestrator.clearSessionMemory()
        }
        Log.d(
            "ROUTINE_SAVE",
            "taskCount=$count insertedCount=0 reminderSuccessCount=0 result=CANCELLED"
        )
        speakRoutineResponse(
            RoutineResponseKind.CANCELLED,
            "Okay, I will not create that routine.",
            listenAgain = false
        )
    }

    private fun savePendingRoutine() {
        val pendingSave = routineDraftController.markSaving()
        if (pendingSave == null) {
            if (routineDraftController.state == RoutineDraftState.SAVING) {
                speakRoutineResponse(
                    RoutineResponseKind.ALREADY_SAVING,
                    "The confirmed routine is already being saved.",
                    listenAgain = false
                )
            } else {
                speakRoutineResponse(
                    RoutineResponseKind.REVISION_HELP,
                    "That routine is not ready to save.",
                    listenAgain = true
                )
            }
            return
        }
        logRoutineDraftState()
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
            val creator = RoutineTaskBatchCreator(
                store = RoutineTaskStore { tasks ->
                    dao.insertRootTasksAtomically(tasks)
                },
                reminderScheduler = RoutineReminderScheduler { task ->
                    ReminderHelper.scheduleReminderFromTask(this@HomeActivity, task)
                }
            )
            val result = creator.create(
                draft = pendingSave.draft,
                saveGeneration = pendingSave.generation
            )
            if (!routineDraftController.completeSaving(pendingSave.generation)) {
                Log.d(
                    "ROUTINE_SAVE_STALE",
                    "saveGeneration=${pendingSave.generation} accepted=false"
                )
                return@launch
            }
            Log.d(
                "ROUTINE_SAVE",
                "taskCount=${result.taskCount} insertedCount=${result.insertedCount} " +
                    "reminderSuccessCount=${result.reminderSuccessCount} " +
                    "result=${result.category.name}"
            )
            conversationOrchestrator.clearSessionMemory()
            homeFollowUpContext = HomeFollowUpContext.NONE
            if (result.insertedCount == result.taskCount &&
                result.insertedCount > 0
            ) {
                refreshOverview()
            }
            if (assistantSession.assistantSessionActive) {
                speakRoutineResponse(
                    RoutineResponseKind.SAVE_RESULT,
                    RoutineResultSpeechRenderer.render(result),
                    listenAgain = false
                )
            }
        }
    }

    private fun speakRoutineResponse(
        kind: RoutineResponseKind,
        text: String,
        listenAgain: Boolean
    ) {
        DebugDiagnosticLog.longEvent(
            "ROUTINE_RESPONSE_DEBUG",
            "kind=${kind.name}\ntext=$text"
        )
        assistantSession.speak(text, listenAgain)
    }

    private fun logRoutineFollowUpDebug(
        stateBefore: RoutineDraftState,
        input: String,
        move: RoutineFollowUpMove
    ) {
        val (moveType, stepIndex, value) = when (move) {
            RoutineFollowUpMove.Confirm -> Triple("CONFIRM", "", "")
            RoutineFollowUpMove.Reject -> Triple("REJECT", "", "")
            RoutineFollowUpMove.Cancel -> Triple("CANCEL", "", "")
            RoutineFollowUpMove.Repeat -> Triple("REPEAT", "", "")
            is RoutineFollowUpMove.ChangeStepTime ->
                Triple("CHANGE_STEP_TIME", (move.stepIndex + 1).toString(), move.value)
            is RoutineFollowUpMove.ChangeStepTitle ->
                Triple("CHANGE_STEP_TITLE", (move.stepIndex + 1).toString(), move.value)
            is RoutineFollowUpMove.ChangeSharedDate ->
                Triple("CHANGE_SHARED_DATE", "", move.value)
            RoutineFollowUpMove.StructuralChange -> Triple("STRUCTURAL_CHANGE", "", "")
            RoutineFollowUpMove.Unknown -> Triple("UNKNOWN", "", "")
        }
        DebugDiagnosticLog.event(
            "ROUTINE_FOLLOW_UP_DEBUG",
            "stateBefore=${stateBefore.name}\n" +
                "input=$input\n" +
                "interpretedMove=$moveType\n" +
                "stepIndex=$stepIndex\n" +
                "value=$value"
        )
    }

    private fun logRoutineFollowUpResult(
        result: String,
        issue: RoutineDraftIssue? = null
    ) {
        DebugDiagnosticLog.event(
            "ROUTINE_FOLLOW_UP_RESULT",
            "stateAfter=${routineDraftController.state.name}\n" +
                "draftRevision=${routineDraftController.draft?.revision ?: 0}\n" +
                "result=$result\n" +
                "issue=${issue?.name.orEmpty()}"
        )
    }

    private fun logRoutineExternalCancellation(moveType: String) {
        val stateBefore = routineDraftController.state
        if (stateBefore == RoutineDraftState.NONE) return
        DebugDiagnosticLog.event(
            "ROUTINE_FOLLOW_UP_DEBUG",
            "stateBefore=${stateBefore.name}\n" +
                "input=\n" +
                "interpretedMove=$moveType\n" +
                "stepIndex=\n" +
                "value="
        )
        val saving = stateBefore == RoutineDraftState.SAVING
        DebugDiagnosticLog.event(
            "ROUTINE_FOLLOW_UP_RESULT",
            "stateAfter=${if (saving) RoutineDraftState.SAVING.name else RoutineDraftState.NONE.name}\n" +
                "draftRevision=${routineDraftController.draft?.revision ?: 0}\n" +
                "result=${if (saving) "SAVING" else "CANCELLED"}\n" +
                "issue="
        )
    }

    private fun beginAssistantRequest(): AssistantRequestToken {
        if (assistantRequestActive) {
            Log.d(
                "ASSISTANT_REQUEST_INVALIDATE",
                "reason=${AssistantRequestInvalidationReason.NEW_COMMAND}"
            )
        }
        assistantRequestGeneration += 1
        assistantRequestActive = true
        Log.d(
            "ASSISTANT_REQUEST_BEGIN",
            "requestGeneration=$assistantRequestGeneration"
        )
        return AssistantRequestToken(assistantRequestGeneration)
    }

    private fun invalidateAssistantRequest(
        reason: AssistantRequestInvalidationReason
    ) {
        assistantRequestGeneration += 1
        assistantRequestActive = false
        Log.d("ASSISTANT_REQUEST_INVALIDATE", "reason=$reason")
    }

    private fun isAssistantRequestCurrent(token: AssistantRequestToken): Boolean {
        val current = AssistantRequestTokenPolicy.isCurrent(
            token = token,
            currentRequestGeneration = assistantRequestGeneration,
            requestActive = assistantRequestActive
        )
        if (!current) {
            Log.d(
                "ASSISTANT_REQUEST_STALE",
                "capturedGeneration=${token.requestGeneration} " +
                    "currentGeneration=$assistantRequestGeneration reason=NEWER_REQUEST"
            )
        }
        return current
    }

    private fun logQueryPageEndIfActive(reason: String) {
        if (accessibleTaskQuerySession != null) {
            Log.d("HOME_QUERY_PAGE_END", "reason=$reason")
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

                    isSimpleFollowUpEndCommand(normalized) -> {
    endAssistantConversation()
    true
}

                    else -> false
                }
            }

            HomeFollowUpContext.AFTER_TASK_SUMMARY -> {
                when {
                    isSimpleFollowUpEndCommand(normalized) -> {
                        endAssistantConversation()
                        true
                    }

                    else -> false
                }
            }

            HomeFollowUpContext.AFTER_TASK_DETAILS -> {
                when {
                    isSimpleFollowUpEndCommand(normalized) -> {
                        endAssistantConversation()
                        true
                    }

                    else -> false
                }
            }

            HomeFollowUpContext.AFTER_DAILY_BRIEFING -> {
                if (isSimpleFollowUpEndCommand(normalized)) {
                    endAssistantConversation()
                    true
                } else {
                    false
                }
            }

            HomeFollowUpContext.QUERY_COUNT,
            HomeFollowUpContext.QUERY_PAGE -> false
            HomeFollowUpContext.NONE -> false
            HomeFollowUpContext.TASK_MATCH_AMBIGUITY -> false
            HomeFollowUpContext.DELETE_CONFIRMATION -> false
            HomeFollowUpContext.BREAKDOWN_CONFIRMATION -> false
            HomeFollowUpContext.BREAKDOWN_SCHEDULE_COLLECTION -> false
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

        if (BuildConfig.DEBUG) {
            Log.d(
                "TASK_MATCH",
                "spoken='$spokenTitle' best='${result.bestTask?.title}' bestScore=${result.bestScore} second='${result.secondTask?.title}' secondScore=${result.secondScore} ambiguous=${result.isAmbiguous}"
            )
        }

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
                if (BuildConfig.DEBUG) {
                    Log.d("HOME_BREAKDOWN", "main task inserted id=$parentId")
                }

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
                    if (BuildConfig.DEBUG) {
                        Log.d("HOME_BREAKDOWN", "inserted subtask title=$subtaskTitle parentId=$parentId")
                    }
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

                if (BuildConfig.DEBUG) {
                    Log.d("HOME_BREAKDOWN", "regenerating breakdown with feedback='$feedback'")
                }

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
        readOnlyTaskContextStore.replaceTaskMatchChoices(listOf(bestTask, secondTask))

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

    private fun handleConversationIntent(
        intent: ConversationIntent,
        normalized: String
    ): Boolean {
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
                    ConversationIntent.CONFIRM_NO,
                    ConversationIntent.STOP_CONVERSATION -> {
                        if (isSimpleFollowUpEndCommand(normalized)) {
                            endAssistantConversation()
                            true
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            }

            HomeFollowUpContext.AFTER_TASK_DETAILS -> {
                when (intent) {
                    ConversationIntent.CONFIRM_NO,
                    ConversationIntent.STOP_CONVERSATION -> {
                        if (isSimpleFollowUpEndCommand(normalized)) {
                            endAssistantConversation()
                            true
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            }
            HomeFollowUpContext.AFTER_DAILY_BRIEFING -> {
                when (intent) {
                    ConversationIntent.CONFIRM_NO,
                    ConversationIntent.STOP_CONVERSATION -> {
                        if (isSimpleFollowUpEndCommand(normalized)) {
                            endAssistantConversation()
                            true
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            }
            HomeFollowUpContext.QUERY_COUNT,
            HomeFollowUpContext.QUERY_PAGE -> false
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
