package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object SermonNotificationHelper {
    const val CHANNEL_REMINDER = "sermon_reminder"
    const val CHANNEL_NOW = "sermon_now"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDER,
                "Sermon Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Advance notice before a scheduled sermon"
                enableVibration(true)
            }

            val nowChannel = NotificationChannel(
                CHANNEL_NOW,
                "Sermon Starting Now",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alert when a scheduled sermon is beginning"
                enableVibration(true)
            }

            manager.createNotificationChannel(reminderChannel)
            manager.createNotificationChannel(nowChannel)
        }
    }
}
