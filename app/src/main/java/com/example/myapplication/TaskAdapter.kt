package com.example.myapplication


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.TaskEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TaskAdapter(
    private val tasks: MutableList<TaskEntity>,
    private val onTaskSelected: (TaskEntity?) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private var selectedTaskId: Long? = null
    private var subtasksByParentId: Map<Long, List<TaskEntity>> = emptyMap()

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val taskCardRoot: LinearLayout = itemView.findViewById(R.id.taskCardRoot)
        val taskText: TextView = itemView.findViewById(R.id.taskText)
        val taskDateText: TextView = itemView.findViewById(R.id.taskDateText)
        val taskTimeText: TextView = itemView.findViewById(R.id.taskTimeText)
        val taskStatusText: TextView = itemView.findViewById(R.id.taskStatusText)
        val taskSubtaskSummaryText: TextView = itemView.findViewById(R.id.taskSubtaskSummaryText)
        val taskSubtaskTitlesText: TextView = itemView.findViewById(R.id.taskSubtaskTitlesText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]

        holder.taskText.text = if (task.isDone) "✓ ${task.title}" else task.title
        holder.taskDateText.text = task.dueDate ?: "No date set"
        holder.taskTimeText.text = task.dueTime ?: "No time set"
        holder.taskStatusText.text = getTaskStatus(task)

        val subtasks = subtasksByParentId[task.id].orEmpty()
        if (subtasks.isNotEmpty()) {
            val completedCount = subtasks.count { it.isDone }
            holder.taskSubtaskSummaryText.visibility = View.VISIBLE
            holder.taskSubtaskSummaryText.text = "Subtasks: $completedCount of ${subtasks.size} completed"
            holder.taskSubtaskTitlesText.visibility = View.VISIBLE
            holder.taskSubtaskTitlesText.text = subtasks.joinToString("\n") { subtask ->
                val prefix = if (subtask.isDone) "✓" else "•"
                "$prefix ${subtask.title}"
            }
        } else {
            holder.taskSubtaskSummaryText.visibility = View.GONE
            holder.taskSubtaskTitlesText.visibility = View.GONE
        }

        val isSelected = task.id == selectedTaskId
        holder.taskCardRoot.setBackgroundResource(
            if (isSelected) R.drawable.bg_task_card_selected
            else R.drawable.bg_task_card
        )

        holder.itemView.setOnClickListenerWithHaptic {
            selectedTaskId = if (selectedTaskId == task.id) null else task.id
            notifyDataSetChanged()
            onTaskSelected(tasks.find { it.id == selectedTaskId })
        }
    }

    override fun getItemCount(): Int = tasks.size

    fun setTasks(newTasks: List<TaskEntity>) {
        setTasksWithSubtasks(newTasks, emptyMap())
    }

    fun setTasksWithSubtasks(
        newTasks: List<TaskEntity>,
        newSubtasksByParentId: Map<Long, List<TaskEntity>>
    ) {
        tasks.clear()
        tasks.addAll(newTasks)
        subtasksByParentId = newSubtasksByParentId

        if (selectedTaskId != null && tasks.none { it.id == selectedTaskId }) {
            selectedTaskId = null
        }

        notifyDataSetChanged()
    }

    fun getSelectedTask(): TaskEntity? {
        return tasks.find { it.id == selectedTaskId }
    }

    fun getSelectedTaskId(): Long? {
        return selectedTaskId
    }

    fun setSelectedTaskId(taskId: Long?) {
        selectedTaskId = taskId
        notifyDataSetChanged()
    }

    private fun getTaskStatus(task: TaskEntity): String {
        if (task.isDone) return "Completed"
        if (task.dueDate == null || task.dueTime == null) return "Unscheduled"

        return try {
            val formatter = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
            val taskDateTime = formatter.parse("${task.dueDate} ${task.dueTime}") ?: return "Upcoming"

            val now = Calendar.getInstance().time
            val todayFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val todayString = todayFormatter.format(now)

            when {
                taskDateTime.before(now) -> "Overdue"
                task.dueDate == todayString -> "Today"
                else -> "Upcoming"
            }
        } catch (e: Exception) {
            "Upcoming"
        }
    }
}
