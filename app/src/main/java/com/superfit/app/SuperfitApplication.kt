package com.superfit.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.initialize
import com.superfit.app.data.DailyReminderWorker
import com.superfit.app.data.TelemetrySyncWorker
import java.util.concurrent.TimeUnit

class SuperfitApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installAppCheck()
        com.superfit.app.data.AppAnalytics.init(this)
        com.superfit.app.data.ReminderNotifications.ensureChannel(this)
        scheduleTelemetrySync()
        scheduleDailyReminder()
    }

    // App Check lets Firebase reject requests that don't come from the real Superfit app, which
    // protects the Gemini calls billed to Superfit's Firebase project. It must be installed
    // before any Firebase call is made.
    private fun installAppCheck() {
        try {
            Firebase.initialize(this)
            Firebase.appCheck.installAppCheckProviderFactory(AppCheckProviders.factory())
        } catch (e: Exception) {
            android.util.Log.e("SuperfitApplication", "App Check setup failed", e)
        }
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
            // KEEP: this runs whenever Android starts the app, including to deliver the reminder
            // itself, and replacing the pending reminder then would cancel it.
            com.superfit.app.data.ReminderScheduler.scheduleReminder(this, hour, minute, ExistingWorkPolicy.KEEP)
        } else {
            com.superfit.app.data.ReminderScheduler.cancelReminder(this)
        }
    }
}
