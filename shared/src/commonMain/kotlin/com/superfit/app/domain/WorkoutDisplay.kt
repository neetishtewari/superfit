package com.superfit.app.domain

import com.superfit.app.domain.ExerciseTypes as E
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
            distanceKm?.let { "${Decimals.fixed(it, 2)} km" },
            "$minutes min",
            paceSecondsPerKm?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')} /km" },
            speedKmh?.let { "${Decimals.one(it)} km/h" },
            avgHeartRate?.let { "$it bpm" }
        ).joinToString(" · ")

    /** Lower-case name for sentences, e.g. "from your run". */
    val noun: String get() = title.lowercase()
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
        E.RUNNING, E.RUNNING_TREADMILL -> "Run"
        E.WALKING -> "Walk"
        E.HIKING -> "Hike"
        E.BIKING, E.BIKING_STATIONARY -> "Ride"
        E.SWIMMING_POOL, E.SWIMMING_OPEN_WATER -> "Swim"
        E.STRENGTH_TRAINING, E.WEIGHTLIFTING -> "Strength workout"
        E.HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        E.YOGA -> "Yoga"
        E.PILATES -> "Pilates"
        E.ELLIPTICAL -> "Elliptical"
        E.ROWING, E.ROWING_MACHINE -> "Row"
        E.STAIR_CLIMBING, E.STAIR_CLIMBING_MACHINE -> "Stair climb"
        E.DANCING -> "Dance"
        E.BADMINTON -> "Badminton"
        E.TENNIS -> "Tennis"
        E.CRICKET -> "Cricket"
        E.SOCCER -> "Football"
        E.BASKETBALL -> "Basketball"
        E.BOXING -> "Boxing"
        E.MARTIAL_ARTS -> "Martial arts"
        E.CALISTHENICS -> "Calisthenics"
        E.STRETCHING -> "Stretching"
        E.EXERCISE_CLASS -> "Exercise class"
        E.BOOT_CAMP -> "Boot camp"
        E.GYMNASTICS -> "Gymnastics"
        E.SQUASH -> "Squash"
        E.TABLE_TENNIS -> "Table tennis"
        E.VOLLEYBALL -> "Volleyball"
        E.SURFING -> "Surfing"
        E.PADDLING -> "Paddling"
        E.SKIING -> "Skiing"
        E.ROCK_CLIMBING -> "Climbing"
        E.GOLF -> "Golf"
        else -> "Workout"
    }

    private val FOOT_TYPES = setOf(
        E.RUNNING,
        E.RUNNING_TREADMILL,
        E.WALKING,
        E.HIKING
    )
    private val WHEEL_TYPES = setOf(E.BIKING, E.BIKING_STATIONARY)

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
