package com.example.myapplication

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.accessibility.AccessibleAssistantInputDialog
import com.example.myapplication.accessibility.AccessibilityStateHelper
import com.example.myapplication.accessibility.AssistantAccessibilityState
import com.example.myapplication.accessibility.TaskCardAccessibilitySemantics
import com.example.myapplication.ai.conversation.ConversationAgentClient
import com.example.myapplication.ai.conversation.taskdetailedit.TaskDetailEditAgentContext
import com.example.myapplication.ai.conversation.taskdetailedit.TaskDetailEditField
import com.example.myapplication.ai.conversation.taskdetailedit.TaskDetailEditLocalCandidate
import com.example.myapplication.ai.conversation.taskdetailedit.TaskDetailEditMoveResolution
import com.example.myapplication.ai.conversation.taskdetailedit.TaskDetailEditProposal
import com.example.myapplication.ai.conversation.taskdetailedit.TaskDetailEditSemanticOrchestrator
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.TaskEntity
import com.example.myapplication.reminder.ReminderEligibilityPolicy
import com.example.myapplication.reminder.ReminderSchedulingEligibility
import com.example.myapplication.voice.AssistantResponseManager
import com.example.myapplication.voice.AssistantVoiceHost
import com.example.myapplication.voice.AssistantVoiceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TaskDetailActivity : AppCompatActivity(), AssistantVoiceHost {
    companion object {
        const val EXTRA_TASK_ID = "task_id"
        private const val TASK_DETAIL_UPDATE_TAG = "TASK_DETAIL_UPDATE"
    }

    private lateinit var dao: com.example.myapplication.data.TaskDao
    private lateinit var voiceHelper: VoiceHelper
    private lateinit var titleSurface: View
    private lateinit var statusSurface: View
    private lateinit var dateSurface: View
    private lateinit var timeSurface: View
    private lateinit var subtaskProgressSurface: View
    private lateinit var subtaskSection: View
    private lateinit var subtaskList: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var dateText: TextView
    private lateinit var timeText: TextView
    private lateinit var subtaskProgressText: TextView
    private lateinit var readAllButton: Button
    private lateinit var toggleDoneButton: Button
    private lateinit var saveButton: Button
    private lateinit var deleteButton: Button
    private lateinit var homeButton: Button
    private lateinit var assistantButton: Button
    private lateinit var unsavedChangesText: TextView
    private lateinit var navigationCoordinator: VoiceFirstNavigationCoordinator
    private lateinit var assistantSession: AssistantVoiceSession
    private lateinit var responseManager: AssistantResponseManager
    private lateinit var taskDetailEditSemanticOrchestrator: TaskDetailEditSemanticOrchestrator

    private var taskId: Long = -1L
    private var currentTask: TaskEntity? = null
    private var currentSubtasks: List<TaskEntity> = emptyList()
    private var draftController: TaskDetailDraftController? = null
    private val fieldResolver = TaskFieldEditResolver()
    private var editInteraction = TaskDetailEditInteraction.IDLE
    private var interactionRevision = -1L
    private var editInteractionGeneration = 0L
    private var pendingFieldClarification: String? = null
    private var pendingPastTimeProposal: TaskDetailPastTimeProposal? = null
    private var pendingSaveClaim: TaskDetailSaveClaim? = null
    private var pendingExitAfterSave: (() -> Unit)? = null
    private var taskMutationInProgress = false
    private var missingTaskSpeechPending = false
    private var activityStopped = false
    private val screenSpeechState = TaskDetailScreenSpeechState()

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) assistantSession.onAudioPermissionGranted()
            else assistantSession.onAudioPermissionDenied()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_detail)
        AccessibilityStateHelper.markHeading(findViewById(R.id.taskDetailHeading))

        dao = AppDatabase.getInstance(this).taskDao()
        voiceHelper = VoiceHelper(this)
        navigationCoordinator = VoiceFirstNavigationCoordinator(
            speak = { text, onFinished ->
                voiceHelper.speakWithResult(text) { success ->
                    runOnUiThread { onFinished(success) }
                }
            }
        )
        bindViews()
        responseManager = AssistantResponseManager.fromPreferences(this)
        taskDetailEditSemanticOrchestrator = TaskDetailEditSemanticOrchestrator(
            semanticClient = ConversationAgentClient(this)
        )
        assistantSession = AssistantVoiceSession(
            activity = this,
            host = this,
            voiceHelper = voiceHelper,
            responseManager = responseManager,
            audioPermissionLauncher = audioPermissionLauncher,
            normalizeFinalTextForHost = false,
            onAccessibilityStateChanged = { state ->
                AccessibilityStateHelper.updateAssistantState(assistantButton, state, announce = false)
            }
        )
        assistantSession.bindAssistantControl(assistantButton)
        bindInteractions()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackExit()
            }
        })

        taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (!intent.hasExtra(EXTRA_TASK_ID) || taskId <= 0L) {
            showMissingTaskAndFinish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (taskId > 0L) loadAuthoritativeTask()
    }

    override fun onStart() {
        super.onStart()
        activityStopped = false
    }

    override fun onDestroy() {
        navigationCoordinator.cancelPending()
        assistantSession.destroy()
        voiceHelper.shutdown()
        super.onDestroy()
    }

    override fun onStop() {
        activityStopped = true
        navigationCoordinator.cancelPending()
        if (!isChangingConfigurations) {
            assistantSession.stopForLifecycle()
            clearLocalInteraction()
        }
        super.onStop()
    }

    private fun bindViews() {
        titleSurface = findViewById(R.id.detailTitleSurface)
        statusSurface = findViewById(R.id.detailStatusSurface)
        dateSurface = findViewById(R.id.detailDateSurface)
        timeSurface = findViewById(R.id.detailTimeSurface)
        subtaskProgressSurface = findViewById(R.id.detailSubtaskProgressSurface)
        subtaskSection = findViewById(R.id.detailSubtasksSection)
        subtaskList = findViewById(R.id.detailSubtaskList)
        titleText = findViewById(R.id.detailTitleText)
        statusText = findViewById(R.id.detailStatusText)
        dateText = findViewById(R.id.detailDateText)
        timeText = findViewById(R.id.detailTimeText)
        subtaskProgressText = findViewById(R.id.detailSubtaskProgressText)
        readAllButton = findViewById(R.id.btnReadAll)
        toggleDoneButton = findViewById(R.id.btnToggleDone)
        saveButton = findViewById(R.id.btnSaveChanges)
        deleteButton = findViewById(R.id.btnDeleteTask)
        homeButton = findViewById(R.id.btnGoHome)
        assistantButton = findViewById(R.id.btnTalkAssistant)
        unsavedChangesText = findViewById(R.id.detailUnsavedChanges)
    }

    private fun bindInteractions() {
        VoiceFirstGestureBinder.bindAction(
            titleSurface,
            speechProvider = { draftController?.draft?.let { TaskDetailSpeechRenderer.title(it.title) } },
            speak = ::speakIdentification,
            activate = { startFieldEdit(TaskDetailEditInteraction.WAITING_FOR_TITLE) }
        )
        titleSurface.setOnLongClickListener { showManualTitleEditor(); true }
        VoiceFirstGestureBinder.bindInformation(
            statusSurface,
            speechProvider = { draftStatus()?.let(TaskDetailSpeechRenderer::status) },
            speak = ::speakIdentification
        )
        VoiceFirstGestureBinder.bindAction(
            dateSurface,
            speechProvider = { draftController?.draft?.let { TaskDetailSpeechRenderer.date(it.dueDate) } },
            speak = ::speakIdentification,
            activate = { startFieldEdit(TaskDetailEditInteraction.WAITING_FOR_DATE) }
        )
        dateSurface.setOnLongClickListener { showManualDatePicker(); true }
        VoiceFirstGestureBinder.bindAction(
            timeSurface,
            speechProvider = { draftController?.draft?.let { TaskDetailSpeechRenderer.time(it.dueTime) } },
            speak = ::speakIdentification,
            activate = { startFieldEdit(TaskDetailEditInteraction.WAITING_FOR_TIME) }
        )
        timeSurface.setOnLongClickListener { showManualTimePicker(); true }
        VoiceFirstGestureBinder.bindInformation(
            subtaskProgressSurface,
            speechProvider = {
                TaskDetailSpeechRenderer.subtaskProgress(
                    currentSubtasks.count { it.isDone },
                    currentSubtasks.size
                )
            },
            speak = ::speakIdentification
        )

        VoiceFirstGestureBinder.bindAction(
            readAllButton,
            speechProvider = TaskScreenControlSpeechRenderer::readAllDescription,
            speak = ::speakIdentification,
            activate = ::readAll
        )
        VoiceFirstGestureBinder.bindAction(
            toggleDoneButton,
            speechProvider = {
                currentTask?.let { TaskScreenControlSpeechRenderer.toggleDescription(it.isDone) }
            },
            speak = ::speakIdentification,
            activate = ::toggleDone,
            doubleTapHaptic = null
        )
        VoiceFirstGestureBinder.bindAction(
            saveButton,
            speechProvider = TaskScreenControlSpeechRenderer::saveDescription,
            speak = ::speakIdentification,
            activate = ::requestSave
        )
        VoiceFirstGestureBinder.bindAction(
            deleteButton,
            speechProvider = TaskScreenControlSpeechRenderer::deleteDescription,
            speak = ::speakIdentification,
            activate = ::handleDelete
        )

        AccessibilityStateHelper.updateAssistantState(
            assistantButton,
            AssistantAccessibilityState.READY,
            announce = false
        )
        VoiceFirstGestureBinder.bindAction(
            homeButton,
            speechProvider = TaskScreenControlSpeechRenderer::homeDescription,
            speak = ::speakIdentification,
            activate = ::handleHomeExit
        )
        VoiceFirstGestureBinder.bindAction(
            assistantButton,
            speechProvider = TaskScreenControlSpeechRenderer::taskAssistantDescription,
            speak = ::speakIdentification,
            activate = ::handleContextualAssistant
        )
    }

    private fun loadAuthoritativeTask() {
        setActionButtonsEnabled(false)
        lifecycleScope.launch {
            try {
                val snapshot = loadAuthoritativeSnapshot()
                if (snapshot == null) {
                    currentTask = null
                    currentSubtasks = emptyList()
                    showMissingTaskAndFinish()
                    return@launch
                }
                reconcileAuthoritativeSnapshot(snapshot)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.w(
                    TASK_DETAIL_UPDATE_TAG,
                    "operation=LOAD outcome=FAILED type=${exception.javaClass.simpleName}"
                )
                voiceHelper.speak(getString(R.string.task_update_failed))
            } finally {
                if (!isFinishing && !taskMutationInProgress && !missingTaskSpeechPending) {
                    setActionButtonsEnabled(true)
                }
            }
        }
    }

    private suspend fun loadAuthoritativeSnapshot(): Pair<TaskEntity, List<TaskEntity>>? =
        withContext(Dispatchers.IO) {
            val task = dao.getById(taskId) ?: return@withContext null
            task to dao.getSubtasks(taskId)
        }

    private fun applySnapshot(snapshot: Pair<TaskEntity, List<TaskEntity>>) {
        currentTask = snapshot.first
        currentSubtasks = snapshot.second
        render(snapshot.first, snapshot.second)
    }

    private fun reconcileAuthoritativeSnapshot(snapshot: Pair<TaskEntity, List<TaskEntity>>) {
        val existing = draftController
        val latest = snapshot.first
        var dirtyDraftInvalidated = false
        val fieldsChangedExternally = existing != null && (
            existing.base.title != latest.title ||
                existing.base.dueDate != latest.dueDate ||
                existing.base.dueTime != latest.dueTime
            )
        when {
            existing == null -> draftController = TaskDetailDraftController.from(latest)
            fieldsChangedExternally -> {
                val wasDirty = existing.isDirty
                existing.replaceFromRoom(latest)
                if (wasDirty) {
                    dirtyDraftInvalidated = true
                    voiceHelper.speak("This task changed elsewhere. Your unsaved draft was cleared.")
                }
            }
            else -> existing.updateAuthoritativeCompletion(latest.isDone)
        }
        applySnapshot(snapshot)
        if (dirtyDraftInvalidated) {
            screenSpeechState.synchronize(snapshot)
        } else {
            screenSpeechState.onAuthoritativeLoad(latest.title, snapshot)?.let(::speakIdentification)
        }
    }

    private fun render(task: TaskEntity, subtasks: List<TaskEntity>) {
        val draft = draftController?.draft ?: return
        val status = statusFor(task.isDone, draft.dueDate, draft.dueTime)
        val completedSubtasks = subtasks.count { it.isDone }

        titleText.text = draft.title.ifBlank { getString(R.string.untitled_task) }
        statusText.text = status.visibleText
        dateText.text = TaskCardAccessibilitySemantics.spokenDate(draft.dueDate)
        timeText.text = TaskCardAccessibilitySemantics.spokenTime(draft.dueTime)
        toggleDoneButton.setText(if (task.isDone) R.string.undo else R.string.mark_done)

        titleSurface.contentDescription = TaskDetailSpeechRenderer.title(draft.title)
        statusSurface.contentDescription = TaskDetailSpeechRenderer.status(status)
        dateSurface.contentDescription = TaskDetailSpeechRenderer.date(draft.dueDate)
        timeSurface.contentDescription = TaskDetailSpeechRenderer.time(draft.dueTime)
        applyStatusTreatment(status.visualStatus)
        renderSubtasks(subtasks, completedSubtasks)
        renderDirtyState()
    }

    private fun renderSubtasks(subtasks: List<TaskEntity>, completedCount: Int) {
        subtaskList.removeAllViews()
        if (subtasks.isEmpty()) {
            subtaskSection.visibility = View.GONE
            return
        }

        subtaskSection.visibility = View.VISIBLE
        subtaskProgressText.text = resources.getQuantityString(
            R.plurals.subtask_progress_format,
            subtasks.size,
            completedCount,
            subtasks.size
        )
        subtaskProgressSurface.contentDescription =
            TaskDetailSpeechRenderer.subtaskProgress(completedCount, subtasks.size)

        subtasks.forEachIndexed { index, subtask ->
            val state = getString(
                if (subtask.isDone) {
                    R.string.subtask_completed_state
                } else {
                    R.string.subtask_not_completed_state
                }
            )
            val marker = if (subtask.isDone) "✓" else "○"
            val subtaskView = TextView(this).apply {
                text = getString(R.string.subtask_state_format, marker, state, subtask.title)
                contentDescription = "$state. ${subtask.title}."
                gravity = Gravity.CENTER_VERTICAL
                minHeight = resources.getDimensionPixelSize(R.dimen.task_detail_subtask_min_height)
                setBackgroundResource(R.drawable.bg_subtask_item)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setTextColor(ContextCompat.getColor(context, R.color.ui_text_primary_light))
                textSize = 18f
            }
            VoiceFirstGestureBinder.bindInformation(
                subtaskView,
                speechProvider = {
                    TaskDetailSpeechRenderer.subtask(subtask.title, subtask.isDone)
                },
                speak = ::speakIdentification
            )
            subtaskList.addView(
                subtaskView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) topMargin = dp(8)
                }
            )
        }
    }

    private fun readAll() {
        navigationCoordinator.cancelPending()
        val task = currentTask ?: return
        val draftState = draftController ?: return
        val draft = draftState.draft
        voiceHelper.speak(
            TaskDetailSpeechRenderer.readAll(
                title = draft.title,
                status = statusFor(task.isDone, draft.dueDate, draft.dueTime),
                dueDate = draft.dueDate,
                dueTime = draft.dueTime,
                completedSubtasks = currentSubtasks.count { it.isDone },
                totalSubtasks = currentSubtasks.size,
                hasUnsavedChanges = draftState.isDirty
            )
        )
    }

    private fun speakIdentification(text: String) {
        navigationCoordinator.cancelPending()
        voiceHelper.speak(text)
    }

    private fun toggleDone() {
        navigationCoordinator.cancelPending()
        if (taskMutationInProgress) return
        val task = currentTask ?: return
        val newDoneState = !task.isDone
        taskMutationInProgress = true
        setActionButtonsEnabled(false)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    dao.updateDoneStatusForTaskAndSubtasks(task.id, newDoneState)
                }
                val refreshed = loadAuthoritativeSnapshot()
                    ?: throw IllegalStateException("Task unavailable after completion update")
                synchronizeDraftAfterCompletion(refreshed.first)
                applySnapshot(refreshed)
                screenSpeechState.synchronize(refreshed)
                syncReminderAfterCompletionChange(refreshed.first)
                toggleDoneButton.performConfirmationHapticFeedback()
                voiceHelper.speak(
                    getString(
                        if (newDoneState) {
                            R.string.task_marked_complete
                        } else {
                            R.string.task_marked_incomplete
                        }
                    )
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.w(
                    TASK_DETAIL_UPDATE_TAG,
                    "operation=TOGGLE_DONE outcome=FAILED type=${exception.javaClass.simpleName}"
                )
                voiceHelper.speak(getString(R.string.task_update_failed))
            } finally {
                taskMutationInProgress = false
                if (!isFinishing) setActionButtonsEnabled(true)
            }
        }
    }

    private fun syncReminderAfterCompletionChange(task: TaskEntity) {
        try {
            if (task.isDone) {
                ReminderHelper.cancelReminder(this@TaskDetailActivity, task.id)
            } else {
                ReminderHelper.scheduleReminderFromTask(this@TaskDetailActivity, task)
            }
        } catch (exception: Exception) {
            Log.w(
                TASK_DETAIL_UPDATE_TAG,
                "operation=REMINDER_SYNC outcome=FAILED type=${exception.javaClass.simpleName}"
            )
        }
    }

    private fun synchronizeDraftAfterCompletion(task: TaskEntity) {
        draftController?.let { controller ->
            val savedFieldsChanged = controller.base.title != task.title ||
                controller.base.dueDate != task.dueDate ||
                controller.base.dueTime != task.dueTime
            if (!controller.isDirty && savedFieldsChanged) {
                controller.replaceFromRoom(task)
            } else {
                controller.updateAuthoritativeCompletion(task.isDone)
            }
        }
    }

    private fun launchHomeAssistant(entryMode: HomeAssistantEntryMode) {
        val task = currentTask ?: return
        startActivity(
            HomeAssistantEntryContract.putTaskDetail(
                intent = Intent(this, HomeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
                taskId = task.id,
                entryMode = entryMode
            )
        )
    }

    private fun returnHome() {
        startActivity(Intent(this, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }

    private fun returnBack() {
        finish()
    }

    private fun statusFor(task: TaskEntity): TaskStatusPresentation =
        statusFor(task.isDone, task.dueDate, task.dueTime)

    private fun statusFor(
        isDone: Boolean,
        dueDate: String?,
        dueTime: String?
    ): TaskStatusPresentation = TaskStatusPresenter.present(isDone, dueDate, dueTime)

    private fun draftStatus(): TaskStatusPresentation? {
        val task = currentTask ?: return null
        val draft = draftController?.draft ?: return null
        return statusFor(task.isDone, draft.dueDate, draft.dueTime)
    }

    private fun applyStatusTreatment(visualStatus: TaskVisualStatus) {
        val (background, textColor) = when (visualStatus) {
            TaskVisualStatus.OVERDUE -> R.drawable.bg_task_status_overdue to R.color.task_status_overdue_text
            TaskVisualStatus.DUE_TODAY -> R.drawable.bg_task_status_today to R.color.task_status_today_text
            TaskVisualStatus.UPCOMING -> R.drawable.bg_task_status_upcoming to R.color.task_status_upcoming_text
            TaskVisualStatus.COMPLETED -> R.drawable.bg_task_status_completed to R.color.task_status_completed_text
            TaskVisualStatus.UNSCHEDULED -> R.drawable.bg_task_status_unscheduled to R.color.task_status_unscheduled_text
        }
        statusSurface.setBackgroundResource(background)
        statusText.setTextColor(ContextCompat.getColor(this, textColor))
    }

    private fun setActionButtonsEnabled(enabled: Boolean) {
        listOf(
            readAllButton,
            toggleDoneButton,
            saveButton,
            deleteButton,
            homeButton,
            assistantButton
        ).forEach { button ->
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.55f
        }
    }

    private fun renderDirtyState() {
        val dirty = draftController?.isDirty == true
        unsavedChangesText.visibility = if (dirty) View.VISIBLE else View.GONE
        saveButton.setBackgroundResource(
            if (dirty) R.drawable.bg_save_changes_dirty else R.drawable.bg_action_button
        )
        saveButton.setTextColor(
            ContextCompat.getColor(
                this,
                if (dirty) R.color.white else R.color.ui_text_primary_light
            )
        )
    }

    private fun startFieldEdit(interaction: TaskDetailEditInteraction) {
        if (taskMutationInProgress) return
        val draft = draftController?.draft ?: run {
            voiceHelper.speak(getString(R.string.task_no_longer_available_spoken))
            return
        }
        navigationCoordinator.cancelPending()
        editInteractionGeneration += 1L
        editInteraction = interaction
        interactionRevision = draft.revision
        pendingFieldClarification = null
        pendingPastTimeProposal = null
        pendingSaveClaim = null
        pendingExitAfterSave = null
        assistantSession.prepareForContextEntry()
        assistantSession.startPassiveSession()
        assistantSession.speakThenListenAgain(
            when (interaction) {
                TaskDetailEditInteraction.WAITING_FOR_TITLE -> TaskDetailEditSpeechRenderer.askTitle()
                TaskDetailEditInteraction.WAITING_FOR_DATE -> TaskDetailEditSpeechRenderer.askDate()
                TaskDetailEditInteraction.WAITING_FOR_TIME -> TaskDetailEditSpeechRenderer.askTime()
                else -> return
            }
        )
    }

    private fun requestSave() {
        if (taskMutationInProgress) return
        val controller = draftController ?: return
        val claim = controller.freezeSaveClaim()
        if (claim == null) {
            voiceHelper.speak("There are no unsaved changes.")
            return
        }
        beginConfirmation(
            interaction = TaskDetailEditInteraction.WAITING_FOR_SAVE_CONFIRMATION,
            prompt = TaskDetailEditSpeechRenderer.confirmSave(claim.draft.title),
            claim = claim
        )
    }

    private fun handleHomeExit() {
        if (taskMutationInProgress) return
        val controller = draftController
        if (controller?.isDirty != true) {
            navigationCoordinator.request(TaskScreenControlSpeechRenderer.returningHome()) {
                returnHome()
            }
            return
        }
        beginConfirmation(
            interaction = TaskDetailEditInteraction.WAITING_FOR_HOME_CONFIRMATION,
            prompt = TaskDetailEditSpeechRenderer.confirmHomeExit(),
            claim = controller.freezeSaveClaim(),
            afterSave = ::returnHome
        )
    }

    private fun handleBackExit() {
        if (taskMutationInProgress) return
        val controller = draftController
        if (controller?.isDirty != true) {
            navigationCoordinator.request(TaskScreenControlSpeechRenderer.goingBack()) {
                returnBack()
            }
            return
        }
        beginConfirmation(
            interaction = TaskDetailEditInteraction.WAITING_FOR_BACK_CONFIRMATION,
            prompt = TaskDetailEditSpeechRenderer.confirmBackExit(),
            claim = controller.freezeSaveClaim(),
            afterSave = ::returnBack
        )
    }

    private fun handleContextualAssistant() {
        if (taskMutationInProgress) return
        val task = currentTask ?: return
        val controller = draftController ?: return
        if (!controller.isDirty) {
            navigationCoordinator.request(
                TaskScreenControlSpeechRenderer.openingTaskAssistant(task.title)
            ) { launchHomeAssistant(HomeAssistantEntryMode.TASK_DETAIL_CONTEXT) }
            return
        }
        beginConfirmation(
            interaction = TaskDetailEditInteraction.WAITING_FOR_ASSISTANT_EXIT_CONFIRMATION,
            prompt = TaskDetailEditSpeechRenderer.confirmAssistantExit(),
            claim = controller.freezeSaveClaim(),
            afterSave = { launchHomeAssistant(HomeAssistantEntryMode.TASK_DETAIL_CONTEXT) }
        )
    }

    private fun handleDelete() {
        if (taskMutationInProgress) return
        val controller = draftController ?: return
        if (!controller.isDirty) {
            navigationCoordinator.request(
                TaskScreenControlSpeechRenderer.openingDeleteConfirmation()
            ) { launchHomeAssistant(HomeAssistantEntryMode.TASK_DETAIL_DELETE_CONFIRMATION) }
            return
        }
        beginConfirmation(
            interaction = TaskDetailEditInteraction.WAITING_FOR_DELETE_DISCARD_CONFIRMATION,
            prompt = TaskDetailEditSpeechRenderer.confirmDeleteDiscard(),
            claim = null
        )
    }

    private fun beginConfirmation(
        interaction: TaskDetailEditInteraction,
        prompt: String,
        claim: TaskDetailSaveClaim?,
        afterSave: (() -> Unit)? = null
    ) {
        navigationCoordinator.cancelPending()
        editInteractionGeneration += 1L
        editInteraction = interaction
        interactionRevision = draftController?.draft?.revision ?: -1L
        pendingSaveClaim = claim
        pendingExitAfterSave = afterSave
        pendingFieldClarification = null
        pendingPastTimeProposal = null
        assistantSession.prepareForContextEntry()
        assistantSession.startPassiveSession()
        assistantSession.expectConfirmation()
        assistantSession.speakThenListenAgain(prompt)
    }

    override fun onAssistantFinalText(text: String) {
        when (editInteraction) {
            TaskDetailEditInteraction.WAITING_FOR_TITLE,
            TaskDetailEditInteraction.WAITING_FOR_DATE,
            TaskDetailEditInteraction.WAITING_FOR_TIME -> handleFieldResponse(text)
            TaskDetailEditInteraction.WAITING_FOR_PAST_TIME_CONFIRMATION ->
                handlePastTimeConfirmationResponse(text)
            TaskDetailEditInteraction.WAITING_FOR_SAVE_CONFIRMATION,
            TaskDetailEditInteraction.WAITING_FOR_HOME_CONFIRMATION,
            TaskDetailEditInteraction.WAITING_FOR_BACK_CONFIRMATION,
            TaskDetailEditInteraction.WAITING_FOR_ASSISTANT_EXIT_CONFIRMATION,
            TaskDetailEditInteraction.WAITING_FOR_DELETE_DISCARD_CONFIRMATION ->
                handleConfirmationResponse(text)
            TaskDetailEditInteraction.IDLE,
            TaskDetailEditInteraction.SAVING -> Unit
        }
    }

    private fun handleFieldResponse(text: String) {
        val controller = draftController ?: return
        if (controller.draft.revision != interactionRevision) {
            endLocalInteraction("That edit is no longer current. Please try again.")
            return
        }
        taskDetailEditSemanticOrchestrator.resolveImmediate(text)?.let { immediate ->
            applySemanticFieldResolution(immediate)
            return
        }

        val capturedInteraction = editInteraction
        val requestedField = requestedField(capturedInteraction) ?: return
        val capturedDraft = controller.draft
        val guard = TaskDetailEditRequestGuard(
            interaction = capturedInteraction,
            interactionGeneration = editInteractionGeneration,
            draftRevision = capturedDraft.revision
        )
        val now = Calendar.getInstance()
        val localResult = when (capturedInteraction) {
            TaskDetailEditInteraction.WAITING_FOR_TITLE -> fieldResolver.resolveTitle(text)
            TaskDetailEditInteraction.WAITING_FOR_DATE ->
                fieldResolver.resolveDate(text, capturedDraft, now)
            TaskDetailEditInteraction.WAITING_FOR_TIME ->
                fieldResolver.resolveTime(text, capturedDraft, now)
            else -> return
        }
        val context = TaskDetailEditAgentContext(
            requestedField = requestedField,
            interactionState = capturedInteraction.name,
            interactionGeneration = guard.interactionGeneration,
            draftRevision = guard.draftRevision,
            hasTitle = capturedDraft.title.isNotBlank(),
            currentDueDate = capturedDraft.dueDate.orEmpty(),
            currentDueTime = capturedDraft.dueTime.orEmpty(),
            currentLocalDate = formatContextDate(now),
            currentLocalTime = formatContextTime(now),
            timezone = now.timeZone.id,
            currentSchedulePast = fieldResolver.isSchedulePast(capturedDraft, now),
            pendingClarification = pendingFieldClarification.orEmpty(),
            allowedMoves = TaskDetailEditAgentContext.allowedMoves(requestedField)
        )

        assistantSession.pauseListeningForAssistantSpeech()
        assistantSession.getBottomSheet()?.setProcessingState()
        lifecycleScope.launch {
            val resolution = taskDetailEditSemanticOrchestrator.resolve(
                userText = text,
                context = context,
                localCandidate = localCandidate(localResult)
            )
            val currentRevision = draftController?.draft?.revision ?: -1L
            if (activityStopped || isFinishing || isDestroyed ||
                !guard.isCurrent(editInteraction, editInteractionGeneration, currentRevision)
            ) {
                Log.d(
                    "TASK_DETAIL_EDIT_RESOLUTION",
                    "target=$requestedField state=${capturedInteraction.name} move=${resolution.move} " +
                        "source=${resolution.source.logValue} confidence=${resolution.confidence} " +
                        "agentAttempted=${resolution.agentAttempted} reason=STALE_RESPONSE " +
                        "draftRevision=${guard.draftRevision} generation=${guard.interactionGeneration}"
                )
                return@launch
            }
            applySemanticFieldResolution(resolution)
        }
    }

    private fun applySemanticFieldResolution(resolution: TaskDetailEditMoveResolution) {
        val controller = draftController ?: return
        when (val proposal = resolution.proposal) {
            is TaskDetailEditProposal.Title ->
                applyValidatedFieldResult(fieldResolver.validateProposedTitle(proposal.value))
            is TaskDetailEditProposal.Schedule ->
                applyValidatedFieldResult(
                    fieldResolver.resolveScheduleProposal(
                        dateText = proposal.dateText,
                        timeText = proposal.timeText,
                        draft = controller.draft
                    )
                )
            is TaskDetailEditProposal.Clarification -> {
                pendingFieldClarification = proposal.question
                assistantSession.speakThenListenAgain(proposal.question)
            }
            is TaskDetailEditProposal.PastSameDayTime ->
                beginPastTimeClarification(proposal.proposedTime, proposal.tomorrowDate)
            TaskDetailEditProposal.Cancel -> endLocalInteraction("Edit cancelled.")
            TaskDetailEditProposal.Unknown -> {
                val question = pendingFieldClarification
                    ?: TaskDetailEditSpeechRenderer.retryQuestion(editInteraction)
                pendingFieldClarification = question
                assistantSession.speakThenListenAgain(question)
            }
        }
    }

    private fun applyValidatedFieldResult(result: TaskFieldEditResult) {
        val controller = draftController ?: return
        when (result) {
            is TaskFieldEditResult.Title -> {
                if (!controller.changeTitle(result.value)) {
                    endLocalInteraction("That title is already set.")
                    return
                }
                renderCurrentDraft()
                endLocalInteraction(TaskDetailEditSpeechRenderer.titleChanged(result.value))
            }
            is TaskFieldEditResult.Schedule -> {
                val oldDraft = controller.draft
                if (!controller.changeSchedule(result.dueDate, result.dueTime)) {
                    endLocalInteraction("That schedule is already set.")
                    return
                }
                renderCurrentDraft()
                endLocalInteraction(
                    TaskDetailEditSpeechRenderer.scheduleChanged(
                        oldDate = oldDraft.dueDate,
                        oldTime = oldDraft.dueTime,
                        newDate = result.dueDate,
                        newTime = result.dueTime
                    )
                )
            }
            is TaskFieldEditResult.NeedsClarification -> {
                pendingFieldClarification = result.prompt
                assistantSession.speakThenListenAgain(result.prompt)
            }
            is TaskFieldEditResult.PastSameDayTime ->
                beginPastTimeClarification(result.proposedTime, result.tomorrowDate)
            TaskFieldEditResult.PastSchedule -> {
                val question = TaskDetailEditSpeechRenderer.pastScheduleRetry(editInteraction)
                pendingFieldClarification = question
                assistantSession.speakThenListenAgain(
                    question
                )
            }
            TaskFieldEditResult.Invalid -> {
                val question = pendingFieldClarification
                    ?: TaskDetailEditSpeechRenderer.retryQuestion(editInteraction)
                pendingFieldClarification = question
                assistantSession.speakThenListenAgain(
                    question
                )
            }
        }
    }

    private fun beginPastTimeClarification(proposedTime: String, tomorrowDate: String) {
        val draft = draftController?.draft ?: return
        pendingPastTimeProposal = TaskDetailPastTimeProposal(
            proposedTime = proposedTime,
            tomorrowDate = tomorrowDate,
            sourceDraftRevision = draft.revision,
            interactionGeneration = editInteractionGeneration
        )
        editInteraction = TaskDetailEditInteraction.WAITING_FOR_PAST_TIME_CONFIRMATION
        val question = TaskDetailEditSpeechRenderer.pastSameDayQuestion(proposedTime)
        pendingFieldClarification = question
        assistantSession.expectConfirmation()
        assistantSession.speakThenListenAgain(question)
    }

    private fun handlePastTimeConfirmationResponse(text: String) {
        when (TaskDetailPastTimeConfirmationResolver.resolve(text)) {
            TaskDetailPastTimeConfirmationMove.APPLY_TOMORROW -> applyPendingTomorrowTime()
            TaskDetailPastTimeConfirmationMove.ASK_DATE_AND_TIME -> {
                val draft = draftController?.draft ?: return
                pendingPastTimeProposal = null
                editInteraction = TaskDetailEditInteraction.WAITING_FOR_TIME
                interactionRevision = draft.revision
                val question = TaskDetailEditSpeechRenderer.askDateAndTime()
                pendingFieldClarification = question
                assistantSession.speakThenListenAgain(question)
            }
            TaskDetailPastTimeConfirmationMove.CANCEL -> endLocalInteraction("Time edit cancelled.")
            TaskDetailPastTimeConfirmationMove.REPEAT_QUESTION -> {
                val question = pendingFieldClarification
                    ?: return endLocalInteraction("That clarification is no longer current.")
                assistantSession.expectConfirmation()
                assistantSession.speakThenListenAgain(question)
            }
        }
    }

    private fun applyPendingTomorrowTime() {
        val controller = draftController ?: return
        val pending = pendingPastTimeProposal
        if (pending == null || !pending.isCurrent(controller.draft.revision, editInteractionGeneration)) {
            endLocalInteraction("That clarification is no longer current. Please try the edit again.")
            return
        }
        applyValidatedFieldResult(
            fieldResolver.resolveScheduleProposal(
                dateText = pending.tomorrowDate,
                timeText = pending.proposedTime,
                draft = controller.draft
            )
        )
    }

    private fun requestedField(interaction: TaskDetailEditInteraction): TaskDetailEditField? =
        when (interaction) {
            TaskDetailEditInteraction.WAITING_FOR_TITLE -> TaskDetailEditField.TITLE
            TaskDetailEditInteraction.WAITING_FOR_DATE -> TaskDetailEditField.DATE
            TaskDetailEditInteraction.WAITING_FOR_TIME -> TaskDetailEditField.TIME
            else -> null
        }

    private fun localCandidate(result: TaskFieldEditResult): TaskDetailEditLocalCandidate =
        when (result) {
            is TaskFieldEditResult.Title -> TaskDetailEditLocalCandidate.Title(result.value)
            is TaskFieldEditResult.Schedule ->
                TaskDetailEditLocalCandidate.Schedule(result.dueDate, result.dueTime)
            is TaskFieldEditResult.NeedsClarification ->
                TaskDetailEditLocalCandidate.Clarification(result.prompt)
            is TaskFieldEditResult.PastSameDayTime ->
                TaskDetailEditLocalCandidate.PastSameDayTime(result.proposedTime, result.tomorrowDate)
            TaskFieldEditResult.Invalid,
            TaskFieldEditResult.PastSchedule -> TaskDetailEditLocalCandidate.Invalid
        }

    private fun formatContextDate(now: Calendar): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.UK).apply { timeZone = now.timeZone }.format(now.time)

    private fun formatContextTime(now: Calendar): String =
        SimpleDateFormat("hh:mm a", Locale.UK).apply { timeZone = now.timeZone }.format(now.time)

    private fun handleConfirmationResponse(text: String) {
        when (TaskDetailConfirmationInterpreter.interpret(text)) {
            TaskDetailConfirmation.YES -> handleConfirmationYes()
            TaskDetailConfirmation.NO -> handleConfirmationNo()
            TaskDetailConfirmation.CANCEL -> handleConfirmationCancel()
            TaskDetailConfirmation.UNCLEAR -> {
                assistantSession.expectConfirmation()
                assistantSession.speakThenListenAgain(
                    TaskDetailEditSpeechRenderer.retryQuestion(editInteraction)
                )
            }
        }
    }

    private fun handleConfirmationYes() {
        when (editInteraction) {
            TaskDetailEditInteraction.WAITING_FOR_SAVE_CONFIRMATION,
            TaskDetailEditInteraction.WAITING_FOR_HOME_CONFIRMATION,
            TaskDetailEditInteraction.WAITING_FOR_BACK_CONFIRMATION,
            TaskDetailEditInteraction.WAITING_FOR_ASSISTANT_EXIT_CONFIRMATION -> {
                val claim = pendingSaveClaim ?: return
                performAuthoritativeSave(claim, pendingExitAfterSave)
            }
            TaskDetailEditInteraction.WAITING_FOR_DELETE_DISCARD_CONFIRMATION -> {
                draftController?.discard()
                renderCurrentDraft()
                clearLocalInteraction()
                assistantSession.speakThenRun(
                    TaskScreenControlSpeechRenderer.openingDeleteConfirmation()
                ) { launchHomeAssistant(HomeAssistantEntryMode.TASK_DETAIL_DELETE_CONFIRMATION) }
            }
            else -> Unit
        }
    }

    private fun handleConfirmationNo() {
        when (editInteraction) {
            TaskDetailEditInteraction.WAITING_FOR_SAVE_CONFIRMATION ->
                endLocalInteraction("Changes not saved.")
            TaskDetailEditInteraction.WAITING_FOR_HOME_CONFIRMATION -> {
                draftController?.discard()
                renderCurrentDraft()
                clearLocalInteraction()
                assistantSession.speakThenRun("Changes discarded. Returning home.", ::returnHome)
            }
            TaskDetailEditInteraction.WAITING_FOR_BACK_CONFIRMATION -> {
                draftController?.discard()
                renderCurrentDraft()
                clearLocalInteraction()
                assistantSession.speakThenRun("Changes discarded. Going back.", ::returnBack)
            }
            TaskDetailEditInteraction.WAITING_FOR_ASSISTANT_EXIT_CONFIRMATION -> {
                draftController?.discard()
                renderCurrentDraft()
                val title = currentTask?.title.orEmpty()
                clearLocalInteraction()
                assistantSession.speakThenRun(
                    "Changes discarded. ${TaskScreenControlSpeechRenderer.openingTaskAssistant(title)}"
                ) { launchHomeAssistant(HomeAssistantEntryMode.TASK_DETAIL_CONTEXT) }
            }
            TaskDetailEditInteraction.WAITING_FOR_DELETE_DISCARD_CONFIRMATION ->
                endLocalInteraction("Deletion cancelled. Your changes are not saved.")
            else -> Unit
        }
    }

    private fun handleConfirmationCancel() {
        val speech = if (editInteraction == TaskDetailEditInteraction.WAITING_FOR_SAVE_CONFIRMATION) {
            "Save cancelled."
        } else {
            "Cancelled."
        }
        endLocalInteraction(speech)
    }

    private fun performAuthoritativeSave(
        claim: TaskDetailSaveClaim,
        afterSuccess: (() -> Unit)?
    ) {
        val controller = draftController ?: return
        if (!controller.isCurrent(claim)) {
            endLocalInteraction("That confirmation is no longer current. Please review your changes.")
            return
        }
        when (fieldResolver.validateDraft(claim.draft)) {
            TaskFieldEditResult.Invalid -> {
                endLocalInteraction("The task title or schedule is invalid. Your changes were not saved.")
                return
            }
            TaskFieldEditResult.PastSchedule -> {
                endLocalInteraction("That schedule is in the past. Your changes were not saved.")
                return
            }
            else -> Unit
        }
        editInteraction = TaskDetailEditInteraction.SAVING
        taskMutationInProgress = true
        setActionButtonsEnabled(false)
        assistantSession.prepareForContextEntry()
        lifecycleScope.launch {
            try {
                val updated = withContext(Dispatchers.IO) {
                    dao.updateTaskAndSubtasksIfAuthoritativeSnapshotMatches(
                        id = claim.base.taskId,
                        expectedTitle = claim.base.title,
                        expectedDueDate = claim.base.dueDate,
                        expectedDueTime = claim.base.dueTime,
                        expectedIsDone = claim.base.isDone,
                        newTitle = claim.draft.title,
                        newDueDate = claim.draft.dueDate,
                        newDueTime = claim.draft.dueTime
                    )
                }
                if (!updated) {
                    if (reloadAfterSaveConflict()) {
                        finishSaveSpeech(
                            "This task changed elsewhere. Your changes were not saved.",
                            afterSuccess = null
                        )
                    } else {
                        finishSaveSpeech(
                            getString(R.string.task_no_longer_available_spoken),
                            afterSuccess = ::finish
                        )
                    }
                    return@launch
                }
                val refreshed = loadAuthoritativeSnapshot()
                    ?: throw IllegalStateException("Task unavailable after save")
                controller.replaceFromRoom(refreshed.first)
                applySnapshot(refreshed)
                screenSpeechState.synchronize(refreshed)
                val reminderRestored = synchronizeReminderAfterSave(refreshed.first)
                saveButton.performConfirmationHapticFeedback()
                finishSaveSpeech(
                    if (reminderRestored) {
                        "Task changes saved."
                    } else {
                        "Task changes saved, but the reminder could not be scheduled."
                    },
                    afterSuccess
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.w(
                    TASK_DETAIL_UPDATE_TAG,
                    "operation=SAVE outcome=FAILED type=${exception.javaClass.simpleName}"
                )
                finishSaveSpeech("I could not save the task. Please try again.", null)
            } finally {
                taskMutationInProgress = false
                if (!isFinishing) setActionButtonsEnabled(true)
            }
        }
    }

    private suspend fun reloadAfterSaveConflict(): Boolean {
        val latest = loadAuthoritativeSnapshot() ?: return false
        draftController?.replaceFromRoom(latest.first)
        applySnapshot(latest)
        screenSpeechState.synchronize(latest)
        return true
    }

    private fun synchronizeReminderAfterSave(task: TaskEntity): Boolean {
        return runCatching {
            ReminderHelper.cancelReminder(this, task.id)
            if (task.isDone || task.parentTaskId != null) return@runCatching true
            val expectsReminder = !task.dueDate.isNullOrBlank() && !task.dueTime.isNullOrBlank()
            if (!expectsReminder) return@runCatching true
            when (ReminderEligibilityPolicy.evaluateForScheduling(task, System.currentTimeMillis())) {
                is ReminderSchedulingEligibility.Eligible ->
                    ReminderHelper.scheduleReminderFromTask(this, task)
                is ReminderSchedulingEligibility.Rejected -> false
            }
        }.getOrElse { exception ->
            Log.w(
                TASK_DETAIL_UPDATE_TAG,
                "operation=SAVE_REMINDER_SYNC outcome=FAILED type=${exception.javaClass.simpleName}"
            )
            false
        }
    }

    private fun finishSaveSpeech(text: String, afterSuccess: (() -> Unit)?) {
        clearLocalInteraction()
        if (activityStopped || isDestroyed) return
        if (afterSuccess == null) {
            assistantSession.startPassiveSession(clearConversation = false)
            assistantSession.speakThenStop(text)
        } else {
            assistantSession.startPassiveSession(clearConversation = false)
            assistantSession.speakThenRun(text, afterSuccess)
        }
    }

    private fun endLocalInteraction(text: String) {
        clearLocalInteraction()
        assistantSession.speakThenStop(text)
    }

    private fun clearLocalInteraction() {
        editInteractionGeneration += 1L
        editInteraction = TaskDetailEditInteraction.IDLE
        interactionRevision = -1L
        pendingSaveClaim = null
        pendingExitAfterSave = null
        pendingFieldClarification = null
        pendingPastTimeProposal = null
    }

    override fun onAssistantCancelled() {
        clearLocalInteraction()
    }

    override fun onAssistantSessionStopped() {
        clearLocalInteraction()
    }

    override fun onAssistantTypedInputRequested() {
        AccessibleAssistantInputDialog.show(
            activity = this,
            title = "Type assistant response",
            message = "Typed and voice responses use the same task detail flow.",
            emptyError = "Please type a response",
            onCancel = assistantSession::onTypedInputCancelled
        ) { typedText ->
            assistantSession.submitTypedText(typedText, clearConversation = false)
        }
    }

    private fun showManualTitleEditor() {
        if (taskMutationInProgress) return
        val controller = draftController ?: return
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(controller.draft.title)
            setSelection(text.length)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit task title")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = input.text.toString().trim()
                if (title.isBlank()) {
                    input.error = "Enter a valid title"
                    return@setOnClickListener
                }
                if (!controller.changeTitle(title)) {
                    input.error = "That title is already set"
                    return@setOnClickListener
                }
                renderCurrentDraft()
                dialog.dismiss()
                voiceHelper.speak(TaskDetailEditSpeechRenderer.titleChanged(title))
            }
        }
        dialog.show()
    }

    private fun showManualDatePicker() {
        if (taskMutationInProgress) return
        val controller = draftController ?: return
        val start = parseStoredDate(controller.draft.dueDate) ?: Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val selected = String.format(Locale.UK, "%02d/%02d/%04d", day, month + 1, year)
                applyManualScheduleResult(
                    fieldResolver.resolveDate(selected, controller.draft),
                    isDate = true
                )
            },
            start.get(Calendar.YEAR),
            start.get(Calendar.MONTH),
            start.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showManualTimePicker() {
        if (taskMutationInProgress) return
        val controller = draftController ?: return
        val start = parseStoredTime(controller.draft.dueTime) ?: Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hour, minute ->
                val selected = formatStoredTime(hour, minute)
                applyManualScheduleResult(
                    fieldResolver.resolveTime(selected, controller.draft),
                    isDate = false
                )
            },
            start.get(Calendar.HOUR_OF_DAY),
            start.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun applyManualScheduleResult(result: TaskFieldEditResult, isDate: Boolean) {
        val controller = draftController ?: return
        when (result) {
            is TaskFieldEditResult.Schedule -> {
                if (!controller.changeSchedule(result.dueDate, result.dueTime)) {
                    voiceHelper.speak("That schedule is already set.")
                    return
                }
                renderCurrentDraft()
                voiceHelper.speak(
                    if (isDate) TaskDetailEditSpeechRenderer.dateChanged(result.dueDate)
                    else TaskDetailEditSpeechRenderer.timeChanged(result.dueTime)
                )
            }
            TaskFieldEditResult.PastSchedule -> voiceHelper.speak("That schedule is in the past.")
            else -> voiceHelper.speak("That value could not be used.")
        }
    }

    private fun renderCurrentDraft() {
        val task = currentTask ?: return
        render(task, currentSubtasks)
    }

    private fun parseStoredDate(value: String?): Calendar? = parseStored("dd/MM/yyyy", value)

    private fun parseStoredTime(value: String?): Calendar? = parseStored("hh:mm a", value)

    private fun parseStored(pattern: String, value: String?): Calendar? = runCatching {
        if (value.isNullOrBlank()) return@runCatching null
        SimpleDateFormat(pattern, Locale.UK).apply { isLenient = false }
            .parse(value)
            ?.let { Calendar.getInstance().apply { time = it } }
    }.getOrNull()

    private fun formatStoredTime(hour: Int, minute: Int): String =
        SimpleDateFormat("hh:mm a", Locale.UK).format(
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }.time
        ).uppercase(Locale.UK)

    private fun showMissingTaskAndFinish() {
        if (isFinishing || missingTaskSpeechPending) return
        missingTaskSpeechPending = true
        Toast.makeText(this, R.string.task_no_longer_available, Toast.LENGTH_SHORT).show()
        voiceHelper.speakWithResult(getString(R.string.task_no_longer_available_spoken)) {
            runOnUiThread {
                missingTaskSpeechPending = false
                if (!isFinishing) finish()
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

}
