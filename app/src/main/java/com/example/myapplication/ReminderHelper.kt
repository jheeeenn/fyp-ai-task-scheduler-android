package com.example.myapplication

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.myapplication.data.TaskEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object ReminderHelper {

    fun cancelReminder(context: Context, taskId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleReminderFromTask(context: Context, task: TaskEntity): Boolean {
        if (task.dueDate.isNullOrBlank() || task.dueTime.isNullOrBlank()) return false
        if (task.isDone) return false

        val formatter = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        val parsedDate = try {
            formatter.parse("${task.dueDate} ${task.dueTime}")
        } catch (_: Exception) {
            null
        } ?: return false

        val triggerCalendar = Calendar.getInstance().apply {
            time = parsedDate
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (triggerCalendar.before(Calendar.getInstance())) {
            return false
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            return false
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("task_title", task.title)
            putExtra("task_id", task.id.toInt())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerCalendar.timeInMillis,
            pendingIntent
        )

        return true
    }
}