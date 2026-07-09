package com.example.preachmode.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object PreachNotificationHelper {
    private const val CHANNEL_ID = "preach_mode_timer_channel"
    private const val CHANNEL_NAME = "Preach Mode Timer Alerts"
    private const val NOTIFICATION_ID = 8821

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Alerts for preacher sermon timing limits"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 100, 250, 100, 250)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun fireTimeUpNotification(context: Context) {
        createNotificationChannel(context)
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Sermon Time is Up!")
            .setContentText("Your scheduled preach mode session time limit has run out.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 100, 250, 100, 250))

        try {
            // Check POST_NOTIFICATIONS permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
