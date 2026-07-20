package com.example.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.data.local.AppDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SermonReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val eventId = inputData.getInt("event_db_id", -1)
        if (eventId == -1) return Result.failure()

        val db = AppDatabase.getInstance(applicationContext)
        val event = db.dao().getSermonCalendarById(eventId) ?: return Result.success() // event was deleted, no-op

        val type = inputData.getString("notif_type") ?: "30mins"
        val isSnoozed = inputData.getBoolean("is_snoozed", false)

        val sermonTitle = event.sermonTitle
        val venueName = event.venueName ?: "Main Sanctuary"
        val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        val scheduledTimeStr = timeFormatter.format(Date(event.scheduledDateMs))

        val notifId = when (type) {
            "1day" -> 1000 + eventId
            "4hours" -> 2000 + eventId
            "30mins" -> 3000 + eventId
            "now" -> 4000 + eventId
            else -> 5000 + eventId
        }

        val channelId = if (type == "now") SermonNotificationHelper.CHANNEL_NOW else SermonNotificationHelper.CHANNEL_REMINDER

        val snoozeSuffix = if (isSnoozed) " (Snoozed)" else ""
        val title = when (type) {
            "1day" -> "Sermon Tomorrow$snoozeSuffix"
            "4hours" -> "Sermon in 4 Hours$snoozeSuffix"
            "now" -> "Sermon Time!$snoozeSuffix"
            else -> "Sermon Starting Soon$snoozeSuffix"
        }

        val body = when (type) {
            "1day" -> "Your sermon '$sermonTitle' is scheduled for tomorrow at $scheduledTimeStr at $venueName."
            "4hours" -> "Prepare your heart! '$sermonTitle' is starting in 4 hours ($scheduledTimeStr) at $venueName."
            "now" -> "Sermon starting now! '$sermonTitle' at $venueName."
            else -> {
                // 30mins reminder + "Leave by" smart transit-aware nudge
                val travelMins = event.travelMinutes
                val leaveByMs = event.scheduledDateMs - (travelMins * 60 * 1000L)
                val leaveByStr = timeFormatter.format(Date(leaveByMs))
                "'$sermonTitle' starts in 30 mins ($scheduledTimeStr) at $venueName. Smart Drive Leave-by: $leaveByStr (includes $travelMins-min drive)."
            }
        }

        // Deep Link back to viewer or calendar
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "sermon_viewer")
            putExtra("sermon_id", event.sermonId)
        }
        val pFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentPendingIntent = PendingIntent.getActivity(
            applicationContext,
            eventId,
            intent,
            pFlags
        )

        // Snooze PendingIntent
        val snoozeIntent = Intent(applicationContext, SermonSnoozeReceiver::class.java).apply {
            putExtra("event_db_id", eventId)
            putExtra("notif_id", notifId)
            putExtra("notif_type", type)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            notifId + 10000,
            snoozeIntent,
            pFlags
        )

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (type == "now") NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze 10m", snoozePendingIntent)

        try {
            NotificationManagerCompat.from(applicationContext).notify(notifId, builder.build())
        } catch (e: SecurityException) {
            // Android 13+ permission block handling
        }

        return Result.success()
    }
}
