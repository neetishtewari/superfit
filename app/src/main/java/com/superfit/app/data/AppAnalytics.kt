package com.superfit.app.data

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Android's side of anonymous usage counts and crash reports (the events themselves are in the
 * shared UsageEvents). The dev app never reports, and users can turn both off in Settings.
 */
object AppAnalytics {
    private const val PREFS = "superfit_usage_reports"
    private const val KEY_ENABLED = "enabled"

    private var analytics: FirebaseAnalytics? = null
    private var active = false

    /** Call once from Application.onCreate. Collection stays off until this says otherwise. */
    fun init(context: Context) {
        try {
            analytics = FirebaseAnalytics.getInstance(context)
            // Every screen reports through UsageEvents, behind the same switch.
            UsageEvents.report = ::log
            UsageEvents.error = ::recordError
            UsageEvents.key = ::setKey
            apply(context)
        } catch (e: Exception) {
            android.util.Log.w("AppAnalytics", "Usage reports unavailable", e)
        }
    }

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        apply(context)
    }

    private fun apply(context: Context) {
        active = isEnabled(context) && !isDevApp(context)
        try {
            analytics?.setAnalyticsCollectionEnabled(active)
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = active
        } catch (e: Exception) {
            android.util.Log.w("AppAnalytics", "Could not change usage reports", e)
        }
    }

    private fun isDevApp(context: Context) = context.packageName.endsWith(".debug")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun log(event: String, params: Map<String, String>) {
        if (!active) return
        analytics?.logEvent(event, Bundle().apply { params.forEach { (k, v) -> putString(k, v) } })
    }

    private fun setKey(name: String, value: String) {
        if (!active) return
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(name, value)
        } catch (_: Exception) {
        }
    }

    private fun recordError(e: Throwable) {
        if (!active) return
        try {
            FirebaseCrashlytics.getInstance().recordException(e)
        } catch (_: Exception) {
        }
    }
}
