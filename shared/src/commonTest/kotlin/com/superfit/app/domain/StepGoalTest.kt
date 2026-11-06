package com.superfit.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

class StepGoalTest {

    // A Monday.
    private val monday = LocalDate(2026, 11, 9)
    private val wednesday = LocalDate(2026, 11, 11)

    /** [steps] for each of the [days] days before [today], most recent first. */
    private fun history(today: LocalDate, vararg steps: Int): Map<LocalDate, Int> =
        steps.mapIndexed { i, s -> today.minus(DatePeriod(days = i + 1)) to s }.toMap()

    private fun flat(today: LocalDate, steps: Int, days: Int = 14) = history(today, *IntArray(days) { steps })

    @Test
    fun healthyAimAndStarterByAge() {
        assertEquals(10_000, StepGoal.healthyAim(35))
        assertEquals(8_000, StepGoal.healthyAim(60))
        assertEquals(7_000, StepGoal.healthyAim(71))
        assertEquals(7_000, StepGoal.starter(35))
        assertEquals(6_000, StepGoal.starter(65))
        assertEquals(5_000, StepGoal.starter(71))
    }

    @Test
    fun usualIsTheMedianIgnoringQuietDaysAndNeedsFiveDays() {
        assertNull(StepGoal.usual(history(monday, 4000, 4000, 4000, 4000), monday))
        // The 100-step day (phone on the table) and the 20,000 wedding don't move it.
        val days = history(monday, 3000, 3100, 100, 3200, 20_000, 2900, 3050)
        assertEquals(3075, StepGoal.usual(days, monday))
        // Today's partial count is not part of the usual.
        assertNull(StepGoal.usual(mapOf(monday to 9000), monday))
    }

    @Test
    fun targetIsUsualPlusSmallStepCappedAtAgeAim() {
        assertEquals(3_500, StepGoal.target(3_100, 71))
        assertEquals(9_000, StepGoal.target(8_200, 40))
        assertEquals(10_000, StepGoal.target(9_400, 40))
        assertEquals(7_000, StepGoal.target(6_800, 71))
        assertEquals(2_000, StepGoal.target(900, 80))
    }

    @Test
    fun veryActivePeopleStayAtTheHealthyAim() {
        assertEquals(10_000, StepGoal.target(12_300, 40))
        assertEquals(10_000, StepGoal.target(22_000, 40))
        assertEquals(8_000, StepGoal.target(11_000, 65))
    }

    @Test
    fun starterUntilEnoughStepsThenPersonal() {
        val start = StepGoal.update(history(monday, 3000, 3000), 71, null, null, monday)
        assertEquals(5_000, start.goal)
        assertEquals(StepGoalReason.STARTER, start.reason)

        // Even mid-week, the first personal goal comes as soon as there are enough days.
        val first = StepGoal.update(flat(wednesday, 3100), 71, start.saved, null, wednesday)
        assertEquals(3_500, first.goal)
        assertEquals(StepGoalReason.FIRST, first.reason)
        assertEquals(3_100, first.usual)
    }

    @Test
    fun keepsTheGoalDuringTheWeek() {
        val saved = SavedStepGoal(3_500, StepGoalReason.FIRST, monday)
        val r = StepGoal.update(flat(wednesday, 6000), 71, saved, null, wednesday)
        assertEquals(3_500, r.goal)
        assertEquals(monday, r.setOn)
    }

    @Test
    fun risesAfterAGoodWeekByOneStep() {
        val saved = SavedStepGoal(3_500, StepGoalReason.FIRST, monday.minus(DatePeriod(days = 7)))
        // Reached 3,500 on 5 of the last 7 days; usual now 3,500.
        val steps = history(monday, 3600, 3700, 3500, 3200, 3550, 3100, 3600, 3400, 3500, 3400, 3500, 3400, 3500, 3400)
        val r = StepGoal.update(steps, 71, saved, null, monday)
        assertEquals(4_000, r.goal)
        assertEquals(StepGoalReason.UP, r.reason)
        assertEquals(5, r.daysReached)
        assertTrue(r.raisedOn(monday))
        assertFalse(r.raisedOn(monday.minus(DatePeriod(days = -1))))
    }

    @Test
    fun aBigWeekRaisesOnlyOneStep() {
        val saved = SavedStepGoal(3_500, StepGoalReason.SAME, monday.minus(DatePeriod(days = 7)))
        val r = StepGoal.update(flat(monday, 9_000), 71, saved, null, monday)
        assertEquals(4_000, r.goal)
    }

    @Test
    fun staysWhenNotReachedOnEnoughDays() {
        val saved = SavedStepGoal(4_000, StepGoalReason.UP, monday.minus(DatePeriod(days = 7)))
        val steps = history(monday, 4100, 4200, 3600, 3700, 3600, 3500, 3600, *IntArray(7) { 3600 })
        val r = StepGoal.update(steps, 71, saved, null, monday)
        assertEquals(4_000, r.goal)
        assertEquals(StepGoalReason.SAME, r.reason)
        assertEquals(monday, r.setOn)
    }

    @Test
    fun stopsAtTheHealthyAim() {
        val saved = SavedStepGoal(7_000, StepGoalReason.UP, monday.minus(DatePeriod(days = 7)))
        val r = StepGoal.update(flat(monday, 7_200), 71, saved, null, monday)
        assertEquals(7_200, r.usual)
        // Walking above the aim, the goal stays at the aim.
        assertEquals(7_000, r.goal)
        assertTrue(r.atHealthyLevel)
    }

    @Test
    fun aGoalSavedAboveTheAimComesDownToIt() {
        val saved = SavedStepGoal(12_000, StepGoalReason.FIRST, wednesday.minus(DatePeriod(days = 1)))
        val r = StepGoal.update(flat(wednesday, 12_300), 40, saved, null, wednesday)
        assertEquals(10_000, r.goal)
    }

    @Test
    fun resetsLowerAfterAQuietStretch() {
        val saved = SavedStepGoal(9_000, StepGoalReason.UP, monday.minus(DatePeriod(days = 7)))
        val r = StepGoal.update(flat(monday, 5_000), 40, saved, null, monday)
        assertEquals(6_000, r.goal)
        assertEquals(StepGoalReason.DOWN, r.reason)
    }

    @Test
    fun keepsWorkedOutGoalWhenStepsStop() {
        val saved = SavedStepGoal(6_000, StepGoalReason.UP, monday.minus(DatePeriod(days = 7)))
        val r = StepGoal.update(emptyMap(), 40, saved, null, monday)
        assertEquals(6_000, r.goal)
    }

    @Test
    fun ownNumberWinsAndBackToAutomaticStartsFresh() {
        val own = StepGoal.update(flat(monday, 3_000), 71, null, 2_500, monday)
        assertEquals(2_500, own.goal)
        assertEquals(StepGoalReason.OWN, own.reason)
        assertEquals(30_000, StepGoal.update(emptyMap(), 30, null, 50_000, monday).goal)

        val back = StepGoal.update(flat(wednesday, 3_000), 71, own.saved, null, wednesday)
        assertEquals(3_500, back.goal)
        assertEquals(StepGoalReason.FIRST, back.reason)
    }

    @Test
    fun weekStartsOnMonday() {
        assertEquals(monday, StepGoal.weekStart(wednesday))
        assertEquals(monday, StepGoal.weekStart(monday))
        assertEquals(monday, StepGoal.weekStart(LocalDate(2026, 11, 15)))
    }
}
