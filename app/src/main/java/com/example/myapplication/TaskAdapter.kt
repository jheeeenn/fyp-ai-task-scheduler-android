package com.example.myapplication


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.accessibility.AccessibilityAnnouncementHelper
import com.example.myapplication.accessibility.TaskCardAccessibilityContent
import com.example.myapplication.accessibility.TaskCardAccessibilitySemantics
import com.example.myapplication.data.TaskEntity

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
        val taskStatus = TaskCardAccessibilitySemantics.status(
            isDone = task.isDone,
            dueDate = task.dueDate,
            dueTime = task.dueTime
        )
        holder.taskStatusText.text = taskStatus

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
        holder.itemView.isSelected = isSelected
        holder.taskCardRoot.setBackgroundResource(
            if (isSelected) R.drawable.bg_task_card_selected
            else R.drawable.bg_task_card
        )
        holder.itemView.contentDescription = TaskCardAccessibilitySemantics.summary(
            TaskCardAccessibilityContent(
                title = task.title,
                date = task.dueDate,
                time = task.dueTime,
                status = taskStatus,
                completedSubtasks = subtasks.count { it.isDone },
                totalSubtasks = subtasks.size,
                isSelected = isSelected
            )
        )
        ViewCompat.replaceAccessibilityAction(
            holder.itemView,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
            if (isSelected) "Deselect task" else "Select task"
        ) { view, _ ->
            view.performClick()
            true
        }

        holder.itemView.setOnClickListenerWithHaptic {
            val clickedPosition = holder.bindingAdapterPosition
            if (clickedPosition == RecyclerView.NO_POSITION) return@setOnClickListenerWithHaptic
            val clickedTask = tasks[clickedPosition]
            val previousSelectedId = selectedTaskId
            selectedTaskId = if (previousSelectedId == clickedTask.id) null else clickedTask.id
            val previousPosition = tasks.indexOfFirst { it.id == previousSelectedId }
            if (previousPosition >= 0 && previousPosition != clickedPosition) {
                notifyItemChanged(previousPosition)
            }
            notifyItemChanged(clickedPosition)
            onTaskSelected(tasks.find { it.id == selectedTaskId })
            AccessibilityAnnouncementHelper.announce(
                holder.itemView,
                screen = "TASK_LIST",
                event = "TASK_SELECTION_CHANGED",
                message = if (selectedTaskId == null) "Task deselected" else "Task selected"
            )
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

}
