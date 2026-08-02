package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.TaskEntity
import com.example.myapplication.accessibility.TaskListAccessibilityController
import com.example.myapplication.accessibility.AccessibilityStateHelper
import com.example.myapplication.accessibility.AssistantAccessibilityState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TodayTasksActivity : AppCompatActivity() {

    private lateinit var adapter: TaskAdapter
    private lateinit var dao: com.example.myapplication.data.TaskDao
    private var todayTasks: List<TaskEntity> = emptyList()

    private var selectedTaskId: Long? = null
    private lateinit var accessibilityController: TaskListAccessibilityController
    private var announceRefreshOnResume = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_list)

        findViewById<TextView>(R.id.titleText).text = "Today Tasks"
        AccessibilityStateHelper.markHeading(findViewById(R.id.titleText))

        val recyclerView = findViewById<RecyclerView>(R.id.taskRecyclerView)

        val btnTaskDone = findViewById<Button>(R.id.btnTaskDone)
        val btnTaskEdit = findViewById<Button>(R.id.btnTaskEdit)
        val btnTaskDelete = findViewById<Button>(R.id.btnTaskDelete)
        val btnCreateNewTask = findViewById<Button>(R.id.btnCreateNewTask)
        val btnGoHome = findViewById<Button>(R.id.btnGoHome)
        val btnTalkAssistant = findViewById<Button>(R.id.btnTalkAssistant)
        AccessibilityStateHelper.updateAssistantState(
            btnTalkAssistant,
            AssistantAccessibilityState.READY,
            announce = false
        )

        dao = AppDatabase.getInstance(this).taskDao()
        accessibilityController = TaskListAccessibilityController(
            activity = this,
            screen = "TODAY_TASKS",
            itemName = "task",
            emptyMessage = "No tasks scheduled for today"
        )

        adapter = TaskAdapter(
            mutableListOf(),
            onTaskSelected = { task ->
                selectedTaskId = task?.id
                updateActionButtonsState()
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter



        btnTaskDone.setOnClickListenerWithHaptic {
            val task = getCurrentlySelectedTask() ?: run {
                Toast.makeText(this, "Please select a task first", Toast.LENGTH_SHORT).show()
                return@setOnClickListenerWithHaptic
            }

            lifecycleScope.launch {
                val newDoneState = !task.isDone

                withContext(Dispatchers.IO) {
                    dao.updateDoneStatusForTaskAndSubtasks(task.id, newDoneState)
                }

                if (newDoneState) {
                    ReminderHelper.cancelReminder(this@TodayTasksActivity, task.id)
                } else {
                    ReminderHelper.scheduleReminderFromTask(
                        this@TodayTasksActivity,
                        task.copy(isDone = false)
                    )
                }

                loadTasks(
                    keepSelection = task.id,
                    mutationAnnouncement = "Task status updated. Task list refreshed"
                )
            }
        }

        btnTaskEdit.setOnClickListenerWithHaptic {
            val task = getCurrentlySelectedTask() ?: run {
                Toast.makeText(this, "Please select a task first", Toast.LENGTH_SHORT).show()
                return@setOnClickListenerWithHaptic
            }

            val intent = Intent(this, EditTaskActivity::class.java)
            intent.putExtra("task_id", task.id)
            intent.putExtra("task_title", task.title)
            intent.putExtra("task_date", task.dueDate)
            intent.putExtra("task_time", task.dueTime)
            announceRefreshOnResume = true
            startActivity(intent)
        }

        btnTaskDelete.setOnClickListenerWithHaptic {
            val task = getCurrentlySelectedTask() ?: run {
                Toast.makeText(this, "Please select a task first", Toast.LENGTH_SHORT).show()
                return@setOnClickListenerWithHaptic
            }

            AlertDialog.Builder(this)
                .setTitle("Delete task?")
                .setMessage("Are you sure you want to delete:\n\n${task.title}")
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            dao.deleteTaskAndSubtasks(task.id)
                        }
                        ReminderHelper.cancelReminder(this@TodayTasksActivity, task.id)
                        selectedTaskId = null
                        loadTasks(mutationAnnouncement = "Task deleted. Task list refreshed")
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnCreateNewTask.setOnClickListenerWithHaptic {
            announceRefreshOnResume = true
            startActivity(Intent(this, CreateTaskActivity::class.java))
        }

        btnGoHome.setOnClickListenerWithHaptic {
            finish()
            //startActivity(Intent(this, HomeActivity::class.java))
        }

        btnTalkAssistant.setOnClickListenerWithHaptic {
            val intent = Intent(this, HomeActivity::class.java).apply {
                putExtra("open_assistant_on_arrival", true)
            }
            startActivity(intent)
        }
        updateActionButtonsState()
    }

    override fun onResume() {
        super.onResume()
        val announcement = if (announceRefreshOnResume) "Task list refreshed" else null
        announceRefreshOnResume = false
        loadTasks(mutationAnnouncement = announcement)
    }

    private fun loadTasks(
        keepSelection: Long? = selectedTaskId,
        mutationAnnouncement: String? = null
    ) {
        lifecycleScope.launch {
            val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                .format(Calendar.getInstance().time)
            val taskData = withContext(Dispatchers.IO) {
                val roots = dao.getRootTasksForDate(today)
                val subtasks = roots.associate { root -> root.id to dao.getSubtasks(root.id) }
                roots to subtasks
            }

            todayTasks = taskData.first
            adapter.setTasksWithSubtasks(todayTasks, taskData.second)

            selectedTaskId = if (todayTasks.any { it.id == keepSelection }) keepSelection else null
            adapter.setSelectedTaskId(selectedTaskId)

            updateActionButtonsState()
            accessibilityController.render(todayTasks.size, mutationAnnouncement)
        }
    }


    private fun getCurrentlySelectedTask(): TaskEntity? {
        return todayTasks.find { it.id == selectedTaskId }
    }

    // helper fucntion for dimming button ui
    private fun updateActionButtonsState() {
        val btnTaskDone = findViewById<Button>(R.id.btnTaskDone)
        val btnTaskEdit = findViewById<Button>(R.id.btnTaskEdit)
        val btnTaskDelete = findViewById<Button>(R.id.btnTaskDelete)

        val selectedTask = getCurrentlySelectedTask()
        val hasSelection = selectedTask != null

        btnTaskDone.isEnabled = hasSelection
        btnTaskEdit.isEnabled = hasSelection
        btnTaskDelete.isEnabled = hasSelection

        val enabledBg = R.drawable.bg_action_button
        val disabledBg = R.drawable.bg_action_button_disabled

        btnTaskDone.setBackgroundResource(if (hasSelection) enabledBg else disabledBg)
        btnTaskEdit.setBackgroundResource(if (hasSelection) enabledBg else disabledBg)
        btnTaskDelete.setBackgroundResource(if (hasSelection) enabledBg else disabledBg)

        btnTaskDone.text = if (selectedTask?.isDone == true) "Undo" else "Done"
    }


}
