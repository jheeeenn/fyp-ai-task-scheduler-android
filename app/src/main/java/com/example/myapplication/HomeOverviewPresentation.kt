package com.example.myapplication

import com.example.myapplication.data.TaskEntity
import java.util.Date
import java.util.TimeZone

data class HomeTaskPreviewPresentation(
    val task: TaskEntity,
    val title: String,
    val dueTime: String?,
    val visualStatus: TaskVisualStatus,
    val spokenSummary: String,
    val contentDescription: String
)

data class HomeOverviewPresentation(
    val todayTaskCount: Int,
    val overdueTaskCount: Int,
    val preview: HomeTaskPreviewPresentation?
)

object HomeOverviewPresenter {
    fun present(
        rootTasks: List<TaskEntity>,
        todayRootTasks: List<TaskEntity>,
        todayDate: String,
        now: Date = Date(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): HomeOverviewPresentation {
        val todayTaskCount = rootTasks.count { task ->
            !task.isDone && task.dueDate == todayDate
        }
        val overdueTaskCount = rootTasks.count { task ->
            !task.isDone && TaskStatusPresenter.present(
                isDone = false,
                dueDate = task.dueDate,
                dueTime = task.dueTime,
                now = now,
                timeZone = timeZone
            ).visualStatus == TaskVisualStatus.OVERDUE
        }
        val previewTask = TaskListOrdering.today(
            tasks = todayRootTasks,
            now = now,
            timeZone = timeZone
        ).firstOrNull()

        return HomeOverviewPresentation(
            todayTaskCount = todayTaskCount,
            overdueTaskCount = overdueTaskCount,
            preview = previewTask?.let { task ->
                val status = TaskStatusPresenter.present(
                    isDone = task.isDone,
                    dueDate = task.dueDate,
                    dueTime = task.dueTime,
                    now = now,
                    timeZone = timeZone
                )
                val card = TaskListCardSpeechRenderer.render(
                    title = task.title,
                    status = status
                )
                HomeTaskPreviewPresentation(
                    task = task,
                    title = task.title.ifBlank { "Untitled task" },
                    dueTime = task.dueTime?.trim()?.takeIf(String::isNotEmpty),
                    visualStatus = status.visualStatus,
                    spokenSummary = card.spokenSummary,
                    contentDescription = card.contentDescription
                )
            }
        )
    }
}
