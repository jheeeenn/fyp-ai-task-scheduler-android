package com.example.myapplication

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
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
        val taskText: TextView = itemView.findViewById(R.id.taskText)
        val taskStatusText: TextView = itemView.findViewById(R.id.taskStatusText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
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

        holder.itemView.setOnClickListener { view ->
            val clickedPosition = holder.bindingAdapterPosition
            if (clickedPosition == RecyclerView.NO_POSITION) return@setOnClickListener
            view.performTapHapticFeedback()
            onOpenTask(tasks[clickedPosition])
        }
        ViewCompat.replaceAccessibilityAction(
            holder.itemView,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
            holder.itemView.context.getString(R.string.open_task_details)
        ) { view, _ ->
            view.performClick()
            true
        }

        val actions = TaskCardGestureActions(
            read = {
                holder.itemView.performTapHapticFeedback()
                onReadTask(card.spokenSummary)
            },
            openDetails = { holder.itemView.performClick() }
        )
        val gestureDetector = GestureDetector(
            holder.itemView.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(event: MotionEvent): Boolean =
                    actions.onSingleTapConfirmed()

                override fun onDoubleTap(event: MotionEvent): Boolean =
                    actions.onDoubleTap()

                override fun onScroll(
                    firstEvent: MotionEvent?,
                    currentEvent: MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean = actions.onScroll()
            }
        )
        holder.itemView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
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
        val (background, statusColor) = when (visualStatus) {
            TaskVisualStatus.OVERDUE -> R.drawable.bg_task_status_overdue to R.color.task_status_overdue_text
            TaskVisualStatus.DUE_TODAY -> R.drawable.bg_task_status_today to R.color.task_status_today_text
            TaskVisualStatus.UPCOMING -> R.drawable.bg_task_status_upcoming to R.color.task_status_upcoming_text
            TaskVisualStatus.COMPLETED -> R.drawable.bg_task_status_completed to R.color.task_status_completed_text
            TaskVisualStatus.UNSCHEDULED -> R.drawable.bg_task_status_unscheduled to R.color.task_status_unscheduled_text
        }
        holder.itemView.setBackgroundResource(background)
        holder.taskStatusText.setTextColor(
            ContextCompat.getColor(holder.itemView.context, statusColor)
        )
    }
}
