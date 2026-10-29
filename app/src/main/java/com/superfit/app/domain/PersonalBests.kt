package com.superfit.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** How a best is measured: one workout, or the total over a finished day, week or month. */
enum class BestPeriod { WORKOUT, DAY, WEEK, MONTH }

/**
 * The bests Superfit keeps. Distances are in km, pace in seconds per km (lower is better), steps in
 * steps. Pace bests are timed over the whole run, since Health Connect doesn't share km splits, so
 * "Fastest 10K+ pace" is the fastest run of about 10 km or longer.
 */
enum class BestKind(
    val label: String,
    val workoutTitle: String?,
    val period: BestPeriod = BestPeriod.WORKOUT,
    val minRunKm: Double? = null
) {
    FASTEST_RUN_PACE("Fastest 5K+ pace", "Run", minRunKm = 5.0),
    FASTEST_10K_PACE("Fastest 10K+ pace", "Run", minRunKm = 10.0),
    FASTEST_HALF_PACE("Fastest half marathon+ pace", "Run", minRunKm = 21.0975),
    LONGEST_RUN("Longest run", "Run"),
    LONGEST_RIDE("Longest ride", "Ride"),
    LONGEST_SWIM("Longest swim", "Swim"),
    LONGEST_WALK("Longest walk", "Walk"),
    MOST_STEPS_DAY("Most steps in a day", null, BestPeriod.DAY),
    MOST_STEPS_WEEK("Most steps in a week", null, BestPeriod.WEEK),
    MOST_STEPS_MONTH("Most steps in a month", null, BestPeriod.MONTH),
    MOST_DISTANCE_DAY("Most km on foot in a day", null, BestPeriod.DAY),
    MOST_DISTANCE_WEEK("Most km on foot in a week", null, BestPeriod.WEEK),
    MOST_DISTANCE_MONTH("Most km on foot in a month", null, BestPeriod.MONTH);

    val isPace: Boolean get() = minRunKm != null
    val isSteps: Boolean get() = this == MOST_STEPS_DAY || this == MOST_STEPS_WEEK || this == MOST_STEPS_MONTH
}

/**
 * A best and what it beat. [rank] is 1 for the best itself and 2 or 3 for the runners-up kept for
 * runs. [workout] is set for workout bests and [periodStart] for day, week and month totals.
 * [achievedAt] is when it happened: the workout's start, or the end of the day, week or month.
 */
data class PersonalBest(
    val kind: BestKind,
    val value: Double,
    val workout: WorkoutView?,
    val previousValue: Double?,
    val previousTime: Long?,
    val rank: Int = 1,
    val periodStart: LocalDate? = null,
    val achievedAt: Long = workout?.startTime ?: 0L,
    val runnersUp: List<PersonalBest> = emptyList()
) {
    /** "Fastest 10K+ pace", or "2nd fastest 10K+ pace" for a runner-up. */
    val title: String
        get() = if (rank == 1) kind.label else "${ordinal(rank)} ${kind.label.replaceFirstChar { it.lowercase() }}"

    /** Remembers a runner-up once it was offered, so each one is offered only once. */
    val podiumKey: String? get() = workout?.let { "${kind.name}:${it.merged.primary.id}" }

    private fun ordinal(n: Int) = when (n) {
        2 -> "2nd"
        3 -> "3rd"
        else -> "${n}th"
    }
}

/** One day's steps, as Health Connect counted them. */
data class DaySteps(val date: LocalDate, val steps: Int)

/** What to offer on Home, and the starting points to remember after this check. */
data class BestCheck(
    val announce: List<PersonalBest>,
    val baseline: Map<BestKind, Double>,
    val podiumSeen: Set<String>
)

/**
 * Personal bests worked out on the phone from imported workouts and daily steps.
 * The first time bests are seen they become the starting points silently, so the 90-day import
 * never floods Home with old "new bests". Day, week and month totals only count once that day,
 * week or month is over, so a best is offered once and not again every hour as it grows.
 */
