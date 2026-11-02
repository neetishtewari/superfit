package com.superfit.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyReadingsTest {

    private val t0 = Instant.parse("2026-10-23T07:00:00Z")
    private fun reading(id: String, kg: Double, fat: Double? = null, daysAgo: Long = 0, lean: Double? = null) =
        ScaleReading(id, t0.minusSeconds(daysAgo * 86400), kg, "FitDays", bodyFatPct = fat, leanKg = lean)

    @Test
    fun bodyValuesFromTheSameAppJoinTheirWeighIn() {
        val w = reading("w1", 80.0)
        val grouped = BodyReadings.group(
            weights = listOf(w),
            bodyFat = listOf(BodyValue(t0.plusSeconds(30), "FitDays", 22.5), BodyValue(t0, "Other app", 40.0)),
            lean = emptyList(),
            bone = listOf(BodyValue(t0.plusSeconds(3600), "FitDays", 3.1)), // an hour later: a different weigh-in
            bmr = listOf(BodyValue(t0, "FitDays", 1700.0))
        )
        assertEquals(22.5, grouped.single().bodyFatPct!!, 0.01)
        assertNull(grouped.single().boneKg)
        assertEquals(1700.0, grouped.single().bmrKcal!!, 0.01)
    }

    @Test
    fun closeWeightAndBodyFatIsMine() {
        assertEquals(ReadingMatch.MINE, BodyReadings.classify(reading("a", 79.2, 23.0), Baseline(80.0, 22.0)))
        assertEquals(ReadingMatch.MINE, BodyReadings.classify(reading("a", 79.2), Baseline(80.0, null)))
    }

    @Test
    fun farWeightIsAsked() {
        assertEquals(ReadingMatch.ASK, BodyReadings.classify(reading("a", 62.0), Baseline(80.0, 22.0)))
        // 7% of 60 kg is 4.2 kg, smaller than 5 kg.
        assertEquals(ReadingMatch.ASK, BodyReadings.classify(reading("a", 64.6), Baseline(60.0, null)))
    }

    @Test
    fun similarWeightButVeryDifferentBodyFatIsAsked() {
        // A partner who weighs about the same: only body fat tells them apart.
        assertEquals(ReadingMatch.ASK, BodyReadings.classify(reading("a", 79.0, 33.0), Baseline(80.0, 22.0)))
    }

    @Test
    fun firstBodyFatReadingIsAlwaysAsked() {
        assertEquals(ReadingMatch.ASK, BodyReadings.classify(reading("a", 80.0, 22.0), Baseline(80.0, null)))
    }

    @Test
    fun readingLikeOneMarkedNotMeIsSetAsideWithoutAsking() {
        val partner = reading("p1", 78.5, 33.0, daysAgo = 2)
        assertEquals(ReadingMatch.NOT_ME, BodyReadings.decide(reading("p2", 79.0, 32.0), Baseline(80.0, 22.0), listOf(partner)))
        // The user's own reading still counts as theirs.
        assertEquals(ReadingMatch.MINE, BodyReadings.decide(reading("me", 79.6, 22.4), Baseline(80.0, 22.0), listOf(partner)))
    }

    @Test
    fun leanMassBmrUsesKatchMcArdle() {
        // 80 kg at 20% body fat = 64 kg lean: 370 + 21.6 * 64 = 1752.4
        assertEquals(1752.4, BodyReadings.leanMassBmr(80.0, 20.0), 0.01)
    }

    @Test
    fun summaryShowsOnlyWhatTheScaleSends() {
        val today = LocalDate.of(2026, 10, 23)
        val weightOnly = BodyReadings.summary(listOf(reading("a", 80.0)), 80.0, 180.0, today, ZoneOffset.UTC)
        assertFalse(weightOnly.hasScaleData)
        assertEquals(24.7, weightOnly.bmi!!, 0.05)
        assertNull(weightOnly.bodyFatPct)

        val withFat = BodyReadings.summary(
            listOf(reading("old", 82.0, 24.0, daysAgo = 30), reading("new", 80.0, 22.6)),
            80.0,
            180.0,
            today,
            ZoneOffset.UTC
        )
        assertTrue(withFat.hasScaleData)
        assertEquals(22.6, withFat.bodyFatPct!!, 0.01)
        assertEquals(-1.4, withFat.bodyFatChange!!, 0.01)
        assertTrue(withFat.insight!!.contains("mostly fat"))
    }

    @Test
    fun insightCallsOutMuscleLoss() {
        // Down 2 kg, of which lean mass fell 1.5 kg.
        val text = BodyReadings.insight(weightChange = -2.0, fatChange = 0.2, leanChange = -1.5)!!
        assertTrue(text.contains("muscle"))
    }

    @Test
    fun oldBodyFatNoLongerSetsTheTarget() {
        MeasuredBody.bodyFatPct = 20.0
        MeasuredBody.measuredOn = LocalDate.of(2026, 8, 1)
        assertNull(MeasuredBody.currentBodyFat(LocalDate.of(2026, 10, 23)))
        MeasuredBody.measuredOn = LocalDate.of(2026, 10, 20)
        assertEquals(20.0, MeasuredBody.currentBodyFat(LocalDate.of(2026, 10, 23))!!, 0.01)
        MeasuredBody.bodyFatPct = null
        MeasuredBody.measuredOn = null
    }

    private val october1 = LocalDate.of(2026, 10, 1)
    private val october31 = LocalDate.of(2026, 10, 31)

    private fun scale(date: String, fat: Double? = null, bmr: Double? = null) =
        ScaleReading(date, Instant.parse("${date}T07:00:00Z"), 80.0, "FitDays", bodyFatPct = fat, bmrKcal = bmr)

    private fun monthChange(vararg r: ScaleReading) = BodyReadings.monthChange(r.toList(), october1, october31, ZoneOffset.UTC)

    @Test
    fun theMonthIsComparedWithTheLastReadingBeforeIt() {
        val c = monthChange(scale("2026-09-28", 24.0, 1600.0), scale("2026-10-05", 23.5, 1610.0), scale("2026-10-28", 23.0, 1625.0))!!
        assertEquals(-1.0, c.fatChange!!, 0.01)
        assertEquals(25.0, c.bmrChange!!, 0.01)
    }

    @Test
    fun withNoReadingBeforeTheMonthAnEarlyOneInItCounts() {
        assertEquals(-1.0, monthChange(scale("2026-10-02", 24.0), scale("2026-10-20", 23.0))!!.fatChange!!, 0.01)
        assertNull(monthChange(scale("2026-10-15", 24.0), scale("2026-10-20", 23.0))) // too close together
    }

    @Test
    fun scaleNoiseIsNotAChange() {
        assertNull(monthChange(scale("2026-09-28", 24.0, 1600.0), scale("2026-10-28", 23.7, 1610.0)))
    }

    @Test
    fun oneMovingAndTheOtherNotShowsOnlyTheOneThatMoved() {
        val c = monthChange(scale("2026-09-28", 24.0, 1600.0), scale("2026-10-28", 23.7, 1630.0))!!
        assertNull(c.fatChange)
        assertEquals(30.0, c.bmrChange!!, 0.01)
    }

    @Test
    fun noReadingsOrOnlyStaleOnesMeanNoChange() {
        assertNull(monthChange())
        assertNull(monthChange(scale("2026-09-28", 24.0))) // nothing in the month itself
        assertNull(monthChange(scale("2026-07-01", 30.0), scale("2026-10-28", 23.0))) // too long ago to compare
        assertNull(monthChange(reading("no fat or bmr", 80.0)))
    }

    @Test
    fun theBodyCardReportsTheChangeInRestingBurn() {
        val s = BodyReadings.summary(
            listOf(reading("then", 80.0, daysAgo = 30).copy(bmrKcal = 1650.0), reading("now", 79.0).copy(bmrKcal = 1630.0)),
            79.0,
            175.0,
            LocalDate.of(2026, 10, 23),
            ZoneOffset.UTC
        )
        assertEquals(1630.0, s.scaleBmr!!, 0.01)
        assertEquals(-20.0, s.scaleBmrChange!!, 0.01)
    }
}
