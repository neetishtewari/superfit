package com.superfit.app.data

import android.content.Context
import android.content.SharedPreferences
import com.superfit.app.domain.CardChip
import org.json.JSONObject

/**
 * The share screen's saved choices: what goes on workout cards by default (photo and plain cards
 * apart), whether weight progress may appear, and the AI lines already written so reopening a card
 * costs nothing. Kept on the phone only.
 */
class ShareCardPrefs(private val prefs: SharedPreferences) {

    /** Defaults for cards with or without the user's photo. 1.0.49's "With one line" choice carries over. */
    fun defaults(photo: Boolean): Set<CardChip> {
        val key = if (photo) KEY_CHIPS_PHOTO else KEY_CHIPS_PLAIN
        prefs.getString(key, null)?.let { return CardChip.parse(it) }
        val oldLine = prefs.getBoolean(if (photo) OLD_LINE_PHOTO else OLD_LINE_PLAIN, !photo)
        return if (oldLine) setOf(CardChip.STANDOUT) else emptySet()
    }

    fun setDefaults(photo: Boolean, chips: Set<CardChip>) {
        prefs.edit().putString(if (photo) KEY_CHIPS_PHOTO else KEY_CHIPS_PLAIN, CardChip.encode(chips)).apply()
    }

    /** Off unless the user turns it on: weight stays private. */
    var showWeight: Boolean
        get() = prefs.getBoolean(KEY_SHOW_WEIGHT, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_WEIGHT, value).apply()

    fun savedLine(key: String): String? = lines().optString(key, "").takeIf { it.isNotBlank() }

    /** Keeps the newest [MAX_LINES] lines. */
    fun saveLine(key: String, line: String) {
        val all = lines()
        all.remove(key)
        val keep = JSONObject()
        val names = all.keys().asSequence().toList().takeLast(MAX_LINES - 1)
        names.forEach { keep.put(it, all.getString(it)) }
        keep.put(key, line)
        prefs.edit().putString(KEY_LINES, keep.toString()).apply()
    }

    private fun lines(): JSONObject = runCatching { JSONObject(prefs.getString(KEY_LINES, null) ?: "{}") }.getOrElse { JSONObject() }

    companion object {
        const val FILE = "share_card_prefs"
        private const val KEY_CHIPS_PHOTO = "chips_photo"
        private const val KEY_CHIPS_PLAIN = "chips_plain"
        private const val KEY_SHOW_WEIGHT = "show_weight"
        private const val KEY_LINES = "ai_lines"
        private const val OLD_LINE_PHOTO = "line_on_photo"
        private const val OLD_LINE_PLAIN = "line_on_plain"
        private const val MAX_LINES = 40

        fun of(context: Context) = ShareCardPrefs(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}
