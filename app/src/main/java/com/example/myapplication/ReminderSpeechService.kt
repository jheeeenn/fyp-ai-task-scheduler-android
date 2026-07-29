package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapplication.reminder.ReminderAlarmIdentity
import com.example.myapplication.reminder.ReminderSpeechQueue
import com.example.myapplication.reminder.ReminderSpeechRequest

class ReminderSpeechService : Service() {
    companion object {
        private const val CHANNEL_ID = "reminder_speech_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 2001
        private const val DELIVERY_TAG = "REMINDER_ESCALATION_DELIVERY"
    }

    private var voiceHelper: VoiceHelper? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var speechQueue: ReminderSpeechQueue

    override fun onCreate() {
        super.onCreate()
        createChannel()
        voiceHelper = VoiceHelper(applicationContext)
        speechQueue = ReminderSpeechQueue(
            speaker = { request, onComplete ->
                startForegroundFor(request)
                if (BuildConfig.DEBUG) {
                    Log.d(
                        DELIVERY_TAG,
                        "stage=${request.stage.name} outcome=SPEECH_STARTED " +
                            "queuedCount=${speechQueue.pendingCount}"
                    )
                }
                val helper = voiceHelper
                if (helper == null) {
                    mainHandler.post(onComplete)
                } else {
                    helper.speak(request.spokenText) {
                        mainHandler.post(onComplete)
                    }
                }
            },
            onQueueEmpty = {
                if (BuildConfig.DEBUG) {
                    Log.d(DELIVERY_TAG, "outcome=SPEECH_QUEUE_EMPTY")
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskTitle = readStringExtra(
            intent,
            ReminderAlarmIdentity.EXTRA_VALIDATED_TASK_TITLE
        )
        val stageWireValue = readStringExtra(
            intent,
            ReminderAlarmIdentity.EXTRA_STAGE
        )
        val accepted = speechQueue.enqueuePayload(taskTitle, stageWireValue)
        if (!accepted) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    DELIVERY_TAG,
                    "stage=${stageWireValue ?: "UNKNOWN"} " +
                        "outcome=SPEECH_SUPPRESSED_INVALID_PAYLOAD"
                )
            }
            if (speechQueue.isIdle) {
                startForegroundFor(null)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        } else if (BuildConfig.DEBUG) {
            Log.d(
                DELIVERY_TAG,
                "stage=$stageWireValue outcome=SPEECH_ENQUEUED " +
                    "queuedCount=${speechQueue.pendingCount}"
            )
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        voiceHelper?.shutdown()
        voiceHelper = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun readStringExtra(intent: Intent?, key: String): String? = try {
        intent?.getStringExtra(key)
    } catch (_: RuntimeException) {
        null
    }

    private fun startForegroundFor(request: ReminderSpeechRequest?) {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Speaking reminder")
            .setContentText(request?.taskTitle ?: "Task reminder")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }

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
