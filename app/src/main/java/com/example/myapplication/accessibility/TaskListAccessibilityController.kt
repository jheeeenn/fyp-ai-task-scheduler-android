package com.example.myapplication.accessibility

import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R

class TaskListAccessibilityController(
    activity: AppCompatActivity,
    private val emptyMessage: String
) {
    private val emptyState: TextView = activity.findViewById(R.id.emptyTaskText)

    fun render(taskCount: Int) {
        emptyState.text = emptyMessage
        emptyState.visibility = if (taskCount == 0) View.VISIBLE else View.GONE
    }
}
