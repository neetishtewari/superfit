package com.superfit.app.domain

/** One of the four everyday activity levels, in plain words. */
data class ActivityLevel(val multiplier: Double, val label: String, val description: String)

/** The four everyday activity levels used at sign-up and in Settings. Same multipliers as before. */
object ActivityLevels : List<ActivityLevel> by listOf(
    ActivityLevel(1.2, "Mostly sitting", "Desk job, little exercise."),
    ActivityLevel(1.375, "1-3 workouts", "Light exercise 1 to 3 days a week."),
    ActivityLevel(1.55, "3-5 workouts", "Exercise 3 to 5 days a week."),
    ActivityLevel(1.725, "6-7 workouts", "Hard exercise most days.")
) {
    fun closest(multiplier: Double): Double = minBy { kotlin.math.abs(it.multiplier - multiplier) }.multiplier
    fun describe(multiplier: Double): String = first { it.multiplier == closest(multiplier) }.description
    fun label(multiplier: Double): String = first { it.multiplier == closest(multiplier) }.label
}
