package com.superfit.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant

class BodyHistoryTest {

    private val utc = TimeZone.UTC
    private val today = LocalDate(2026, 11, 6)
    private fun ago(n: Int) = today.minus(n, DateTimeUnit.DAY)

    @OptIn(kotlin.time.ExperimentalTime::class)
    private fun r(date: LocalDate, kg: Double, fat: Double? = null, lean: Double? = null, bone: Double? = null) =
        ScaleReading("$date$kg", date.atTime(LocalTime(7, 0)).toInstant(utc).toEpochMilliseconds(), kg, "FitDays", bodyFatPct = fat, leanKg = lean, boneKg = bone)

    private fun series(weighIns: List<Pair<LocalDate, Double>>, scale: List<ScaleReading> = emptyList(), goal: Double = -5.0, period: BodyPeriod = BodyPeriod.DEFAULT) =
        BodyHistory.series(weighIns, scale, heightCm = 175.0, goalKgChange = goal, period = period, today = today, zone = utc)

    @Test
    fun withoutAScaleOnlyWeightAndBmiShow() {
        val s = series(listOf(ago(60) to 82.0, ago(30) to 81.0, ago(1) to 80.0))
        assertEquals(listOf(BodyMeasure.WEIGHT, BodyMeasure.BMI), s.map { it.measure })
        assertEquals(-2.0, s.first().change, 1e-9)
        assertEquals(BodyDirection.GOOD, s.first().direction)
    }

    @Test
    fun aMeasureNeedsTwoDaysInThePeriod() {
        val s = series(listOf(ago(200) to 85.0, ago(1) to 80.0))
        assertTrue(s.isEmpty())
        val year = series(listOf(ago(200) to 85.0, ago(1) to 80.0), period = BodyPeriod.YEAR)
        assertEquals(2, year.size)
    }

    @Test
    fun scaleMeasuresShowWhenTheScaleSendsThem() {
        val scale = listOf(r(ago(80), 82.0, fat = 28.0, lean = 56.0, bone = 3.1), r(ago(2), 80.0, fat = 26.0, lean = 56.4, bone = 3.1))
        val s = series(listOf(ago(80) to 82.0, ago(2) to 80.0), scale)
        assertEquals(listOf(BodyMeasure.WEIGHT, BodyMeasure.BODY_FAT, BodyMeasure.MUSCLE, BodyMeasure.BONE, BodyMeasure.BMI), s.map { it.measure })
        val byMeasure = s.associateBy { it.measure }
        assertEquals(BodyDirection.GOOD, byMeasure.getValue(BodyMeasure.BODY_FAT).direction)
        assertEquals(BodyDirection.GOOD, byMeasure.getValue(BodyMeasure.MUSCLE).direction)
        assertEquals(BodyDirection.NEUTRAL, byMeasure.getValue(BodyMeasure.BONE).direction)
        assertTrue(byMeasure.getValue(BodyMeasure.BONE).steady)
    }

    @Test
    fun weightGoingUpIsGoodWhenTheGoalIsToGain() {
        val s = series(listOf(ago(40) to 60.0, ago(1) to 62.0), goal = 5.0)
        assertEquals(BodyDirection.GOOD, s.first().direction)
        val lose = series(listOf(ago(40) to 60.0, ago(1) to 62.0), goal = -5.0)
        assertEquals(BodyDirection.NOT_GOOD, lose.first().direction)
    }

    @Test
    fun severalReadingsOnADayKeepTheLast() {
        val s = series(listOf(ago(10) to 81.0, ago(1) to 80.5, ago(1) to 80.0))
        assertEquals(2, s.first().points.size)
        assertEquals(80.0, s.first().latest, 1e-9)
    }

    @Test
    fun periodFromLabelFallsBackToThreeMonths() {
        assertEquals(BodyPeriod.SIX_MONTHS, BodyPeriod.fromLabel("6M"))
        assertEquals(BodyPeriod.THREE_MONTHS, BodyPeriod.fromLabel(null))
    }
}
