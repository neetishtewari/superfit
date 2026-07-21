package com.superfit.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.superfit.app.data.TelemetrySyncWorker
import com.superfit.app.data.DailyReminderWorker
import java.util.concurrent.TimeUnit

class SuperfitApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleTelemetrySync()
        scheduleDailyReminder()
    }

    private fun scheduleTelemetrySync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<TelemetrySyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "TelemetrySyncWork",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    private fun scheduleDailyReminder() {
        val sharedPrefs = com.superfit.app.data.getUserSharedPrefs(this)
        val enabled = sharedPrefs.getBoolean("reminder_enabled", true)
        if (enabled) {
            val hour = sharedPrefs.getInt("reminder_hour", 21) // default 9 PM
            val minute = sharedPrefs.getInt("reminder_minute", 0)
            com.superfit.app.data.ReminderScheduler.scheduleReminder(this, hour, minute)
        } else {
            com.superfit.app.data.ReminderScheduler.cancelReminder(this)
        }
    }

}
