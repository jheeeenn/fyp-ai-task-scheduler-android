package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.example.myapplication.accessibility.AccessibilityActivity
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TodayTasksActivity : AccessibilityActivity() {
    private lateinit var adapter: TaskAdapter
    private lateinit var dao: com.example.myapplication.data.TaskDao
    private lateinit var voiceHelper: VoiceHelper
    private lateinit var navigationCoordinator: VoiceFirstNavigationCoordinator
    private lateinit var accessibilityController: TaskListAccessibilityController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_list)

        findViewById<TextView>(R.id.titleText).setText(R.string.today_tasks_title)
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
        navigationCoordinator = VoiceFirstNavigationCoordinator(
            speak = { text, onFinished ->
                voiceHelper.speakWithResult(text) { success ->
                    runOnUiThread { onFinished(success) }
                }
            }
        )
        accessibilityController = TaskListAccessibilityController(
            activity = this,
            emptyMessage = "No tasks scheduled for today"
        )
        adapter = TaskAdapter(
            tasks = mutableListOf(),
            onReadTask = ::speakIdentification,
            onOpenTask = { task ->
                navigationCoordinator.request(
                    TaskNavigationSpeechRenderer.openingDetails(task.title)
                ) { navigateToTaskDetails(task) }
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        VoiceFirstGestureBinder.bindAction(
            view = btnGoHome,
            speechProvider = TaskScreenControlSpeechRenderer::homeDescription,
            speak = ::speakIdentification,
            activate = {
                navigationCoordinator.request(TaskScreenControlSpeechRenderer.returningHome()) {
                    finish()
                }
            }
        )
        VoiceFirstGestureBinder.bindAction(
            view = btnTalkAssistant,
            speechProvider = TaskScreenControlSpeechRenderer::assistantDescription,
            speak = ::speakIdentification,
            activate = {
                navigationCoordinator.request(TaskScreenControlSpeechRenderer.openingAssistant()) {
                    startActivity(
                        HomeAssistantEntryContract.putGeneric(
                            Intent(this, HomeActivity::class.java).apply {
                                addFlags(
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                                )
                            }
                        )
                    )
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        loadTasks()
    }

    override fun onDestroy() {
        navigationCoordinator.cancelPending()
        voiceHelper.shutdown()
        super.onDestroy()
    }

    override fun onStop() {
        navigationCoordinator.cancelPending()
        super.onStop()
    }

    private fun loadTasks() {
        lifecycleScope.launch {
            val today = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).format(Date())
            val taskData = withContext(Dispatchers.IO) {
                val roots = dao.getRootTasksForDate(today)
                val subtasks = roots.associate { root -> root.id to dao.getSubtasks(root.id) }
                TaskListOrdering.today(roots) to subtasks
            }
            adapter.setTasksWithSubtasks(taskData.first, taskData.second)
            accessibilityController.render(taskData.first.size)
            speakIdentification(
                TaskListScreenSpeechRenderer.today(
                    TaskListScreenSpeechRenderer.activeCount(taskData.first)
                )
            )
        }
    }

    private fun speakIdentification(text: String) {
        navigationCoordinator.cancelPending()
        voiceHelper.speak(text)
    }

    private fun navigateToTaskDetails(task: TaskEntity) {
        startActivity(Intent(this, TaskDetailActivity::class.java).apply {
            putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.id)
        })
    }
}
