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
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.superfit.app.data.DailyReminderWorker
import com.superfit.app.data.TelemetrySyncWorker
import com.superfit.app.data.userPrefs
import java.util.concurrent.TimeUnit

class SuperfitApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installAppCheck()
        // Shared code (meal and workout reading, routing, tagging) reaches the AI through here.
        com.superfit.app.domain.Ai.text = com.superfit.app.domain.AiText { prompt, system, isJson ->
            com.superfit.app.domain.GeminiClient.generateContent(prompt, system, isJson)
        }
        com.superfit.app.domain.Ai.photoText = com.superfit.app.domain.AiPhotoText { photo, prompt, system, isJson ->
            com.superfit.app.domain.GeminiClient.generateContentWithImage(photo.bitmap, prompt, system, isJson)
        }
        com.superfit.app.domain.Ai.chatText = com.superfit.app.domain.AiChat { system, history, message ->
            com.superfit.app.domain.GeminiClient.chat(system, history, message)
        }
        com.superfit.app.domain.Ai.friendlyError = { com.superfit.app.domain.GeminiClient.sanitizeError(it) }
        loadAiModels()
        com.superfit.app.data.AppAnalytics.init(this)
        com.superfit.app.data.GeminiKeyStore.forgetOldKeys(this)
        com.superfit.app.data.BodyImporter.load(this)
        com.superfit.app.data.NutritionAdviceStore.load(userPrefs(this))
        com.superfit.app.data.CoachStyleStore.load(userPrefs(this))
        com.superfit.app.data.ReminderNotifications.ensureChannel(this)
        scheduleTelemetrySync()
        scheduleDailyReminder()
        com.superfit.app.data.RecapNotificationWorker.schedule(this)
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

    // The Gemini models to try first come from Firebase Remote Config, so a retired model can be
    // swapped in the console without a store update. The last fetched list applies at once; a fresh
    // one is fetched in the background (at most every few hours) and applies from then on. With no
    // value, or if Remote Config can't be reached, the built-in list is used.
    private fun loadAiModels() {
        try {
            val config = Firebase.remoteConfig
            config.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = 6 * 60 * 60 })
            val apply = { com.superfit.app.domain.AiFailover.setRemoteModels(config.getString(com.superfit.app.domain.AiFailover.REMOTE_CONFIG_KEY)) }
            apply()
            config.fetchAndActivate().addOnCompleteListener { apply() }
        } catch (e: Exception) {
            android.util.Log.w("SuperfitApplication", "Remote Config unavailable; using built-in AI models", e)
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
