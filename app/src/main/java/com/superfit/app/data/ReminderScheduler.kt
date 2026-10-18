package com.superfit.app.data

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    const val WORK_NAME = "DailyReminderWork"

    /**
     * Schedules the reminder for the next [hour]:[minute].
     *
     * [policy] matters because the reminder re-schedules itself: REPLACE cancels whatever reminder
     * is pending OR RUNNING, so it must only be used when the user changes the time. App start uses
     * KEEP and the reminder itself uses APPEND_OR_REPLACE, otherwise the 9 PM run cancels itself
     * before the notification is shown.
     */
    fun scheduleReminder(
        context: Context,
        hour: Int,
        minute: Int,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE
    ) {
        val workManager = WorkManager.getInstance(context)

        val workRequest = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay(delayUntil(hour, minute, Calendar.getInstance()), TimeUnit.MILLISECONDS)
            .addTag("DailyReminderWorkTag")
            .build()

        workManager.enqueueUniqueWork(WORK_NAME, policy, workRequest)
    }

    /** Milliseconds from [now] until the next [hour]:[minute], today if it's still ahead, else tomorrow. */
    fun delayUntil(hour: Int, minute: Int, now: Calendar): Long {
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }

    fun cancelReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
