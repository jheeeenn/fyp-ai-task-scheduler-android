package com.example.myapplication.accessibility

import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R

class TaskListAccessibilityController(
    activity: AppCompatActivity,
    private val screen: String,
    private val itemName: String,
    private val emptyMessage: String
) {
    private val root: View = activity.findViewById(R.id.taskListRoot)
    private val summary: TextView = activity.findViewById(R.id.taskListSummary)
    private val emptyState: TextView = activity.findViewById(R.id.emptyTaskText)

    fun render(taskCount: Int, mutationAnnouncement: String? = null) {
        summary.text = if (taskCount == 1) "1 $itemName" else "$taskCount ${itemName}s"
        emptyState.text = emptyMessage
        emptyState.visibility = if (taskCount == 0) View.VISIBLE else View.GONE
        if (mutationAnnouncement != null) {
            AccessibilityAnnouncementHelper.announce(
                root,
                screen = screen,
                event = "TASK_LIST_REFRESHED",
                message = mutationAnnouncement
            )
        }
    }
}
