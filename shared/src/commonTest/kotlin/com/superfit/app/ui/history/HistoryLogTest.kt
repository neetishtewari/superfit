package com.superfit.app.ui.history

import com.superfit.app.data.MemoryPrefs
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.WorkoutEntryEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class HistoryLogTest {

    private fun meal(kcal: Double) = NutritionEntryEntity(foodText = "meal", calories = kcal, proteinG = 10.0, carbsG = 10.0, fatG = 5.0, timestamp = 0)
    private val target = { _: LocalDate -> 2000.0 }

    @Test
    fun aMonthHasEveryDayAndADayNeedsTwoMealsToCount() {
        val nutrition = mapOf(
            LocalDate.parse("2026-02-03") to listOf(meal(700.0), meal(800.0)),
            LocalDate.parse("2026-02-04") to listOf(meal(1500.0), meal(900.0)),
            LocalDate.parse("2026-02-05") to listOf(meal(400.0))
        )

        val days = HistoryLog.monthDays(LocalDate.parse("2026-02-17"), nutrition, target)

        assertEquals(28, days.size)
        assertEquals(LocalDate.parse("2026-02-01"), days.first().date)
        assertEquals(DayStatus.Deficit, days[2].status)
        assertEquals(DayStatus.Surplus, days[3].status)
        // One meal isn't a full day, so it never reads as eating under target.
        assertEquals(DayStatus.Insufficient, days[4].status)
    }

    @Test
    fun consistencyCountsOnlyFullyLoggedDaysAndAnyWorkoutDay() {
        val today = LocalDate.parse("2026-11-11")
        val nutrition = mapOf(
            today to listOf(meal(700.0), meal(800.0)),
            LocalDate.parse("2026-11-10") to listOf(meal(1500.0), meal(900.0)),
            LocalDate.parse("2026-11-09") to listOf(meal(300.0))
        )
        val workouts = mapOf(
            LocalDate.parse("2026-11-10") to listOf(WorkoutEntryEntity(description = "run", caloriesBurned = 300.0, workoutType = "Cardio", setsCount = 0, repsCount = 0, timestamp = 0))
        )

        val result = HistoryLog.consistency(today, nutrition, workouts, emptyMap(), target)

        assertEquals(2, result.loggedDays)
        assertEquals(1, result.deficitDays)
        assertEquals(50, result.score)
        assertEquals(3, result.trainingScore)
    }

    @Test
    fun ownCalorieNumberWinsOverTheWorkedOutTarget() {
        val prefs = MemoryPrefs()
        prefs.edit {
            putBoolean("custom_macro_enabled", true)
            putInt("custom_calories", 1800)
        }

        assertEquals(1800.0, HistoryLog.calorieTarget(null, prefs, emptyList(), emptyList()))
    }

    @Test
    fun theChartShowsTheWeekUpToThePickedDay() {
        val days = HistoryLog.monthDays(LocalDate.parse("2026-11-01"), emptyMap(), target)

        val week = HistoryLog.chartWeek(days, LocalDate.parse("2026-11-10"))

        assertEquals(LocalDate.parse("2026-11-04"), week.first().date)
        assertEquals(LocalDate.parse("2026-11-10"), week.last().date)
    }
}
