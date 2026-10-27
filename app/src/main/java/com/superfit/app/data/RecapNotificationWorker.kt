package com.superfit.app.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.superfit.app.domain.RecapCard
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Once a day, late morning: on the first days of a month, one "Your October is ready" notification.
 * People who turned notifications off still find the recap on Home and in Progress.
 */
class RecapNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val today = LocalDate.now()
        if (today.dayOfMonth > 3 || !ReminderNotifications.canPost(applicationContext)) return Result.success()
        val month = MonthlyRecapData.offeredMonth(today)
        if (MonthlyRecapData.openedOn(applicationContext, month) != null) return Result.success()
        val repository = DataRepository(applicationContext, HealthConnectManager(applicationContext))
        val recap = runCatching { MonthlyRecapData.load(applicationContext, repository, month) }.getOrNull()
        if (recap == null || recap.cards.none { it !is RecapCard.Setup }) return Result.success()
        if (!MonthlyRecapData.claimNotification(applicationContext, month)) return Result.success()
        notify(applicationContext, month)
        return Result.success()
    }

    companion object {
        const val ACTION_OPEN_RECAP = "com.superfit.app.ACTION_OPEN_RECAP"
        const val EXTRA_MONTH = "month"
        private const val WORK_NAME = "RecapNotificationWork"
        private const val NOTIFICATION_ID = 1002

        /** Daily at about 10 am; KEEP so app starts don't move it. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(10, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<RecapNotificationWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun notify(context: Context, month: YearMonth) {
            val name = month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
            val intent = Intent(context, Class.forName("com.superfit.app.MainActivity")).apply {
                action = ACTION_OPEN_RECAP
                putExtra(EXTRA_MONTH, month.toString())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(context, 5, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            ReminderNotifications.ensureChannel(context)
            val notification = NotificationCompat.Builder(context, ReminderNotifications.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.star_on)
                .setContentTitle("Your $name is ready")
                .setContentText("Your month in a few cards worth sharing. Tap to see it.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification)
        }
    }
}
