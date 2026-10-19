package com.superfit.app.domain

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.superfit.app.data.ImportedWorkoutEntity

/** One workout as the user sees it: the best copy of a session that one or more apps recorded. */
data class MergedWorkout(
    val primary: ImportedWorkoutEntity,
    // Every app's copy of this workout, the primary included, so hiding it hides all of them.
    val memberIds: List<String>
) {
    val durationMillis: Long get() = primary.endTime - primary.startTime
}

/**
 * Turns the workouts other apps share through Health Connect into one list without doubles.
 * Samsung Health passes runs on to Strava, and Google Fit records walks inside Samsung's, so the
 * same workout often arrives two or three times.
 */
object WorkoutMerge {

    private const val DAY_MILLIS = 24L * 3600 * 1000

    /**
     * Groups copies of the same workout, keeps the best copy of each, and drops hidden ones.
     * [mainApp] (a package name) wins whenever it recorded the workout; otherwise the copy with
     * the most detail wins. Newest first.
     */
    fun merge(
        workouts: List<ImportedWorkoutEntity>,
        mainApp: String? = null,
        hiddenIds: Set<String> = emptySet()
    ): List<MergedWorkout> =
        groups(workouts.filter(::isValid))
            .filter { group -> group.none { it.id in hiddenIds } }
            .map { group -> MergedWorkout(pickPrimary(group, mainApp), group.map { it.id }) }
            .sortedByDescending { it.primary.startTime }

    /** Broken entries from other apps, such as an end before the start, are skipped. */
    fun isValid(workout: ImportedWorkoutEntity): Boolean {
        val duration = workout.endTime - workout.startTime
        return duration in 1..DAY_MILLIS
    }

    /** Same kind of exercise, overlapping for at least half of the shorter one. */
    fun sameWorkout(a: ImportedWorkoutEntity, b: ImportedWorkoutEntity): Boolean {
        if (family(a.exerciseType) != family(b.exerciseType)) return false
        val overlap = minOf(a.endTime, b.endTime) - maxOf(a.startTime, b.startTime)
        if (overlap <= 0) return false
        val shorter = minOf(a.endTime - a.startTime, b.endTime - b.startTime)
        return overlap * 2 >= shorter
    }

    /** Heart rate counts most, then calories, then distance. */
    fun detailScore(workout: ImportedWorkoutEntity): Int =
        (if (workout.avgHeartRate != null) 4 else 0) +
            (if (workout.totalKcal != null) 2 else 0) +
            (if (workout.distanceMeters != null) 1 else 0)

    private fun groups(workouts: List<ImportedWorkoutEntity>): List<List<ImportedWorkoutEntity>> {
        val sorted = workouts.sortedBy { it.startTime }
        val parent = IntArray(sorted.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) x = parent[x]
            return x
        }
        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                if (sorted[j].startTime >= sorted[i].endTime) break
                if (sameWorkout(sorted[i], sorted[j])) parent[find(j)] = find(i)
            }
        }
        return sorted.indices.groupBy(::find).values.map { indices -> indices.map { sorted[it] } }
    }

    private fun pickPrimary(group: List<ImportedWorkoutEntity>, mainApp: String?): ImportedWorkoutEntity {
        val candidates = group.filter { it.sourcePackage == mainApp }.ifEmpty { group }
        return candidates.sortedWith(
            compareByDescending<ImportedWorkoutEntity> { detailScore(it) }
                .thenByDescending { it.endTime - it.startTime }
                .thenBy { it.id }
        ).first()
    }

    // Health Connect has separate types for, say, a treadmill run and an outdoor run; apps don't
    // always agree on which one to use, so they count as the same kind here.
    private fun family(type: Int): Int = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
        else -> type
    }

    /**
     * Apps share total calories, which include what the body burns anyway. The exercise part is
     * the total minus resting burn (BMR spread over the day) for the workout's length.
     */
    fun exerciseKcal(totalKcal: Double, durationMillis: Long, bmr: Double): Double =
        (totalKcal - bmr * durationMillis.toDouble() / DAY_MILLIS).coerceAtLeast(0.0)
}
