package com.superfit.app.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class ForecastStatus { ON_TRACK, AT_RISK, OFF_TRACK, REACHED, NOT_ENOUGH_DATA }

/** "Keep weekends within 300 kcal of target": [weeksSooner] when there is a forecast, else the date it would bring. */
data class ForecastFix(val label: String, val weeksSooner: Int?, val newDate: LocalDate?)

data class GoalMilestone(val label: String, val weightKg: Double, val reachedOn: LocalDate?, val kgToGo: Double)

data class JourneyForecast(
    val startKg: Double,
    val targetKg: Double,
    val currentKg: Double,
    val percentCovered: Int,
    val kgDone: Double,
    val kgToGo: Double,
    val dayNumber: Long,
    /** Progress towards the goal per week (positive = the right direction), last 4 weeks and the 4 before. */
    val recentRate: Double?,
    val earlierRate: Double?,
    val rateFromWeighIns: Boolean,
    val forecastDate: LocalDate?,
    val targetDate: LocalDate?,
    /** Weeks after the target date (negative = early). */
    val weeksLate: Int?,
    val status: ForecastStatus,
    val headline: String,
    val explanation: String,
    val slowing: List<Insight>,
    val fixes: List<ForecastFix>,
    val allFixesDate: LocalDate?,
    val milestones: List<GoalMilestone>,
    val daysSinceWeighIn: Long?,
    val weighIns: List<Pair<LocalDate, Double>>
)

/**
 * Journey's "will I get there?": goal covered, a forecast date at current habits, what is slowing
 * the user down and what would bring the date closer. Weigh-ins drive the pace when there are
 * enough; otherwise the logged eating against maintenance does. No AI.
 */
object GoalForecast {

