package com.superfit.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * The daily reminder's notification channel and permission checks.
 *
 * On Android 13+ an app can't show notifications until the user allows them, and the reminder
 * switch in Settings starts out on, so nobody used to flip it and the permission was never asked
 * for. The reminder then ran every evening and Android silently dropped it.
 */
object ReminderNotifications {
    const val CHANNEL_ID = "superfit_alerts"
    const val PERMISSION = "android.permission.POST_NOTIFICATIONS"

    // Not per-user: the permission belongs to the app, not to the signed-in account.
    private const val PREFS = "superfit_device_prefs"
    private const val KEY_PERMISSION_ASKED = "notification_permission_asked"

    /** Creates the channel up front so Superfit is listed in the phone's notification settings. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Your evening meal-log reminder and coach summary"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** True when the phone will actually show Superfit's notifications. */
    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Whether to show the system "Allow notifications?" prompt on its own, once per install. */
    fun shouldAskOnLaunch(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (canPost(context)) return false
        if (!getUserSharedPrefs(context).getBoolean("reminder_enabled", true)) return false
        return !wasAsked(context)
    }

    fun wasAsked(context: Context): Boolean =
        devicePrefs(context).getBoolean(KEY_PERMISSION_ASKED, false)

    fun markAsked(context: Context) {
        devicePrefs(context).edit().putBoolean(KEY_PERMISSION_ASKED, true).apply()
    }

    /** Opens Superfit's page in the phone's notification settings, for when the prompt won't show. */
    fun openSystemSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", context.packageName, null))
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun devicePrefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
