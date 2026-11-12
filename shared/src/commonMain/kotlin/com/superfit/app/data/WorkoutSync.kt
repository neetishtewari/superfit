package com.superfit.app.data

import com.superfit.app.domain.AppLog
import com.superfit.app.domain.WorkoutDisplay
import com.superfit.app.domain.WorkoutMerge
import com.superfit.app.domain.WorkoutView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One workout from the phone's health app, with the name of the app or watch that recorded it. */
data class HealthWorkout(val row: ImportedWorkoutEntity, val sourceName: String)

/**
 * Brings workouts other apps saved to the phone's health app into imported_workouts, the way
 * Android's WorkoutImporter does with Health Connect: 90 days back the first time, then the last
 * week again so edits and late syncs are picked up, and workouts deleted there go here too.
 */
object WorkoutSync {
    private const val DAY_MS = 24L * 3600 * 1000
    private const val FIRST_IMPORT_DAYS = 90
    private const val REFRESH_DAYS = 7

    // Opening the app and coming back to it must not import over each other.
    private val lock = Mutex()

    /** Returns how many workouts were read, or null when nothing could be read or the import is off. */
    suspend fun import(
        source: HealthSource,
        dao: ImportedWorkoutDao,
        prefs: WorkoutImportPrefs,
        names: HealthSourceNames,
        bmr: Double,
        nowMs: Long
    ): Int? = lock.withLock {
        if (!prefs.enabled) return@withLock null
        val from = nowMs - DAY_MS * if (prefs.lastImportMillis == 0L) FIRST_IMPORT_DAYS else REFRESH_DAYS
        val workouts = try {
            source.readWorkouts(from, nowMs, bmr)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("WorkoutSync", "Reading workouts failed", e)
            null
        } ?: return@withLock null
        // The person may have turned the import off while this one was running.
        if (!prefs.enabled) return@withLock null
        val rows = workouts.map { it.row }.filter(WorkoutMerge::isValid)
        names.remember(workouts)
        val known = dao.getBetween(from, nowMs).map { it.id }.toSet()
        dao.upsertAll(rows)
        val gone = known - rows.map { it.id }.toSet()
        if (gone.isNotEmpty()) dao.deleteByIds(gone.toList())
        prefs.lastImportMillis = nowMs
        rows.size
    }

    /** Workouts without doubles or hidden ones, newest first, named after the app that recorded them. */
    fun views(rows: List<ImportedWorkoutEntity>, prefs: WorkoutImportPrefs, names: HealthSourceNames, bmr: Double): List<WorkoutView> =
        if (!prefs.enabled) {
            emptyList()
        } else {
            WorkoutMerge.merge(rows, prefs.mainApp, prefs.hiddenIds).map {
                WorkoutDisplay.view(it, names.label(it.primary.sourcePackage), bmr)
            }
        }

    /** A workout from another app in the shape the coach already understands; calories are the exercise part only. */
    fun coachEntry(view: WorkoutView): WorkoutEntryEntity = WorkoutEntryEntity(
        description = "${view.title}, ${view.detailLine} (from ${view.sourceName})",
        caloriesBurned = view.exerciseKcal ?: 0.0,
        workoutType = if (view.title == "Strength workout") "Strength" else "Cardio",
        setsCount = 0,
        repsCount = 0,
        timestamp = view.startTime
    )
}

/**
 * The names of the apps and watches that recorded workouts ("Neetish's Apple Watch", "Strava"),
 * kept in the account's settings on the phone, since the phone gives the name only when reading.
 */
class HealthSourceNames(private val prefs: Prefs) {
    fun label(sourceId: String): String = known()[sourceId] ?: "another app"

    fun remember(workouts: List<HealthWorkout>) {
        val names = known() + workouts.filter { it.sourceName.isNotBlank() }.associate { it.row.sourcePackage to it.sourceName }
        if (names == known()) return
        prefs.edit { putStringSet(KEY, names.map { (id, name) -> id + SEPARATOR + name }.toSet()) }
    }

    private fun known(): Map<String, String> = prefs.getStringSet(KEY).mapNotNull { line ->
        val id = line.substringBefore(SEPARATOR)
        val name = line.substringAfter(SEPARATOR, "")
        if (id.isNotBlank() && name.isNotBlank()) id to name else null
    }.toMap()

    private companion object {
        const val KEY = "health_source_names"
        const val SEPARATOR = "\u0001"
    }
}
