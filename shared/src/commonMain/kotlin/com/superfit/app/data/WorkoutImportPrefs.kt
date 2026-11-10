package com.superfit.app.data

import com.superfit.app.domain.BestKind
import com.superfit.app.domain.WorkoutAddBack

/**
 * The user's choices for workouts from other apps, kept in their own prefs. Every key starts with
 * "workout_import" so screens can listen for changes. The import switch, main app, add-back
 * choice, hidden workouts and personal-best starting points are backed up to the cloud; the card
 * and prompt flags stay on the phone.
 */
class WorkoutImportPrefs(private val prefs: Prefs) {

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    /** Package name of the app that always wins when two apps recorded the same workout; null for Automatic. */
    var mainApp: String?
        get() = prefs.getString(KEY_MAIN_APP, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_MAIN_APP, value ?: "") }

    var addBack: WorkoutAddBack
        get() = runCatching { WorkoutAddBack.valueOf(prefs.getString(KEY_ADD_BACK, null) ?: "") }
            .getOrDefault(WorkoutAddBack.ALL)
        set(value) = prefs.edit { putString(KEY_ADD_BACK, value.name) }

    val hiddenIds: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN)

    fun hide(ids: Collection<String>) {
        prefs.edit { putStringSet(KEY_HIDDEN, hiddenIds + ids) }
    }

    fun unhideAll() {
        prefs.edit { putStringSet(KEY_HIDDEN, emptySet()) }
    }

    var cardDismissed: Boolean
        get() = prefs.getBoolean(KEY_CARD_DISMISSED, false)
        set(value) = prefs.edit { putBoolean(KEY_CARD_DISMISSED, value) }

    /** Imported workouts already asked about after a voice-logged one, so each is asked about once. */
    fun wasAskedAboutDuplicate(id: String): Boolean = id in prefs.getStringSet(KEY_DUPLICATE_ASKED)

    fun markAskedAboutDuplicate(id: String) {
        prefs.edit { putStringSet(KEY_DUPLICATE_ASKED, prefs.getStringSet(KEY_DUPLICATE_ASKED) + id) }
    }

    /** When the last import finished; 0 before the first one, which reads 90 days back. */
    var lastImportMillis: Long
        get() = prefs.getLong(KEY_LAST_IMPORT, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_IMPORT, value) }

    /** Personal bests already seen or shared, so each new best is offered once, even after a reinstall. */
    var bestBaseline: Map<BestKind, Double>
        get() = decodeBests(prefs.getStringSet(KEY_BEST_BASELINE))
        set(value) = prefs.edit { putStringSet(KEY_BEST_BASELINE, encodeBests(value)) }

    /** 2nd and 3rd best runs already offered, so each is offered once. */
    var bestPodiumSeen: Set<String>
        get() = prefs.getStringSet(KEY_BEST_PODIUM)
        set(value) = prefs.edit { putStringSet(KEY_BEST_PODIUM, value) }

    /** What the cloud backup holds. */
    fun toBackup(): Map<String, Any> = mapOf(
        KEY_ENABLED to enabled,
        KEY_MAIN_APP to (mainApp ?: ""),
        KEY_ADD_BACK to addBack.name,
        KEY_HIDDEN to hiddenIds.toList(),
        KEY_BEST_BASELINE to encodeBests(bestBaseline).toList(),
        KEY_BEST_PODIUM to bestPodiumSeen.toList()
    )

    /** Restores the backup, keeping workouts hidden on this phone hidden. */
    fun restore(data: Map<String, Any>) {
        val hidden = (data[KEY_HIDDEN] as? List<*>)?.filterIsInstance<String>().orEmpty()
        prefs.edit {
            (data[KEY_ENABLED] as? Boolean)?.let { putBoolean(KEY_ENABLED, it) }
            (data[KEY_MAIN_APP] as? String)?.let { putString(KEY_MAIN_APP, it) }
            (data[KEY_ADD_BACK] as? String)?.let { putString(KEY_ADD_BACK, it) }
            putStringSet(KEY_HIDDEN, hiddenIds + hidden)
            val bests = (data[KEY_BEST_BASELINE] as? List<*>)?.filterIsInstance<String>().orEmpty()
            if (bests.isNotEmpty()) putStringSet(KEY_BEST_BASELINE, encodeBests(decodeBests(bests) + bestBaseline))
            val podium = (data[KEY_BEST_PODIUM] as? List<*>)?.filterIsInstance<String>().orEmpty()
            putStringSet(KEY_BEST_PODIUM, podium.toSet() + bestPodiumSeen)
        }
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
        private const val KEY_BEST_PODIUM = "workout_import_best_podium"
    }
}
