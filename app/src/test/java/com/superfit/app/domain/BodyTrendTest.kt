package com.superfit.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyTrendTest {

    private val utc = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 2)

    private fun r(date: LocalDate, kg: Double, fat: Double? = null, bmr: Double? = null, lean: Double? = null) =
        ScaleReading("$date$kg", date.atTime(7, 0).toInstant(utc), kg, "FitDays", bodyFatPct = fat, leanKg = lean, bmrKcal = bmr)

    @Test
    fun weightOnlyScalesSayNothing() {
        val mine = listOf(r(today.minusDays(30), 80.0), r(today.minusDays(2), 78.0))
        assertNull(BodyTrend.fourWeeks(mine, today, utc))
    }

    @Test
    fun averagesEachEndSoOneOddReadingDoesNotCount() {
        val mine = listOf(
            r(today.minusDays(30), 80.0, fat = 25.0, bmr = 1600.0),
            r(today.minusDays(28), 80.0, fat = 27.0, bmr = 1600.0),
            r(today.minusDays(3), 78.0, fat = 24.0, bmr = 1630.0),
            r(today.minusDays(1), 78.0, fat = 24.4, bmr = 1630.0)
        )
        val c = BodyTrend.fourWeeks(mine, today, utc)!!
        assertEquals(-1.8, c.fat!!, 0.001)
        assertEquals(30.0, c.bmr!!, 0.001)
        assertTrue(c.notable)
        val i = BodyTrend.finding(c, loseGoal = true)!!
        assertEquals("body_fat_down", i.id)
        assertTrue(i.detail.contains("Resting burn up 30 kcal"))
        assertFalse(BodyTrend.isWatch(i))
    }

    @Test
    fun smallWobblesAreNotNews() {
        val mine = listOf(r(today.minusDays(30), 80.0, fat = 25.0), r(today.minusDays(2), 79.8, fat = 24.8))
        val c = BodyTrend.fourWeeks(mine, today, utc)!!
        assertFalse(c.notable)
    }

    @Test
    fun muscleGoingWithTheWeightIsAHeadsUp() {
        val mine = listOf(
            r(today.minusDays(30), 80.0, fat = 25.0, lean = 60.0),
            r(today.minusDays(2), 77.0, fat = 25.2, lean = 57.6)
        )
        val i = BodyTrend.finding(BodyTrend.fourWeeks(mine, today, utc)!!, loseGoal = true)!!
        assertEquals("body_muscle_loss", i.id)
        assertTrue(BodyTrend.isWatch(i))
    }

    @Test
    fun monthComparesItsStartWithItsEnd() {
        val oct = YearMonth.of(2026, 10)
        val mine = listOf(
            r(LocalDate.of(2026, 9, 28), 80.0, fat = 26.0),
            r(LocalDate.of(2026, 10, 5), 79.6, fat = 25.6),
            r(LocalDate.of(2026, 10, 15), 79.0, fat = 25.0),
            r(LocalDate.of(2026, 10, 29), 78.4, fat = 24.6)
        )
        val c = BodyTrend.month(mine, oct, utc)!!
        assertEquals(-1.2, c.fat!!, 0.001)
        assertNotNull(c.lean)
    }

    @Test
    fun weekReportAddsTheBodyLineOnlyWhenItMoved() {
        val mine = listOf(r(today.minusDays(30), 80.0, fat = 26.0), r(today.minusDays(2), 78.0, fat = 24.5))
        val body = BodyTrend.fourWeeks(mine, today, utc)
        val report = InsightsEngine.weekReport(emptyMap(), today, loseGoal = true, zone = utc, body = body)
        assertTrue(report.working.any { it.id == "body_fat_down" })
        val none = InsightsEngine.weekReport(emptyMap(), today, loseGoal = true, zone = utc, body = null)
        assertTrue(none.working.none { it.id.startsWith("body_") } && none.watch.none { it.id.startsWith("body_") })
    }
}
