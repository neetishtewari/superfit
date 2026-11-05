package com.superfit.app.domain

import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinLocalDate
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyTrendWeekReportTest {

    private val today = LocalDate.of(2026, 11, 2)

    private fun r(date: LocalDate, kg: Double, fat: Double) =
        ScaleReading("$date$kg", date.atTime(7, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), kg, "FitDays", bodyFatPct = fat)

    @Test
    fun weekReportAddsTheBodyLineOnlyWhenItMoved() {
        val mine = listOf(r(today.minusDays(30), 80.0, fat = 26.0), r(today.minusDays(2), 78.0, fat = 24.5))
        val body = BodyTrend.fourWeeks(mine, today.toKotlinLocalDate(), TimeZone.UTC)
        val report = InsightsEngine.weekReport(emptyMap(), today, loseGoal = true, zone = ZoneOffset.UTC, body = body)
        assertTrue(report.working.any { it.id == "body_fat_down" })
        val none = InsightsEngine.weekReport(emptyMap(), today, loseGoal = true, zone = ZoneOffset.UTC, body = null)
        assertTrue(none.working.none { it.id.startsWith("body_") } && none.watch.none { it.id.startsWith("body_") })
    }
}
