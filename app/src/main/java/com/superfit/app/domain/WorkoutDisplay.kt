package com.superfit.app.domain

import androidx.health.connect.client.records.ExerciseSessionRecord as E
import java.util.Locale
import kotlin.math.roundToLong

/** An imported workout ready to show: names, pace and exercise calories worked out. */
data class WorkoutView(
    val merged: MergedWorkout,
    val title: String,
    val sourceName: String,
    val minutes: Long,
    val distanceKm: Double?,
    val paceSecondsPerKm: Long?,
    val speedKmh: Double?,
    val avgHeartRate: Long?,
    val exerciseKcal: Double?
) {
    val startTime: Long get() = merged.primary.startTime

    /** "8.66 km · 52 min · 6:00 /km · 148 bpm" with whatever the app shared. */
    val detailLine: String
        get() = listOfNotNull(
            distanceKm?.let { String.format(Locale.US, "%.2f km", it) },
            "$minutes min",
            paceSecondsPerKm?.let { "%d:%02d /km".format(Locale.US, it / 60, it % 60) },
            speedKmh?.let { String.format(Locale.US, "%.1f km/h", it) },
            avgHeartRate?.let { "$it bpm" }
        ).joinToString(" · ")

    /** Lower-case name for sentences, e.g. "from your run". */
    val noun: String get() = title.lowercase(Locale.getDefault())
}

object WorkoutDisplay {

    fun view(merged: MergedWorkout, sourceName: String, bmr: Double): WorkoutView {
        val w = merged.primary
        val durationMillis = merged.durationMillis
        val km = w.distanceMeters?.div(1000.0)?.takeIf { it >= 0.05 }
        val foot = w.exerciseType in FOOT_TYPES
        val wheels = w.exerciseType in WHEEL_TYPES
        return WorkoutView(
            merged = merged,
            title = title(w.exerciseType),
            sourceName = sourceName,
            minutes = (durationMillis / 60_000.0).roundToLong(),
            distanceKm = km,
            paceSecondsPerKm = if (foot && km != null) (durationMillis / 1000.0 / km).roundToLong() else null,
            speedKmh = if (wheels && km != null) km / (durationMillis / 3_600_000.0) else null,
            avgHeartRate = w.avgHeartRate,
            exerciseKcal = w.totalKcal?.let { WorkoutMerge.exerciseKcal(it, durationMillis, bmr) }
        )
    }

    /** Names for Health Connect's own exercise types; anything else is a "Workout". */
    fun title(type: Int): String = when (type) {
        E.EXERCISE_TYPE_RUNNING, E.EXERCISE_TYPE_RUNNING_TREADMILL -> "Run"
        E.EXERCISE_TYPE_WALKING -> "Walk"
        E.EXERCISE_TYPE_HIKING -> "Hike"
        E.EXERCISE_TYPE_BIKING, E.EXERCISE_TYPE_BIKING_STATIONARY -> "Ride"
        E.EXERCISE_TYPE_SWIMMING_POOL, E.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Swim"
        E.EXERCISE_TYPE_STRENGTH_TRAINING, E.EXERCISE_TYPE_WEIGHTLIFTING -> "Strength workout"
        E.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        E.EXERCISE_TYPE_YOGA -> "Yoga"
        E.EXERCISE_TYPE_PILATES -> "Pilates"
        E.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
        E.EXERCISE_TYPE_ROWING, E.EXERCISE_TYPE_ROWING_MACHINE -> "Row"
        E.EXERCISE_TYPE_STAIR_CLIMBING, E.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "Stair climb"
        E.EXERCISE_TYPE_DANCING -> "Dance"
        E.EXERCISE_TYPE_BADMINTON -> "Badminton"
        E.EXERCISE_TYPE_TENNIS -> "Tennis"
        E.EXERCISE_TYPE_CRICKET -> "Cricket"
        E.EXERCISE_TYPE_SOCCER -> "Football"
        E.EXERCISE_TYPE_BASKETBALL -> "Basketball"
        else -> "Workout"
    }

    private val FOOT_TYPES = setOf(
        E.EXERCISE_TYPE_RUNNING,
        E.EXERCISE_TYPE_RUNNING_TREADMILL,
        E.EXERCISE_TYPE_WALKING,
        E.EXERCISE_TYPE_HIKING
    )
    private val WHEEL_TYPES = setOf(E.EXERCISE_TYPE_BIKING, E.EXERCISE_TYPE_BIKING_STATIONARY)

    /**
     * A day's exercise calories: imported workouts (exercise part only) plus voice-logged ones.
     * Never added to the "burned today" number, which already comes from Health Connect.
     */
    fun dayExerciseKcal(imported: List<WorkoutView>, voiceLoggedKcal: Double): Double =
        imported.sumOf { it.exerciseKcal ?: 0.0 } + voiceLoggedKcal

    /** "from your run", or "from your workouts" when the day had several. */
    fun boostSource(imported: List<WorkoutView>, voiceLoggedCount: Int): String {
        val withKcal = imported.filter { (it.exerciseKcal ?: 0.0) > 0 }
        return if (withKcal.size == 1 && voiceLoggedCount == 0) {
            "from your ${withKcal.first().noun}"
        } else {
            "from your workouts"
        }
    }
}