    private const val KCAL_PER_KG = 7700.0
    private const val MIN_RATE = 0.05 // kg a week; slower than this is "not moving"
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())

    fun compute(
        startKg: Double,
        targetKg: Double,
        startDate: LocalDate,
        weighIns: List<Pair<LocalDate, Double>>,
        days: Map<LocalDate, DayTotals>,
        calorieOffset: Int,
        targetDate: LocalDate?,
        today: LocalDate,
        fallbackCurrentKg: Double
    ): JourneyForecast {
        val sorted = weighIns.sortedBy { it.first }
        val currentKg = sorted.lastOrNull()?.second ?: fallbackCurrentKg
        val dir = if (targetKg < startKg) -1.0 else 1.0
        val total = abs(targetKg - startKg)
        val done = ((currentKg - startKg) * dir).coerceAtLeast(0.0)
        val toGo = ((targetKg - currentKg) * dir).coerceAtLeast(0.0)
        val percent = if (total < 0.01) 100 else ((done / total) * 100).roundToInt().coerceIn(0, 100)
        val dayNumber = ChronoUnit.DAYS.between(startDate, today).coerceAtLeast(0) + 1

        val recentWeigh = slope(sorted, today.minusDays(27), today)
        val earlierWeigh = slope(sorted, today.minusDays(55), today.minusDays(28))
        val recentEnergy = energyRate(days, today.minusDays(28), today.minusDays(1), calorieOffset)
        val earlierEnergy = energyRate(days, today.minusDays(56), today.minusDays(29), calorieOffset)
        val fromWeighIns = recentWeigh != null
        val recent = (recentWeigh ?: recentEnergy)?.times(dir)
        val earlier = (if (fromWeighIns) earlierWeigh else earlierEnergy)?.times(dir)

        val month = InsightsEngine.range(days, today.minusDays(28), today.minusDays(1))
        val prevMonth = InsightsEngine.range(days, today.minusDays(56), today.minusDays(29))
        val slowing = slowingReasons(month, prevMonth, dir < 0)
        val lastWeighIn = sorted.lastOrNull()?.first
        val milestones = milestones(startKg, targetKg, currentKg, sorted, dir)

        fun forecastFor(rate: Double?): LocalDate? =
            if (rate == null || rate < MIN_RATE || toGo <= 0) {
                null
            } else {
                today.plusDays(ceil(toGo / rate * 7).roundToLong())
            }

        val forecastDate = forecastFor(recent)
        val weeksLate = if (forecastDate != null && targetDate != null) {
            ceil(ChronoUnit.DAYS.between(targetDate, forecastDate) / 7.0).toInt()
        } else {
            null
        }
        val slowed = recent != null && earlier != null && earlier >= MIN_RATE && recent <= earlier * 0.6

        val status = when {
            toGo <= 0.0 -> ForecastStatus.REACHED
            recent == null -> ForecastStatus.NOT_ENOUGH_DATA
            forecastDate == null -> ForecastStatus.OFF_TRACK
            weeksLate != null -> if (weeksLate > 0) ForecastStatus.AT_RISK else ForecastStatus.ON_TRACK
            slowed -> ForecastStatus.AT_RISK
            else -> ForecastStatus.ON_TRACK
        }

        val goal = fmt(targetKg)
        val headline = when (status) {
            ForecastStatus.REACHED -> "You've reached $goal kg."
            ForecastStatus.NOT_ENOUGH_DATA -> "Log a weekly weigh-in and most of your meals to see when you'll reach $goal kg."
            ForecastStatus.OFF_TRACK -> "At your current habits your weight isn't moving towards $goal kg."
            else -> "At your current habits you'll reach $goal kg around ${forecastDate!!.format(dayMonth)}."
        }
        val explanation = buildList {
            if (weeksLate != null && targetDate != null) {
                add(
                    when {
                        weeksLate > 0 -> "That's ${weeks(weeksLate)} later than your target date of ${targetDate.format(dayMonth)}."
                        weeksLate < 0 -> "That's ${weeks(-weeksLate)} ahead of your target date of ${targetDate.format(dayMonth)}."
                        else -> "Right on your target date of ${targetDate.format(dayMonth)}."
                    }
                )
            }
            if (slowed) {
                add("Your pace has slowed from ${rate(earlier!!)} to ${rate(recent!!.coerceAtLeast(0.0))} kg a week over the last month.")
            } else if (recent != null && status != ForecastStatus.REACHED && status != ForecastStatus.OFF_TRACK) {
                add("You're ${if (dir < 0) "losing" else "gaining"} about ${rate(recent)} kg a week${if (fromWeighIns) "" else ", going by your logged meals"}.")
            }
            if (status == ForecastStatus.REACHED) add("Set your next goal, or switch to maintaining.")
        }.joinToString(" ")

        val fixes = if (status == ForecastStatus.REACHED || status == ForecastStatus.NOT_ENOUGH_DATA) {
            emptyList()
        } else {
            fixes(month, prevMonth, dir < 0, recent ?: 0.0, forecastDate, ::forecastFor)
        }
        val extraAll = fixExtras(month, prevMonth, dir < 0).sumOf { it.second }
        val allFixesDate = if (fixes.size >= 2) forecastFor((recent ?: 0.0) + extraAll / KCAL_PER_KG) else null

        return JourneyForecast(
            startKg = startKg,
            targetKg = targetKg,
            currentKg = currentKg,
            percentCovered = percent,
            kgDone = done,
            kgToGo = toGo,
            dayNumber = dayNumber,
            recentRate = recent,
            earlierRate = earlier,
            rateFromWeighIns = fromWeighIns,
            forecastDate = forecastDate,
            targetDate = targetDate,
            weeksLate = weeksLate,
            status = status,
            headline = headline,
            explanation = explanation,
            slowing = if (status == ForecastStatus.REACHED) emptyList() else slowing,
            fixes = fixes,
            allFixesDate = allFixesDate,
            milestones = milestones,
            daysSinceWeighIn = lastWeighIn?.let { ChronoUnit.DAYS.between(it, today) },
            weighIns = sorted
        )
    }

    /** Least-squares weight change in kg a week; needs 2+ weigh-ins at least 10 days apart. */
    fun slope(points: List<Pair<LocalDate, Double>>, from: LocalDate, to: LocalDate): Double? {
        val p = points.filter { !it.first.isBefore(from) && !it.first.isAfter(to) }
        if (p.size < 2) return null
        if (ChronoUnit.DAYS.between(p.first().first, p.last().first) < 10) return null
        val xs = p.map { ChronoUnit.DAYS.between(from, it.first).toDouble() }
        val ys = p.map { it.second }
        val mx = xs.average()
        val my = ys.average()
        val den = xs.sumOf { (it - mx) * (it - mx) }
        if (den == 0.0) return null
        return xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den * 7
    }

    /**
     * Weight change a week implied by logged eating: (eaten - maintenance) / 7700. The day's target
     * already includes the goal's offset, so maintenance = target - offset. Needs 7+ logged days.
     */
    fun energyRate(days: Map<LocalDate, DayTotals>, from: LocalDate, to: LocalDate, calorieOffset: Int): Double? {
        val logged = InsightsEngine.range(days, from, to).filter { it.logged && it.kcalTarget > 0 }
        if (logged.size < 7) return null
        val balance = logged.map { it.net + calorieOffset }.average()
        return balance * 7 / KCAL_PER_KG
    }

    private fun slowingReasons(month: List<DayTotals>, prev: List<DayTotals>, lose: Boolean): List<Insight> = buildList {
        if (lose) {
            InsightsEngine.weekendSplit(month)?.let { (weekendAvg, share) ->
                if (weekendAvg >= 300 && share >= 0.3) {
                    val weekdayAvg = month.filter { it.logged && it.date.dayOfWeek.value <= 5 }.map { -it.net }.average()
                    add(
                        Insight(
                            "weekend",
                            "Weekends undo ${if (share >= 0.75) "most" else "much"} of your weekday deficit",
                            "You average ${InsightsEngine.kcal(weekdayAvg)} kcal under target Mon to Fri, but ${InsightsEngine.kcal(weekendAvg)} over on Sat and Sun.",
                            "weekends"
                        )
                    )
                }
            }
        }
        val now = month.sumOf { it.workouts.size } / 4.0
        val before = prev.sumOf { it.workouts.size } / 4.0
        if (before >= 2 && now <= before * 0.7 && before - now >= 1) {
            add(Insight("fewer", "Fewer workouts this month", "${now.roundToInt()} a week in the last 4 weeks, down from ${before.roundToInt()}.", "fewer workouts"))
        }
        if (lose) {
            val logged = month.filter { it.logged }
            if (logged.size >= 7 && logged.count { it.net > 0 } * 2 > logged.size && none { it.id == "weekend" }) {
                add(Insight("over", "Over target on most days", "${logged.count { it.net > 0 }} of ${logged.size} logged days were above your calorie target.", "over target"))
            }
        }
        if (month.count { it.logged } < 14) {
            add(Insight("gaps", "Many days aren't logged", "${month.count { it.logged }} of the last 28 days are fully logged, so the forecast is less sure.", "gaps"))
        }
    }

    /** Each fix with the extra kcal a week it would save (or burn). */
    private fun fixExtras(month: List<DayTotals>, prev: List<DayTotals>, lose: Boolean): List<Pair<String, Double>> = buildList {
        if (lose) {
            InsightsEngine.weekendSplit(month)?.let { (weekendAvg, share) ->
                if (weekendAvg > 300 && share >= 0.3) add("Keep weekends within 300 kcal of target" to 2 * (weekendAvg - 300))
            }
        }
        val nowCount = month.sumOf { it.workouts.size } / 4.0
        val beforeCount = prev.sumOf { it.workouts.size } / 4.0
        val all = (month + prev).flatMap { it.workouts }
        val perWorkout = if (all.isEmpty()) 0.0 else all.sumOf { it.kcal } / all.size
        if (lose && beforeCount >= 2 && nowCount <= beforeCount * 0.7 && beforeCount - nowCount >= 1 && perWorkout >= 50) {
            add("Get back to ${beforeCount.roundToInt()} workouts a week" to (beforeCount - nowCount) * perWorkout)
        }
    }

    private fun fixes(
        month: List<DayTotals>,
        prev: List<DayTotals>,
        lose: Boolean,
        rate: Double,
        current: LocalDate?,
        forecastFor: (Double?) -> LocalDate?
    ): List<ForecastFix> = fixExtras(month, prev, lose).mapNotNull { (label, extraKcal) ->
        val newDate = forecastFor(rate + extraKcal / KCAL_PER_KG) ?: return@mapNotNull null
        if (current == null) return@mapNotNull ForecastFix(label, null, newDate)
        val sooner = (ChronoUnit.DAYS.between(newDate, current) / 7.0).roundToInt()
        if (sooner >= 1) ForecastFix(label, sooner, newDate) else null
    }

    private fun milestones(start: Double, target: Double, current: Double, weighIns: List<Pair<LocalDate, Double>>, dir: Double): List<GoalMilestone> {
        val total = abs(target - start)
        fun reached(kg: Double) = weighIns.firstOrNull { (it.second - kg) * dir >= 0 }?.first
        return buildList {
            if (total >= 4) {
                val first = start + 2 * dir
                add(GoalMilestone("First 2 kg ${if (dir < 0) "down" else "up"}", first, reached(first), ((first - current) * dir).coerceAtLeast(0.0)))
            }
            if (total >= 2) {
                val half = start + total / 2 * dir
                add(GoalMilestone("Halfway (${fmt(half)} kg)", half, reached(half), ((half - current) * dir).coerceAtLeast(0.0)))
            }
            add(GoalMilestone("Goal (${fmt(target)} kg)", target, reached(target), ((target - current) * dir).coerceAtLeast(0.0)))
        }
    }

    private fun weeks(n: Int) = if (n == 1) "1 week" else "$n weeks"

    fun fmt(v: Double): String = String.format(Locale.US, "%.1f", v)

    /** "0.25", "0.5", "1" kg a week. */
    fun rate(v: Double): String = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
}
