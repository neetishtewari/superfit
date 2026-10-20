package com.superfit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalForecastTest {

    private val today = LocalDate.of(2026, 10, 19)
    private val start = today.minusDays(57)

    private fun day(date: LocalDate, eaten: Double, target: Double = 2000.0, workouts: Int = 0) =
        DayTotals(date, eaten, target, 3, 110.0, 110.0, 6000, List(workouts) { ActivityFact("Run", 40, 6.0, 450.0, 330, true) })

    /** Weigh-ins every 7 days losing [early] kg/week for the first 4 weeks, then [late]. */
    private fun weighIns(from: Double, early: Double, late: Double): List<Pair<LocalDate, Double>> {
        val out = mutableListOf<Pair<LocalDate, Double>>()
        var kg = from
        for (w in 0..8) {
            out += start.plusDays(7L * w) to kg
            kg -= if (w < 4) early else late
        }
        return out
    }

    private fun forecast(
        weighIns: List<Pair<LocalDate, Double>>,
        days: Map<LocalDate, DayTotals> = emptyMap(),
        targetDate: LocalDate? = null,
        startKg: Double = 82.0,
        targetKg: Double = 75.0
    ) = GoalForecast.compute(startKg, targetKg, start, weighIns, days, calorieOffset = -500, targetDate = targetDate, today = today, fallbackCurrentKg = startKg)

    @Test
    fun goalCoveredAndForecastFromWeighIns() {
        val f = forecast(weighIns(82.0, 0.5, 0.5))
        assertEquals(78.0, f.currentKg, 0.01)
        assertEquals(57, f.percentCovered)
        assertEquals(3.0, f.kgToGo, 0.01)
        assertTrue(f.rateFromWeighIns)
        assertEquals(0.5, f.recentRate!!, 0.05)
        assertEquals(ForecastStatus.ON_TRACK, f.status)
        // 3 kg at 0.5 kg a week is about 6 weeks away.
        assertEquals(today.plusWeeks(6).toEpochDay().toDouble(), f.forecastDate!!.toEpochDay().toDouble(), 7.0)
    }

    @Test
    fun slowingPaceIsAtRisk() {
        val f = forecast(weighIns(82.0, 0.6, 0.2))
        assertEquals(ForecastStatus.AT_RISK, f.status)
        assertTrue(f.explanation.contains("slowed"))
    }

    @Test
    fun lateAgainstTargetDateSaysHowManyWeeks() {
        val f = forecast(weighIns(82.0, 0.5, 0.25), targetDate = today.plusWeeks(4))
        assertEquals(ForecastStatus.AT_RISK, f.status)
        assertTrue(f.weeksLate!! > 0)
        assertTrue(f.explanation.contains("later than your target date"))
    }

    @Test
    fun weightNotMovingIsOffTrack() {
        val flat = (0..8).map { start.plusDays(7L * it) to 80.0 }
        val f = forecast(flat)
        assertEquals(ForecastStatus.OFF_TRACK, f.status)
        assertNull(f.forecastDate)
    }

    @Test
    fun withoutWeighInsLoggedEatingDrivesThePace() {
        // Eating 200 under a target that is already 500 under maintenance: 700 a day, about 0.64 kg a week.
        val days = (1..28).associate { today.minusDays(it.toLong()) to day(today.minusDays(it.toLong()), 1800.0) }
        val f = forecast(emptyList(), days)
        assertTrue(!f.rateFromWeighIns)
        assertEquals(0.64, f.recentRate!!, 0.02)
        assertNotNull(f.forecastDate)
    }

    @Test
    fun tooLittleDataSaysWhatToLog() {
        val f = forecast(listOf(today.minusDays(3) to 80.0))
        assertEquals(ForecastStatus.NOT_ENOUGH_DATA, f.status)
        assertEquals(3L, f.daysSinceWeighIn)
    }

    @Test
    fun weekendFixShowsWeeksSooner() {
        val days = (1..28).associate { i ->
            val d = today.minusDays(i.toLong())
            d to day(d, if (d.dayOfWeek.value >= 6) 2900.0 else 1500.0)
        }
        val f = forecast(weighIns(82.0, 0.3, 0.2), days)
        assertTrue(f.slowing.any { it.id == "weekend" })
        val fix = f.fixes.firstOrNull { it.label.startsWith("Keep weekends") }
        assertNotNull(fix)
        assertTrue(fix!!.weeksSooner!! >= 1)
    }

    @Test
    fun passingTheGoalIsReached() {
        val f = forecast(listOf(today.minusDays(20) to 76.0, today.minusDays(2) to 74.0))
        assertEquals(ForecastStatus.REACHED, f.status)
        assertEquals(100, f.percentCovered)
        assertTrue(f.fixes.isEmpty())
    }

    @Test
    fun gainGoalCountsUpwards() {
        val ups = (0..8).map { start.plusDays(7L * it) to 60.0 + it * 0.25 }
        val f = GoalForecast.compute(60.0, 65.0, start, ups, emptyMap(), 300, null, today, 60.0)
        assertEquals(40, f.percentCovered)
        assertEquals(0.25, f.recentRate!!, 0.03)
        assertEquals(ForecastStatus.ON_TRACK, f.status)
    }

    @Test
    fun milestonesRecordWhenTheyWereReached() {
        val f = forecast(weighIns(82.0, 0.5, 0.5))
        val first = f.milestones.first()
        assertEquals("First 2 kg down", first.label)
        assertEquals(start.plusWeeks(4), first.reachedOn)
        assertNull(f.milestones.last().reachedOn)
    }
}
