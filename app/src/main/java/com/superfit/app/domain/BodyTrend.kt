package com.superfit.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

/**
 * How body fat, muscle (lean mass) and resting burn moved over a stretch of time, from the user's
 * own smart scale readings in Health Connect. Each end is the average of the readings near it, since
 * a single scale reading can swing a point with water. A value is null when the scale doesn't send
 * it or there aren't readings at both ends; nothing is ever estimated.
 */
data class BodyChange(
    val fatFrom: Double?,
    val fatTo: Double?,
    val leanFrom: Double?,
    val leanTo: Double?,
    val bmrFrom: Double?,
    val bmrTo: Double?,
    val weightFrom: Double?,
    val weightTo: Double?
) {
    val fat: Double? get() = diff(fatFrom, fatTo)
    val lean: Double? get() = diff(leanFrom, leanTo)
    val bmr: Double? get() = diff(bmrFrom, bmrTo)
    val weight: Double? get() = diff(weightFrom, weightTo)

    val fatMoved: Boolean get() = (fat?.let { abs(it) } ?: 0.0) >= BodyTrend.FAT_POINTS
    val leanMoved: Boolean get() = (lean?.let { abs(it) } ?: 0.0) >= BodyTrend.LEAN_KG
    val bmrMoved: Boolean get() = (bmr?.let { abs(it) } ?: 0.0) >= BodyTrend.BMR_KCAL

    /** Worth a line: at least one of the scale's own measures moved more than reading noise. */
    val notable: Boolean get() = fatMoved || leanMoved || bmrMoved

    private fun diff(a: Double?, b: Double?) = if (a != null && b != null) b - a else null
}

object BodyTrend {
    /** Smaller moves than these are within what a home scale wobbles day to day. */
    const val FAT_POINTS = 0.5
    const val LEAN_KG = 0.3
    const val BMR_KCAL = 20.0

    /** Averages readings in [fromStart]..[fromEnd] against [toStart]..[toEnd]; null when either end has none. */
    fun between(
        mine: List<ScaleReading>,
        fromStart: LocalDate,
        fromEnd: LocalDate,
        toStart: LocalDate,
        toEnd: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): BodyChange? {
        fun inRange(a: LocalDate, b: LocalDate) = mine.filter { it.time.atZone(zone).toLocalDate().let { d -> !d.isBefore(a) && !d.isAfter(b) } }
        val before = inRange(fromStart, fromEnd)
        val after = inRange(toStart, toEnd)
        if (before.isEmpty() || after.isEmpty()) return null
        fun avg(list: List<ScaleReading>, f: (ScaleReading) -> Double?) = list.mapNotNull(f).takeIf { it.isNotEmpty() }?.average()
        val lean: (ScaleReading) -> Double? = { r -> r.leanKg ?: r.bodyFatPct?.let { r.weightKg * (1 - it / 100) } }
        val change = BodyChange(
            fatFrom = avg(before) { it.bodyFatPct },
            fatTo = avg(after) { it.bodyFatPct },
            leanFrom = avg(before, lean),
            leanTo = avg(after, lean),
            bmrFrom = avg(before) { it.bmrKcal },
            bmrTo = avg(after) { it.bmrKcal },
            weightFrom = avg(before) { it.weightKg },
            weightTo = avg(after) { it.weightKg }
        )
        return change.takeIf { it.fat != null || it.lean != null || it.bmr != null }
    }

    /** The last 4 weeks: readings from the last 10 days against those 3 to 5 weeks ago. */
    fun fourWeeks(mine: List<ScaleReading>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): BodyChange? =
        between(mine, today.minusDays(38), today.minusDays(21), today.minusDays(9), today, zone)

    /** A month: readings around its start (from a week before) against its last 10 days. */
    fun month(mine: List<ScaleReading>, month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): BodyChange? {
        val end = month.atEndOfMonth()
        return between(mine, month.atDay(1).minusDays(7), month.atDay(10), end.minusDays(9), end, zone)
    }

    /**
     * The Insights line, goal-aware: losing fat or keeping muscle is good news; muscle going with
     * the weight, or fat creeping up while losing, is a heads-up. Null when nothing moved enough.
     */
    fun finding(c: BodyChange, loseGoal: Boolean): Insight? {
        val fat = c.fat
        val lean = c.lean
        val bmr = c.bmr
        val bmrText = bmr?.takeIf { c.bmrMoved }?.let { "Resting burn ${if (it > 0) "up" else "down"} ${kcal(abs(it))} kcal a day." }
        return when {
            lean != null && lean <= -LEAN_KG && (c.weight ?: 0.0) < 0 && loseGoal && (fat == null || fat > -1.0) -> Insight(
                "body_muscle_loss",
                "Some of the loss is muscle",
                listOfNotNull("Muscle down ${BodyReadings.fmt(-lean)} kg in 4 weeks.", bmrText, "More protein and two strength sessions a week help keep it.").joinToString(" "),
                "some of the weight lost is muscle"
            )
            fat != null && fat >= 1.0 && !(lean != null && lean >= LEAN_KG && !loseGoal) -> Insight(
                "body_fat_up",
                "Body fat up ${BodyReadings.fmt(fat)} points",
                listOfNotNull("Over the last 4 weeks, from your scale.", bmrText).joinToString(" "),
                "body fat crept up"
            )
            fat != null && fat <= -FAT_POINTS -> Insight(
                "body_fat_down",
                "Body fat down ${BodyReadings.fmt(-fat)} points",
                listOfNotNull(
                    "In 4 weeks, from your scale" + (if (lean != null && lean > -LEAN_KG) ", with muscle kept." else "."),
                    bmrText
                ).joinToString(" "),
                "body fat came down"
            )
            lean != null && lean >= LEAN_KG -> Insight(
                "body_muscle_up",
                "Muscle up ${BodyReadings.fmt(lean)} kg",
                listOfNotNull("In 4 weeks, from your scale.", bmrText).joinToString(" "),
                "muscle went up"
            )
            bmr != null && c.bmrMoved -> Insight(
                if (bmr > 0) "body_bmr_up" else "body_bmr_down",
                "Resting burn ${if (bmr > 0) "up" else "down"} ${kcal(abs(bmr))} kcal a day",
                "In 4 weeks, from your scale." + if (bmr > 0) " More muscle burns more at rest." else " Keeping muscle helps keep it up.",
                "resting burn ${if (bmr > 0) "rose" else "dipped"}"
            )
            else -> null
        }
    }

    /** True when [finding] is a heads-up rather than good news. */
    fun isWatch(i: Insight): Boolean = i.id in setOf("body_muscle_loss", "body_fat_up", "body_bmr_down")

    private fun kcal(v: Double) = Math.round(v).toString()
}
