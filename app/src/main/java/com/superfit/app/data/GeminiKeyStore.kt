package com.superfit.app.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

/**
 * Holds each signed-in user's Gemini API key.
 *
 * The key lives in its own prefs file, [PREFS_FILE], which res/xml/backup_rules.xml and
 * res/xml/data_extraction_rules.xml exclude from Android cloud backup, so a user's key is
 * never copied into their Google Drive backup. Keys written by older builds to the
 * per-user prefs file (which is backed up) are moved here on first read.
 */
class GeminiKeyStore(context: Context) {

    private val appContext = context.applicationContext
    private val secrets = appContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private fun prefKey(): String {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        return "gemini_api_key_$userId"
    }

    fun get(): String {
        val prefKey = prefKey()
        secrets.getString(prefKey, null)?.let { return it }

        val legacyPrefs = getUserSharedPrefs(appContext)
        val legacyKey = legacyPrefs.getString(LEGACY_PREF_KEY, null)
        if (!legacyKey.isNullOrBlank()) {
            secrets.edit().putString(prefKey, legacyKey).commit()
            legacyPrefs.edit().remove(LEGACY_PREF_KEY).apply()
            return legacyKey
        }
        return ""
    }

    fun hasKey(): Boolean = get().isNotBlank()

    fun set(key: String) {
        secrets.edit().putString(prefKey(), key).apply()
    }

    fun clear() {
        secrets.edit().remove(prefKey()).commit()
    }

    companion object {
        const val PREFS_FILE = "superfit_secrets"
        private const val LEGACY_PREF_KEY = "gemini_api_key"
    }
}
