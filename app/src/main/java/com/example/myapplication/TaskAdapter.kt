package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.example.myapplication.accessibility.resolveThemeColor
import com.example.myapplication.data.TaskEntity
import java.util.Date

class TaskAdapter(
    private val tasks: MutableList<TaskEntity>,
    private val onReadTask: (String) -> Unit,
    private val onOpenTask: (TaskEntity) -> Unit,
    private val nowProvider: () -> Date = { Date() }
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private var subtasksByParentId: Map<Long, List<TaskEntity>> = emptyMap()

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val statusSignifier: View = itemView.findViewById(R.id.taskStatusSignifier)
        val taskText: TextView = itemView.findViewById(R.id.taskText)
        val taskStatusText: TextView = itemView.findViewById(R.id.taskStatusText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        val subtasks = subtasksByParentId[task.id].orEmpty()
        val completedSubtasks = subtasks.count { it.isDone }
        val status = TaskStatusPresenter.present(
            isDone = task.isDone,
            dueDate = task.dueDate,
            dueTime = task.dueTime,
            now = nowProvider()
        )
        val card = TaskListCardSpeechRenderer.render(
            title = task.title,
            status = status,
            completedSubtasks = completedSubtasks,
            totalSubtasks = subtasks.size
        )

        holder.taskText.text = task.title.ifBlank {
            holder.itemView.context.getString(R.string.untitled_task)
        }
        holder.taskStatusText.text = card.visibleStatus
        holder.itemView.contentDescription = card.contentDescription
        applyStatusTreatment(holder, status.visualStatus)

        VoiceFirstGestureBinder.bindAction(
            view = holder.itemView,
            speechProvider = { currentCardPresentation(holder)?.spokenSummary },
            speak = onReadTask,
            activate = {
                val clickedPosition = holder.bindingAdapterPosition
                if (clickedPosition != RecyclerView.NO_POSITION) {
                    onOpenTask(tasks[clickedPosition])
                }
            }
        )
        ViewCompat.replaceAccessibilityAction(
            holder.itemView,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
            holder.itemView.context.getString(R.string.open_task_details)
        ) { view, _ ->
            view.performClick()
            true
        }

    }

    override fun getItemCount(): Int = tasks.size

    fun setTasksWithSubtasks(
        newTasks: List<TaskEntity>,
        newSubtasksByParentId: Map<Long, List<TaskEntity>>
    ) {
        val incomingTasks = newTasks.toList()
        val incomingSubtasks = newSubtasksByParentId.mapValues { (_, subtasks) ->
            subtasks.toList()
        }
        val previousTasks = tasks.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = previousTasks.size

            override fun getNewListSize(): Int = incomingTasks.size

            override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean =
                previousTasks[oldPosition].id == incomingTasks[newPosition].id

            // A reload must also refresh time-relative wording even when Room data is unchanged.
            override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean = false
        })
        tasks.clear()
        tasks.addAll(incomingTasks)
        subtasksByParentId = incomingSubtasks
        diff.dispatchUpdatesTo(this)
    }

    private fun applyStatusTreatment(
        holder: TaskViewHolder,
        visualStatus: TaskVisualStatus
    ) {
        val statusColorAttribute = when (visualStatus) {
            TaskVisualStatus.OVERDUE -> R.attr.appColorTaskListStatusOverdue
            TaskVisualStatus.DUE_TODAY -> R.attr.appColorTaskListStatusDueToday
            TaskVisualStatus.UPCOMING -> R.attr.appColorTaskListStatusUpcoming
            TaskVisualStatus.COMPLETED -> R.attr.appColorTaskListStatusCompleted
            TaskVisualStatus.UNSCHEDULED -> R.attr.appColorTaskListStatusUnscheduled
        }
        val statusColor = holder.itemView.context.resolveThemeColor(statusColorAttribute)
        holder.statusSignifier.setBackgroundColor(statusColor)
        holder.taskStatusText.setTextColor(statusColor)
    }

    private fun currentCardPresentation(holder: TaskViewHolder): TaskListCardPresentation? {
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return null
        val task = tasks[position]
        val subtasks = subtasksByParentId[task.id].orEmpty()
        return TaskListCardSpeechRenderer.render(
            title = task.title,
            status = TaskStatusPresenter.present(
                isDone = task.isDone,
                dueDate = task.dueDate,
                dueTime = task.dueTime,
                now = nowProvider()
            ),
            completedSubtasks = subtasks.count { it.isDone },
            totalSubtasks = subtasks.size
        )
    }
}
