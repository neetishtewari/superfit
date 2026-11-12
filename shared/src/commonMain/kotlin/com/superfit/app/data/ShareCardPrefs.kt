package com.superfit.app.data

import com.superfit.app.domain.CardChip
import com.superfit.app.json.JSONObject

/**
 * The share screen's saved choices: what goes on workout cards by default (photo and plain cards
 * apart), whether weight progress may appear, and the AI lines already written so reopening a card
 * costs nothing. Kept on the phone only, the same on Android and iPhone.
 */
class ShareCardPrefs(private val prefs: Prefs) {

    /** Defaults for cards with or without the user's photo. 1.0.49's "With one line" choice carries over. */
    fun defaults(photo: Boolean): Set<CardChip> {
        val key = if (photo) KEY_CHIPS_PHOTO else KEY_CHIPS_PLAIN
        prefs.getString(key, null)?.let { return CardChip.parse(it) }
        val oldLine = prefs.getBoolean(if (photo) OLD_LINE_PHOTO else OLD_LINE_PLAIN, !photo)
        // The heart-rate graph starts on, as in 1.0.49; the user can switch it off.
        return if (oldLine) setOf(CardChip.HEART, CardChip.STANDOUT) else setOf(CardChip.HEART)
    }

    fun setDefaults(photo: Boolean, chips: Set<CardChip>) {
        prefs.edit { putString(if (photo) KEY_CHIPS_PHOTO else KEY_CHIPS_PLAIN, CardChip.encode(chips)) }
    }

    /** Off unless the user turns it on: weight stays private. */
    var showWeight: Boolean
        get() = prefs.getBoolean(KEY_SHOW_WEIGHT, false)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_WEIGHT, value) }

    /** The "ready to share" card on Today: offer every run (on unless turned off). */
    var offerRuns: Boolean
        get() = prefs.getBoolean(KEY_OFFER_RUNS, true)
        set(value) = prefs.edit { putBoolean(KEY_OFFER_RUNS, value) }

    /** Offer walks at least this long; null means walks are off. */
    var offerWalkMinutes: Int?
        get() = prefs.getInt(KEY_OFFER_WALK_MIN, com.superfit.app.domain.ShareOffer.DEFAULT_WALK_MINUTES).takeIf { it > 0 }
        set(value) = prefs.edit { putInt(KEY_OFFER_WALK_MIN, value ?: 0) }

    /** Workouts whose card was shared or dismissed, so it isn't offered again. Keeps the newest few. */
    val handledOffers: Set<String> get() = prefs.getString(KEY_OFFER_HANDLED, null)?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    fun markOfferHandled(id: String) {
        val list = (prefs.getString(KEY_OFFER_HANDLED, null)?.split(',')?.filter { it.isNotBlank() } ?: emptyList()) - id + id
        prefs.edit { putString(KEY_OFFER_HANDLED, list.takeLast(MAX_HANDLED).joinToString(",")) }
    }

    /** Whether the photo hint ("Pinch to zoom…") was seen. */
    var photoHintShown: Boolean
        get() = prefs.getBoolean(KEY_HINT_SHOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_HINT_SHOWN, value) }

    /** Fresh roasts from "Try another" used on [day] ("2026-11-04"). */
    fun roastsAgain(day: String): Int = if (prefs.getString(KEY_AGAIN_DAY, null) == day) prefs.getInt(KEY_AGAIN_COUNT, 0) else 0

    fun countRoastAgain(day: String) {
        val next = roastsAgain(day) + 1
        prefs.edit {
            putString(KEY_AGAIN_DAY, day)
            putInt(KEY_AGAIN_COUNT, next)
        }
    }

    fun savedLine(key: String): String? = lines().optString(key, "").takeIf { it.isNotBlank() }

    /** Keeps the newest [MAX_LINES] lines. */
    fun saveLine(key: String, line: String) {
        val all = lines()
        all.remove(key)
        val keep = JSONObject()
        val names = all.keys().asSequence().toList().takeLast(MAX_LINES - 1)
        names.forEach { keep.put(it, all.getString(it)) }
        keep.put(key, line)
        prefs.edit { putString(KEY_LINES, keep.toString()) }
    }

    private fun lines(): JSONObject = runCatching { JSONObject(prefs.getString(KEY_LINES, null) ?: "{}") }.getOrElse { JSONObject() }

    companion object {
        const val FILE = "share_card_prefs"
        private const val KEY_HINT_SHOWN = "photo_hint_shown"
        private const val KEY_CHIPS_PHOTO = "chips_photo"
        private const val KEY_CHIPS_PLAIN = "chips_plain"
        private const val KEY_SHOW_WEIGHT = "show_weight"
        private const val KEY_LINES = "ai_lines"
        private const val KEY_AGAIN_DAY = "roast_again_day"
        private const val KEY_AGAIN_COUNT = "roast_again_count"
        private const val OLD_LINE_PHOTO = "line_on_photo"
        private const val OLD_LINE_PLAIN = "line_on_plain"
        private const val MAX_LINES = 40
        private const val KEY_OFFER_RUNS = "offer_runs"
        private const val KEY_OFFER_WALK_MIN = "offer_walk_minutes"
        private const val KEY_OFFER_HANDLED = "offer_handled_ids"
        private const val MAX_HANDLED = 30
    }
}
