package com.superfit.app.data

import android.content.SharedPreferences
import com.superfit.app.domain.BestKind
import com.superfit.app.domain.WorkoutAddBack

/**
 * The user's choices for workouts from other apps, kept in their own prefs. Every key starts with
 * "workout_import" so screens can listen for changes. The import switch, main app, add-back
 * choice, hidden workouts and personal-best starting points are backed up to the cloud; the card
 * and prompt flags stay on the phone.
 */
class WorkoutImportPrefs(private val prefs: SharedPreferences) {

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Package name of the app that always wins when two apps recorded the same workout; null for Automatic. */
    var mainApp: String?
        get() = prefs.getString(KEY_MAIN_APP, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_MAIN_APP, value ?: "").apply()

    var addBack: WorkoutAddBack
        get() = runCatching { WorkoutAddBack.valueOf(prefs.getString(KEY_ADD_BACK, null) ?: "") }
            .getOrDefault(WorkoutAddBack.ALL)
        set(value) = prefs.edit().putString(KEY_ADD_BACK, value.name).apply()

    val hiddenIds: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()

    fun hide(ids: Collection<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN, hiddenIds + ids).apply()
    }

    fun unhideAll() {
        prefs.edit().putStringSet(KEY_HIDDEN, emptySet()).apply()
    }

    var cardDismissed: Boolean
        get() = prefs.getBoolean(KEY_CARD_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(KEY_CARD_DISMISSED, value).apply()

    /** Imported workouts already asked about after a voice-logged one, so each is asked about once. */
    fun wasAskedAboutDuplicate(id: String): Boolean = id in prefs.getStringSet(KEY_DUPLICATE_ASKED, emptySet()).orEmpty()

    fun markAskedAboutDuplicate(id: String) {
        prefs.edit().putStringSet(KEY_DUPLICATE_ASKED, prefs.getStringSet(KEY_DUPLICATE_ASKED, emptySet()).orEmpty() + id).apply()
    }

    /** When the last import finished; 0 before the first one, which reads 90 days back. */
    var lastImportMillis: Long
        get() = prefs.getLong(KEY_LAST_IMPORT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_IMPORT, value).apply()

    /** Personal bests already seen or shared, so each new best is offered once, even after a reinstall. */
    var bestBaseline: Map<BestKind, Double>
        get() = decodeBests(prefs.getStringSet(KEY_BEST_BASELINE, emptySet()).orEmpty())
        set(value) = prefs.edit().putStringSet(KEY_BEST_BASELINE, encodeBests(value)).apply()

    /** True while the current streak is a best that was already offered, so it isn't offered every day. */
    var bestStreakRiding: Boolean
        get() = prefs.getBoolean(KEY_BEST_STREAK_RIDING, false)
        set(value) = prefs.edit().putBoolean(KEY_BEST_STREAK_RIDING, value).apply()

    /** What the cloud backup holds. */
    fun toBackup(): Map<String, Any> = mapOf(
        KEY_ENABLED to enabled,
        KEY_MAIN_APP to (mainApp ?: ""),
        KEY_ADD_BACK to addBack.name,
        KEY_HIDDEN to hiddenIds.toList(),
        KEY_BEST_BASELINE to encodeBests(bestBaseline).toList(),
        KEY_BEST_STREAK_RIDING to bestStreakRiding
    )

    /** Restores the backup, keeping workouts hidden on this phone hidden. */
    fun restore(data: Map<String, Any>) {
        val hidden = (data[KEY_HIDDEN] as? List<*>)?.filterIsInstance<String>().orEmpty()
        prefs.edit().apply {
            (data[KEY_ENABLED] as? Boolean)?.let { putBoolean(KEY_ENABLED, it) }
            (data[KEY_MAIN_APP] as? String)?.let { putString(KEY_MAIN_APP, it) }
            (data[KEY_ADD_BACK] as? String)?.let { putString(KEY_ADD_BACK, it) }
            putStringSet(KEY_HIDDEN, hiddenIds + hidden)
            val bests = (data[KEY_BEST_BASELINE] as? List<*>)?.filterIsInstance<String>().orEmpty()
            if (bests.isNotEmpty()) putStringSet(KEY_BEST_BASELINE, encodeBests(decodeBests(bests) + bestBaseline))
            (data[KEY_BEST_STREAK_RIDING] as? Boolean)?.let { putBoolean(KEY_BEST_STREAK_RIDING, it) }
        }.apply()
    }

    private fun encodeBests(bests: Map<BestKind, Double>): Set<String> = bests.map { (k, v) -> "${k.name}=$v" }.toSet()

    private fun decodeBests(lines: Collection<String>): Map<BestKind, Double> = lines.mapNotNull { line ->
        val kind = BestKind.entries.firstOrNull { it.name == line.substringBefore('=') }
        val value = line.substringAfter('=', "").toDoubleOrNull()
        if (kind != null && value != null) kind to value else null
    }.toMap()

    companion object {
        const val KEY_PREFIX = "workout_import"
        private const val KEY_ENABLED = "workout_import_enabled"
        private const val KEY_MAIN_APP = "workout_import_main_app"
        private const val KEY_ADD_BACK = "workout_import_add_back"
        private const val KEY_HIDDEN = "workout_import_hidden_ids"
        private const val KEY_CARD_DISMISSED = "workout_import_card_dismissed"
        private const val KEY_LAST_IMPORT = "workout_import_last_millis"
        private const val KEY_DUPLICATE_ASKED = "workout_import_duplicate_asked"
        private const val KEY_BEST_BASELINE = "workout_import_best_baseline"
        private const val KEY_BEST_STREAK_RIDING = "workout_import_best_streak_riding"
    }
}
