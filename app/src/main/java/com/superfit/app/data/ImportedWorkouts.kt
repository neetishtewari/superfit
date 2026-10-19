package com.superfit.app.data

import android.content.Context
import com.superfit.app.domain.DailyTarget
import com.superfit.app.domain.PhysiologyEngine
import com.superfit.app.domain.WorkoutDisplay
import com.superfit.app.domain.WorkoutMerge
import com.superfit.app.domain.WorkoutView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Shared by Home, History, the coach and the evening reminder, so they all agree on a day's workouts and target. */
object ImportedWorkouts {

    /** Workouts without doubles or hidden ones, newest first. */
    fun views(context: Context, rows: List<ImportedWorkoutEntity>, prefs: WorkoutImportPrefs, bmr: Double): List<WorkoutView> =
        WorkoutMerge.merge(rows, prefs.mainApp, prefs.hiddenIds).map {
            WorkoutDisplay.view(it, SourceAppNames.label(context, it.primary.sourcePackage), bmr)
        }

    fun byDate(views: List<WorkoutView>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, List<WorkoutView>> =
        views.groupBy { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }

    /**
     * A day's targets with that day's workouts added back for people who connected them.
     * Imported workouts only count while the import is on.
     */
    fun target(
        profile: UserProfileEntity,
        prefs: WorkoutImportPrefs,
        imported: List<WorkoutView>,
        voiceLogged: List<WorkoutEntryEntity>
    ): DailyTarget = PhysiologyEngine.dailyTarget(
        profile = profile,
        workoutsConnected = prefs.enabled,
        addBack = prefs.addBack,
        exerciseKcal = WorkoutDisplay.dayExerciseKcal(
            if (prefs.enabled) imported else emptyList(),
            voiceLogged.sumOf { it.caloriesBurned }
        )
    )

    /** The shape the coach already understands; calories are the exercise part only. */
    fun coachEntry(view: WorkoutView): WorkoutEntryEntity = WorkoutEntryEntity(
        description = "${view.title}, ${view.detailLine} (from ${view.sourceName})",
        caloriesBurned = view.exerciseKcal ?: 0.0,
        workoutType = if (view.title == "Strength workout") "Strength" else "Cardio",
        setsCount = 0,
        repsCount = 0,
        timestamp = view.startTime
    )
}
