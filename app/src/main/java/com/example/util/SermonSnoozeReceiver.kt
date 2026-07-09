package com.example.util

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SermonSnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra("notif_id", -1)
        if (notifId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notifId)
        }

        val eventId = intent.getIntExtra("event_db_id", -1)
        val notifType = intent.getStringExtra("notif_type") ?: "30mins"

        if (eventId != -1) {
            val workManager = WorkManager.getInstance(context)
            val data = Data.Builder()
                .putInt("event_db_id", eventId)
                .putString("notif_type", notifType)
                .putBoolean("is_snoozed", true)
                .build()

            val snoozeRequest = OneTimeWorkRequestBuilder<SermonReminderWorker>()
                .setInitialDelay(10, TimeUnit.MINUTES)
                .setInputData(data)
                .addTag("sermon_event_${eventId}_snooze")
                .build()

            workManager.enqueue(snoozeRequest)
        }
    }
}
