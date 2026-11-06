package com.superfit.app.data

import android.content.Context

/**
 * Which switch options (Activity, Journey, History) the person has opened at least once. Until
 * then the option shows a small dot, so people notice there is more behind it.
 */
object SeenTabs {
    private const val PREFS = "seen_tabs"

    fun unseen(context: Context, keys: Collection<String>): Set<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return keys.filterTo(mutableSetOf()) { !prefs.getBoolean(it, false) }
    }

    fun markSeen(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, true).apply()
    }
}
