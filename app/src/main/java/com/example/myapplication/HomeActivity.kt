package com.example.myapplication

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings

import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.example.myapplication.accessibility.AccessibilityActivity
import com.example.myapplication.accessibility.resolveThemeColor
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Locale




import com.example.myapplication.voice.TextNormalizer
import com.example.myapplication.voice.BoundedConfirmationPolicy
import com.example.myapplication.voice.BoundedConfirmationResult
import com.example.myapplication.voice.DeleteConfirmationPolicy

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
import com.example.myapplication.ai.conversation.AppGuidanceCatalog
import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.taskcontext.AuthoritativeSubtaskReader
import com.example.myapplication.ai.conversation.taskcontext.BreakdownPostSaveContextFocusPolicy
import com.example.myapplication.ai.conversation.taskcontext.BreakdownTaskContextPublisher
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationDecisionParser
import com.example.myapplication.ai.conversation.ConversationOrchestrator
import com.example.myapplication.ai.conversation.ConversationOrchestratorException
import com.example.myapplication.ai.conversation.ConversationNavigationTarget
import com.example.myapplication.ai.conversation.ConversationRoute
import com.example.myapplication.ai.conversation.ConversationSettingAction
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
import com.example.myapplication.ai.conversation.ResponseVerbalizationDeliveryGuard
import com.example.myapplication.ai.conversation.ResponseVerbalizationDeliveryState
import com.example.myapplication.ai.conversation.ResponseVerbalizationStaleReason
import com.example.myapplication.ai.conversation.ResponseVerbalizationTone
import com.example.myapplication.ai.conversation.ResponseVerbalizationVerbosity
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
import com.example.myapplication.ai.conversation.AssistantExitInterpreter
import com.example.myapplication.ai.conversation.ConversationEndSessionSafetyPolicy
import com.example.myapplication.ai.conversation.ConversationContextFocus
import com.example.myapplication.ai.conversation.VoiceSettingRoutingContext
import com.example.myapplication.ai.conversation.SafeStyleAuthorizationPolicy
import com.example.myapplication.ai.conversation.SafeStyleAuthorizationStatus
import com.example.myapplication.ai.conversation.taskcontext.ContextReferenceMutationGuard
import com.example.myapplication.ai.conversation.taskcontext.ContextItemRestatementDisposition
import com.example.myapplication.ai.conversation.taskcontext.ContextItemRestatementPolicy
import com.example.myapplication.ai.conversation.taskcontext.ContextItemReadDisposition
import com.example.myapplication.ai.conversation.taskcontext.ContextItemReadPolicy
import com.example.myapplication.ai.conversation.taskcontext.ContextActionDecisionValidator
import com.example.myapplication.ai.conversation.taskcontext.ContextActionValidationResult
import com.example.myapplication.ai.conversation.taskcontext.ContextDeleteFailureFallbackPolicy
import com.example.myapplication.ai.conversation.taskcontext.ContextActionRepairPolicy
import com.example.myapplication.ai.conversation.taskcontext.ContextActionTargetValidator
import com.example.myapplication.ai.conversation.taskcontext.TaskCompletionMutationPolicy
import com.example.myapplication.ai.conversation.taskcontext.TaskCompletionReminderDirective
import com.example.myapplication.ai.conversation.taskcontext.SubtaskCompletionContextRefresher
import com.example.myapplication.ai.conversation.taskcontext.ContextActionReferenceGroundingValidator
import com.example.myapplication.ai.conversation.taskcontext.ContextReadValidationResult
import com.example.myapplication.ai.conversation.taskcontext.ContextReadDetailCompatibilityPolicy
import com.example.myapplication.ai.conversation.taskcontext.ContextReadRepairDisposition
import com.example.myapplication.ai.conversation.taskcontext.ContextReadRepairPolicy
import com.example.myapplication.ai.conversation.taskcontext.ContextFocusCarryForwardPolicy
import com.example.myapplication.ai.conversation.taskcontext.ContextFocusActionEllipsisPolicy
import com.example.myapplication.ai.conversation.taskcontext.PendingContextActionTargetMove
import com.example.myapplication.ai.conversation.taskcontext.PendingContextActionTargetAuthorityValidator
import com.example.myapplication.ai.conversation.taskcontext.PresentedQueryFocusPolicy
import com.example.myapplication.ai.conversation.taskcontext.PresentedQueryFocusResult
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextStore
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextCapture
import com.example.myapplication.ai.conversation.taskcontext.TaskContextScope
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextReadValidator
import com.example.myapplication.ai.conversation.taskcontext.ReadOnlyTaskContextResponseRenderer
import com.example.myapplication.ai.conversation.taskcontext.ValidatedContextRead
import com.example.myapplication.ai.conversation.suggestion.ContextSuggestionDecision
import com.example.myapplication.ai.conversation.suggestion.ContextSuggestionDeliveryGuard
import com.example.myapplication.ai.conversation.suggestion.ContextSuggestionDeliveryResult
import com.example.myapplication.ai.conversation.suggestion.ContextSuggestionFocusPolicy
import com.example.myapplication.ai.conversation.suggestion.ContextSuggestionSemanticOrchestrator
import com.example.myapplication.ai.conversation.suggestion.ContextSuggestionSnapshot
import com.example.myapplication.ai.conversation.suggestion.ContextSuggestionSnapshotBuilder
import com.example.myapplication.ai.conversation.suggestion.ContextSuggestionSpeechRenderer
import com.example.myapplication.ai.conversation.suggestion.ContextSuggestionType
import com.example.myapplication.ai.conversation.querypresentation.QueryPresentationSemanticOrchestrator
import com.example.myapplication.ai.conversation.query.AccessibleTaskQuerySession
import com.example.myapplication.ai.conversation.query.AuthoritativeRepeatState
import com.example.myapplication.ai.conversation.query.QueryReadingControlPolicy
import com.example.myapplication.ai.conversation.query.QueryReadingInteractionState
import com.example.myapplication.ai.conversation.query.QueryCountFollowUpMove
import com.example.myapplication.ai.conversation.query.RepeatableSpeechKind
import com.example.myapplication.voice.AssistantResponseManager
import com.example.myapplication.voice.AssistantTone
import com.example.myapplication.voice.AssistantVerbosity
import com.example.myapplication.voice.VoiceSettingsDecisionValidator
import com.example.myapplication.voice.VoiceSettingsExecutor
import com.example.myapplication.voice.VoiceSettingsReadDecisionValidator
import com.example.myapplication.voice.VoiceSettingsReadRecoveryPolicy
import com.example.myapplication.voice.VoiceSettingsStatusReader
import com.example.myapplication.voice.VoiceSettingReadStatus
import com.example.myapplication.voice.VoiceSettingsMutationSafetyPolicy
import com.example.myapplication.voice.VoiceSettingsSafetyDisposition
import com.example.myapplication.voice.VoiceSettingsSafetyResult
import com.example.myapplication.voice.VoiceSettingConversationContext
import com.example.myapplication.voice.VoiceSettingContextualActionResolver
import com.example.myapplication.voice.voiceSettingTarget
import java.text.SimpleDateFormat

import com.example.myapplication.voice.AssistantVoiceHost
import com.example.myapplication.voice.AssistantVoiceSession
import com.example.myapplication.accessibility.AccessibleAssistantInputDialog
import com.example.myapplication.accessibility.AccessibilityStateHelper
import com.example.myapplication.accessibility.AssistantAccessibilityState

import com.example.myapplication.ai.ConversationIntent
import com.example.myapplication.ai.TaskMatcher
import com.example.myapplication.ai.NamedTaskQueryResolver
import com.example.myapplication.ai.NamedTaskQueryStatus
import com.example.myapplication.ai.TaskQueryDetail

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
import com.example.myapplication.ai.temporal.ExactTemporalSchedule
import com.example.myapplication.ai.temporal.RelativeTemporalCalculationResult
import com.example.myapplication.ai.temporal.RelativeTemporalChangeCalculator
import com.example.myapplication.ai.temporal.RelativeTemporalOperation
import com.example.myapplication.ai.temporal.RelativeTemporalSpeechRenderer
import com.example.myapplication.ai.routine.RoutineDraftController
import com.example.myapplication.ai.routine.RoutineDraftIssue
import com.example.myapplication.ai.routine.RoutineDraftState
import com.example.myapplication.ai.routine.RoutineDraftUpdate
import com.example.myapplication.ai.routine.RoutineFollowUpInterpreter
import com.example.myapplication.ai.routine.RoutineFollowUpMove
import com.example.myapplication.ai.routine.RoutineReminderScheduler
import com.example.myapplication.ai.routine.RoutineResultSpeechRenderer
import com.example.myapplication.ai.routine.RoutinePersistenceCoordinator
import com.example.myapplication.ai.routine.RoutinePersistenceStore
import com.example.myapplication.ai.routine.RoutineMatcher
import com.example.myapplication.ai.routine.RoutineMatchResult
import com.example.myapplication.ai.routine.RoutineDraftOrigin
import com.example.myapplication.ai.routine.followup.RoutineFollowUpAgentContext
import com.example.myapplication.ai.routine.followup.RoutineFollowUpDeliveryGuard
import com.example.myapplication.ai.routine.followup.RoutineFollowUpSemanticFallbackPolicy
import com.example.myapplication.ai.routine.followup.RoutineFollowUpSemanticOrchestrator
import com.example.myapplication.ai.routine.saved.SavedRoutineAction
import com.example.myapplication.ai.routine.saved.SavedRoutineActionConsistencyPolicy
import com.example.myapplication.ai.routine.saved.SavedRoutineActionDecision
import com.example.myapplication.ai.routine.saved.SavedRoutineActionSchemaException
import com.example.myapplication.ai.routine.saved.SavedRoutineCandidate
import com.example.myapplication.ai.routine.saved.SavedRoutineChoice
import com.example.myapplication.ai.routine.saved.SavedRoutineInteractionController
import com.example.myapplication.ai.routine.saved.SavedRoutineInteractionState
import com.example.myapplication.ai.routine.saved.SavedRoutineSemanticOrchestrator
import com.example.myapplication.ai.breakdown.BreakdownControlInterpreter
import com.example.myapplication.ai.breakdown.BreakdownDraftController
import com.example.myapplication.ai.breakdown.BreakdownDraftMode
import com.example.myapplication.ai.breakdown.BreakdownDraftState
import com.example.myapplication.ai.breakdown.BreakdownDraftUpdate
import com.example.myapplication.ai.breakdown.BreakdownFollowUpContext
import com.example.myapplication.ai.breakdown.BreakdownFollowUpException
import com.example.myapplication.ai.breakdown.BreakdownFollowUpMove
import com.example.myapplication.ai.breakdown.BreakdownFollowUpSemanticClient
import com.example.myapplication.ai.breakdown.BreakdownFollowUpSemanticOrchestrator
import com.example.myapplication.ai.breakdown.BreakdownPersistenceCoordinator
import com.example.myapplication.ai.breakdown.BreakdownPersistenceStore
import com.example.myapplication.ai.breakdown.BreakdownReminderScheduler
import com.example.myapplication.ai.breakdown.BreakdownSaveResultCategory
import com.example.myapplication.ai.breakdown.BreakdownTargetPreference
import com.example.myapplication.ai.breakdown.BreakdownTargetResolution
import com.example.myapplication.ai.breakdown.BreakdownTargetResolver
import com.example.myapplication.ai.breakdown.PendingBreakdownDraft
import com.example.myapplication.data.RoutineOccurrenceInsertResult
import com.example.myapplication.data.RoutineWithSteps
import com.example.myapplication.data.BreakdownTransactionResult
import com.example.myapplication.diagnostics.DebugDiagnosticLog
import com.example.myapplication.reminder.LegacyReminderCanceller
import com.example.myapplication.reminder.ReminderBootstrapLogger
import com.example.myapplication.reminder.ReminderBootstrapScheduler
import com.example.myapplication.reminder.ReminderBootstrapTaskSource
import com.example.myapplication.reminder.ReminderEscalationBootstrapper
import com.example.myapplication.reminder.SharedPreferencesReminderBootstrapVersionStore
import com.example.myapplication.preferences.AppPreferences
import com.example.myapplication.voice.AssistantInteractionMode
import com.example.myapplication.voice.AssistantTranscriptEvent

open class HomeActivity : AccessibilityActivity(), AssistantVoiceHost {
    private var pendingAssistantEntry: HomeAssistantEntry? = null
    private var assistantEntryGeneration: Long = 0
    private var homePreviewTask: TaskEntity? = null
    private var homePreviewSpeech: String? = null
    private lateinit var conversationIntentClassifier: LocalConversationIntentClassifier

    private lateinit var responseManager: AssistantResponseManager
    private lateinit var voiceSettingsExecutor: VoiceSettingsExecutor
    private lateinit var voiceSettingsStatusReader: VoiceSettingsStatusReader
    private lateinit var agentOrchestrator: AgentOrchestrator
    private lateinit var conversationOrchestrator: ConversationOrchestrator
    private lateinit var routineFollowUpSemanticOrchestrator:
        RoutineFollowUpSemanticOrchestrator
    private lateinit var savedRoutineSemanticOrchestrator:
        SavedRoutineSemanticOrchestrator
    private lateinit var contextSuggestionSemanticOrchestrator:
        ContextSuggestionSemanticOrchestrator
    private lateinit var queryPresentationSemanticOrchestrator:
        QueryPresentationSemanticOrchestrator
    private lateinit var breakdownFollowUpSemanticOrchestrator:
        BreakdownFollowUpSemanticOrchestrator
    private val readOnlyTaskContextStore = ReadOnlyTaskContextStore()


    private lateinit var assistantSession: AssistantVoiceSession
    private lateinit var voiceHelper: VoiceHelper
    private var developerAssistantOverlay: DeveloperAssistantOverlay? = null

    private enum class HomeFollowUpContext {
        NONE,
        AFTER_NO_TASKS,
        AFTER_TASK_SUMMARY,
        AFTER_TASK_DETAILS,
        AFTER_DAILY_BRIEFING,
        AFTER_CONTEXT_SUGGESTION,
        QUERY_COUNT,
        QUERY_PAGE,
        CONTEXT_ACTION_TARGET_CLARIFICATION,
        CONTEXT_ACTION_CHANGE_CLARIFICATION,
        TASK_MATCH_AMBIGUITY,
        DELETE_CONFIRMATION,
        BREAKDOWN_CONFIRMATION,
        BREAKDOWN_SCHEDULE_COLLECTION
    }
    private data class PendingContextActionClarification(
        val action: ConversationContextAction,
        val originalNormalizedRequest: String,
        val capturedGeneration: Long,
        val suppliedRefs: Set<String>,
        val returnContext: HomeFollowUpContext
    )
    private data class PendingContextActionChangeClarification(
        val action: ConversationContextAction,
        val authorityValidatedRef: String,
        val capturedGeneration: Long,
        val authoritativeTaskSnapshot: TaskEntity,
        val originalNormalizedRequest: String,
        val returnContext: HomeFollowUpContext
    )
    private data class PendingContextActionResolution(
        val decision: ConversationDecision? = null,
        val originalActionRequest: String? = null,
        val authorityValidatedRef: String? = null
    )
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
    private var pendingContextActionClarification: PendingContextActionClarification? = null
    private var pendingContextActionChangeClarification:
        PendingContextActionChangeClarification? = null
    private val temporalQueryResolver = TemporalQueryResolver()
    private val namedTaskQueryResolver = NamedTaskQueryResolver(temporalQueryResolver)
    private var accessibleTaskQuerySession: AccessibleTaskQuerySession? = null
    private var authoritativeRepeatState: AuthoritativeRepeatState? = null
    private var currentQueryPageRepeatState: AuthoritativeRepeatState? = null
    private var queryReadingStateGeneration: Long = 0
    private var assistantRequestGeneration: Long = 0
    private var assistantRequestActive: Boolean = false
    private var pendingVoiceDisplayRefresh: Boolean = false
    private val voiceSettingConversationContext = VoiceSettingConversationContext()
    private val routineDraftController = RoutineDraftController()
    private val breakdownDraftController = BreakdownDraftController()
    private val savedRoutineInteractionController = SavedRoutineInteractionController()
    private val routineStepTimeClarification =
        "Please provide one exact clock time for that step."

    //for delete confirmation when the task intent is 'delete'
    private var pendingDeleteTaskId: Long? = null
    private var pendingDeleteTaskTitle: String? = null

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
    private var reminderBootstrapInProgress = false
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

        val taskAgentClient = LaptopAgentClient(this)
        agentOrchestrator = AgentOrchestrator(
            taskAgentClient,
            TaskAgentResponseParser(),
            TaskActionNormalizer(),
            ActionValidator()
        )
        breakdownFollowUpSemanticOrchestrator =
            BreakdownFollowUpSemanticOrchestrator(
                client = BreakdownFollowUpSemanticClient { userText, contextSummary ->
                    taskAgentClient.processBreakdownFollowUp(userText, contextSummary)
                }
            )
        val conversationAgentClient = ConversationAgentClient(this)
        conversationOrchestrator = ConversationOrchestrator(
            conversationAgentClient,
            ConversationDecisionParser()
        )
        routineFollowUpSemanticOrchestrator = RoutineFollowUpSemanticOrchestrator(
            conversationAgentClient
        )
        savedRoutineSemanticOrchestrator = SavedRoutineSemanticOrchestrator(
            conversationAgentClient
        )
        contextSuggestionSemanticOrchestrator = ContextSuggestionSemanticOrchestrator(
            conversationAgentClient
        )
        queryPresentationSemanticOrchestrator = QueryPresentationSemanticOrchestrator(
            conversationAgentClient
        )
        conversationIntentClassifier = LocalConversationIntentClassifier(this)

        val greetingText = findViewById<TextView>(R.id.greetingText)

        // declaring btns
        val btnTodayTasks = findViewById<Button>(R.id.btnTodayTasks)
        val btnCreateTask = findViewById<Button>(R.id.btnCreateTask)
        val btnScheduledTasks = findViewById<Button>(R.id.btnScheduledTasks)
        val btnTalkAssistant = findViewById<Button>(R.id.btnTalkAssistant)
        val btnSettings = findViewById<Button>(R.id.btnSettings)
        val todayPreviewSurface = findViewById<View>(R.id.todayPreviewSurface)

