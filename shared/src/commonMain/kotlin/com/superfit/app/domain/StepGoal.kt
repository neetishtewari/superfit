package com.superfit.app.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** Why the daily step goal has its current value. */
enum class StepGoalReason {
    /** Not enough step data yet: a starting goal by age. */
    STARTER,

    /** The first goal worked out from the person's own steps. */
    FIRST,

    /** Raised after a week where the goal was reached on enough days. */
    UP,

    /** Lowered to fit after a quieter stretch (illness, travel). */
    DOWN,

    /** Checked this week and kept. */
    SAME,

    /** The person set their own number in Settings. */
    OWN;

    companion object {
        fun fromName(name: String?): StepGoalReason? = entries.firstOrNull { it.name == name }
    }
}

/** The goal as saved on the phone between days: its value, why, and the day it was set. */
data class SavedStepGoal(val goal: Int, val reason: StepGoalReason, val setOn: LocalDate)

/** Today's step goal and what's needed to explain it. */
data class StepGoalResult(
    val goal: Int,
    val reason: StepGoalReason,
    val setOn: LocalDate,
    /** The person's typical day over the last two weeks; null when there aren't enough days yet. */
    val usual: Int?,
    /** Where the goal stops rising for this age. */
    val healthyAim: Int,
    /** How much the goal goes up after a good week. */
    val stepUp: Int,
    /** Days last week the previous goal was reached (only when it rose). */
    val daysReached: Int = 0
) {
    val saved: SavedStepGoal get() = SavedStepGoal(goal, reason, setOn)

    /** True when the goal has just been raised today, so Home can say so once. */
    fun raisedOn(today: LocalDate): Boolean = reason == StepGoalReason.UP && setOn == today

    /** True when the person already walks at or above the healthy level for their age. */
    val atHealthyLevel: Boolean get() = usual != null && usual >= healthyAim
}

/**
 * A daily step goal that fits the person: their usual day plus a small step up, rising a little
 * after each good week and stopping at a healthy level for their age. Checked once a week (the
 * first time the app works it out on or after Monday), never during a day.
 * See docs/superpowers/specs/2026-11-06-personal-step-goal.md.
 */
object StepGoal {
    /** Days that count towards "your usual"; quieter ones are a phone left on the table. */
    const val MIN_DAY_STEPS = 500
    const val USUAL_WINDOW_DAYS = 14
    const val MIN_DAYS = 5
    const val FLOOR = 2_000
    const val CEILING = 15_000
    const val DAYS_TO_RISE = 4
    const val DROP_GAP = 1_500
    const val OWN_MIN = 1_000
    const val OWN_MAX = 30_000

    /** Where most of walking's health benefit is reached (Paluch et al. 2022). */
    fun healthyAim(age: Int): Int = when {
        age >= 70 -> 7_000
        age >= 60 -> 8_000
        else -> 10_000
    }

    /** The goal before there are enough days of steps. */
    fun starter(age: Int): Int = when {
        age >= 70 -> 5_000
        age >= 60 -> 6_000
        else -> 7_000
    }

    fun stepUp(usual: Int): Int = if (usual < 5_000) 500 else 1_000

    /** Median steps over the 14 days before [today], counting days with at least 500; null below 5 such days. */
    fun usual(stepsByDate: Map<LocalDate, Int>, today: LocalDate): Int? {
        val days = (1..USUAL_WINDOW_DAYS)
            .mapNotNull { stepsByDate[today.minus(DatePeriod(days = it))] }
            .filter { it >= MIN_DAY_STEPS }
            .sorted()
        if (days.size < MIN_DAYS) return null
        val mid = days.size / 2
        return if (days.size % 2 == 1) days[mid] else (days[mid - 1] + days[mid]) / 2
    }

    /** The goal for someone whose usual day is [usual] steps. */
    fun target(usual: Int, age: Int): Int {
        val aim = healthyAim(age)
        if (usual >= aim) return maxOf(aim, usual / 500 * 500).coerceAtMost(CEILING)
        return roundTo500(usual + stepUp(usual)).coerceIn(FLOOR, aim)
    }

    /** Monday of the week [day] is in. */
    fun weekStart(day: LocalDate): LocalDate = day.minus(DatePeriod(days = day.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal))

    /**
     * Today's goal. [saved] is what the phone kept from last time; [ownGoal] is a number the
     * person set in Settings, which wins over everything.
     */
    fun update(
        stepsByDate: Map<LocalDate, Int>,
        age: Int,
        saved: SavedStepGoal?,
        ownGoal: Int?,
        today: LocalDate
    ): StepGoalResult {
        val usual = usual(stepsByDate, today)
        val aim = healthyAim(age)
        fun result(goal: Int, reason: StepGoalReason, setOn: LocalDate, reached: Int = 0) =
            StepGoalResult(goal, reason, setOn, usual, aim, stepUp(goal), reached)

        if (ownGoal != null) return result(ownGoal.coerceIn(OWN_MIN, OWN_MAX), StepGoalReason.OWN, saved?.setOn ?: today)

        // A goal left from an own number (just switched back to automatic) is not a base to build on.
        val base = saved?.takeIf { it.reason != StepGoalReason.OWN }

        if (usual == null) {
            // Not enough steps yet (or Health Connect went quiet): keep a worked-out goal, else start by age.
            return if (base != null && base.reason != StepGoalReason.STARTER) {
                result(base.goal, base.reason, base.setOn)
            } else {
                result(starter(age), StepGoalReason.STARTER, base?.setOn ?: today)
            }
        }

        if (base == null || base.reason == StepGoalReason.STARTER) {
            return result(target(usual, age), StepGoalReason.FIRST, today)
        }

        // Checked already this week: keep it steady.
        if (base.setOn >= weekStart(today)) return result(base.goal, base.reason, base.setOn)

        val next = target(usual, age)
        val reached = (1..7).count { (stepsByDate[today.minus(DatePeriod(days = it))] ?: 0) >= base.goal }
        return when {
            next > base.goal && reached >= DAYS_TO_RISE ->
                // Rise by at most one step up a week, so a single big week doesn't jump it.
                result(minOf(next, base.goal + stepUp(base.goal)), StepGoalReason.UP, today, reached)
            usual < base.goal - DROP_GAP -> result(next, StepGoalReason.DOWN, today)
            else -> result(base.goal, StepGoalReason.SAME, today)
        }
    }

    /** The age-right range for the coach's step tips. */
    fun healthyRangeText(age: Int): String = if (age >= 60) "6,000 to 8,000" else "8,000 to 10,000"

    private fun roundTo500(steps: Int): Int = (steps + 250) / 500 * 500
}
