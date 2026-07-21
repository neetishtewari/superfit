package com.superfit.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.superfit.app.domain.PhysiologyEngine
import com.superfit.app.domain.CoachingEngine
import com.superfit.app.domain.MacroTargets
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.Instant

class DailyReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sharedPrefs = getUserSharedPrefs(applicationContext)

        // 1. Reschedule for tomorrow at the configured time
        val reminderEnabled = sharedPrefs.getBoolean("reminder_enabled", true)
        if (reminderEnabled) {
            val hour = sharedPrefs.getInt("reminder_hour", 21)
            val minute = sharedPrefs.getInt("reminder_minute", 0)
            ReminderScheduler.scheduleReminder(applicationContext, hour, minute)
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
                val targets = PhysiologyEngine.calculateMacroTargets(profile, tdee, 50)
                targets.calories
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
            val apiKey = sharedPrefs.getString("gemini_api_key", "") ?: ""
            if (apiKey.isBlank()) {
                sendNotification(
                    title = "⚡ Daily Focus Complete",
                    text = "You've successfully hit your calorie targets today (${totalCalories.toInt()} kcal)! Make sure to prioritize sleep tonight for recovery."
                )
            } else {
                try {
                    val activity = database.telemetryDao().getActivity(today.toString()) 
                        ?: com.superfit.app.data.ActivityTelemetryEntity(today.toString(), 0, 0.0)
                    val sleep = database.telemetryDao().getSleep(today.toString())

                    val bmr = if (profile != null) PhysiologyEngine.calculateBmr(profile) else 1500.0
                    val tdee = if (profile != null) PhysiologyEngine.calculateTdee(bmr, profile.activityMultiplier) else 2000.0
                    val targets = if (profile != null) {
                        PhysiologyEngine.calculateMacroTargets(profile, tdee, sleep?.readinessScore ?: 50)
                    } else {
                        MacroTargets(2000.0, 130.0, 200.0, 70.0)
                    }

                    val proteinEaten = entries.sumOf { it.proteinG }
                    val carbsEaten = entries.sumOf { it.carbsG }
                    val fatEaten = entries.sumOf { it.fatG }

                    val historyNutrition = database.nutritionDao().getAllEntries()
                    val historyWorkouts = database.workoutDao().getAllEntries()
                    val historyActivity = database.telemetryDao().getAllActivity()
                    val historySleep = database.telemetryDao().getAllSleep()

                    val coachingEngine = CoachingEngine(apiKey)
                    val insight = coachingEngine.generateDailyInsight(
                        profile = profile ?: UserProfileEntity(age = 30, weightKg = 70.0, heightCm = 175.0, isMale = true, activityMultiplier = 1.2),
                        activity = activity,
                        sleep = sleep,
                        nutrition = entries,
                        macroTargets = targets,
                        caloriesEaten = totalCalories,
                        proteinEaten = proteinEaten,
                        carbsEaten = carbsEaten,
                        fatEaten = fatEaten,
                        historyNutrition = historyNutrition,
                        historyWorkouts = historyWorkouts,
                        historyActivity = historyActivity,
                        historySleep = historySleep
                    )

                    // Cache generated insight in shared prefs for dashboard
                    sharedPrefs.edit()
                        .putString("coaching_insight_date", today.toString())
                        .putString("coaching_insight_text", insight)
                        .apply()

                    sendNotification(
                        title = "⚡ Daily AI Coaching Insight",
                        text = insight
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

        return Result.success()
    }

    private fun sendNotification(title: String, text: String) {
        val channelId = "superfit_alerts"
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

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Superfit Smart Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders to log your meals"
            }
            notificationManager.createNotificationChannel(channel)
        }

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

