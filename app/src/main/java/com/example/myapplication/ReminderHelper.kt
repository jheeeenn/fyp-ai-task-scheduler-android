package com.example.myapplication

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.myapplication.data.TaskEntity
import com.example.myapplication.reminder.ReminderAlarmIdentity
import com.example.myapplication.reminder.ReminderAlarmSpec
import com.example.myapplication.reminder.ReminderEligibilityPolicy
import com.example.myapplication.reminder.ReminderEscalationStage
import com.example.myapplication.reminder.ReminderSchedulingEligibility
import com.example.myapplication.reminder.ReminderSequenceCoordinator
import com.example.myapplication.reminder.ReminderStageCanceller
import com.example.myapplication.reminder.ReminderStageScheduler

object ReminderHelper {
    private const val SCHEDULE_TAG = "REMINDER_ESCALATION_SCHEDULE"
    private const val CANCEL_TAG = "REMINDER_ESCALATION_CANCEL"

    fun cancelReminder(context: Context, taskId: Long) {
        if (taskId <= 0L) {
            logCancel(taskId, null, "INVALID_TASK_ID")
            return
        }
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            logCancel(taskId, null, "ALARM_MANAGER_UNAVAILABLE")
            return
        }

        val cancelled = ReminderSequenceCoordinator.cancelAllStages(
            taskId = taskId,
            canceller = alarmCanceller(context, alarmManager)
        )
        logCancel(taskId, null, if (cancelled) "SEQUENCE_CANCELLED" else "PARTIAL_FAILURE")
    }

    fun scheduleReminderFromTask(context: Context, task: TaskEntity): Boolean {
        if (task.id <= 0L) {
            logSchedule(task.id, null, null, null, "INVALID_TASK_ID")
            return false
        }

        val eligibility = ReminderEligibilityPolicy.evaluateForScheduling(
            task = task,
            nowEpochMillis = System.currentTimeMillis()
        )
        if (eligibility is ReminderSchedulingEligibility.Rejected) {
            logSchedule(task.id, null, null, null, "REJECTED_${eligibility.reason}")
            return false
        }
        val originalDueEpochMillis =
            (eligibility as ReminderSchedulingEligibility.Eligible).originalDueEpochMillis

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            logSchedule(
                task.id,
                null,
                originalDueEpochMillis,
                null,
                "ALARM_MANAGER_UNAVAILABLE"
            )
            return false
        }

        val exactAlarmsAvailable = try {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
        } catch (exception: RuntimeException) {
            logSchedule(
                task.id,
                null,
                originalDueEpochMillis,
                null,
                "EXACT_ALARM_PERMISSION_CHECK_FAILED_${exception.javaClass.simpleName}"
            )
            false
        }
        if (!exactAlarmsAvailable) {
            logSchedule(
                task.id,
                null,
                originalDueEpochMillis,
                null,
                "EXACT_ALARM_PERMISSION_UNAVAILABLE"
            )
            return false
        }

        val scheduled = ReminderSequenceCoordinator.scheduleCompleteSequence(
            taskId = task.id,
            originalDueEpochMillis = originalDueEpochMillis,
            scheduler = alarmScheduler(context, alarmManager),
            canceller = alarmCanceller(context, alarmManager)
        )
        logSchedule(
            task.id,
            null,
            originalDueEpochMillis,
            null,
            if (scheduled) "SEQUENCE_SCHEDULED" else "SEQUENCE_FAILED_CLEANED_UP"
        )
        return scheduled
    }

    private fun alarmScheduler(
        context: Context,
        alarmManager: AlarmManager
    ) = ReminderStageScheduler { spec ->
        try {
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                alarmIntent(context, spec),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                spec.triggerEpochMillis,
                pendingIntent
            )
            logSchedule(
                spec.taskId,
                spec.stage,
                spec.expectedOriginalDueEpochMillis,
                spec.triggerEpochMillis,
                "SCHEDULED"
            )
        } catch (exception: Exception) {
            logSchedule(
                spec.taskId,
                spec.stage,
                spec.expectedOriginalDueEpochMillis,
                spec.triggerEpochMillis,
                "FAILED_${exception.javaClass.simpleName}"
            )
            throw exception
        }
    }

    private fun alarmCanceller(
        context: Context,
        alarmManager: AlarmManager
    ) = ReminderStageCanceller { taskId, stage ->
        try {
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                alarmIntent(context, taskId, stage),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent == null) {
                logCancel(taskId, stage, "NOT_FOUND")
            } else {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                logCancel(taskId, stage, "CANCELLED")
            }
        } catch (exception: Exception) {
            logCancel(taskId, stage, "FAILED_${exception.javaClass.simpleName}")
            throw exception
        }
    }

    private fun alarmIntent(context: Context, spec: ReminderAlarmSpec): Intent =
        alarmIntent(context, spec.taskId, spec.stage).apply {
            putExtra(
                ReminderAlarmIdentity.EXTRA_EXPECTED_DUE_EPOCH,
                spec.expectedOriginalDueEpochMillis
            )
        }

    private fun alarmIntent(
        context: Context,
        taskId: Long,
        stage: ReminderEscalationStage
    ): Intent {
        val identity = ReminderAlarmIdentity.forTaskStage(taskId, stage)
        return Intent(context, ReminderReceiver::class.java).apply {
            action = identity.action
            data = Uri.parse(identity.dataUri)
            putExtra(ReminderAlarmIdentity.EXTRA_TASK_ID, taskId)
            putExtra(ReminderAlarmIdentity.EXTRA_STAGE, stage.name)
        }
    }

    private fun logSchedule(
        taskId: Long,
        stage: ReminderEscalationStage?,
        expectedDueEpoch: Long?,
        triggerEpoch: Long?,
        outcome: String
    ) {
        if (BuildConfig.DEBUG) {
            Log.d(
                SCHEDULE_TAG,
                "taskId=$taskId stage=${stage?.name ?: "ALL"} " +
                    "expectedDueEpoch=${expectedDueEpoch ?: "NA"} " +
                    "triggerEpoch=${triggerEpoch ?: "NA"} outcome=$outcome"
            )
        }
    }

    private fun logCancel(
        taskId: Long,
        stage: ReminderEscalationStage?,
        outcome: String
    ) {
        if (BuildConfig.DEBUG) {
            Log.d(
                CANCEL_TAG,
                "taskId=$taskId stage=${stage?.name ?: "ALL"} outcome=$outcome"
            )
        }
    }
}
