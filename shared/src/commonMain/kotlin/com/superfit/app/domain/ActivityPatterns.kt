package com.superfit.app.domain

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.datetime.DayOfWeek

/** Same kind of walk or run as before, with the heart working less: a sign of getting fitter. */
data class HeartChange(val activity: String, val paceNow: Long, val pacePrev: Long, val bpmNow: Int, val bpmPrev: Int) {
    val bpmDrop: Int get() = bpmPrev - bpmNow
}

/** Reps logged by voice: the total, the period before, and the exercise done most when it's clear. */
data class RepTotals(val total: Int, val prev: Int, val top: Pair<String, Int>?)

/** Meals on days with a workout or 7,500+ steps against rest days. */
data class MoveDayPlates(val activeRating: Double, val restRating: Double, val activeProteinPct: Int?, val restProteinPct: Int?)

/** When the user tends to move: a usual workout hour and their biggest step day of the week. */
data class MovingRhythm(
    /** Most common start hour (0-23), when most workouts start within an hour of it. */
    val hour: Int?,
    val workoutsNearHour: Int,
    val workoutsTimed: Int,
    val bestDay: DayOfWeek?,
    val bestDaySteps: Int,
    val avgSteps: Int
)

/**
 * Patterns in the activity data that the plain totals miss. Each one only answers when there is
 * enough data for it to be true, and every number is worked out here, never by the AI.
 */
object ActivityPatterns {

    /**
     * Runs first, then walks: average heart rate on 1 km+ outings now against before, at the same
     * pace or faster. Null unless the heart rate is at least 3 bpm lower.
     */
    fun heart(now: List<ActivityFact>, before: List<ActivityFact>, minNow: Int = 2, minBefore: Int = 3): HeartChange? {
        fun usable(list: List<ActivityFact>, run: Boolean) = list.filter {
            (if (run) it.isRun else it.isWalk) && it.avgHeartRate != null && it.paceSecondsPerKm != null && (it.distanceKm ?: 0.0) >= 1.0
        }
        for (run in listOf(true, false)) {
            val a = usable(now, run)
            val b = usable(before, run)
            if (a.size < minNow || b.size < minBefore) continue
            val paceNow = weightedPace(a)
            val pacePrev = weightedPace(b)
            // A slower pace would explain a lower heart rate, so it isn't a fitness sign.
            if (paceNow > pacePrev + 5) continue
            val bpmNow = a.map { it.avgHeartRate!! }.average().roundToInt()
            val bpmPrev = b.map { it.avgHeartRate!! }.average().roundToInt()
            if (bpmPrev - bpmNow >= 3) return HeartChange(if (run) "run" else "walk", paceNow, pacePrev, bpmNow, bpmPrev)
        }
        return null
    }

    private fun weightedPace(list: List<ActivityFact>): Long {
        val km = list.sumOf { it.distanceKm!! }
        return (list.sumOf { it.paceSecondsPerKm!! * it.distanceKm!! } / km).roundToLong()
    }

    /** Voice-logged reps; null when fewer than [minTotal]. */
    fun reps(now: List<ActivityFact>, before: List<ActivityFact>, minTotal: Int = 50): RepTotals? {
        val withReps = now.filter { it.reps > 0 }
        val total = withReps.sumOf { it.reps }
        if (total < minTotal) return null
        val prev = before.sumOf { it.reps }
        // Only name an exercise when every workout's words add up to the reps the app stored.
        val parsed = withReps.map { exercises(it.title).takeIf { ex -> ex.isNotEmpty() && ex.sumOf { e -> e.second } == it.reps } }
        val top = if (parsed.all { it != null }) {
            parsed.flatMap { it!! }.groupBy { it.first.lowercase().replace(Regex("[^a-z]"), "") }
                .map { (_, list) -> list.first().first to list.sumOf { it.second } }
                .maxByOrNull { it.second }
                ?.takeIf { it.second * 2 >= total }
        } else {
            null
        }
        return RepTotals(total, prev, top)
    }

    private val NOT_EXERCISES = setOf("set", "sets", "min", "mins", "minute", "minutes", "sec", "secs", "second", "seconds", "hour", "hours", "km", "kg", "lb", "lbs", "rep", "reps", "round", "rounds", "x", "times")

    /** "20 burpees, 50 jumping jacks and 30 pushups" -> [(burpees, 20), (jumping jacks, 50), (pushups, 30)]. */
    fun exercises(text: String): List<Pair<String, Int>> =
        text.split(Regex(",|;|\\band\\b|\\+|\\.")).mapNotNull { part ->
            val m = Regex("^\\s*(\\d{1,4})\\s+([A-Za-z][A-Za-z\\- ]{1,30})\\s*$").find(part) ?: return@mapNotNull null
            val name = m.groupValues[2].trim().lowercase()
            if (name.split(' ').first() in NOT_EXERCISES) null else name to m.groupValues[1].toInt()
        }

    /** Average meal rating and protein on active against rest days; null unless active days are clearly better. */
    fun moveDayPlates(days: List<DayTotals>, minEach: Int = 4): MoveDayPlates? {
        val rated = days.filter { it.logged && it.mealRating != null }
        val active = rated.filter { it.active }
        val rest = rated.filter { !it.active }
        if (active.size < minEach || rest.size < minEach) return null
        val a = active.mapNotNull { it.mealRating }.average()
        val r = rest.mapNotNull { it.mealRating }.average()
        if (a - r < 0.7) return null
        fun protein(list: List<DayTotals>) = list.mapNotNull { it.proteinShare }.takeIf { it.size >= 2 }?.let { (it.average() * 100).roundToInt() }
        return MoveDayPlates(round1(a), round1(r), protein(active), protein(rest))
    }

    /** A usual workout hour (from 6+ timed workouts) and the biggest step day (from 2+ weeks of steps). */
    fun rhythm(days: List<DayTotals>): MovingRhythm? {
        val hours = days.flatMap { it.workouts }.mapNotNull { it.startHour }
        val hour = if (hours.size >= 6) {
            val mode = hours.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
            val near = hours.count { abs(it - mode) <= 1 }
            if (near * 10 >= hours.size * 6) mode to near else null
        } else {
            null
        }
        val stepDays = days.filter { it.steps > 0 }
        val best = if (stepDays.size >= 14) {
            val avg = stepDays.map { it.steps }.average()
            stepDays.groupBy { it.date.dayOfWeek }.filter { it.value.size >= 2 }
                .mapValues { (_, list) -> list.map { it.steps }.average() }
                .maxByOrNull { it.value }
                ?.takeIf { it.value >= avg * 1.15 && it.value >= 5000 }
                ?.let { Triple(it.key, it.value.roundToInt(), avg.roundToInt()) }
        } else {
            null
        }
        if (hour == null && best == null) return null
        return MovingRhythm(hour?.first, hour?.second ?: 0, hours.size, best?.first, best?.second ?: 0, best?.third ?: 0)
    }

    /** "7 am", "12 pm", "6 pm". */
    fun hourLabel(h: Int): String = when {
        h == 0 -> "12 am"
        h < 12 -> "$h am"
        h == 12 -> "12 pm"
        else -> "${h - 12} pm"
    }

    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0
}
