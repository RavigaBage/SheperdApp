package com.example.util

import android.content.Context
import androidx.work.*
import com.example.data.local.SermonCalendarEntity
import java.util.concurrent.TimeUnit

object SermonCalendarScheduler {

    fun schedule(
        context: Context,
        event: SermonCalendarEntity,
        onJobsScheduled: (reminderJobId: String, nowJobId: String) -> Unit
    ) {
        val workManager = WorkManager.getInstance(context)

        // Cancel old jobs if rescheduling
        workManager.cancelAllWorkByTag("sermon_event_${event.id}")

        val nowMs = System.currentTimeMillis()
        val eventMs = event.scheduledDateMs

        // 1. One Day Before Reminder
        val oneDayMs = eventMs - (24 * 60 * 60 * 1000L)
        val oneDayRequest = if (oneDayMs > nowMs) {
            val data = Data.Builder()
                .putInt("event_db_id", event.id)
                .putString("notif_type", "1day")
                .build()
            OneTimeWorkRequestBuilder<SermonReminderWorker>()
                .setInitialDelay(oneDayMs - nowMs, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("sermon_event_${event.id}")
                .addTag("sermon_event_${event.id}_1day")
                .build()
        } else null

        // 2. Four Hours Before Reminder
        val fourHoursMs = eventMs - (4 * 60 * 60 * 1000L)
        val fourHoursRequest = if (fourHoursMs > nowMs) {
            val data = Data.Builder()
                .putInt("event_db_id", event.id)
                .putString("notif_type", "4hours")
                .build()
            OneTimeWorkRequestBuilder<SermonReminderWorker>()
                .setInitialDelay(fourHoursMs - nowMs, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("sermon_event_${event.id}")
                .addTag("sermon_event_${event.id}_4hours")
                .build()
        } else null

        // 3. Thirty Minutes Before Reminder (includes "Leave by" transit logic)
        val thirtyMinsMs = eventMs - (30 * 60 * 1000L)
        val thirtyMinsRequest = if (thirtyMinsMs > nowMs) {
            val data = Data.Builder()
                .putInt("event_db_id", event.id)
                .putString("notif_type", "30mins")
                .build()
            OneTimeWorkRequestBuilder<SermonReminderWorker>()
                .setInitialDelay(thirtyMinsMs - nowMs, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("sermon_event_${event.id}")
                .addTag("sermon_event_${event.id}_30mins")
                .build()
        } else null

        // 4. Fallback start-now alert (exactly at scheduled time)
        val startNowRequest = if (eventMs > nowMs) {
            val data = Data.Builder()
                .putInt("event_db_id", event.id)
                .putString("notif_type", "now")
                .build()
            OneTimeWorkRequestBuilder<SermonReminderWorker>()
                .setInitialDelay(eventMs - nowMs, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("sermon_event_${event.id}")
                .addTag("sermon_event_${event.id}_now")
                .build()
        } else null

        oneDayRequest?.let { workManager.enqueue(it) }
        fourHoursRequest?.let { workManager.enqueue(it) }
        thirtyMinsRequest?.let { workManager.enqueue(it) }
        startNowRequest?.let { workManager.enqueue(it) }

        val mainReminderIdStr = thirtyMinsRequest?.id?.toString() ?: fourHoursRequest?.id?.toString() ?: oneDayRequest?.id?.toString() ?: ""
        val nowIdStr = startNowRequest?.id?.toString() ?: ""
        onJobsScheduled(mainReminderIdStr, nowIdStr)
    }

    fun cancel(context: Context, eventId: Int) {
        WorkManager.getInstance(context).cancelAllWorkByTag("sermon_event_$eventId")
    }
}