        // Greeting
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> getString(R.string.good_morning)
            hour < 18 -> getString(R.string.good_afternoon)
            else -> getString(R.string.good_evening)
        }
        greetingText.text = greeting
        greetingText.contentDescription = greeting
        AccessibilityStateHelper.markHeading(greetingText)
        AccessibilityStateHelper.markHeading(findViewById(R.id.todaySectionHeading))


        // Navigation buttons
        VoiceFirstGestureBinder.bindAction(
            view = btnTodayTasks,
            speechProvider = HomeControlSpeechRenderer::todayTasks,
            speak = ::speakControlIdentification,
            activate = {
                speakThenOpen("opening today's task.") {
                    startActivity(Intent(this, TodayTasksActivity::class.java))
                }
            }
        )
        VoiceFirstGestureBinder.bindAction(
            view = btnCreateTask,
            speechProvider = HomeControlSpeechRenderer::createTask,
            speak = ::speakControlIdentification,
            activate = {
                speakThenOpen("opening task create.") {
                    startActivity(Intent(this, CreateTaskActivity::class.java))
                }
            }
        )

        VoiceFirstGestureBinder.bindAction(
            view = btnScheduledTasks,
            speechProvider = HomeControlSpeechRenderer::scheduledTasks,
            speak = ::speakControlIdentification,
            activate = {
                speakThenOpen("opening scheduled task.") {
                    startActivity(Intent(this, MainActivity::class.java))
                }
            }
        )

        VoiceFirstGestureBinder.bindAction(
            view = btnSettings,
            speechProvider = HomeControlSpeechRenderer::settings,
            speak = ::speakControlIdentification,
            activate = {
                speakThenOpen("opening settings.") {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
            }
        )
        VoiceFirstGestureBinder.bindAction(
            view = todayPreviewSurface,
            speechProvider = { homePreviewSpeech },
            speak = ::speakControlIdentification,
            activate = {
                homePreviewTask?.let { task ->
                    speakThenOpen(TaskNavigationSpeechRenderer.openingDetails(task.title)) {
                        startActivity(Intent(this, TaskDetailActivity::class.java).apply {
                            putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.id)
                        })
                    }
                }
            }
        )

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
        val appPreferences = AppPreferences(this)
        voiceSettingsExecutor = VoiceSettingsExecutor(appPreferences)
        voiceSettingsStatusReader = VoiceSettingsStatusReader(appPreferences)

        assistantSession = AssistantVoiceSession(
            activity = this,
            host = this,
            voiceHelper = voiceHelper,
            responseManager = responseManager,
            audioPermissionLauncher = audioPermissionLauncher,
            onAccessibilityStateChanged = { state ->
                AccessibilityStateHelper.updateAssistantState(btnTalkAssistant, state, announce = false)
                onAssistantPresentationStateChanged(state)
            },
            interactionMode = assistantInteractionMode,
            shouldSpeakAudio = ::shouldSpeakAssistantAudio,
            transcriptObserver = ::onAssistantTranscript
        )
        assistantSession.bindAssistantControl(btnTalkAssistant)
        AccessibilityStateHelper.updateAssistantState(
            btnTalkAssistant,
            AssistantAccessibilityState.READY,
            announce = false
        )



        VoiceFirstGestureBinder.bindAction(
            view = btnTalkAssistant,
            speechProvider = HomeControlSpeechRenderer::assistant,
            speak = ::speakControlIdentification,
            activate = ::startGenericAssistantSession
        )

        btnTalkAssistant.setOnLongClickListener {
            btnTalkAssistant.performLongClickHapticFeedback()
            prepareGenericAssistantSession()
            showTypedAssistantInputDialog()
            true
        }
        AccessibilityStateHelper.exposeTypedInputAction(btnTalkAssistant)

        acceptAssistantEntry(intent)
        developerAssistantOverlay = DeveloperAssistantOverlay.attach(
            activity = this,
            onSubmit = ::submitPersistentTypedAssistantText
        )


    } // end of onCreate

    private fun showTypedAssistantInputDialog() {
        AccessibleAssistantInputDialog.show(
            activity = this,
            title = "Type assistant command",
            message = "Typed and voice input use the same assistant flow.",
            emptyError = "Please type a command",
            onCancel = assistantSession::onTypedInputCancelled
        ) { typedText ->
            assistantSession.submitTypedText(typedText)
        }
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
        savedRoutineInteractionController.clear()
        if (routineDraftController.state != RoutineDraftState.SAVING) {
            routineDraftController.clear()
        }
        applyPendingVoiceDisplayRefresh()
    }

    override fun onAssistantSessionStopped() {
        logRoutineExternalCancellation("SESSION_STOP")
        invalidateAssistantRequest(AssistantRequestInvalidationReason.SESSION_STOPPED)
        clearConversationSessionContext()
        homeFollowUpContext = HomeFollowUpContext.NONE
        clearPendingTaskMatchState()
        clearPendingDeleteState()
        clearPendingBreakdownState()
        savedRoutineInteractionController.clear()
        if (routineDraftController.state != RoutineDraftState.SAVING) {
            routineDraftController.clear()
        }
        applyPendingVoiceDisplayRefresh()
    }

    override fun onAssistantTypedInputRequested() {
        showTypedAssistantInputDialog()
    }

    protected open val assistantInteractionMode: AssistantInteractionMode
        get() = DeveloperTestSession.interactionMode()

    protected open fun shouldSpeakAssistantAudio(): Boolean =
        DeveloperTestSession.shouldSpeakAudio()

    protected open fun onAssistantTranscript(event: AssistantTranscriptEvent) {
        DeveloperTestSession.recordTranscript(event)
    }

    protected open fun onAssistantPresentationStateChanged(
        state: AssistantAccessibilityState
    ) {
        DeveloperTestSession.updateAssistantState(state)
    }

    protected open fun preserveAssistantSessionWhileStopped(): Boolean =
        DeveloperTestSession.isActive

    protected fun submitPersistentTypedAssistantText(text: String) {
        assistantSession.submitTypedText(text, clearConversation = false)
    }

    override fun onResume(){
        super.onResume()

        refreshOverview()

        val reminderPermissionsReady = allRequiredPermissionsReady()
        if (reminderPermissionsReady) {
            runReminderEscalationBootstrapIfReady()
        } else if(!hasShownPermissionDialog){
            hasShownPermissionDialog = true
            showReminderSetupDialog()
        }

        processPendingAssistantEntry()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptAssistantEntry(intent)
        processPendingAssistantEntry()
    }

    private fun acceptAssistantEntry(intent: Intent) {
        val entry = HomeAssistantEntryContract.read(intent) ?: return
        HomeAssistantEntryContract.consume(intent)
        assistantEntryGeneration += 1
        pendingAssistantEntry = entry
        if (entry.openAssistant && entry.entryMode != HomeAssistantEntryMode.GENERIC) {
            assistantSession.prepareForContextEntry()
        }
    }

    private fun processPendingAssistantEntry() {
        val entry = pendingAssistantEntry ?: return
        pendingAssistantEntry = null
        if (!entry.openAssistant) return
        val capturedEntryGeneration = assistantEntryGeneration
        when (entry.entryMode) {
            HomeAssistantEntryMode.GENERIC -> startGenericAssistantSession()
            HomeAssistantEntryMode.TASK_DETAIL_CONTEXT,
            HomeAssistantEntryMode.TASK_DETAIL_DELETE_CONFIRMATION -> {
                loadTaskDetailAssistantEntry(entry, capturedEntryGeneration)
            }
        }
    }

    private fun startGenericAssistantSession() {
        prepareGenericAssistantSession()
        assistantSession.startSession()
    }

    private fun prepareGenericAssistantSession() {
        invalidateAssistantRequest(AssistantRequestInvalidationReason.NEW_COMMAND)
        clearConversationSessionContext()
        homeFollowUpContext = HomeFollowUpContext.NONE
        clearPendingTaskMatchState()
        clearPendingDeleteState()
    }

    private fun loadTaskDetailAssistantEntry(
        entry: HomeAssistantEntry,
        capturedEntryGeneration: Long
    ) {
        lifecycleScope.launch {
            val taskId = entry.contextTaskId
            val roomData = if (taskId == null) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
                    val task = dao.getById(taskId) ?: return@withContext null
                    task to dao.getSubtasks(taskId)
                }
            }
            if (capturedEntryGeneration != assistantEntryGeneration) return@launch
            if (roomData == null) {
                handleUnavailableTaskDetailEntry(entry.entryMode)
                return@launch
            }

            invalidateAssistantRequest(AssistantRequestInvalidationReason.NEW_COMMAND)
            clearConversationSessionContext()
            clearPendingTaskMatchState()
            clearPendingDeleteState()
            val task = roomData.first
            val subtasks = roomData.second
            currentSubtasksByParentId = mapOf(task.id to subtasks)

            when (entry.entryMode) {
                HomeAssistantEntryMode.TASK_DETAIL_CONTEXT -> {
                    val capture = publishTaskDetailAssistantContext(task, subtasks)
                    homeFollowUpContext = HomeFollowUpContext.AFTER_TASK_DETAILS
                    Log.d(
                        TASK_DETAIL_ENTRY_TAG,
                        "entryMode=TASK_DETAIL_CONTEXT taskAvailable=true " +
                            "contextScope=${capture.snapshot.scope} " +
                            "contextGeneration=${capture.snapshot.generation} " +
                            "contextItemCount=${capture.snapshot.items.size} " +
                            "focusEstablished=true"
                    )
                    assistantSession.startPassiveSession()
                    assistantSession.speak(
                        "You are asking about ${task.title}. What would you like to know?",
                        listenAgain = true
                    )
                }

                HomeAssistantEntryMode.TASK_DETAIL_DELETE_CONFIRMATION -> {
                    val capture = publishTaskDetailAssistantContext(task, subtasks)
                    Log.d(
                        TASK_DETAIL_ENTRY_TAG,
                        "entryMode=TASK_DETAIL_DELETE_CONFIRMATION taskAvailable=true " +
                            "contextScope=${capture.snapshot.scope} " +
                            "contextGeneration=${capture.snapshot.generation} " +
                            "contextItemCount=${capture.snapshot.items.size} " +
                            "focusEstablished=true"
                    )
                    assistantSession.startPassiveSession()
                    askDeleteConfirmation(task)
                }

                HomeAssistantEntryMode.GENERIC -> Unit
            }
        }
    }

    private fun publishTaskDetailAssistantContext(
        task: com.example.myapplication.data.TaskEntity,
        subtasks: List<com.example.myapplication.data.TaskEntity>
    ): ReadOnlyTaskContextCapture {
        readOnlyTaskContextStore.replaceTaskDetailResult(task, subtasks)
        val capture = readOnlyTaskContextStore.capture()
        val item = capture.snapshot.items.single()
        conversationOrchestrator.setAuthoritativeContextFocus(
            item = item,
            selectedRef = item.ref,
            capturedGeneration = capture.snapshot.generation
        )
        return capture
    }

    private fun handleUnavailableTaskDetailEntry(entryMode: HomeAssistantEntryMode) {
        invalidateAssistantRequest(AssistantRequestInvalidationReason.NEW_COMMAND)
        clearConversationSessionContext()
        homeFollowUpContext = HomeFollowUpContext.NONE
        clearPendingTaskMatchState()
        clearPendingDeleteState()
        Log.d(
            TASK_DETAIL_ENTRY_TAG,
            "entryMode=${entryMode.name} taskAvailable=false " +
                "contextGeneration=${readOnlyTaskContextStore.currentGeneration()}"
        )
        assistantSession.startPassiveSession()
        assistantSession.speak("That task is no longer available.", listenAgain = false)
    }

    private fun refreshOverview() {
        val dao = AppDatabase.getInstance(this).taskDao()

        lifecycleScope.launch {
            val now = Date()
            val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now)
            val presentation = withContext(Dispatchers.IO) {
                val rootTasks = dao.getRootTasks()
                val todayRootTasks = dao.getRootTasksForDate(today)
                HomeOverviewPresenter.present(
                    rootTasks = rootTasks,
                    todayRootTasks = todayRootTasks,
                    todayDate = today,
                    now = now
                )
            }
            renderHomeOverview(presentation)
        }
    }

    private fun renderHomeOverview(presentation: HomeOverviewPresentation) {
        findViewById<TextView>(R.id.overviewText).text = resources.getQuantityString(
            R.plurals.home_tasks_today,
            presentation.todayTaskCount,
            presentation.todayTaskCount
        )

        findViewById<TextView>(R.id.overdueText).apply {
            visibility = if (presentation.overdueTaskCount == 0) View.GONE else View.VISIBLE
            if (presentation.overdueTaskCount > 0) {
                text = resources.getQuantityString(
                    R.plurals.home_overdue_tasks,
                    presentation.overdueTaskCount,
                    presentation.overdueTaskCount
                )
            }
        }

        val previewSurface = findViewById<View>(R.id.todayPreviewSurface)
        val preview = presentation.preview
        homePreviewTask = preview?.task
        homePreviewSpeech = preview?.spokenSummary
        if (preview == null) {
            previewSurface.visibility = View.GONE
            previewSurface.contentDescription = null
            return
        }

        findViewById<TextView>(R.id.todayPreviewTitle).text = preview.title
        findViewById<TextView>(R.id.todayPreviewTime).apply {
            text = preview.dueTime
            visibility = if (preview.dueTime == null) View.GONE else View.VISIBLE
        }
        val statusColor = resolveThemeColor(homeStatusColorAttribute(preview.visualStatus))
        findViewById<View>(R.id.todayPreviewStatusSignifier).setBackgroundColor(statusColor)
        findViewById<TextView>(R.id.todayPreviewStatus).apply {
            setText(homeStatusText(preview.visualStatus))
            setTextColor(statusColor)
        }
        previewSurface.contentDescription = preview.contentDescription
        previewSurface.visibility = View.VISIBLE
    }

    private fun homeStatusText(status: TaskVisualStatus): Int = when (status) {
        TaskVisualStatus.OVERDUE -> R.string.home_status_overdue
        TaskVisualStatus.DUE_TODAY -> R.string.home_status_due_today
        TaskVisualStatus.UPCOMING -> R.string.home_status_upcoming
        TaskVisualStatus.COMPLETED -> R.string.home_status_completed
        TaskVisualStatus.UNSCHEDULED -> R.string.home_status_unscheduled
    }

    private fun homeStatusColorAttribute(status: TaskVisualStatus): Int = when (status) {
        TaskVisualStatus.OVERDUE -> R.attr.appColorHomeStatusOverdue
        TaskVisualStatus.DUE_TODAY -> R.attr.appColorHomeStatusDueToday
        TaskVisualStatus.UPCOMING -> R.attr.appColorHomeStatusUpcoming
        TaskVisualStatus.COMPLETED -> R.attr.appColorHomeStatusCompleted
        TaskVisualStatus.UNSCHEDULED -> R.attr.appColorHomeStatusUnscheduled
    }

    private fun allRequiredPermissionsReady(): Boolean {
        val notificationReady =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
        val exactAlarmReady =
            isExactAlarmPermissionReady()
        return notificationReady && exactAlarmReady
    }

    private fun isExactAlarmPermissionReady(): Boolean = try {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .canScheduleExactAlarms()
    } catch (_: RuntimeException) {
        false
    }

    private fun runReminderEscalationBootstrapIfReady() {
        if (reminderBootstrapInProgress || !allRequiredPermissionsReady()) return
        reminderBootstrapInProgress = true
        lifecycleScope.launch {
            try {
                val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
                val bootstrapper = ReminderEscalationBootstrapper(
                    taskSource = ReminderBootstrapTaskSource {
                        dao.getRootActiveTasks()
                    },
                    scheduler = ReminderBootstrapScheduler { task ->
                        ReminderHelper.scheduleReminderFromTask(
                            this@HomeActivity,
                            task
                        )
                    },
                    legacyCanceller = LegacyReminderCanceller { taskId ->
                        ReminderHelper.cancelLegacyReminder(
                            this@HomeActivity,
                            taskId
                        )
                    },
                    versionStore = SharedPreferencesReminderBootstrapVersionStore(
                        this@HomeActivity
                    ),
                    exactAlarmPermissionReady = ::isExactAlarmPermissionReady,
                    logger = ReminderBootstrapLogger { result ->
                        if (BuildConfig.DEBUG) {
                            Log.d(
                                "REMINDER_ESCALATION_BOOTSTRAP",
                                "eligibleCount=${result.eligibleCount} " +
                                    "scheduledCount=${result.scheduledCount} " +
                                    "rejectedCount=${result.rejectedCount} " +
                                    "outcome=${result.outcome.name}"
                            )
                        }
                    }
                )
                withContext(Dispatchers.IO) {
                    bootstrapper.runIfNeeded()
                }
            } finally {
                reminderBootstrapInProgress = false
            }
        }
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
                return
            }
        }
        runReminderEscalationBootstrapIfReady()
    }

    private fun checkExactAlarmPermissionAfterReturn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                showPermissionDeniedDialog(
                    "Alarms & reminders is still disabled. Task reminders may not work until it is enabled."
                )
                return
            }
        }
        runReminderEscalationBootstrapIfReady()
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
                    "Ask what the first, second, or another recently read result was.",
                    "Ask for a recently read task's date, time, status, or subtask summary.",
                    "Give another task command using the task name, or end the assistant session."
                )
            )

            HomeFollowUpContext.AFTER_TASK_DETAILS -> Pair(
                "Detailed task results were already read.",
                listOf(
                    "Ask what the first, second, or another recently read result was.",
                    "Ask for a recently read task's date, time, status, or subtask summary.",
                    "Give another task command using the task name, or end the assistant session."
                )
            )

            HomeFollowUpContext.AFTER_DAILY_BRIEFING -> Pair(
                "An on-demand daily briefing was read.",
                listOf(
                    "Ask what the first, second, or another spoken task was.",
                    "Ask for a spoken task's date, time, status, or subtask summary.",
                    "Give a contextual update or reschedule request for a spoken task.",
                    "Ask to repeat the exact briefing, request the full task list, or end the session."
                )
            )

            HomeFollowUpContext.AFTER_CONTEXT_SUGGESTION -> Pair(
                "An on-demand context-aware task suggestion was read.",
                listOf(
                    "Ask about the selected task or either selected task in a close-schedule pair.",
                    "Ask for a spoken task's date, time, status, or subtask summary.",
                    "Give a separate explicit task command using the task name.",
                    "Ask to repeat the exact suggestion or end the session."
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
                "The current group of task-query results was read.",
                listOf(
                    "Say continue for the next group, repeat the current group, or stop.",
                    "Ask what the first, second, or another task in the current group was.",
                    "Ask for a current task's date, time, status, or subtask summary.",
                    "Give a contextual update or reschedule request for a task in the current group."
                )
            )

            HomeFollowUpContext.CONTEXT_ACTION_TARGET_CLARIFICATION -> Pair(
                "The assistant is waiting to learn which recently presented task the user means.",
                listOf(
                    "Choose one recently presented task by name or position in the spoken list.",
                    "Give a fresh task command to abandon this clarification."
                )
            )

            HomeFollowUpContext.CONTEXT_ACTION_CHANGE_CLARIFICATION -> Pair(
                "A task and contextual action are already grounded, and the assistant is waiting " +
                    "for the requested change details.",
                listOf(
                    "Provide the missing title or schedule change for the already selected task.",
                    "Cancel or stop without changing the task."
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
                "One task deletion is waiting for confirmation and has not happened yet.",
                listOf(
                    "Say yes or confirm to delete the pending task.",
                    "Say no or cancel to keep the pending task.",
                    "Ask a read-only question about the pending task before deciding."
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
                "The assistant is preparing a new reusable routine proposal.",
                listOf("Wait for the proposal or cancel the routine request.")
            )
            RoutineDraftState.COLLECTING_SHARED_DATE -> Pair(
                "A routine occurrence draft needs one exact shared date.",
                listOf("Provide an exact date or cancel the routine.")
            )
            RoutineDraftState.COLLECTING_STEP_TIME -> Pair(
                "A routine draft needs an exact time for the next missing step.",
                listOf("Provide one exact time or cancel the routine.")
            )
            RoutineDraftState.WAITING_FOR_CONFIRMATION -> Pair(
                "A complete routine proposal is waiting for explicit confirmation.",
                listOf(
                    "Confirm or reject the complete proposal.",
                    "Change one selected step's time or title, or change the shared date.",
                    "Repeat the complete proposal."
                )
            )
            RoutineDraftState.SAVING -> Pair(
                "The confirmed routine occurrence is being saved.",
                listOf("Wait for saving and reminder scheduling to finish.")
            )
        }

        val baseGuidanceContext = AppGuidanceCatalog.homeContext(
            interactionState = homeFollowUpContext.name,
            currentInteraction = interaction.first,
            currentInteractionGuidance = interaction.second
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



        if (routineDraftController.state != RoutineDraftState.NONE &&
            handleRoutineFollowUp(normalized, requestToken)
        ) {
            return
        }

        if (savedRoutineInteractionController.state != SavedRoutineInteractionState.NONE) {
            handleSavedRoutineInteractionFollowUp(normalized, requestToken)
            return
        }

        if (isConversationExitCommand(normalized)) {
            endAssistantConversation()
            return
        }

        if (handlePendingVoiceSettingClarification(normalized)) {
            return
        }

        if (homeFollowUpContext == HomeFollowUpContext.TASK_MATCH_AMBIGUITY) {
            handleTaskMatchAmbiguity(normalized)
            return
        }

        if (breakdownDraftController.state != BreakdownDraftState.NONE) {
            if (handleBreakdownFollowUp(normalized, requestToken)) {
                return
            }
        }

        if (handleBoundedDeleteConfirmation(normalized)) {
            return
        }

        val pendingContextTargetOwnsTurn =
            homeFollowUpContext == HomeFollowUpContext.CONTEXT_ACTION_TARGET_CLARIFICATION ||
                homeFollowUpContext == HomeFollowUpContext.CONTEXT_ACTION_CHANGE_CLARIFICATION
        if (!pendingContextTargetOwnsTurn && handleContextItemRestatement(normalized)) {
            return
        }

        if (!pendingContextTargetOwnsTurn && handleContextItemRead(normalized)) {
            return
        }

        if (!pendingContextTargetOwnsTurn && handleQueryReadingFollowUp(
                normalized,
                requestToken,
                localStyleAuthorization
            )
        ) {
            return
        }

        if (homeFollowUpContext != HomeFollowUpContext.NONE && !pendingContextTargetOwnsTurn) {
            val shouldDeferContextReference =
                (homeFollowUpContext == HomeFollowUpContext.AFTER_TASK_SUMMARY ||
                    homeFollowUpContext == HomeFollowUpContext.AFTER_TASK_DETAILS ||
                    homeFollowUpContext == HomeFollowUpContext.AFTER_DAILY_BRIEFING ||
                    homeFollowUpContext == HomeFollowUpContext.AFTER_CONTEXT_SUGGESTION ||
                    homeFollowUpContext == HomeFollowUpContext.QUERY_PAGE ||
                    homeFollowUpContext == HomeFollowUpContext.DELETE_CONFIRMATION) &&
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

                if (LocalConversationIntentClassifier.shouldExecuteLocally(convoResult)) {
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

        if (pendingContextTargetOwnsTurn) {
            Log.d("PENDING_CONTEXT_TARGET", "dedicated semantic interpreter owns turn")
        }

        if (!pendingContextTargetOwnsTurn && handleHomeFollowUp(normalized)) {

            // log
            if (BuildConfig.DEBUG) {
                Log.d("HOME_FOLLOWUP", "handled by old hard-coded follow-up: '$normalized'")
            }
            return
        }
        lifecycleScope.launch {
            try {
                if (handleBoundedQueryCountFollowUp(
                        normalized = normalized,
                        requestToken = requestToken,
                        authorization = localStyleAuthorization
                    )
                ) {
                    return@launch
                }
                Log.d("CONVO_ORCH", "normalizedChars=${normalized.length}")
                val taskContextCapture = readOnlyTaskContextStore.capture()
                val isResultInteraction =
                    homeFollowUpContext == HomeFollowUpContext.AFTER_TASK_SUMMARY ||
                        homeFollowUpContext == HomeFollowUpContext.AFTER_TASK_DETAILS ||
                        homeFollowUpContext == HomeFollowUpContext.AFTER_DAILY_BRIEFING ||
                        homeFollowUpContext == HomeFollowUpContext.AFTER_CONTEXT_SUGGESTION ||
                        homeFollowUpContext == HomeFollowUpContext.QUERY_PAGE ||
                        homeFollowUpContext == HomeFollowUpContext.DELETE_CONFIRMATION ||
                        homeFollowUpContext == HomeFollowUpContext.CONTEXT_ACTION_TARGET_CLARIFICATION ||
                        homeFollowUpContext == HomeFollowUpContext.CONTEXT_ACTION_CHANGE_CLARIFICATION
                val contextFocus = conversationOrchestrator.contextFocusForSnapshot(
                    taskContextCapture.snapshot
                )
                val voiceSettingFocus = voiceSettingConversationContext.focus
                val voiceSettingRoutingContext = VoiceSettingRoutingContext.from(
                    voiceSettingFocus
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
                val pendingTargetResolution =
                    if (pendingContextActionChangeClarification != null) {
                        resolvePendingContextActionChange(
                            normalizedText = normalized,
                            taskContextCapture = taskContextCapture,
                            requestToken = requestToken
                        )
                    } else {
                        resolvePendingContextActionTarget(
                            normalizedText = normalized,
                            taskContextCapture = taskContextCapture,
                            contextFocus = contextFocus,
                            requestToken = requestToken
                        )
                    }
                val contextActionRequestText =
                    pendingTargetResolution.originalActionRequest ?: normalized
                val pendingAuthorityValidatedRef = pendingTargetResolution.authorityValidatedRef
                var conversationDecision = pendingTargetResolution.decision ?: try {
                    conversationOrchestrator.process(
                        normalizedText = normalized,
                        appContextSummary = buildConversationAppContextSummary(),
                        readOnlyTaskContextSnapshot = taskContextCapture.promptText,
                        contextFocus = contextFocus,
                        voiceSettingRoutingContext = voiceSettingRoutingContext,
                        capturedTaskContextSnapshot = taskContextCapture.snapshot
                    )
                } catch (e: ConversationOrchestratorException) {
                    Log.e("CONVO_ORCH", "Conversation Agent failed after schema retry", e)
                    val fallbackReply = "I could not understand that request correctly. Please try again."
                    val failureDecision = ConversationDecision(
                        route = ConversationRoute.UNKNOWN,
                        reply = fallbackReply,
                        listenAgain = true,
                        source = "android_conversation_failure"
                    )
                    val focusedReadFallback = ContextFocusCarryForwardPolicy.resolve(
                        normalizedText = normalized,
                        focus = contextFocus,
                        capturedSnapshot = taskContextCapture.snapshot,
                        isResultInteraction = isResultInteraction
                    )
                    val focusedReadValidation = focusedReadFallback?.let { candidate ->
                        ReadOnlyTaskContextReadValidator.validate(
                            decision = candidate,
                            capturedSnapshot = taskContextCapture.snapshot,
                            currentGeneration = readOnlyTaskContextStore.currentGeneration(),
                            normalizedText = normalized
                        )
                    }
                    if (focusedReadFallback != null && focusedReadValidation?.isValid == true) {
                        Log.d(
                            "HOME_CONTEXT_FOCUS",
                            "CONTEXT_FOCUS_FAILURE_FALLBACK_ACCEPTED " +
                                "ref=${focusedReadFallback.contextRef} " +
                                "generation=${taskContextCapture.snapshot.generation} " +
                                "detail=${focusedReadFallback.contextDetail}"
                        )
                        focusedReadFallback
                    } else {
                        ContextDeleteFailureFallbackPolicy.resolve(
                            normalizedText = normalized,
                            currentDecision = failureDecision,
                            capturedSnapshot = taskContextCapture.snapshot,
                            currentGeneration = readOnlyTaskContextStore.currentGeneration(),
                            currentFocus = contextFocus,
                            agentAttempted = true
                        ) ?: run {
                            conversationOrchestrator.commitFinalDecision(failureDecision)
                            assistantSession.speak(
                                fallbackReply,
                                listenAgain = true
                            )
                            return@launch
                        }
                    }
                }
                if (
                    conversationDecision.route == ConversationRoute.CONTEXT_READ &&
                    !ContextReadDetailCompatibilityPolicy.isCompatible(
                        normalized,
                        conversationDecision.contextDetail
                    )
                ) {
                    Log.d(
                        "HOME_CONTEXT_READ",
                        "detail rejected for explicit request; attempting bounded repair"
                    )
                    conversationDecision = ConversationDecision(
                        route = ConversationRoute.ASK_CLARIFICATION,
                        reply = "",
                        confidence = conversationDecision.confidence,
                        listenAgain = true,
                        source = conversationDecision.source
                    )
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
                        contextFocus = contextFocus,
                        currentGeneration = readOnlyTaskContextStore.currentGeneration()
                    )
                if (contextActionRepairEligible) {
                    Log.d(
                        "HOME_CONTEXT_ACTION_REPAIR",
                        "CONTEXT_ACTION_REPAIR_ATTEMPTED primarySource=${conversationDecision.source}"
                    )
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
                                val repairGrounding = ContextActionReferenceGroundingValidator.validate(
                                    normalizedText = normalized,
                                    decision = repairedDecision,
                                    capturedSnapshot = taskContextCapture.snapshot,
                                    currentFocus = contextFocus
                                )
                                val groundedRepairDecision = repairedDecision.copy(
                                    contextRef = repairGrounding.ref
                                )
                                val repairValidation = if (repairGrounding.isValid) {
                                    ContextActionDecisionValidator.validate(
                                        decision = groundedRepairDecision,
                                        capturedSnapshot = taskContextCapture.snapshot,
                                        currentGeneration = readOnlyTaskContextStore.currentGeneration()
                                    )
                                } else {
                                    null
                                }
                                if (repairValidation?.isValid == true) {
                                    conversationDecision = groundedRepairDecision
                                    Log.d(
                                        "HOME_CONTEXT_ACTION_REPAIR",
                                        "CONTEXT_ACTION_REPAIR_ACCEPTED " +
                                            "grounding=${repairGrounding.result}"
                                    )
                                } else {
                                    if (!repairGrounding.isValid) {
                                        beginContextActionTargetClarification(
                                            action = repairedDecision.contextAction,
                                            capture = taskContextCapture,
                                            originalNormalizedRequest = normalized
                                        )
                                        conversationDecision = ConversationDecision(
                                            route = ConversationRoute.ASK_CLARIFICATION,
                                            reply = contextActionTargetQuestion(
                                                repairedDecision.contextAction
                                            ),
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

                val focusEllipsis = ContextFocusActionEllipsisPolicy.resolve(
                    normalizedText = normalized,
                    currentDecision = conversationDecision,
                    capturedSnapshot = taskContextCapture.snapshot,
                    currentGeneration = readOnlyTaskContextStore.currentGeneration(),
                    currentFocus = contextFocus
                )
                if (focusEllipsis != null) {
                    val ellipsisValidation = ContextActionDecisionValidator.validate(
                        decision = focusEllipsis,
                        capturedSnapshot = taskContextCapture.snapshot,
                        currentGeneration = readOnlyTaskContextStore.currentGeneration()
                    )
                    val ellipsisGrounding = if (ellipsisValidation.isValid) {
                        ContextActionReferenceGroundingValidator.validate(
                            normalizedText = normalized,
                            decision = focusEllipsis,
                            capturedSnapshot = taskContextCapture.snapshot,
                            currentFocus = contextFocus
                        )
                    } else {
                        null
                    }
                    if (ellipsisValidation.isValid && ellipsisGrounding?.isValid == true) {
                        conversationDecision = focusEllipsis
                        Log.d(
                            "CONTEXT_ELLIPSIS",
                            "action=${focusEllipsis.contextAction.name} ref=${focusEllipsis.contextRef} result=ACCEPTED"
                        )
                    } else {
                        Log.d(
                            "CONTEXT_ELLIPSIS",
                            "action=${focusEllipsis.contextAction.name} ref=${focusEllipsis.contextRef} result=REJECTED"
                        )
                    }
                }

                val contextDeleteFallback = ContextDeleteFailureFallbackPolicy.resolve(
                    normalizedText = normalized,
                    currentDecision = conversationDecision,
                    capturedSnapshot = taskContextCapture.snapshot,
                    currentGeneration = readOnlyTaskContextStore.currentGeneration(),
                    currentFocus = contextFocus,
                    agentAttempted = true
                )
                if (contextDeleteFallback != null) {
                    val fallbackValidation = ContextActionDecisionValidator.validate(
                        decision = contextDeleteFallback,
                        capturedSnapshot = taskContextCapture.snapshot,
                        currentGeneration = readOnlyTaskContextStore.currentGeneration()
                    )
                    val fallbackGrounding = if (fallbackValidation.isValid) {
                        ContextActionReferenceGroundingValidator.validate(
                            normalizedText = normalized,
                            decision = contextDeleteFallback,
                            capturedSnapshot = taskContextCapture.snapshot,
                            currentFocus = contextFocus
                        )
                    } else {
                        null
                    }
                    if (fallbackValidation.isValid && fallbackGrounding?.isValid == true) {
                        conversationDecision = contextDeleteFallback
                        Log.d(
                            "HOME_CONTEXT_DELETE_FALLBACK",
                            "source=${ContextDeleteFailureFallbackPolicy.SOURCE} " +
                                "generation=${taskContextCapture.snapshot.generation} accepted=true"
                        )
                    } else {
                        Log.d(
                            "HOME_CONTEXT_DELETE_FALLBACK",
                            "source=${ContextDeleteFailureFallbackPolicy.SOURCE} " +
                                "generation=${taskContextCapture.snapshot.generation} accepted=false"
                        )
                    }
                }

                if (conversationDecision.route == ConversationRoute.END_SESSION &&
                    ConversationEndSessionSafetyPolicy.shouldRejectModelEndSession(normalized)
                ) {
                    conversationDecision = ConversationDecision(
                        route = ConversationRoute.ASK_CLARIFICATION,
                        reply = ConversationEndSessionSafetyPolicy.CLARIFICATION,
                        confidence = 1.0,
                        listenAgain = true,
                        source = "android_end_session_semantic_guard"
                    )
                    Log.d("HOME_END_SESSION_GUARD", "questionLike=true rejected=true")
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
                            currentGeneration = readOnlyTaskContextStore.currentGeneration(),
                            normalizedText = normalized
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

                val recoveredSettingReadDecision =
                    VoiceSettingsReadRecoveryPolicy.recoverDecision(normalized)
                if (recoveredSettingReadDecision != null &&
                    (conversationDecision.route != ConversationRoute.SETTINGS_READ ||
                        conversationDecision.settingTarget != recoveredSettingReadDecision.settingTarget)
                ) {
                    Log.d(
                        "VOICE_SETTINGS_READ_RECOVERY",
                        "modelRoute=${conversationDecision.route.name} " +
                            "modelAction=${conversationDecision.settingAction.name} " +
                            "recoveredTarget=${recoveredSettingReadDecision.settingTarget.name}"
                    )
                    conversationDecision = recoveredSettingReadDecision
                }

                val recoveredSettingAction = VoiceSettingContextualActionResolver.resolve(
                    normalizedUtterance = normalized,
                    focus = voiceSettingFocus
                )
                if (recoveredSettingAction != null &&
                    (conversationDecision.route != ConversationRoute.SETTINGS_ACTION ||
                        conversationDecision.settingAction != recoveredSettingAction)
                ) {
                    Log.d(
                        "VOICE_SETTINGS_CONTEXT_RECOVERY",
                        "modelAction=${conversationDecision.settingAction.name} " +
                            "recoveredAction=${recoveredSettingAction.name} " +
                            "focus=${voiceSettingFocus?.target?.name ?: "NONE"}"
                    )
                    conversationDecision = ConversationDecision(
                        route = ConversationRoute.SETTINGS_ACTION,
                        settingAction = recoveredSettingAction,
                        confidence = 1.0,
                        listenAgain = true,
                        source = "android_voice_settings_context_recovery"
                    )
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
                if (conversationDecision.route !in setOf(
                        ConversationRoute.SETTINGS_ACTION,
                        ConversationRoute.SETTINGS_READ,
                        ConversationRoute.DIRECT_REPLY,
                        ConversationRoute.ASK_CLARIFICATION
                    )
                ) {
                    voiceSettingConversationContext.clear()
                }
                when (conversationDecision.route) {
                    ConversationRoute.APP_NAVIGATION -> {
                        if (!isAssistantRequestCurrent(requestToken)) return@launch
                        val navigation = when (conversationDecision.navigationTarget) {
                            ConversationNavigationTarget.CREATE_TASK ->
                                "Opening task creation." to CreateTaskActivity::class.java
                            ConversationNavigationTarget.TODAY_TASKS ->
                                "Opening today's tasks." to TodayTasksActivity::class.java
                            ConversationNavigationTarget.SCHEDULED_TASKS ->
                                "Opening scheduled tasks." to MainActivity::class.java
                            ConversationNavigationTarget.SETTINGS ->
                                "Opening settings." to SettingsActivity::class.java
                            ConversationNavigationTarget.NONE -> null
                        }
                        if (navigation == null) {
                            val clarification = ConversationDecision(
                                route = ConversationRoute.ASK_CLARIFICATION,
                                reply = "Which app screen would you like me to open?",
                                confidence = 1.0,
                                listenAgain = true,
                                source = "android_navigation_target_validation"
                            )
                            Log.d("HOME_NAVIGATION", "target=NONE result=REJECTED")
                            conversationOrchestrator.commitFinalDecision(clarification)
                            assistantSession.speak(clarification.reply, listenAgain = true)
                            return@launch
                        }
                        conversationOrchestrator.commitFinalDecision(conversationDecision)
                        Log.d(
                            "HOME_NAVIGATION",
                            "target=${conversationDecision.navigationTarget.name} result=OPENING"
                        )
                        speakThenOpen(navigation.first) {
                            val navigationIntent = Intent(this@HomeActivity, navigation.second).apply {
                                if (conversationDecision.navigationTarget == ConversationNavigationTarget.CREATE_TASK) {
                                    putExtra(EXTRA_CREATE_TASK_ASSISTANT_HANDOFF, true)
                                }
                            }
                            startActivity(navigationIntent)
                        }
                        return@launch
                    }
                    ConversationRoute.SMART_ROUTINE_BUILDER -> {
                        if (!isAssistantRequestCurrent(requestToken)) return@launch
                        conversationOrchestrator.commitFinalDecision(conversationDecision)
                        handleSmartRoutineBuilder(normalized, requestToken)
                        return@launch
                    }
                    ConversationRoute.SAVED_ROUTINE_ACTION -> {
                        if (!isAssistantRequestCurrent(requestToken)) return@launch
                        conversationOrchestrator.commitFinalDecision(conversationDecision)
                        handleSavedRoutineAction(normalized, requestToken)
                        return@launch
                    }
                    ConversationRoute.DAILY_BRIEFING -> {
                        executeDailyBriefing(
                            requestToken = requestToken,
                            decision = conversationDecision
                        )
                        return@launch
                    }
                    ConversationRoute.CONTEXT_AWARE_SUGGESTION -> {
                        executeContextSuggestion(
                            normalizedRequest = conversationDecision.taskText,
                            requestToken = requestToken,
                            routingDecision = conversationDecision
                        )
                        return@launch
                    }
                    ConversationRoute.CONTEXT_READ -> {
                        val validation = ReadOnlyTaskContextReadValidator.validate(
                            decision = conversationDecision,
                            capturedSnapshot = taskContextCapture.snapshot,
                            currentGeneration = readOnlyTaskContextStore.currentGeneration(),
                            normalizedText = normalized
                        )
                        executeContextRead(
                            decision = conversationDecision,
                            taskContextCapture = taskContextCapture,
                            validation = validation
                        )
                        return@launch
                    }
                    ConversationRoute.CONTEXT_ACTION -> {
                        val pendingTargetAuthorityApplies =
                            pendingAuthorityValidatedRef != null &&
                            conversationDecision.source in setOf(
                                "conversation_agent_pending_context_target",
                                "android_pending_context_change"
                            ) &&
                            conversationDecision.contextRef.equals(
                                pendingAuthorityValidatedRef,
                                ignoreCase = true
                            )
                        val grounding = if (!pendingTargetAuthorityApplies) {
                            ContextActionReferenceGroundingValidator.validate(
                                normalizedText = normalized,
                                decision = conversationDecision,
                                capturedSnapshot = taskContextCapture.snapshot,
                                currentFocus = contextFocus
                            )
                        } else {
                            null
                        }
                        val groundedRef = pendingAuthorityValidatedRef
                            .takeIf { pendingTargetAuthorityApplies } ?: grounding?.ref.orEmpty()
                        Log.d(
                            "HOME_CONTEXT_ACTION_GROUNDING",
                            "ref=${conversationDecision.contextRef} result=" +
                                if (pendingTargetAuthorityApplies) {
                                    if (conversationDecision.source == "android_pending_context_change") {
                                        "VALID_PENDING_CHANGE"
                                    } else {
                                        "VALID_PENDING_TARGET"
                                    }
                                } else {
                                    grounding?.result
                                }
                        )
                        if (!pendingTargetAuthorityApplies && grounding?.isValid != true) {
                            beginContextActionTargetClarification(
                                action = conversationDecision.contextAction,
                                capture = taskContextCapture,
                                originalNormalizedRequest = normalized
                            )
                            rejectContextAction(
                                contextActionTargetQuestion(conversationDecision.contextAction),
                                "android_context_action_reference_grounding"
                            )
                            return@launch
                        }

                        val groundedDecision = conversationDecision.copy(contextRef = groundedRef)
                        val validation = ContextActionDecisionValidator.validate(
                            decision = groundedDecision,
                            capturedSnapshot = taskContextCapture.snapshot,
                            currentGeneration = readOnlyTaskContextStore.currentGeneration()
                        )
                        Log.d(
                            "HOME_CONTEXT_ACTION",
                            "route=${groundedDecision.route} " +
                                "modelRef=${conversationDecision.contextRef} " +
                                "groundedRef=$groundedRef " +
                                "action=${groundedDecision.contextAction} " +
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
                        conversationDecision = groundedDecision

                        val capturedGeneration = taskContextCapture.snapshot.generation
                        val privateTaskId = readOnlyTaskContextStore.resolveRef(
                            ref = groundedRef,
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
                        val initiallyEligible = isEligibleContextActionTarget(
                            initiallyFetchedTask,
                            validation.action
                        ) && initiallyFetchedTask != null &&
                            readOnlyTaskContextStore.matchesResolvedTask(
                                ref = groundedRef,
                                expectedGeneration = capturedGeneration,
                                task = initiallyFetchedTask
                            )
                        Log.d(
                            "CONTEXT_ACTION_TARGET_RESOLVED",
                            "generation=$capturedGeneration eligible=$initiallyEligible"
                        )
                        if (!initiallyEligible) {
                            rejectUnavailableContextAction()
                            return@launch
                        }

                        if (validation.action == ConversationContextAction.DELETE) {
                            if (!isAssistantRequestCurrent(requestToken)) return@launch
                            conversationOrchestrator.commitFinalDecision(conversationDecision)
                            askDeleteConfirmation(requireNotNull(initiallyFetchedTask))
                            return@launch
                        }

                        if (validation.action == ConversationContextAction.MARK_DONE ||
                            validation.action == ConversationContextAction.MARK_UNDONE
                        ) {
                            if (!isAssistantRequestCurrent(requestToken) ||
                                readOnlyTaskContextStore.currentGeneration() != capturedGeneration
                            ) {
                                rejectUnavailableContextAction()
                                return@launch
                            }
                            val completionTaskId = readOnlyTaskContextStore.resolveRef(
                                ref = groundedRef,
                                expectedGeneration = capturedGeneration
                            )
                            if (completionTaskId == null || completionTaskId != privateTaskId) {
                                rejectUnavailableContextAction()
                                return@launch
                            }
                            val completionTask = withContext(Dispatchers.IO) {
                                taskDao.getById(completionTaskId)
                            }
                            if (completionTask == null ||
                                !isAssistantRequestCurrent(requestToken) ||
                                !isEligibleContextActionTarget(completionTask, validation.action) ||
                                !sameContextActionTaskSnapshot(initiallyFetchedTask, completionTask) ||
                                !readOnlyTaskContextStore.matchesResolvedTask(
                                    ref = groundedRef,
                                    expectedGeneration = capturedGeneration,
                                    task = completionTask
                                )
                            ) {
                                rejectUnavailableContextAction()
                                return@launch
                            }
                            conversationOrchestrator.commitFinalDecision(conversationDecision)
                            val completionGrounding = if (pendingTargetAuthorityApplies) {
                                "VALID_PENDING_TARGET"
                            } else {
                                grounding?.result?.name.orEmpty()
                            }
                            if (taskContextCapture.snapshot.scope == TaskContextScope.SUBTASK_LIST) {
                                executeContextSubtaskCompletion(
                                    requestToken = requestToken,
                                    task = requireNotNull(completionTask),
                                    action = validation.action,
                                    grounding = completionGrounding,
                                    targetRef = groundedRef,
                                    capturedGeneration = capturedGeneration
                                )
                            } else {
                                speakObservation(
                                    executeDeterministicTaskCompletion(
                                        task = requireNotNull(completionTask),
                                        action = validation.action,
                                        grounding = completionGrounding,
                                        targetRef = groundedRef
                                    )
                                )
                            }
                            return@launch
                        }

                        val extractedChange = try {
                            agentOrchestrator.processContextAction(
                                normalizedText = contextActionRequestText,
                                expectedAction = validation.action
                            )
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (_: TaskAgentProcessingException) {
                            if (!isRelativeTemporalRequestCurrent(
                                    requestToken,
                                    "AFTER_EXTRACTION"
                                )
                            ) return@launch
                            val clarification = if (
                                validation.action == ConversationContextAction.RESCHEDULE
                            ) {
                                RelativeTemporalSpeechRenderer.semanticClarification()
                            } else {
                                "Please repeat the requested change."
                            }
                            beginOrRetainContextActionChangeClarification(
                                action = validation.action,
                                groundedRef = groundedRef,
                                capturedGeneration = capturedGeneration,
                                authoritativeTaskSnapshot = requireNotNull(initiallyFetchedTask),
                                originalNormalizedRequest = contextActionRequestText
                            )
                            rejectContextAction(
                                clarification,
                                "android_context_action_extraction"
                            )
                            return@launch
                        }
                        if (!isRelativeTemporalRequestCurrent(
                                requestToken,
                                "AFTER_EXTRACTION"
                            )
                        ) return@launch
                        val proposal = extractedChange.temporalProposal
                        val hasDateChange = proposal != null &&
                            proposal.dateOperation != RelativeTemporalOperation.KEEP
                        val hasTimeChange = proposal != null &&
                            proposal.timeOperation != RelativeTemporalOperation.KEEP
                        Log.d(
                            "HOME_CONTEXT_RESCHEDULE_EXTRACTION",
                            "hasDateChange=$hasDateChange " +
                                "hasTimeChange=$hasTimeChange " +
                                "clarificationRequired=false"
                        )

                        if (readOnlyTaskContextStore.currentGeneration() != capturedGeneration) {
                            rejectUnavailableContextAction()
                            return@launch
                        }
                        val reResolvedTaskId = readOnlyTaskContextStore.resolveRef(
                            ref = groundedRef,
                            expectedGeneration = capturedGeneration
                        )
                        if (reResolvedTaskId == null || reResolvedTaskId != privateTaskId) {
                            rejectUnavailableContextAction()
                            return@launch
                        }
                        val calculationTask = withContext(Dispatchers.IO) {
                            taskDao.getById(reResolvedTaskId)
                        }
                        if (!isRelativeTemporalRequestCurrent(
                                requestToken,
                                "AFTER_CALCULATION"
                            )
                        ) return@launch
                        if (!isEligibleContextActionTarget(calculationTask, validation.action) ||
                            !sameContextActionTaskSnapshot(initiallyFetchedTask, calculationTask)
                        ) {
                            rejectUnavailableContextAction()
                            return@launch
                        }

                        val calculation = if (
                            validation.action == ConversationContextAction.RESCHEDULE
                        ) {
                            RelativeTemporalChangeCalculator().calculate(
                                authoritativeOriginal = ExactTemporalSchedule(
                                    date = calculationTask?.dueDate,
                                    time = calculationTask?.dueTime
                                ),
                                currentProposal = null,
                                proposal = requireNotNull(proposal),
                                now = Calendar.getInstance()
                            )
                        } else {
                            null
                        }
                        when (calculation) {
                            is RelativeTemporalCalculationResult.Failure -> {
                                if (!isRelativeTemporalRequestCurrent(
                                        requestToken,
                                        "AFTER_CALCULATION"
                                    )
                                ) return@launch
                                Log.d(
                                    "RELATIVE_TEMPORAL_CALCULATION",
                                    "result=REJECTED crossedDateBoundary=false " +
                                        "source=${calculation.source}"
                                )
                                beginOrRetainContextActionChangeClarification(
                                    action = validation.action,
                                    groundedRef = groundedRef,
                                    capturedGeneration = capturedGeneration,
                                    authoritativeTaskSnapshot = requireNotNull(calculationTask),
                                    originalNormalizedRequest = contextActionRequestText
                                )
                                rejectContextAction(
                                    RelativeTemporalSpeechRenderer.calculationClarification(
                                        calculation.reason
                                    ),
                                    "android_relative_temporal_calculation"
                                )
                                return@launch
                            }
                            is RelativeTemporalCalculationResult.PastSchedule -> {
                                if (!isRelativeTemporalRequestCurrent(
                                        requestToken,
                                        "AFTER_CALCULATION"
                                    )
                                ) return@launch
                                Log.d(
                                    "RELATIVE_TEMPORAL_CALCULATION",
                                    "result=PAST crossedDateBoundary=${calculation.crossedDateBoundary} " +
                                        "source=${calculation.source}"
                                )
                                beginOrRetainContextActionChangeClarification(
                                    action = validation.action,
                                    groundedRef = groundedRef,
                                    capturedGeneration = capturedGeneration,
                                    authoritativeTaskSnapshot = requireNotNull(calculationTask),
                                    originalNormalizedRequest = contextActionRequestText
                                )
                                rejectContextAction(
                                    RelativeTemporalSpeechRenderer.pastSchedule(calculation.schedule),
                                    "android_relative_temporal_past"
                                )
                                return@launch
                            }
                            is RelativeTemporalCalculationResult.Success -> {
                                if (!isRelativeTemporalRequestCurrent(
                                        requestToken,
                                        "AFTER_CALCULATION"
                                    )
                                ) return@launch
                                Log.d(
                                    "RELATIVE_TEMPORAL_CALCULATION",
                                    "result=SUCCESS crossedDateBoundary=${calculation.crossedDateBoundary} " +
                                        "source=${calculation.source}"
                                )
                            }
                            null -> Unit
                        }

                        if (readOnlyTaskContextStore.currentGeneration() != capturedGeneration) {
                            rejectUnavailableContextAction()
                            return@launch
                        }
                        val openingTask = withContext(Dispatchers.IO) {
                            taskDao.getById(reResolvedTaskId)
                        }
                        if (!isRelativeTemporalRequestCurrent(
                                requestToken,
                                "BEFORE_OPEN"
                            )
                        ) return@launch
                        if (!isEligibleContextActionTarget(openingTask, validation.action) ||
                            !sameContextActionTaskSnapshot(calculationTask, openingTask)
                        ) {
                            rejectUnavailableContextAction()
                            return@launch
                        }

                        if (!isRelativeTemporalRequestCurrent(
                                requestToken,
                                "BEFORE_OPEN"
                            )
                        ) return@launch
                        pendingContextActionChangeClarification?.let { pending ->
                            Log.d(
                                "CONTEXT_ACTION_CHANGE_CLARIFICATION",
                                "action=${pending.action.name} " +
                                    "ref=${pending.authorityValidatedRef} " +
                                    "generation=${pending.capturedGeneration} state=RESOLVED"
                            )
                        }
                        clearPendingContextActionChangeClarification(restoreContext = true)
                        conversationOrchestrator.commitFinalDecision(conversationDecision)
                        if (!isRelativeTemporalRequestCurrent(
                                requestToken,
                                "BEFORE_OPEN"
                            )
                        ) return@launch
                        openContextActionEditScreen(
                            requestToken = requestToken,
                            task = requireNotNull(openingTask),
                            action = validation.action,
                            extractedChange = extractedChange,
                            calculatedTemporal =
                                calculation as? RelativeTemporalCalculationResult.Success
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
                    ConversationRoute.SETTINGS_READ -> {
                        if (!VoiceSettingsReadDecisionValidator.isValid(conversationDecision)) {
                            val clarification = "Which setting would you like me to check?"
                            conversationOrchestrator.commitFinalDecision(
                                ConversationDecision(
                                    route = ConversationRoute.ASK_CLARIFICATION,
                                    reply = clarification,
                                    listenAgain = true,
                                    source = "android_voice_settings_read_validation"
                                )
                            )
                            assistantSession.speak(clarification, listenAgain = true)
                            return@launch
                        }
                        voiceSettingConversationContext.clearPending()
                        conversationDecision.settingTarget.voiceSettingTarget()?.let(
                            voiceSettingConversationContext::focus
                        )
                        conversationOrchestrator.commitFinalDecision(conversationDecision)
                        val readResult = voiceSettingsStatusReader.read(
                            conversationDecision.settingTarget
                        )
                        Log.d(
                            "VOICE_SETTINGS_READ",
                            "target=${readResult.target.name} status=${readResult.status.name}"
                        )
                        val speech = if (readResult.status == VoiceSettingReadStatus.READ) {
                            readResult.speech
                        } else {
                            "Which setting would you like me to check?"
                        }
                        assistantSession.speak(speech, listenAgain = true)
                        return@launch
                    }
                    ConversationRoute.SETTINGS_ACTION -> {
                        if (!VoiceSettingsDecisionValidator.isValid(conversationDecision)) {
                            voiceSettingConversationContext.clear()
                            val clarification = "I could not change that setting safely. Please try again."
                            conversationOrchestrator.commitFinalDecision(
                                ConversationDecision(
                                    route = ConversationRoute.ASK_CLARIFICATION,
                                    reply = clarification,
                                    listenAgain = true,
                                    source = "android_voice_settings_validation"
                                )
                            )
                            assistantSession.speak(clarification, listenAgain = true)
                            return@launch
                        }
                        val safetyResult = VoiceSettingsMutationSafetyPolicy.evaluate(
                            normalizedUtterance = normalized,
                            proposedAction = conversationDecision.settingAction,
                            currentFocus = voiceSettingConversationContext.focus
                        )
                        logVoiceSettingsSafety(
                            action = conversationDecision.settingAction,
                            result = safetyResult
                        )
                        when (safetyResult.disposition) {
                            VoiceSettingsSafetyDisposition.ALLOW -> {
                                voiceSettingConversationContext.clearPending()
                                safetyResult.authorizedAction.voiceSettingTarget()?.let(
                                    voiceSettingConversationContext::focus
                                )
                                conversationOrchestrator.commitFinalDecision(conversationDecision)
                                executeAllowedVoiceSetting(safetyResult.authorizedAction)
                            }
                            VoiceSettingsSafetyDisposition.GUIDANCE_ONLY -> {
                                voiceSettingConversationContext.clearPending()
                                safetyResult.groundedTarget?.let(
                                    voiceSettingConversationContext::focus
                                )
                                deliverVoiceSettingsSafetyResponse(
                                    safetyResult,
                                    ConversationRoute.DIRECT_REPLY
                                )
                            }
                            VoiceSettingsSafetyDisposition.CLARIFY_HAPTIC_TARGET -> {
                                voiceSettingConversationContext.clear()
                                voiceSettingConversationContext.retain(
                                    safetyResult.pendingClarification
                                )
                                deliverVoiceSettingsSafetyResponse(
                                    safetyResult,
                                    ConversationRoute.ASK_CLARIFICATION
                                )
                            }
                            VoiceSettingsSafetyDisposition.CLARIFY_SETTING_TARGET -> {
                                if (safetyResult.groundedTarget != null) {
                                    voiceSettingConversationContext.focus(
                                        safetyResult.groundedTarget
                                    )
                                } else {
                                    voiceSettingConversationContext.clear()
                                }
                                voiceSettingConversationContext.retain(
                                    safetyResult.pendingClarification
                                )
                                deliverVoiceSettingsSafetyResponse(
                                    safetyResult,
                                    ConversationRoute.ASK_CLARIFICATION
                                )
                            }
                        }
                        return@launch
                    }
                    ConversationRoute.DIRECT_REPLY -> {
                        Log.d("CONVO_ORCH", "handled directly as DIRECT_REPLY")
                        VoiceSettingsMutationSafetyPolicy.groundedTarget(normalized)?.let {
                            voiceSettingConversationContext.clearPending()
                            voiceSettingConversationContext.focus(it)
                        } ?: voiceSettingConversationContext.clear()
                        conversationOrchestrator.commitFinalDecision(conversationDecision)
                        assistantSession.speak(
                            conversationDecision.reply,
                            listenAgain = conversationDecision.listenAgain
                        )
                        return@launch
                    }
                    ConversationRoute.ASK_CLARIFICATION -> {
                        Log.d("CONVO_ORCH", "handled directly as ASK_CLARIFICATION")
                        val settingsClarification =
                            VoiceSettingsMutationSafetyPolicy.inferPendingClarification(
                                normalizedUtterance = normalized,
                                currentFocus = voiceSettingConversationContext.focus
                            )
                        if (settingsClarification != null) {
                            voiceSettingConversationContext.clear()
                            voiceSettingConversationContext.retain(
                                settingsClarification.pendingClarification
                            )
                            logVoiceSettingsSafety(
                                ConversationSettingAction.NONE,
                                settingsClarification
                            )
                            deliverVoiceSettingsSafetyResponse(
                                settingsClarification,
                                ConversationRoute.ASK_CLARIFICATION
                            )
                            return@launch
                        }
                        VoiceSettingsMutationSafetyPolicy.groundedTarget(normalized)?.let {
                            voiceSettingConversationContext.clearPending()
                            voiceSettingConversationContext.focus(it)
                        } ?: voiceSettingConversationContext.clear()
                        conversationOrchestrator.commitFinalDecision(conversationDecision)
                        assistantSession.speak(conversationDecision.reply, listenAgain = true)
                        return@launch
                    }
                    ConversationRoute.END_SESSION -> {
                        Log.d("CONVO_ORCH", "handled directly as END_SESSION")
                        conversationOrchestrator.commitFinalDecision(conversationDecision)
                        logQueryPageEndIfActive("USER_STOPPED")
                        invalidateAssistantRequest(
                            AssistantRequestInvalidationReason.CONVERSATION_ENDED
                        )
                        clearConversationSessionContext()
                        routineDraftController.clear()
                        savedRoutineInteractionController.clear()
                        homeFollowUpContext = HomeFollowUpContext.NONE
                        assistantSession.endConversation(conversationDecision.reply)
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
                if (!isAssistantRequestCurrent(requestToken)) return@launch
                val boundedPresentation =
                    queryPresentationSemanticOrchestrator.resolveForValidatedIntent(
                        normalizedUserText = normalized,
                        validatedTaskAgentIntent = aiResult.intent
                    )
                if (!isAssistantRequestCurrent(requestToken)) return@launch
                val presentationResolution = TaskQueryPresentationReconciler.reconcile(
                    taskAgentIntent = aiResult.intent,
                    conversationHint = conversationDecision.queryPresentationHint,
                    taskAgentValue = aiResult.queryPresentation,
                    boundedSemantic = boundedPresentation.presentation
                )
                if (
                    aiResult.intent == AiIntent.QUERY_TASK.name ||
                    conversationDecision.queryPresentationHint != TaskQueryPresentation.NONE
                ) {
                    Log.d(
                        "HOME_QUERY_PRESENTATION",
                        "boundedSemantic=${boundedPresentation.presentation} " +
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
                                " target=${aiResult.targetTaskTitle}, queryDetail=${aiResult.queryDetail}," +
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
                                putExtra(EXTRA_CREATE_TASK_ASSISTANT_HANDOFF, true)
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
                        Log.d("HOME_ACTION", "QUERY_TASK detail=${aiResult.queryDetail}")

                        if (aiResult.needsClarification) {
                            if (aiResult.queryDetail != TaskQueryDetail.NONE) {
                                assistantSession.speak("Please say the task name and which schedule detail you need.", listenAgain = true)
                                return@launch
                            }
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

                        if (!aiResult.targetTaskTitle.isNullOrBlank() && aiResult.queryDetail != TaskQueryDetail.NONE) {
                            handleNamedTaskQuery(
                                targetTitle = aiResult.targetTaskTitle,
                                detail = aiResult.queryDetail,
                                targetDateText = aiResult.targetDateText,
                                targetTimeText = aiResult.targetTimeText,
                                requestToken = requestToken
                            )
                        } else {
                            handleQueryTask(
                                normalized = normalized,
                                agentDateText = aiResult.targetDateText ?: aiResult.dateText,
                                agentTimeText = aiResult.targetTimeText ?: aiResult.timeText,
                                presentation = presentationResolution.effective,
                                requestToken = requestToken,
                                authorization = routedStyleAuthorization
                            )
                        }
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
                                    speakObservation(
                                        executeDeterministicTaskCompletion(
                                            task = matchedTask,
                                            action = ConversationContextAction.MARK_DONE,
                                            grounding = "NAMED_TASK"
                                        )
                                    )
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
                                    speakObservation(
                                        executeDeterministicTaskCompletion(
                                            task = matchedTask,
                                            action = ConversationContextAction.MARK_UNDONE,
                                            grounding = "NAMED_TASK"
                                        )
                                    )
                                }

                                else -> {
                                    speakObservation(ExecutionObservation(ExecutionOperation.MARK_UNDONE, ExecutionOutcome.NOT_FOUND, listenAgain = true, fallbackSpeech = responseManager.taskMatchNotFound()))
                                }
                            }
                        }
                    }

                    // for breakdown tasks
                    AiIntent.BREAKDOWN_TASK.name -> {
                        Log.d("HOME_ACTION", "BREAKDOWN_TASK -> validate and resolve target")
                        beginBreakdownTargetResolution(
                            title = aiResult.taskTitle ?: normalized,
                            plan = aiResult.plan,
                            originalRequest = normalized,
                            dateText = aiResult.newDateText ?: aiResult.dateText,
                            timeText = aiResult.newTimeText ?: aiResult.timeText,
                            targetPreference = aiResult.breakdownTargetPreference,
                            requestToken = requestToken
                        )
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
            conversationOrchestrator.respondToObservation(
                observation = observation,
                tone = responseVerbalizationTone(),
                verbosity = responseVerbalizationVerbosity()
            )
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
        conversationOrchestrator.recordDeliveredObservationResponse(observation, response)
    }

    private fun deliverObservationResponse(
        observation: ExecutionObservation,
        response: ConversationResponse,
        afterSpeech: (() -> Unit)? = null
    ) {
        if (afterSpeech != null) {
            assistantSession.speakThenRun(response.speech) { afterSpeech() }
        } else if (observation.listenAgain) {
            assistantSession.speakThenListenAgain(response.speech)
        } else {
            assistantSession.speakThenStop(
                response.speech,
                dismissPanel = true
            )
        }
    }

    private suspend fun speakObservation(observation: ExecutionObservation): Boolean {
        val deliveryState = captureResponseVerbalizationDeliveryState()
        if (!isResponseVerbalizationDeliveryCurrent(observation, deliveryState)) return false
        val response = renderObservationResponse(observation)
        var delivered = false
        val staleReason = ResponseVerbalizationDeliveryGuard.runIfCurrent(
            captured = deliveryState,
            current = captureResponseVerbalizationDeliveryState()
        ) {
            recordObservationResponse(observation, response)
            deliverObservationResponse(observation, response)
            delivered = true
        }
        logStaleResponseVerbalization(observation, response, staleReason)
        return delivered
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
            updateFocusAfterAuthoritativeQueryDelivery(
                observation = observation,
                kind = kind,
                deliveredGeneration = contextGeneration
            )
        }
        if (staleReason != null) {
            Log.d("SAFE_OBSERVATION_STYLE_STALE", "reason=$staleReason")
        }
    }

    private fun updateFocusAfterAuthoritativeQueryDelivery(
        observation: ExecutionObservation,
        kind: RepeatableSpeechKind,
        deliveredGeneration: Long?
    ) {
        if (kind == RepeatableSpeechKind.QUERY_COUNT) {
            Log.d("CONTEXT_FOCUS_NOT_ESTABLISHED", "reason=COUNT_ONLY")
            return
        }
        if (kind != RepeatableSpeechKind.QUERY_PAGE) return
        val presentation = observation.queryPage?.presentation
            ?: TaskQueryPresentationLevel.COUNT_ONLY
        val snapshot = readOnlyTaskContextStore.snapshot()
        val evaluation = PresentedQueryFocusPolicy.evaluate(
            presentation = presentation,
            snapshot = snapshot,
            deliveredGeneration = deliveredGeneration,
            currentGeneration = readOnlyTaskContextStore.currentGeneration()
        )
        if (evaluation.shouldEstablish) {
            val item = requireNotNull(evaluation.item)
            conversationOrchestrator.setAuthoritativeContextFocus(
                item = item,
                selectedRef = item.ref,
                capturedGeneration = snapshot.generation
            )
            Log.d(
                "CONTEXT_FOCUS_ESTABLISHED",
                "source=SINGLE_PRESENTED_QUERY_RESULT ref=${item.ref} generation=${snapshot.generation}"
            )
        } else {
            val reason = when (evaluation.result) {
                PresentedQueryFocusResult.COUNT_ONLY -> "COUNT_ONLY"
                PresentedQueryFocusResult.MULTIPLE_ITEMS -> "MULTIPLE_ITEMS"
                PresentedQueryFocusResult.NO_ITEMS -> "NO_ITEMS"
                PresentedQueryFocusResult.STALE -> "STALE"
                PresentedQueryFocusResult.ESTABLISH -> "STALE"
            }
            Log.d("CONTEXT_FOCUS_NOT_ESTABLISHED", "reason=$reason")
        }
    }

    private suspend fun speakObservationThenRun(observation: ExecutionObservation, action: () -> Unit) {
        val deliveryState = captureResponseVerbalizationDeliveryState()
        if (!isResponseVerbalizationDeliveryCurrent(observation, deliveryState)) return
        val response = renderObservationResponse(observation)
        val staleReason = ResponseVerbalizationDeliveryGuard.runIfCurrent(
            captured = deliveryState,
            current = captureResponseVerbalizationDeliveryState()
        ) {
            recordObservationResponse(observation, response)
            deliverObservationResponse(observation, response, action)
        }
        logStaleResponseVerbalization(observation, response, staleReason)
    }

    private fun captureResponseVerbalizationDeliveryState() =
        ResponseVerbalizationDeliveryState(
            requestGeneration = assistantRequestGeneration,
            assistantRequestActive = assistantRequestActive,
            activityActive = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                !isFinishing && !isDestroyed
        )

    private fun isResponseVerbalizationDeliveryCurrent(
        observation: ExecutionObservation,
        captured: ResponseVerbalizationDeliveryState
    ): Boolean {
        val reason = ResponseVerbalizationDeliveryGuard.staleReason(
            captured,
            captureResponseVerbalizationDeliveryState()
        )
        if (reason != null) {
            Log.d(
                "RESPONSE_VERBALIZATION_STALE",
                "operation=${observation.operation} outcome=${observation.outcome} " +
                    "reason=$reason source=before_request"
            )
            return false
        }
        return true
    }

    private fun logStaleResponseVerbalization(
        observation: ExecutionObservation,
        response: ConversationResponse,
        reason: ResponseVerbalizationStaleReason?
    ) {
        if (reason != null) {
            Log.d(
                "RESPONSE_VERBALIZATION_STALE",
                "operation=${observation.operation} outcome=${observation.outcome} " +
                    "reason=$reason source=${response.source}"
            )
        }
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
            RepeatableSpeechKind.CONTEXT_SUGGESTION -> SafeObservationInteraction.NONE
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
        val deterministicResponse = AndroidObservationResponseRenderer.render(observation)

        if (!isDailyBriefingRequestCurrent(requestToken)) return
        conversationOrchestrator.commitFinalDecision(decision)
        if (!isDailyBriefingRequestCurrent(requestToken)) return
        authoritativeRepeatState = AuthoritativeRepeatState(
            speech = deterministicResponse.speech,
            kind = RepeatableSpeechKind.DAILY_BRIEFING,
            contextGeneration = contextGeneration
        )
        homeFollowUpContext = HomeFollowUpContext.AFTER_DAILY_BRIEFING
        if (!isDailyBriefingRequestCurrent(requestToken)) return
        val response = renderObservationResponse(observation)
        if (!isDailyBriefingRequestCurrent(requestToken)) return
        recordObservationResponse(observation, response)
        updateAuthoritativeRepeatSpeech(response.speech)
        if (!isDailyBriefingRequestCurrent(requestToken)) return
        Log.d(
            "DAILY_BRIEFING_RESPONSE",
            "source=${response.source} speechLength=${response.speech.length}"
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

    private fun isRelativeTemporalRequestCurrent(
        requestToken: AssistantRequestToken,
        phase: String
    ): Boolean {
        val current = isAssistantRequestCurrent(requestToken)
        if (!current) {
            Log.d(
                "RELATIVE_TEMPORAL_STALE",
                "phase=$phase reason=NEWER_ASSISTANT_REQUEST"
            )
        }
        return current
    }

    private suspend fun executeContextSuggestion(
        normalizedRequest: String,
        requestToken: AssistantRequestToken,
        routingDecision: ConversationDecision
    ) {
        if (!isContextSuggestionRequestCurrent(requestToken)) return
        Log.d(
            "CONTEXT_SUGGESTION_REQUEST",
            "requestGeneration=${requestToken.requestGeneration}"
        )
        val capturedNow = Calendar.getInstance()
        val taskDao = AppDatabase.getInstance(this@HomeActivity).taskDao()

        val roomData = withContext(Dispatchers.IO) {
            val roots = taskDao.getRootTasks()
            val subtasks = roots.associate { root ->
                root.id to taskDao.getSubtasks(root.id)
            }
            roots to subtasks
        }
        if (!isContextSuggestionRequestCurrent(requestToken)) return

        val snapshot = ContextSuggestionSnapshotBuilder.build(
            now = capturedNow,
            rootTasks = roomData.first,
            subtasksByParentId = roomData.second
        )
        Log.d(
            "CONTEXT_SUGGESTION_SNAPSHOT",
            "requestGeneration=${requestToken.requestGeneration} " +
                "candidateCount=${snapshot.candidates.size} " +
                "closePairCount=${snapshot.closePairs.size} " +
                "actionableClosePairCount=${snapshot.actionableClosePairCount} " +
                "reservedClosePairCandidateCount=" +
                "${snapshot.reservedClosePairCandidateCount} " +
                "excludedPastClosePairCount=${snapshot.excludedPastClosePairCount}"
        )

        if (!isContextSuggestionRequestCurrent(requestToken)) return
        val selection = contextSuggestionSemanticOrchestrator.select(
            originalRequest = normalizedRequest,
            snapshot = snapshot
        )
        if (!isContextSuggestionRequestCurrent(requestToken)) return
        Log.d(
            "CONTEXT_SUGGESTION_AGENT",
            "requestGeneration=${requestToken.requestGeneration} " +
                "suggestionType=${selection.decision.suggestionType} " +
                "primaryRef=${selection.decision.primaryRef} " +
                "secondaryRef=${selection.decision.secondaryRef} " +
                "confidence=${selection.decision.confidence} " +
                "source=${selection.source}"
        )
        Log.d(
            "CONTEXT_SUGGESTION_VALIDATION",
            "requestGeneration=${requestToken.requestGeneration} " +
                "validationResult=${selection.validationResult} " +
                "source=${selection.source}"
        )

        if (!isContextSuggestionRequestCurrent(requestToken)) return
        val selectedCandidates = selectedContextSuggestionCandidates(
            snapshot,
            selection.decision
        )
        val refreshed = withContext(Dispatchers.IO) {
            if (
                selection.decision.suggestionType == ContextSuggestionType.NO_SUGGESTION ||
                selection.decision.suggestionType == ContextSuggestionType.NO_CLOSE_SCHEDULE
            ) {
                val roots = taskDao.getRootTasks()
                val subtasks = roots.associate { root ->
                    root.id to taskDao.getSubtasks(root.id)
                }
                Triple(
                    emptyMap(),
                    emptyMap(),
                    ContextSuggestionSnapshotBuilder.build(
                        now = Calendar.getInstance(),
                        rootTasks = roots,
                        subtasksByParentId = subtasks
                    )
                )
            } else {
                val tasks = selectedCandidates.mapNotNull { candidate ->
                    taskDao.getById(candidate.taskId)
                }
                val subtasks = selectedCandidates.associate { candidate ->
                    candidate.taskId to taskDao.getSubtasks(candidate.taskId)
                }
                Triple(
                    tasks.associateBy(TaskEntity::id),
                    subtasks,
                    null
                )
            }
        }
        if (!isContextSuggestionRequestCurrent(requestToken)) return

        val deliveryResult = when (selection.decision.suggestionType) {
            ContextSuggestionType.NO_SUGGESTION ->
                ContextSuggestionDeliveryGuard.validateFreshNoSuggestion(
                    requireNotNull(refreshed.third)
                )
            ContextSuggestionType.NO_CLOSE_SCHEDULE ->
                ContextSuggestionDeliveryGuard.validateFreshNoCloseSchedule(
                    requireNotNull(refreshed.third)
                )
            else -> ContextSuggestionDeliveryGuard.validateFreshSelection(
                snapshot = snapshot,
                decision = selection.decision,
                freshTasksById = refreshed.first,
                freshSubtasksByParentId = refreshed.second,
                now = Calendar.getInstance()
            )
        }
        if (deliveryResult != ContextSuggestionDeliveryResult.CURRENT) {
            Log.d(
                "CONTEXT_SUGGESTION_STALE",
                "requestGeneration=${requestToken.requestGeneration} " +
                    "validationResult=$deliveryResult"
            )
            deliverChangedContextSuggestion(requestToken, routingDecision, deliveryResult)
            return
        }

        if (!isContextSuggestionRequestCurrent(requestToken)) return
        clearAccessibleTaskQuerySession(clearTaskContext = true)
        if (!isContextSuggestionRequestCurrent(requestToken)) return

        val selectedTasks = selectedCandidates.mapNotNull { refreshed.first[it.taskId] }
        if (selectedTasks.isNotEmpty()) {
            readOnlyTaskContextStore.replaceContextSuggestionResults(
                tasks = selectedTasks,
                subtasksByParentId = refreshed.second
            )
            currentSubtasksByParentId = refreshed.second
        }
        if (::conversationOrchestrator.isInitialized) {
            conversationOrchestrator.clearInvalidContextFocus(
                readOnlyTaskContextStore.snapshot()
            )
        }
        val contextGeneration = readOnlyTaskContextStore.currentGeneration()

        if (!isContextSuggestionRequestCurrent(requestToken)) return
        val primaryCandidate = snapshot.candidate(selection.decision.primaryRef)
        val secondaryCandidate = snapshot.candidate(selection.decision.secondaryRef)
        val primaryTask = primaryCandidate?.let { refreshed.first[it.taskId] }
        val secondaryTask = secondaryCandidate?.let { refreshed.first[it.taskId] }
        val firstUnfinishedSubtask = primaryTask?.let { task ->
            refreshed.second[task.id]
                .orEmpty()
                .sortedWith(compareBy<TaskEntity> { it.subtaskOrder }.thenBy { it.id })
                .firstOrNull { !it.isDone }
        }
        val speech = ContextSuggestionSpeechRenderer.render(
            decision = selection.decision,
            snapshot = snapshot,
            primaryTask = primaryTask,
            secondaryTask = secondaryTask,
            firstUnfinishedSubtask = firstUnfinishedSubtask,
            now = Calendar.getInstance()
        )
        val outcome = if (
            selection.decision.suggestionType == ContextSuggestionType.NO_SUGGESTION ||
            selection.decision.suggestionType == ContextSuggestionType.NO_CLOSE_SCHEDULE
        ) {
            ExecutionOutcome.NO_RESULTS
        } else {
            ExecutionOutcome.INFORMATION
        }
        val observation = ExecutionObservation(
            operation = ExecutionOperation.CONTEXT_SUGGESTION,
            outcome = outcome,
            taskTitle = primaryTask?.title.orEmpty(),
            taskCount = selectedTasks.size,
            detail = selection.decision.suggestionType.name,
            tasks = selectedTasks.map { task ->
                TaskObservationMapper.observedTask(
                    task,
                    refreshed.second[task.id].orEmpty()
                )
            },
            listenAgain = true,
            fallbackSpeech = speech
        )
        val deterministicResponse = AndroidObservationResponseRenderer.render(observation)

        if (!isContextSuggestionRequestCurrent(requestToken)) return
        conversationOrchestrator.commitFinalDecision(routingDecision)
        establishContextSuggestionFocus(
            suggestionType = selection.decision.suggestionType,
            capturedGeneration = contextGeneration,
            requestToken = requestToken
        )
        authoritativeRepeatState = AuthoritativeRepeatState(
            speech = deterministicResponse.speech,
            kind = RepeatableSpeechKind.CONTEXT_SUGGESTION,
            contextGeneration = contextGeneration
        )
        homeFollowUpContext = HomeFollowUpContext.AFTER_CONTEXT_SUGGESTION
        if (!isContextSuggestionRequestCurrent(requestToken)) return
        val response = renderObservationResponse(observation)
        if (!isContextSuggestionRequestCurrent(requestToken)) return
        recordObservationResponse(observation, response)
        updateAuthoritativeRepeatSpeech(response.speech)
        if (!isContextSuggestionRequestCurrent(requestToken)) return
        Log.d(
            "CONTEXT_SUGGESTION_RESPONSE",
            "requestGeneration=${requestToken.requestGeneration} " +
                "suggestionType=${selection.decision.suggestionType} " +
                "primaryRef=${selection.decision.primaryRef} " +
                "secondaryRef=${selection.decision.secondaryRef} " +
                "source=${selection.source} responseSource=${response.source} outcome=$outcome"
        )
        deliverObservationResponse(observation, response)
    }

    private fun selectedContextSuggestionCandidates(
        snapshot: ContextSuggestionSnapshot,
        decision: ContextSuggestionDecision
    ) = when (decision.suggestionType) {
        ContextSuggestionType.NO_SUGGESTION,
        ContextSuggestionType.NO_CLOSE_SCHEDULE -> emptyList()
        ContextSuggestionType.REVIEW_CLOSE_SCHEDULE -> listOfNotNull(
            snapshot.candidate(decision.primaryRef),
            snapshot.candidate(decision.secondaryRef)
        )
        else -> listOfNotNull(snapshot.candidate(decision.primaryRef))
    }

    private fun establishContextSuggestionFocus(
        suggestionType: ContextSuggestionType,
        capturedGeneration: Long,
        requestToken: AssistantRequestToken
    ) {
        if (!isContextSuggestionRequestCurrent(requestToken)) return
        val publishedSnapshot = readOnlyTaskContextStore.snapshot()
        val item = ContextSuggestionFocusPolicy.authoritativeItemOrNull(
            suggestionType = suggestionType,
            publishedSnapshot = publishedSnapshot,
            capturedGeneration = capturedGeneration,
            currentGeneration = readOnlyTaskContextStore.currentGeneration()
        ) ?: return
        if (!isContextSuggestionRequestCurrent(requestToken)) return
        if (readOnlyTaskContextStore.currentGeneration() != capturedGeneration) return
        conversationOrchestrator.setAuthoritativeContextFocus(
            item = item,
            selectedRef = "T1",
            capturedGeneration = capturedGeneration
        )
    }

    private suspend fun deliverChangedContextSuggestion(
        requestToken: AssistantRequestToken,
        routingDecision: ConversationDecision,
        deliveryResult: ContextSuggestionDeliveryResult
    ) {
        if (!isContextSuggestionRequestCurrent(requestToken)) return
        clearAccessibleTaskQuerySession(clearTaskContext = true)
        if (!isContextSuggestionRequestCurrent(requestToken)) return
        val speech =
            "Your tasks changed while I was checking. Please ask for another suggestion."
        val observation = ExecutionObservation(
            operation = ExecutionOperation.CONTEXT_SUGGESTION,
            outcome = ExecutionOutcome.INFORMATION,
            detail = deliveryResult.name,
            listenAgain = true,
            fallbackSpeech = speech
        )
        val deterministicResponse = AndroidObservationResponseRenderer.render(observation)
        conversationOrchestrator.commitFinalDecision(routingDecision)
        authoritativeRepeatState = AuthoritativeRepeatState(
            speech = deterministicResponse.speech,
            kind = RepeatableSpeechKind.CONTEXT_SUGGESTION,
            contextGeneration = readOnlyTaskContextStore.currentGeneration()
        )
        homeFollowUpContext = HomeFollowUpContext.AFTER_CONTEXT_SUGGESTION
        if (!isContextSuggestionRequestCurrent(requestToken)) return
        val response = renderObservationResponse(observation)
        if (!isContextSuggestionRequestCurrent(requestToken)) return
        recordObservationResponse(observation, response)
        updateAuthoritativeRepeatSpeech(response.speech)
        if (!isContextSuggestionRequestCurrent(requestToken)) return
        Log.d(
            "CONTEXT_SUGGESTION_RESPONSE",
            "requestGeneration=${requestToken.requestGeneration} " +
                "suggestionType=NONE primaryRef= secondaryRef= " +
                "source=ANDROID_STALE_GUARD responseSource=${response.source} " +
                "outcome=${ExecutionOutcome.INFORMATION}"
        )
        deliverObservationResponse(observation, response)
    }

    private fun isContextSuggestionRequestCurrent(
        requestToken: AssistantRequestToken
    ): Boolean {
        val current = ContextSuggestionDeliveryGuard.requestResult(
            token = requestToken,
            currentRequestGeneration = assistantRequestGeneration,
            requestActive = assistantRequestActive
        ) == ContextSuggestionDeliveryResult.CURRENT
        if (!current) {
            Log.d(
                "CONTEXT_SUGGESTION_STALE",
                "requestGeneration=${requestToken.requestGeneration} " +
                    "validationResult=${ContextSuggestionDeliveryResult.STALE_REQUEST}"
            )
        }
        return current
    }

    private fun isEligibleContextActionTarget(
        task: TaskEntity?,
        action: ConversationContextAction
    ): Boolean = ContextActionTargetValidator.isEligible(task, action)

    private fun contextActionTargetQuestion(action: ConversationContextAction): String = when (action) {
        ConversationContextAction.RESCHEDULE -> "Which task do you want to reschedule?"
        ConversationContextAction.DELETE -> "Which task do you want to delete?"
        ConversationContextAction.MARK_DONE -> "Which task do you want to complete?"
        ConversationContextAction.MARK_UNDONE -> "Which task do you want to reopen?"
        else -> "Which task do you want to edit?"
    }

    private fun beginContextActionTargetClarification(
        action: ConversationContextAction,
        capture: ReadOnlyTaskContextCapture,
        originalNormalizedRequest: String
    ) {
        require(originalNormalizedRequest.isNotBlank()) {
            "Pending context action requires the original normalized request"
        }
        val suppliedRefs = capture.snapshot.items
            .map { it.ref.uppercase(Locale.ROOT) }
            .toSet()
        if (suppliedRefs.isEmpty()) {
            clearPendingContextActionClarification(restoreContext = true)
            if (homeFollowUpContext == HomeFollowUpContext.CONTEXT_ACTION_TARGET_CLARIFICATION) {
                homeFollowUpContext = HomeFollowUpContext.NONE
            }
            Log.d(
                "CONTEXT_ACTION_CLARIFICATION",
                "action=${action.name} generation=${capture.snapshot.generation} " +
                    "state=REJECTED_EMPTY_CANDIDATES"
            )
            return
        }
        pendingContextActionClarification = PendingContextActionClarification(
            action = action,
            originalNormalizedRequest = originalNormalizedRequest,
            capturedGeneration = capture.snapshot.generation,
            suppliedRefs = suppliedRefs,
            returnContext = homeFollowUpContext
        )
        homeFollowUpContext = HomeFollowUpContext.CONTEXT_ACTION_TARGET_CLARIFICATION
        Log.d(
            "CONTEXT_ACTION_CLARIFICATION",
            "action=${action.name} generation=${capture.snapshot.generation} state=WAITING_FOR_TARGET"
        )
    }

    private fun clearPendingContextActionClarification(restoreContext: Boolean) {
        val pending = pendingContextActionClarification
        pendingContextActionClarification = null
        if (restoreContext &&
            pending != null &&
            homeFollowUpContext == HomeFollowUpContext.CONTEXT_ACTION_TARGET_CLARIFICATION
        ) {
            homeFollowUpContext = pending.returnContext
        }
    }

    private fun beginOrRetainContextActionChangeClarification(
        action: ConversationContextAction,
        groundedRef: String,
        capturedGeneration: Long,
        authoritativeTaskSnapshot: TaskEntity,
        originalNormalizedRequest: String
    ) {
        if (action !in setOf(
                ConversationContextAction.UPDATE,
                ConversationContextAction.RESCHEDULE
            )
        ) return

        val existing = pendingContextActionChangeClarification
        if (existing != null) {
            homeFollowUpContext = HomeFollowUpContext.CONTEXT_ACTION_CHANGE_CLARIFICATION
            Log.d(
                "CONTEXT_ACTION_CHANGE_CLARIFICATION",
                "action=${existing.action.name} ref=${existing.authorityValidatedRef} " +
                    "generation=${existing.capturedGeneration} state=RETAINED"
            )
            return
        }

        pendingContextActionChangeClarification = PendingContextActionChangeClarification(
            action = action,
            authorityValidatedRef = groundedRef,
            capturedGeneration = capturedGeneration,
            authoritativeTaskSnapshot = authoritativeTaskSnapshot,
            originalNormalizedRequest = originalNormalizedRequest,
            returnContext = homeFollowUpContext
        )
        homeFollowUpContext = HomeFollowUpContext.CONTEXT_ACTION_CHANGE_CLARIFICATION
        Log.d(
            "CONTEXT_ACTION_CHANGE_CLARIFICATION",
            "action=${action.name} ref=$groundedRef generation=$capturedGeneration state=WAITING_FOR_CHANGE"
        )
    }

    private fun clearPendingContextActionChangeClarification(restoreContext: Boolean) {
        val pending = pendingContextActionChangeClarification
        pendingContextActionChangeClarification = null
        if (restoreContext &&
            pending != null &&
            homeFollowUpContext == HomeFollowUpContext.CONTEXT_ACTION_CHANGE_CLARIFICATION
        ) {
            homeFollowUpContext = pending.returnContext
        }
    }

    private suspend fun resolvePendingContextActionChange(
        normalizedText: String,
        taskContextCapture: ReadOnlyTaskContextCapture,
        requestToken: AssistantRequestToken
    ): PendingContextActionResolution {
        val pending = pendingContextActionChangeClarification
            ?: return PendingContextActionResolution()
        val normalizedRef = pending.authorityValidatedRef.uppercase(Locale.ROOT)
        val snapshotRefCount = taskContextCapture.snapshot.items.count {
            it.ref.uppercase(Locale.ROOT) == normalizedRef
        }
        val generationCurrent =
            pending.capturedGeneration == taskContextCapture.snapshot.generation &&
                pending.capturedGeneration == readOnlyTaskContextStore.currentGeneration()
        val resolvedTaskId = if (generationCurrent && snapshotRefCount == 1) {
            readOnlyTaskContextStore.resolveRef(
                ref = normalizedRef,
                expectedGeneration = pending.capturedGeneration
            )
        } else {
            null
        }
        val resolvedTask = resolvedTaskId?.let { taskId ->
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(this@HomeActivity).taskDao().getById(taskId)
            }
        }
        if (!isAssistantRequestCurrent(requestToken) ||
            pendingContextActionChangeClarification != pending
        ) {
            throw CancellationException("Pending context change result is stale")
        }

        val authorityCurrent = generationCurrent &&
            snapshotRefCount == 1 &&
            resolvedTask != null &&
            readOnlyTaskContextStore.matchesResolvedTask(
                ref = normalizedRef,
                expectedGeneration = pending.capturedGeneration,
                task = resolvedTask
            ) &&
            sameContextActionTaskSnapshot(pending.authoritativeTaskSnapshot, resolvedTask) &&
            isEligibleContextActionTarget(resolvedTask, pending.action)
        if (!authorityCurrent) {
            clearPendingContextActionChangeClarification(restoreContext = false)
            homeFollowUpContext = HomeFollowUpContext.NONE
            Log.d(
                "CONTEXT_ACTION_CHANGE_CLARIFICATION",
                "action=${pending.action.name} ref=$normalizedRef " +
                    "generation=${pending.capturedGeneration} state=STALE"
            )
            return PendingContextActionResolution(
                decision = ConversationDecision(
                    route = ConversationRoute.ASK_CLARIFICATION,
                    reply = "That task changed while I was waiting. Please repeat your task query.",
                    listenAgain = true,
                    source = "android_context_action_change_clarification_stale"
                )
            )
        }

        Log.d(
            "CONTEXT_ACTION_CHANGE_CLARIFICATION",
            "action=${pending.action.name} ref=$normalizedRef " +
                "generation=${pending.capturedGeneration} state=CONTINUING"
        )
        return PendingContextActionResolution(
            decision = ConversationDecision(
                route = ConversationRoute.CONTEXT_ACTION,
                contextRef = normalizedRef,
                contextAction = pending.action,
                confidence = 1.0,
                listenAgain = false,
                source = "android_pending_context_change"
            ),
            originalActionRequest = normalizedText,
            authorityValidatedRef = normalizedRef
        )
    }

    private suspend fun resolvePendingContextActionTarget(
        normalizedText: String,
        taskContextCapture: ReadOnlyTaskContextCapture,
        contextFocus: ConversationContextFocus?,
        requestToken: AssistantRequestToken
    ): PendingContextActionResolution {
        val pending = pendingContextActionClarification
            ?: return PendingContextActionResolution()
        val currentGeneration = readOnlyTaskContextStore.currentGeneration()
        val currentRefs = taskContextCapture.snapshot.items
            .map { it.ref.uppercase(Locale.ROOT) }
            .toSet()
        if (pending.capturedGeneration != taskContextCapture.snapshot.generation ||
            currentGeneration != pending.capturedGeneration ||
            currentRefs != pending.suppliedRefs
        ) {
            clearPendingContextActionClarification(restoreContext = false)
            homeFollowUpContext = HomeFollowUpContext.NONE
            Log.d(
                "CONTEXT_ACTION_CLARIFICATION",
                "action=${pending.action.name} generation=${pending.capturedGeneration} state=STALE"
            )
            return PendingContextActionResolution(
                decision = ConversationDecision(
                    route = ConversationRoute.ASK_CLARIFICATION,
                    reply = "Those task results changed. Please repeat your task query.",
                    listenAgain = true,
                    source = "android_context_action_clarification_stale"
                )
            )
        }

        val interpretation = try {
            conversationOrchestrator.processPendingContextActionTarget(
                normalizedText = normalizedText,
                readOnlyTaskContextSnapshot = taskContextCapture.promptText,
                pendingAction = pending.action
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            return PendingContextActionResolution(
                decision = ConversationDecision(
                    route = ConversationRoute.ASK_CLARIFICATION,
                    reply = contextActionTargetQuestion(pending.action),
                    listenAgain = true,
                    source = "android_context_action_clarification_fallback"
                )
            )
        }
        Log.d(
            "PENDING_CONTEXT_TARGET_RESULT",
            "move=${interpretation.move.name} ref=${interpretation.contextRef} " +
                "confidence=${interpretation.confidence}"
        )
        if (!isAssistantRequestCurrent(requestToken) ||
            pendingContextActionClarification != pending
        ) {
            throw CancellationException("Pending context target result is stale")
        }
        if (readOnlyTaskContextStore.currentGeneration() != pending.capturedGeneration) {
            clearPendingContextActionClarification(restoreContext = false)
            homeFollowUpContext = HomeFollowUpContext.NONE
            return PendingContextActionResolution(
                decision = ConversationDecision(
                    route = ConversationRoute.ASK_CLARIFICATION,
                    reply = "Those task results changed. Please repeat your task query.",
                    listenAgain = true,
                    source = "android_context_action_clarification_stale"
                )
            )
        }

        if (interpretation.move == PendingContextActionTargetMove.NOT_A_TARGET_ANSWER) {
            clearPendingContextActionClarification(restoreContext = true)
            conversationOrchestrator.clearPendingDialogueAction()
            Log.d(
                "CONTEXT_ACTION_CLARIFICATION",
                "action=${pending.action.name} generation=${pending.capturedGeneration} state=ABANDONED"
            )
            return PendingContextActionResolution()
        }
        if (interpretation.move == PendingContextActionTargetMove.ASK_CLARIFICATION) {
            return PendingContextActionResolution(
                decision = ConversationDecision(
                    route = ConversationRoute.ASK_CLARIFICATION,
                    reply = contextActionTargetQuestion(pending.action),
                    listenAgain = true,
                    source = "conversation_agent_pending_context_target"
                )
            )
        }

        val candidate = ConversationDecision(
            route = ConversationRoute.CONTEXT_ACTION,
            contextRef = interpretation.contextRef,
            contextAction = pending.action,
            confidence = interpretation.confidence,
            listenAgain = false,
            source = "conversation_agent_pending_context_target"
        )
        val validation = ContextActionDecisionValidator.validate(
            decision = candidate,
            capturedSnapshot = taskContextCapture.snapshot,
            currentGeneration = currentGeneration
        )
        val authority = if (validation.isValid) {
            PendingContextActionTargetAuthorityValidator.validate(
                normalizedText = normalizedText,
                interpretation = interpretation,
                candidate = candidate,
                pendingAction = pending.action,
                pendingGeneration = pending.capturedGeneration,
                pendingSuppliedRefs = pending.suppliedRefs,
                capturedSnapshot = taskContextCapture.snapshot,
                currentGeneration = currentGeneration,
                currentFocus = contextFocus
            )
        } else {
            null
        }
        Log.d(
            "PENDING_CONTEXT_TARGET_AUTHORITY",
            "ref=${interpretation.contextRef} " +
                "result=${if (authority?.isAccepted == true) "ACCEPTED" else "REJECTED"} " +
                "reason=${authority?.result ?: validation.result}"
        )
        if (!validation.isValid || authority?.isAccepted != true) {
            return PendingContextActionResolution(
                decision = ConversationDecision(
                    route = ConversationRoute.ASK_CLARIFICATION,
                    reply = contextActionTargetQuestion(pending.action),
                    listenAgain = true,
                    source = "android_pending_context_target_validation"
                )
            )
        }

        clearPendingContextActionClarification(restoreContext = true)
        Log.d(
            "CONTEXT_ACTION_CLARIFICATION_RESOLVED",
            "action=${pending.action.name} ref=${authority.ref} generation=${pending.capturedGeneration}"
        )
        return PendingContextActionResolution(
            decision = candidate.copy(contextRef = authority.ref),
            originalActionRequest = pending.originalNormalizedRequest,
            authorityValidatedRef = authority.ref
        )
    }

    private fun sameContextActionTaskSnapshot(first: TaskEntity?, second: TaskEntity?): Boolean =
        first != null && second != null &&
            first.id == second.id &&
            first.title == second.title &&
            first.dueDate == second.dueDate &&
            first.dueTime == second.dueTime &&
            first.isDone == second.isDone &&
            first.parentTaskId == second.parentTaskId &&
            first.subtaskOrder == second.subtaskOrder

    private suspend fun executeContextSubtaskCompletion(
        requestToken: AssistantRequestToken,
        task: TaskEntity,
        action: ConversationContextAction,
        grounding: String,
        targetRef: String,
        capturedGeneration: Long
    ) {
        val parentTaskId = task.parentTaskId ?: return
        val observation = executeDeterministicTaskCompletion(
            task = task,
            action = action,
            grounding = grounding,
            targetRef = targetRef
        ).copy(listenAgain = true)
        val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
        val refreshed = SubtaskCompletionContextRefresher(
            store = readOnlyTaskContextStore,
            getSubtasks = { parentId ->
                withContext(Dispatchers.IO) { dao.getSubtasks(parentId) }
            },
            isCurrent = {
                assistantSession.assistantSessionActive &&
                    isAssistantRequestCurrent(requestToken)
            }
        ).refresh(
            parentTaskId = parentTaskId,
            affectedTaskId = task.id,
            expectedGeneration = capturedGeneration
        ) ?: return

        val delivered = speakObservation(observation)
        if (!delivered ||
            !assistantSession.assistantSessionActive ||
            !isAssistantRequestCurrent(requestToken) ||
            readOnlyTaskContextStore.currentGeneration() != refreshed.capture.snapshot.generation
        ) {
            return
        }
        conversationOrchestrator.setAuthoritativeContextFocus(
            item = refreshed.affectedItem,
            selectedRef = refreshed.affectedItem.ref,
            capturedGeneration = refreshed.capture.snapshot.generation
        )
    }

    private suspend fun executeDeterministicTaskCompletion(
        task: TaskEntity,
        action: ConversationContextAction,
        grounding: String,
        targetRef: String = ""
    ): ExecutionObservation {
        val plan = TaskCompletionMutationPolicy.plan(task, action)
        val operation = if (plan.desiredDone) {
            ExecutionOperation.MARK_DONE
        } else {
            ExecutionOperation.MARK_UNDONE
        }
        if (!plan.requiresMutation) {
            Log.d(
                "CONTEXT_COMPLETION",
                "action=${action.name} grounding=$grounding targetRef=$targetRef " +
                    "previousDone=${task.isDone} result=ALREADY_IN_STATE"
            )
            return ExecutionObservation(
                operation = operation,
                outcome = ExecutionOutcome.INFORMATION,
                taskTitle = task.title,
                tasks = listOf(observedTask(task)),
                listenAgain = false,
                fallbackSpeech = if (plan.desiredDone) {
                    "${task.title} is already completed."
                } else {
                    "${task.title} is already active and not completed."
                }
            )
        }

        val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
        withContext(Dispatchers.IO) {
            if (plan.propagateToSubtasks) {
                dao.updateDoneStatusForTaskAndSubtasks(task.id, plan.desiredDone)
            } else {
                dao.updateDoneStatus(task.id, plan.desiredDone)
            }
        }
        when (plan.reminderDirective) {
            TaskCompletionReminderDirective.CANCEL_ROOT_SEQUENCE ->
                ReminderHelper.cancelReminder(this@HomeActivity, task.id)
            TaskCompletionReminderDirective.RESCHEDULE_ROOT_IF_ELIGIBLE ->
                ReminderHelper.scheduleReminderFromTask(
                    this@HomeActivity,
                    task.copy(isDone = false)
                )
            TaskCompletionReminderDirective.NONE -> Unit
        }
        refreshOverview()
        Log.d(
            "CONTEXT_COMPLETION",
            "action=${action.name} grounding=$grounding targetRef=$targetRef " +
                "previousDone=${task.isDone} result=APPLIED"
        )
        val updatedTask = task.copy(isDone = plan.desiredDone)
        return ExecutionObservation(
            operation = operation,
            outcome = ExecutionOutcome.SUCCESS,
            taskTitle = task.title,
            tasks = listOf(observedTask(updatedTask)),
            listenAgain = false,
            fallbackSpeech = if (plan.desiredDone) {
                responseManager.markDoneSuccess(task.title)
            } else {
                responseManager.markUndoneSuccess(task.title)
            }
        )
    }

    private fun rejectUnavailableContextAction() {
        if (pendingContextActionChangeClarification != null) {
            clearPendingContextActionChangeClarification(restoreContext = false)
            homeFollowUpContext = HomeFollowUpContext.NONE
        }
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
        requestToken: AssistantRequestToken,
        task: TaskEntity,
        action: ConversationContextAction,
        extractedChange: ContextActionChangeSet,
        calculatedTemporal: RelativeTemporalCalculationResult.Success?
    ) {
        val operation = if (action == ConversationContextAction.RESCHEDULE) {
            ExecutionOperation.RESCHEDULE_TASK
        } else {
            ExecutionOperation.UPDATE_TASK
        }
        val reply = if (action == ConversationContextAction.RESCHEDULE) {
            "I calculated the new schedule. Opening it for confirmation."
        } else {
            responseManager.openEditTask()
        }
        if (!isRelativeTemporalRequestCurrent(requestToken, "BEFORE_OPEN")) return
        speakObservationThenRun(
            ExecutionObservation(
                operation = operation,
                outcome = ExecutionOutcome.INFORMATION,
                taskTitle = task.title,
                tasks = listOf(observedTask(task)),
                dateText = calculatedTemporal?.schedule?.date.orEmpty(),
                timeText = calculatedTemporal?.schedule?.time.orEmpty(),
                listenAgain = false,
                fallbackSpeech = reply
            )
        ) {
            if (!isRelativeTemporalRequestCurrent(requestToken, "BEFORE_OPEN")) {
                return@speakObservationThenRun
            }
            val editIntent = Intent(this@HomeActivity, EditTaskActivity::class.java).apply {
                putExtra("task_id", task.id)
                putExtra("task_title", task.title)
                putExtra("task_date", task.dueDate)
                putExtra("task_time", task.dueTime)
                putExtra("task_is_done", task.isDone)
                putExtra("opened_by_assistant", true)
                putExtra("prefill_new_date_text", calculatedTemporal?.schedule?.date)
                putExtra("prefill_new_time_text", calculatedTemporal?.schedule?.time)
                if (action == ConversationContextAction.UPDATE &&
                    !extractedChange.replacementTitle.isNullOrBlank()
                ) {
                    putExtra("prefill_title", extractedChange.replacementTitle)
                }
                if (action == ConversationContextAction.RESCHEDULE) {
                    putExtra("assistant_mode", "reschedule")
                    putExtra("relative_temporal_proposal", calculatedTemporal != null)
                    putExtra("relative_temporal_revision", 1)
                    extractedChange.temporalProposal?.let { proposal ->
                        putExtra("relative_temporal_date_operation", proposal.dateOperation.name)
                        putExtra("relative_temporal_time_operation", proposal.timeOperation.name)
                        putExtra("relative_temporal_base", proposal.relativeBase.name)
                        putExtra("relative_temporal_replacement_date", proposal.replacementDateText)
                        putExtra("relative_temporal_replacement_time", proposal.replacementTimeText)
                        putExtra("relative_temporal_date_offset_days", proposal.dateOffsetDays)
                        putExtra("relative_temporal_time_offset_minutes", proposal.timeOffsetMinutes)
                        putExtra("relative_temporal_confidence", proposal.confidence)
                    }
                    putExtra(
                        "relative_temporal_crossed_date_boundary",
                        calculatedTemporal?.crossedDateBoundary == true
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

    private fun handleNamedTaskQuery(
        targetTitle: String,
        detail: TaskQueryDetail,
        targetDateText: String?,
        targetTimeText: String?,
        requestToken: AssistantRequestToken
    ) {
        if (!isAssistantRequestCurrent(requestToken)) return
        val queryGeneration = clearAccessibleTaskQuerySession(clearTaskContext = true)
        homeFollowUpContext = HomeFollowUpContext.NONE
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
            val roots = withContext(Dispatchers.IO) { dao.getRootTasks() }
            if (queryGeneration != queryReadingStateGeneration || !isAssistantRequestCurrent(requestToken)) return@launch
            val result = namedTaskQueryResolver.resolve(targetTitle, roots, targetDateText, targetTimeText)
            if (BuildConfig.DEBUG) {
                Log.d(
                    "HOME_NAMED_QUERY",
                    "target='$targetTitle' detail=$detail match='${result.task?.title.orEmpty()}' " +
                        "score=${result.score} ambiguous=${result.status == NamedTaskQueryStatus.AMBIGUOUS} " +
                        "result=${result.status}"
                )
            }
            when (result.status) {
                NamedTaskQueryStatus.NOT_FOUND -> {
                    assistantSession.speak(responseManager.taskMatchNotFound(), listenAgain = true)
                    return@launch
                }
                NamedTaskQueryStatus.AMBIGUOUS -> {
                    assistantSession.speak("More than one task matches. Please say a more specific task name or its date or time.", listenAgain = true)
                    return@launch
                }
                NamedTaskQueryStatus.UNRESOLVED_TEMPORAL -> {
                    assistantSession.speak("I could not understand the date or time used to identify that task. Please say it another way.", listenAgain = true)
                    return@launch
                }
                NamedTaskQueryStatus.RESOLVED -> Unit
            }
            val matched = requireNotNull(result.task)
            val (currentTask, subtasks) = withContext(Dispatchers.IO) {
                dao.getById(matched.id) to dao.getSubtasks(matched.id)
            }
            if (queryGeneration != queryReadingStateGeneration || !isAssistantRequestCurrent(requestToken)) return@launch
            if (currentTask == null || currentTask != matched) {
                assistantSession.speak("That task changed or is no longer available. Please ask again.", listenAgain = true)
                return@launch
            }
            // No model verbalization: only re-fetched Room values enter factual speech.
            val speech = ReadOnlyTaskContextResponseRenderer.renderSchedule(
                currentTask.title, currentTask.dueDate, currentTask.dueTime, detail
            )
            val capture = publishTaskDetailAssistantContext(currentTask, subtasks)
            Log.d("HOME_NAMED_QUERY", "result=PUBLISHED contextScope=${capture.snapshot.scope} focusEstablished=true")
            homeFollowUpContext = HomeFollowUpContext.AFTER_TASK_DETAILS
            authoritativeRepeatState = AuthoritativeRepeatState(
                speech = speech,
                kind = RepeatableSpeechKind.CONTEXT_READ,
                contextGeneration = capture.snapshot.generation
            )
            assistantSession.speak(speech, listenAgain = true)
        }
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

    private fun responseVerbalizationTone(): ResponseVerbalizationTone =
        when (responseManager.tone) {
            AssistantTone.FRIENDLY -> ResponseVerbalizationTone.FRIENDLY
            AssistantTone.NEUTRAL -> ResponseVerbalizationTone.NEUTRAL
            AssistantTone.PROFESSIONAL -> ResponseVerbalizationTone.PROFESSIONAL
        }

    private fun responseVerbalizationVerbosity(): ResponseVerbalizationVerbosity =
        when (responseManager.verbosity) {
            AssistantVerbosity.BRIEF -> ResponseVerbalizationVerbosity.SHORT
            AssistantVerbosity.BALANCED -> ResponseVerbalizationVerbosity.NORMAL
            AssistantVerbosity.DETAILED -> ResponseVerbalizationVerbosity.DETAILED
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





    private fun isConversationExitCommand(normalized: String): Boolean =
        AssistantExitInterpreter.isExitUtterance(normalized)

    private fun isSimpleFollowUpEndCommand(normalized: String): Boolean =
        AssistantExitInterpreter.isFollowUpExitUtterance(normalized)

    private fun isSimpleFollowUpAgreement(normalized: String): Boolean = normalized in setOf(
        "yes",
        "yeah",
        "sure",
        "okay",
        "ok",
        "please do",
        "read them"
    )

    private fun isBoundedCreateFollowUpControl(normalized: String): Boolean = normalized in setOf(
        "yes",
        "yes please",
        "yeah",
        "yep",
        "sure",
        "okay",
        "ok",
        "create",
        "create one"
    )

    private fun handleBoundedDeleteConfirmation(normalized: String): Boolean {
        if (homeFollowUpContext != HomeFollowUpContext.DELETE_CONFIRMATION) return false
        val resolution = DeleteConfirmationPolicy.resolve(normalized)
        Log.d(
            "CONFIRMATION_RESOLUTION",
            "raw='${resolution.normalizedText}' context=DELETE_CONFIRMATION " +
                "result=${resolution.result.name} source=${resolution.source} " +
                "confidence=${resolution.confidence}"
        )
        return when (resolution.result) {
            BoundedConfirmationResult.AFFIRM -> {
                confirmPendingDelete()
                true
            }
            BoundedConfirmationResult.REJECT,
            BoundedConfirmationResult.CANCEL -> {
                cancelPendingDeleteConfirmation()
                true
            }
            BoundedConfirmationResult.UNKNOWN -> false
        }
    }

    private fun cancelPendingDeleteConfirmation() {
        val title = pendingDeleteTaskTitle
        clearPendingDeleteState()
        clearConversationSessionContext()
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
    }

    private fun handleContextItemRestatement(normalized: String): Boolean {
        val isResultInteraction = homeFollowUpContext in setOf(
            HomeFollowUpContext.AFTER_TASK_SUMMARY,
            HomeFollowUpContext.AFTER_TASK_DETAILS,
            HomeFollowUpContext.QUERY_PAGE,
            HomeFollowUpContext.AFTER_DAILY_BRIEFING,
            HomeFollowUpContext.AFTER_CONTEXT_SUGGESTION,
            HomeFollowUpContext.DELETE_CONFIRMATION
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
            HomeFollowUpContext.AFTER_DAILY_BRIEFING,
            HomeFollowUpContext.AFTER_CONTEXT_SUGGESTION,
            HomeFollowUpContext.DELETE_CONFIRMATION
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

        if (validation.detail == ConversationContextDetail.SUBTASKS) {
            val readRequestGeneration = assistantRequestGeneration
            lifecycleScope.launch {
                val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
                val speech = AuthoritativeSubtaskReader(
                    readOnlyTaskContextStore, dao::getById, dao::getSubtasks,
                    isCurrent = {
                        assistantSession.assistantSessionActive &&
                            assistantRequestGeneration == readRequestGeneration
                    }
                ).read(requireNotNull(validation.item).ref, taskContextCapture.snapshot.generation)
                if (!assistantSession.assistantSessionActive ||
                    assistantRequestGeneration != readRequestGeneration
                ) return@launch
                if (speech == null) {
                    assistantSession.speak(
                        "Those task results changed. Please repeat your task query.",
                        listenAgain = true
                    )
                    return@launch
                }
                conversationOrchestrator.recordAuthoritativeContextRead(
                    item = requireNotNull(validation.item),
                    selectedRef = validation.item.ref,
                    selectedDetail = validation.detail,
                    capturedGeneration = taskContextCapture.snapshot.generation,
                    finalSpeech = speech
                )
                conversationOrchestrator.clearInvalidContextFocus(readOnlyTaskContextStore.snapshot())
                authoritativeRepeatState = AuthoritativeRepeatState(
                    speech = speech,
                    kind = RepeatableSpeechKind.CONTEXT_READ,
                    contextGeneration = readOnlyTaskContextStore.currentGeneration()
                )
                assistantSession.speak(speech, listenAgain = true)
            }
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

            HomeFollowUpContext.AFTER_DAILY_BRIEFING,
            HomeFollowUpContext.AFTER_CONTEXT_SUGGESTION -> false
            else -> false
        }
    }

    private suspend fun handleBoundedQueryCountFollowUp(
        normalized: String,
        requestToken: AssistantRequestToken,
        authorization: SafeStyleTurnAuthorization
    ): Boolean {
        if (homeFollowUpContext != HomeFollowUpContext.QUERY_COUNT ||
            accessibleTaskQuerySession == null
        ) return false

        val decision = try {
            conversationOrchestrator.processQueryCountFollowUp(normalized)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            Log.d(
                "QUERY_COUNT_FOLLOWUP",
                "move=NOT_A_QUERY_READING_CONTROL source=android_fallback"
            )
            return false
        }
        if (!isAssistantRequestCurrent(requestToken) ||
            homeFollowUpContext != HomeFollowUpContext.QUERY_COUNT ||
            accessibleTaskQuerySession == null
        ) return true

        Log.d(
            "QUERY_COUNT_FOLLOWUP",
            "move=${decision.move.name} source=conversation_agent_bounded"
        )
        return when (decision.move) {
            QueryCountFollowUpMove.START_OVERVIEW -> {
                startQueryOverviewFromCount(requestToken, authorization)
                true
            }
            QueryCountFollowUpMove.STOP -> {
                endAssistantConversation()
                true
            }
            QueryCountFollowUpMove.NOT_A_QUERY_READING_CONTROL -> false
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
            HomeFollowUpContext.AFTER_CONTEXT_SUGGESTION ->
                QueryReadingInteractionState.CONTEXT_SUGGESTION
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

    /** Updates only the repeatable presentation payload; kind and context stay Android-owned. */
    private fun updateAuthoritativeRepeatSpeech(speech: String) {
        authoritativeRepeatState = authoritativeRepeatState?.copy(speech = speech)
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
        clearPendingTaskMatchState()
        clearPendingDeleteState()
        clearPendingBreakdownState()
        savedRoutineInteractionController.clear()
        if (routineDraftController.state != RoutineDraftState.SAVING) {
            routineDraftController.clear()
        }
        assistantSession.endConversation(responseManager.stopListening())
    }

    private fun clearConversationSessionContext() {
        voiceSettingConversationContext.clear()
        clearPendingContextActionClarification(restoreContext = false)
        clearPendingContextActionChangeClarification(restoreContext = false)
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

    private suspend fun handleSavedRoutineAction(
        normalizedRequest: String,
        requestToken: AssistantRequestToken
    ) {
        if (!isAssistantRequestCurrent(requestToken)) return
        val semanticSnapshot = savedRoutineInteractionController.begin(
            SavedRoutineAction.UNKNOWN,
            ""
        )
        val modelDecision = try {
            savedRoutineSemanticOrchestrator.interpret(normalizedRequest)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SavedRoutineActionSchemaException) {
            if (savedRoutineInteractionController.isCurrent(semanticSnapshot.generation) &&
                isAssistantRequestCurrent(requestToken)
            ) {
                savedRoutineInteractionController.clear()
                assistantSession.speak(
                    "I could not safely interpret that saved-routine request. Please try again.",
                    listenAgain = true
                )
            }
            return
        } catch (_: Exception) {
            if (savedRoutineInteractionController.isCurrent(semanticSnapshot.generation) &&
                isAssistantRequestCurrent(requestToken)
            ) {
                savedRoutineInteractionController.clear()
                assistantSession.speak(
                    "I could not check saved routines right now. Please try again.",
                    listenAgain = true
                )
            }
            return
        }
        if (!savedRoutineInteractionController.isCurrent(semanticSnapshot.generation) ||
            !isAssistantRequestCurrent(requestToken)
        ) {
            DebugDiagnosticLog.event(
                "SAVED_ROUTINE_STALE",
                "phase=SEMANTIC_DELIVERY\nreason=NEWER_INTERACTION"
            )
            return
        }
        val consistency = SavedRoutineActionConsistencyPolicy.reconcile(
            userText = normalizedRequest,
            modelDecision = modelDecision
        )
        val decision = consistency.decision
        DebugDiagnosticLog.event(
            "SAVED_ROUTINE_ACTION_CONSISTENCY",
            "originalAction=${modelDecision.action.name}\n" +
                "reconciledAction=${decision.action.name}\n" +
                "reason=${consistency.reason}\n" +
                "titleRecoveredFromLiteralText=" +
                consistency.titleRecoveredFromLiteralText
        )
        DebugDiagnosticLog.event(
            "SAVED_ROUTINE_ACTION_DECISION",
            "action=${decision.action.name}\n" +
                "routineTitle=${decision.routineTitle}\n" +
                "dateText=${decision.dateText}\nconfidence=${decision.confidence}"
        )
        val interaction = savedRoutineInteractionController.begin(
            decision.action,
            decision.dateText
        )
        when (decision.action) {
            SavedRoutineAction.LIST -> listSavedRoutines(interaction.generation, requestToken)
            SavedRoutineAction.READ_DETAILS,
            SavedRoutineAction.RUN,
            SavedRoutineAction.DELETE ->
                resolveSavedRoutineTarget(decision, interaction.generation, requestToken)
            SavedRoutineAction.UNKNOWN -> {
                savedRoutineInteractionController.clear()
                assistantSession.speak(
                    "I can list, read, run, or delete a saved routine. Please say which one you want.",
                    listenAgain = true
                )
            }
        }
    }

    private suspend fun listSavedRoutines(
        interactionGeneration: Long,
        requestToken: AssistantRequestToken
    ) {
        val routines = try {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(this@HomeActivity).routineDao().listAll()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (savedRoutineInteractionController.isCurrent(interactionGeneration) &&
                isAssistantRequestCurrent(requestToken)
            ) {
                savedRoutineInteractionController.clear()
                assistantSession.speak(
                    "I could not read saved routines right now. Nothing was changed.",
                    listenAgain = true
                )
            }
            return
        }
        if (!savedRoutineInteractionController.isCurrent(interactionGeneration) ||
            !isAssistantRequestCurrent(requestToken)
        ) {
            DebugDiagnosticLog.event(
                "SAVED_ROUTINE_STALE",
                "phase=LIST_DELIVERY\nreason=NEWER_INTERACTION"
            )
            return
        }
        savedRoutineInteractionController.clear()
        val count = routines.size
        val speech = if (count == 0) {
            "You have no saved routines."
        } else {
            val spoken = routines.take(5).mapIndexed { index, routine ->
                "${savedRoutineOrdinal(index)}, ${routine.routine.title}."
            }.joinToString(" ")
            val additional = count - minOf(count, 5)
            val suffix = if (additional > 0) {
                " There ${if (additional == 1) "is" else "are"} $additional more saved " +
                    "${if (additional == 1) "routine" else "routines"}."
            } else {
                ""
            }
            "You have $count saved ${if (count == 1) "routine" else "routines"}. $spoken$suffix"
        }
        assistantSession.speak(speech, listenAgain = true)
    }

    private suspend fun resolveSavedRoutineTarget(
        decision: SavedRoutineActionDecision,
        interactionGeneration: Long,
        requestToken: AssistantRequestToken
    ) {
        val routines = try {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(this@HomeActivity).routineDao().listAll()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (savedRoutineInteractionController.isCurrent(interactionGeneration) &&
                isAssistantRequestCurrent(requestToken)
            ) {
                savedRoutineInteractionController.clear()
                assistantSession.speak(
                    "I could not read saved routines right now. Nothing was changed.",
                    listenAgain = true
                )
            }
            return
        }
        if (!savedRoutineInteractionController.isCurrent(interactionGeneration) ||
            !isAssistantRequestCurrent(requestToken)
        ) {
            DebugDiagnosticLog.event(
                "SAVED_ROUTINE_STALE",
                "phase=MATCH_DELIVERY\nreason=NEWER_INTERACTION"
            )
            return
        }
        val match = RoutineMatcher.match(
            decision.routineTitle,
            routines.map(RoutineWithSteps::routine)
        )
        val matchCount = when (match) {
            RoutineMatchResult.NoMatch -> 0
            is RoutineMatchResult.One -> 1
            is RoutineMatchResult.Ambiguous -> match.routines.size
        }
        DebugDiagnosticLog.event(
            "SAVED_ROUTINE_MATCH",
            "matchCount=$matchCount\nambiguous=${match is RoutineMatchResult.Ambiguous}"
        )
        when (match) {
            RoutineMatchResult.NoMatch -> {
                savedRoutineInteractionController.clear()
                assistantSession.speak(
                    "I could not find a saved routine matching ${decision.routineTitle}.",
                    listenAgain = true
                )
            }
            is RoutineMatchResult.One -> {
                if (!savedRoutineInteractionController.selectSingle(
                        interactionGeneration,
                        match.routine.id
                    )
                ) {
                    DebugDiagnosticLog.event(
                        "SAVED_ROUTINE_STALE",
                        "phase=SINGLE_MATCH\nreason=CONTROLLER_CHANGED"
                    )
                    return
                }
                executeSelectedSavedRoutine(
                    routineId = match.routine.id,
                    interactionGeneration = interactionGeneration,
                    requestToken = requestToken
                )
            }
            is RoutineMatchResult.Ambiguous -> {
                val candidates = match.routines.take(5).map {
                    SavedRoutineCandidate(it.id, it.title)
                }
                if (!savedRoutineInteractionController.chooseCandidates(
                        interactionGeneration,
                        candidates
                    )
                ) {
                    return
                }
                val choices = candidates.mapIndexed { index, candidate ->
                    "${savedRoutineOrdinal(index)}, ${candidate.displayTitle}."
                }.joinToString(" ")
                assistantSession.speak(
                    "I found ${match.routines.size} possible routines. $choices " +
                        "Please choose an option, or say cancel.",
                    listenAgain = true
                )
            }
        }
    }

    private fun handleSavedRoutineInteractionFollowUp(
        normalized: String,
        requestToken: AssistantRequestToken
    ) {
        when (savedRoutineInteractionController.state) {
            SavedRoutineInteractionState.CHOOSING_MATCH -> {
                when (val choice = savedRoutineInteractionController.choose(normalized)) {
                    is SavedRoutineChoice.Selected -> {
                        val generation = savedRoutineInteractionController.snapshot().generation
                        lifecycleScope.launch {
                            executeSelectedSavedRoutine(
                                choice.routineId,
                                generation,
                                requestToken
                            )
                        }
                    }
                    SavedRoutineChoice.Cancelled -> assistantSession.speak(
                        "Cancelled. No routine was selected and nothing was changed.",
                        listenAgain = false
                    )
                    SavedRoutineChoice.Invalid -> assistantSession.speak(
                        "Please say first, second, a unique routine title, or cancel.",
                        listenAgain = true
                    )
                }
            }
            SavedRoutineInteractionState.CONFIRMING_DELETE -> {
                when {
                    isSavedRoutineConfirmation(normalized) -> deleteConfirmedSavedRoutine()
                    isSavedRoutineRejection(normalized) -> {
                        savedRoutineInteractionController.clear()
                        assistantSession.speak(
                            "Okay, I did not delete the saved routine. Existing tasks are unchanged.",
                            listenAgain = false
                        )
                    }
                    else -> assistantSession.speak(
                        "Please say yes to delete the saved routine, or no to keep it.",
                        listenAgain = true
                    )
                }
            }
            SavedRoutineInteractionState.DELETING -> {
                assistantSession.speak(
                    "That saved-routine deletion is already being processed.",
                    listenAgain = false
                )
            }
            SavedRoutineInteractionState.RESOLVING -> {
                if (isSavedRoutineRejection(normalized)) {
                    savedRoutineInteractionController.clear()
                    assistantSession.speak(
                        "Cancelled. Nothing was changed.",
                        listenAgain = false
                    )
                } else {
                    savedRoutineInteractionController.clear()
                    handleVoiceCommand(normalized)
                }
            }
            SavedRoutineInteractionState.NONE -> Unit
        }
    }

    private suspend fun executeSelectedSavedRoutine(
        routineId: Long,
        interactionGeneration: Long,
        requestToken: AssistantRequestToken
    ) {
        if (!savedRoutineInteractionController.isCurrent(interactionGeneration) ||
            !isAssistantRequestCurrent(requestToken)
        ) {
            DebugDiagnosticLog.event(
                "SAVED_ROUTINE_STALE",
                "phase=SELECTION\nreason=NEWER_INTERACTION"
            )
            return
        }
        DebugDiagnosticLog.event(
            "SAVED_ROUTINE_SELECTED",
            "internalRoutineId=$routineId"
        )
        val routine = try {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(this@HomeActivity).routineDao().getById(routineId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (savedRoutineInteractionController.isCurrent(interactionGeneration) &&
                isAssistantRequestCurrent(requestToken)
            ) {
                savedRoutineInteractionController.clear()
                assistantSession.speak(
                    "I could not load that saved routine. Nothing was changed.",
                    listenAgain = true
                )
            }
            return
        }
        if (!savedRoutineInteractionController.isCurrent(interactionGeneration) ||
            !isAssistantRequestCurrent(requestToken)
        ) {
            DebugDiagnosticLog.event(
                "SAVED_ROUTINE_STALE",
                "phase=LOAD_DELIVERY\nreason=NEWER_INTERACTION"
            )
            return
        }
        if (routine == null) {
            savedRoutineInteractionController.clear()
            assistantSession.speak(
                "That saved routine is no longer available.",
                listenAgain = true
            )
            return
        }
        DebugDiagnosticLog.event(
            "SAVED_ROUTINE_LOAD",
            "loadedStepCount=${routine.steps.size}"
        )
        when (savedRoutineInteractionController.intendedAction) {
            SavedRoutineAction.READ_DETAILS -> {
                savedRoutineInteractionController.clear()
                val speech = renderSavedRoutineDetails(routine)
                assistantSession.speak(
                    speech ?: "That saved routine is incomplete or corrupted and cannot be read safely.",
                    listenAgain = true
                )
            }
            SavedRoutineAction.RUN -> {
                val datePhrase = savedRoutineInteractionController.suppliedDatePhrase
                savedRoutineInteractionController.clear()
                val update = routineDraftController.startFromSavedRoutine(routine, datePhrase)
                handleRoutineDraftUpdate(
                    update = update,
                    invalidSpeech =
                        "That saved routine is incomplete or corrupted, so I cannot run it safely.",
                    requestToken = requestToken,
                    invalidDateSpeech =
                        "I could not understand that date. Please provide a valid date for the saved routine.",
                    invalidTimeSpeech =
                        "That saved routine has an invalid stored step time, so I cannot run it safely."
                )
            }
            SavedRoutineAction.DELETE -> {
                if (savedRoutineInteractionController.beginDeleteConfirmation(
                        interactionGeneration,
                        routineId
                    )
                ) {
                    DebugDiagnosticLog.event(
                        "SAVED_ROUTINE_DELETE",
                        "phase=CONFIRMATION_REQUESTED"
                    )
                    assistantSession.speak(
                        "Do you want me to delete the saved routine ${routine.routine.title}? " +
                            "Tasks already created from it will remain unchanged.",
                        listenAgain = true
                    )
                }
            }
            else -> {
                savedRoutineInteractionController.clear()
                assistantSession.speak(
                    "Please repeat the saved-routine request.",
                    listenAgain = true
                )
            }
        }
    }

    private fun deleteConfirmedSavedRoutine() {
        val routineId = savedRoutineInteractionController.claimDelete()
        if (routineId == null) {
            assistantSession.speak(
                "That deletion is already being processed.",
                listenAgain = false
            )
            return
        }
        val generation = savedRoutineInteractionController.snapshot().generation
        DebugDiagnosticLog.event(
            "SAVED_ROUTINE_DELETE",
            "phase=COMMITTED\nstate=${savedRoutineInteractionController.state.name}"
        )
        lifecycleScope.launch {
            val deleted = try {
                withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(this@HomeActivity)
                        .routineDao()
                        .deleteRoutineAndSteps(routineId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!savedRoutineInteractionController.completeDelete(generation)) {
                DebugDiagnosticLog.event(
                    "SAVED_ROUTINE_STALE",
                    "phase=DELETE_RESULT\nreason=COMMITTED_STATE_CHANGED"
                )
                return@launch
            }
            DebugDiagnosticLog.event(
                "SAVED_ROUTINE_DELETE",
                "phase=COMPLETE\nsuccess=$deleted"
            )
            if (assistantSession.assistantSessionActive) {
                assistantSession.speak(
                    if (deleted) {
                        "The saved routine was deleted. Tasks already created from it remain unchanged."
                    } else {
                        "I could not delete that saved routine. Nothing was changed."
                    },
                    listenAgain = false
                )
            }
        }
    }

    private fun renderSavedRoutineDetails(routine: RoutineWithSteps): String? {
        val steps = routine.steps.sortedBy { it.stepOrder }
        if (routine.routine.title.isBlank() ||
            steps.size !in 2..5 ||
            steps.map { it.stepOrder } != steps.indices.toList() ||
            steps.any { it.title.isBlank() || !isStoredRoutineTimeValid(it.dueTime) }
        ) {
            return null
        }
        val details = steps.mapIndexed { index, step ->
            "${savedRoutineOrdinal(index)}, ${step.title} at ${step.dueTime.replace(":00 ", " ")}."
        }.joinToString(" ")
        return "${routine.routine.title} has ${steps.size} steps. $details"
    }

    private fun isStoredRoutineTimeValid(value: String): Boolean {
        val formatter = SimpleDateFormat("h:mm a", Locale.UK).apply {
            isLenient = false
        }
        val normalized = value.trim()
        val position = java.text.ParsePosition(0)
        return formatter.parse(normalized, position) != null &&
            position.index == normalized.length
    }

    private fun savedRoutineOrdinal(index: Int): String =
        listOf("First", "Second", "Third", "Fourth", "Fifth").getOrElse(index) {
            "${index + 1}."
        }

    private fun isSavedRoutineConfirmation(normalized: String): Boolean =
        BoundedConfirmationPolicy.resolve(normalized).result ==
            BoundedConfirmationResult.AFFIRM || normalized == "delete it"

    private fun isSavedRoutineRejection(normalized: String): Boolean =
        BoundedConfirmationPolicy.resolve(normalized).result in setOf(
            BoundedConfirmationResult.REJECT,
            BoundedConfirmationResult.CANCEL
        )

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
        if (stateBefore == RoutineDraftState.NONE) return false
        val localMove = routineFollowUpSemanticOrchestrator.proposeLocal(normalized)
        if (stateBefore == RoutineDraftState.SAVING) {
            logRoutineMoveLocal(stateBefore, localMove, "NOT_APPLICABLE_SAVING")
            logRoutineFollowUpDebug(stateBefore, normalized, localMove)
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

        logRoutineFollowUpDebug(stateBefore, normalized, localMove)
        if (localMove is RoutineFollowUpMove.Cancel ||
            localMove is RoutineFollowUpMove.Reject
        ) {
            logRoutineMoveLocal(stateBefore, localMove, "CONTROL_ACCEPTED")
            cancelPendingRoutine()
            logRoutineFollowUpResult("CANCELLED")
            return true
        }

        val immediate = routineFollowUpSemanticOrchestrator.resolveImmediate(
            localMove = localMove,
            state = stateBefore,
            draft = routineDraftController.draft
        )
        return when (stateBefore) {
            RoutineDraftState.NONE -> false
            RoutineDraftState.EXTRACTING -> {
                if (immediate != null) {
                    logRoutineMoveLocal(stateBefore, localMove, "CONTROL_ACCEPTED")
                    applyResolvedRoutineMove(immediate.move, requestToken)
                } else {
                    logRoutineMoveLocal(stateBefore, localMove, "SEMANTIC_REQUIRED")
                    launchRoutineSemanticFollowUp(
                        normalized,
                        requestToken,
                        stateBefore,
                        localMove
                    )
                }
                true
            }
            RoutineDraftState.COLLECTING_SHARED_DATE -> {
                if (immediate != null) {
                    logRoutineMoveLocal(stateBefore, localMove, "CONTROL_ACCEPTED")
                    applyResolvedRoutineMove(immediate.move, requestToken)
                    return true
                }
                val rawUpdate = routineDraftController.provideSharedDate(normalized)
                val shouldUseSemantic =
                    RoutineFollowUpSemanticFallbackPolicy.afterRawSharedDate(
                        rawUpdate,
                        routineDraftController.state
                    )
                if (shouldUseSemantic) {
                    logRoutineMoveLocal(stateBefore, localMove, "INVALID_DATE")
                    launchRoutineSemanticFollowUp(
                        normalized,
                        requestToken,
                        stateBefore,
                        localMove
                    )
                } else {
                    logRoutineMoveLocal(stateBefore, localMove, "RAW_DATE_ACCEPTED")
                    val outcome = handleRoutineDraftUpdate(
                        rawUpdate,
                        invalidSpeech = sharedDateInvalidSpeech(),
                        requestToken = requestToken
                    )
                    logRoutineFollowUpResult(outcome.result, outcome.issue)
                }
                true
            }
            RoutineDraftState.COLLECTING_STEP_TIME -> {
                if (immediate != null) {
                    logRoutineMoveLocal(stateBefore, localMove, "CONTROL_ACCEPTED")
                    applyResolvedRoutineMove(immediate.move, requestToken)
                    return true
                }
                val rawUpdate = routineDraftController.provideNextStepTime(normalized)
                val shouldUseSemantic =
                    RoutineFollowUpSemanticFallbackPolicy.afterRawStepTime(
                        rawUpdate,
                        routineDraftController.state
                    )
                if (shouldUseSemantic) {
                    logRoutineMoveLocal(stateBefore, localMove, "INVALID_TIME")
                    launchRoutineSemanticFollowUp(
                        normalized,
                        requestToken,
                        stateBefore,
                        localMove
                    )
                } else {
                    logRoutineMoveLocal(stateBefore, localMove, "RAW_TIME_ACCEPTED")
                    val outcome = handleRoutineDraftUpdate(
                        rawUpdate,
                        invalidSpeech = routineStepTimeClarification,
                        requestToken = requestToken
                    )
                    logRoutineFollowUpResult(outcome.result, outcome.issue)
                }
                true
            }
            RoutineDraftState.WAITING_FOR_CONFIRMATION -> {
                if (immediate != null) {
                    logRoutineMoveLocal(stateBefore, localMove, "CONTROL_ACCEPTED")
                    applyResolvedRoutineMove(immediate.move, requestToken)
                } else {
                    logRoutineMoveLocal(stateBefore, localMove, "SEMANTIC_REQUIRED")
                    launchRoutineSemanticFollowUp(
                        normalized,
                        requestToken,
                        stateBefore,
                        localMove
                    )
                }
                true
            }
            RoutineDraftState.SAVING -> true
        }
    }

    private fun launchRoutineSemanticFollowUp(
        normalized: String,
        requestToken: AssistantRequestToken,
        capturedState: RoutineDraftState,
        localMove: RoutineFollowUpMove
    ) {
        if (!isAssistantRequestCurrent(requestToken)) return
        val context = RoutineFollowUpAgentContext.capture(
            state = capturedState,
            draft = routineDraftController.draft,
            localMove = localMove
        )
        val capturedRevision = context.revision
        lifecycleScope.launch {
            if (!isAssistantRequestCurrent(requestToken)) return@launch
            val resolution = try {
                routineFollowUpSemanticOrchestrator.resolveSemantic(
                    userText = normalized,
                    context = context,
                    localMove = localMove
                )
            } catch (e: CancellationException) {
                throw e
            }
            val requestCurrent = isAssistantRequestCurrent(requestToken)
            val currentState = routineDraftController.state
            val currentRevision = routineDraftController.draft?.revision ?: 0L
            val deliver = RoutineFollowUpDeliveryGuard.shouldDeliver(
                capturedState = capturedState,
                currentState = currentState,
                capturedRevision = capturedRevision,
                currentRevision = currentRevision,
                requestCurrent = requestCurrent
            )
            DebugDiagnosticLog.event(
                "ROUTINE_MOVE_DELIVERY_GUARD",
                "capturedState=${capturedState.name}\n" +
                    "currentState=${currentState.name}\n" +
                    "capturedRevision=$capturedRevision\n" +
                    "currentRevision=$currentRevision\n" +
                    "requestCurrent=$requestCurrent\n" +
                    "action=${if (deliver) "DELIVER" else "DROP_STALE"}"
            )
            if (!deliver) return@launch
            applyResolvedRoutineMove(resolution.move, requestToken)
        }
    }

    private fun applyResolvedRoutineMove(
        move: RoutineFollowUpMove,
        requestToken: AssistantRequestToken
    ) {
        if (!isAssistantRequestCurrent(requestToken)) return
        val outcome = when (move) {
            RoutineFollowUpMove.Confirm -> {
                if (routineDraftController.state == RoutineDraftState.WAITING_FOR_CONFIRMATION) {
                    savePendingRoutine()
                    RoutineFollowUpOutcome("SAVING")
                } else {
                    speakStateAppropriateRoutineClarification()
                    RoutineFollowUpOutcome("UNKNOWN")
                }
            }
            RoutineFollowUpMove.Reject,
            RoutineFollowUpMove.Cancel -> {
                cancelPendingRoutine()
                RoutineFollowUpOutcome("CANCELLED")
            }
            RoutineFollowUpMove.Repeat -> {
                val proposal = routineDraftController.authoritativeProposal
                if (proposal != null &&
                    routineDraftController.state == RoutineDraftState.WAITING_FOR_CONFIRMATION
                ) {
                    speakRoutineResponse(
                        RoutineResponseKind.PROPOSAL,
                        proposal,
                        listenAgain = true
                    )
                    RoutineFollowUpOutcome("ACCEPTED")
                } else {
                    speakStateAppropriateRoutineClarification()
                    RoutineFollowUpOutcome("UNKNOWN")
                }
            }
            is RoutineFollowUpMove.ProvideSharedDate -> handleRoutineDraftUpdate(
                routineDraftController.provideSharedDate(move.value),
                invalidSpeech = sharedDateInvalidSpeech(),
                requestToken = requestToken
            )
            is RoutineFollowUpMove.ProvideStepTime -> handleRoutineDraftUpdate(
                routineDraftController.provideNextStepTime(move.value),
                invalidSpeech = routineStepTimeClarification,
                requestToken = requestToken
            )
            is RoutineFollowUpMove.ChangeStepTime -> handleRoutineDraftUpdate(
                routineDraftController.changeStepTime(move.stepIndex, move.value),
                invalidSpeech =
                    "Please select a valid routine step and give one exact time.",
                requestToken = requestToken
            )
            is RoutineFollowUpMove.ChangeStepTitle -> handleRoutineDraftUpdate(
                routineDraftController.changeStepTitle(move.stepIndex, move.value),
                invalidSpeech =
                    "Please select a valid routine step and give a non-empty title.",
                requestToken = requestToken
            )
            is RoutineFollowUpMove.ChangeSharedDate -> handleRoutineDraftUpdate(
                routineDraftController.changeSharedDate(move.value),
                invalidSpeech = "Please provide one exact future date for the routine.",
                requestToken = requestToken
            )
            RoutineFollowUpMove.StructuralChange -> {
                speakRoutineResponse(
                    RoutineResponseKind.REVISION_HELP,
                    "To add or remove routine steps, cancel this draft and start a new routine request.",
                    listenAgain = true
                )
                RoutineFollowUpOutcome("REJECTED")
            }
            RoutineFollowUpMove.RequestHelp,
            RoutineFollowUpMove.Unknown -> {
                speakStateAppropriateRoutineClarification()
                RoutineFollowUpOutcome("UNKNOWN")
            }
        }
        logRoutineFollowUpResult(outcome.result, outcome.issue)
    }

    private fun speakStateAppropriateRoutineClarification() {
        val (kind, speech) = when (routineDraftController.state) {
            RoutineDraftState.EXTRACTING ->
                RoutineResponseKind.REVISION_HELP to
                    "I am still preparing that routine. You can cancel it if needed."
            RoutineDraftState.COLLECTING_SHARED_DATE ->
                RoutineResponseKind.INVALID_DATE to sharedDateInvalidSpeech()
            RoutineDraftState.COLLECTING_STEP_TIME ->
                RoutineResponseKind.INVALID_TIME to routineStepTimeClarification
            RoutineDraftState.WAITING_FOR_CONFIRMATION ->
                RoutineResponseKind.REVISION_HELP to
                    if (routineDraftController.draft?.origin == RoutineDraftOrigin.NEW_ROUTINE) {
                        "Say yes to save the routine for reuse and create this occurrence, no to reject it, repeat the routine, or change one step."
                    } else {
                        "Say yes to create this occurrence, no to reject it, repeat the routine, or change one step."
                    }
            RoutineDraftState.SAVING ->
                RoutineResponseKind.ALREADY_SAVING to
                    "The confirmed routine is already being saved."
            RoutineDraftState.NONE ->
                RoutineResponseKind.REVISION_HELP to
                    "Please start the routine request again."
        }
        speakRoutineResponse(kind, speech, listenAgain = true)
    }

    private fun sharedDateInvalidSpeech(): String {
        val hasConstrainedDate = routineDraftController.draft?.steps.orEmpty().any {
            it.resolvedDate == null &&
                it.dateClassification ==
                com.example.myapplication.ai.routine.RoutineDateClassification.CONSTRAINED
        }
        return if (hasConstrainedDate) {
            "Please provide one exact date that satisfies the routine's date constraints."
        } else {
            "Please provide one exact future date, such as 4 August 2026."
        }
    }

    private fun logRoutineMoveLocal(
        state: RoutineDraftState,
        localMove: RoutineFollowUpMove,
        rawValidationResult: String
    ) {
        DebugDiagnosticLog.event(
            "ROUTINE_MOVE_LOCAL",
            "state=${state.name}\n" +
                "localMove=${routineMoveName(localMove)}\n" +
                "rawValidationResult=$rawValidationResult"
        )
    }

    private fun handleRoutineDraftUpdate(
        update: RoutineDraftUpdate,
        invalidSpeech: String,
        requestToken: AssistantRequestToken,
        invalidDateSpeech: String? = null,
        invalidTimeSpeech: String? = null
    ): RoutineFollowUpOutcome {
        if (!isAssistantRequestCurrent(requestToken)) return RoutineFollowUpOutcome("UNKNOWN")
        return when (update) {
            is RoutineDraftUpdate.Ask -> {
                logRoutineDraftState()
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
                    RoutineDraftIssue.INVALID_DATE ->
                        invalidDateSpeech ?: invalidSpeech
                    RoutineDraftIssue.INVALID_TIME ->
                        invalidTimeSpeech ?: invalidSpeech
                    RoutineDraftIssue.PAST_SCHEDULE ->
                        "Please provide a future exact date and time."
                    RoutineDraftIssue.INVALID_STATE -> invalidSpeech
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
            "routineSavedCount=0 taskCount=$count insertedCount=0 " +
                "reminderSuccessCount=0 result=CANCELLED"
        )
        speakRoutineResponse(
            RoutineResponseKind.CANCELLED,
            "Cancelled. Zero new routines were saved, zero new tasks were inserted, and zero reminders were scheduled.",
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
            val routineDao = AppDatabase.getInstance(this@HomeActivity).routineDao()
            val coordinator = RoutinePersistenceCoordinator(
                store = object : RoutinePersistenceStore {
                    override suspend fun insertNewRoutineWithFirstOccurrence(
                        routine: com.example.myapplication.data.RoutineEntity,
                        steps: List<com.example.myapplication.data.RoutineStepEntity>,
                        tasks: List<TaskEntity>
                    ): RoutineOccurrenceInsertResult =
                        routineDao.insertRoutineWithFirstOccurrence(routine, steps, tasks)

                    override suspend fun insertSavedRoutineOccurrence(
                        tasks: List<TaskEntity>
                    ): RoutineOccurrenceInsertResult =
                        routineDao.insertSavedRoutineOccurrence(tasks)
                },
                reminderScheduler = RoutineReminderScheduler { task ->
                    ReminderHelper.scheduleReminderFromTask(this@HomeActivity, task)
                }
            )
            val result = coordinator.persist(
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
                speakRoutineTerminalResponse(
                    RoutineResponseKind.SAVE_RESULT,
                    RoutineResultSpeechRenderer.render(result)
                )
            }
        }
    }

    private fun speakRoutineTerminalResponse(
        kind: RoutineResponseKind,
        text: String
    ) {
        DebugDiagnosticLog.longEvent(
            "ROUTINE_RESPONSE_DEBUG",
            "kind=${kind.name}\ntext=$text"
        )
        assistantSession.speakThenStop(text, dismissPanel = true)
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
            is RoutineFollowUpMove.ProvideSharedDate ->
                Triple("PROVIDE_SHARED_DATE", "", move.value)
            is RoutineFollowUpMove.ProvideStepTime ->
                Triple("PROVIDE_STEP_TIME", "", move.value)
            is RoutineFollowUpMove.ChangeStepTime ->
                Triple("CHANGE_STEP_TIME", (move.stepIndex + 1).toString(), move.value)
            is RoutineFollowUpMove.ChangeStepTitle ->
                Triple("CHANGE_STEP_TITLE", (move.stepIndex + 1).toString(), move.value)
            is RoutineFollowUpMove.ChangeSharedDate ->
                Triple("CHANGE_SHARED_DATE", "", move.value)
            RoutineFollowUpMove.StructuralChange -> Triple("STRUCTURAL_CHANGE", "", "")
            RoutineFollowUpMove.RequestHelp -> Triple("REQUEST_HELP", "", "")
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

    private fun routineMoveName(move: RoutineFollowUpMove): String = when (move) {
        RoutineFollowUpMove.Confirm -> "CONFIRM"
        RoutineFollowUpMove.Reject -> "REJECT"
        RoutineFollowUpMove.Cancel -> "CANCEL"
        RoutineFollowUpMove.Repeat -> "REPEAT"
        is RoutineFollowUpMove.ProvideSharedDate -> "PROVIDE_SHARED_DATE"
        is RoutineFollowUpMove.ProvideStepTime -> "PROVIDE_STEP_TIME"
        is RoutineFollowUpMove.ChangeSharedDate -> "CHANGE_SHARED_DATE"
        is RoutineFollowUpMove.ChangeStepTime -> "CHANGE_STEP_TIME"
        is RoutineFollowUpMove.ChangeStepTitle -> "CHANGE_STEP_TITLE"
        RoutineFollowUpMove.StructuralChange -> "STRUCTURAL_CHANGE"
        RoutineFollowUpMove.RequestHelp -> "REQUEST_HELP"
        RoutineFollowUpMove.Unknown -> "UNKNOWN"
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
                    isBoundedCreateFollowUpControl(normalized) -> {
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

            HomeFollowUpContext.AFTER_CONTEXT_SUGGESTION -> {
                if (isSimpleFollowUpEndCommand(normalized)) {
                    endAssistantConversation()
                    true
                } else {
                    false
                }
            }

            HomeFollowUpContext.QUERY_COUNT,
            HomeFollowUpContext.QUERY_PAGE -> false
            HomeFollowUpContext.CONTEXT_ACTION_TARGET_CLARIFICATION -> false
            HomeFollowUpContext.CONTEXT_ACTION_CHANGE_CLARIFICATION -> false
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
                    fallbackSpeech = "Delete ${task.title}? Please say yes or no."
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

            val authoritativeTitle = withContext(Dispatchers.IO) {
                val authoritativeTask = dao.getById(taskId) ?: return@withContext null
                dao.deleteTaskAndSubtasks(taskId)
                authoritativeTask.title
            }

            if (authoritativeTitle == null) {
                clearPendingDeleteState()
                clearConversationSessionContext()
                homeFollowUpContext = HomeFollowUpContext.NONE
                assistantSession.speak("That task is no longer available.", listenAgain = false)
                return@launch
            }

            ReminderHelper.cancelReminder(this@HomeActivity, taskId)
            refreshOverview()

            clearPendingDeleteState()
            clearConversationSessionContext()
            homeFollowUpContext = HomeFollowUpContext.NONE

            speakObservation(ExecutionObservation(ExecutionOperation.DELETE_TASK, ExecutionOutcome.SUCCESS, taskTitle = authoritativeTitle, listenAgain = false, fallbackSpeech = responseManager.deleteSuccess(authoritativeTitle)))
        }
    }
    private suspend fun beginBreakdownTargetResolution(
        title: String,
        plan: List<String>,
        originalRequest: String,
        dateText: String?,
        timeText: String?,
        targetPreference: BreakdownTargetPreference,
        requestToken: AssistantRequestToken
    ) {
        if (!isAssistantRequestCurrent(requestToken)) return
        val initial = breakdownDraftController.beginDraft(
            parentTitle = title,
            proposedSubtasks = plan,
            originalRequest = originalRequest,
            dateText = dateText,
            timeText = timeText
        )
        if (initial is BreakdownDraftUpdate.Rejected) {
            homeFollowUpContext = HomeFollowUpContext.NONE
            speakBreakdownValidationFailure()
            return
        }
        val draft = (initial as BreakdownDraftUpdate.Resolving).draft
        if (targetPreference == BreakdownTargetPreference.NEW_ROOT) {
            logBreakdownTargetResolution(0, false, BreakdownDraftMode.NEW_ROOT)
            when (val update = breakdownDraftController.applyNewRoot(draft.generation)) {
                is BreakdownDraftUpdate.Review -> presentBreakdownReview(update.draft)
                else -> handleBreakdownDraftFailure(update)
            }
            return
        }
        val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
        val activeRoots = withContext(Dispatchers.IO) { dao.getRootActiveTasks() }
        if (!isAssistantRequestCurrent(requestToken) ||
            !breakdownDraftController.isCurrent(draft.generation, draft.revision)
        ) {
            return
        }

        when (
            val resolution = BreakdownTargetResolver.resolve(
                targetPreference,
                title,
                activeRoots
            )
        ) {
            is BreakdownTargetResolution.ExistingRoot -> {
                logBreakdownTargetResolution(1, false, BreakdownDraftMode.EXISTING_ROOT)
                val hasSubtasks = withContext(Dispatchers.IO) {
                    dao.getSubtasks(resolution.task.id).isNotEmpty()
                }
                if (!isAssistantRequestCurrent(requestToken)) return
                handleExistingBreakdownTarget(
                    generation = draft.generation,
                    parent = resolution.task,
                    hasSubtasks = hasSubtasks
                )
            }
            is BreakdownTargetResolution.Ambiguous -> {
                logBreakdownTargetResolution(
                    resolution.matchCount,
                    true,
                    selectedMode = null
                )
                when (
                    val update = breakdownDraftController.applyAmbiguousTargets(
                        draft.generation,
                        resolution.tasks
                    )
                ) {
                    is BreakdownDraftUpdate.ChoosingTarget ->
                        askBreakdownTargetClarification(update.choices)
                    else -> handleBreakdownDraftFailure(update)
                }
            }
            BreakdownTargetResolution.NewRoot -> {
                logBreakdownTargetResolution(0, false, BreakdownDraftMode.NEW_ROOT)
                when (val update = breakdownDraftController.applyNewRoot(draft.generation)) {
                    is BreakdownDraftUpdate.Review -> presentBreakdownReview(update.draft)
                    else -> handleBreakdownDraftFailure(update)
                }
            }
        }
    }

    private fun handleExistingBreakdownTarget(
        generation: Long,
        parent: TaskEntity,
        hasSubtasks: Boolean
    ) {
        when (
            val update = breakdownDraftController.applyExistingRoot(
                generation,
                parent,
                hasSubtasks
            )
        ) {
            is BreakdownDraftUpdate.Review -> presentBreakdownReview(update.draft)
            BreakdownDraftUpdate.AlreadyHasSubtasks -> {
                homeFollowUpContext = HomeFollowUpContext.NONE
                assistantSession.speakThenStop(
                    "${parent.title} already has subtasks, so I did not add or change anything."
                )
            }
            else -> handleBreakdownDraftFailure(update)
        }
    }

    private fun askBreakdownTargetClarification(choices: List<String>) {
        homeFollowUpContext = HomeFollowUpContext.BREAKDOWN_CONFIRMATION
        lifecycleScope.launch {
            speakObservation(
                ExecutionObservation(
                    operation = ExecutionOperation.BREAKDOWN_TASK,
                    outcome = ExecutionOutcome.AMBIGUOUS,
                    taskCount = choices.size,
                    choices = choices,
                    requiredInput = RequiredInput.TASK_CHOICE,
                    allowedUserMoves = listOf(
                        AllowedUserMove.SELECT_OPTION,
                        AllowedUserMove.CANCEL,
                        AllowedUserMove.REQUEST_HELP
                    ),
                    listenAgain = true,
                    fallbackSpeech =
                        "I found more than one matching task: " +
                            choices.mapIndexed { index, value ->
                                "${index + 1}, $value"
                            }.joinToString(". ") +
                            ". Which one should I use?",
                    fallbackHint = "Say first, second, or the unique task title."
                )
            )
        }
    }

    private fun presentBreakdownReview(draft: PendingBreakdownDraft) {
        homeFollowUpContext = HomeFollowUpContext.BREAKDOWN_CONFIRMATION
        val fallback = buildBreakdownProposalSpeech(draft)
        lifecycleScope.launch {
            speakObservation(
                ExecutionObservation(
                    operation = ExecutionOperation.BREAKDOWN_TASK,
                    outcome = ExecutionOutcome.NEEDS_CONFIRMATION,
                    taskTitle = draft.parentTitle,
                    planItems = draft.proposedSubtasks,
                    requiredInput = RequiredInput.CONFIRMATION,
                    allowedUserMoves = listOf(
                        AllowedUserMove.CONFIRM,
                        AllowedUserMove.REJECT,
                        AllowedUserMove.CANCEL,
                        AllowedUserMove.CHANGE_FIELD
                    ),
                    listenAgain = true,
                    fallbackSpeech = fallback,
                    fallbackHint =
                        "Say yes to continue, no to cancel, or describe how to revise the plan."
                )
            )
        }
    }

    private fun buildBreakdownProposalSpeech(draft: PendingBreakdownDraft): String {
        val intro = when (draft.mode) {
            BreakdownDraftMode.EXISTING_ROOT ->
                "I found your existing task ${draft.parentTitle}. I propose adding these subtasks."
            BreakdownDraftMode.NEW_ROOT ->
                "I propose creating ${draft.parentTitle} with these subtasks."
            null -> "I prepared a breakdown for ${draft.parentTitle}."
        }
        val planSpeech = draft.proposedSubtasks.mapIndexed { index, item ->
            "${index + 1}. $item."
        }.joinToString(" ")
        val schedule = if (
            draft.mode == BreakdownDraftMode.NEW_ROOT &&
            !draft.dateText.isNullOrBlank() &&
            !draft.timeText.isNullOrBlank()
        ) {
            " The proposed schedule is ${draft.dateText} at ${draft.timeText}."
        } else {
            ""
        }
        val question = if (draft.mode == BreakdownDraftMode.EXISTING_ROOT) {
            "Do you want me to add these subtasks to the existing task?"
        } else {
            "Do you want me to continue with this task and subtask plan?"
        }
        return "$intro $planSpeech$schedule $question"
    }

    private fun clearPendingBreakdownState() {
        breakdownDraftController.clear()
    }

    private fun handleBreakdownFollowUp(
        normalized: String,
        requestToken: AssistantRequestToken
    ): Boolean = when (breakdownDraftController.state) {
        BreakdownDraftState.NONE -> false
        BreakdownDraftState.RESOLVING_TARGET -> {
            when (BreakdownControlInterpreter.interpret(normalized)) {
                BreakdownFollowUpMove.CANCEL,
                BreakdownFollowUpMove.REJECT -> {
                    cancelPendingBreakdown()
                    true
                }
                else -> {
                    breakdownDraftController.clear()
                    homeFollowUpContext = HomeFollowUpContext.NONE
                    false
                }
            }
        }
        BreakdownDraftState.CHOOSING_TARGET -> {
            handleBreakdownTargetChoice(normalized, requestToken)
            true
        }
        BreakdownDraftState.WAITING_FOR_CONFIRMATION ->
            handleBreakdownConfirmationFollowUp(normalized, requestToken)
        BreakdownDraftState.COLLECTING_SCHEDULE ->
            handleBreakdownScheduleFollowUp(normalized)
        BreakdownDraftState.SAVING -> {
            assistantSession.speak(
                "The confirmed task breakdown is already being saved.",
                listenAgain = false
            )
            true
        }
    }

    private fun handleBreakdownTargetChoice(
        normalized: String,
        requestToken: AssistantRequestToken
    ) {
        when (BreakdownControlInterpreter.interpret(normalized)) {
            BreakdownFollowUpMove.CANCEL,
            BreakdownFollowUpMove.REJECT -> {
                cancelPendingBreakdown()
                return
            }
            else -> Unit
        }
        val captured = breakdownDraftController.draft ?: return
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
            val activeRoots = withContext(Dispatchers.IO) { dao.getRootActiveTasks() }
            if (!isAssistantRequestCurrent(requestToken) ||
                !breakdownDraftController.isCurrent(
                    captured.generation,
                    captured.revision
                )
            ) {
                return@launch
            }
            val selected = breakdownDraftController.resolveTargetChoice(
                normalized,
                activeRoots
            )
            if (selected == null) {
                assistantSession.speakThenListenAgain(
                    "Please choose the first task, the second task, or say one unique task title."
                )
                return@launch
            }
            val hasSubtasks = withContext(Dispatchers.IO) {
                dao.getSubtasks(selected.id).isNotEmpty()
            }
            if (!isAssistantRequestCurrent(requestToken)) return@launch
            logBreakdownTargetResolution(1, false, BreakdownDraftMode.EXISTING_ROOT)
            handleExistingBreakdownTarget(
                captured.generation,
                selected,
                hasSubtasks
            )
        }
    }

    private fun handleBreakdownConfirmationFollowUp(
        normalized: String,
        requestToken: AssistantRequestToken
    ): Boolean {
        return when (BreakdownControlInterpreter.interpret(normalized)) {
            BreakdownFollowUpMove.CONFIRM -> {
                proceedAfterBreakdownApproval()
                true
            }
            BreakdownFollowUpMove.REJECT,
            BreakdownFollowUpMove.CANCEL -> {
                cancelPendingBreakdown()
                true
            }
            else -> {
                interpretBreakdownFeedbackSemantically(normalized, requestToken)
                true
            }
        }
    }

    private fun interpretBreakdownFeedbackSemantically(
        feedback: String,
        requestToken: AssistantRequestToken
    ) {
        val captured = breakdownDraftController.draft ?: return
        val context = BreakdownFollowUpContext.capture(
            breakdownDraftController.state,
            captured
        )
        lifecycleScope.launch {
            val decision = try {
                breakdownFollowUpSemanticOrchestrator.interpret(feedback, context)
            } catch (e: BreakdownFollowUpException) {
                if (isAssistantRequestCurrent(requestToken) &&
                    breakdownDraftController.isCurrent(
                        captured.generation,
                        captured.revision
                    )
                ) {
                    assistantSession.speakThenListenAgain(
                        "I could not interpret that safely. Please confirm, cancel, or describe the plan change another way."
                    )
                }
                return@launch
            }
            if (!isAssistantRequestCurrent(requestToken) ||
                !breakdownDraftController.isCurrent(
                    captured.generation,
                    captured.revision
                )
            ) {
                DebugDiagnosticLog.event("BREAKDOWN_SAVE_RESULT", "result=STALE")
                return@launch
            }

            when (decision.move) {
                BreakdownFollowUpMove.CONFIRM -> proceedAfterBreakdownApproval()
                BreakdownFollowUpMove.REJECT,
                BreakdownFollowUpMove.CANCEL -> cancelPendingBreakdown()
                BreakdownFollowUpMove.REVISE -> {
                    when (
                        val update = breakdownDraftController.applyRevision(
                            expectedGeneration = captured.generation,
                            expectedRevision = captured.revision,
                            proposedSubtasks = decision.plan
                        )
                    ) {
                        is BreakdownDraftUpdate.Review ->
                            presentBreakdownReview(update.draft)
                        is BreakdownDraftUpdate.Rejected ->
                            assistantSession.speakThenListenAgain(
                                "That revised plan was not structurally safe. Please describe a different revision."
                            )
                        else -> Unit
                    }
                }
                BreakdownFollowUpMove.UNKNOWN ->
                    assistantSession.speakThenListenAgain(
                        "Please confirm, cancel, or tell me how the proposed subtasks should change."
                    )
            }
        }
    }

    private fun cancelPendingBreakdown() {
        val title = breakdownDraftController.draft?.parentTitle
        if (!breakdownDraftController.clear()) {
            assistantSession.speak(
                "The confirmed task breakdown is already being saved.",
                listenAgain = false
            )
            return
        }
        homeFollowUpContext = HomeFollowUpContext.NONE
        assistantSession.speakThenStop(
            if (title.isNullOrBlank()) {
                "Okay, I did not create those subtasks."
            } else {
                "Okay, I did not create or add subtasks for $title."
            }
        )
    }

    private fun proceedAfterBreakdownApproval() {
        val draft = breakdownDraftController.draft ?: return
        if (draft.mode == BreakdownDraftMode.EXISTING_ROOT) {
            savePendingBreakdown()
            return
        }

        val resolution = temporalQueryResolver.resolve(
            draft.dateText,
            draft.timeText,
            ""
        )
        when (val policy = TemporalActionPolicy.evaluate(
            resolution,
            TemporalUseCase.BREAKDOWN
        )) {
            is TemporalPolicyResult.Ready -> createPendingBreakdownIfFuture(
                requireNotNull(resolution.startDateInclusive),
                ScheduleTextParser.formatTime(
                    requireNotNull(resolution.startMinuteInclusive) / 60,
                    requireNotNull(resolution.startMinuteInclusive) % 60
                )
            )
            is TemporalPolicyResult.InvalidPastSchedule ->
                enterBreakdownFutureCorrection(resolution)
            is TemporalPolicyResult.Unresolved -> {
                val replacement = TemporalQueryWindow(
                    TemporalResolutionStatus.NONE,
                    spokenLabel = "a future schedule"
                )
                beginOrUpdateBreakdownScheduleCollection(
                    PendingTemporalClarification(
                        original = replacement,
                        needsExactDate = true,
                        needsExactTime = true,
                        replacingOriginalConstraint = true
                    )
                )
                promptNextBreakdownTemporalClarification()
            }
            else -> {
                val needsDate =
                    policy is TemporalPolicyResult.NeedsExactDate ||
                        policy is TemporalPolicyResult.NeedsExactDateAndTime
                val needsTime =
                    policy is TemporalPolicyResult.NeedsExactTime ||
                        policy is TemporalPolicyResult.NeedsExactDateAndTime
                beginOrUpdateBreakdownScheduleCollection(
                    PendingTemporalClarification(
                        resolution,
                        needsExactDate = needsDate,
                        needsExactTime = needsTime
                    )
                )
                promptNextBreakdownTemporalClarification()
            }
        }
    }

    private fun beginOrUpdateBreakdownScheduleCollection(
        clarification: PendingTemporalClarification
    ) {
        if (breakdownDraftController.state == BreakdownDraftState.WAITING_FOR_CONFIRMATION) {
            breakdownDraftController.startScheduleCollection(clarification)
        } else {
            breakdownDraftController.updateTemporalClarification(clarification)
        }
        homeFollowUpContext = HomeFollowUpContext.BREAKDOWN_SCHEDULE_COLLECTION
    }

    private fun promptNextBreakdownTemporalClarification() {
        val pending = breakdownDraftController.draft?.temporalClarification ?: return
        val prompt = when {
            pending.needsExactDate && pending.exactDate == null ->
                pending.original.originalDatePhrase
                    .ifBlank { pending.original.spokenLabel }
                    .trim()
                    .takeIf(String::isNotEmpty)
                    ?.let { "Which exact date within $it should I use?" }
                    ?: "Which exact date should I use?"
            pending.needsExactTime && pending.exactMinute == null ->
                pending.original.originalTimePhrase
                    .ifBlank { pending.original.spokenLabel }
                    .trim()
                    .takeIf(String::isNotEmpty)
                    ?.let { "What exact time within $it should I use?" }
                    ?: "What exact time should I use?"
            else -> null
        }
        if (prompt != null) {
            assistantSession.speakThenListenAgain(prompt)
        } else {
            val date = pending.exactDate ?: pending.original.startDateInclusive
            val minute = pending.exactMinute ?: pending.original.startMinuteInclusive
            if (date != null && minute != null) {
                breakdownDraftController.updateTemporalClarification(null)
                createPendingBreakdownIfFuture(
                    date,
                    ScheduleTextParser.formatTime(minute / 60, minute % 60)
                )
            }
        }
    }

    private fun handleBreakdownScheduleFollowUp(normalized: String): Boolean {
        when (BreakdownControlInterpreter.interpret(normalized)) {
            BreakdownFollowUpMove.CANCEL,
            BreakdownFollowUpMove.REJECT -> {
                cancelPendingBreakdown()
                return true
            }
            else -> Unit
        }

        val pending = breakdownDraftController.draft?.temporalClarification
        if (pending != null) {
            if (pending.needsExactDate && pending.exactDate == null) {
                val resolution = temporalQueryResolver.resolve(normalized, null, normalized)
                if (!resolution.isExactDate ||
                    resolution.startDateInclusive == null ||
                    !TemporalActionPolicy.validateClarification(
                        pending.original,
                        resolution.startDateInclusive,
                        null
                    )
                ) {
                    assistantSession.speakThenListenAgain(
                        "That date is outside the requested range. Please choose a valid exact date."
                    )
                    return true
                }
                breakdownDraftController.updateTemporalClarification(
                    pending.copy(exactDate = resolution.startDateInclusive)
                )
                promptNextBreakdownTemporalClarification()
                return true
            }
            if (pending.needsExactTime && pending.exactMinute == null) {
                val resolution = temporalQueryResolver.resolve(null, normalized, normalized)
                val minute = resolution.startMinuteInclusive
                if (!resolution.isExactTime ||
                    minute == null ||
                    !TemporalActionPolicy.validateClarification(
                        pending.original,
                        null,
                        minute
                    )
                ) {
                    assistantSession.speakThenListenAgain(
                        "That time is outside the requested range. Please choose a valid exact time."
                    )
                    return true
                }
                breakdownDraftController.updateTemporalClarification(
                    pending.copy(exactMinute = minute)
                )
                promptNextBreakdownTemporalClarification()
                return true
            }
        }

        val incoming = temporalQueryResolver.resolve(null, null, normalized)
        breakdownDraftController.updateProposedSchedule(
            dateText = incoming.takeIf { it.isExactDate }?.startDateInclusive,
            timeText = incoming.takeIf { it.isExactTime }
                ?.startMinuteInclusive
                ?.let { ScheduleTextParser.formatTime(it / 60, it % 60) }
        )
        proceedAfterBreakdownApproval()
        return true
    }

    private fun createPendingBreakdownIfFuture(dueDate: String, dueTime: String) {
        val finalResolution = temporalQueryResolver.resolve(
            dueDate,
            dueTime,
            "$dueDate $dueTime"
        )
        if (
            TemporalActionPolicy.evaluate(
                finalResolution,
                TemporalUseCase.BREAKDOWN
            ) is TemporalPolicyResult.InvalidPastSchedule
        ) {
            enterBreakdownFutureCorrection(finalResolution)
            return
        }
        savePendingBreakdown(dueDate, dueTime)
    }

    private fun enterBreakdownFutureCorrection(rejectedResolution: TemporalQueryWindow) {
        val rejectedDate = rejectedResolution.startDateInclusive
        val rejectedMinute = rejectedResolution.startMinuteInclusive
        val replacementOriginal = TemporalQueryWindow(
            TemporalResolutionStatus.NONE,
            spokenLabel = "a future schedule"
        )
        val calendar = Calendar.getInstance()
        val currentMinute =
            calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val dateIsPast = isBreakdownDateBeforeToday(rejectedDate)
        val timeIsPastToday =
            isBreakdownDateToday(rejectedDate) &&
                rejectedMinute != null &&
                rejectedMinute <= currentMinute
        val clarification = when {
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
        beginOrUpdateBreakdownScheduleCollection(clarification)
        val prompt = when {
            clarification.needsExactDate && clarification.exactDate == null ->
                "Please provide a future exact date."
            clarification.needsExactTime && clarification.exactMinute == null ->
                "Please provide a later exact time."
            else -> "Please provide a future date and time."
        }
        assistantSession.speakThenListenAgain("${responseManager.pastDateTime()} $prompt")
    }

    private fun isBreakdownDateBeforeToday(date: String?): Boolean {
        val parsed = parseBreakdownDateMillis(date) ?: return false
        return parsed < breakdownTodayStartMillis()
    }

    private fun isBreakdownDateToday(date: String?): Boolean {
        val parsed = parseBreakdownDateMillis(date) ?: return false
        return parsed == breakdownTodayStartMillis()
    }

    private fun breakdownTodayStartMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun parseBreakdownDateMillis(date: String?): Long? = try {
        if (date.isNullOrBlank()) {
            null
        } else {
            SimpleDateFormat("dd/MM/yyyy", Locale.UK).parse(date)?.time
        }
    } catch (_: Exception) {
        null
    }

    private fun savePendingBreakdown(
        exactDate: String? = null,
        exactTime: String? = null
    ) {
        val pendingSave = breakdownDraftController.markSaving(exactDate, exactTime)
        if (pendingSave == null) {
            val message = if (
                breakdownDraftController.state == BreakdownDraftState.SAVING
            ) {
                "The confirmed task breakdown is already being saved."
            } else {
                "That task breakdown is not ready to save."
            }
            assistantSession.speak(message, listenAgain = false)
            return
        }
        homeFollowUpContext = HomeFollowUpContext.BREAKDOWN_CONFIRMATION
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@HomeActivity).taskDao()
            val coordinator = BreakdownPersistenceCoordinator(
                store = object : BreakdownPersistenceStore {
                    override suspend fun insertSubtasksIntoExistingRootAtomically(
                        parentTaskId: Long,
                        subtaskTitles: List<String>
                    ): BreakdownTransactionResult =
                        dao.insertSubtasksIntoExistingRootAtomically(
                            parentTaskId,
                            subtaskTitles
                        )

                    override suspend fun insertNewRootWithSubtasksAtomically(
                        parent: TaskEntity,
                        subtaskTitles: List<String>
                    ): BreakdownTransactionResult =
                        dao.insertNewRootWithSubtasksAtomically(parent, subtaskTitles)
                },
                reminderScheduler = BreakdownReminderScheduler { task ->
                    ReminderHelper.scheduleReminderFromTask(this@HomeActivity, task)
                }
            )
            val result = coordinator.persist(pendingSave)
            if (!breakdownDraftController.completeSaving(
                    pendingSave.saveGeneration
                )
            ) {
                return@launch
            }
            homeFollowUpContext = HomeFollowUpContext.NONE
            val publishedParentContext = if (
                result.category == BreakdownSaveResultCategory.SUCCESS ||
                result.category == BreakdownSaveResultCategory.PARTIAL_REMINDER_FAILURE
            ) {
                refreshOverview()
                BreakdownTaskContextPublisher(
                    readOnlyTaskContextStore, dao::getById, dao::getSubtasks
                ).publish(result)
            } else {
                null
            }
            if (!assistantSession.assistantSessionActive) return@launch
            val speech = when (result.category) {
                BreakdownSaveResultCategory.SUCCESS ->
                    if (result.mode == BreakdownDraftMode.EXISTING_ROOT) {
                        "I added ${result.insertedCount} subtasks to ${pendingSave.draft.parentTitle}."
                    } else {
                        "I created ${pendingSave.draft.parentTitle} with " +
                            "${result.insertedCount} subtasks for " +
                            "${pendingSave.draft.dateText} at ${pendingSave.draft.timeText}."
                    }
                BreakdownSaveResultCategory.PARTIAL_REMINDER_FAILURE ->
                    "I created ${pendingSave.draft.parentTitle} with " +
                        "${result.insertedCount} subtasks, but I could not schedule its reminder."
                BreakdownSaveResultCategory.PARENT_CHANGED ->
                    "That task changed before I could save the breakdown, so I inserted nothing."
                BreakdownSaveResultCategory.ALREADY_HAS_SUBTASKS ->
                    "${pendingSave.draft.parentTitle} already has subtasks, so I inserted nothing."
                BreakdownSaveResultCategory.FAILURE ->
                    "I could not save the task breakdown. Nothing was inserted."
            }
            val delivered = speakObservation(
                ExecutionObservation(
                    operation = ExecutionOperation.BREAKDOWN_TASK,
                    outcome = when (result.category) {
                        BreakdownSaveResultCategory.SUCCESS -> ExecutionOutcome.SUCCESS
                        BreakdownSaveResultCategory.PARTIAL_REMINDER_FAILURE ->
                            ExecutionOutcome.PARTIAL_SUCCESS
                        else -> ExecutionOutcome.FAILURE
                    },
                    taskTitle = pendingSave.draft.parentTitle,
                    taskCount = result.insertedCount,
                    dateText = pendingSave.draft.dateText.orEmpty(),
                    timeText = pendingSave.draft.timeText.orEmpty(),
                    planItems = pendingSave.draft.proposedSubtasks,
                    listenAgain = result.category == BreakdownSaveResultCategory.SUCCESS ||
                        result.category == BreakdownSaveResultCategory.PARTIAL_REMINDER_FAILURE,
                    fallbackSpeech = speech
                )
            )
            val publishedSnapshot = publishedParentContext?.snapshot
            val focusedItem = publishedSnapshot?.let {
                BreakdownPostSaveContextFocusPolicy.authoritativeItemOrNull(
                    publishedSnapshot = it,
                    currentGeneration = readOnlyTaskContextStore.currentGeneration()
                )
            }
            if (delivered && focusedItem != null) {
                conversationOrchestrator.setAuthoritativeContextFocus(
                    item = focusedItem,
                    selectedRef = focusedItem.ref,
                    capturedGeneration = requireNotNull(publishedSnapshot).generation
                )
            }
        }
    }

    private fun handleBreakdownDraftFailure(update: BreakdownDraftUpdate) {
        if (update == BreakdownDraftUpdate.Stale) return
        breakdownDraftController.discard(
            if (update == BreakdownDraftUpdate.ParentChanged) {
                "PARENT_CHANGED"
            } else {
                "FAILURE"
            }
        )
        homeFollowUpContext = HomeFollowUpContext.NONE
        assistantSession.speakThenListenAgain(
            "That task changed while I was preparing the breakdown. Please try again."
        )
    }

    private fun speakBreakdownValidationFailure() {
        lifecycleScope.launch {
            speakObservation(
                ExecutionObservation(
                    operation = ExecutionOperation.BREAKDOWN_TASK,
                    outcome = ExecutionOutcome.FAILURE,
                    requiredInput = RequiredInput.RETRY,
                    allowedUserMoves = listOf(
                        AllowedUserMove.RETRY,
                        AllowedUserMove.CANCEL,
                        AllowedUserMove.REQUEST_HELP
                    ),
                    listenAgain = true,
                    fallbackSpeech =
                        "The proposed breakdown was not structurally safe. Please describe the large task again."
                )
            )
        }
    }

    private fun logBreakdownTargetResolution(
        matchCount: Int,
        ambiguous: Boolean,
        selectedMode: BreakdownDraftMode?
    ) {
        DebugDiagnosticLog.event(
            "BREAKDOWN_TARGET_RESOLUTION",
            "matchCount=$matchCount\nambiguous=$ambiguous\n" +
                "selectedMode=${selectedMode?.name.orEmpty()}"
        )
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
                    speakObservation(
                        executeDeterministicTaskCompletion(
                            task = chosenTask,
                            action = ConversationContextAction.MARK_DONE,
                            grounding = "AMBIGUITY_SELECTION"
                        )
                    )
                }

                PendingTaskAction.MARK_UNDONE -> {
                    speakObservation(
                        executeDeterministicTaskCompletion(
                            task = chosenTask,
                            action = ConversationContextAction.MARK_UNDONE,
                            grounding = "AMBIGUITY_SELECTION"
                        )
                    )
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

    private fun speakControlIdentification(text: String) {
        voiceHelper.speak(text)
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
                        if (!isBoundedCreateFollowUpControl(normalized)) return false
                        // log
                        Log.d("HOME_CONVO_ACTION", "opening create from follow-up")
                        openCreateTaskFromFollowUp()
                        true
                    }

                    ConversationIntent.CONFIRM_NO,
                    ConversationIntent.STOP_CONVERSATION -> {
                        if (!isSimpleFollowUpEndCommand(normalized)) return false
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
            HomeFollowUpContext.AFTER_CONTEXT_SUGGESTION -> {
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
            HomeFollowUpContext.CONTEXT_ACTION_TARGET_CLARIFICATION -> false
            HomeFollowUpContext.CONTEXT_ACTION_CHANGE_CLARIFICATION -> false
            HomeFollowUpContext.DELETE_CONFIRMATION -> {
                val confirmation = DeleteConfirmationPolicy.resolve(normalized)
                when (intent) {
                    ConversationIntent.CONFIRM_YES -> {
                        if (confirmation.result != BoundedConfirmationResult.AFFIRM) return false
                        Log.d("HOME_CONVO_ACTION", "delete confirmed")
                        confirmPendingDelete()
                        true
                    }

                    ConversationIntent.CONFIRM_NO,
                    ConversationIntent.STOP_CONVERSATION -> {
                        if (confirmation.result !in setOf(
                                BoundedConfirmationResult.REJECT,
                                BoundedConfirmationResult.CANCEL
                            )
                        ) return false
                        cancelPendingDeleteConfirmation()
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
                        if (!isSimpleFollowUpEndCommand(normalized)) return false
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



    override fun onStop() {
        if (!isChangingConfigurations && !preserveAssistantSessionWhileStopped()) {
            invalidateAssistantRequest(AssistantRequestInvalidationReason.SESSION_STOPPED)
            clearConversationSessionContext()
            assistantSession.stopForLifecycle()
        }
        super.onStop()
    }

    override fun onDestroy() {
        invalidateAssistantRequest(AssistantRequestInvalidationReason.SESSION_STOPPED)
        savedRoutineInteractionController.clearForActivityDestruction()
        developerAssistantOverlay?.dismiss()
        developerAssistantOverlay = null
        assistantSession.destroy()
        voiceHelper.shutdown()
        super.onDestroy()
    }

    private fun applyPendingVoiceDisplayRefresh() {
        if (!pendingVoiceDisplayRefresh) return
        pendingVoiceDisplayRefresh = false
        if (!isFinishing && !isDestroyed) recreate()
    }

    private fun handlePendingVoiceSettingClarification(normalized: String): Boolean {
        val pending = voiceSettingConversationContext.pendingClarification ?: return false
        val safetyResult = VoiceSettingsMutationSafetyPolicy.evaluateClarification(
            normalizedUtterance = normalized,
            pending = pending
        ) ?: run {
            if (VoiceSettingsMutationSafetyPolicy.shouldRetainClarification(
                    normalizedUtterance = normalized,
                    pending = pending
                )
            ) {
                val retry = VoiceSettingsMutationSafetyPolicy.retryClarification(pending)
                voiceSettingConversationContext.retain(retry.pendingClarification)
                logVoiceSettingsSafety(retry.authorizedAction, retry)
                assistantSession.speak(retry.speech, listenAgain = true)
                return true
            }
            voiceSettingConversationContext.clearPending()
            return false
        }
        logVoiceSettingsSafety(safetyResult.authorizedAction, safetyResult)
        return when (safetyResult.disposition) {
            VoiceSettingsSafetyDisposition.ALLOW -> {
                val decision = ConversationDecision(
                    route = ConversationRoute.SETTINGS_ACTION,
                    settingAction = safetyResult.authorizedAction,
                    confidence = 1.0,
                    listenAgain = true,
                    source = "android_voice_setting_clarification"
                )
                if (!VoiceSettingsDecisionValidator.isValid(decision)) {
                    voiceSettingConversationContext.clearPending()
                    false
                } else {
                    voiceSettingConversationContext.clearPending()
                    safetyResult.authorizedAction.voiceSettingTarget()?.let(
                        voiceSettingConversationContext::focus
                    )
                    conversationOrchestrator.commitFinalDecision(decision)
                    executeAllowedVoiceSetting(safetyResult.authorizedAction)
                    true
                }
            }
            VoiceSettingsSafetyDisposition.CLARIFY_HAPTIC_TARGET -> {
                voiceSettingConversationContext.retain(safetyResult.pendingClarification)
                assistantSession.speak(safetyResult.speech, listenAgain = true)
                true
            }
            VoiceSettingsSafetyDisposition.CLARIFY_SETTING_TARGET -> {
                voiceSettingConversationContext.retain(safetyResult.pendingClarification)
                assistantSession.speak(safetyResult.speech, listenAgain = true)
                true
            }
            VoiceSettingsSafetyDisposition.GUIDANCE_ONLY -> {
                voiceSettingConversationContext.clearPending()
                false
            }
        }
    }

    private fun executeAllowedVoiceSetting(action: ConversationSettingAction) {
        val result = voiceSettingsExecutor.execute(action)
        if (result.displayRefreshRequired) {
            pendingVoiceDisplayRefresh = true
        }
        responseManager.profile = AssistantResponseManager.fromPreferences(this).profile
        assistantSession.speak(result.speech, listenAgain = true)
    }

    private fun deliverVoiceSettingsSafetyResponse(
        result: VoiceSettingsSafetyResult,
        route: ConversationRoute
    ) {
        conversationOrchestrator.commitFinalDecision(
            ConversationDecision(
                route = route,
                reply = result.speech,
                listenAgain = true,
                source = "android_voice_settings_safety"
            )
        )
        assistantSession.speak(result.speech, listenAgain = true)
    }

    private fun logVoiceSettingsSafety(
        action: ConversationSettingAction,
        result: VoiceSettingsSafetyResult
    ) {
        Log.d(
            "VOICE_SETTINGS_SAFETY",
            "action=${action.name} disposition=${result.disposition.name}"
        )
    }

    private companion object {
        const val TASK_DETAIL_ENTRY_TAG = "TASK_DETAIL_ENTRY"
    }
}

// this is a comment for version tally, the current version is 2.4
