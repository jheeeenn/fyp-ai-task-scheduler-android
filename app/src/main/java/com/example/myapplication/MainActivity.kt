package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: TaskAdapter
    private lateinit var dao: com.example.myapplication.data.TaskDao
    private var allTasks: List<TaskEntity> = emptyList()
    private var currentFilter = "all"
    private var selectedTaskId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_task_list)

        val recyclerView = findViewById<RecyclerView>(R.id.taskRecyclerView)

        val btnTaskDone = findViewById<Button>(R.id.btnTaskDone)
        val btnTaskEdit = findViewById<Button>(R.id.btnTaskEdit)
        val btnTaskDelete = findViewById<Button>(R.id.btnTaskDelete)
        val btnCreateNewTask = findViewById<Button>(R.id.btnCreateNewTask)
        val btnGoHome = findViewById<Button>(R.id.btnGoHome)
        val btnTalkAssistant = findViewById<Button>(R.id.btnTalkAssistant)

        dao = AppDatabase.getInstance(this).taskDao()

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



        btnTaskDone.setOnClickListener {
            val task = getCurrentlySelectedTask() ?: run {
                Toast.makeText(this, "Please select a task first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val newDoneState = !task.isDone

                withContext(Dispatchers.IO) {
                    dao.updateDoneStatus(task.id, newDoneState)
                }

                if (newDoneState) {
                    ReminderHelper.cancelReminder(this@MainActivity, task.id.toInt())
                } else {
                    ReminderHelper.scheduleReminderFromTask(
                        this@MainActivity,
                        task.copy(isDone = false)
                    )
                }

                loadTasks(keepSelection = task.id)
            }
        }

        btnTaskEdit.setOnClickListener {
            val task = getCurrentlySelectedTask() ?: run {
                Toast.makeText(this, "Please select a task first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, EditTaskActivity::class.java)
            intent.putExtra("task_id", task.id)
            intent.putExtra("task_title", task.title)
            intent.putExtra("task_date", task.dueDate)
            intent.putExtra("task_time", task.dueTime)
            startActivity(intent)
        }

        btnTaskDelete.setOnClickListener {
            val task = getCurrentlySelectedTask() ?: run {
                Toast.makeText(this, "Please select a task first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Delete task?")
                .setMessage("Are you sure you want to delete:\n\n${task.title}")
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            dao.deleteById(task.id)
                        }
                        ReminderHelper.cancelReminder(this@MainActivity, task.id.toInt())
                        selectedTaskId = null
                        loadTasks()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnCreateNewTask.setOnClickListener {
            startActivity(Intent(this, CreateTaskActivity::class.java))
        }

        btnGoHome.setOnClickListener {
            finish()
            //startActivity(Intent(this, HomeActivity::class.java))
        }

        btnTalkAssistant.setOnClickListener {
            //startActivity(Intent(this, AssistantActivity::class.java))
            startActivity(Intent(this, HomeActivity::class.java))
            android.widget.Toast.makeText(
                this,
                "Voice assistant will be added here",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        updateActionButtonsState()
    }

    override fun onResume() {
        super.onResume()
        loadTasks()
    }

    private fun loadTasks(keepSelection: Long? = selectedTaskId) {
        lifecycleScope.launch {
            val tasks = withContext(Dispatchers.IO) { dao.getAll() }
            allTasks = sortTasksByDateTime(tasks.filter { it.dueDate != null && it.dueTime != null })
            adapter.setTasks(allTasks)

            selectedTaskId = if (allTasks.any { it.id == keepSelection }) keepSelection else null
            adapter.setSelectedTaskId(selectedTaskId)

            updateActionButtonsState()
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

    /*private fun showTaskOptions(task: TaskEntity) {
        val options = arrayOf("Edit", "Delete", "Cancel")

        AlertDialog.Builder(this)
            .setTitle("Task Options")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(this, EditTaskActivity::class.java)
                        intent.putExtra("task_id", task.id)
                        intent.putExtra("task_title", task.title)
                        intent.putExtra("task_date", task.dueDate)
                        intent.putExtra("task_time", task.dueTime)
                        startActivity(intent)
                    }

                    1 -> {
                        AlertDialog.Builder(this)
                            .setTitle("Delete task?")
                            .setMessage("Are you sure you want to delete:\n\n${task.title}")
                            .setPositiveButton("Delete") { _, _ ->
                                lifecycleScope.launch {
                                    withContext(Dispatchers.IO) {
                                        dao.deleteById(task.id)
                                    }
                                    loadTasks()
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }

                    else -> dialog.dismiss()
                }
            }
            .show()
    }*/
}