object PersonalBests {

    // A run this much short of the distance still counts, so a 9.96 km run is a 10K.
    const val NEAR_MISS = 0.01

    const val PODIUM_SIZE = 3

    // A new best must beat the old one by this much, so a GPS wobble doesn't count.
    const val DISTANCE_MARGIN_KM = 0.1
    const val PACE_MARGIN_SECONDS = 1.0
    const val STEPS_MARGIN = 100.0

    // Older workouts (late imports, details filled in later) update the starting point quietly.
    const val ANNOUNCE_WITHIN_MILLIS = 7L * 24 * 3600 * 1000

    // Runner-ups worth a moment of their own.
    private val podiumKinds = setOf(BestKind.FASTEST_RUN_PACE, BestKind.FASTEST_10K_PACE, BestKind.FASTEST_HALF_PACE, BestKind.LONGEST_RUN)

    private data class Entry(val value: Double, val at: Long, val workout: WorkoutView? = null, val periodStart: LocalDate? = null)

    fun compute(
        workouts: List<WorkoutView>,
        steps: List<DaySteps>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<PersonalBest> = BestKind.entries.mapNotNull { kind ->
        val entries = when (kind.period) {
            BestPeriod.WORKOUT -> workouts.mapNotNull { w -> valueOf(kind, w)?.let { Entry(it, w.startTime, w) } }
            else -> periodTotals(kind, workouts, steps, today, zone)
        }.sortedBy { it.at }
        best(kind, entries)
    }

    private fun best(kind: BestKind, entries: List<Entry>): PersonalBest? {
        val top = entries.reduceOrNull { a, b -> if (isBetter(kind, b.value, a.value)) b else a } ?: return null
        val previous = entries.filter { it.at < top.at }.reduceOrNull { a, b -> if (isBetter(kind, b.value, a.value)) b else a }
        val runnersUp = if (kind in podiumKinds) {
            entries.filter { it !== top }
                .sortedWith { a, b ->
                    if (isBetter(kind, a.value, b.value)) {
                        -1
                    } else if (isBetter(kind, b.value, a.value)) {
                        1
                    } else {
                        0
                    }
                }
                .take(PODIUM_SIZE - 1)
                .mapIndexed { i, e -> PersonalBest(kind, e.value, e.workout, null, null, rank = i + 2, achievedAt = e.at) }
        } else {
            emptyList()
        }
        return PersonalBest(
            kind = kind,
            value = top.value,
            workout = top.workout,
            previousValue = previous?.value,
            previousTime = previous?.at,
            periodStart = top.periodStart,
            achievedAt = top.at,
            runnersUp = runnersUp
        )
    }

    /** Totals for each finished day, week or month. Distance is runs and walks added up. */
    private fun periodTotals(
        kind: BestKind,
        workouts: List<WorkoutView>,
        steps: List<DaySteps>,
        today: LocalDate,
        zone: ZoneId
    ): List<Entry> {
        val perDay: Map<LocalDate, Double> = if (kind.isSteps) {
            steps.filter { it.steps > 0 }.associate { it.date to it.steps.toDouble() }
        } else {
            workouts.filter { (it.title == "Run" || it.title == "Walk") && (it.distanceKm ?: 0.0) > 0 }
                .groupBy { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
                .mapValues { (_, ws) -> ws.sumOf { it.distanceKm ?: 0.0 } }
        }
        return perDay.entries
            .groupBy { periodStart(kind.period, it.key) }
            .mapNotNull { (start, days) ->
                val end = periodEnd(kind.period, start)
                if (!end.isBefore(today)) return@mapNotNull null
                // Steps are counted every day, so a week or month with gaps in the data would undercount.
                if (kind.isSteps && days.size < minStepDays(kind.period, start)) return@mapNotNull null
                val endMillis = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                Entry(days.sumOf { it.value }, endMillis, periodStart = start)
            }
    }

    /** Days of step data a week or month needs to count: all but one of a week, all but three of a month. */
    private fun minStepDays(period: BestPeriod, start: LocalDate): Int = when (period) {
        BestPeriod.WEEK -> 6
        BestPeriod.MONTH -> start.lengthOfMonth() - 3
        else -> 1
    }

    fun periodStart(period: BestPeriod, date: LocalDate): LocalDate = when (period) {
        BestPeriod.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        BestPeriod.MONTH -> date.withDayOfMonth(1)
        else -> date
    }

    private fun periodEnd(period: BestPeriod, start: LocalDate): LocalDate = when (period) {
        BestPeriod.WEEK -> start.plusDays(6)
        BestPeriod.MONTH -> start.plusMonths(1).minusDays(1)
        else -> start
    }

    /** The number a workout counts for, or null when it doesn't count for [kind]. */
    fun valueOf(kind: BestKind, workout: WorkoutView): Double? {
        if (kind.workoutTitle == null || workout.title != kind.workoutTitle) return null
        val min = kind.minRunKm ?: return workout.distanceKm
        return workout.paceSecondsPerKm?.takeIf { (workout.distanceKm ?: 0.0) >= min * (1 - NEAR_MISS) }?.toDouble()
    }

    /** True when [new] beats [old] by at least the margin. */
    fun beats(kind: BestKind, new: Double, old: Double): Boolean = when {
        kind.isPace -> old - new >= PACE_MARGIN_SECONDS
        kind.isSteps -> new - old >= STEPS_MARGIN
        else -> new - old >= DISTANCE_MARGIN_KM - 1e-9
    }

    private fun isBetter(kind: BestKind, a: Double, b: Double): Boolean = if (kind.isPace) a < b else a > b

    /**
     * Decides which bests to offer for sharing. A best is offered when it beats the remembered
     * starting point and happened in the last week. A recent 2nd or 3rd best run is offered once
     * too. Each stays offered until [acknowledge] is called for it.
     */
    fun check(bests: List<PersonalBest>, baseline: Map<BestKind, Double>, podiumSeen: Set<String>, now: Long): BestCheck {
        val updated = baseline.toMutableMap()
        val seen = podiumSeen.toMutableSet()
        val announce = mutableListOf<PersonalBest>()
        val podium = mutableListOf<PersonalBest>()
        for (best in bests) {
            val old = updated[best.kind]
            val firstLook = old == null
            when {
                old == null -> updated[best.kind] = best.value
                beats(best.kind, best.value, old) -> {
                    if (best.achievedAt >= now - ANNOUNCE_WITHIN_MILLIS) {
                        announce += best.copy(previousValue = best.previousValue ?: old)
                    } else {
                        updated[best.kind] = best.value
                    }
                }
                // A hidden workout no longer counts, so the starting point drops back.
                isBetter(best.kind, old, best.value) -> updated[best.kind] = best.value
            }
            for (up in best.runnersUp) {
                val key = up.podiumKey ?: continue
                if (key in seen) continue
                if (firstLook || up.achievedAt < now - ANNOUNCE_WITHIN_MILLIS) seen += key else podium += up
            }
        }
        return BestCheck(announce + podium, updated, seen)
    }

    /** The starting points after a best was shared or dismissed. */
    fun acknowledge(best: PersonalBest, baseline: Map<BestKind, Double>): Map<BestKind, Double> =
        if (best.rank == 1) baseline + (best.kind to best.value) else baseline

    /** Runner-ups already offered, after one was shared or dismissed. */
    fun acknowledgePodium(best: PersonalBest, seen: Set<String>): Set<String> = best.podiumKey?.let { seen + it } ?: seen

    /** Steps rounded for display, e.g. "23,410". */
    fun stepsText(value: Double): String = "%,d".format(value.roundToInt())
}
