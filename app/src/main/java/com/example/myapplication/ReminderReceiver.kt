package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.reminder.ReminderAlarmIdentity
import com.example.myapplication.reminder.ReminderDeliveryEligibility
import com.example.myapplication.reminder.ReminderEligibilityPolicy
import com.example.myapplication.reminder.ReminderEscalationStage
import com.example.myapplication.reminder.ReminderNotificationSpeechRenderer
import com.example.myapplication.reminder.ReminderSuppressionReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        private const val CHANNEL_ID = "task_reminder_channel"
        private const val RECEIVER_TAG = "REMINDER_ESCALATION_RECEIVER"
        private const val DELIVERY_TAG = "REMINDER_ESCALATION_DELIVERY"
    }

    private data class AlarmPayload(
        val taskId: Long,
        val stage: ReminderEscalationStage,
        val expectedOriginalDueEpochMillis: Long
    )

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val applicationContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                receiveAndRevalidate(applicationContext, intent)
            } catch (exception: Exception) {
                logReceiver(
                    taskId = null,
                    stage = null,
                    expectedDueEpoch = null,
                    outcome = "FAILED_${exception.javaClass.simpleName}",
                    suppressionReason = null
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun receiveAndRevalidate(context: Context, intent: Intent) {
        val payload = parsePayload(intent) ?: return
        val task = AppDatabase.getInstance(context)
            .taskDao()
            .getById(payload.taskId)

        when (
            val eligibility = ReminderEligibilityPolicy.evaluateForDelivery(
                task = task,
                expectedOriginalDueEpochMillis = payload.expectedOriginalDueEpochMillis
            )
        ) {
            is ReminderDeliveryEligibility.Suppressed -> {
                logReceiver(
                    taskId = payload.taskId,
                    stage = payload.stage,
                    expectedDueEpoch = payload.expectedOriginalDueEpochMillis,
                    outcome = "SUPPRESSED",
                    suppressionReason = eligibility.reason
                )
            }

            is ReminderDeliveryEligibility.Eligible -> {
                deliver(
                    context = context,
                    taskId = payload.taskId,
                    taskTitle = eligibility.task.title,
                    stage = payload.stage,
                    expectedDueEpoch = payload.expectedOriginalDueEpochMillis
                )
            }
        }
    }

    private fun parsePayload(intent: Intent): AlarmPayload? {
        val rawStage = try {
            intent.getStringExtra(ReminderAlarmIdentity.EXTRA_STAGE)
        } catch (_: RuntimeException) {
            null
        }
        val stage = ReminderEscalationStage.fromWireValue(rawStage)
        if (stage == null) {
            logReceiver(
                taskId = null,
                stage = null,
                expectedDueEpoch = null,
                outcome = "SUPPRESSED",
                suppressionReason = ReminderSuppressionReason.UNKNOWN_STAGE
            )
            return null
        }

        val taskId: Long
        val expectedDueEpoch: Long
        try {
            if (!intent.hasExtra(ReminderAlarmIdentity.EXTRA_TASK_ID) ||
                !intent.hasExtra(ReminderAlarmIdentity.EXTRA_EXPECTED_DUE_EPOCH)
            ) {
                suppressInvalidPayload(stage)
                return null
            }
            taskId = intent.getLongExtra(ReminderAlarmIdentity.EXTRA_TASK_ID, -1L)
            expectedDueEpoch = intent.getLongExtra(
                ReminderAlarmIdentity.EXTRA_EXPECTED_DUE_EPOCH,
                -1L
            )
        } catch (_: RuntimeException) {
            suppressInvalidPayload(stage)
            return null
        }

        val expectedIdentity = ReminderAlarmIdentity.forTaskStage(taskId, stage)
        if (taskId <= 0L ||
            expectedDueEpoch <= 0L ||
            intent.action != expectedIdentity.action ||
            intent.dataString != expectedIdentity.dataUri
        ) {
            logReceiver(
                taskId = taskId.takeIf { it > 0L },
                stage = stage,
                expectedDueEpoch = expectedDueEpoch.takeIf { it > 0L },
                outcome = "SUPPRESSED",
                suppressionReason = ReminderSuppressionReason.INVALID_PAYLOAD
            )
            return null
        }

        return AlarmPayload(taskId, stage, expectedDueEpoch)
    }

    private fun suppressInvalidPayload(stage: ReminderEscalationStage) {
        logReceiver(
            taskId = null,
            stage = stage,
            expectedDueEpoch = null,
            outcome = "SUPPRESSED",
            suppressionReason = ReminderSuppressionReason.INVALID_PAYLOAD
        )
    }

    private fun deliver(
        context: Context,
        taskId: Long,
        taskTitle: String,
        stage: ReminderEscalationStage,
        expectedDueEpoch: Long
    ) {
        val wording = ReminderNotificationSpeechRenderer.render(taskTitle, stage)
        val notificationId = ReminderAlarmIdentity.notificationId(taskId)

        try {
            createNotificationChannel(context)
            val openAppIntent = Intent(context, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse("task-reminder://task/$taskId/open")
                putExtra("open_assistant_on_arrival", false)
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(wording.notificationTitle)
                .setContentText(wording.notificationMessage)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(wording.notificationMessage)
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(openAppPendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (exception: SecurityException) {
            logDelivery(
                taskId,
                stage,
                expectedDueEpoch,
                "NOTIFICATION_FAILED_${exception.javaClass.simpleName}"
            )
            return
        } catch (exception: RuntimeException) {
            logDelivery(
                taskId,
                stage,
                expectedDueEpoch,
                "NOTIFICATION_FAILED_${exception.javaClass.simpleName}"
            )
            return
        }

        val speechOutcome = try {
            val speechIntent = Intent(context, ReminderSpeechService::class.java).apply {
                putExtra(ReminderAlarmIdentity.EXTRA_VALIDATED_TASK_TITLE, taskTitle)
                putExtra(ReminderAlarmIdentity.EXTRA_STAGE, stage.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(speechIntent)
            } else {
                context.startService(speechIntent)
            }
            "STARTED"
        } catch (exception: RuntimeException) {
            "FAILED_${exception.javaClass.simpleName}"
        }

        logDelivery(
            taskId = taskId,
            stage = stage,
            expectedDueEpoch = expectedDueEpoch,
            outcome = "DELIVERED speechOutcome=$speechOutcome"
        )
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Task Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for scheduled task reminders"
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun logReceiver(
        taskId: Long?,
        stage: ReminderEscalationStage?,
        expectedDueEpoch: Long?,
        outcome: String,
        suppressionReason: ReminderSuppressionReason?
    ) {
        if (BuildConfig.DEBUG) {
            Log.d(
                RECEIVER_TAG,
                "taskId=${taskId ?: "NA"} stage=${stage?.name ?: "NA"} " +
                    "expectedDueEpoch=${expectedDueEpoch ?: "NA"} outcome=$outcome " +
                    "suppressionReason=${suppressionReason?.name ?: "NONE"}"
            )
        }
    }

    private fun logDelivery(
        taskId: Long,
        stage: ReminderEscalationStage,
        expectedDueEpoch: Long,
        outcome: String
    ) {
        if (BuildConfig.DEBUG) {
            Log.d(
                DELIVERY_TAG,
                "taskId=$taskId stage=${stage.name} expectedDueEpoch=$expectedDueEpoch " +
                    "outcome=$outcome"
            )
        }
    }
}
