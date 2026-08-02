package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button

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
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: TaskAdapter
    private lateinit var dao: com.example.myapplication.data.TaskDao
    private var allTasks: List<TaskEntity> = emptyList()

    private var selectedTaskId: Long? = null
    private lateinit var accessibilityController: TaskListAccessibilityController
    private var announceRefreshOnResume = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_task_list)
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
            screen = "SCHEDULED_TASKS",
            itemName = "scheduled task",
            emptyMessage = "No scheduled tasks"
        )

        //val tasks = mutableListOf<String>()
        //val tasks = mutableListOf<TaskEntity>()


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
                    ReminderHelper.cancelReminder(this@MainActivity, task.id)
                } else {
                    ReminderHelper.scheduleReminderFromTask(
                        this@MainActivity,
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
                        ReminderHelper.cancelReminder(this@MainActivity, task.id)
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
            val taskData = withContext(Dispatchers.IO) {
                val roots = dao.getRootTasks()
                val subtasks = roots.associate { root -> root.id to dao.getSubtasks(root.id) }
                roots to subtasks
            }
            allTasks = sortTasksByDateTime(taskData.first.filter { it.dueDate != null && it.dueTime != null })
            adapter.setTasksWithSubtasks(allTasks, taskData.second)

            selectedTaskId = if (allTasks.any { it.id == keepSelection }) keepSelection else null
            adapter.setSelectedTaskId(selectedTaskId)

            updateActionButtonsState()
            accessibilityController.render(allTasks.size, mutationAnnouncement)
        }
    }


    private fun sortTasksByDateTime(taskList: List<TaskEntity>): List<TaskEntity> {
        val formatter = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())

        return taskList.sortedWith(compareBy<TaskEntity> { task ->
            if (task.dueDate == null || task.dueTime == null) {
                Long.MAX_VALUE
            } else {
                try {
                    formatter.parse("${task.dueDate} ${task.dueTime}")?.time ?: Long.MAX_VALUE
                } catch (e: Exception) {
                    Long.MAX_VALUE
                }
            }
        }.thenBy { it.id })
    }
    private fun getCurrentlySelectedTask(): TaskEntity? {
        return allTasks.find { it.id == selectedTaskId }
    }

    // helper function for button ui dimming
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
