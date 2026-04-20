package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log
class ReminderSpeechService : Service() {

    private var voiceHelper: VoiceHelper? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("REMINDER_DEBUG", "ReminderSpeechService onCreate")
        createChannel()
        voiceHelper = VoiceHelper(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskTitle = intent?.getStringExtra("task_title") ?: "Task reminder"
        Log.d("REMINDER_DEBUG", "ReminderSpeechService onStartCommand taskTitle='$taskTitle'")
        val notification: Notification = NotificationCompat.Builder(this, "reminder_speech_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Speaking reminder")
            .setContentText(taskTitle)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(2001, notification)

        val textToSpeak = "You have a reminder. $taskTitle"

        voiceHelper?.speak(textToSpeak) {
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
            val manager = getSystemService(NotificationManager::class.java)

            val channel = NotificationChannel(
                "reminder_speech_channel",
                "Reminder Speech",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground service channel for speaking reminder titles"
            }

            manager.createNotificationChannel(channel)
        }
    }


}