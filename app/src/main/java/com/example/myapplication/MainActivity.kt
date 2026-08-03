package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.accessibility.AccessibilityStateHelper
import com.example.myapplication.accessibility.AssistantAccessibilityState
import com.example.myapplication.accessibility.TaskListAccessibilityController
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: TaskAdapter
    private lateinit var dao: com.example.myapplication.data.TaskDao
    private lateinit var voiceHelper: VoiceHelper
    private lateinit var detailNavigation: TaskDetailNavigationCoordinator
    private lateinit var accessibilityController: TaskListAccessibilityController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_list)
        AccessibilityStateHelper.markHeading(findViewById(R.id.titleText))

        val recyclerView = findViewById<RecyclerView>(R.id.taskRecyclerView)
        val btnGoHome = findViewById<Button>(R.id.btnGoHome)
        val btnTalkAssistant = findViewById<Button>(R.id.btnTalkAssistant)
        AccessibilityStateHelper.updateAssistantState(
            btnTalkAssistant,
            AssistantAccessibilityState.READY,
            announce = false
        )

        dao = AppDatabase.getInstance(this).taskDao()
        voiceHelper = VoiceHelper(this)
        detailNavigation = TaskDetailNavigationCoordinator(
            speak = { text, onFinished ->
                voiceHelper.speakWithResult(text) { success ->
                    runOnUiThread { onFinished(success) }
                }
            },
            openDetails = ::navigateToTaskDetails
        )
        accessibilityController = TaskListAccessibilityController(
            activity = this,
            emptyMessage = "No scheduled tasks"
        )
        adapter = TaskAdapter(
            tasks = mutableListOf(),
            onReadTask = voiceHelper::speak,
            onOpenTask = { task -> detailNavigation.request(task) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        btnGoHome.setOnClickListenerWithHaptic { finish() }
        btnTalkAssistant.setOnClickListenerWithHaptic {
            startActivity(
                HomeAssistantEntryContract.putGeneric(
                    Intent(this, HomeActivity::class.java)
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        loadTasks()
    }

    override fun onDestroy() {
        voiceHelper.shutdown()
        super.onDestroy()
    }

    override fun onStop() {
        detailNavigation.cancelPending()
        super.onStop()
    }

    private fun loadTasks() {
        lifecycleScope.launch {
            val taskData = withContext(Dispatchers.IO) {
                val roots = dao.getRootTasks()
                    .filter { !it.dueDate.isNullOrBlank() && !it.dueTime.isNullOrBlank() }
                val subtasks = roots.associate { root -> root.id to dao.getSubtasks(root.id) }
                TaskListOrdering.byUrgency(roots) to subtasks
            }
            adapter.setTasksWithSubtasks(taskData.first, taskData.second)
            accessibilityController.render(taskData.first.size)
        }
    }

    private fun navigateToTaskDetails(task: TaskEntity) {
        startActivity(Intent(this, TaskDetailActivity::class.java).apply {
            putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.id)
        })
    }
}
