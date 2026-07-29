package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapplication.reminder.ReminderAlarmIdentity
import com.example.myapplication.reminder.ReminderEscalationStage
import com.example.myapplication.reminder.ReminderNotificationSpeechRenderer

class ReminderSpeechService : Service() {
    companion object {
        private const val CHANNEL_ID = "reminder_speech_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 2001
        private const val DELIVERY_TAG = "REMINDER_ESCALATION_DELIVERY"
    }

    private var voiceHelper: VoiceHelper? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        voiceHelper = VoiceHelper(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskTitle = intent
            ?.getStringExtra(ReminderAlarmIdentity.EXTRA_VALIDATED_TASK_TITLE)
            ?.takeIf { it.isNotBlank() }
        val stage = ReminderEscalationStage.fromWireValue(
            intent?.getStringExtra(ReminderAlarmIdentity.EXTRA_STAGE)
        )
        val foregroundText = taskTitle ?: "Task reminder"
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Speaking reminder")
            .setContentText(foregroundText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(FOREGROUND_NOTIFICATION_ID, notification)

        if (taskTitle == null || stage == null) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    DELIVERY_TAG,
                    "stage=${stage?.name ?: "UNKNOWN"} outcome=SPEECH_SUPPRESSED_INVALID_PAYLOAD"
                )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val spokenText = ReminderNotificationSpeechRenderer
            .render(taskTitle, stage)
            .spokenText
        if (BuildConfig.DEBUG) {
            Log.d(
                DELIVERY_TAG,
                "stage=${stage.name} outcome=SPEECH_STARTED"
            )
        }
        voiceHelper?.speak(spokenText) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        voiceHelper?.shutdown()
        voiceHelper = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reminder Speech",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground service channel for speaking task reminders"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
