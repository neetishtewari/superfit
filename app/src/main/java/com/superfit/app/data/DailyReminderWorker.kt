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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DailyReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val now = LocalTime.now()
        val currentHour = now.hour

        // Target checking windows: 2:00 PM - 3:00 PM (14) and 8:00 PM - 9:00 PM (20)
        val targetPeriod = when {
            currentHour == 14 -> 14
            currentHour == 20 -> 20
            else -> null
        }

        if (targetPeriod != null) {
            val sharedPrefs = getUserSharedPrefs(applicationContext)
            val todayStr = LocalDate.now().toString()
            val lastSentDate = sharedPrefs.getString("last_reminder_sent_date", "")
            val lastSentPeriod = sharedPrefs.getInt("last_reminder_sent_period", -1)

            if (lastSentDate != todayStr || lastSentPeriod != targetPeriod) {
                val database = SuperfitDatabase.getDatabase(applicationContext)
                val today = LocalDate.now()
                val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

                val nutritionDao = database.nutritionDao()
                val entries = nutritionDao.getEntriesForDay(startOfDay, endOfDay)

                if (entries.isEmpty()) {
                    sendNotification(
                        title = "Superfit Smart Reminder",
                        text = "No meals logged yet today! Tap to speak what you ate."
                    )
                } else if (targetPeriod == 20) {
                    val totalCalories = entries.sumOf { it.calories }
                    val customMacroEnabled = sharedPrefs.getBoolean("custom_macro_enabled", false)
                    val targetCalories = if (customMacroEnabled) {
                        sharedPrefs.getInt("custom_calories", 2000).toDouble()
                    } else {
                        val profile = database.profileDao().getProfile()
                        if (profile != null) {
                            val bmr = PhysiologyEngine.calculateBmr(profile)
                            val tdee = PhysiologyEngine.calculateTdee(bmr, profile.activityMultiplier)
                            val targets = PhysiologyEngine.calculateMacroTargets(profile, tdee, 50)
                            targets.calories
                        } else {
                            2000.0
                        }
                    }

                    if (totalCalories < 0.5 * targetCalories) {
                        sendNotification(
                            title = "Superfit Daily Focus",
                            text = "Your logged calories today are quite low (${totalCalories.toInt()} / ${targetCalories.toInt()} kcal). Did you enter all your meals?"
                        )
                    }
                }

                // Record that we evaluated/sent a reminder for this period
                sharedPrefs.edit()
                    .putString("last_reminder_sent_date", todayStr)
                    .putInt("last_reminder_sent_period", targetPeriod)
                    .apply()
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
