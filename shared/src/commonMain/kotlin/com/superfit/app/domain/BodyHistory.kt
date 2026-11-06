package com.superfit.app.domain

import kotlin.math.abs
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/** How far back Journey's body trends look. Three months is the default. */
enum class BodyPeriod(val label: String, val days: Int) {
    MONTH("1M", 30),
    THREE_MONTHS("3M", 91),
    SIX_MONTHS("6M", 182),
    YEAR("1Y", 365);

    companion object {
        val DEFAULT = THREE_MONTHS
        fun fromLabel(label: String?): BodyPeriod = entries.firstOrNull { it.label == label } ?: DEFAULT
    }
}

/** Which way is good for a measure: weight and BMI follow the goal, body fat down, muscle up, bone neither. */
enum class BodyMeasure(val label: String, val unit: String, val steadyBelow: Double) {
    WEIGHT("Weight", "kg", 0.2),
    BODY_FAT("Body fat", "%", 0.2),
    MUSCLE("Muscle", "kg", 0.2),
    BONE("Bone mass", "kg", 0.1),
    BMI("BMI", "", 0.1)
}

/** One day's value; several readings on a day keep the last. */
data class BodyPoint(val date: LocalDate, val value: Double)

enum class BodyDirection { GOOD, NOT_GOOD, NEUTRAL }

data class BodySeries(val measure: BodyMeasure, val points: List<BodyPoint>, val direction: BodyDirection) {
    val latest: Double get() = points.last().value
    val change: Double get() = points.last().value - points.first().value
    val steady: Boolean get() = abs(change) < measure.steadyBelow
}

/**
 * Journey's "How your body has changed": one series per measure the person actually has, over the
 * chosen period. Weight comes from the weight log (scale readings already land there), body fat,
 * muscle and bone from smart scale readings, and BMI from weight and height so everyone has it.
 * A measure shows only with at least two days of readings in the period; nothing is filled in.
 */
@OptIn(ExperimentalTime::class)
object BodyHistory {

    /** [goalKgChange] is target minus start weight: below zero to lose, above to gain, about zero to keep. */
    fun series(
        weighIns: List<Pair<LocalDate, Double>>,
        scale: List<ScaleReading>,
        heightCm: Double,
        goalKgChange: Double,
        period: BodyPeriod,
        today: LocalDate,
        zone: TimeZone = TimeZone.currentSystemDefault()
    ): List<BodySeries> {
        val from = today.minus(period.days, DateTimeUnit.DAY)
        fun daily(values: List<Pair<LocalDate, Double>>) = values
            .filter { it.first in from..today }
            .groupBy { it.first }
            .map { (date, list) -> BodyPoint(date, list.last().second) }
            .sortedBy { it.date }

        val readings = scale.sortedBy { it.timeMs }.map { Instant.fromEpochMilliseconds(it.timeMs).toLocalDateTime(zone).date to it }
        fun scaleDaily(f: (ScaleReading) -> Double?) = daily(readings.mapNotNull { (d, r) -> f(r)?.let { d to it } })

        val weight = daily(weighIns)
        val bmi = if (heightCm > 0) weight.mapNotNull { p -> BodyReadings.bmi(p.value, heightCm)?.let { BodyPoint(p.date, it) } } else emptyList()

        fun dir(measure: BodyMeasure, points: List<BodyPoint>): BodyDirection {
            val change = points.last().value - points.first().value
            if (abs(change) < measure.steadyBelow) return BodyDirection.NEUTRAL
            val goodIfDown = when (measure) {
                BodyMeasure.WEIGHT, BodyMeasure.BMI -> when {
                    goalKgChange <= -0.5 -> true
                    goalKgChange >= 0.5 -> false
                    else -> return BodyDirection.NEUTRAL
                }
                BodyMeasure.BODY_FAT -> true
                BodyMeasure.MUSCLE -> false
                BodyMeasure.BONE -> return BodyDirection.NEUTRAL
            }
            return if ((change < 0) == goodIfDown) BodyDirection.GOOD else BodyDirection.NOT_GOOD
        }

        return listOf(
            BodyMeasure.WEIGHT to weight,
            BodyMeasure.BODY_FAT to scaleDaily { it.bodyFatPct },
            BodyMeasure.MUSCLE to scaleDaily { it.leanKg },
            BodyMeasure.BONE to scaleDaily { it.boneKg },
            BodyMeasure.BMI to bmi
        ).filter { it.second.size >= 2 }
            .map { (m, pts) -> BodySeries(m, pts, dir(m, pts)) }
    }
}
