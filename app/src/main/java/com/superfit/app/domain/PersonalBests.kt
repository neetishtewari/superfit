package com.superfit.app.domain

/** The bests Superfit keeps. Distances are in km, pace in seconds per km (lower is better), the streak in days. */
enum class BestKind(val label: String, val workoutTitle: String?) {
    LONGEST_RUN("Longest run", "Run"),
    LONGEST_RIDE("Longest ride", "Ride"),
    LONGEST_SWIM("Longest swim", "Swim"),
    LONGEST_WALK("Longest walk", "Walk"),
    FASTEST_RUN_PACE("Fastest run pace", "Run"),
    LONGEST_STREAK("Longest streak", null)
}

/** A best and what it beat. [workout] is null for the streak; [previousTime] is null when unknown. */
data class PersonalBest(
    val kind: BestKind,
    val value: Double,
    val workout: WorkoutView?,
    val previousValue: Double?,
    val previousTime: Long?
)

/** What to offer on Home, and the starting points to remember after this check. */
data class BestCheck(
    val announce: List<PersonalBest>,
    val baseline: Map<BestKind, Double>,
    val streakRiding: Boolean
)

/**
 * Personal bests worked out on the phone from imported workouts and the meal streak.
 * The first time bests are seen they become the starting points silently, so the 90-day import
 * never floods Home with old "new bests".
 */
object PersonalBests {

    const val MIN_PACE_RUN_KM = 5.0

    // A new best must beat the old one by this much, so a GPS wobble doesn't count.
    const val DISTANCE_MARGIN_KM = 0.1
    const val PACE_MARGIN_SECONDS = 1.0
    const val STREAK_MARGIN_DAYS = 1.0
    const val MIN_STREAK_DAYS = 7

    // Older workouts (late imports, details filled in later) update the starting point quietly.
    const val ANNOUNCE_WITHIN_MILLIS = 7L * 24 * 3600 * 1000

    fun compute(workouts: List<WorkoutView>, longestStreak: Int): List<PersonalBest> {
        val bests = BestKind.entries.filter { it != BestKind.LONGEST_STREAK }.mapNotNull { kind ->
            val candidates = workouts
                .filter { it.title == kind.workoutTitle && valueOf(kind, it) != null }
                .sortedBy { it.startTime }
            val best = candidates.reduceOrNull { a, b -> if (isBetter(kind, valueOf(kind, b)!!, valueOf(kind, a)!!)) b else a }
                ?: return@mapNotNull null
            val earlier = candidates.filter { it.startTime < best.startTime }
            val previous = earlier.reduceOrNull { a, b -> if (isBetter(kind, valueOf(kind, b)!!, valueOf(kind, a)!!)) b else a }
            PersonalBest(kind, valueOf(kind, best)!!, best, previous?.let { valueOf(kind, it) }, previous?.startTime)
        }
        val streak = if (longestStreak > 0) {
            PersonalBest(BestKind.LONGEST_STREAK, longestStreak.toDouble(), null, null, null)
        } else {
            null
        }
        return bests + listOfNotNull(streak)
    }

    /** The number a workout counts for, or null when it doesn't count for [kind]. */
    fun valueOf(kind: BestKind, workout: WorkoutView): Double? = when (kind) {
        BestKind.FASTEST_RUN_PACE ->
            workout.paceSecondsPerKm?.takeIf { (workout.distanceKm ?: 0.0) >= MIN_PACE_RUN_KM }?.toDouble()
        BestKind.LONGEST_STREAK -> null
        else -> workout.distanceKm
    }

    /** True when [new] beats [old] by at least the margin. */
    fun beats(kind: BestKind, new: Double, old: Double): Boolean = when (kind) {
        BestKind.FASTEST_RUN_PACE -> old - new >= PACE_MARGIN_SECONDS
        BestKind.LONGEST_STREAK -> new - old >= STREAK_MARGIN_DAYS
        else -> new - old >= DISTANCE_MARGIN_KM - 1e-9
    }

    private fun isBetter(kind: BestKind, a: Double, b: Double): Boolean =
        if (kind == BestKind.FASTEST_RUN_PACE) a < b else a > b

    /**
     * Decides which bests to offer for sharing. A best is offered when it beats the remembered
     * starting point, came from a workout in the last week (or, for the streak, is the current run
     * of 7 days or more). It stays offered until [acknowledge] is called for it.
     */
    fun check(
        bests: List<PersonalBest>,
        baseline: Map<BestKind, Double>,
        streakRiding: Boolean,
        currentStreak: Int,
        now: Long
    ): BestCheck {
        val updated = baseline.toMutableMap()
        val longest = bests.firstOrNull { it.kind == BestKind.LONGEST_STREAK }?.value?.toInt() ?: 0
        // After a streak best is shared, each extra day of the same run is the same best.
        val riding = streakRiding && currentStreak > 0 && currentStreak >= longest
        if (riding) updated[BestKind.LONGEST_STREAK] = maxOf(updated[BestKind.LONGEST_STREAK] ?: 0.0, longest.toDouble())

        val announce = mutableListOf<PersonalBest>()
        for (best in bests) {
            val old = updated[best.kind]
            when {
                old == null -> updated[best.kind] = best.value
                beats(best.kind, best.value, old) -> {
                    val fresh = if (best.kind == BestKind.LONGEST_STREAK) {
                        longest >= MIN_STREAK_DAYS && currentStreak == longest
                    } else {
                        best.workout != null && best.workout.startTime >= now - ANNOUNCE_WITHIN_MILLIS
                    }
                    if (fresh) {
                        announce += best.copy(previousValue = best.previousValue ?: old)
                    } else {
                        updated[best.kind] = best.value
                    }
                }
                // A hidden workout no longer counts, so the starting point drops back.
                isBetter(best.kind, old, best.value) -> updated[best.kind] = best.value
            }
        }
        return BestCheck(announce, updated, riding)
    }

    /** The starting points after a best was shared or dismissed. */
    fun acknowledge(best: PersonalBest, baseline: Map<BestKind, Double>): Map<BestKind, Double> =
        baseline + (best.kind to best.value)
}
