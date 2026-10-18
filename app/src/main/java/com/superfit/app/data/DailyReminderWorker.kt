package com.superfit.app.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import com.superfit.app.domain.PhysiologyEngine
import com.superfit.app.domain.CoachingEngine
import com.superfit.app.domain.MacroTargets
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.Instant
import kotlinx.coroutines.CancellationException

class DailyReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sharedPrefs = getUserSharedPrefs(applicationContext)
        try {
            remind(sharedPrefs)
        } catch (e: CancellationException) {
            throw e // Android stopped this run; WorkManager runs it again later.
        } catch (e: Exception) {
            android.util.Log.e("DailyReminderWorker", "Reminder failed", e)
        }

        // 1. Tomorrow's reminder, queued behind this one. REPLACE here would cancel this very
        // run, which is why the reminder never used to appear.
        if (sharedPrefs.getBoolean("reminder_enabled", true)) {
            ReminderScheduler.scheduleReminder(
                applicationContext,
                sharedPrefs.getInt("reminder_hour", 21),
                sharedPrefs.getInt("reminder_minute", 0),
                ExistingWorkPolicy.APPEND_OR_REPLACE
            )
        }
        return Result.success()
    }

    private suspend fun remind(sharedPrefs: android.content.SharedPreferences) {
        // Nothing can be shown, so don't spend an AI call on it.
        if (!ReminderNotifications.canPost(applicationContext)) {
            android.util.Log.w("DailyReminderWorker", "Notifications are turned off for Superfit; skipping reminder")
            return
        }

        // 2. Fetch today's logging data
        val database = SuperfitDatabase.getDatabase(applicationContext)
        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

        val nutritionDao = database.nutritionDao()
        val entries = nutritionDao.getEntriesForDay(startOfDay, endOfDay)
        val totalCalories = entries.sumOf { it.calories }

        // Determine target calories
        val customMacroEnabled = sharedPrefs.getBoolean("custom_macro_enabled", false)
        val profile = database.profileDao().getProfile()
        val targetCalories = if (customMacroEnabled) {
            sharedPrefs.getInt("custom_calories", 2000).toDouble()
        } else {
            if (profile != null) {
                val bmr = PhysiologyEngine.calculateBmr(profile)
                val tdee = PhysiologyEngine.calculateTdee(bmr, profile.activityMultiplier)
                PhysiologyEngine.calculateMacroTargets(profile, tdee).calories
            } else {
                2000.0
            }
        }

        // 3. Decide notification style:
        // Under-logged if total calories is less than 75% of target
        if (totalCalories < 0.75 * targetCalories) {
            sendNotification(
                title = "📝 Complete your food log?",
                text = "You've logged ${totalCalories.toInt()} / ${targetCalories.toInt()} kcal today. Did you forget to log a meal?"
            )
        } else {
            // Log is complete -> Send Daily AI Coaching Summary
            val apiKey = GeminiKeyStore(applicationContext).get()
            try {
                val coachProfile = profile ?: UserProfileEntity(age = 30, weightKg = 70.0, heightCm = 175.0, isMale = true, activityMultiplier = 1.2)
                val bmr = PhysiologyEngine.calculateBmr(coachProfile)
                val tdee = PhysiologyEngine.calculateTdee(bmr, coachProfile.activityMultiplier)
                val targets = if (customMacroEnabled) {
                    MacroTargets(
                        calories = sharedPrefs.getInt("custom_calories", 2000).toDouble(),
                        proteinG = sharedPrefs.getInt("custom_protein_g", 130).toDouble(),
                        carbsG = sharedPrefs.getInt("custom_carbs_g", 200).toDouble(),
                        fatG = sharedPrefs.getInt("custom_fat_g", 70).toDouble()
                    )
                } else {
                    PhysiologyEngine.calculateMacroTargets(coachProfile, tdee)
                }

                val insight = CoachingEngine(apiKey).generateDailyInsight(
                    profile = coachProfile,
                    macroTargets = targets,
                    maintenanceCalories = if (customMacroEnabled) null else tdee,
                    historyNutrition = database.nutritionDao().getAllEntries(),
                    historyWorkouts = database.workoutDao().getAllEntries(),
                    historyActivity = database.telemetryDao().getAllActivity(),
                    historySleep = database.telemetryDao().getAllSleep(),
                    historyWeights = database.weightDao().getAllWeightEntries(),
                    memory = CoachInsightStore.memory(sharedPrefs)
                )

                // Cache for the home screen, and remember what was said
                CoachInsightStore.save(sharedPrefs, today, insight)

                sendNotification(
                    title = "⚡ Your coach",
                    text = insight.asText().replace("**", "")
                )
            } catch (e: Exception) {
                android.util.Log.e("DailyReminderWorker", "AI Coaching notification generation failed", e)
                sendNotification(
                    title = "⚡ Daily Focus Complete",
                    text = "You've successfully hit your calorie targets today (${totalCalories.toInt()} kcal)! Prioritize sleep tonight for recovery."
                )
            }
        }
    }

    private fun sendNotification(title: String, text: String) {
        val channelId = ReminderNotifications.CHANNEL_ID
        val notificationId = 1001

        val intent = Intent(applicationContext, Class.forName("com.superfit.app.MainActivity")).apply {
            action = "com.superfit.app.ACTION_VOICE_LOG"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification Action Intents
        val voiceIntent = Intent(applicationContext, Class.forName("com.superfit.app.MainActivity")).apply {
            action = "com.superfit.app.ACTION_VOICE_LOG"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val voicePendingIntent = PendingIntent.getActivity(
            applicationContext,
            1,
            voiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val favIntent = Intent(applicationContext, Class.forName("com.superfit.app.MainActivity")).apply {
            action = "com.superfit.app.ACTION_FAVORITES_LOG"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val favPendingIntent = PendingIntent.getActivity(
            applicationContext,
            2,
            favIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        ReminderNotifications.ensureChannel(applicationContext)
        if (!ReminderNotifications.canPost(applicationContext)) {
            android.util.Log.w("DailyReminderWorker", "Notifications are turned off for Superfit; reminder not shown")
            return
        }
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_btn_speak_now,
                "Voice Input",
                voicePendingIntent
            )
            .addAction(
                android.R.drawable.btn_star,
                "Log Favorite",
                favPendingIntent
            )
            .build()

        notificationManager.notify(notificationId, notification)
    }
}

