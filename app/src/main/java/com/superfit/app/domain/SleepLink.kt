package com.superfit.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * A sleep line for a workout card, only when the user's own workouts prove the link: at least 10
 * earlier ones of the same kind with sleep data, and the ones after 7+ hour nights clearly better.
 * Shown only after a good night, so it's never a telling-off after a short one.
 */
object SleepLink {

    const val GOOD_NIGHT_HOURS = 7.0
    const val MIN_WORKOUTS = 10
    private const val MIN_PER_GROUP = 3

    /** [sleepHours] is the night that ended on each date (the morning of the workout). */
    fun line(workout: WorkoutView, earlier: List<WorkoutView>, sleepHours: Map<LocalDate, Double>, zone: ZoneId = ZoneId.systemDefault()): String? {
        fun day(w: WorkoutView) = Instant.ofEpochMilli(w.startTime).atZone(zone).toLocalDate()
        val lastNight = sleepHours[day(workout)]?.takeIf { it >= GOOD_NIGHT_HOURS } ?: return null
        if (workout.title == "Workout") return null
        val withSleep = earlier.filter { it.title == workout.title && sleepHours[day(it)] != null && it.startTime < workout.startTime }
        if (withSleep.size < MIN_WORKOUTS) return null
        val (good, short) = withSleep.partition { (sleepHours[day(it)] ?: 0.0) >= GOOD_NIGHT_HOURS }
        if (good.size < MIN_PER_GROUP || short.size < MIN_PER_GROUP) return null
        val proven = if (workout.title == "Run") runsBetter(good, short) else easierHeart(good, short)
        if (!proven) return null
        val slept = hours(lastNight)
        return if (workout.title == "Run") {
            "Slept $slept. Your best runs follow 7+ hour nights."
        } else {
            "Slept $slept. Your ${plural(WorkoutMoments.sentenceName(workout.title))} feel easier after 7+ hour nights."
        }
    }

    /** Runs after good nights at least 10 s/km faster, at a similar heart rate when both have one. */
    fun runsBetter(good: List<WorkoutView>, short: List<WorkoutView>): Boolean {
        val g = good.mapNotNull { it.paceSecondsPerKm }
        val s = short.mapNotNull { it.paceSecondsPerKm }
        if (g.size < MIN_PER_GROUP || s.size < MIN_PER_GROUP) return false
        if (g.average() > s.average() - 10) return false
        val gh = good.mapNotNull { it.avgHeartRate }
        val sh = short.mapNotNull { it.avgHeartRate }
        // Faster only because they pushed harder isn't a sleep effect.
        if (gh.size >= MIN_PER_GROUP && sh.size >= MIN_PER_GROUP && gh.average() > sh.average() + 3) return false
        return true
    }

    /** At least 5 bpm lower average heart rate after good nights, for workouts of about the same length. */
    fun easierHeart(good: List<WorkoutView>, short: List<WorkoutView>): Boolean {
        val g = good.filter { it.avgHeartRate != null }
        val s = short.filter { it.avgHeartRate != null }
        if (g.size < MIN_PER_GROUP || s.size < MIN_PER_GROUP) return false
        val gm = g.map { it.minutes }.average()
        val sm = s.map { it.minutes }.average()
        if (sm <= 0 || gm < sm * 0.8 || gm > sm * 1.2) return false
        return g.map { it.avgHeartRate!! }.average() <= s.map { it.avgHeartRate!! }.average() - 5
    }

    /** "7h 40m". */
    fun hours(h: Double): String {
        val total = (h * 60).roundToInt()
        return "${total / 60}h ${"%02d".format(total % 60)}m"
    }

    private fun plural(name: String): String = when {
        name == "HIIT" -> "HIIT sessions"
        name.endsWith("s") -> "$name sessions"
        else -> name + "s"
    }
}
