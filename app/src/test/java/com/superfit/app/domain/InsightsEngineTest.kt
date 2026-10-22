package com.superfit.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsEngineTest {

    // A Monday, so the 7 full days before it run Mon 12 Oct to Sun 18 Oct.
    private val today = LocalDate.of(2026, 10, 19)

    private fun run(km: Double, paceSec: Long, kcal: Double = 500.0) =
        ActivityFact("Run", minutes = km.times(paceSec).div(60).toLong(), distanceKm = km, kcal = kcal, paceSecondsPerKm = paceSec, isRun = true)

    private fun day(
        date: LocalDate,
        eaten: Double = 1800.0,
        target: Double = 2000.0,
        meals: Int = 3,
        protein: Double = 110.0,
        steps: Int = 6000,
        workouts: List<ActivityFact> = emptyList()
    ) = DayTotals(date, eaten, target, meals, protein, 110.0, steps, workouts)

    private fun map(vararg d: DayTotals) = d.associateBy { it.date }

    @Test
    fun weekLeavesTodayOutAndComparesWithTheWeekBefore() {
        val days = map(
            day(today, workouts = listOf(run(10.0, 330))), // today: not counted
            day(today.minusDays(1), workouts = listOf(run(5.0, 330))),
            day(today.minusDays(8), workouts = listOf(run(3.0, 330)))
        )
        val r = InsightsEngine.weekReport(days, today)
        assertEquals(LocalDate.of(2026, 10, 12), r.from)
        assertEquals(LocalDate.of(2026, 10, 18), r.to)
        assertEquals(5.0, r.thisWeek.distanceKm, 0.01)
        assertEquals(3.0, r.lastWeek.distanceKm, 0.01)
        assertEquals(7, r.dailyNet.size)
    }

    @Test
    fun proteinIsAPercentOfGoalWithEachDayCappedAndBlankDaysLeftOut() {
        val days = map(
            day(today.minusDays(1), protein = 88.0), // 80%
            day(today.minusDays(2), protein = 220.0), // 200%, counts as 100%
            day(today.minusDays(3), protein = 66.0), // 60%
            day(today.minusDays(4), protein = 0.0, meals = 0) // not logged: left out
        )
        val s = InsightsEngine.weekReport(days, today).thisWeek
        assertEquals(80, s.proteinPct)
        assertEquals(1, s.proteinDaysHit)
        assertEquals(3, s.loggedDays)
    }

    @Test
    fun closeToProteinGoalEveryDayIsNotFlaggedAsShort() {
        val days = map(*(1L..5L).map { day(today.minusDays(it), protein = 97.0) }.toTypedArray()) // 88% each
        val r = InsightsEngine.weekReport(days, today)
        assertEquals(88, r.thisWeek.proteinPct)
        assertTrue(r.watch.none { it.id == "protein_low" })
    }

    @Test
    fun proteinWellUnderGoalIsWatched() {
        val days = map(*(1L..4L).map { day(today.minusDays(it), protein = 55.0) }.toTypedArray()) // 50%
        val r = InsightsEngine.weekReport(days, today)
        assertTrue(r.watch.any { it.id == "protein_low" && it.detail.contains("50%") })
    }

    @Test
    fun noLoggedDaysHasNoProteinPercent() {
        assertNull(InsightsEngine.weekReport(emptyMap(), today).thisWeek.proteinPct)
    }

    @Test
    fun blankDaysAreUnknownNotUnderEating() {
        val days = map(day(today.minusDays(1), eaten = 300.0, meals = 1))
        val r = InsightsEngine.weekReport(days, today)
        assertEquals(0, r.thisWeek.loggedDays)
        assertNull(r.dailyNet.last().second)
        assertEquals(0.0, r.thisWeek.netKcal, 0.0)
        assertTrue(r.watch.any { it.id == "logging" })
    }

    @Test
    fun weekendSurplusThatCancelsTheWeekdayDeficitIsFlagged() {
        val week = (1..7).map { today.minusDays(it.toLong()) }.map { d ->
            if (d.dayOfWeek.value >= 6) day(d, eaten = 2800.0) else day(d, eaten = 1600.0)
        }
        val r = InsightsEngine.weekReport(map(*week.toTypedArray()), today)
        val weekend = r.watch.firstOrNull { it.id == "weekend" }
        assertNotNull(weekend)
        assertTrue(weekend!!.title.contains("800"))
        assertTrue(r.verdict.contains("weekend eating"))
    }

    @Test
    fun underFuelledBigRunNamesTheDay() {
        val sat = LocalDate.of(2026, 10, 17)
        val r = InsightsEngine.weekReport(map(day(sat, protein = 50.0, workouts = listOf(run(8.7, 334, kcal = 610.0)))), today)
        val f = r.watch.firstOrNull { it.id == "fuel_low" }
        assertNotNull(f)
        assertTrue(f!!.detail.startsWith("Saturday's run 8.7 km"))
    }

    @Test
    fun activeWeekAndFasterRunsAreWorking() {
        val days = mutableMapOf<LocalDate, DayTotals>()
        for (i in 1..28) days[today.minusDays(i.toLong())] = day(today.minusDays(i.toLong()), steps = 9000, workouts = listOf(run(5.0, 320)))
        for (i in 29..56) days[today.minusDays(i.toLong())] = day(today.minusDays(i.toLong()), workouts = listOf(run(5.0, 350)))
        val r = InsightsEngine.weekReport(days, today)
        assertEquals("active", r.working[0].id)
        assertTrue(r.working.any { it.id == "pace" })
        assertTrue(r.working.first { it.id == "pace" }.detail.contains("30 s faster"))
        assertTrue(r.working.size <= 2)
    }

    @Test
    fun fewerWorkoutsThanUsualIsWatched() {
        val days = mutableMapOf<LocalDate, DayTotals>()
        for (i in 8..28) days[today.minusDays(i.toLong())] = day(today.minusDays(i.toLong()), workouts = listOf(run(3.0, 360)))
        days[today.minusDays(2)] = day(today.minusDays(2), workouts = listOf(run(3.0, 360)))
        val r = InsightsEngine.weekReport(days, today)
        assertTrue(r.watch.any { it.id == "fewer" })
    }

    @Test
    fun usualNeedsTwoEarlierSameWeekdays() {
        assertNull(UsualDay.forDay(map(day(today.minusWeeks(1), steps = 8000)), today))
        val u = UsualDay.forDay(map(day(today.minusWeeks(1), steps = 8000), day(today.minusWeeks(2), steps = 6000), day(today.minusDays(1), steps = 20000)), today)
        assertEquals(7000, u!!.steps)
        assertEquals(2, u.samples)
    }

    @Test
    fun gainGoalDoesNotCallASurplusAProblem() {
        val week = (1..7).map { day(today.minusDays(it.toLong()), eaten = 2400.0) }
        val r = InsightsEngine.weekReport(map(*week.toTypedArray()), today, loseGoal = false)
        assertFalse(r.watch.any { it.id == "over" || it.id == "weekend" })
    }

    @Test
    fun todayVsUsualOnlyNudgesLateInTheDay() {
        val usual = UsualDay(steps = 8000, distanceKm = 6.0, moveMinutes = 40, workoutKcal = 300.0, samples = 3)
        val quiet = TodayVsUsual.of(today, 2000, emptyList(), usual)
        assertNull(quiet.coachLine(hour = 11))
        assertTrue(quiet.coachLine(hour = 19)!!.contains("6,000 steps short"))
        val busy = TodayVsUsual.of(today, 9000, listOf(run(8.0, 330)), usual)
        assertTrue(busy.coachLine(hour = 9)!!.contains("steps and move time"))
        assertEquals(44, busy.moveMinutes)
        assertTrue(busy.chatLine().contains("steps 9000 vs 8000"))
        assertNull(TodayVsUsual.of(today, 9000, emptyList(), null).coachLine(20))
    }
}
