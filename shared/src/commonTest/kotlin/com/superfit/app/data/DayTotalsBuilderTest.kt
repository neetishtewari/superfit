package com.superfit.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class DayTotalsBuilderTest {

    private val zone = TimeZone.of("Asia/Kolkata")
    private fun at(date: String, hour: Int) = LocalDateTime(LocalDate.parse(date), LocalTime(hour, 0)).toInstant(zone).toEpochMilliseconds()

    @Test
    fun eachDayGetsItsMealsStepsAndWorkoutsInTheLocalZone() {
        val days = DayTotalsBuilder.days(
            nutrition = listOf(
                NutritionEntryEntity(foodText = "dal", calories = 300.0, proteinG = 12.0, carbsG = 40.0, fatG = 8.0, timestamp = at("2026-11-09", 13)),
                // 00:30 in India is still the 10th there, though it's the 9th in UTC.
                NutritionEntryEntity(foodText = "milk", calories = 100.0, proteinG = 6.0, carbsG = 8.0, fatG = 4.0, timestamp = at("2026-11-10", 0) + 30 * 60_000)
            ),
            voiceWorkouts = listOf(
                WorkoutEntryEntity(description = "20 push-ups", caloriesBurned = 15.0, workoutType = "Strength", setsCount = 1, repsCount = 20, timestamp = at("2026-11-09", 18))
            ),
            activity = listOf(ActivityTelemetryEntity(date = "2026-11-09", steps = 8000, activeCalories = 300.0)),
            imported = emptyMap(),
            kcalTarget = { 2000.0 },
            proteinTargetG = 120.0,
            zone = zone
        )

        val ninth = days.getValue(LocalDate.parse("2026-11-09"))
        assertEquals(300.0, ninth.kcalEaten)
        assertEquals(8000, ninth.steps)
        assertEquals(listOf("20 push-ups"), ninth.workouts.map { it.title })
        assertEquals(20, ninth.workouts.single().reps)
        assertEquals(100.0, days.getValue(LocalDate.parse("2026-11-10")).kcalEaten)
    }
}
