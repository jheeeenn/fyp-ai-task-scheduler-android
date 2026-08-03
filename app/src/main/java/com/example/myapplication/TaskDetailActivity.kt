package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.accessibility.AccessibilityStateHelper
import com.example.myapplication.accessibility.AssistantAccessibilityState
import com.example.myapplication.accessibility.TaskCardAccessibilitySemantics
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TaskDetailActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_TASK_ID = "task_id"
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
    private lateinit var editButton: Button
    private lateinit var deleteButton: Button

    private var taskId: Long = -1L
    private var currentTask: TaskEntity? = null
    private var currentSubtasks: List<TaskEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_detail)
        AccessibilityStateHelper.markHeading(findViewById(R.id.taskDetailHeading))

        dao = AppDatabase.getInstance(this).taskDao()
        voiceHelper = VoiceHelper(this)
        bindViews()
        bindInteractions()

        taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (!intent.hasExtra(EXTRA_TASK_ID) || taskId <= 0L) {
            showMissingTaskAndFinish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (taskId > 0L) loadAuthoritativeTask()
    }

    override fun onDestroy() {
        voiceHelper.shutdown()
        super.onDestroy()
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
        editButton = findViewById(R.id.btnEditTask)
        deleteButton = findViewById(R.id.btnDeleteTask)
    }

    private fun bindInteractions() {
        titleSurface.setOnClickListenerWithHaptic {
            currentTask?.let { task -> voiceHelper.speak(TaskDetailSpeechRenderer.title(task.title)) }
        }
        statusSurface.setOnClickListenerWithHaptic {
            currentTask?.let { task ->
                voiceHelper.speak(TaskDetailSpeechRenderer.status(statusFor(task)))
            }
        }
        dateSurface.setOnClickListenerWithHaptic {
            currentTask?.let { task -> voiceHelper.speak(TaskDetailSpeechRenderer.date(task.dueDate)) }
        }
        timeSurface.setOnClickListenerWithHaptic {
            currentTask?.let { task -> voiceHelper.speak(TaskDetailSpeechRenderer.time(task.dueTime)) }
        }
        subtaskProgressSurface.setOnClickListenerWithHaptic {
            voiceHelper.speak(
                TaskDetailSpeechRenderer.subtaskProgress(
                    currentSubtasks.count { it.isDone },
                    currentSubtasks.size
                )
            )
        }

        readAllButton.setOnClickListenerWithHaptic { readAll() }
        toggleDoneButton.setOnClickListenerWithHaptic { toggleDone() }
        editButton.setOnClickListenerWithHaptic { editTask() }
        deleteButton.setOnClickListenerWithHaptic { confirmDelete() }

        val btnGoHome = findViewById<Button>(R.id.btnGoHome)
        val btnTalkAssistant = findViewById<Button>(R.id.btnTalkAssistant)
        AccessibilityStateHelper.updateAssistantState(
            btnTalkAssistant,
            AssistantAccessibilityState.READY,
            announce = false
        )
        btnGoHome.setOnClickListenerWithHaptic {
            startActivity(Intent(this, HomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
        }
        btnTalkAssistant.setOnClickListenerWithHaptic {
            startActivity(Intent(this, HomeActivity::class.java).apply {
                putExtra("open_assistant_on_arrival", true)
            })
        }
    }

    private fun loadAuthoritativeTask() {
        setActionButtonsEnabled(false)
        lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val task = dao.getById(taskId) ?: return@withContext null
                task to dao.getSubtasks(taskId)
            }
            if (snapshot == null) {
                showMissingTaskAndFinish()
                return@launch
            }
            currentTask = snapshot.first
            currentSubtasks = snapshot.second
            render(snapshot.first, snapshot.second)
            setActionButtonsEnabled(true)
        }
    }

    private fun render(task: TaskEntity, subtasks: List<TaskEntity>) {
        val status = statusFor(task)
        val completedSubtasks = subtasks.count { it.isDone }

        titleText.text = task.title.ifBlank { getString(R.string.untitled_task) }
        statusText.text = status.visibleText
        dateText.text = TaskCardAccessibilitySemantics.spokenDate(task.dueDate)
        timeText.text = TaskCardAccessibilitySemantics.spokenTime(task.dueTime)
        toggleDoneButton.setText(if (task.isDone) R.string.undo else R.string.mark_done)

        titleSurface.contentDescription = TaskDetailSpeechRenderer.title(task.title)
        statusSurface.contentDescription = TaskDetailSpeechRenderer.status(status)
        dateSurface.contentDescription = TaskDetailSpeechRenderer.date(task.dueDate)
        timeSurface.contentDescription = TaskDetailSpeechRenderer.time(task.dueTime)
        applyStatusTreatment(status.visualStatus)
        renderSubtasks(subtasks, completedSubtasks)
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
        val task = currentTask ?: return
        voiceHelper.speak(
            TaskDetailSpeechRenderer.readAll(
                title = task.title,
                status = statusFor(task),
                dueDate = task.dueDate,
                dueTime = task.dueTime,
                completedSubtasks = currentSubtasks.count { it.isDone },
                totalSubtasks = currentSubtasks.size
            )
        )
    }

    private fun toggleDone() {
        val task = currentTask ?: return
        val newDoneState = !task.isDone
        setActionButtonsEnabled(false)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                dao.updateDoneStatusForTaskAndSubtasks(task.id, newDoneState)
            }
            if (newDoneState) {
                ReminderHelper.cancelReminder(this@TaskDetailActivity, task.id)
            } else {
                ReminderHelper.scheduleReminderFromTask(
                    this@TaskDetailActivity,
                    task.copy(isDone = false)
                )
            }
            loadAuthoritativeTask()
        }
    }

    private fun editTask() {
        val task = currentTask ?: return
        startActivity(Intent(this, EditTaskActivity::class.java).apply {
            putExtra("task_id", task.id)
            putExtra("task_title", task.title)
            putExtra("task_date", task.dueDate)
            putExtra("task_time", task.dueTime)
            putExtra("task_is_done", task.isDone)
        })
    }

    private fun confirmDelete() {
        val task = currentTask ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_task_title)
            .setMessage(getString(R.string.delete_task_message, task.title))
            .setPositiveButton(R.string.delete) { _, _ -> deleteTask(task) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteTask(task: TaskEntity) {
        setActionButtonsEnabled(false)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                dao.deleteTaskAndSubtasks(task.id)
            }
            ReminderHelper.cancelReminder(this@TaskDetailActivity, task.id)
            finish()
        }
    }

    private fun statusFor(task: TaskEntity): TaskStatusPresentation =
        TaskStatusPresenter.present(task.isDone, task.dueDate, task.dueTime)

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
        listOf(readAllButton, toggleDoneButton, editButton, deleteButton).forEach { button ->
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.55f
        }
    }

    private fun showMissingTaskAndFinish() {
        if (isFinishing) return
        Toast.makeText(this, R.string.task_no_longer_available, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
