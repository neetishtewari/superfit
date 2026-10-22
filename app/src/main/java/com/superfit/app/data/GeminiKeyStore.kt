package com.superfit.app.data

import android.content.Context

/**
 * Removes Gemini API keys that people typed into Settings in older versions.
 *
 * Superfit no longer uses a key of the user's own: every AI request goes through Firebase AI
 * Logic. The old own-key path used Google's earlier AI SDK, which clashes with Firebase AI's
 * networking library and crashed the app on every AI request (1.0.30). Keys are deleted rather
 * than left lying around unused.
 */
object GeminiKeyStore {
    /** Kept in res/xml backup rules so an old key is never copied into cloud backup. */
    private const val PREFS_FILE = "superfit_secrets"
    private const val LEGACY_PREF_KEY = "gemini_api_key"

    fun forgetOldKeys(context: Context) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).edit().clear().apply()
        try {
            // Very old builds kept the key in the signed-in user's settings.
            getUserSharedPrefs(appContext).edit().remove(LEGACY_PREF_KEY).apply()
        } catch (e: Exception) {
            // Firebase not ready; nothing depends on this, so try again next launch.
        }
    }
}
