package com.superfit.app.data

import android.content.Context
import android.content.pm.PackageManager

/** The name people know an app by ("Samsung Health"), from the phone itself where it can. */
object SourceAppNames {
    private val cache = mutableMapOf<String, String>()

    fun label(context: Context, packageName: String): String = synchronized(cache) {
        cache.getOrPut(packageName) { lookUp(context, packageName) }
    }

    private fun lookUp(context: Context, packageName: String): String {
        val pm = context.packageManager
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            // The app was uninstalled, or Android hides it from Superfit.
            FALLBACK[packageName] ?: packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    // Seen on test phones; used only when Android won't tell us the name.
    private val FALLBACK = mapOf(
        "com.sec.android.app.shealth" to "Samsung Health",
        "com.strava" to "Strava",
        "com.google.android.apps.fitness" to "Google Fit"
    )
}
