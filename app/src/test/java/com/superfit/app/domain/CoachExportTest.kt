package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachExportTest {

    private val today = LocalDate.of(2026, 10, 22)
    private val zone = ZoneOffset.UTC
    private val header = CoachExportHeader("Sam", "LOSE_WEIGHT", 34, 175.0, true, 82.0, 75.0)

    private fun meal(date: LocalDate, hour: Int, name: String, kcal: Double, protein: Double) = NutritionEntryEntity(
        foodText = name,
        calories = kcal,
        proteinG = protein,
        carbsG = 50.0,
        fatG = 20.0,
        timestamp = date.atTime(hour, 0).toInstant(zone).toEpochMilli()
    )

    private fun build(meals: List<NutritionEntryEntity>, weeks: Int = 2, weighIns: List<Pair<LocalDate, Double>> = emptyList(), workouts: Map<LocalDate, List<ActivityFact>> = emptyMap()): CoachExport {
        val days = meals.groupBy { java.time.Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .mapValues { (date, list) -> DayTotals(date, list.sumOf { it.calories }, 2000.0, list.size, list.sumOf { it.proteinG }, 120.0, 0, workouts[date].orEmpty()) }
            .toMutableMap()
        workouts.forEach { (date, w) -> if (date !in days) days[date] = DayTotals(date, 0.0, 2000.0, 0, 0.0, 120.0, 0, w) }
        return CoachExportBuilder.build(header, weeks, today, days, meals, weighIns, 120.0, listOf(null, 70), zone)
    }

    @Test
    fun periodEndsYesterdayAndCoversWholeWeeks() {
        val r = build(emptyList(), weeks = 4)
        assertEquals(LocalDate.of(2026, 10, 21), r.to)
        assertEquals(LocalDate.of(2026, 9, 24), r.from)
        assertEquals(28, r.totalDays)
    }

    @Test
    fun averagesCountOnlyLoggedDays() {
        val d1 = today.minusDays(1)
        val d2 = today.minusDays(2)
        val r = build(
            listOf(
                meal(d1, 8, "Oats", 600.0, 30.0),
                meal(d1, 13, "Dal rice", 1000.0, 60.0),
                meal(d2, 8, "Eggs", 400.0, 40.0),
                meal(d2, 20, "Chicken", 1200.0, 80.0),
                meal(today.minusDays(3), 9, "Just a snack", 200.0, 5.0) // one meal: not fully logged
            )
        )
        assertEquals(2, r.daysLogged)
        assertEquals(1600, r.avgKcal)
        assertEquals(105, r.avgProteinG)
        assertEquals(88, r.proteinPct) // 75% and 100% (capped)
        assertFalse(r.days.first { it.date == today.minusDays(3) }.logged)
        assertEquals(listOf("08:00", "13:00"), r.days.first { it.date == d1 }.meals.map { it.time })
    }

    @Test
    fun nothingLoggedHasNoAverages() {
        val r = build(emptyList())
        assertEquals(0, r.daysLogged)
        assertNull(r.avgKcal)
        assertNull(r.proteinPct)
    }

    @Test
    fun weightChangeUsesFirstAndLastWeighInInsideThePeriod() {
        val r = build(
            emptyList(),
            weighIns = listOf(today.minusDays(30) to 84.0, today.minusDays(10) to 82.5, today.minusDays(2) to 81.7, today to 81.0)
        )
        assertEquals(82.5, r.weightStart!!.second, 0.01)
        assertEquals(81.7, r.weightEnd!!.second, 0.01)
    }

    @Test
    fun singleWeighInHasNoEnd() {
        val r = build(emptyList(), weighIns = listOf(today.minusDays(3) to 80.0))
        assertEquals(80.0, r.weightStart!!.second, 0.01)
        assertNull(r.weightEnd)
    }

    @Test
    fun workoutsAreCountedAndDescribed() {
        val run = ActivityFact("Run", 32, 5.24, 400.0, 366, true)
        val r = build(emptyList(), workouts = mapOf(today.minusDays(4) to listOf(run)))
        assertEquals(1, r.workoutCount)
        assertEquals(32L, r.workoutMinutes)
        assertEquals(listOf("Run · 32 min · 5.2 km"), r.days.first { it.date == today.minusDays(4) }.workouts)
        assertTrue(r.days.first { it.date == today.minusDays(4) }.meals.isEmpty())
    }

    @Test
    fun walksAreGroupedAndCountedApart() {
        val walk = ActivityFact("Walk", 15, 1.0, 60.0, null, false)
        val run = ActivityFact("Run", 30, 5.0, 350.0, 360, true)
        val day = today.minusDays(3)
        val r = build(emptyList(), workouts = mapOf(day to listOf(walk, run, walk.copy(minutes = 20, distanceKm = 1.5))))
        assertEquals(1, r.workoutCount)
        assertEquals(2, r.walkCount)
        assertEquals(listOf("2× Walk · 35 min · 2.5 km", "Run · 30 min · 5.0 km"), r.days.first { it.date == day }.workouts)
    }
}
