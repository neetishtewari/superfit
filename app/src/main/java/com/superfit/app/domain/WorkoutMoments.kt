package com.superfit.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The one line a workout card can carry: something that stands out about this workout against
 * the user's own earlier ones. Worked out here, never by the AI, and never about food or weight.
 * Null when nothing stands out, so the card stays numbers only.
 */
object WorkoutMoments {

    private const val DAY = 86_400_000L

    fun line(workout: WorkoutView, history: List<WorkoutView>, zone: ZoneId = ZoneId.systemDefault()): String? {
        // Only what happened before this workout, without this one or its copies.
        val earlier = history.filter { it.startTime < workout.startTime && it.merged.primary.id !in workout.merged.memberIds }
        return fitterHeart(workout, earlier)
            ?: hardest(workout, earlier, zone)
            ?: longest(workout, earlier, zone)
            ?: busiestWeek(workout, earlier, zone)
    }

    /** Runs and walks of 1 km+: last 30 days against the 30 before, at the same pace or faster. */
    fun fitterHeart(workout: WorkoutView, earlier: List<WorkoutView>): String? {
        if (workout.title != "Run" && workout.title != "Walk") return null
        if ((workout.distanceKm ?: 0.0) < 1.0 || workout.avgHeartRate == null) return null
        val t = workout.startTime
        val sameKind = earlier.filter { it.title == workout.title }
        val now = sameKind.filter { it.startTime >= t - 30 * DAY } + workout
        val before = sameKind.filter { it.startTime in (t - 60 * DAY) until (t - 30 * DAY) }
        val change = ActivityPatterns.heart(now.map { ActivityFact.of(it) }, before.map { ActivityFact.of(it) }, minNow = 3, minBefore = 3)
            ?: return null
        if (change.activity != workout.noun || change.bpmDrop < 5) return null
        val faster = change.paceNow < change.pacePrev - 5
        return if (faster) {
            "Faster than last month, ${change.bpmDrop} bpm lower heart rate"
        } else {
            "Same pace as last month, ${change.bpmDrop} bpm lower heart rate"
        }
    }

    /** The highest average heart rate for this kind of workout in a month or more. Walks don't count. */
    fun hardest(workout: WorkoutView, earlier: List<WorkoutView>, zone: ZoneId = ZoneId.systemDefault()): String? {
        if (workout.title == "Workout" || workout.title == "Walk") return null
        val bpm = workout.avgHeartRate ?: return null
        val sameKind = earlier.filter { it.title == workout.title && it.avgHeartRate != null }
        if (sameKind.size < 4) return null
        return record(workout, sameKind, zone, "Hardest") { (it.avgHeartRate ?: 0L) >= bpm }
    }

    /** The longest of this kind of workout in a month or more: by distance when it has one, else by time. */
    fun longest(workout: WorkoutView, earlier: List<WorkoutView>, zone: ZoneId = ZoneId.systemDefault()): String? {
        if (workout.title == "Workout" || workout.minutes < 10) return null
        val sameKind = earlier.filter { it.title == workout.title }
        if (sameKind.size < 3) return null
        val km = workout.distanceKm
        return record(workout, sameKind, zone, "Longest") {
            if (km != null) (it.distanceKm ?: 0.0) >= km else it.minutes >= workout.minutes
        }
    }

    /**
     * "Hardest run since August" when the last one at least as big is 30+ days back, or
     * "Hardest run in 45 days" when none is and the earliest of its kind is 30+ days back.
     */
    private fun record(workout: WorkoutView, sameKind: List<WorkoutView>, zone: ZoneId, word: String, atLeastAsBig: (WorkoutView) -> Boolean): String? {
        val name = sentenceName(workout.title)
        val lastAsBig = sameKind.filter(atLeastAsBig).maxByOrNull { it.startTime }
        if (lastAsBig != null) {
            if (workout.startTime - lastAsBig.startTime < 30 * DAY) return null
            return "$word $name since ${monthName(lastAsBig.startTime, workout.startTime, zone)}"
        }
        val days = ((workout.startTime - sameKind.minOf { it.startTime }) / DAY.toDouble()).roundToInt()
        return if (days >= 30) "$word $name in $days days" else null
    }

    /** Three or more workouts this week (walks aside), more than any week in 8 or more. */
    fun busiestWeek(workout: WorkoutView, earlier: List<WorkoutView>, zone: ZoneId = ZoneId.systemDefault()): String? {
        if (workout.title == "Walk") return null
        val weekStart = { millis: Long ->
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().with(WeekFields.ISO.dayOfWeek(), 1)
        }
        val thisWeek = weekStart(workout.startTime)
        val counted = earlier.filter { it.title != "Walk" }
        val count = counted.count { weekStart(it.startTime) == thisWeek } + 1
        if (count < 3) return null
        val before = counted.filter { weekStart(it.startTime) < thisWeek }
        if (before.isEmpty()) return null
        val perWeek = before.groupingBy { weekStart(it.startTime) }.eachCount()
        val lastAsBusy = perWeek.filter { it.value >= count }.keys.maxOrNull()
        if (lastAsBusy != null) {
            val weeks = ChronoUnit.WEEKS.between(lastAsBusy, thisWeek)
            if (weeks < 8) return null
            return "Most workouts in a week since ${monthName(lastAsBusy, thisWeek)}"
        }
        val weeks = ChronoUnit.WEEKS.between(perWeek.keys.min(), thisWeek)
        return if (weeks >= 8) "Most workouts in a week in $weeks weeks" else null
    }

    /** "strength workout" in a sentence, but "HIIT" stays as it is. */
    fun sentenceName(title: String): String = if (title.none { it.isLowerCase() }) title else title.lowercase(Locale.getDefault())

    private fun monthName(thenMillis: Long, nowMillis: Long, zone: ZoneId): String =
        monthName(Instant.ofEpochMilli(thenMillis).atZone(zone).toLocalDate(), Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate())

    /** "August", or "August 2025" when it was another year. */
    private fun monthName(then: LocalDate, now: LocalDate): String {
        val month = then.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        return if (then.year == now.year) month else "$month ${then.year}"
    }
}
